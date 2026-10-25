# Changes

## 0.8.16

Phone-only session length is now a shared App Settings choice: 5, 15, 30, 60 minutes, or Unlimited. Existing installs keep the five-minute default until changed. A renewable bounded wake lock supports longer locked-screen calls and releases when the session stops. Cancellation removes the timer and renewal callbacks; stale callbacks cannot stop a later session. Provider and legacy-backend limits remain separate. Character data, keys, and memory protocols are unchanged.

Automated timer tests cover all finite deadlines, Unlimited beyond 24 simulated hours, Stop, renewal failures, invalid stored choices, and stale callbacks. A real-phone long-call test is still needed.

## 0.8.15 source preview

Natural explicit memory saving now keeps the user-derived candidate separate from character readback. Complete corrections create a new revision requiring a new readback and confirmation. Leading recognized laughter annotations and request punctuation are normalized without rewriting the fact. The no-delegation fallback saves an eligible confirmed candidate once. Readback matching accepts a trailing declarative phrase; premature success claims trigger bounded corrective steering.

Earlier iterations added reviewable AI drafts, optional conversation history, complete character backups, a fixed Save control, and editing of saved facts. The phrase-by-phrase assistant suffix cleanup used in earlier versions is no longer the explicit-save payload strategy.

Public packaging adds MIT licensing, contributor documentation, a portable Gradle regression module, development application-ID separation, and CI. Gradle now owns SDK version declarations. Three documented lint annotations explain existing safety/navigation code. No personal signing keys, provider keys, backups, or transcripts are included. Runtime Java behavior remains the tested 0.8.15 baseline.
