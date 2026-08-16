param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$escaped = [regex]::Escape($Version)
$checks = @(
    @{ Path = "apps/windows/build.gradle.kts"; Pattern = "version\s*=\s*`"$escaped`"" },
    @{ Path = "apps/windows/src/main/kotlin/com/syncdows/app/platform/UpdateConfig.kt"; Pattern = "CURRENT_VERSION\s*=\s*`"$escaped`"" },
    @{ Path = "apps/macos/build.gradle.kts"; Pattern = "version\s*=\s*`"$escaped`"" },
    @{ Path = "apps/macos/src/main/kotlin/com/synctosh/app/platform/UpdateConfig.kt"; Pattern = "CURRENT_VERSION\s*=\s*`"$escaped`"" },
    @{ Path = "apps/android/app/build.gradle.kts"; Pattern = "versionName\s*=\s*`"$escaped`"" }
)

foreach ($check in $checks) {
    $path = Join-Path $root $check.Path
    if ((Get-Content -Raw -LiteralPath $path) -notmatch $check.Pattern) {
        throw "$($check.Path) does not declare release version $Version"
    }
}

$parts = $Version.Split('.')
$nativeVersion = "1.$($parts[1]).$($parts[2])"
foreach ($relativePath in @("apps/windows/build.gradle.kts", "apps/macos/build.gradle.kts")) {
    $path = Join-Path $root $relativePath
    if ((Get-Content -Raw -LiteralPath $path) -notmatch "packageVersion\s*=\s*`"$([regex]::Escape($nativeVersion))`"") {
        throw "$relativePath must use jpackage version $nativeVersion for release $Version"
    }
}

Write-Host "All platform versions match $Version"
