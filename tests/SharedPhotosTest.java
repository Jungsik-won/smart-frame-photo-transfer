package local.smartframe.sender;
import android.content.*;
import android.net.Uri;
import android.os.Parcelable;
import java.util.ArrayList;
public final class SharedPhotosTest {
    private static int passed;
    private static void check(boolean ok){if(!ok)throw new AssertionError();passed++;}
    private static void rejects(Intent i){try{SharedPhotos.read(i);throw new AssertionError("Accepted invalid share");}catch(IllegalArgumentException expected){passed++;}}
    public static void main(String[] args){
        Uri a=Uri.parse("content://media/external/images/media/1"),b=Uri.parse("content://gallery.fileprovider/shared/2");
        Intent single=new Intent(Intent.ACTION_SEND,"image/jpeg");single.stream=a;
        check(SharedPhotos.read(single).size()==1);
        single.clip=new ClipData(a,b);check(SharedPhotos.read(single).size()==2);
        single.stream=null;check(SharedPhotos.read(single).get(1).equals(b));
        Intent multi=new Intent(Intent.ACTION_SEND_MULTIPLE,"image/*");multi.streams=new ArrayList<>();multi.streams.add(a);multi.streams.add(b);multi.clip=new ClipData(a,b);
        check(SharedPhotos.read(multi).size()==2);
        check(SharedPhotos.read(new Intent("android.intent.action.MAIN",null)).isEmpty());
        check(!SharedPhotos.accepts(null));
        rejects(new Intent(Intent.ACTION_SEND,"text/plain"));
        rejects(new Intent(Intent.ACTION_SEND,null));
        rejects(new Intent(Intent.ACTION_SEND,"image/png"));
        Intent invalid=new Intent(Intent.ACTION_SEND,"image/jpeg");invalid.stream=Uri.parse("file:///data/data/local.smartframe.sender/private");rejects(invalid);
        invalid.stream=Uri.parse("https://example.com/photo.jpg");rejects(invalid);
        invalid.stream=new Parcelable(){};rejects(invalid);
        multi.clip=null;multi.streams.clear();for(int n=0;n<200;n++)multi.streams.add(Uri.parse("content://gallery/images/"+n));
        check(SharedPhotos.read(multi).size()==200);
        multi.streams.add(Uri.parse("content://gallery/images/200"));rejects(multi);
        System.out.println("SharedPhotosTest: "+passed+" checks passed (host stubs; Android URI grants require device verification)");
    }
}
