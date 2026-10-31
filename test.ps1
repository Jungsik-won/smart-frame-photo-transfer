$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'toolchain.ps1')
$jdk = Join-Path $jdk 'bin'
$out = Join-Path $PSScriptRoot 'build\tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sources = @(Get-ChildItem "$PSScriptRoot\common\src\local\smartframe\transfer\core" -Filter '*.java' | ForEach-Object { $_.FullName }) + @("$PSScriptRoot\tests\WireTest.java", "$PSScriptRoot\tests\IncomingPhotoTest.java", "$PSScriptRoot\tests\PhotoSelectionTest.java", "$PSScriptRoot\tests\PhotoDeletionTest.java")
& "$jdk\javac.exe" -encoding UTF-8 --release 8 -d $out @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$jdk\java.exe" -cp $out WireTest
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
& "$jdk\java.exe" -cp $out IncomingPhotoTest $out
if ($LASTEXITCODE -ne 0) { throw 'Incoming-photo tests failed' }
& "$jdk\java.exe" -cp $out PhotoSelectionTest
if ($LASTEXITCODE -ne 0) { throw 'Gallery selection tests failed' }
& "$jdk\java.exe" -cp $out PhotoDeletionTest
if ($LASTEXITCODE -ne 0) { throw 'Remote deletion tests failed' }
