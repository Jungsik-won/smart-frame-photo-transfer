import java.util.*;
import local.smartframe.transfer.core.PhotoSelection;

public class PhotoSelectionTest {
    static int checks;
    static void ok(boolean test,String name){checks++;if(!test)throw new AssertionError(name);}
    public static void main(String[] args){
        PhotoSelection s=new PhotoSelection(2);
        ok(s.toggle("a")==PhotoSelection.Change.ADDED&&s.contains("a"),"one tap selects");
        s.toggle("b");ok(s.toggle("c")==PhotoSelection.Change.FULL&&s.size()==2&&!s.contains("c"),"limit rejects without changing selection");
        ok(s.toggle("a")==PhotoSelection.Change.REMOVED&&!s.contains("a"),"second tap deselects even when full");
        s.toggle("c");ok(s.values().equals(Arrays.asList("b","c")),"replacement preserves order");
        ArrayList<String> snapshot=s.values();snapshot.clear();ok(s.size()==2,"result snapshot is independent");
        PhotoSelection rotated=new PhotoSelection(2);rotated.restore(s.values());ok(rotated.values().equals(s.values()),"recreation restores selected photos");
        rotated.retain(Arrays.asList("c","d"));ok(rotated.values().equals(Arrays.asList("c")),"unavailable photo is not returned");
        s.restore(Arrays.asList("a","a",null,"","b","c"));ok(s.values().equals(Arrays.asList("a","b")),"restoration rejects duplicates and enforces limit");
        PhotoSelection many=new PhotoSelection(200);for(int i=0;i<200;i++)many.toggle("photo"+i);ok(many.size()==200&&many.toggle("overflow")==PhotoSelection.Change.FULL,"200 photo boundary");
        System.out.println("PASS: "+checks+" gallery selection checks");
    }
}
