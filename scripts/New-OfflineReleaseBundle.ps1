param(
    [Parameter(Mandatory = $true)]
    [string]$AssetDirectory,

    [string]$PrivateKeyPath = (Join-Path ([Environment]::GetFolderPath("UserProfile")) ".syncdroid\release-signing-private.pem"),

    [string]$OutputPath
)

$ErrorActionPreference = "Stop"

$assetRoot = (Resolve-Path -LiteralPath $AssetDirectory).Path
$privateKey = (Resolve-Path -LiteralPath $PrivateKeyPath).Path
$manifestPath = Join-Path $assetRoot "syncdroid-update.properties"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Missing release manifest: $manifestPath"
}

$properties = ConvertFrom-StringData (Get-Content -LiteralPath $manifestPath -Raw)
$version = $properties.version
if ($version -notmatch '^\d+\.\d+\.\d+$') {
    throw "The release manifest has an invalid stable version"
}

$releaseAssets = @(
    @{ Prefix = 'asset.android'; Name = $properties.'asset.android.file' }
    @{ Prefix = 'asset.macos-arm64'; Name = $properties.'asset.macos-arm64.file' }
    @{ Prefix = 'asset.windows-x64'; Name = $properties.'asset.windows-x64.file' }
)
foreach ($releaseAsset in $releaseAssets) {
    $assetName = $releaseAsset.Name
    if ([string]::IsNullOrWhiteSpace($assetName) -or -not (Test-Path -LiteralPath (Join-Path $assetRoot $assetName) -PathType Leaf)) {
        throw "The release manifest names a missing asset: $assetName"
    }
    $assetPath = Join-Path $assetRoot $assetName
    $expectedSize = [long]$properties.($releaseAsset.Prefix + '.size')
    $expectedHash = $properties.($releaseAsset.Prefix + '.sha256')
    $actualSize = (Get-Item -LiteralPath $assetPath).Length
    $actualHash = (Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSize -ne $expectedSize -or $actualHash -ne $expectedHash) {
        throw "$assetName does not match its manifest size or SHA-256"
    }
}
$assetNames = $releaseAssets.Name

$opensslCommand = Get-Command openssl -ErrorAction SilentlyContinue
$opensslCandidates = @(
    $(if ($null -ne $opensslCommand) { $opensslCommand.Source }),
    (Join-Path $env:ProgramFiles "Git\usr\bin\openssl.exe")
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path -LiteralPath $_ -PathType Leaf) }
$openssl = $opensslCandidates | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($openssl)) {
    throw "OpenSSL was not found. Install Git for Windows or add openssl to PATH."
}

$signatureBinary = Join-Path ([System.IO.Path]::GetTempPath()) ("syncdroid-signature-" + [Guid]::NewGuid().ToString("N") + ".bin")
& $openssl dgst -sha256 -sign $privateKey -out $signatureBinary $manifestPath
if ($LASTEXITCODE -ne 0) {
    throw "OpenSSL failed to sign the release manifest"
}
$signaturePath = Join-Path $assetRoot "syncdroid-update.properties.sig"
[System.IO.File]::WriteAllText($signaturePath, [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($signatureBinary)))
Remove-Item -LiteralPath $signatureBinary -Force

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $assetRoot "SyncDroid-Mesh-$version-offline.sdu"
}
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$temporaryZip = [System.IO.Path]::ChangeExtension($resolvedOutput, ".zip")
if ($temporaryZip -eq $resolvedOutput) {
    $temporaryZip = "$resolvedOutput.tmp.zip"
}

Remove-Item -LiteralPath $temporaryZip -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $resolvedOutput -Force -ErrorAction SilentlyContinue
$bundleFiles = @($manifestPath, $signaturePath) + @($assetNames | ForEach-Object { Join-Path $assetRoot $_ })
Compress-Archive -LiteralPath $bundleFiles -DestinationPath $temporaryZip -CompressionLevel Optimal
Move-Item -LiteralPath $temporaryZip -Destination $resolvedOutput

Write-Host "Signed offline update bundle created: $resolvedOutput"
