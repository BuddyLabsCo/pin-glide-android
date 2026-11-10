# Own-Pairing alias rename

Issue #21 completes the existing Home rename workflow through the selected stable
Pairing ID. Editing starts with that Pairing's current alias. Both editing and
submission use the same validation: trim the input, require 1–40 Unicode code
points, reject unpaired surrogates, and reject case-insensitive matches among
other current own Pairings. Supplementary-plane characters count once and are
never truncated. Confirmed removals disappear from the authoritative own list
and therefore from duplicate checks; a failed refresh cannot establish removal.

The editor presents separate blank, length, invalid-character and duplicate
errors in Chinese and English, including accessible error/live-region semantics.
It uses the existing light/dark theme, scrollable sheet, system/IME insets and
48dp controls. Cancel and Save share a row that wraps when the available width or
font size requires it. Saving has a text status with no additional animation.
Idle Cancel, Back, outside taps and downward dismissal discard the draft and
return to the selected target's details; configuration changes retain the editor
and ViewModel work.

## Submission and reconciliation

- The generated PatchSelfPairing operation uses management authority and the
  selected Pairing ID. It does not update a Receiver or another Sender's alias.
- The existing shared HTTP client bounds each call to 30 seconds and disables
  automatic connection retries. The ViewModel suppresses duplicate submissions
  and keeps dismissal, gestures, cancellation and editing blocked until the
  mutation and authoritative own-list reconciliation finish.
- Network failure or timeout retains the draft and reports an unconfirmed save.
  A failed response does not establish that the write was rolled back. The
  subsequent list read updates shared Home state; it does not replay the write.
  Editing or explicit retry remains available afterward.
- A successful response must match the requested alias and stable Pairing/Receiver
  identity. Under the state lock, completion checks current Account/Sender,
  authority generation, refresh state, membership and the alias observed at
  submission. A newer alias/removal wins; concurrent local switches and other
  Pairings are preserved by reconciliation against the current snapshot.
- Completion feedback waits for synchronization and is discarded after an
  authority-generation/owner change. A changed alias from the authoritative list
  cannot close the editor with an obsolete success notice.

## Published contract

The latest published service release was verified on November 10, 2026 as
v1.0.2 (published November 8, 2026). The existing fixed pin already selects it.
The normal build downloaded its openapi.yaml and asyncapi.yaml assets and
regenerated both HTTP client and WSS models under build/. The published alias
endpoint permits a wider string and clearing; Android enforces this product's
nonempty 1–40-character policy before submission. No generated source is checked
in and no service endpoint is invented.

## Verification boundary

The existing OwnPairingsTest host suite exercises management serialization,
refresh races, switches, removal and owner changes. An untracked build-directory
self-check exercises the compiled alias validator's blank, ASCII/supplementary
40/41-character boundaries, trimming, duplicate detection and malformed
surrogates. This ticket adds no persistent automated tests, E2E harness or debug
seeds. Android visual, TalkBack and real-service acceptance remain manual checks.
