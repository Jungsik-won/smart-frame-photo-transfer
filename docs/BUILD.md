# Build from source · 직접 빌드

[한국어 소개](../README.md) · [English overview](../README.en.md)

These are native Java apps built without Gradle. The supplied scripts target **Windows PowerShell / PowerShell on Windows**. App packages and runtime behavior are unchanged by the build environment configuration.

Gradle 없이 Java와 Android SDK 도구로 빌드합니다. 아래 스크립트는 **Windows**용입니다.

## 1. Toolchain / 개발 환경

Install these dependencies separately; they are not included in this repository:

| Dependency | Version / purpose |
| --- | --- |
| JDK | 17; `java`, `javac`, `jar`, `keytool` |
| Android SDK Platform | `platforms;android-29` |
| Android SDK Build Tools | `build-tools;34.0.0` by default |
| Python | 3, for SQLite scenario tests |

Use Android's [SDK Manager](https://developer.android.com/tools/sdkmanager) or Android Studio's SDK Manager to install the platform and build tools. Using a newer build-tools directory is possible via `SMARTFRAME_BUILD_TOOLS`, but has not been validated here.

Set environment variables in the current PowerShell session. Replace the example paths with your actual installation paths:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_SDK_ROOT = 'C:\path\to\Android\Sdk'
$env:SMARTFRAME_BUILD_TOOLS = '34.0.0'
```

`ANDROID_HOME` is also accepted when `ANDROID_SDK_ROOT` is unset. If neither SDK variable nor `JAVA_HOME` is set, scripts retain a fallback for the original developer's sibling `tools/` directory. A clean checkout should use the environment variables above.

환경 변수는 SDK·JDK의 실제 설치 경로로 바꾸세요. 개발자가 사용하던 로컬 도구와 개인 경로는 배포하지 않습니다.

## 2. Build / 빌드

From the repository root:

```powershell
.\build.ps1
```

The script compiles resources with AAPT2, compiles Java, converts classes with D8, aligns APKs, signs them, and verifies the signatures.

| Output | Package | Minimum / target SDK |
| --- | --- | --- |
| `build/smart-frame-sender.apk` | `local.smartframe.sender` | 26 / 29 |
| `build/smart-frame-receiver.apk` | `local.smartframe.receiver` | 29 / 29 |

These APKs are intended for direct installation. This is not a statement of current Play Store submission eligibility.

### Signing / 서명 키

On the first build, a private signing key and password file are generated under `signing/`. Keep them privately backed up for future updates. **Never commit or upload this directory.**

A local build uses your own key and cannot update the publisher's APK in place. Android requires a matching signing identity for an update. Do not uninstall or clear data without understanding the loss of pairing/settings/history.

첫 빌드의 `signing/`에 개인 서명 키가 생성됩니다. 이후 업데이트에도 같은 키가 필요하므로 비공개로 보관하세요. 공개 릴리스의 서명 키는 저장소에 포함하지 않습니다.

## 3. Tests / 검사

```powershell
# Pure Java protocol, staging, selection, and deletion logic
.\test.ps1

# Actual shared-intent parser against minimal Android host stubs
.\test-share.ps1

# Production SQL exercised with SQLite
python .\tests\StorageMigrationTest.py
python .\tests\SenderQueueTest.py

# Local TLS/API fixture; build first
.\fetch-test-deps.ps1
.\test-api.ps1
```

`fetch-test-deps.ps1` downloads pinned `org.json:json:20240303` from Maven Central and validates its SHA-256. This library is used only by host tests; Android's platform JSON implementation is used in the apps.

TLS integration tests listen only on the local machine and create disposable fixtures under `build/api-tests/`. They do not contact a real frame or delete real device photos. If a fixture certificate has expired, remove only the generated `build/api-tests/fixture.jks` and rerun that test to regenerate it.

자동 검사는 실제 액자 사진에 접근하지 않습니다. Android 권한, 저장소 및 화면 동작의 실기 검증은 별도로 필요합니다. See [testing status](TESTING.md).

## 4. Repository map / 파일 구성

```text
sender/                 Phone app: gallery, Share, queue, frame-photo management
receiver/               Frame app: pairing, HTTPS, storage, boot receiver
common/                 UI helpers, wire protocol, staging and shared logic
tests/                  Java fixtures, Android host stubs, SQLite scenarios
docs/                   Korean / English guides and project artwork
build.ps1               Compile, package, sign, and verify both apps
toolchain.ps1           Resolve local JDK and Android SDK paths
```

## 5. Release hygiene / 공개 시 제외할 파일

Publish only reviewed original source, documentation, and the intended signed APK artifacts. Exclude `signing/`, device backups, private logs/databases, original or decompiled third-party APKs, SDK/JDK bundles, and local investigation notes. `.gitignore` provides defaults; review the actual file list before each publication.

MIT covers this project's original code and documentation. Android SDK/JDK and test dependencies retain their own licenses. No third-party APK or firmware is distributed here.
