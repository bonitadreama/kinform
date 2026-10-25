# Phone-only setup

1. Open **App Settings → Connection & API key**. Select Phone-only and **Add / replace my API key**. Enter your own dedicated OpenAI project key privately on your phone.
2. Leave Remember unchecked for process-lifetime storage, or choose encrypted storage. Closing an activity does not necessarily stop the process. **Remove my API key** clears the app's key; revoking a key at the provider is a separate action.
3. Create/select a character and choose a preset voice. Custom voices require provider eligibility and access to their originating project.
4. Connect a Bluetooth device supporting both microphone and speaker call audio. Tap Talk and select it. Stop ends recording; session finalization may take up to 15 seconds. Route/focus loss and the five-minute limit stop sessions. There is no automatic reconnect.

## Memories

Save character preferences before testing them; reopen to check the selection persisted.

For an explicit save, give a complete fact: “Save this: I prefer soft lighting when reading.” After the character reads it back and asks, say “yep,” or “nah, cancel.” A complete correction such as “Actually, I prefer warm lighting for drawing” requires a new readback and confirmation. The confirmed user wording is the storage payload. Ambiguous requests need clarification. Check Memories after stopping Talk to establish whether anything actually saved.

AI suggestions are separate drafts. Choose **Remembering & conversation → AI suggestions: review before saving**. After Talk, open Memories and **Review / edit suggestion**. Approve & save stores the reviewed wording; Reject discards the draft. Spoken yes does not approve an AI-review draft. Drafts may not appear on every call. Up to 20 drafts per character stay on the phone and are excluded from backups.

Remembering Off prevents memory use and new saves, suggestions, and history writes. Existing stored data is retained. Each character owns its own memories.

## Recent conversations and backups

Conversation history is optional. Up to 10 sessions and about 60,000 serialized characters are retained; the latest 10,000 rendered characters are supplied on a later call. This is bounded continuity, not unlimited recall. Turning history off retains old excerpts but excludes them from context. History can contain a canceled or rejected proposal, so recalling it does not prove it was saved as a fact.

Transcripts may be inaccurate or incomplete, and generated speech may not have played. A crash or force-stop may lose the current session. Kinform stores no audio recording.

Character exports include retained history and saved facts, even when their use is disabled. Keep these plaintext files private. Keys, pending drafts, and connection settings are excluded. Import creates a separate character and supports older version-1 backups; newer exports require a compatible app. Export to a new file and keep earlier backups. Do not clear app data or uninstall as a troubleshooting step.
