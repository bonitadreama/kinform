package org.kinform.app;

import java.util.ArrayDeque;

/** Fixed labels and counters only. Never accepts transcripts, IDs, or exception messages. */
final class MemoryTrace {
    enum Event { STARTED, INPUT_CLEARED_OUTPUT, DELEGATED, DRAFT_PARSED, NO_DRAFT,
        QUEUED, DUPLICATE, FULL, OFF, INVALID, STORAGE_ERROR, OWNER_MISSING,
        PROTOCOL_ERROR, COMMENTARY_SENT, COMMENTARY_FAILED, STOPPED, SESSION_READY,
        MARKER_DETECTED, EMPTY_OUTPUT_AT_DELEGATION, MARKER_NOT_PARSEABLE, STOP_DRAFT_CAPTURE,
        STOP_CHECK, STOP_DRAFT_READY, STOP_NO_OUTPUT, STOP_INVALID_DRAFT, STOP_NO_MARKER, AUTOMATIC_STOP,
        READBACK_MARKER, READBACK_CAPTURED_BEFORE_REPLY, VOICE_PENDING, VOICE_NOT_READY,
        VOICE_SAVE_REQUESTED, VOICE_SAVED, VOICE_SAVE_FAILED, VOICE_REPLY_BOUNDARY, VOICE_AUDIO_HELD,
        PREMATURE_SAVE_CLAIM, SAVE_CORRECTION_SENT, SAVE_CORRECTION_FAILED }
    private final ArrayDeque<Event> recent=new ArrayDeque<>();
    private int inputs,outputs,missingIds,delegations,queued,clears;
    private boolean markerSeen,readbackSeen;
    private final boolean auto,history,direct;
    MemoryTrace(boolean auto,boolean history,boolean direct){this.auto=auto;this.history=history;this.direct=direct;event(Event.STARTED);}
    void transcript(boolean input,boolean hasId,boolean marker){
        if(input)inputs++;else outputs++;
        if(!hasId)missingIds++;
        if(marker && !markerSeen)event(Event.MARKER_DETECTED);
        markerSeen|=marker;
    }
    void event(Event e){
        if(e==Event.READBACK_MARKER){if(readbackSeen)return;readbackSeen=true;}
        if(e==Event.DELEGATED)delegations++;
        if(e==Event.QUEUED)queued++;
        if(e==Event.INPUT_CLEARED_OUTPUT)clears++;
        if(recent.size()==24)recent.removeFirst();recent.addLast(e);
    }
    String report(){return "Memory diagnostic · 0.8.15\nConnection: "+(direct?"phone-only":"legacy backend")+
        "\nAI suggestions enabled: "+auto+"\nHistory enabled: "+history+
        "\nInput transcript events: "+inputs+"\nOutput transcript events: "+outputs+
        "\nTranscript events missing IDs: "+missingIds+"\nSuggestion marker detected: "+markerSeen+
        "\nDelegations: "+delegations+"\nOutput buffers cleared by input: "+clears+
        "\nDraft writes confirmed: "+queued+"\nRecent steps: "+recent+
        "\n\nCounters describe the latest call only. No audio, conversation text, key, or character name is included. They do not prove audio was heard.";}
}
