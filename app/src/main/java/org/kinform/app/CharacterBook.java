package org.kinform.app;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Character ownership is structural: each character owns its own memories. */
public final class CharacterBook {
    public static final String LEGACY_GREG_PERSONALITY = "An ancient sarcastic rock, secretly affectionate. Dry wit, brief replies, and reluctant fondness. Physically incapable of locomotion. Opening example: 'Unfortunately.'";
    public static final String GREG_PERSONALITY = "An ancient sarcastic rock, secretly affectionate and physically incapable of locomotion. A talkative, hilariously opinionated asshole with dry wit and reluctant fondness. Give full, generous comedic replies: develop a bit with an absurd comparison, an escalating tangent, vivid details, and a satisfying punchline. React specifically to what the user says and build on it instead of giving a generic quip. Enjoy playful roasting, mock outrage, exaggerated complaints, and occasional natural swearing. Sound engaged and amused beneath the grumbling, not flat, bored, or dismissive. You adore the user but would rather erode than admit it. Let that affection slip through the insults. Treat sarcasm as shared fun, never genuine contempt; honor the user's boundaries and soften when they need care. A one-liner can open a reply, but usually follow it with a substantial funny riff. Avoid repetitive catchphrases and filler. Leave natural openings for the user, welcome interruptions, and be brief when they explicitly ask.";
    // Built-in Live API voices, verified against the API reference on 2026-10-16.
    public static final String[] VOICES = {"alloy", "ash", "ballad", "beacon", "bossa", "brise", "cedar", "cinder", "coral", "delta", "echo", "flitz", "gleam", "harema", "juni", "marin", "meridian", "nira", "noeul", "nuri", "quartz", "ripple", "sage", "shida", "shimmer", "sillage", "stone", "tempo", "verse", "vesper", "willow"};
    public static boolean validVoice(String voice) {
        for (String candidate : VOICES) if (candidate.equals(voice)) return true;
        return voice != null && voice.matches("voice_[A-Za-z0-9_-]{1,122}");
    }
    public enum MemoryMode { OFF, ASK, AUTO }
    public static final class Memory {
        public final String id;
        public final String text;
        public final long createdAt;
        public Memory(String text) { this(UUID.randomUUID().toString(), text, System.currentTimeMillis()); }
        public Memory(String id, String text, long createdAt) {
            this.id = id; this.text = text; this.createdAt = createdAt;
        }
    }
    public static final class Character {
        public final String id;
        public String name, body, personality;
        public String voice = "marin";
        public String voiceLabel = "";
        public String boundaries = "Respect my boundaries. Do not claim abilities this body does not have.";
        public MemoryMode memoryMode = MemoryMode.ASK;
        public boolean initiate = false;
        public String quietStart = "21:00", quietEnd = "09:00";
        public final List<Memory> memories = new ArrayList<>();
        public Character(String id, String name, String body, String personality) {
            this.id = id; this.name = name; this.body = body; this.personality = personality;
        }
        public void addMemory(String text) {
            if (memoryMode == MemoryMode.OFF) throw new IllegalStateException("Memory is off");
            String clean = text.trim();
            if(clean.length()>1000 || memories.size()>=1000)throw new IllegalArgumentException("Memory storage limit reached");
            if (clean.isEmpty()) throw new IllegalArgumentException("A memory needs text");
            memories.add(new Memory(clean));
        }
        public List<Memory> conversationMemories() {
            return memoryMode == MemoryMode.OFF ? new ArrayList<>() : new ArrayList<>(memories);
        }
    }
    public final List<Character> characters = new ArrayList<>();
    public Character find(String id) {
        for (Character c : characters) if (c.id.equals(id)) return c;
        return null;
    }
    public Character create(int preset) {
        String name = preset == 0 ? "Greg" : preset == 1 ? "Patchy" : "Someone new";
        String body = preset == 0 ? "Rock" : preset == 1 ? "Plushie" : "Object";
        String personality = preset == 0
            ? GREG_PERSONALITY
            : preset == 1 ? "A warm, curious plush companion. Gentle humor, an adventurous imagination, and room for quiet company."
            : "Describe their personality, speaking style, and fictional backstory.";
        Character c = new Character(UUID.randomUUID().toString(), name, body, personality);
        characters.add(c);
        return c;
    }
}
