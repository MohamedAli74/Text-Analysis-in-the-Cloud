package dsp1;

import java.util.HashMap;

public class SQSMessage {

    private HashMap<String, String> parts;
    

    public SQSMessage(HashMap<String, String> parts) {
        this.parts = parts;
    }

    public HashMap<String, String> getParts() {
        return this.parts;
    }

}