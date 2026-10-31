# From your phone to your frame

[← Project overview](../README.en.md) · [한국어](GUIDE.ko.md) · **English**

> Install the **sender on the phone** and the **receiver on the frame**.
> **Fotoo is a separate app** that displays the received photos. The current app UI is Korean; relevant labels are translated below.

## 1. Before you start

| You need | Requirement |
| --- | --- |
| Android phone | Android 8.0 or later |
| Android frame | Android 10 or later, able to install APKs |
| Wi-Fi | Both devices on a local network that allows communication between clients |
| Photo viewer | Fotoo or another app that reads shared image storage |
| Optional USB/SD | Recognized by Android as writable shared storage |

You must be able to read the pairing code and verification text on the frame during setup. This project does not replace device activation or Lenovo account setup. You do not need to factory-reset a device to install these apps.

### Download and install

Get both APKs from [GitHub Releases](https://github.com/Jungsik-won/smart-frame-photo-transfer/releases):

- **Phone:** `smart-frame-sender-0.5.0.apk`
- **Frame:** `smart-frame-receiver-0.1.5.apk`

Android may show installation-permission or Play Protect scanning prompts. Verify the source and follow Android's normal installation flow; the app cannot hide these prompts.

Existing users should update over the installed app without uninstalling. An APK you build with a different signing key cannot update the published APK in place. Clearing app data to resolve this can remove pairing, settings, and history.

## 2. Prepare the frame

1. Open the receiver. Check that it says **사진 받을 준비가 됐어요** (Ready to receive) and that the self-test passes.
2. Tap **사진 저장소 선택 (내부 / USB / SD)** (Select storage) and choose a location.
3. Use its displayed **address**, **connection code**, and **verification text** to register the phone.
4. If battery restrictions interrupt receiving, open **배터리 제한 설정 열기** (Battery restriction settings) and allow the receiver to run.

The receiving service is implemented to start after boot once the app has been opened. Manufacturer restrictions or force-stop behavior can prevent this; verify on your actual device.

### Using USB or SD storage

- If a device is missing from the list, check whether Android recognizes it first.
- `Pictures/SmartFrame/` is created on the selected volume **when the first photo arrives**.
- Temporary validation files are also staged on that volume and cleaned up after success or handled errors.
- Allow roughly twice the incoming photo size plus spare space during transfer. The final photo remains after completion. If abnormal termination leaves temporary files, the service cleans up stale files on attached volumes at its next start.
- Disconnecting the selected volume causes transfers to fail. There is no automatic fallback to internal storage.
- Changing storage does not move or delete existing photos. Finish active transfers before switching.

## 3. Pair the phone

1. Open **⚙ 설정 → 액자 연결 · 변경** (Settings → Connect/change frame) in the sender.
2. Discover the frame, or manually enter the address displayed by the receiver.
3. Compare the **connection verification text** on both screens.
4. Enter the frame's **six-digit connection code** to complete pairing.

Codes expire after ten minutes and rotate after successful pairing. Use the current code; repeated incorrect attempts cause a temporary block.

Repeat these steps for another phone. The receiver stores up to 20 phone credentials. Transfer history is local to each phone. After revoking a connection, pair again with a new code.

## 4. Send photos

### Option A — from your usual gallery

**Select photos → Share → 액자에 사진 보내기 → check the frame → 사진 보내기 (Send photos)**

Share one or multiple images. If the app is missing, expand the share sheet's additional apps. Shared images are appended to the current queue, so review any photos you selected earlier before sending.

Cloud-only photos work only if the gallery supplies readable image data. If it shares only a link or cannot provide the file, download the photo to the phone and share again.

### Option B — inside the sender

1. Tap **＋ 사진 고르기** (Choose photos).
2. **Tap once** to select each photo, and tap again to deselect. No long press is needed.
3. Tap **선택한 사진 N장 추가** (Add N selected photos), then **사진 보내기** (Send photos).

The built-in gallery requires photo access and lists the phone's shared images. Cloud-only photos and other apps' private photos may not appear.

### Read the result

| Korean label or location | Meaning |
| --- | --- |
| 보낼 사진 | Queued, not yet sent |
| 보내는 중 | Sending |
| 다시 보내 주세요 | Failed; resolve the issue and retry |
| 설정 → 전송 기록 보기 | Settings → Transfer history; 50 records per page |

Confirmed completed photos leave the home screen. After the transfer finishes, the sender removes completed staging copies; copies with pending or failed transfers to another frame may remain. Original phone photos and history remain intact.

The sender checks whether the frame already has the same file before uploading. This is file-content matching, not visual similarity detection; editing or re-encoding can produce a different photo hash.

## 5. Display photos in Fotoo

1. Install and open Fotoo separately on the frame.
2. Send at least one photo with the receiver.
3. In **Fotoo → Gallery**, select the **SmartFrame** album.
4. After switching to USB/SD, select the album on the new volume.

The receiver registers images in MediaStore and signals changes. There is no need to open a separate gallery app to register them. Fotoo's refresh interval, playback order, permissions, and version affect when new photos appear.

Fotoo's **Launch on boot** is a separate setting. The receiver does not make Fotoo the default home app or disable Lenovo's official app. The official app may take over the screen after a reboot on some devices.

## 6. Delete photos from the frame only

1. Open **⚙ 설정 → 액자 사진 관리 · 삭제** (Settings → Manage/delete frame photos).
2. Inspect the thumbnails and tap the photos to remove.
3. Tap **액자 사진 N장 삭제** (Delete N frame photos).
4. Check the frame name, storage, and count; confirm **액자에서 삭제** (Delete from frame).

**This does not delete your phone originals.** Only images transferred by this app into `Pictures/SmartFrame/` on the currently selected volume are managed. Photos from other apps and other volumes are excluded.

The list is paginated in groups of 50; select up to 50 images per deletion. If the network fails, some images may already have been deleted. Refresh before retrying. Other phones' past transfer records are not automatically updated.

**고른 사진 목록 비우기** (Clear selected-photo list) is different: it clears the phone's staged queue without deleting frame photos or phone originals.

## Troubleshooting

| Symptom | First checks |
| --- | --- |
| No frame discovered | Same Wi-Fi; enter the receiver's displayed address manually |
| Discovered but cannot connect | Receiver running, current address, guest-network/AP isolation, VPN settings |
| `SSLHandshakeException` | Latest receiver, self-test, and **연결 진단 보기** (Connection diagnostics). Do not disable certificate verification |
| Verification text differs | Stop pairing and check the destination. Reinstallation or data reset requires new pairing |
| USB missing | Android must recognize it as writable shared image storage |
| Insufficient space | Allow roughly twice the incoming photo size plus spare space on the selected volume |
| Photos missing in Fotoo | Transfer completed, correct volume and SmartFrame album, Fotoo permissions and refresh |
| Receiver's Open Fotoo fails | Launch Fotoo directly from the app list; install it separately if absent |
| No receiving after reboot | Open receiver once; inspect battery and manufacturer auto-start restrictions |
| App absent from Share | Sender 0.5.0+, image-file sharing rather than text/link, expanded share sheet |
| Cannot read a shared photo | Download it first; supported formats are JPEG, PNG, WebP, HEIC/HEIF |

Communication uses TCP **8844**, with mDNS for discovery. Identical Wi-Fi names do not guarantee that the router permits client-to-client traffic. Internet port forwarding is not required.

## Get help

Open an [issue](https://github.com/Jungsik-won/smart-frame-photo-transfer/issues) with app versions, Android versions, device model, reproduction steps, and the error message. Remove personal photos, pairing codes, tokens, certificate fingerprints, and actual network addresses from public logs or screenshots.

[← Overview](../README.en.md) · [Testing status](TESTING.md) · [Build guide](BUILD.md)
