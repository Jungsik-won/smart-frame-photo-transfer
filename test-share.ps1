$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'toolchain.ps1')
$jdk = Join-Path $jdk 'bin'
$out = Join-Path $PSScriptRoot 'build\share-tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sources = @(Get-ChildItem "$PSScriptRoot\tests\share-stubs" -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }) + @("$PSScriptRoot\sender\src\local\smartframe\sender\SharedPhotos.java", "$PSScriptRoot\tests\SharedPhotosTest.java")
& "$jdk\javac.exe" -encoding UTF-8 --release 8 -d $out @sources
if ($LASTEXITCODE -ne 0) { throw 'Share test compilation failed' }
& "$jdk\java.exe" -cp $out local.smartframe.sender.SharedPhotosTest
if ($LASTEXITCODE -ne 0) { throw 'Share tests failed' }
