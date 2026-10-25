# Learn and contribute

Start with [BUILDING.md](BUILDING.md). Run the Java regression suites and backend tests without a provider key. Keep experiments in a development install and export characters before device tests.

## Follow one memory through the code

1. Read `LiveProtocol.java`: input transcript assembly recognizes a complete user save request and creates an immutable candidate revision.
2. Follow assistant output separately. A corresponding marked readback makes that revision eligible for a later confirmation; assistant wording is never substituted for candidate text.
3. Follow confirmation/delegation and the zero-delegation fallback into `VoiceService.java`. Storage checks and the durable write precede the app's success acknowledgment.
4. Read `LiveProtocolTest.java` for weird confirmation wrappers, user-owned quoted phrases, mismatches, corrections, cancellation, expiry, duplicates, and no-delegation behavior.
5. Compare `SuggestionReview` and `SuggestionStore`: AI-review drafts require phone approval and must not be saved by an ordinary voice yes.

The source is intentionally small: native Java Android UI, an event-driven voice protocol, bounded local stores, and an optional Node LAN relay. `CharacterBackup` handles portable character data. `MemoryTrace` records fixed diagnostic labels/counters without transcript, character name, or keys.

## A small first exercise

Add a synthetic unusual confirmation wrapper to a protocol regression case. Verify the saved fact is still exactly the user candidate. Do not fix tests by extracting the assistant's text as a fallback payload. Try a complete correction and prove the old revision cannot be approved.

For behavior changes, add a focused regression and explain the before/after result. Run `:checks:check`, Android build/lint, and relevant backend checks. Preserve Memory Off, bounded transcripts, expiry, cancellation, ownership, duplicate protection, and draft separation. Distinguish automated evidence from phone evidence. Do not add paid/networked tests to ordinary CI.

Keep credentials and personal test data out of contributions. Use fictional fixtures. Preserve dependency notices. Contributions are under the project's MIT license. AI assistance is welcome; describe what was checked and do not claim generated tests prove untested device behavior.
