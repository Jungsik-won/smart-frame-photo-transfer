$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
. (Join-Path $project 'toolchain.ps1')
foreach ($required in @($androidJar, (Join-Path $buildTools 'aapt2.exe'), (Join-Path $buildTools 'zipalign.exe'), (Join-Path $buildTools 'lib\d8.jar'), (Join-Path $buildTools 'lib\apksigner.jar'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Android SDK tool not found: $required. See docs/BUILD.md." }
}
$taskTemp = Join-Path $project 'build\tmp'
New-Item -ItemType Directory -Force -Path $taskTemp | Out-Null
function Invoke-Checked([string]$tool, [string[]]$arguments) {
    & $tool @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build command failed: $tool ($LASTEXITCODE)" }
}
$signing = Join-Path $project 'signing'
New-Item -ItemType Directory -Force -Path $signing | Out-Null
$key = Join-Path $signing 'transfer.jks'
$pass = Join-Path $signing 'password.txt'
if (-not (Test-Path -LiteralPath $key)) {
    if (-not (Test-Path -LiteralPath $pass)) { [guid]::NewGuid().ToString('N') | Set-Content -Encoding ascii -LiteralPath $pass }
    Invoke-Checked "$jdk\bin\keytool.exe" @('-genkeypair','-keystore',$key,'-storepass:file',$pass,'-keypass:file',$pass,'-alias','transfer','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=Smart Frame Photo Transfer')
}
foreach ($app in @('receiver','sender')) {
    $src = Join-Path $project $app
    $out = Join-Path $project "build\$app"
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    foreach ($leaf in @('generated','classes','dex')) {
        $target = [IO.Path]::GetFullPath((Join-Path $out $leaf))
        $allowed = [IO.Path]::GetFullPath((Join-Path $project 'build')) + [IO.Path]::DirectorySeparatorChar
        if (-not $target.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)) { throw 'Build directory escaped workspace' }
        if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
        New-Item -ItemType Directory -Path $target | Out-Null
    }
    Invoke-Checked "$buildTools\aapt2.exe" @('compile','--dir',"$src\res",'-o',"$out\compiled.zip")
    Invoke-Checked "$buildTools\aapt2.exe" @('link','-o',"$out\unsigned.apk",'--manifest',"$src\AndroidManifest.xml",'-I',$androidJar,'--java',"$out\generated","$out\compiled.zip")
    $sources = @(Get-ChildItem "$src\src","$project\common\src","$out\generated" -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
    Invoke-Checked "$jdk\bin\javac.exe" (@('-encoding','UTF-8','--release','8','-classpath',$androidJar,'-d',"$out\classes") + $sources)
    Invoke-Checked "$jdk\bin\jar.exe" @("-J-Djava.io.tmpdir=$taskTemp",'cf',"$out\classes.jar",'-C',"$out\classes",'.')
    $minApi = if ($app -eq 'receiver') { '29' } else { '26' }
    Invoke-Checked "$jdk\bin\java.exe" @('-cp',"$buildTools\lib\d8.jar",'com.android.tools.r8.D8','--min-api',$minApi,'--lib',$androidJar,'--output',"$out\dex","$out\classes.jar")
    Invoke-Checked "$jdk\bin\jar.exe" @("-J-Djava.io.tmpdir=$taskTemp",'uf',"$out\unsigned.apk",'-C',"$out\dex",'classes.dex')
    Invoke-Checked "$buildTools\zipalign.exe" @('-f','4',"$out\unsigned.apk","$out\aligned.apk")
    $apk = Join-Path $project "build\smart-frame-$app.apk"
    Invoke-Checked "$jdk\bin\java.exe" @('-jar',"$buildTools\lib\apksigner.jar",'sign','--ks',$key,'--ks-pass',"file:$pass",'--out',$apk,"$out\aligned.apk")
    Invoke-Checked "$jdk\bin\java.exe" @('-jar',"$buildTools\lib\apksigner.jar",'verify','--verbose',$apk)
    Get-FileHash -LiteralPath $apk -Algorithm SHA256
}
