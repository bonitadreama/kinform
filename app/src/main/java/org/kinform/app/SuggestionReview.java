package org.kinform.app;

/** Called only by the explicit phone approval action, never by model events. */
final class SuggestionReview {
    static void approve(CharacterBook.Character owner,String draftId,String edited,long now) {
        if(owner==null || owner.memoryMode==CharacterBook.MemoryMode.OFF)throw new IllegalStateException("Remembering is off");
        String clean=edited.trim();
        if(clean.isEmpty() || clean.length()>1000 || !draftId.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("Invalid memory");
        for(CharacterBook.Memory m:owner.memories)if(m.id.equals(draftId))return;
        if(owner.memories.size()>=1000)throw new IllegalStateException("Memory limit reached");
        owner.memories.add(new CharacterBook.Memory(draftId,clean,now));
    }
}
