import java.util.*;
import local.smartframe.transfer.core.*;
public class PhotoDeletionTest {
    static int checks;static void ok(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
    public static void main(String[] args){
        String a=Wire.hash("a"),b=Wire.hash("b"),c=Wire.hash("c");Set<String> frame=new HashSet<>(Arrays.asList(a,b,c));Set<String> phone=new HashSet<>(frame);
        PhotoDeletion.Sink sink=h->{if(h.equals(b))throw new Exception("storage read only");return frame.remove(h);};
        try{PhotoDeletion.delete(Arrays.asList(a,"../bad"),sink);throw new AssertionError("invalid selection accepted");}catch(IllegalArgumentException expected){ok(frame.size()==3,"entire request validated before deleting first photo");}
        try{PhotoDeletion.delete(Collections.nCopies(51,a),sink);throw new AssertionError("over-limit request accepted");}catch(IllegalArgumentException expected){ok(frame.size()==3,"oversized request has no side effects");}
        try{PhotoDeletion.requireVolume("old-usb","new-usb");throw new AssertionError("wrong storage accepted");}catch(IllegalArgumentException expected){checks++;}
        PhotoDeletion.requireVolume("usb","usb");checks++;
        PhotoDeletion.Result result=PhotoDeletion.delete(Arrays.asList(a,a,b),sink);
        ok(result.deleted.equals(Arrays.asList(a))&&result.failed.containsKey(b),"selected deletion deduplicates and reports partial failure");
        ok(frame.contains(b)&&frame.contains(c)&&!frame.contains(a),"failed and unselected frame photos preserved");
        ok(phone.size()==3&&phone.contains(a),"separate phone originals never passed to deletion sink");
        ok(PhotoDeletion.delete(Arrays.asList(a),sink).missing.equals(Arrays.asList(a)),"repeating after response loss reports already absent");
        try{PhotoDeletion.delete(Collections.emptyList(),sink);throw new AssertionError("empty request accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS: "+checks+" remote deletion checks");
    }
}
