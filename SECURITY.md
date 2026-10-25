# Security and privacy

This is a personal BYOK prototype, not an independently audited security product. Never commit API keys, `.env.local`, `backend/private`, pairing files, signing keys, character backups, or private transcripts. Share redacted diagnostics rather than raw conversation screenshots.

Phone-only mode sends microphone audio, character instructions, applicable saved facts, and optional history directly to OpenAI. It requests `store:false`; provider data policies still apply. Fixed provider destinations, certificate/hostname checks, and disabled automatic retries/redirects reduce accidental credential forwarding. There is no bundled provider key.

Optional saved API keys use AES-GCM backed by Android Keystore. A bearer key must be decrypted in process memory to make requests. This does not protect against a compromised app/device or a malicious replacement build. Use a dedicated project/key, monitor provider usage, and revoke credentials when needed. Local time limits are not a monetary spending cap. Never put another person's shared billing key into a distributed APK.

App data is local to the phone, with Android automatic backup disabled. Manual character exports are unencrypted and include retained conversation excerpts; anyone who obtains one may read them. Pending review drafts and API keys are omitted. Losing the phone or uninstalling can lose local data. A manual backup is not an export of the provider's custom voice model.

Explicit voice saves validate the current user-derived candidate, readback, revision, confirmation, owner, expiry, and storage outcome. AI-review drafts have a separate approval path. Model speech and transcripts remain fallible; do not rely on a spoken “saved” without inspecting storage. The app's corrective steering cannot guarantee the model never makes an unsupported claim.

If you find a vulnerability, use the repository's private vulnerability-reporting option if enabled. Otherwise ask the maintainer for a private reporting channel without posting exploit details, keys, or personal data in a public issue. Never test against other people's keys or devices.
