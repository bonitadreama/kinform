package org.kinform.app;

/** No Android or third-party dependencies: run with scripts/test.ps1. */
public final class CharacterBookTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        CharacterBook book = new CharacterBook();
        CharacterBook.Character greg = book.create(0), otherGreg = book.create(0);
        check(!greg.id.equals(otherGreg.id), "Same preset must create distinct identities");
        greg.voice="cedar";
        check(otherGreg.voice.equals("marin"), "Voice and mode must remain character-specific");
        greg.addMemory("Example user likes quiet mornings.");
        check(otherGreg.memories.isEmpty(), "Memory leaked between characters");
        greg.memoryMode = CharacterBook.MemoryMode.OFF;
        check(greg.conversationMemories().isEmpty(), "Off must exclude existing memories from conversation");
        check(greg.memories.size() == 1, "Turning off must not silently erase memories");
        boolean rejected = false;
        try { greg.addMemory("Do not store this"); } catch (IllegalStateException e) { rejected = true; }
        check(rejected && greg.memories.size() == 1, "Off must prevent new memories");
        greg.memoryMode = CharacterBook.MemoryMode.ASK;
        check(greg.conversationMemories().size() == 1, "Memory should return when enabled");
        greg.conversationMemories().clear();
        check(greg.memories.size() == 1, "Conversation list must not mutate stored collection");
        book.characters.remove(greg);
        check(book.find(greg.id) == null && book.find(otherGreg.id) != null, "Delete must preserve other identities");
        System.out.println("PASS: identity isolation, memory off/read/write policy, snapshot isolation, deletion");
    }
}
