# Kinform 🖤

Give an ordinary object a voice, a personality, and memories you control.

Kinform is an experimental native Android companion app. Greg, a sarcastic rock, helped us test it. Create your own character, connect a Bluetooth device with microphone and speaker call audio, and talk using your own OpenAI API key.

**Public source preview: 0.8.15.** Android 12 or newer. Phone-only mode connects directly to OpenAI: no PC or Kinform server is needed during use. It needs internet, API billing, and access to the configured `gpt-live-1` model. A ChatGPT subscription does not pay for API usage. Model and custom-voice availability depend on your account.

## Start here

1. [Build a development APK](BUILDING.md). Development builds install as `org.kinform.app.dev`, separately from the original app. No signed public release APK is bundled.
2. [Set up phone-only mode](PHONE-ONLY.md), enter your own key privately on the phone, and select a voice your project can use.
3. Create a character and start Talk with a compatible Bluetooth call-audio device. Stop ends recording. Sessions are limited to five minutes.
4. Try one harmless memory: “Save this: I prefer soft lighting when reading.” Check the readback, say “yep,” then Stop and inspect Memories. A spoken “saved” alone is not proof.

## What it does

- Separate characters, personalities, voices, and saved facts.
- Explicit voice saving with confirmation, complete corrections, cancellation, and expiry.
- Optional AI suggestions that wait for phone review, editing, and approval.
- Optional recent conversation excerpts, with bounded storage and model context.
- Editable saved memories and character exports/imports.
- A fixed Save control on character editing screens.

Explicit voice saves persist the **user's candidate fact**, never the character's confirmation wording. Ambiguous “save that” and incomplete corrections need clarification. The current parser is conservative and English-oriented; it is not arbitrary natural-language understanding. [Architecture and a guided exercise](CONTRIBUTING.md).

## Your data and your key

Characters, facts, and retained history live on the phone. Audio and relevant character/context text are sent to OpenAI during Talk; this is not offline AI. Kinform does not store audio recordings. Optional encrypted key storage uses Android Keystore; a personal BYOK app is still a client-side credential tradeoff. [Security and privacy limits](SECURITY.md).

Exported character backups are readable plaintext and may include private conversations. They omit API keys, app connection settings, and pending AI drafts. Keep backups private. Import creates a separate character. A voice reference is not a backup of the provider's voice model.

## Status and participation

Real-phone tests have confirmed clean explicit saves, complete corrections, cancellation, and persistence after reopening on 0.8.15. This is a community preview, not a security certification or a guarantee across phones, Bluetooth devices, or model responses. [Verification and limitations](VERIFICATION.md), [roadmap](ROADMAP.md), [changes](CHANGELOG.md).

The optional [legacy LAN backend](backend/README.md) remains available for experimentation. It does not provide the newer phone-only memory/history feature set.

Built through human product direction and real-device testing with Codex implementation assistance. This is an independent educational project, not an official OpenAI product. Contributions and playful experiments are welcome. Project code is [MIT licensed](LICENSE); dependencies retain their [own licenses](THIRD_PARTY.md).
