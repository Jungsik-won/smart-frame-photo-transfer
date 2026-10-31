$ErrorActionPreference = 'Stop'
$folder = Join-Path $PSScriptRoot 'test-deps'
$jar = Join-Path $folder 'json-20240303.jar'
$expected = '3CF6CD6892E32E2B4C1C39E0F52F5248A2F5B37646FDFBB79A66B46B618414ED'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
if (-not (Test-Path -LiteralPath $jar)) {
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar' -OutFile $jar
}
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $expected) {
    throw 'JSON test library checksum mismatch. Do not use this file.'
}
Write-Output 'Test-only org.json 20240303 dependency verified. It is not bundled in either APK.'
