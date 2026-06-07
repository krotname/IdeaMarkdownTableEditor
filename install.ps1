param(
	[string]$IdeaConfigDir = "$env:APPDATA\JetBrains\IntelliJIdea2026.1"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PluginName = "MarkdownTableEditorIdea"
$ZipPath = Join-Path $ProjectRoot "build\$PluginName-0.1.0.zip"
$PluginsDir = Join-Path $IdeaConfigDir "plugins"
$TargetDir = Join-Path $PluginsDir $PluginName

if (-not (Test-Path -LiteralPath $ZipPath)) {
	& (Join-Path $ProjectRoot "build.ps1")
}

if (-not (Test-Path -LiteralPath $PluginsDir)) {
	New-Item -ItemType Directory -Path $PluginsDir | Out-Null
}

if (Test-Path -LiteralPath $TargetDir) {
	Remove-Item -LiteralPath $TargetDir -Recurse -Force
}

$TempDir = Join-Path ([IO.Path]::GetTempPath()) ("$PluginName-install-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $TempDir | Out-Null
try {
	Expand-Archive -LiteralPath $ZipPath -DestinationPath $TempDir -Force
	Copy-Item -LiteralPath (Join-Path $TempDir $PluginName) -Destination $PluginsDir -Recurse -Force
}
finally {
	if (Test-Path -LiteralPath $TempDir) {
		Remove-Item -LiteralPath $TempDir -Recurse -Force
	}
}

Write-Output "Installed to $TargetDir"
Write-Output "Restart IntelliJ IDEA to load the plugin."
