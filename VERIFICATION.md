# Verification scope

## Real phone evidence

On October 25, 2026, the owner reported these results on 0.8.16-local:

- Installed the update and selected Unlimited in App Settings, then saved.
- Reopened App Settings and confirmed Unlimited remained selected.
- A real phone call with Greg continued past six minutes.
- Manual Stop ended the call normally.

This passes the Unlimited phone acceptance test for preference persistence, continuing beyond the old five-minute cap, and manual Stop. The report did not separately confirm screen-lock conditions; longer-duration, interruption, and broader-device behavior remain unverified. Unlimited removes Kinform's timer, not provider or connection limits.


On October 25, 2026, the owner reported these results on 0.8.15:

- A soft-lighting preference saved after “yep,” contained only the user fact, and survived reopening.
- A complete bright-to-warm-lighting correction saved the corrected fact without confirmation wording and survived reopening.
- “Nah, cancel” produced no new saved memory.
- A fresh character backup was exported while older backups were retained.

Earlier versions had user-confirmed standalone phone operation with the PC disconnected, character backup/import, memory editing, the fixed Save button, suggestion review, and Memory Off tests. These are not a complete rerun of every feature on the current build.

## Automated checks

`:checks:check` executes CharacterBook, CharacterBackup, LiveProtocol, and SessionTimer regression suites. SessionTimer uses a simulated clock to check finite and unlimited calls, wake-lease renewal, cancellation, and stale callbacks. The protocol suite includes 382 checks covering candidate payload boundaries, corrections, readback matching, cancellation, expiry, Off, duplicate/late events, review separation, and the zero-delegation fallback. Backend tests use a mock provider.

Build and lint check compilation and static issues; they do not establish microphone routing or model behavior. No paid API requests are required by the ordinary regression suite.

## Public package checks (October 25, 2026)

The standard Gradle 9.1.0 / AGP 9.0.1 build passed on Windows with Android SDK 36, Build-Tools 36.0.0, and the installed Android Studio JBR (Java 25). A fresh isolated Gradle cache was used. All three Java suites and 15 backend tests passed. The debug APK signature verified and its ID is `org.kinform.app.dev`. The wrapper JAR matched the official published SHA-256. The published 0.8.15 code subsequently passed GitHub CI on Linux/JDK 17 and Node 22. The 0.8.16 update also passed all four Java suites, Android build/lint, and backend tests locally and in GitHub CI (run 40231872155).

Lint passed with 12 warnings (dependency-version notices, drawing allocation, and localization). Three inspected false positives have narrow annotated explanations: AudioRecord errors already enter cleanup through an exception handler; modern Back already registers through reflection; explicitly assigned radio IDs are converted to an enum ordinal. These annotations do not change runtime behavior. The obsolete SDK declaration was removed from the manifest; Gradle retains min SDK 31 and target 36.

Known-private-value and credential-pattern source scans found no matches; compiled DEX checks also passed. These checks are limited, not proof that all vulnerabilities have been found. The npm advisory service did not return a usable audit response, so dependency vulnerability clearance is not claimed. The separate Gradle development APK has not been installed on the owner's phone; the personal 0.8.16-local update was installed and tested as described above. Public binary signing remains undecided.

## Remaining limits

No comprehensive device matrix or independent security audit. Bluetooth compatibility, key-storage lifecycle, interruption/network behavior, and all deletion confirmations need broader checks. The latest ambiguous “save that” phone exercise was deferred; negative automated coverage is not a phone pass. Model wording, transcript completeness, and actually audible playback can differ. Success claims from the character alone are not storage evidence.
