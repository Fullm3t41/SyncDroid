$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot
& .\gradlew.bat --no-daemon clean test packageExe packageMsi

$releaseDirectory = Join-Path $PSScriptRoot "build\release"
New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
Copy-Item "build\compose\binaries\main\exe\*.exe" (Join-Path $releaseDirectory "SyncDows-0.1.0.exe") -Force
Copy-Item "build\compose\binaries\main\msi\*.msi" (Join-Path $releaseDirectory "SyncDows-0.1.0.msi") -Force

Write-Host "SyncDows installers are ready in $releaseDirectory"
