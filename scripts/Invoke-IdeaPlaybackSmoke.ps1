param(
    [string]$IdeaExe = "",
    [string]$PluginZip = "",
    [string]$WorkDir = "",
    [string]$ConfigDir = "",
    [switch]$KeepInstalledPlugin,
    [int]$WaitForExitSeconds = 240
)

$ErrorActionPreference = "Stop"

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$buildRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot "build"))

function Resolve-LatestIdeaExe {
    $candidates = @(Get-ChildItem -Path "C:\Program Files\JetBrains" -Filter "idea64.exe" -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like "*IntelliJ IDEA*\bin\idea64.exe" } |
        Sort-Object FullName -Descending)
    if ($candidates.Count -eq 0) {
        throw "IntelliJ IDEA idea64.exe was not found. Pass -IdeaExe explicitly."
    }
    return $candidates[0].FullName
}

function Resolve-LatestPluginZip {
    $distDir = Join-Path $projectRoot "build\distributions"
    $candidates = @(Get-ChildItem -Path $distDir -Filter "MarkdownTableEditorIdea-*.zip" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending)
    if ($candidates.Count -eq 0) {
        throw "Plugin ZIP was not found under $distDir. Build it first or pass -PluginZip explicitly."
    }
    return $candidates[0].FullName
}

function Resolve-IdeaConfigDir([string]$ResolvedIdeaExe) {
    $installDir = Split-Path (Split-Path $ResolvedIdeaExe -Parent) -Parent
    $installName = Split-Path $installDir -Leaf
    if ($installName -match "IntelliJ IDEA (\d{4}\.\d+)") {
        $candidate = Join-Path $env:APPDATA "JetBrains\IntelliJIdea$($Matches[1])"
        if (Test-Path -LiteralPath $candidate) {
            return [IO.Path]::GetFullPath($candidate)
        }
    }

    $configs = @(Get-ChildItem -Path (Join-Path $env:APPDATA "JetBrains") -Directory -Filter "IntelliJIdea*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending)
    if ($configs.Count -eq 0) {
        throw "IntelliJ IDEA config directory was not found. Pass -ConfigDir explicitly."
    }
    return $configs[0].FullName
}

function Reset-Directory([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    if (-not $resolved.StartsWith($buildRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to reset path outside IDEA build dir: $resolved"
    }
    if (Test-Path -LiteralPath $resolved) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    New-Item -ItemType Directory -Path $resolved -Force | Out-Null
}

function Remove-Directory([string]$Path, [string]$AllowedRoot) {
    $resolved = [IO.Path]::GetFullPath($Path)
    $root = [IO.Path]::GetFullPath($AllowedRoot).TrimEnd('\', '/')
    if (-not $resolved.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -and
        -not $resolved.Equals($root, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove path outside allowed root: $resolved"
    }
    if (Test-Path -LiteralPath $resolved) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $encoding = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText($Path, $Text, $encoding)
}

function Read-Normalized([string]$Path) {
    return ([IO.File]::ReadAllText($Path) -replace "`r`n", "`n").TrimEnd("`r", "`n")
}

function Assert-FileContent([string]$Name, [string]$Expected) {
    $path = Join-Path $WorkDir $Name
    $actual = Read-Normalized $path
    $normalizedExpected = ($Expected -replace "`r`n", "`n").TrimEnd("`r", "`n")
    if ($actual -ne $normalizedExpected) {
        throw "${Name}: result mismatch.`nExpected:`n$normalizedExpected`nActual:`n$actual"
    }
}

function Install-PluginZip([string]$ZipPath, [string]$PluginsDir, [string]$PluginDir, [string]$StagingDir) {
    Reset-Directory $StagingDir
    Expand-Archive -LiteralPath $ZipPath -DestinationPath $StagingDir -Force
    $expandedPluginDir = Join-Path $StagingDir "MarkdownTableEditorIdea"
    if (-not (Test-Path -LiteralPath $expandedPluginDir)) {
        throw "Expanded plugin ZIP does not contain MarkdownTableEditorIdea/: $ZipPath"
    }

    Remove-Directory $PluginDir $PluginsDir
    Copy-Item -LiteralPath $expandedPluginDir -Destination $PluginsDir -Recurse -Force
}

if (-not $IdeaExe) {
    $IdeaExe = Resolve-LatestIdeaExe
}
if (-not $PluginZip) {
    $PluginZip = Resolve-LatestPluginZip
}
if (-not $WorkDir) {
    $WorkDir = Join-Path $buildRoot "idea-playback-smoke"
} elseif (-not [IO.Path]::IsPathRooted($WorkDir)) {
    $WorkDir = Join-Path $projectRoot $WorkDir
}

$IdeaExe = [IO.Path]::GetFullPath($IdeaExe)
$PluginZip = [IO.Path]::GetFullPath($PluginZip)
$WorkDir = [IO.Path]::GetFullPath($WorkDir)

if (-not (Test-Path -LiteralPath $IdeaExe)) {
    throw "IDEA executable not found: $IdeaExe"
}
if (-not (Test-Path -LiteralPath $PluginZip)) {
    throw "Plugin ZIP not found: $PluginZip"
}
if (-not $WorkDir.StartsWith($buildRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "WorkDir must stay under the IDEA build dir. Got: $WorkDir"
}
if (Get-Process idea64 -ErrorAction SilentlyContinue) {
    throw "Close IntelliJ IDEA before running playback smoke."
}

if (-not $ConfigDir) {
    $ConfigDir = Resolve-IdeaConfigDir $IdeaExe
}
$ConfigDir = [IO.Path]::GetFullPath($ConfigDir)
$pluginsDir = Join-Path $ConfigDir "plugins"
$pluginDir = Join-Path $pluginsDir "MarkdownTableEditorIdea"
$optionsDir = Join-Path $ConfigDir "options"
$settingsFile = Join-Path $optionsDir "markdownTableEditor.xml"
$backupDir = ""
$settingsBackupFile = ""
$settingsHadFile = $false

New-Item -ItemType Directory -Path $pluginsDir -Force | Out-Null
Reset-Directory $WorkDir

$stagingDir = Join-Path $WorkDir "plugin-staging"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (Test-Path -LiteralPath $pluginDir) {
    $backupDir = Join-Path $pluginsDir "MarkdownTableEditorIdea.bak-playback-$timestamp"
    Move-Item -LiteralPath $pluginDir -Destination $backupDir
}

$previousIdeaVmOptions = $env:IDEA_VM_OPTIONS
$process = $null
$restoreNeeded = -not $KeepInstalledPlugin

try {
    Install-PluginZip $PluginZip $pluginsDir $pluginDir $stagingDir

    New-Item -ItemType Directory -Path $optionsDir -Force | Out-Null
    if (Test-Path -LiteralPath $settingsFile) {
        $settingsHadFile = $true
        $settingsBackupFile = Join-Path $WorkDir "markdownTableEditor.xml.bak"
        Copy-Item -LiteralPath $settingsFile -Destination $settingsBackupFile -Force
    }
    Write-Utf8NoBom $settingsFile @"
<application>
  <component name="MarkdownTableEditorSettings">
    <option name="autoAlignEnabled" value="false" />
    <option name="autoFitEnabled" value="false" />
    <option name="debounceMs" value="160" />
  </component>
</application>
"@

    Write-Utf8NoBom (Join-Path $WorkDir "csv-current-block.md") "Name,Score`r`nAnna,10`r`n"
    Write-Utf8NoBom (Join-Path $WorkDir "tsv-current-block.md") "Name`tScore`r`nAnna`t10`r`n"
    Write-Utf8NoBom (Join-Path $WorkDir "sort-ascending.md") "| Name | Score |`r`n| ---- | ----- |`r`n| Bob | 2 |`r`n| Anna | 10 |`r`n"
    Write-Utf8NoBom (Join-Path $WorkDir "sort-descending.md") "| Name | Score |`r`n| ---- | ----- |`r`n| Anna | 10 |`r`n| Bob | 2 |`r`n"
    Write-Utf8NoBom (Join-Path $WorkDir "align-menu.md") "| N | Score |`r`n| ---- | ----- |`r`n| Anna | 10 |`r`n"
    Write-Utf8NoBom (Join-Path $WorkDir "tab-align-table.md") "| N | Score |`r`n| ---- | ----- |`r`n| Anna | 10 |`r`n"
    Write-Utf8NoBom (Join-Path $WorkDir "tab-plain-indent.md") "plain`r`n"

    $playbackScript = Join-Path $WorkDir "installed-idea-smoke.ijperf"
    Write-Utf8NoBom $playbackScript @"
%sleep 9000
%openFile csv-current-block.md
%assertCurrentFile csv-current-block.md
%moveCaret Anna
%executeEditorAction MarkdownTableEditor.ConvertCsvTsv
%sleep 1000
%saveDocumentsAndSettings
%sleep 500
%openFile tsv-current-block.md
%assertCurrentFile tsv-current-block.md
%moveCaret Anna
%executeEditorAction MarkdownTableEditor.ConvertCsvTsv
%sleep 1000
%saveDocumentsAndSettings
%sleep 500
%openFile sort-ascending.md
%assertCurrentFile sort-ascending.md
%moveCaret Bob
%executeEditorAction MarkdownTableEditor.SortAscending
%sleep 1000
%saveDocumentsAndSettings
%sleep 500
%openFile sort-descending.md
%assertCurrentFile sort-descending.md
%moveCaret Anna
%executeEditorAction MarkdownTableEditor.SortDescending
%sleep 1000
%saveDocumentsAndSettings
%sleep 500
%openFile align-menu.md
%assertCurrentFile align-menu.md
%moveCaret Anna
%executeEditorAction MarkdownTableEditor.Align
%sleep 1000
%saveDocumentsAndSettings
%sleep 500
%openFile tab-align-table.md
%assertCurrentFile tab-align-table.md
%moveCaret Anna
%pressKey TAB
%sleep 1000
%saveDocumentsAndSettings
%sleep 500
%openFile tab-plain-indent.md
%assertCurrentFile tab-plain-indent.md
%moveCaret plain
%pressKey TAB
%sleep 1000
%saveDocumentsAndSettings
%sleep 500
%exitApp
"@

    $vmOptionsPath = Join-Path $WorkDir "idea-playback.vmoptions"
    Write-Utf8NoBom $vmOptionsPath @"
-Xms128m
-Xmx2048m
-XX:ReservedCodeCacheSize=512m
-XX:+HeapDumpOnOutOfMemoryError
-XX:+IgnoreUnrecognizedVMOptions
-ea
-Dsun.io.useCanonCaches=false
-Dide.show.tips.on.startup.default.value=false
-Dtestscript.filename=$playbackScript
"@

    $env:IDEA_VM_OPTIONS = $vmOptionsPath
    Write-Host "Running IDEA playback smoke with $IdeaExe"
    Write-Host "Plugin ZIP: $PluginZip"
    $process = Start-Process -FilePath $IdeaExe -ArgumentList @($WorkDir) -WorkingDirectory $WorkDir -WindowStyle Normal -PassThru
    if (-not $process.WaitForExit($WaitForExitSeconds * 1000)) {
        try {
            $process.Kill()
            $process.WaitForExit()
        } catch {
        }
        throw "IDEA playback smoke timed out after $WaitForExitSeconds seconds."
    }
    if ($process.ExitCode -ne 0) {
        throw "IDEA playback smoke exited with code $($process.ExitCode)."
    }

    Assert-FileContent "csv-current-block.md" "| Name | Score |`n| ---- | ----- |`n| Anna | 10    |"
    Assert-FileContent "tsv-current-block.md" "| Name | Score |`n| ---- | ----- |`n| Anna | 10    |"
    Assert-FileContent "sort-ascending.md" "| Name | Score |`n| ---- | ----- |`n| Anna | 10    |`n| Bob  | 2     |"
    Assert-FileContent "sort-descending.md" "| Name | Score |`n| ---- | ----- |`n| Bob  | 2     |`n| Anna | 10    |"
    Assert-FileContent "align-menu.md" "| N    | Score |`n| ---- | ----- |`n| Anna | 10    |"
    Assert-FileContent "tab-align-table.md" "| N    | Score |`n| ---- | ----- |`n| Anna | 10    |"
    Assert-FileContent "tab-plain-indent.md" "    plain"

    Write-Host "IDEA playback smoke passed"
}
finally {
    if ($null -ne $previousIdeaVmOptions) {
        $env:IDEA_VM_OPTIONS = $previousIdeaVmOptions
    } else {
        Remove-Item Env:\IDEA_VM_OPTIONS -ErrorAction SilentlyContinue
    }

    if ($restoreNeeded) {
        Remove-Directory $pluginDir $pluginsDir
        if ($backupDir -and (Test-Path -LiteralPath $backupDir)) {
            Move-Item -LiteralPath $backupDir -Destination $pluginDir
        }
    }

    if ($settingsHadFile -and $settingsBackupFile -and (Test-Path -LiteralPath $settingsBackupFile)) {
        Copy-Item -LiteralPath $settingsBackupFile -Destination $settingsFile -Force
    } elseif (-not $settingsHadFile -and (Test-Path -LiteralPath $settingsFile)) {
        Remove-Item -LiteralPath $settingsFile -Force
    }
}
