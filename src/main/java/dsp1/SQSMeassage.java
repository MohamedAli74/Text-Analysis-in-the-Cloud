package dsp1;
import java.util.HashMap;

public class SQSMeassage {
    private String tag;
    private HashMap<String, String> attributes;
    
    public SQSMeassage(String tag, HashMap<String, String> attributes){
        this.tag = tag;
        this.attributes = attributes;
    }
    public String getTag(){
        return this.tag;
    }
    public HashMap<String, String> getAttributes(){
        return this.attributes;
    }

}
