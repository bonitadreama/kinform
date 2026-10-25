package org.kinform.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One local snapshot; portable backups are explicit through CharacterBackup. */
final class BookStore {
    private final android.content.SharedPreferences prefs;
    private String loadedSnapshot;
    BookStore(Context context) { prefs = context.getSharedPreferences("kinform.characters.v1", Context.MODE_PRIVATE); }
    CharacterBook load() throws JSONException {
        CharacterBook book = new CharacterBook();
        String raw = prefs.getString("snapshot", null);
        loadedSnapshot = raw;
        if (raw == null) { book.create(0); return book; }
        JSONObject root = new JSONObject(raw);
        if (root.getInt("schema") != 1) throw new JSONException("Unsupported character data version");
        JSONArray all = root.getJSONArray("characters");
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.getJSONObject(i);
            CharacterBook.Character c = new CharacterBook.Character(item.getString("id"), item.getString("name"), item.getString("body"), item.getString("personality"));
            // Upgrade only the unchanged old preset; preserve user-written personalities.
            if(c.personality.equals(CharacterBook.LEGACY_GREG_PERSONALITY))c.personality=CharacterBook.GREG_PERSONALITY;
            c.boundaries = item.getString("boundaries");
            c.voice = item.optString("voice", "marin");
            c.voiceLabel = item.optString("voiceLabel", "");
            if (!CharacterBook.validVoice(c.voice)) c.voice = "marin";
            try { c.memoryMode = CharacterBook.MemoryMode.valueOf(item.getString("memoryMode")); }
            catch (IllegalArgumentException e) { throw new JSONException("Unknown memory preference"); }
            c.initiate = item.getBoolean("initiate");
            c.quietStart = item.getString("quietStart"); c.quietEnd = item.getString("quietEnd");
            JSONArray memories = item.getJSONArray("memories");
            for (int m = 0; m < memories.length(); m++) {
                JSONObject memory = memories.getJSONObject(m);
                c.memories.add(new CharacterBook.Memory(memory.getString("id"), memory.getString("text"), memory.getLong("createdAt")));
            }
            book.characters.add(c);
        }
        return book;
    }
    void save(CharacterBook book) throws JSONException {
        if(!java.util.Objects.equals(loadedSnapshot,prefs.getString("snapshot",null)))throw new IllegalStateException("Saved data changed; reload first");
        JSONArray all = new JSONArray();
        for (CharacterBook.Character c : book.characters) {
            JSONArray memories = new JSONArray();
            for (CharacterBook.Memory m : c.memories) memories.put(new JSONObject().put("id", m.id).put("text", m.text).put("createdAt", m.createdAt));
            all.put(new JSONObject().put("id", c.id).put("name", c.name).put("body", c.body).put("personality", c.personality)
                .put("boundaries", c.boundaries).put("voice", c.voice).put("voiceLabel",c.voiceLabel).put("memoryMode", c.memoryMode.name()).put("initiate", c.initiate)
                .put("quietStart", c.quietStart).put("quietEnd", c.quietEnd).put("memories", memories));
        }
        String snapshot=new JSONObject().put("schema",1).put("characters",all).toString();
        if (!prefs.edit().putString("snapshot", snapshot).commit())
            throw new IllegalStateException("Device could not save character data");
        loadedSnapshot=snapshot;
    }
}
