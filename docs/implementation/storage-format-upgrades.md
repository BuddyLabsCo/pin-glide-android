# Protected identity and Pairing format upgrades

Issue #36 extends `ProtectedIdentityStore` without changing the `IdentityStore`,
`LogoutStore` or `PairingStore` interfaces. Identity, complete M/U/F credentials and
expiries, renewal markers, generation, registration closure state and active or
provisional Pairings still commit in one encrypted `identity.snapshot` under
`noBackupFilesDir`.

## Formats and ownership

| Document | Representation | Upgrade |
| --- | --- | --- |
| 1 | Legacy identity and optional Pairing fields; inline Pairing key bytes | Normalize existing defaults to 2, then protect keys as 3 |
| 2 | Explicit authority, renewal, closure, restoration and Pairing metadata; inline keys | Protect keys as 3 |
| 3 | Complete state with separately protected `protectedKey` envelopes per Pairing | Current |

Document validation checks complete credentials and expiries, renewal ownership,
positive generations, registration/logout invariants, unique Pairing/Receiver IDs,
aliases, encryption counters, provisional markers and Account/Sender ownership.
Only version 1 may omit fields that older writers supplied through defaults.
Expired credentials remain readable so the existing renewal/login policy can act.

Envelope 1 keeps its original AES-256-GCM authenticated context for decryption.
Envelope 2 authenticates version, Keystore alias and purpose. The outer purpose is
`identity`. Each Pairing key authenticates its Account, Sender, Pairing and Receiver
IDs, so moving a reference between targets or Accounts cannot recover a usable
key. Alias, forwarding switch and provisional status can change without changing
that ownership. The provider credential cache continues to use its separate
in-memory envelope and lifecycle.

## Commit and failure behavior

All reads, migrations and writes for a canonical snapshot path share one process
mutex. Migration validates the source, prepares and verifies the replacement keys,
then encrypts the complete replacement. It syncs a sibling staging file and
atomically replaces the committed file. A direct 1-to-3 upgrade runs both steps in
memory and commits only the final document. Old Keystore aliases are retained;
neither a failed preparation nor a cancelled replacement deletes them. An abandoned
staging file is never a restoration source. Subsequent writers read the committed
generation and Pairing revision before applying changes.

Unreadable outer protection, malformed authority or Pairing metadata, unsupported
document/envelope versions, future key versions and failed migration leave the committed bytes
untouched and surface the existing storage error. A malformed supported Pairing
key or missing target Keystore key retains its original protected material, marks
only that target's key failed and switches it off. Healthy targets remain usable
through the existing key-error UI. No recovery silently enrolls over a snapshot.

Logout retains Pairing key references, encryption counts and provisional markers
for verified same-Sender restoration. A different Account or Sender starts without
those Pairings. Registration closure still removes authority and Pairings while
retaining closed Sender IDs. Setup/language preferences remain independent.

No SMS, OTP, model payload, delivery ciphertext, QR secret, handshake or pending destructive
action is added to durable state. `allowBackup=false`, the existing backup rules
and cloud/device-transfer exclusions remain in force. Reinstall or app-data clear
has no restorable identity or Pairing keys and requires login and Pairing again.

## Verification and compatibility

Verification uses temporary instrumentation under ignored `build/`, without adding
test infrastructure or debug seeds to the production change. It exercises the public store seams
with real Android Keystore protection on an API 37 emulator. It checks both legacy
versions and restart, renewal/generation/Pairing preservation, failed migration,
concurrent stale writers, cancelled replacement, missing outer/target keys,
corruption, future formats, swapped references, logout/restoration, registration
closure and Account separation. Existing host tests and repository quality checks
provide the remaining regression checks.

This upgrade is forward-only: older application builds that understand only
document/envelope 1 cannot read the upgraded file. The emulator checks do not prove
physical-device Keystore behavior or persistence across sudden device power loss.
