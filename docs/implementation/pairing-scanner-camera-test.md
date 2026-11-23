# Pairing scanner camera regression

`HomeDuplicateScanTest.cameraRecognizesAnotherQrAfterRejection` uses the emulator back camera, CameraX, ML Kit, and the production Pairing QR reader. The emulator feed presents an unrelated QR, then a valid synthetic QR for the fixture's already-paired `receiver-office`. The test expects the invalid-QR notice, the same streaming preview, and a return to Home with the existing target highlighted.

From an empty working directory, create `valid-qr.txt` with this single line. The public key is the P-256 generator and the PSK contains only zero bytes. Both are test data.

```json
{"version":1,"cryptoVersion":1,"sessionId":"session","receiverId":"receiver-office","recipientPublicKey":"BGsX0fLhLEJH+Lzm5WOkQPJ3A32BLeszoPShOUXYmMKWT+NC4v4af5uO5+tKfA+eFivOM1drMV7Oy7ZAaDe/UfU=","psk":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}
```

Generate the camera feed with FFmpeg built with `qrencodesrc` and `libx264`.

```sh
ffmpeg -f lavfi -i 'color=white:s=640x480:r=15' \
  -f lavfi -i 'qrencodesrc=text=invalid-qr:q=160:Q=224:l=L:r=15' \
  -f lavfi -i 'qrencodesrc=textfile=valid-qr.txt:q=210:Q=270:l=L:r=15' \
  -filter_complex '[0:v][1:v]overlay=180-w/2:(H-h)/2:enable=between(t\,3\,13)[first];[first][2:v]overlay=180-w/2:(H-h)/2:enable=gte(t\,13)[video]' \
  -map '[video]' -t 28 -c:v libx264 -pix_fmt yuv420p qr-sequence.mp4
```

Boot an API 37 AVD with `-camera-back videofile:<absolute-path-to-qr-sequence.mp4>`. Install the debug app and instrumentation APKs, then run the test against that emulator.

```sh
adb shell am instrument -w -r -e cameraQrSequence true \
  -e class 'com.buddylabs.pinglide.ui.home.HomeDuplicateScanTest#cameraRecognizesAnotherQrAfterRejection' \
  com.buddylabs.pinglide.test/androidx.test.runner.AndroidJUnitRunner
```
