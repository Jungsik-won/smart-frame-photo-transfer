# Testing status · 검증 현황

[한국어 소개](../README.md) · [English overview](../README.en.md)

Status as of **2026-10-31**. Sender **0.5.0**, receiver **0.1.5**. This is an early public release, not a claim of compatibility with every Android frame.

## Automated evidence / 자동 검사

| Check | Result | What it covers |
| --- | --- | --- |
| `WireTest` | 16 passed | HTTP framing, limits, hashing, integrity handling |
| `IncomingPhotoTest` | 15 passed | Staging, duplicate handling, publication, cleanup |
| `PhotoSelectionTest` | 9 passed | Selection, deselection, capacity, restoration |
| `PhotoDeletionTest` | 9 passed | Batch validation, deduplication, storage mismatch, partial results |
| `SharedPhotosTest` | 14 passed | Actual intent parser with host stubs; single/multiple/ClipData, duplicates, limits, invalid inputs |
| `StorageMigrationTest.py` | 6 passed | Production SQL migration, rollback, volume isolation |
| `SenderQueueTest.py` | Passed | 200 completed photos, archive/history, reselection, other-frame failures, deletion status |
| `TlsEngineTest` | Passed | Server-side TLS engine key-manager regression |
| `ApiIntegrationTest` | 21 passed | Actual sender API against local TLS fixtures, authentication, thumbnails, deletion, phone fixture preservation |
| APK packaging | Passed | Compilation, version metadata and APK signature verification |

Host stubs do **not** emulate Android URI permission grants or Activity lifecycle behavior. Local TLS fixtures do **not** prove Android MediaStore or real flash-drive compatibility.

호스트 대역·PC 로컬 통합 검사는 안드로이드 권한과 실제 저장장치 동작을 대신 검증하지 않습니다.

## Device observations / 실제 기기에서 확인한 범위

- Development hardware: **Lenovo Smart Frame CD-3L501F, Android 10**.
- Receiver self-test passed after the TLS key authorization fix in 0.1.2.
- PC-to-frame TLS 1.2 connection and public information request succeeded.
- The user confirmed successful phone-to-frame connection.

These observations establish connectivity on one device, not complete verification of all newer features.

## Still to verify / 실기 확인이 필요한 항목

- Sender 0.5.0 in Samsung Gallery / Google Photos share sheets, including URI grants, reopening an existing app, rotation, and process recreation.
- Sender's large-text layout, photo grid, permissions, and long photo lists on real phones.
- External USB/SD selection, actual file placement, removal/reconnection, and read-only storage behavior.
- Frame-photo thumbnails and deletion through Android MediaStore; phone originals remain untouched in host fixtures, but real-device UI flows still require testing.
- Fotoo refresh after receiving or deleting photos; receiver's Fotoo launch fallback across versions.
- Receiver startup after reboot under Lenovo background restrictions; interaction with the official home app and Fotoo's own boot option.
- Concurrent use from multiple physical phones and other Android device models.

실기 테스트가 끝나지 않은 항목은 정상 동작을 보장하지 않습니다. 첫 사용은 별도로 보관한 소수의 시험 사진으로 확인하는 것이 좋습니다.

## Release files / 배포 파일

Signed APKs and checksums are attached to the GitHub release. Versioned receiver 0.1.5 is retained from the preceding verified build; the documentation/build-path update does not change its app source. Rebuilding the same source is not guaranteed to produce byte-identical APKs.

Never upload device identity, pairing tokens, certificate private keys, personal photos, or unredacted device logs as test evidence.
