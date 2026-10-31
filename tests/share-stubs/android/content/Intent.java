package android.content;
import android.os.Parcelable;
import java.util.ArrayList;
public final class Intent {
    public static final String ACTION_SEND="android.intent.action.SEND", ACTION_SEND_MULTIPLE="android.intent.action.SEND_MULTIPLE", EXTRA_STREAM="android.intent.extra.STREAM";
    public String action,type;public Parcelable stream;public ArrayList<Parcelable> streams;public ClipData clip;
    public Intent(String action,String type){this.action=action;this.type=type;}
    public String getAction(){return action;}public String getType(){return type;}
    public Parcelable getParcelableExtra(String key){return stream;}
    public ArrayList<Parcelable> getParcelableArrayListExtra(String key){return streams;}
    public ClipData getClipData(){return clip;}
}
