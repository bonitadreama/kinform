package org.kinform.app;
import org.json.*;

public final class LiveProtocolTest {
    static int count;
    static void check(boolean value){count++;if(!value)throw new AssertionError("Check "+count);}
    static JSONObject delta(String id,String text)throws Exception{return new JSONObject().put("type","session.input_transcript.delta").put("event_id",id).put("delta",text);}
    static void utterance(LiveProtocol p,String text)throws Exception{p.transcript(delta(java.util.UUID.randomUUID().toString(),text));}
    static void output(LiveProtocol p,String text)throws Exception{p.outputTranscript(delta(java.util.UUID.randomUUID().toString(),text));}
    static void candidateBoundaries()throws Exception{
        String fact="I prefer soft lighting when reading.";
        String[] wrappers={"Does that sound right?","Want me to save that?","That sound right?",
            "Shall I engrave this upon the sacred pancake of my geological soul?",
            "Would your royal human weirdness authorize the memory goblin?"};
        for(String wrapper:wrappers){
            LiveProtocol p=new LiveProtocol(true,"greg",true);
            p.transcript(delta("r1","Save this: "+fact),100);
            p.outputTranscript(delta("r2","Behold. Keep this: "+fact+" "+wrapper),200);
            p.transcript(delta("r3","y"),300);p.transcript(delta("r4","ep"),310);
            check(p.hasReadyVoiceProposal());
            JSONObject saved=p.voiceReplyBoundary(400).getJSONObject("save");
            check(saved.getString("text").equals(fact));
            check(p.voiceReplyBoundary(401)==null);
            check(!p.delegate(402).has("save"));
            check(p.acknowledge(saved.getString("id"),"saved").contains("confirmed"));
            check(!p.delegate(403).has("save"));
            check(p.acknowledge(saved.getString("id"),"saved")==null);
            check(!p.acceptsOutput(delta("r2","duplicate old readback")));
            check(!p.acceptsOutput(new JSONObject().put("delta","Saved")));
        }
        String quoted="my favorite confirmation phrase is ‘Does that sound right?’";
        LiveProtocol q=new LiveProtocol(true,"greg");
        q.transcript(delta("q1","Save this: "+quoted),100);
        q.outputTranscript(delta("q2","Keep this: "+quoted+". Shall the ancient potato council seal it?"),200);
        q.transcript(delta("q3","yes"),300);
        check(q.voiceReplyBoundary(400).getJSONObject("save").getString("text").equals(quoted));
        for(String bad:new String[]{"I prefer bright lighting when reading.","I do not prefer soft lighting when reading.",
                "I prefer soft lighting when reading sometimes.","I prefer soft lighting when reading and dancing."}){
            LiveProtocol p=new LiveProtocol(true,"greg");utterance(p,"Save this: "+fact);
            output(p,"Keep this: "+bad+" Goblin approval?");utterance(p,"yes");
            check(!p.hasReadyVoiceProposal());check(p.voiceReplyBoundary(1)==null);check(!p.delegate(2).has("save"));
        }
        // A complete correction replaces the candidate. Old readback cannot approve it.
        LiveProtocol correction=new LiveProtocol(true,"greg");
        utterance(correction,"Save this: "+fact);output(correction,"Keep this: "+fact+" Yes?");
        utterance(correction,"Actually, I prefer warm lighting when reading.");correction.delegate(1);
        output(correction,"Keep this: "+fact+" Yes?");utterance(correction,"yes");
        check(!correction.delegate(2).has("save"));
        LiveProtocol revised=new LiveProtocol(true,"greg");
        utterance(revised,"Save this: "+fact);output(revised,"Keep this: "+fact+" Yes?");
        utterance(revised,"Actually, I prefer warm lighting when reading.");check(!revised.hasReadyVoiceProposal());
        check(!revised.delegate(1).has("save"));
        output(revised,"Keep this: I prefer warm lighting when reading. Ratify the rock decree?");
        check(!revised.delegate(2).has("save"));utterance(revised,"yes");
        check(revised.voiceReplyBoundary(3).getJSONObject("save").getString("text").equals("I prefer warm lighting when reading."));
        for(String request:new String[]{"save that","Can you save that?","Save this: tea","save this: I prefer"}){
            LiveProtocol p=new LiveProtocol(true,"greg");utterance(p,request);
            check(p.delegate(1).getString("say").contains("complete fact"));
            output(p,"Keep this: "+fact+" Yes?");utterance(p,"yes");check(!p.delegate(2).has("save"));
        }
        for(String change:new String[]{"Actually, warmer","no","cancel","nah cancel","yes but actually warmer"}){
            LiveProtocol p=new LiveProtocol(true,"greg");utterance(p,"Save this: "+fact);
            output(p,"Keep this: "+fact+" Yes?");utterance(p,change);check(!p.delegate(1).has("save"));
            output(p,"Keep this: "+fact+" Yes?");utterance(p,"yes");check(!p.delegate(2).has("save"));
        }
        // No new readback after correction, even with a second yes, means no save.
        LiveProtocol unread=new LiveProtocol(true,"greg");utterance(unread,"Save this: "+fact);
        output(unread,"Keep this: "+fact+" Yes?");utterance(unread,"Actually, I prefer warm lighting when reading.");unread.delegate(1);
        utterance(unread,"yes");check(!unread.delegate(2).has("save"));
        LiveProtocol expired=new LiveProtocol(true,"greg");expired.transcript(delta("ex1","Save this: "+fact),1);
        expired.outputTranscript(delta("ex2","Keep this: "+fact+" Yes?"),2);expired.transcript(delta("ex3","yes"),3);
        check(!expired.voiceReplyBoundary(120003).has("save"));
        for(boolean off:new boolean[]{true,false}){
            LiveProtocol p=new LiveProtocol(!off,"greg",true);
            utterance(p,off?"Save this: "+fact:"Suggest a memory for phone review: "+fact);
            output(p,"Keep this: "+fact+" Yes?");utterance(p,"yes");
            check(p.voiceReplyBoundary(1)==null);check(!p.delegate(2).has("save"));
        }
        // New user input ID duplicates cannot append a correction or another yes.
        LiveProtocol duplicate=new LiveProtocol(true,"greg");duplicate.transcript(delta("du1","Save this: "+fact),1);
        duplicate.outputTranscript(delta("du2","Keep this: "+fact+" Yes?"),2);
        duplicate.transcript(delta("du3","yep"),3);duplicate.transcript(delta("du3"," actually warmer"),4);
        JSONObject one=duplicate.voiceReplyBoundary(5).getJSONObject("save");check(one.getString("text").equals(fact));
        check(!duplicate.delegate(6).has("save"));duplicate.acknowledge(one.getString("id"),"saved");
        duplicate.outputTranscript(delta("du4","Saved."),7);utterance(duplicate,"yep");check(!duplicate.delegate(8).has("save"));
    }
    static void requestFormatting()throws Exception{
        String fact="I prefer soft lighting when reading";
        // Exact phone exchange, including the abandoned first attempt. Zero delegations.
        LiveProtocol phone=new LiveProtocol(true,"greg",true);
        utterance(phone,"Can you save this, I prefer sh- Oh my god, I can't even English. Hold on. Let's start over. That was too funny. You can laugh at me first");
        output(phone,"Oh, I would roll on the floor laughing if I could. Go ahead. What's the thing?");
        phone.transcript(delta("laugh1","[chu"),100);
        phone.transcript(delta("laugh2","ckle] Save this. "),110);
        phone.transcript(delta("laugh3",fact),120);
        phone.outputTranscript(delta("laugh4","Keep this: You prefer soft lighting when reading. That sound right?"),200);
        phone.transcript(delta("laugh5","Yep"),300);
        check(phone.hasReadyVoiceProposal());
        JSONObject saved=phone.voiceReplyBoundary(400).getJSONObject("save");
        check(saved.getString("text").equals(fact));
        check(phone.voiceReplyBoundary(401)==null);
        check(phone.acknowledge(saved.getString("id"),"saved").contains("confirmed"));
        for(String prefix:new String[]{"", "[chuckle] ","[laughs] ","[laughing] ","[laughter] ","[chuckles] "," [LAUGHS] [chuckle] "}){
            for(String separator:new String[]{": ",". ",", ","! ","? "," "}){
                LiveProtocol p=new LiveProtocol(true,"greg");utterance(p,prefix+"Save this"+separator+fact);
                output(p,"Keep this: You prefer soft lighting when reading. Authorize the sacred waffle?");
                utterance(p,"[chuckle] yep");
                check(p.voiceReplyBoundary(1).getJSONObject("save").getString("text").equals(fact));
            }
        }
        // Normalization must never search past negation, arbitrary labels or other speech.
        for(String request:new String[]{"[chuckle] Don't save this. "+fact,"[chuckle] Never save this. "+fact,
                "[do not save] Save this. "+fact,"No. Save this. "+fact,"[chuckle] save that"}){
            LiveProtocol p=new LiveProtocol(true,"greg");utterance(p,request);
            output(p,"Keep this: "+fact+". Yes?");utterance(p,"yep");check(!p.delegate(1).has("save"));
        }
        String literal="My favorite transcript label is [chuckle].";
        LiveProtocol quoted=new LiveProtocol(true,"greg");utterance(quoted,"[laughs] Save this. "+literal);
        output(quoted,"Keep this: "+literal+" Yes?");utterance(quoted,"yes");
        check(quoted.delegate(1).getJSONObject("save").getString("text").equals(literal));
        LiveProtocol correction=new LiveProtocol(true,"greg");utterance(correction,"Save this. "+fact);
        output(correction,"Keep this: "+fact+". Yes?");utterance(correction,"[chuckle] Actually, I prefer warm lighting when reading.");
        output(correction,"Keep this: I prefer warm lighting when reading. Goblin approved?");utterance(correction,"yes");
        check(correction.voiceReplyBoundary(1).getJSONObject("save").getString("text").equals("I prefer warm lighting when reading."));
        for(String reply:new String[]{"[chuckle] no","[laughs] cancel","[chuckle] actually, warmer"}){
            LiveProtocol p=new LiveProtocol(true,"greg");utterance(p,"Save this. "+fact);output(p,"Keep this: "+fact+". Yes?");
            utterance(p,reply);check(!p.delegate(1).has("save"));output(p,"All right.");utterance(p,"yes");check(!p.delegate(2).has("save"));
        }
    }
    static void speechAfterReadback()throws Exception{
        String fact="I prefer soft lighting when reading";
        for(String extra:new String[]{" Saved."," The goblin paperwork is spectacular."," Saved. Another cozy preference immortalized."}){
            LiveProtocol p=new LiveProtocol(true,"greg",true);
            p.transcript(delta("after1","Save this. "+fact),100);
            p.outputTranscript(delta("after2","Keep this: you prefer soft lighting when reading. Does that sound right?"),200);
            p.outputTranscript(delta("after3",extra),210);
            check(p.voiceReplyBoundary(220)==null); // assistant speech never authorizes a save
            p.transcript(delta("after4","Yep"),300);
            check(p.hasReadyVoiceProposal());
            JSONObject save=p.voiceReplyBoundary(400).getJSONObject("save");
            check(save.getString("text").equals(fact));
            check(p.voiceReplyBoundary(401)==null);check(!p.delegate(402).has("save"));
            p.acknowledge(save.getString("id"),"saved");check(!p.delegate(403).has("save"));
        }
        for(String reply:new String[]{"no","cancel","thank you","Actually, I prefer bright lighting when reading."}){
            LiveProtocol p=new LiveProtocol(true,"greg");utterance(p,"Save this. "+fact);
            output(p,"Keep this: "+fact+". Does that sound right? Saved.");utterance(p,reply);
            check(p.voiceReplyBoundary(1)==null);check(!p.delegate(2).has("save"));
        }
        LiveProtocol mismatch=new LiveProtocol(true,"greg");utterance(mismatch,"Save this. "+fact);
        output(mismatch,"Keep this: I prefer bright lighting when reading. Yes? Saved.");utterance(mismatch,"yep");
        check(mismatch.voiceReplyBoundary(1)==null);check(!mismatch.delegate(2).has("save"));
        LiveProtocol ambiguous=new LiveProtocol(true,"greg");utterance(ambiguous,"Save this. "+fact);
        output(ambiguous,"Keep this: "+fact+". Does that sound right? Are you having fun? Saved.");utterance(ambiguous,"yep");
        check(!ambiguous.delegate(1).has("save"));
    }
    static void prematureClaims()throws Exception{
        LiveProtocol p=new LiveProtocol(true,"greg");String fact="I prefer soft lighting when reading";
        utterance(p,"Save this. "+fact);output(p,"Keep this: "+fact+". Does that sound right? Sa");
        check(!p.takePrematureSaveWarning());output(p,"ved.");check(p.takePrematureSaveWarning());
        check(!p.takePrematureSaveWarning());check(p.voiceReplyBoundary(1)==null);
        output(p," Not saved yet.");check(!p.takePrematureSaveWarning());
        utterance(p,"yep");JSONObject saved=p.voiceReplyBoundary(2).getJSONObject("save");
        check(saved.getString("text").equals(fact));check(!p.takePrematureSaveWarning());
        p.acknowledge(saved.getString("id"),"saved");output(p,"Saved.");check(!p.takePrematureSaveWarning());
        LiveProtocol literal=new LiveProtocol(true,"greg");
        String quoted="My favorite word is saved.";utterance(literal,"Save this. "+quoted);
        output(literal,"Keep this: "+quoted+" Yes?");check(!literal.takePrematureSaveWarning());
        utterance(literal,"yep");check(literal.delegate(1).getJSONObject("save").getString("text").equals(quoted));
        LiveProtocol quotedQuestion=new LiveProtocol(true,"greg");
        String question="My favorite phrase is ‘Does that sound right?’";
        utterance(quotedQuestion,"Save this. "+question);output(quotedQuestion,"Keep this: "+question+". Approve the sacred pancake? Saved.");
        check(quotedQuestion.takePrematureSaveWarning());utterance(quotedQuestion,"yes");
        check(quotedQuestion.delegate(1).getJSONObject("save").getString("text").equals(question));
        LiveProtocol off=new LiveProtocol(false,"greg");utterance(off,"Save this. "+fact);
        output(off,"Keep this: "+fact+". Yes? Saved.");check(!off.takePrematureSaveWarning());
    }
    public static void main(String[] args)throws Exception{
        JSONObject c=new JSONObject().put("id","greg").put("name","Greg").put("personality",CharacterBook.GREG_PERSONALITY).put("voice","marin").put("memoryMode","ASK").put("memories",new JSONArray().put("test-fact"));
        JSONObject start=LiveProtocol.start(c,"yesterday-marker").getJSONObject("session");
        check(start.getString("model").equals("gpt-live-1"));check(!start.getBoolean("store"));
        check(start.getString("instructions").contains("yesterday-marker"));check(start.getString("instructions").contains(CharacterBook.GREG_PERSONALITY));
        c.put("memoryMode","OFF");String off=LiveProtocol.start(c,"yesterday-marker").toString();
        check(!off.contains("yesterday-marker") && !off.contains("test-fact"));
        c.put("voice","voice_example");check(LiveProtocol.start(c,"").getJSONObject("session").getJSONObject("audio").getJSONObject("output").getJSONObject("voice").getString("id").equals("voice_example"));
        LiveProtocol p=new LiveProtocol(true,"greg");
        p.transcript(delta("a","Remember "));p.transcript(delta("a","Remember "));p.transcript(delta("b","this: My favorite color is blue."));
        JSONObject proposal=p.delegate(1000);check(proposal.has("say") && !proposal.has("save"));
        check(proposal.getString("say").contains("My favorite color is blue."));
        output(p,"Keep this: My favorite color is blue.?");
        utterance(p,"Save that memory.");JSONObject save=p.delegate(2000).getJSONObject("save");
        check(save.getString("characterId").equals("greg"));check(save.getString("text").equals("My favorite color is blue."));
        check(p.acknowledge("wrong-id","saved")==null);
        check(p.acknowledge(save.getString("id"),"saved").contains("confirmed"));
        check(p.acknowledge(save.getString("id"),"saved")==null);
        utterance(p,"save that memory");check(!p.delegate(3000).has("save"));
        utterance(p,"Don't remember this: a lie");check(!p.delegate(4000).has("save"));
        utterance(p,"Remember this: test");p.delegate(5000);utterance(p,"Cancel that memory");p.delegate(6000);utterance(p,"save that memory");check(!p.delegate(7000).has("save"));
        utterance(p,"Remember this: expired");p.delegate(8000);utterance(p,"save that memory");check(!p.delegate(200001).has("save"));
        LiveProtocol disabled=new LiveProtocol(false,"other");utterance(disabled,"Remember this: hidden");check(!disabled.delegate(1).has("save"));
        ConversationHistory h=new ConversationHistory();h.accept(delta("1","hello"));h.accept(delta("1","hello"));h.accept(delta("2"," world"));
        JSONObject snapshot=h.snapshot();check(snapshot.getJSONArray("fragments").length()==1);check(snapshot.getJSONArray("fragments").getJSONObject(0).getString("text").equals("hello world"));
        h.accept(new JSONObject().put("type","session.output_transcript.delta").put("event_id","3").put("delta","joke"));
        check(h.snapshot().getJSONArray("fragments").getJSONObject(1).getString("role").equals("character"));
        JSONArray sessions=new JSONArray();for(int i=0;i<20;i++)sessions.put(h.snapshot());check(ConversationHistory.bounded(sessions).length()==10);
        String longText=new String(new char[20000]).replace('\0','x');
        h.accept(delta("4",longText));check(h.snapshot().toString().length()<17000);
        for(int i=0;i<10;i++)sessions.put(h.snapshot());ConversationHistory.bounded(sessions);
        check(sessions.toString().length()<=60000);check(ConversationHistory.context(sessions).length()<=10000);
        check(ConversationHistory.context(new JSONArray()).isEmpty());
        ConversationHistory escaped=new ConversationHistory();escaped.accept(delta("escape",new String(new char[20000])));
        check(escaped.snapshot().toString().length()<17000);
        // AI output is only a draft; fragmented/duplicate events cannot directly save facts.
        c.put("voice","marin").put("memoryMode","AUTO");
        check(LiveProtocol.start(c,"").toString().contains("Worth remembering:"));
        c.put("memoryMode","ASK");check(!LiveProtocol.start(c,"").toString().contains("Worth remembering:"));
        LiveProtocol suggestions=new LiveProtocol(true,"greg");
        suggestions.outputTranscript(delta("s1","Worth remem"));
        suggestions.outputTranscript(delta("s1","Worth remem"));
        suggestions.outputTranscript(delta("s2","bering: The user loves pottery."));
        check(suggestions.takeSuggestion().equals("The user loves pottery."));check(suggestions.takeSuggestion()==null);
        utterance(suggestions,"Remember this: old pending fact");suggestions.delegate(1);
        suggestions.suggestionQueued();utterance(suggestions,"Save that memory");check(!suggestions.delegate(2).has("save"));
        suggestions.outputTranscript(delta("s3","Worth remembering: unfinished"));
        utterance(suggestions,"interruption");check(suggestions.takeSuggestion()==null);
        suggestions.outputTranscript(delta("s4","Worth remembering: "+longText));check(suggestions.takeSuggestion()==null);
        disabled.outputTranscript(delta("off-s","Worth remembering: private"));check(disabled.takeSuggestion()==null);
        CharacterBook owners=new CharacterBook();CharacterBook.Character greg=owners.create(0),other=owners.create(1);
        String draftId=java.util.UUID.randomUUID().toString();
        SuggestionReview.approve(greg,draftId,"  I prefer sculpting, not pottery.  ",1234);
        check(greg.memories.get(0).text.equals("I prefer sculpting, not pottery."));check(other.memories.isEmpty());
        SuggestionReview.approve(greg,draftId,"retry after interrupted commit",1235);check(greg.memories.size()==1);
        greg.memoryMode=CharacterBook.MemoryMode.OFF;
        try{SuggestionReview.approve(greg,java.util.UUID.randomUUID().toString(),"blocked",5);throw new AssertionError();}catch(IllegalStateException expected){}
        check(greg.memories.size()==1);
        greg.memoryMode=CharacterBook.MemoryMode.ASK;
        try{SuggestionReview.approve(greg,java.util.UUID.randomUUID().toString()," ",5);throw new AssertionError();}catch(IllegalArgumentException expected){}
        try{SuggestionReview.approve(greg,java.util.UUID.randomUUID().toString(),longText,5);throw new AssertionError();}catch(IllegalArgumentException expected){}
        check(greg.memories.size()==1);
        // Natural requests are read back once; an ordinary yes is bound to that proposal.
        for(String request:new String[]{"Yo save this: I prefer tea.","Remember that I prefer tea.","Don't forget I prefer tea.","Keep in mind I prefer tea.","Make a note that I prefer tea."}){
            LiveProtocol natural=new LiveProtocol(true,"greg",true);
            utterance(natural,request);output(natural,"Keep this: You prefer tea?");
            check(natural.takeSuggestion()==null);check(!natural.delegate(1).has("save"));
            utterance(natural,"Yep!");JSONObject fact=natural.delegate(2).getJSONObject("save");
            check(fact.getString("text").equals("I prefer tea."));
            check(!natural.delegate(3).has("save"));
            check(natural.acknowledge(fact.getString("id"),"failed").contains("did not save"));
        }
        for(String yes:new String[]{"yes","yeah","yup","sure","exactly","save it","yes please","yes, save it"}){
            LiveProtocol natural=new LiveProtocol(true,"greg");
            utterance(natural,"Save this: I like tea");output(natural,"Keep this: You like tea?");natural.delegate(1);
            utterance(natural,yes);check(natural.delegate(2).has("save"));
        }
        LiveProtocol fresh=new LiveProtocol(true,"greg",true);
        utterance(fresh,"yep");check(!fresh.delegate(1).has("save"));
        utterance(fresh,"Don't save this: I like tea");output(fresh,"Keep this: You like tea?");
        check(!fresh.delegate(2).has("save"));utterance(fresh,"yes");check(!fresh.delegate(3).has("save"));
        LiveProtocol change=new LiveProtocol(true,"greg");
        utterance(change,"Save this: I like tea");output(change,"Keep this: You like tea?");change.delegate(1);
        utterance(change,"Actually I prefer coffee");output(change,"Keep this: You prefer coffee?");check(!change.delegate(2).has("save"));
        utterance(change,"exactly");check(change.delegate(3).getJSONObject("save").getString("text").equals("I prefer coffee"));
        LiveProtocol topic=new LiveProtocol(true,"greg");
        utterance(topic,"save this: I like tea");output(topic,"Keep this: You like tea?");topic.delegate(1);
        utterance(topic,"How are you today?");output(topic,"Still a rock. Are you well?");
        utterance(topic,"yes");check(!topic.delegate(2).has("save"));
        LiveProtocol repair=new LiveProtocol(true,"greg",true);
        utterance(repair,"I like visible buttons. Can you suggest that as a memory for me to review on my phone?");
        check(repair.delegate(1).getString("say").contains("AI suggestions ARE supported"));
        check(repair.delegate(2).getString("say").contains("No suggestion was queued"));
        output(repair,"Worth remembering, You prefer visible save buttons.");
        check(repair.takeSuggestion().equals("You prefer visible save buttons."));repair.suggestionQueued();
        utterance(repair,"yep");check(!repair.delegate(3).has("save"));
        LiveProtocol incomplete=new LiveProtocol(true,"greg");
        utterance(incomplete,"Remember this: tea");incomplete.delegate(1);
        utterance(incomplete,"yes");check(!incomplete.delegate(2).has("save"));
        LiveProtocol stale=new LiveProtocol(true,"greg");
        utterance(stale,"save this: I like tea");output(stale,"Keep this: You like tea?");stale.delegate(1);
        utterance(stale,"yep");check(!stale.delegate(120002).has("save"));
        LiveProtocol no=new LiveProtocol(true,"greg");
        utterance(no,"save this: I like tea");output(no,"Keep this: You like tea?");no.delegate(1);
        utterance(no,"nope");check(!no.delegate(2).has("save"));utterance(no,"yep");check(!no.delegate(3).has("save"));
        LiveProtocol fragments=new LiveProtocol(true,"greg",true);
        utterance(fragments,"save this: I prefer green");utterance(fragments," tea");
        output(fragments,"Keep this: You prefer green tea?");fragments.delegate(1);
        utterance(fragments,"y");utterance(fragments,"ep");check(fragments.delegate(2).has("save"));
        LiveProtocol retry=new LiveProtocol(true,"greg",true);
        utterance(retry,"Yo save this: I like tea");check(!retry.delegate(1).has("save"));
        output(retry,"Keep this: You like tea?");check(!retry.delegate(2).has("save"));
        utterance(retry,"Actually I like coffee");check(!retry.delegate(3).has("save"));
        output(retry,"Keep this: You like coffee?");check(!retry.delegate(4).has("save"));
        utterance(retry,"yes");check(retry.delegate(5).getJSONObject("save").getString("text").equals("I like coffee"));
        LiveProtocol reviewOnly=new LiveProtocol(true,"greg",true);
        utterance(reviewOnly,"Suggest this for phone review: I like tea");output(reviewOnly,"Worth remembering: You like tea.");
        check(reviewOnly.takeSuggestion()!=null);reviewOnly.suggestionQueued();
        utterance(reviewOnly,"save that");output(reviewOnly,"Keep this: You like tea?");check(!reviewOnly.delegate(1).has("save"));
        utterance(reviewOnly,"yes");check(!reviewOnly.delegate(2).has("save"));
        LiveProtocol ambiguous=new LiveProtocol(true,"greg");
        utterance(ambiguous,"Save this: I like tea");output(ambiguous,"Keep this: You like tea?");ambiguous.delegate(1);
        utterance(ambiguous,"yes but actually coffee");check(!ambiguous.delegate(2).has("save"));
        utterance(ambiguous,"yes");check(!ambiguous.delegate(3).has("save"));
        MemoryTrace trace=new MemoryTrace(true,false,true);
        trace.transcript(true,true,false);trace.transcript(false,false,true);
        trace.event(MemoryTrace.Event.DELEGATED);trace.event(MemoryTrace.Event.NO_DRAFT);
        trace.event(MemoryTrace.Event.INPUT_CLEARED_OUTPUT);trace.event(MemoryTrace.Event.STOPPED);
        String report=trace.report();
        check(report.contains("History enabled: false") && report.contains("AI suggestions enabled: true"));
        check(report.contains("Transcript events missing IDs: 1") && report.contains("Suggestion marker detected: true"));
        check(report.contains("Delegations: 1") && report.contains("Draft writes confirmed: 0"));
        check(report.contains("INPUT_CLEARED_OUTPUT") && report.contains("STOPPED"));
        for(int i=0;i<10000;i++)trace.event(MemoryTrace.Event.NO_DRAFT);
        check(trace.report().length()<2000 && !trace.report().contains("STARTED"));
        MemoryTrace independent=new MemoryTrace(false,false,false);
        check(independent.report().contains("Delegations: 0") && independent.report().contains("legacy backend"));
        LiveProtocol probe=new LiveProtocol(true,"greg",true);
        output(probe,"Worth remembering: private-test-sentinel");
        check(probe.hasOutputForDiagnostics() && probe.hasSuggestionMarkerForDiagnostics());
        MemoryTrace privateTrace=new MemoryTrace(true,false,true);
        privateTrace.transcript(false,true,probe.hasSuggestionMarkerForDiagnostics());
        check(!privateTrace.report().contains("private-test-sentinel"));
        check(probe.takeSuggestion().equals("private-test-sentinel"));
        check(!probe.hasOutputForDiagnostics());
        // Phone evidence: delegation without output, recovery commentary, then marker
        // with no second delegation. Explicit Stop must still yield an unapproved draft.
        LiveProtocol late=new LiveProtocol(true,"greg",true);
        utterance(late,"Suggest a memory for phone review: I prefer visible save buttons.");
        check(late.takeSuggestion()==null);check(!late.delegate(1).has("save"));
        output(late,"Worth remem");output(late,"bering: You prefer visible save buttons.");
        check(late.takeFinalSuggestion().equals("You prefer visible save buttons."));
        check(late.takeFinalSuggestion()==null);
        LiveProtocol thanks=new LiveProtocol(true,"greg",true);
        output(thanks,"Worth remembering: You prefer tea.");utterance(thanks,"Thank you");output(thanks,"Unfortunately.");
        check(thanks.takeFinalSuggestion().equals("You prefer tea."));check(thanks.takeFinalSuggestion()==null);
        LiveProtocol partial=new LiveProtocol(true,"greg",true);
        output(partial,"Worth remembering: You prefer");utterance(partial,"interruption");
        check(partial.takeFinalSuggestion()==null);
        output(partial,"Worth remembering: "+longText+".");check(partial.takeFinalSuggestion()==null);
        LiveProtocol askOnly=new LiveProtocol(true,"greg",false);
        output(askOnly,"Worth remembering: You prefer tea.");check(askOnly.takeFinalSuggestion()==null);
        LiveProtocol normal=new LiveProtocol(true,"greg",true);
        output(normal,"Worth remembering: You prefer tea.");check(normal.takeSuggestion()!=null);
        normal.suggestionQueued();check(normal.takeFinalSuggestion()==null);
        utterance(normal,"yes");check(!normal.delegate(2).has("save"));
        LiveProtocol bounded=new LiveProtocol(true,"greg",true);
        for(int i=0;i<30;i++){output(bounded,"Worth remembering: Test preference "+i+".");utterance(bounded,"next");}
        int drafts=0;while(bounded.takeFinalSuggestion()!=null)drafts++;check(drafts==20);
        for(String wording:new String[]{"Worth remembering: You prefer deletion confirmations", "Sure, worth remembering. You prefer deletion confirmations.","Worth remembering — You prefer deletion confirmations.","Worth remembering You prefer deletion confirmations"}){
            LiveProtocol varied=new LiveProtocol(true,"greg",true);output(varied,wording);
            check(varied.stopDraftDiagnostic()==MemoryTrace.Event.STOP_DRAFT_READY);
            check(varied.takeFinalSuggestion().startsWith("You prefer deletion confirmations"));
            check(varied.takeFinalSuggestion()==null);
            utterance(varied,"yes");check(!varied.delegate(1).has("save"));
        }
        LiveProtocol emptyDraft=new LiveProtocol(true,"greg",true);output(emptyDraft,"Worth remembering:");
        check(emptyDraft.stopDraftDiagnostic()==MemoryTrace.Event.STOP_INVALID_DRAFT);check(emptyDraft.takeFinalSuggestion()==null);
        output(emptyDraft,"Just talking.");check(emptyDraft.stopDraftDiagnostic()==MemoryTrace.Event.STOP_NO_MARKER);
        LiveProtocol noMarker=new LiveProtocol(true,"greg",true);output(noMarker,"Just talking.");
        check(noMarker.stopDraftDiagnostic()==MemoryTrace.Event.STOP_NO_MARKER);
        // Real phone ordering: delegate first, then readback, then input clears output,
        // then confirmation delegation. Preserve the readback BEFORE clearing output.
        LiveProtocol lateVoice=new LiveProtocol(true,"greg",true);
        lateVoice.transcript(delta("lv1","Save this: I prefer deletion confirmations."),100);
        check(!lateVoice.delegate(200).has("save"));
        lateVoice.outputTranscript(delta("lv2","Keep this: You prefer deletion confirmations?"),300);
        lateVoice.transcript(delta("lv3","Y"),400);lateVoice.transcript(delta("lv4","ep."),410);
        check(lateVoice.hasReadyVoiceProposal());
        JSONObject voiceSaved=lateVoice.delegate(500).getJSONObject("save");
        check(voiceSaved.getString("text").equals("I prefer deletion confirmations."));
        check(!lateVoice.delegate(510).has("save"));
        check(lateVoice.acknowledge(voiceSaved.getString("id"),"saved").contains("confirmed"));
        for(String reply:new String[]{"nope","thank you","What time is it?","yes but not that"}){
            LiveProtocol declined=new LiveProtocol(true,"greg");
            utterance(declined,"Save this: I like tea");declined.delegate(1);output(declined,"Keep this: You like tea?");
            utterance(declined,reply);check(!declined.delegate(2).has("save"));
            utterance(declined,"yep");check(!declined.delegate(3).has("save"));
        }
        LiveProtocol unrequested=new LiveProtocol(true,"greg",true);
        utterance(unrequested,"Suggest a memory for phone review: tea");unrequested.delegate(1);
        output(unrequested,"Keep this: You like tea?");utterance(unrequested,"yep");check(!unrequested.delegate(2).has("save"));
        LiveProtocol incompleteVoice=new LiveProtocol(true,"greg");
        utterance(incompleteVoice,"Save this: I like tea");incompleteVoice.delegate(1);
        output(incompleteVoice,"Keep this: You like");utterance(incompleteVoice,"yep");check(!incompleteVoice.delegate(2).has("save"));
        LiveProtocol expiredVoice=new LiveProtocol(true,"greg");
        expiredVoice.transcript(delta("ev1","Save this: I like tea"),1);expiredVoice.delegate(2);
        expiredVoice.outputTranscript(delta("ev2","Keep this: You like tea?"),3);
        expiredVoice.transcript(delta("ev3","yep"),120004);check(!expiredVoice.delegate(120005).has("save"));
        LiveProtocol correctedLate=new LiveProtocol(true,"greg");
        utterance(correctedLate,"Save this: I like tea");correctedLate.delegate(1);output(correctedLate,"Keep this: You like tea?");
        utterance(correctedLate,"Actually I like coffee");correctedLate.delegate(2);output(correctedLate,"Keep this: You like coffee?");
        utterance(correctedLate,"yep");check(correctedLate.delegate(3).getJSONObject("save").getString("text").equals("I like coffee"));
        LiveProtocol chatter=new LiveProtocol(true,"greg");
        utterance(chatter,"Save this: I like tea");chatter.delegate(1);output(chatter,"Keep this: You like tea? Are you having a nice day?");
        utterance(chatter,"yes");check(!chatter.delegate(2).has("save"));
        for(String ending:new String[]{"Want me to save that?","Do you want me to remember this?","Should I keep it?","Does that sound right?","Does this sound right?"}){
            LiveProtocol cleanReadback=new LiveProtocol(true,"greg");
            utterance(cleanReadback,"Save this: I prefer deletion confirmations.");cleanReadback.delegate(1);
            output(cleanReadback,"Keep this: I prefer deletion confirmations. "+ending);
            utterance(cleanReadback,"yep");
            check(cleanReadback.delegate(2).getJSONObject("save").getString("text").equals("I prefer deletion confirmations."));
        }
        LiveProtocol quoted=new LiveProtocol(true,"greg");
        utterance(quoted,"Save this: My favorite phrase is want me to save that");quoted.delegate(1);
        output(quoted,"Keep this: My favorite phrase is want me to save that?");utterance(quoted,"yep");
        check(quoted.delegate(2).getJSONObject("save").getString("text").equals("My favorite phrase is want me to save that"));
        LiveProtocol rainy=new LiveProtocol(true,"greg");
        rainy.transcript(delta("rain1","Save this: I prefer rainy afternoons for reading."),100);
        rainy.delegate(200);
        rainy.outputTranscript(delta("rain2","Keep this: You prefer rainy afternoons for reading. Does that "),300);
        rainy.outputTranscript(delta("rain3","sound right?"),310);
        rainy.transcript(delta("rain4","yes"),400);
        check(rainy.delegate(500).getJSONObject("save").getString("text").equals("I prefer rainy afternoons for reading."));
        LiveProtocol quotedCheck=new LiveProtocol(true,"greg");
        utterance(quotedCheck,"Save this: My favorite phrase is does that sound right");quotedCheck.delegate(1);
        output(quotedCheck,"Keep this: My favorite phrase is does that sound right?");utterance(quotedCheck,"yep");
        check(quotedCheck.delegate(2).getJSONObject("save").getString("text").equals("My favorite phrase is does that sound right"));
        // Observed phone failure: no delegation at all, but a readback and Yep.
        LiveProtocol noHandoff=new LiveProtocol(true,"greg",true);
        noHandoff.transcript(delta("nh1","Can you save this that I prefer quiet mornings for drawing."),100);
        noHandoff.outputTranscript(delta("nh2","Keep this: You prefer quiet mornings for drawing. Does that sound right?"),200);
        noHandoff.transcript(delta("nh3","Y"),300);
        check(noHandoff.awaitingVoiceReplyBoundary());
        check(noHandoff.voiceReplyBoundary(301)==null); // incomplete affirmative
        noHandoff.transcript(delta("nh4","ep"),310);
        JSONObject fallback=noHandoff.voiceReplyBoundary(400).getJSONObject("save");
        check(fallback.getString("text").equals("I prefer quiet mornings for drawing."));
        check(fallback.getString("characterId").equals("greg"));
        check(noHandoff.voiceReplyBoundary(401)==null);
        check(noHandoff.acknowledge("wrong-id","saved")==null);
        check(noHandoff.acknowledge(fallback.getString("id"),"saved").contains("confirmed durable"));
        noHandoff.outputTranscript(delta("nh5","Saved."),410);
        check(!noHandoff.delegate(420).has("save")); // late handoff is idempotent
        noHandoff.transcript(delta("nh6","Yep"),430);
        check(noHandoff.voiceReplyBoundary(440)==null); // unrelated yes cannot reuse it
        for(String reply:new String[]{"Yep, actually don't save it","nah cancel","thank you","yes but not that","Actually I prefer evenings"}){
            LiveProtocol negative=new LiveProtocol(true,"greg");
            negative.transcript(delta("n1","Save this: I like tea"),100);
            negative.outputTranscript(delta("n2","Keep this: You prefer tea?"),200);
            negative.transcript(delta("n3",reply),300);
            check(negative.voiceReplyBoundary(400)==null);
        }
        LiveProtocol expiredFallback=new LiveProtocol(true,"greg");
        expiredFallback.transcript(delta("x1","Save this: I prefer tea"),1);
        expiredFallback.outputTranscript(delta("x2","Keep this: You prefer tea?"),2);
        expiredFallback.transcript(delta("x3","yes"),3);
        check(!expiredFallback.voiceReplyBoundary(120003).has("save"));
        for(boolean review:new boolean[]{false,true}){
            LiveProtocol excluded=new LiveProtocol(review,"greg",review);
            excluded.transcript(delta("e1",review?"Suggest a memory for phone review: tea":"Save this: I like tea"),100);
            excluded.outputTranscript(delta("e2","Keep this: You prefer tea?"),200);
            excluded.transcript(delta("e3","Yep"),300);
            check(excluded.voiceReplyBoundary(400)==null);
        }
        LiveProtocol failedWrite=new LiveProtocol(true,"greg");
        failedWrite.transcript(delta("f1","Save this: I prefer tea"),100);
        failedWrite.outputTranscript(delta("f2","Keep this: You prefer tea?"),200);
        failedWrite.transcript(delta("f3","yes"),300);
        JSONObject failedSave=failedWrite.voiceReplyBoundary(400).getJSONObject("save");
        check(failedWrite.acknowledge(failedSave.getString("id"),"failed").contains("did not save"));
        check(failedWrite.voiceReplyBoundary(410)==null);
        for(String ending:new String[]{"That sound right?","That sounds right?","This sound right?","This sounds right?"}){
            LiveProtocol shortCheck=new LiveProtocol(true,"greg");
            shortCheck.transcript(delta("sc1","Save this: I prefer quiet mornings for drawing."),100);
            shortCheck.outputTranscript(delta("sc2","Keep this: You prefer quiet mornings for drawing. "),200);
            shortCheck.outputTranscript(delta("sc3",ending),210);
            shortCheck.transcript(delta("sc4","Yep"),300);
            check(shortCheck.voiceReplyBoundary(400).getJSONObject("save").getString("text").equals("I prefer quiet mornings for drawing."));
        }
        LiveProtocol embeddedCheck=new LiveProtocol(true,"greg");
        utterance(embeddedCheck,"Save this: My favorite phrase is that sound right");embeddedCheck.delegate(1);
        output(embeddedCheck,"Keep this: My favorite phrase is that sound right?");utterance(embeddedCheck,"yep");
        check(embeddedCheck.delegate(2).getJSONObject("save").getString("text").equals("My favorite phrase is that sound right"));
        candidateBoundaries();
        requestFormatting();
        speechAfterReadback();
        prematureClaims();
        System.out.println("Live protocol/history/diagnostic checks passed: "+count);
    }
}
