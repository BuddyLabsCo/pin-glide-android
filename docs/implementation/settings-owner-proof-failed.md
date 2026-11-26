# Failed QQ owner verification (#134)

The failed-proof warning now uses the exact approved Simplified Chinese copy,
`QQ 验证失败，未提交撤销。`, with corresponding English feedback. The shared
centered owner-proof layout and warning card from #132 and #133 already match
the approved composition and remain unchanged. Verification and Cancel retain
their existing behavior.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](settings-owner-proof-failed-evidence/light-comparison.png) |
| Dark | [Comparison](settings-owner-proof-failed-evidence/dark-comparison.png) |

Both comparisons use Simplified Chinese, 100% font scale and a 366 × 820 dp
viewport on an isolated API 37 emulator. The left screen is cropped from the
unchanged [pinned reference](../ui-prototype/shots/05-owner-proof-failed.png)
and resized to the Android screenshot. Native system bars and font rendering
are retained. No motion or prototype changes were made.

## Verification

- The new focused UI regression failed on the missing approved warning copy
  before the change and passed afterward. It checks the centered warning,
  icon, palette, assertive accessibility announcement, selected Sender,
  enabled retry and Cancel, and the pending and failed retry feedback.
- All five `SenderRevocationConfirmationTest` cases passed in light theme,
  including English at 200% font scale. The failed-proof regression also
  passed in dark theme.
- Debug, release and instrumentation Kotlin compilation, both debug APK
  assemblies, `qualityCheck`, and all 46 JVM tests passed.
- Across the 159 device cases, 156 passed, one camera assumption was skipped,
  one unrelated Pairing navigation test failed, and one unrelated Home
  scrolling test stalled. Two full runs stalled in
  `HomeLoginExpiredScreenTest.expiredListStillScrollsButHidesRefreshControlsAndFeedback`.
  The remaining 143 cases completed with 142 passes and one failure in
  `PairingConsentNavigationTest.backReturnsToScannerAndCancelReturnsToFirstDeviceEntry`.
  Both unrelated cases were retried individually; the former stalled again
  and the latter failed again. Neither screen was changed.

QQ authorization and service operations were simulated; no real destructive
operation was exercised.

The issue evidence links require publication of the local commit by the
authorized publishing workflow; no issue attachment upload tool is available.
