# Settings navigation and installation language

Home opens Settings in a full-screen Compose dialog while keeping the Home
composition and its ViewModel alive. Returning exposes the current Home state,
including changes received while Settings was open. Definitive revocation still
takes precedence over Settings. Account-owner changes reset the Settings entry.

Settings owns its dialog's dismissal callback. System Back and the toolbar return
children to Settings; root Back dismisses Settings. A saveable state holder keeps
each destination's scroll state. The root toolbar and action rows share a saved
focus owner, so configuration changes restore the actual focused control,
including Back. Returning from a child still restores its originating row.
Privacy uses the same read-only PrivacyScreen as Welcome.

Account now reads its profile and provides ordinary logout. Device access reads
the current Account's unrevoked Sender inventory and captures permitted targets;
see sender-inventory.md. QQ owner proof and permanent revocation remain owned by
issues #34-#35.

## Language and system entries

LanguagePreferences stores only the installation language in a separate
installation preferences file. Identity, credentials, Pairing state and setup
progress are not rewritten. The three mutually exclusive choices apply
immediately and return to Settings. The application host supplies a localized
Context, Configuration and Compose LocalResources without recreating the Activity
on selection. Follow system reads the current primary configuration locale;
English uses English resources and other languages use Simplified Chinese.
Later system configuration changes update Follow system while explicit choices
remain fixed. All other configuration properties, including font scale and
system appearance, are preserved.

Appearance and the version row have no click action. DeviceInfo reads the
installed package version. Battery optimization reuses SetupPermissions' Android
settings launcher and its existing fallback; unsuccessful launches show an
accessible inline error and retain Settings. No exemption probe, battery status,
extra explanation screen or global forwarding switch is introduced.

## Verification

Run from the repository root on Windows:

```powershell
./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.buddylabs.pinglide.domain.pairing.OwnPairingsTest --console=plain
./gradlew.bat qualityCheck :app:compileDebugKotlin :app:compileReleaseKotlin :app:assembleDebug :app:test --console=plain
```

The focused Pairing suite and Kotlin compilation were run before and during
implementation. The final qualityCheck, Debug/Release Kotlin compilation, Debug
APK assembly and full :app:test suite passed (14 tests, no failures or skips).
An API-37 emulator exercised the production Settings composables
through a disposable preview APK under ignored build/ticket-29/, without logging
in or seeding identity or Pairing data. The preview was removed afterward.

Observed checks include both picker changes and one checked radio choice,
return to Settings, live English/Chinese system changes, French fallback,
explicit-language persistence through restart, child/system/root Back,
Account/device-access destinations, keyboard activation/focus restoration,
the actual Android battery screen and return, injected launcher failure,
both themes, 200% font scaling, rotation and retained privacy scroll. Version
0.1.0 matches the installed package. Screenshots remain in build/ticket-29/.

The immediate-selection check initially reproduced a stale resource cache:
the selected value changed while other text retained its old language. Providing
Compose LocalResources explicitly fixed it; both picker directions then updated
all visible Settings text immediately.

The focus review regression was reproduced on the API-37 emulator before the
fix: focus QQ account, move up to toolbar Back, then rotate. Focus incorrectly
returned to QQ account. After sharing the saved root focus owner with the
toolbar, the same sequence retained Back, and keyboard activation dismissed
Settings to the original preview host. This uses the existing disposable preview;
authenticated Home dismissal remains outside the device checks above.
QQ account and Language also retained their originating-row focus after child
Back and rotation. Back and Language retained focus through light/dark changes.
The focused suite, final qualityCheck, both Kotlin compilations, Debug assembly
and full unit suite passed again (14 tests, no failures or skips).

Ripwire's quality delta and test gate were also inspected. They flag generic
Compose layout similarity, navigation parameter growth, recent-file churn and
existing application wiring without mapped automated coverage. These heuristic
findings are not a passing automated-coverage claim; the repository's four
required static checks passed and this slice was exercised as described above.
For this review fix, the quality delta has no gating findings and one minor
screen-length warning. The test gate still maps no automated tests to this UI
change; the regression checks above exercise the production composables.

Full authenticated Home navigation, ongoing network operations during
configuration changes, physical devices, TalkBack speech and older Android
versions were not exercised. Account profile/logout/inventory behavior belongs
to the separately assigned issues above. No automated tests, dependencies,
test harnesses or debug seeds are added to the shipped source sets.
