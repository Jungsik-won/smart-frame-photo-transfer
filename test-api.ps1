$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
. (Join-Path $project 'toolchain.ps1')
$jdk = Join-Path $jdk 'bin'
$out = Join-Path $project 'build\api-tests'
$json = Join-Path $project 'test-deps\json-20240303.jar'
if ((Get-FileHash $json -Algorithm SHA256).Hash -ne '3CF6CD6892E32E2B4C1C39E0F52F5248A2F5B37646FDFBB79A66B46B618414ED') { throw 'Unexpected JSON test library checksum' }
New-Item -ItemType Directory -Force -Path $out | Out-Null
$key = Join-Path $out 'fixture.jks'
if (-not (Test-Path -LiteralPath $key)) {
    & "$jdk\keytool.exe" -genkeypair -keystore $key -storetype JKS -storepass local-test-only -keypass local-test-only -alias test -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=Local Integration Fixture'
    if ($LASTEXITCODE -ne 0) { throw 'TLS fixture creation failed' }
}
$classpath = "$project\build\sender\classes;$json;$androidJar"
& "$jdk\javac.exe" -encoding UTF-8 --release 8 -cp $classpath -d $out "$project\common\src\local\smartframe\transfer\core\FrameKeyManager.java" "$project\sender\src\local\smartframe\sender\Api.java" "$project\tests\ApiIntegrationTest.java" "$project\tests\TlsEngineTest.java"
if ($LASTEXITCODE -ne 0) { throw 'API test compilation failed' }
& "$jdk\java.exe" -cp "$out;$classpath" TlsEngineTest $key
if ($LASTEXITCODE -ne 0) { throw 'Engine handshake regression test failed' }
& "$jdk\java.exe" -cp "$out;$classpath" local.smartframe.sender.ApiIntegrationTest $key $out
if ($LASTEXITCODE -ne 0) { throw 'API integration test failed' }
