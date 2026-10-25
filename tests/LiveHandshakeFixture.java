package org.kinform.app;
import org.json.*;
public final class LiveHandshakeFixture {
    public static void main(String[] args)throws Exception{
        JSONObject c=new JSONObject().put("id","synthetic-check").put("name","Test character").put("body","Speaker")
            .put("personality","Wait quietly for the user to speak.").put("boundaries","").put("voice","marin").put("memoryMode","ASK").put("memories",new JSONArray());
        System.out.print(LiveProtocol.start(c,"User previously discussed a fictional purple garden."));
    }
}
