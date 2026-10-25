package org.kinform.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;

public final class MainActivity extends Activity {
    private static final int PAPER = 0xFFF6F4ED, INK = 0xFF222D26, MUTED = 0xFF606A60, GREEN = 0xFF365C43;
    private CharacterBook book;
    private BookStore store;
    private LinearLayout content;
    private Button headerSave;
    private String page = "home", selectedId;
    private String pendingExportId;
    private long lastHomeBack;
    private Object backDispatcher, backCallback;
    private int nextViewId;
    private TextView voiceStatus;
    private final android.os.Handler voiceUi = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statusRefresh = new Runnable() {
        @Override public void run() {
            if(voiceStatus != null) voiceStatus.setText(VoiceService.status);
            voiceUi.postDelayed(this,500);
        }
    };
    @Override public void onResume() { super.onResume(); voiceUi.post(statusRefresh); }
    @Override public void onPause() { lastHomeBack=0;voiceUi.removeCallbacks(statusRefresh); super.onPause(); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        registerModernBack();
        store = new BookStore(this);
        try { book = store.load(); }
        catch (Exception e) {
            base("Your characters are safe from changes");
            text("Kinform couldn't read its saved data. It has not overwritten it. Close the app and retain its data so we can investigate.", 17, MUTED);
            return;
        }
        if (state != null) { selectedId = state.getString("character"); page = state.getString("page", "home"); pendingExportId=state.getString("pendingExportId"); }
        render();
    }
    @Override public void onSaveInstanceState(Bundle state) {
        state.putString("character", selectedId); state.putString("page", page);
        state.putString("pendingExportId",pendingExportId);
        super.onSaveInstanceState(state);
    }
    private void render() {
        if(page.equals("settings")){appSettings();return;}
        CharacterBook.Character c = book.find(selectedId == null ? "" : selectedId);
        if (c == null || page.equals("home")) { home(); return; }
        switch (page) {
            case "edit": edit(c); break;
            case "memory": memories(c); break;
            case "preferences": preferences(c); break;
            case "connection": connection(c); break;
            default: character(c);
        }
    }
    private void go(String destination, CharacterBook.Character c) {
        lastHomeBack=0;
        try { book=store.load(); } catch(Exception e) { new AlertDialog.Builder(this).setMessage("Could not refresh saved characters. Close and reopen Kinform before editing.").setPositiveButton("OK",null).show();return; }
        page = destination; selectedId = c == null ? null : c.id; render();
    }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private void base(String title) {
        voiceStatus = null;
        nextViewId = 100;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(PAPER);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(16),dp(4),dp(16),dp(4));
        Button back=new Button(this);back.setText("‹ Back");back.setAllCaps(false);back.setTextColor(GREEN);back.setMinHeight(dp(48));
        back.setOnClickListener(v->handleBack());toolbar.addView(back,new LinearLayout.LayoutParams(-2,-2));
        View spacer=new View(this);toolbar.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        headerSave=new Button(this);headerSave.setText("Save");headerSave.setAllCaps(false);headerSave.setTextColor(GREEN);headerSave.setMinHeight(dp(48));headerSave.setVisibility(View.GONE);
        toolbar.addView(headerSave,new LinearLayout.LayoutParams(-2,-2));
        if(!page.equals("home"))root.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(PAPER);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(36));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets edges = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
            v.setPadding(edges.left, edges.top, edges.right, edges.bottom); return insets;
        });
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setStatusBarColor(PAPER); getWindow().setNavigationBarColor(PAPER);
        text("KINFORM", 14, GREEN).setLetterSpacing(.15f);
        TextView heading = text(title, 34, INK); heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        gap(12);
    }
    private void stickySave(Runnable action){headerSave.setVisibility(View.VISIBLE);headerSave.setOnClickListener(v->action.run());}
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(6)); view.setLineSpacing(dp(3), 1);
        content.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private void gap(int height) { View v = new View(this); content.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    private void divider() {
        gap(12); View v = new View(this); v.setBackgroundColor(0xFFDADFD5);
        content.addView(v, new LinearLayout.LayoutParams(-1, dp(1))); gap(12);
    }
    private void button(String label, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(16);
        button.setTextColor(primary ? Color.WHITE : GREEN); button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(18), dp(12), dp(18), dp(12)); button.setMinHeight(dp(52));
        GradientDrawable background = new GradientDrawable(); background.setColor(primary ? GREEN : 0xFFE7EBE1); background.setCornerRadius(dp(14));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8);
        content.addView(button, params); button.setOnClickListener(v -> action.run());
    }
    private EditText field(String label, String value, boolean multiline) {
        text(label, 14, GREEN);
        EditText input = new EditText(this); input.setId(nextViewId++); input.setText(value); input.setTextSize(17); input.setTextColor(INK);
        input.setPadding(dp(10), dp(10), dp(10), dp(10)); input.setSelectAllOnFocus(false);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setMinLines(multiline ? 3 : 1); input.setMaxLines(multiline ? 7 : 1);
        content.addView(input, new LinearLayout.LayoutParams(-1, -2)); return input;
    }
    private boolean save() {
        try {
            store.save(book);
            // A live session holds a context snapshot. End it after edits so forgotten facts
            // or a newly disabled memory policy cannot remain in a running conversation.
            if(VoiceService.active)startService(new Intent(this,VoiceService.class).setAction("stop"));
            return true;
        }
        catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Not saved").setMessage("Your changes could not be saved. A voice memory may have changed the saved data, or storage may be full. Go back and reopen this page before trying again.").setPositiveButton("OK", null).show();
            return false;
        }
    }
    private void home() {
        page = "home"; base("Your characters");
        text("A familiar voice. A body of their own.", 17, MUTED);
        button("App Settings",false,()->go("settings",null));
        voiceStatus = text(VoiceService.status, 13, GREEN);
        for (CharacterBook.Character c : book.characters) {
            divider();
            text(c.name, 28, INK); text(c.body + " · " + c.memories.size() + " saved memories", 14, MUTED);
            TextView preview = text(c.personality, 16, MUTED); preview.setMaxLines(2); preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            button("Open " + c.name + "  →", true, () -> go("character", c));
        }
        divider();
        button("＋  Create a character", false, () -> new AlertDialog.Builder(this).setTitle("Start with a personality")
            .setItems(new String[]{"Greg · sarcastic rock", "Patchy · warm plush companion", "Custom · make someone new"}, (dialog, which) -> {
                CharacterBook.Character c = book.create(which);
                if (save()) go("edit", c); else book.characters.remove(c);
            }).show());
        button("Import character backup",false,()->{
            if(!backupIdle())return;
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),56);
        });
        text("Stored on this phone. Export each character to keep a backup. Uninstalling Kinform removes local data. Backups include private memories in readable text; keep them somewhere you trust.", 13, MUTED);
    }
    private void character(CharacterBook.Character c) {
        page = "character"; base(c.name);
        text(c.body, 15, MUTED);
        voiceStatus = text(VoiceService.status, 14, GREEN);
        if (c.body.equalsIgnoreCase("Rock")) {
            View stone = new Stone(); stone.setContentDescription("Illustration of a resting rock");
            content.addView(stone, new LinearLayout.LayoutParams(-1, dp(150)));
        }
        text(c.personality, 18, INK);
        divider();
        button("Personality & body", true, () -> go("edit", c));
        button("Memories · " + c.memories.size(), false, () -> go("memory", c));
        button("Remembering & conversation", false, () -> go("preferences", c));
        button("Speaker & voice setup", false, () -> go("connection", c));
        button("Export character backup",false,()->{
            if(!backupIdle())return;
            new AlertDialog.Builder(this).setTitle("Back up " + c.name + "?")
                .setMessage("Includes personality, voice choice, preferences and all saved memories, even if remembering is off. The file is readable text, not encrypted. Choose a private location. Pending AI suggestion drafts, app API-key and backend settings are excluded. Conversation text may contain private information. Custom voices require access to the original OpenAI project. Includes all recent conversation excerpts still stored on this phone and the history preference, even when history is off. Older discarded sessions cannot be recovered.")
                .setNegativeButton("Cancel",null).setPositiveButton("Choose location",(d,w)->{
                    pendingExportId=c.id;
                    String filename=c.name.replaceAll("[^A-Za-z0-9_-]","_");if(filename.isEmpty())filename="character";
                    startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE,"Kinform-"+filename+"-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.US).format(new Date())+".json"),55);
                }).show();
        });
        divider();
        button("Delete character", false, () -> new AlertDialog.Builder(this).setTitle("Delete " + c.name + "?")
            .setMessage("This deletes this character and all of their saved memories from this phone. It cannot be undone.")
            .setNegativeButton("Keep", null).setPositiveButton("Delete", (d, w) -> {
                int index = book.characters.indexOf(c); book.characters.remove(c);
                if (save()) {try{HistoryStore.delete(this,c.id);SuggestionStore.delete(this,c.id);}catch(Exception e){backupMessage("Character deleted, but its history could not be removed. Keep app data private.");}go("home", null);} else book.characters.add(index, c);
            }).show());
    }
    private void edit(CharacterBook.Character c) {
        base("Make them yours");
        text("Presets are a starting point. Every part below is yours to edit.", 16, MUTED);
        EditText name = field("Name", c.name, false);
        EditText body = field("Body / physical form", c.body, false);
        text("This setup has a microphone and speaker. No camera or movement is connected.", 14, MUTED);
        EditText personality = field("Personality & fictional backstory", c.personality, true);
        EditText boundaries = field("Boundaries & speaking preferences", c.boundaries, true);
        text("Backstory stays separate from memories of your time together.", 14, MUTED);
        Runnable saveCharacter=() -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Give them a name"); return; }
            if (body.getText().toString().trim().isEmpty()) { body.setError("Describe their body"); return; }
            if (personality.getText().toString().trim().isEmpty()) { personality.setError("Describe their personality"); return; }
            String oldName = c.name, oldBody = c.body, oldPersonality = c.personality, oldBoundaries = c.boundaries;
            c.name = name.getText().toString().trim(); c.body = body.getText().toString().trim();
            c.personality = personality.getText().toString().trim(); c.boundaries = boundaries.getText().toString().trim();
            if (save()) go("character", c);
            else { c.name = oldName; c.body = oldBody; c.personality = oldPersonality; c.boundaries = oldBoundaries; }
        };
        stickySave(saveCharacter);
        button("Save character",true,saveCharacter);
    }
    private void memories(CharacterBook.Character c) {
        base(c.name + "’s memories");
        historyControls(c);
        suggestionControls(c);
        text("Only this character owns these memories. You can inspect and remove each one. Voice-confirmed memories appear here too. The newest memories, within a conversation-size budget, are used on the next call.", 16, MUTED);
        if (c.memoryMode == CharacterBook.MemoryMode.OFF) text("Memory is off. Existing memories are retained, but excluded from conversation context. Turn remembering on to add more.", 15, GREEN);
        else {
            EditText draft = field("Add something they should remember", "", true);
            button("Save memory for " + c.name, true, () -> {
                if (draft.getText().toString().trim().isEmpty()) { draft.setError("Write a memory first"); return; }
                try { c.addMemory(draft.getText().toString()); } catch (RuntimeException e) { draft.setError("Use 1–1000 characters; memory storage may be full."); return; }
                if (save()) go("memory", c); else c.memories.remove(c.memories.size() - 1);
            });
        }
        if (c.memories.isEmpty()) { divider(); text("Nothing remembered yet.", 20, INK); text("Their backstory lives in Personality. Memories are things you choose to save about your time together.", 15, MUTED); }
        for (CharacterBook.Memory m : c.memories) {
            divider(); text(m.text, 18, INK);
            text("Added by you · " + DateFormat.getDateInstance().format(new Date(m.createdAt)), 12, MUTED);
            button("Edit this memory",false,()->editMemory(c,m));
            button("Forget this memory", false, () -> new AlertDialog.Builder(this).setTitle("Forget this memory?")
                .setMessage(m.text).setNegativeButton("Keep", null).setPositiveButton("Forget", (d, w) -> {
                    int index = c.memories.indexOf(m); c.memories.remove(m);
                    if (save()) go("memory", c); else c.memories.add(index, m);
                }).show());
        }
    }
    private void editMemory(CharacterBook.Character c,CharacterBook.Memory original){
        if(!backupIdle())return;
        EditText input=new EditText(this);input.setText(original.text);input.setMinLines(3);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Edit saved memory").setView(input)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!backupIdle())return;
            String clean=input.getText().toString().trim();
            if(clean.isEmpty()){input.setError("Write a memory first");return;}
            try{
                BookStore fresh=new BookStore(this);CharacterBook latest=fresh.load();CharacterBook.Character owner=latest.find(c.id);
                if(owner==null)throw new IllegalStateException();
                int index=-1;for(int i=0;i<owner.memories.size();i++)if(owner.memories.get(i).id.equals(original.id)){index=i;break;}
                if(index<0 || !owner.memories.get(index).text.equals(original.text))throw new IllegalStateException();
                owner.memories.set(index,new CharacterBook.Memory(original.id,clean,original.createdAt));
                fresh.save(latest);dialog.dismiss();go("memory",owner);
            }catch(Exception e){backupMessage("Could not confirm the edit. Reopen Memories and check the entry before retrying.");}
        }));dialog.show();
    }
    private void suggestionControls(CharacterBook.Character c) {
        divider();text("AI memory suggestions",23,INK);
        button("Memory diagnostic · latest call",false,()->{
            if(!backupIdle())return;
            String report=MemoryDiagnostics.read(this,c.id);
            try{report+="\nPending drafts currently stored: "+SuggestionStore.load(this,c.id).length();}
            catch(Exception e){report+="\nPending drafts could not be read.";}
            new AlertDialog.Builder(this).setTitle("Memory diagnostic").setMessage(report).setPositiveButton("OK",null).show();
        });
        text("Unconfirmed drafts only. Review the wording carefully: AI can misunderstand. Approve saves the displayed text for this character; edit it first if needed. Reject discards the draft. Stop Talk before reviewing. Pending drafts stay on this phone and are excluded from backups.",14,MUTED);
        if(c.memoryMode!=CharacterBook.MemoryMode.AUTO)button("Set up AI suggestions",false,()->go("preferences",c));
        try {
            org.json.JSONArray items=SuggestionStore.load(this,c.id);
            if(items.length()==0)text("No suggestions waiting. With AI suggestions enabled, talk about something you would like Greg to know over time.",14,MUTED);
            for(int i=0;i<items.length();i++) {
                org.json.JSONObject item=items.getJSONObject(i);
                final String id=item.getString("id"),value=item.getString("text");
                text(value,17,INK);
                button("Review / edit suggestion",false,()->{
                    if(!backupIdle())return;
                    EditText edit=new EditText(this);edit.setText(value);edit.setMinLines(3);
                    edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                    edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
                    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Approve a memory for "+c.name).setView(edit)
                        .setNegativeButton("Keep pending",null).setNeutralButton("Reject",(d,w)->{
                            try{SuggestionStore.remove(this,c.id,id);go("memory",c);}catch(Exception e){backupMessage("Could not discard the draft. Please reopen Memories.");}
                        }).setPositiveButton("Approve & save",null).create();
                    dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                        if(!backupIdle())return;
                        String clean=edit.getText().toString().trim();
                        if(clean.isEmpty()){edit.setError("Write a memory first");return;}
                        try {
                            SuggestionStore.find(this,c.id,id);
                            BookStore fresh=new BookStore(this);CharacterBook latest=fresh.load();CharacterBook.Character owner=latest.find(c.id);
                            SuggestionReview.approve(owner,id,clean,System.currentTimeMillis());
                            // Repeat approval after a partial storage failure cannot duplicate the fact.
                            fresh.save(latest);
                            SuggestionStore.remove(this,c.id,id);
                            dialog.dismiss();go("memory",owner);backupMessage("Approved memory saved on this phone.");
                        }catch(Exception e){backupMessage("Could not confirm completion. Reopen Memories and check saved facts before retrying. Remembering must be on and storage available.");}
                    }));dialog.show();
                });
            }
        }catch(Exception e){text("Could not read pending suggestions. Existing data was kept.",14,MUTED);}
    }
    // Radio IDs 200..202 are assigned immediately below; subtracting 200 yields
    // the enum ordinal, not a resource lookup. Lint propagates the ID annotation.
    @android.annotation.SuppressLint("ResourceType")
    private void preferences(CharacterBook.Character c) {
        base("On your terms");
        text("Remembering", 23, INK);
        RadioGroup modes = new RadioGroup(this); modes.setId(nextViewId++);
        String[] labels = {"Off · don't use or add memories", "Ask me before remembering", "AI suggestions: review before saving"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = new RadioButton(this); r.setId(200 + i); r.setText(labels[i]); r.setTextColor(INK); r.setMinHeight(dp(52)); modes.addView(r);
        }
        modes.check(200 + c.memoryMode.ordinal()); content.addView(modes);
        text("In phone-only mode, ask naturally: save this, remember that, or don't forget. After a short readback, say yep to save or no to cancel. AI suggestions are optional during phone-only Talk. Your character reads out a proposed fact; Stop and open Memories to approve, edit or reject it. Suggestions are unconfirmed drafts, never automatically saved facts. Up to 20 pending drafts are kept locally; pending drafts are not included in character backups. Saved memories stay on this phone; uninstalling or clearing app data deletes them. Use Export character backup on the character page to keep a copy, including memories while remembering is off.", 14, MUTED);
        divider(); text("Starting conversations", 23, INK);
        Switch initiate = new Switch(this); initiate.setId(nextViewId++); initiate.setText("Let them speak first"); initiate.setTextColor(INK); initiate.setMinHeight(dp(56)); initiate.setChecked(c.initiate); content.addView(initiate);
        EditText start = field("Quiet hours begin (24-hour HH:MM)", c.quietStart, false);
        EditText end = field("Quiet hours end (24-hour HH:MM)", c.quietEnd, false);
        text("Speak-first and quiet hours are saved preferences for a future feature. Voice starts only when you tap Talk. No wake-word listening or automatic starts yet.", 14, MUTED);
        Runnable savePreferences=() -> {
            String a = start.getText().toString().trim(), b = end.getText().toString().trim();
            String pattern = "(?:[01][0-9]|2[0-3]):[0-5][0-9]";
            if (!a.matches(pattern)) { start.setError("Use HH:MM, like 21:00"); return; }
            if (!b.matches(pattern)) { end.setError("Use HH:MM, like 09:00"); return; }
            if (a.equals(b)) { end.setError("Choose a different end time"); return; }
            CharacterBook.MemoryMode oldMode = c.memoryMode; boolean oldInitiate = c.initiate;
            String oldStart = c.quietStart, oldEnd = c.quietEnd;
            c.memoryMode = CharacterBook.MemoryMode.values()[modes.getCheckedRadioButtonId() - 200];
            c.initiate = initiate.isChecked(); c.quietStart = a; c.quietEnd = b;
            if (save()) go("character", c);
            else { c.memoryMode = oldMode; c.initiate = oldInitiate; c.quietStart = oldStart; c.quietEnd = oldEnd; }
        };
        stickySave(savePreferences);
        button("Save preferences",true,savePreferences);
    }
    private void connection(CharacterBook.Character c) {
        base("A voice for " + c.name);
        voiceStatus=text(VoiceService.status,22,INK);
        text("Voice choice, personality and memories belong only to " + c.name + ".",15,MUTED);
        text(ApiKeyStore.direct(this)?"Shared connection: phone-only":"Shared connection: private backend",14,GREEN);
        button("App Settings · key & connection for all characters",false,()->go("settings",c));
        text("Audio and character context go to OpenAI during Talk. Audio is not saved by Kinform. Optional conversation history is managed in this character's Memories. API provider data policies apply.",14,MUTED);
        text("AI-generated voice · " + (c.voice.startsWith("voice_")?c.voiceLabel+" (custom)":c.voice),14,GREEN);
        button("Choose preset voice",false,()->{
            if(VoiceService.active){new AlertDialog.Builder(this).setMessage("Stop listening before choosing another voice.").setPositiveButton("OK",null).show();return;}
            int selected=java.util.Arrays.asList(CharacterBook.VOICES).indexOf(c.voice);
            new AlertDialog.Builder(this).setTitle("A voice for " + c.name)
                .setSingleChoiceItems(CharacterBook.VOICES,selected,(dialog,index)->{
                    String previous=c.voice;c.voice=CharacterBook.VOICES[index];
                    if(save()){dialog.dismiss();go("connection",c);}else c.voice=previous;
                }).setNegativeButton("Cancel",null).show();
        });
        button("Custom voices · design or choose",false,()->{
            if(VoiceService.active){new AlertDialog.Builder(this).setMessage("Stop listening before changing voices.").setPositiveButton("OK",null).show();return;}
            VoiceDesigner.library(this,c.name,(id,name)->{
                if(VoiceService.active){new AlertDialog.Builder(this).setMessage("Your voice is in the library. Stop listening, then select it there.").setPositiveButton("OK",null).show();return;}
                try {
                    BookStore fresh=new BookStore(this);CharacterBook latest=fresh.load();
                    for(CharacterBook.Character target:latest.characters)if(target.id.equals(c.id)) {
                        if(!CharacterBook.validVoice(id))throw new Exception();
                        target.voice=id;target.voiceLabel=name;fresh.save(latest);
                        go("connection",target);return;
                    }
                    throw new Exception();
                }catch(Exception e){new AlertDialog.Builder(this).setMessage("The custom voice may be saved in the library, but could not be assigned. Reopen this character and select it from Custom voices.").setPositiveButton("OK",null).show();}
            });
        });
        text("Choose a voice, then tap Talk and say hello to hear it through your object. Stop before trying another. Each trial uses a paid API voice session. Your choice is saved just for this character.",14,MUTED);
        text("Conversation model: GPT-Live",14,GREEN);
        text("Phone-only memory: ask naturally, like save this or don't forget. After the short readback, say yep to save or no to cancel. Nothing is saved until you confirm and the phone reports success. AI suggestions are reviewed separately in Memories. The legacy backend still uses Remember this and Save that memory.",14,MUTED);
        button("Talk with " + c.name,true,()->prepareVoice(c));
        button("Stop listening",false,()->{
            if(VoiceService.active)startService(new Intent(this,VoiceService.class).setAction("stop"));
        });
        text("Starts only when you tap Talk. Continues with the screen locked, with an active-session notification. Bluetooth loss ends the session. First tests are limited to five minutes.",14,MUTED);
        button("Open Bluetooth settings",false,()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
    }
    private void appSettings(){
        base("App Settings");
        text("Connection & API key",23,INK);
        text("Shared by ALL characters on this phone. Changing the key or connection changes how every character connects. Their personalities, selected voices and memories stay separate and are not changed here.",16,MUTED);
        text("Custom voices require access to their original OpenAI project. Switching keys or projects may make a character's selected custom voice unavailable.",14,MUTED);
        voiceStatus=text(VoiceService.status,14,GREEN);
        if(VoiceService.active)button("Stop current voice session",false,()->startService(new Intent(this,VoiceService.class).setAction("stop")));
        boolean direct=ApiKeyStore.direct(this);
        text(direct?"Phone-only · your own OpenAI API key. No computer or Kinform server required. Internet and a separately billed OpenAI API account are required.":"Advanced backend mode · your own server must be running.",16,GREEN);
        button("Connection mode",false,()->{
            if(!settingsIdle())return;
            new AlertDialog.Builder(this).setTitle("How should Kinform connect?").setSingleChoiceItems(new String[]{"Phone-only · my OpenAI key","Advanced · my private backend"},direct?0:1,(d,w)->{ApiKeyStore.mode(this,w==0);d.dismiss();go("settings",book.find(selectedId));}).setNegativeButton("Cancel",null).show();
        });
        if(direct){
            try{ApiKeyStore.load(this);text("Personal key available on this phone",14,GREEN);}catch(Exception e){text("Add your own API key to start",14,MUTED);}
            button("Add / replace my API key",false,()->{if(settingsIdle())keyDialog();});
            button("Remove my API key",false,()->{if(!settingsIdle())return;try{ApiKeyStore.forget(this);go("settings",book.find(selectedId));}catch(Exception e){backupMessage("Could not remove key storage. Revoke the key in your OpenAI account.");}});
            text("Your key is never included in character exports. Remembered keys are encrypted using Android Keystore; a compromised phone or app can still access a key during use. Use a dedicated project/key, monitor API usage and revoke it when needed. Five-minute sessions are a local safety limit, not an account spending cap.",13,MUTED);
        }
        if(!direct){divider();text("Your backend",22,INK);
        try {text(ConnectionStore.load(this).getString("url"),14,MUTED);}catch(Exception e){text("No connection file imported",15,MUTED);}
        button("Import private connection file",false,()->{
            if(VoiceService.active){new AlertDialog.Builder(this).setMessage("Stop the current session before changing its connection.").setPositiveButton("OK",null).show();return;}
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick,42);
        });
        text("Import kinform-connection.json from your own backend. Treat it like a password. Never import someone else's file unless you trust their server with your audio.",14,MUTED);
        button("Forget backend connection",false,()->{
            if(!settingsIdle())return;
            ConnectionStore.forget(this);go("settings",book.find(selectedId));
        });
        }
    }
    private boolean settingsIdle(){
        if(!VoiceService.active)return true;
        backupMessage("Stop the current voice session and wait for it to finish before changing shared connection settings.");return false;
    }
    private void keyDialog(){
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(20),dp(12),dp(20),dp(12));
        TextView help=new TextView(this);help.setText("Enter your own OpenAI API key. Kinform sends it only to api.openai.com. API usage is billed to you, separately from ChatGPT. Without Remember, the key is held only while this app process lives.");layout.addView(help);
        EditText key=new EditText(this);key.setHint("Your API key");key.setSingleLine(true);
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);key.setSaveEnabled(false);
        key.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(520)});layout.addView(key);
        CheckBox remember=new CheckBox(this);remember.setText("Remember encrypted on this phone");layout.addView(remember);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("My personal API key").setView(layout).setNegativeButton("Cancel",null).setPositiveButton("Use my key",null).create();
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnDismissListener(d->{key.setText("");});
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{ApiKeyStore.save(this,key.getText().toString(),remember.isChecked());key.setText("");dialog.dismiss();go("settings",book.find(selectedId));}
            catch(Exception e){key.setError("Check the key format. Secure storage may also be unavailable.");}
        }));dialog.show();
    }
    private void historyControls(CharacterBook.Character c){
        text("Recent conversation history",23,INK);
        text("Optional, per-character continuity in phone-only mode. Keeps excerpts from up to 10 sessions (about 60,000 characters total); the newest 10,000 characters are sent on the next Talk. Older excerpts are removed automatically. Transcription can be wrong or incomplete, and generated speech may not have played. No audio recordings. History is local and included in new character backups. Export regularly to keep a copy.",14,MUTED);
        text("Remembering Off prevents history saving and use. Switching history off keeps existing excerpts for you to inspect; Delete removes them. A crash can lose the current session. Previously spoken conversations cannot be recovered retroactively.",14,MUTED);
        button(HistoryStore.enabled(this,c.id)?"Turn conversation history off":"Enable conversation history",false,()->{
            if(!backupIdle())return;
            try{HistoryStore.enable(this,c.id,!HistoryStore.enabled(this,c.id));go("memory",c);}catch(Exception e){backupMessage("Could not save history preference.");}
        });
        button("Read recent conversations",false,()->{
            try{
                org.json.JSONArray sessions=HistoryStore.load(this,c.id);
                String value=ConversationHistory.display(sessions);
                new AlertDialog.Builder(this).setTitle("Recent excerpts · "+sessions.length()+" sessions").setMessage(value.isEmpty()?"No conversation history saved yet.":value).setPositiveButton("OK",null).show();
            }catch(Exception e){backupMessage("Could not read conversation history. It was not overwritten.");}
        });
        button("Delete conversation history",false,()->{
            if(!backupIdle())return;
            new AlertDialog.Builder(this).setTitle("Delete recent conversations?").setMessage("Removes this character's history from this phone and switches history off. Explicitly saved facts are kept. This cannot be undone.").setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{
                try{HistoryStore.delete(this,c.id);go("memory",c);}catch(Exception e){backupMessage("Could not delete history.");}
            }).show();
        });divider();
    }
    private void prepareVoice(CharacterBook.Character requested) {
        if(VoiceService.active){new AlertDialog.Builder(this).setMessage("A voice session is already active. Stop it before switching characters.").setPositiveButton("OK",null).show();return;}
        final CharacterBook.Character c;
        try{c=new BookStore(this).load().find(requested.id);if(c==null)throw new Exception();}
        catch(Exception e){backupMessage("Could not refresh this character's saved memories. Reopen the character before starting.");return;}
        try{if(ApiKeyStore.direct(this))ApiKeyStore.load(this);else ConnectionStore.load(this);}catch(Exception e){backupMessage(ApiKeyStore.direct(this)?"Open App Settings to add your own OpenAI API key first.":"Open App Settings to import your backend connection file first.");return;}
        java.util.ArrayList<String> permissions=new java.util.ArrayList<>();
        for(String permission:new String[]{"android.permission.RECORD_AUDIO","android.permission.BLUETOOTH_CONNECT"})
            if(checkSelfPermission(permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED)permissions.add(permission);
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)permissions.add("android.permission.POST_NOTIFICATIONS");
        if(!permissions.isEmpty()){requestPermissions(permissions.toArray(new String[0]),43);return;}
        android.media.AudioManager audio=getSystemService(android.media.AudioManager.class);
        java.util.ArrayList<android.media.AudioDeviceInfo> devices=new java.util.ArrayList<>();
        for(android.media.AudioDeviceInfo device:audio.getAvailableCommunicationDevices())if(VoiceService.bluetooth(device))devices.add(device);
        if(devices.isEmpty()){new AlertDialog.Builder(this).setMessage("No Bluetooth call-audio device is available. Connect a Bluetooth device with a microphone and speaker, enable call audio in its Bluetooth settings, then try again.").setPositiveButton("OK",null).show();return;}
        String[] names=new String[devices.size()];for(int i=0;i<names.length;i++)names[i]=devices.get(i).getProductName().toString();
        new AlertDialog.Builder(this).setTitle("Choose the object's microphone & speaker").setItems(names,(d,index)->{
            try {
                org.json.JSONArray memories=new org.json.JSONArray();
                java.util.List<CharacterBook.Memory> saved=c.conversationMemories();
                java.util.ArrayList<String> context=new java.util.ArrayList<>();int budget=6000;
                for(int i=saved.size()-1;i>=0 && context.size()<30;i--){String fact=saved.get(i).text;if(fact.length()<=1000 && fact.length()<=budget){context.add(0,fact);budget-=fact.length();}}
                for(String fact:context)memories.put(fact);
                org.json.JSONObject character=new org.json.JSONObject().put("id",c.id).put("name",c.name).put("body",c.body)
                    .put("personality",c.personality).put("voice",c.voice).put("boundaries",c.boundaries).put("memoryMode",c.memoryMode.name()).put("memories",memories);
                startForegroundService(new Intent(this,VoiceService.class).putExtra("character",character.toString()).putExtra("deviceId",devices.get(index).getId()));
            }catch(Exception e){new AlertDialog.Builder(this).setMessage("Could not start this character's voice session.").setPositiveButton("OK",null).show();}
        }).show();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==43)new AlertDialog.Builder(this).setMessage("Tap Talk again when microphone and Nearby devices permissions are enabled.").setPositiveButton("OK",null).show();
    }
    @Override public void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==55 || request==56){
            if(result==RESULT_OK && data!=null && data.getData()!=null && backupIdle()){
                if(request==55)exportBackup(data.getData());else importBackup(data.getData());
            }else if(request==55)pendingExportId=null;
            return;
        }
        if(request!=42 || result!=RESULT_OK || data==null || data.getData()==null)return;
        try(java.io.InputStream input=getContentResolver().openInputStream(data.getData());java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[1024];int n;
            while((n=input.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>12000)throw new Exception();}
            String raw=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONObject candidate=ConnectionStore.validate(raw);
            new AlertDialog.Builder(this).setTitle("Trust this backend?")
                .setMessage("This server will receive your voice and character context:\n\n"+candidate.getString("url")+"\n\nConfirm only if this file came from your own backend.")
                .setNegativeButton("Cancel",null).setPositiveButton("Trust & save",(dialog,which)->{
                    try{ConnectionStore.save(this,raw);render();}catch(Exception e){new AlertDialog.Builder(this).setMessage("Could not securely save the connection.").setPositiveButton("OK",null).show();}
                }).show();
        }catch(Exception e){new AlertDialog.Builder(this).setMessage("This isn't a valid Kinform connection file, or its certificate has expired.").setPositiveButton("OK",null).show();}
    }
    private boolean backupIdle(){
        if(!VoiceService.active)return true;
        backupMessage("Stop the voice session before exporting or importing a character.");return false;
    }
    private void backupMessage(String message){
        if(!isFinishing() && !isDestroyed())new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show();
    }
    private void exportBackup(android.net.Uri uri){
        final String raw;
        try{
            CharacterBook latest=new BookStore(this).load();CharacterBook.Character c=latest.find(pendingExportId);
            if(c==null)throw new Exception();raw=CharacterBackup.encode(c,HistoryStore.enabled(this,c.id),HistoryStore.load(this,c.id));
        }catch(Exception e){pendingExportId=null;backupMessage("Could not prepare a complete backup. Your character is unchanged. The selected file may be empty; do not rely on it.");return;}
        pendingExportId=null;
        new Thread(()->{
            String message;
            try(java.io.OutputStream output=getContentResolver().openOutputStream(uri,"wt")){
                if(output==null)throw new Exception();output.write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));output.flush();
            }catch(Exception e){runOnUiThread(()->backupMessage("Backup could not be completed. Your character is unchanged. The destination may contain a partial file; export again to a new file."));return;}
            message="Backup exported. Keep the file private. To verify it, import it as a separate copy and compare its memories, recent conversations and settings.";
            final String result=message;runOnUiThread(()->backupMessage(result));
        },"kinform-export").start();
    }
    private void importBackup(android.net.Uri uri){
        new Thread(()->{
            try(java.io.InputStream input=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){
                if(input==null)throw new Exception();byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1){if(bytes.size()+n>CharacterBackup.MAX_BYTES)throw new Exception();bytes.write(buffer,0,n);}
                java.nio.charset.CharsetDecoder decoder=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
                CharacterBackup.Restored candidate=CharacterBackup.restore(decoder.decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString());
                runOnUiThread(()->confirmBackup(candidate));
            }catch(Exception e){runOnUiThread(()->backupMessage("This is not a supported complete Kinform character backup, or the file could not be read. Maximum size: 8 MiB. No characters were changed."));}
        },"kinform-import").start();
    }
    private void confirmBackup(CharacterBackup.Restored restored){
        CharacterBook.Character candidate=restored.character;
        if(isFinishing() || isDestroyed())return;
        String message="Recent conversation sessions: "+restored.sessions.length()+"\nHistory enabled: "+restored.historyEnabled+"\nWhen enabled and remembering is on, imported excerpts are sent to OpenAI on Talk. Review only trusted backups.\n\nName: "+candidate.name+"\nBody: "+candidate.body+"\nMemories: "+candidate.memories.size()+"\nRemembering: "+candidate.memoryMode.name()+"\nVoice: "+(candidate.voice.startsWith("voice_")?candidate.voiceLabel+" (custom)":candidate.voice)+"\n\nPersonality:\n"+candidate.personality+
            "\n\nImport creates a separate character with new IDs. Existing characters are never replaced. Only import files you trust: personality text influences how the character behaves."+
            (candidate.voice.startsWith("voice_")?"\n\nThis custom voice requires access to the original OpenAI project. A preset can be selected if it is unavailable.":"");
        new AlertDialog.Builder(this).setTitle("Restore as a separate character?").setMessage(message)
            .setNegativeButton("Cancel",null).setPositiveButton("Import copy",(d,w)->{
                if(!backupIdle())return;
                try{
                    BookStore fresh=new BookStore(this);CharacterBook latest=fresh.load();
                    // Write history under a fresh ID first: a failed or interrupted import cannot expose a character missing its history.
                    HistoryStore.restoreNew(this,candidate.id,restored.historyEnabled,restored.sessions);
                    latest.characters.add(candidate);
                    fresh.save(latest); // Keep history if commit reports failure: Android may have updated its in-memory preferences.
                    go("character",candidate);
                    backupMessage("Imported character and conversation history as a separate copy. Existing characters were kept.");
                }catch(Exception e){backupMessage("Could not confirm the imported character was saved. Existing characters were not replaced. Reopen the character list and check for an imported copy before retrying; storage may be full.");}
            }).show();
    }
    // Public API reflection keeps the local API-31 compiler compatible with Android 13+.
    private void registerModernBack() {
        if(android.os.Build.VERSION.SDK_INT<33)return;
        try {
            Class<?> callbackType=Class.forName("android.window.OnBackInvokedCallback");
            Class<?> dispatcherType=Class.forName("android.window.OnBackInvokedDispatcher");
            backDispatcher=Activity.class.getMethod("getOnBackInvokedDispatcher").invoke(this);
            backCallback=java.lang.reflect.Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,method,args)->{
                if(method.getName().equals("onBackInvoked")){handleBack();return null;}
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];
                if(method.getName().equals("toString"))return "KinformBackCallback";
                return null;
            });
            dispatcherType.getMethod("registerOnBackInvokedCallback",int.class,callbackType).invoke(backDispatcher,0,backCallback);
        }catch(Exception e){android.util.Log.e("Kinform","Could not register system Back navigation");}
    }
    @Override public void onDestroy(){
        if(backDispatcher!=null && backCallback!=null)try{
            Class.forName("android.window.OnBackInvokedDispatcher").getMethod("unregisterOnBackInvokedCallback",Class.forName("android.window.OnBackInvokedCallback")).invoke(backDispatcher,backCallback);
        }catch(Exception ignored){}
        super.onDestroy();
    }
    // API 33+ uses registerModernBack() above; this is the older Android fallback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBack(); }
    private void handleBack() {
        if(page.equals("settings")){
            CharacterBook.Character origin=book==null?null:book.find(selectedId);
            go(origin==null?"home":"connection",origin);return;
        }
        if (!page.equals("home") && book != null) {
            if (page.equals("character")) go("home", null); else go("character", book.find(selectedId));
        } else {
            long now=android.os.SystemClock.elapsedRealtime();
            if(lastHomeBack!=0 && now-lastHomeBack<2000){lastHomeBack=0;finish();return;}
            lastHomeBack=now;
            Toast.makeText(this,VoiceService.active?"Press Back again to exit. Voice continues until you tap Stop.":"Press Back again to exit",Toast.LENGTH_SHORT).show();
        }
    }
    private final class Stone extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Stone() { super(MainActivity.this); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            paint.setColor(0xFFE0E4D8); canvas.drawOval(w * .18f, h * .76f, w * .82f, h * .89f, paint);
            Path p = new Path(); p.moveTo(w * .21f, h * .73f); p.cubicTo(w * .17f, h * .51f, w * .3f, h * .19f, w * .49f, h * .19f);
            p.cubicTo(w * .68f, h * .13f, w * .77f, h * .44f, w * .8f, h * .7f);
            p.cubicTo(w * .72f, h * .86f, w * .32f, h * .86f, w * .21f, h * .73f);
            paint.setColor(0xFF89927D); canvas.drawPath(p, paint);
            paint.setColor(0xFFAEB6A1); paint.setStrokeWidth(dp(2)); canvas.drawLine(w * .33f, h * .4f, w * .42f, h * .3f, paint);
        }
    }
}
