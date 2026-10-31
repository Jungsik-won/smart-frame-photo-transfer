# Contributing · 참여 안내

Bug reports and small, focused patches are welcome. Please read [testing status](docs/TESTING.md) before describing an untested feature as a regression.

버그 제보와 작은 단위의 개선을 환영합니다. [검증 현황](docs/TESTING.md)에서 아직 실기 확인이 필요한 기능을 먼저 확인해 주세요.

## Report an issue / 문제 제보

Include sender/receiver versions, Android versions, device model, the steps you followed, the expected result, and the actual error text. For storage issues, specify internal storage, USB, or SD and whether Android recognizes it.

앱 버전·Android 버전·기기 모델·재현 순서·예상 결과·오류 문구를 적어주세요. 저장소 문제라면 내부/USB/SD 종류와 Android 인식 여부도 알려주세요.

**Do not post** personal photos, pairing codes, access tokens, private keys, or unredacted logs/screenshots. Redact actual network addresses and identifying details. For a suspected security issue, avoid posting working credentials or private data; describe the affected component and impact without them.

## Propose a change / 코드 변경

- Keep phone originals untouched and preserve the distinction between clearing a queue and deleting frame photos.
- Preserve TLS certificate verification, pairing checks, selected-volume isolation, and bounded input handling.
- Do not silently fall back to internal storage when the selected external volume is missing.
- Explain the behavior change, run the relevant [tests](docs/BUILD.md), and state any device tests you actually performed.
- Update Korean and English documentation when user-facing behavior changes. Current app labels are Korean.
- Never commit signing keys, generated builds, third-party APKs, or device backups.

Contributions are made under the project's [MIT License](LICENSE). Please submit only work you have the right to contribute.
