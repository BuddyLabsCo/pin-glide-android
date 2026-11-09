# Home synchronization, readiness and setup skip

Issue #16 connects accepted Sender identity to a durable setup completion checkpoint
and a Home backed by the published own-Pairing list. Set up later and system/top
Back from first-device entry both complete setup only after rechecking actual SMS
permission. A completed installation returns to recomputed Home after restart,
including when SMS permission subsequently disappears.

## Ownership and refresh

- The Activity owns foreground triggers and the retained Home ViewModel.
  Accepted navigation refreshes after identity restoration or login; foreground
  return and Home pull, keyboard Enter or accessibility action use the same
  process-owned OwnPairings workflow.
- OwnPairings coalesces pending refreshes and uses the existing shared management
  authorizer. A published credential_expired rejection permits the existing one
  renewal/business retry. A list invalidated by a concurrent local write gets
  one fresh reconciliation attempt; it cannot overwrite the newer write.
- OwnPairingService consumes generated getSelfReceiversWithHttpInfo and preserves
  item order, Pairing/Receiver identity, alias, presence and stateChangedAt. The
  timestamp is displayed as the actual presence transition in local time, never
  as the last query time. Unknown presence stays unknown. Malformed/duplicate
  results and network/service failures cannot establish absence or revocation.
- ProtectedIdentityStore serializes identity and Pairing writes under its existing
  writer and replaces the encrypted snapshot atomically. Compare-and-set checks
  authority generation, Account/Sender, refresh state and Pairing revision. A
  stable Pairing ID cannot silently change Receiver. The additive version-1
  fields preserve unrelated document and per-Pairing fields, keys and switches.
  Presence is intentionally operation-memory only and starts unknown after a
  restart. A successful own list alone cannot fabricate a missing local key.
- Screen projections contain no credentials or keys. Local switch actions carry
  their observed Home state and reject stale UI work. Eligible cached targets
  remain selected through a failed list refresh. Later activation/alias/removal
  workflows should call OwnPairings.refresh after successful mutations, through
  this same shared path.

Definitive current-Sender 409 device_registration_revoked is reported to the
shared authorizer only while the request's management credential still matches.
It persists terminal authority; a delayed lesser error cannot downgrade it.
Known unusable access requires login, while an unknown 409 or transport failure
does not invent identity loss.

## Presentation

Readiness resolves revoked, unrecoverable login, missing SMS permission, unknown
initial membership, no active Pairings, no selected targets, all selected targets
known offline, then ready. An online OFF target cannot hide selected offline
targets; partial offline remains ready. Failed/refreshing presence is awaiting
update and never invents offline status. Initial loading/failure has no fixture
rows, and cached failure retains actual targets.

Home uses the existing light/dark theme with Chinese/English resources, wrapping
text and safe system insets. Ready uses white-on-teal; warning uses themed yellow;
five active Pairings, including offline/OFF, show the orange capacity label and
hide Add device. Empty Home has exactly one Add first device action.

Material 3 pull-to-refresh handles the list-top threshold, cancelled gestures and
row-click cancellation. Its status label shows pull/release/pending states. The
same refresh is available through a focusable Enter action and an accessibility
custom action, including the empty/initial-error region. Status text uses polite
live regions; the custom indicator adds no independent animation.

## Slice boundaries

The existing first-device entry remains the Add device destination until shared
scanning/consent is implemented by #23. This change does not implement a scanner,
Pairing handshake, rename/removal, SMS recovery or QQ restoration. Those belong
to their accepted tickets. The small read-only target dialog and local selection
projection are the Home integration boundary for #17; no history, polling,
delivery WSS, normal refresh button, global switch or debug seed is introduced.

GitHub's latest service release was checked on November 9, 2026: v1.0.2,
published November 8 (UTC), with openapi.yaml and asyncapi.yaml. The existing
explicit pin is reused. Both assets and the HTTP/WSS generated sources were
regenerated through generateServiceClients under ignored build output.

## Verification

The final command passed:

```text
gradlew qualityCheck :app:compileDebugKotlin :app:compileReleaseKotlin :app:assembleDebug :app:test --continue
```

This includes formatting, Detekt, Debug/Release Android lint, both Kotlin variants,
the Debug APK and all six existing host tests. Ten isolated assertions against
the compiled readiness projection also passed. No automated tests or harnesses
were added to this production-only slice.

Ripwire quality-delta still reports five structural findings: preference-setter
similarity, Activity churn/size and encrypted-store size. These are accepted
trade-offs to retain the existing preference convention and single encrypted
writer; no findings were suppressed. Its test graph maps no tests to the new
workflow and leaves 33 files unindexed, so the host suite is not evidence of
end-to-end Home coverage. No device or emulator was connected: gesture delivery,
TalkBack, visual themes/font scaling and process-restart behavior remain
unverified on Android hardware.
