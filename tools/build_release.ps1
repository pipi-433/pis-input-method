param(
    [string]$Version = '2.0.2',
    [string]$Python = 'python',
    [string]$OutputDirectory = '',
    [string]$CommitHash = ''
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$signing = Join-Path $repo '.pis-local/signing'
$keyFile = Join-Path $signing 'pis-release.jks'
$credentials = Join-Path $signing 'credentials.xml'
if (!(Test-Path -LiteralPath $keyFile) -or !(Test-Path -LiteralPath $credentials)) {
    throw 'Release signing material is missing. Restore the existing release key; never replace it for an update.'
}
if (!$env:JAVA_HOME -or !$env:ANDROID_HOME) { throw 'Set JAVA_HOME and ANDROID_HOME first.' }
if (!$OutputDirectory) { $OutputDirectory = Join-Path $repo ".pis-local/releases/$Version" }
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Use a numeric major.minor.patch version.' }
if ($CommitHash -and $CommitHash -notmatch '^[0-9a-fA-F]{7,40}$') { throw 'Use a Git commit hash.' }
$oldFile = $env:SIGN_KEY_FILE; $oldPwd = $env:SIGN_KEY_PWD; $oldAlias = $env:SIGN_KEY_ALIAS
try {
    $credential = Import-Clixml -LiteralPath $credentials
    $env:SIGN_KEY_FILE = $keyFile
    $env:SIGN_KEY_ALIAS = $credential.UserName
    $env:SIGN_KEY_PWD = $credential.GetNetworkCredential().Password
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    Push-Location $repo
    try {
        $gradleArguments = @('--no-daemon', '-Pkotlin.incremental=false', "-PpythonExecutable=$Python", '-PbuildABI=arm64-v8a', "-PbuildVersionName=$Version")
        if ($CommitHash) { $gradleArguments += "-PbuildCommitHash=$CommitHash" }
        & ./gradlew.bat @gradleArguments :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
        & $Python tools/check_fcitx_api.py --classes app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes
        if ($LASTEXITCODE -ne 0) { throw 'Release FcitxAPI delegate verification failed.' }
        $apkFiles = @(Get-ChildItem app/build/outputs/apk/release -Filter '*.apk' | Where-Object { $_.Name -match 'arm64-v8a' -and $_.Name -notmatch 'unsigned' })
        if ($apkFiles.Count -ne 1) { throw 'Expected exactly one signed ARM64 APK.' }
        $destination = Join-Path $OutputDirectory "pis-input-method-$Version-arm64.apk"
        Copy-Item -LiteralPath $apkFiles[0].FullName -Destination $destination
        & $Python tools/audit_pis_apk.py --apk $destination --release
        if ($LASTEXITCODE -ne 0) { throw 'Package audit failed.' }
        Copy-Item verification/evidence/phone-apk-audit.json (Join-Path $OutputDirectory 'package-audit.json')
        $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $(Split-Path -Leaf $destination)" | Set-Content (Join-Path $OutputDirectory 'SHA256SUMS.txt') -Encoding ascii
        $diagnostics = Join-Path $repo ".pis-local/releases/$Version/diagnostics"
        New-Item -ItemType Directory -Path $diagnostics -Force | Out-Null
        foreach ($name in @('mapping.txt', 'configuration.txt', 'usage.txt')) {
            $path = Join-Path $repo "app/build/outputs/mapping/release/$name"
            if (Test-Path -LiteralPath $path) { Copy-Item -LiteralPath $path -Destination $diagnostics }
        }
        Write-Output "Release artifacts: $OutputDirectory"
    } finally { Pop-Location }
} finally {
    $env:SIGN_KEY_FILE = $oldFile; $env:SIGN_KEY_PWD = $oldPwd; $env:SIGN_KEY_ALIAS = $oldAlias
}
