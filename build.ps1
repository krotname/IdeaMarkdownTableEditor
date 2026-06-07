param(
	[string]$IdeaHome = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3",
	[string]$Version = "5.5.0"
)

$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -Scope Global -ErrorAction SilentlyContinue) {
	$global:PSNativeCommandUseErrorActionPreference = $true
}

function Convert-ToJavacPath([string]$Path) {
	return $Path.Replace("\", "/")
}

function Write-JavacArgs([string]$Path, [string[]]$Lines) {
	$encoding = New-Object Text.UTF8Encoding($false)
	[IO.File]::WriteAllLines($Path, $Lines, $encoding)
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PluginName = "MarkdownTableEditorIdea"
$BuildDir = Join-Path $ProjectRoot "build"
$ClassesDir = Join-Path $BuildDir "classes"
$TestClassesDir = Join-Path $BuildDir "test-classes"
$JarStageDir = Join-Path $BuildDir "jar"
$DistRoot = Join-Path $BuildDir "dist"
$PluginDistDir = Join-Path $DistRoot $PluginName
$JarPath = Join-Path $PluginDistDir "lib\$PluginName.jar"
$ZipPath = Join-Path $BuildDir "$PluginName-$Version.zip"

if (-not (Test-Path -LiteralPath $IdeaHome)) {
	throw "IntelliJ IDEA home not found: $IdeaHome"
}

$LibDir = Join-Path $IdeaHome "lib"
$Jars = Get-ChildItem -LiteralPath $LibDir -Filter "*.jar" | ForEach-Object { Convert-ToJavacPath $_.FullName }
if (-not $Jars) {
	throw "No IntelliJ IDEA jars found in $LibDir"
}

$Classpath = [string]::Join([IO.Path]::PathSeparator, $Jars)

if (Test-Path -LiteralPath $BuildDir) {
	Remove-Item -LiteralPath $BuildDir -Recurse -Force
}
New-Item -ItemType Directory -Path $ClassesDir, $TestClassesDir, (Split-Path -Parent $JarPath), $JarStageDir | Out-Null

$Sources = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "src\main\java") -Recurse -Filter "*.java" | ForEach-Object { Convert-ToJavacPath $_.FullName }
if (-not $Sources) {
	throw "No main Java sources found"
}

$MainArgsFile = Join-Path $BuildDir "javac-main.args"
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
Write-JavacArgs $MainArgsFile $MainArgs

javac "@$MainArgsFile"

$Resources = Join-Path $ProjectRoot "src\main\resources"
if (Test-Path -LiteralPath $Resources) {
	Copy-Item -Path (Join-Path $Resources "*") -Destination $JarStageDir -Recurse -Force
}
Copy-Item -Path (Join-Path $ClassesDir "*") -Destination $JarStageDir -Recurse -Force

jar --create --file $JarPath -C $JarStageDir .

$Tests = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "src\test\java") -Recurse -Filter "*.java" | ForEach-Object { Convert-ToJavacPath $_.FullName }
if ($Tests) {
	$TestArgsFile = Join-Path $BuildDir "javac-test.args"
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
	Write-JavacArgs $TestArgsFile $TestArgs

	javac "@$TestArgsFile"

	$TestRunArgsFile = Join-Path $BuildDir "java-test.args"
	$TestRuntimeClasspath = "$(Convert-ToJavacPath $ClassesDir)$([IO.Path]::PathSeparator)$(Convert-ToJavacPath $TestClassesDir)$([IO.Path]::PathSeparator)$Classpath"
	$TestRunArgs = @(
		"-cp"
		"`"$TestRuntimeClasspath`""
		"name.krot.markdowntableidea.core.MarkdownTableCoreSmoke"
	)
	Write-JavacArgs $TestRunArgsFile $TestRunArgs
	java "@$TestRunArgsFile"
}

if (Test-Path -LiteralPath $ZipPath) {
	Remove-Item -LiteralPath $ZipPath -Force
}
Compress-Archive -LiteralPath $PluginDistDir -DestinationPath $ZipPath -CompressionLevel Optimal

Write-Output "Built $ZipPath"
