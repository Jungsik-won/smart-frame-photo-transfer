package local.smartframe.receiver;

import android.content.*;
import android.os.Environment;
import android.os.storage.*;
import android.provider.MediaStore;
import android.text.format.Formatter;
import java.io.*;
import java.util.*;

/** Select only mounted MediaStore volumes; never redirect an unavailable choice. */
final class StorageTargets {
    static final class Target {
        final String volume,label;final File cache;
        Target(String v,String l,File c){volume=v;label=l;cache=c;}
    }
    private final Context context;
    private final SharedPreferences prefs;
    StorageTargets(Context c){context=c;prefs=c.getSharedPreferences("photo_storage",0);}
    String selected(){return prefs.getString("volume",MediaStore.VOLUME_EXTERNAL_PRIMARY);}
    String selectedLabel(){return prefs.getString("label","내부 저장소");}
    List<Target> available(){
        Set<String> mounted=MediaStore.getExternalVolumeNames(context);
        StorageManager sm=context.getSystemService(StorageManager.class);
        List<Target> result=new ArrayList<>();
        for(File cache:context.getExternalCacheDirs()){
            if(cache==null)continue;
            StorageVolume sv=sm.getStorageVolume(cache);
            if(sv==null||!Environment.MEDIA_MOUNTED.equals(sv.getState()))continue;
            String volume=sv.isPrimary()?MediaStore.VOLUME_EXTERNAL_PRIMARY:sv.getUuid();
            if(volume==null)continue;volume=volume.toLowerCase(Locale.ROOT);
            if(!mounted.contains(volume))continue;
            String label=sv.isPrimary()?"내부 저장소":sv.getDescription(context)+" ("+volume+")";
            result.add(new Target(volume,label,cache));
        }
        result.sort((a,b)->a.volume.equals(b.volume)?0:a.volume.equals(MediaStore.VOLUME_EXTERNAL_PRIMARY)?-1:b.volume.equals(MediaStore.VOLUME_EXTERNAL_PRIMARY)?1:a.label.compareTo(b.label));
        return result;
    }
    Target require(String volume)throws IOException{
        for(Target t:available())if(t.volume.equals(volume))return t;
        throw new IOException("선택한 저장소가 연결되지 않았거나 쓸 수 없어요. USB·SD카드를 연결하고 수신 앱의 저장소 설정을 확인해 주세요.");
    }
    void select(Target target)throws IOException{
        Target current=require(target.volume);
        staging(current);
        if(!prefs.edit().putString("volume",current.volume).putString("label",current.label).commit())throw new IOException("저장소 선택을 저장하지 못했어요.");
    }
    File staging(Target t)throws IOException{
        require(t.volume);
        File dir=new File(t.cache,"incoming");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("선택한 저장소에 임시 폴더를 만들 수 없어요.");
        if(!dir.canWrite())throw new IOException("선택한 저장소가 쓰기 금지 상태예요.");
        return dir;
    }
    String describe(Target t){return t.label+" · 남은 공간 "+Formatter.formatFileSize(context,t.cache.getUsableSpace());}
    String summary(){
        try{return describe(require(selected()))+"\n"+PhotoStore.FOLDER;}
        catch(Exception e){return selectedLabel()+" · 연결 안 됨 / 저장 불가\nUSB·SD카드를 연결하거나 저장소를 다시 선택해 주세요.";}
    }
    void cleanStaleTemps(){
        for(Target t:available()){
            File[] files=new File(t.cache,"incoming").listFiles((dir,name)->name.startsWith("receive-")&&name.endsWith(".part"));
            if(files!=null)for(File f:files)f.delete();
        }
    }
}
