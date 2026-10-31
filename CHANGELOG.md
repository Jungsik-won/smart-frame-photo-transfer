# Changelog · 변경 기록

The sender and receiver have independent version numbers. / 송신·수신 앱은 버전을 별도로 관리합니다.

## Public preview — sender 0.5.0 / receiver 0.1.5 · 2026-10-31

- First public source release with Korean and English documentation, MIT license, and installation APKs.
- 최초 공개: 한국어·영어 문서, MIT 라이선스, 설치 APK 제공.

### Sender 0.5.0

- Receive one or multiple images through Android's Share menu.
- Add shared photos to the existing queue; users confirm the destination before sending.
- Handle `EXTRA_STREAM` and `ClipData`, deduplicate URIs, and enforce a 200-photo share limit.
- 갤러리의 공유 메뉴에서 사진을 받아 기존 전송 목록에 추가. 받을 액자를 확인한 뒤 전송.

### Sender 0.4.0 / receiver 0.1.5

- Remove confirmed completed photos from the home queue while retaining paginated history.
- Clean up completed sender staging copies; preserve phone originals.
- Browse thumbnails and delete app-managed photos from the selected frame storage, with explicit confirmation.
- 완료 사진 목록 정리, 50건씩 기록 조회, 액자 사진 선택 삭제 기능 추가.

### Sender 0.3.0

- Built-in gallery with tap-to-select photos, large selection indicators, and up to 200 queued photos.
- 길게 누르지 않고 톡톡 눌러 여러 장을 고르는 갤러리 추가.

### Sender 0.2.0

- Larger buttons and a simplified home screen. Connection, history, and storage checks moved into Settings.
- 큰 버튼과 단순한 첫 화면. 연결·기록·저장 확인은 설정으로 이동.

### Receiver 0.1.4

- Select internal, USB, or SD shared image storage; stage on the selected volume and create `Pictures/SmartFrame/` on first upload.
- 내부·USB·SD 저장 위치 선택, 같은 볼륨에 임시 저장, 첫 전송 시 폴더 자동 생성.

### Receiver 0.1.3

- Improve Fotoo launch fallback and distinguish launch failure from an absent app.
- Fotoo 실행 경로 보완 및 실패 안내 개선.

### Receiver 0.1.2

- Fix AndroidKeyStore TLS signing/decryption authorizations and add an actual loopback TLS self-test.
- TLS 키 권한 수정과 자체 검사 추가. 이전 키에서 변경 시 새 휴대폰 등록 필요.

### Receiver 0.1.1 / initial 0.1.0

- TLS engine key-manager fix following the initial local pairing, discovery, upload, and duplicate-check implementation.
- 초기 로컬 검색·연결·업로드·중복 확인 기능과 TLS 엔진 인증서 선택 수정.
