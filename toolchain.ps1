# Environment variables allow a clean checkout to use a standard Android SDK.
# Existing local development tools remain a fallback and are never published.
$workspace = Split-Path $PSScriptRoot
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Join-Path $workspace 'tools\jdk17\jdk-17.0.20.1+1' }
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if ($sdkRoot) {
    $sdkBuildVersion = if ($env:SMARTFRAME_BUILD_TOOLS) { $env:SMARTFRAME_BUILD_TOOLS } else { '34.0.0' }
    $buildTools = Join-Path $sdkRoot "build-tools\$sdkBuildVersion"
    $androidJar = Join-Path $sdkRoot 'platforms\android-29\android.jar'
} else {
    $buildTools = Join-Path $workspace 'tools\build-tools\android-14'
    $androidJar = Join-Path $workspace 'tools\platform29\android-10\android.jar'
}
if (-not (Test-Path -LiteralPath (Join-Path $jdk 'bin\javac.exe'))) {
    throw 'JDK not found. Set JAVA_HOME to a JDK 17 installation; see docs/BUILD.md.'
}
