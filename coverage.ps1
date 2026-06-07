param(
	[string]$IdeaHome = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3",
	[string]$JacocoVersion = "0.8.13"
)

$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -Scope Global -ErrorAction SilentlyContinue) {
	$global:PSNativeCommandUseErrorActionPreference = $true
}

function Convert-ToJavacPath([string]$Path) {
	return $Path.Replace("\", "/")
}

function Write-Utf8File([string]$Path, [string[]]$Lines) {
	$encoding = New-Object Text.UTF8Encoding($false)
	[IO.File]::WriteAllLines($Path, $Lines, $encoding)
}

function Format-Rate([int]$Covered, [int]$Missed) {
	$total = $Covered + $Missed
	if ($total -eq 0) {
		return "n/a"
	}
	return "{0:P2}" -f ($Covered / $total)
}

function Download-FileIfMissing([string]$Path, [string]$Url) {
	if (Test-Path -LiteralPath $Path) {
		return
	}
	Write-Output "Downloading $Url"
	Invoke-WebRequest -Uri $Url -OutFile $Path
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildDir = Join-Path $ProjectRoot "build"
$CoverageWorkDir = Join-Path $BuildDir "coverage"
$ClassesDir = Join-Path $CoverageWorkDir "classes"
$TestClassesDir = Join-Path $CoverageWorkDir "test-classes"
$ToolsDir = Join-Path $CoverageWorkDir "tools"
$ReportsDir = Join-Path $BuildDir "reports\coverage"
$HtmlDir = Join-Path $ReportsDir "html"
$CsvPath = Join-Path $ReportsDir "jacoco.csv"
$XmlPath = Join-Path $ReportsDir "jacoco.xml"
$ExecPath = Join-Path $CoverageWorkDir "jacoco.exec"
$SummaryJsonPath = Join-Path $ReportsDir "summary.json"
$SummaryMarkdownPath = Join-Path $ReportsDir "coverage-summary.md"

if (-not (Test-Path -LiteralPath $IdeaHome)) {
	throw "IntelliJ IDEA home not found: $IdeaHome"
}

if (Test-Path -LiteralPath $CoverageWorkDir) {
	Remove-Item -LiteralPath $CoverageWorkDir -Recurse -Force
}
if (Test-Path -LiteralPath $ReportsDir) {
	Remove-Item -LiteralPath $ReportsDir -Recurse -Force
}
New-Item -ItemType Directory -Path $ClassesDir, $TestClassesDir, $ToolsDir, $HtmlDir | Out-Null

$AgentJar = Join-Path $ToolsDir "jacocoagent.jar"
$CliJar = Join-Path $ToolsDir "jacococli.jar"
$MavenBase = "https://repo.maven.apache.org/maven2/org/jacoco"
Download-FileIfMissing $AgentJar "$MavenBase/org.jacoco.agent/$JacocoVersion/org.jacoco.agent-$JacocoVersion-runtime.jar"
Download-FileIfMissing $CliJar "$MavenBase/org.jacoco.cli/$JacocoVersion/org.jacoco.cli-$JacocoVersion-nodeps.jar"

$LibDir = Join-Path $IdeaHome "lib"
$Jars = Get-ChildItem -LiteralPath $LibDir -Filter "*.jar" | ForEach-Object { Convert-ToJavacPath $_.FullName }
if (-not $Jars) {
	throw "No IntelliJ IDEA jars found in $LibDir"
}

$Classpath = [string]::Join([IO.Path]::PathSeparator, $Jars)
$Sources = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "src\main\java") -Recurse -Filter "*.java" | ForEach-Object { Convert-ToJavacPath $_.FullName }
if (-not $Sources) {
	throw "No main Java sources found"
}

$MainArgsFile = Join-Path $CoverageWorkDir "javac-main.args"
$MainArgs = @(
	"-encoding"
	"UTF-8"
	"-source"
	"21"
	"-target"
	"21"
	"-cp"
	"`"$Classpath`""
	"-d"
	"`"$(Convert-ToJavacPath $ClassesDir)`""
) + ($Sources | ForEach-Object { "`"$_`"" })
Write-Utf8File $MainArgsFile $MainArgs
javac "@$MainArgsFile"

$Tests = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "src\test\java") -Recurse -Filter "*.java" | ForEach-Object { Convert-ToJavacPath $_.FullName }
if (-not $Tests) {
	throw "No Java tests found"
}

$TestArgsFile = Join-Path $CoverageWorkDir "javac-test.args"
$TestArgs = @(
	"-encoding"
	"UTF-8"
	"-source"
	"21"
	"-target"
	"21"
	"-cp"
	"`"$(Convert-ToJavacPath $ClassesDir)$([IO.Path]::PathSeparator)$Classpath`""
	"-d"
	"`"$(Convert-ToJavacPath $TestClassesDir)`""
) + ($Tests | ForEach-Object { "`"$_`"" })
Write-Utf8File $TestArgsFile $TestArgs
javac "@$TestArgsFile"

$RuntimeClasspath = "$(Convert-ToJavacPath $ClassesDir)$([IO.Path]::PathSeparator)$(Convert-ToJavacPath $TestClassesDir)$([IO.Path]::PathSeparator)$Classpath"
$AgentArg = "-javaagent:$AgentJar=destfile=$ExecPath,includes=name.krot.markdowntableidea.*"
$TestRunArgsFile = Join-Path $CoverageWorkDir "java-test.args"
$TestRunArgs = @(
	$AgentArg
	"-cp"
	"`"$RuntimeClasspath`""
	"name.krot.markdowntableidea.core.MarkdownTableCoreSmoke"
)
Write-Utf8File $TestRunArgsFile $TestRunArgs
java "@$TestRunArgsFile"

java -jar $CliJar report $ExecPath `
	--classfiles $ClassesDir `
	--sourcefiles (Join-Path $ProjectRoot "src\main\java") `
	--html $HtmlDir `
	--xml $XmlPath `
	--csv $CsvPath

$Rows = Import-Csv -LiteralPath $CsvPath
$InstructionCovered = 0
$InstructionMissed = 0
$BranchCovered = 0
$BranchMissed = 0
$LineCovered = 0
$LineMissed = 0
$MethodCovered = 0
$MethodMissed = 0
foreach ($row in $Rows) {
	$InstructionCovered += [int]$row.INSTRUCTION_COVERED
	$InstructionMissed += [int]$row.INSTRUCTION_MISSED
	$BranchCovered += [int]$row.BRANCH_COVERED
	$BranchMissed += [int]$row.BRANCH_MISSED
	$LineCovered += [int]$row.LINE_COVERED
	$LineMissed += [int]$row.LINE_MISSED
	$MethodCovered += [int]$row.METHOD_COVERED
	$MethodMissed += [int]$row.METHOD_MISSED
}

$Summary = [ordered]@{
	project = "IdeaMarkdownTableEditor"
	lineCoverage = Format-Rate $LineCovered $LineMissed
	linesCovered = $LineCovered
	linesMissed = $LineMissed
	instructionCoverage = Format-Rate $InstructionCovered $InstructionMissed
	instructionsCovered = $InstructionCovered
	instructionsMissed = $InstructionMissed
	branchCoverage = Format-Rate $BranchCovered $BranchMissed
	branchesCovered = $BranchCovered
	branchesMissed = $BranchMissed
	methodCoverage = Format-Rate $MethodCovered $MethodMissed
	methodsCovered = $MethodCovered
	methodsMissed = $MethodMissed
	htmlReport = $HtmlDir
	xmlReport = $XmlPath
	csvReport = $CsvPath
}

$Summary | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $SummaryJsonPath -Encoding UTF8

$Markdown = @(
	"# Java coverage report"
	""
	"Generated: $(Get-Date -Format s)"
	""
	"| Metric | Covered | Missed | Coverage |"
	"| --- | ---: | ---: | ---: |"
	"| Lines | $LineCovered | $LineMissed | $(Format-Rate $LineCovered $LineMissed) |"
	"| Instructions | $InstructionCovered | $InstructionMissed | $(Format-Rate $InstructionCovered $InstructionMissed) |"
	"| Branches | $BranchCovered | $BranchMissed | $(Format-Rate $BranchCovered $BranchMissed) |"
	"| Methods | $MethodCovered | $MethodMissed | $(Format-Rate $MethodCovered $MethodMissed) |"
	""
	"HTML: $HtmlDir"
	"XML: $XmlPath"
	"CSV: $CsvPath"
)
Write-Utf8File $SummaryMarkdownPath $Markdown

Write-Output "Coverage summary: $SummaryMarkdownPath"
Write-Output "HTML report: $HtmlDir\index.html"
