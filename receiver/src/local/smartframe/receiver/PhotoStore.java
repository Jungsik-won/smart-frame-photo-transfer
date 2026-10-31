package local.smartframe.receiver;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.io.*;
import org.json.*;
import local.smartframe.transfer.core.Wire;
import local.smartframe.transfer.core.IncomingPhoto;
import local.smartframe.transfer.core.PhotoDeletion;

final class PhotoStore extends SQLiteOpenHelper {
    private final Context context;
    final StorageTargets storage;
    private final Object uploadLock=new Object();
    static final String FOLDER="Pictures/SmartFrame/";
    PhotoStore(Context c){super(c,"photos.db",null,2);context=c;storage=new StorageTargets(c);}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE photos(hash TEXT NOT NULL,volume TEXT NOT NULL,uri TEXT NOT NULL,name TEXT,size INTEGER,saved INTEGER,PRIMARY KEY(hash,volume))");}
    public void onUpgrade(SQLiteDatabase db,int a,int b){
        if(a<2){
            db.execSQL("ALTER TABLE photos RENAME TO photos_v1");
            onCreate(db);
            db.execSQL("INSERT INTO photos(hash,volume,uri,name,size,saved) SELECT hash,'external_primary',replace(uri,'content://media/external/','content://media/external_primary/'),name,size,saved FROM photos_v1");
            db.execSQL("DROP TABLE photos_v1");
        }
    }
    synchronized boolean has(String hash,String volume)throws IOException{
        storage.require(volume);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,size FROM photos WHERE hash=? AND volume=?",new String[]{hash,volume})){
            if(!c.moveToFirst())return false;
            try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(Uri.parse(c.getString(0)),"r")){
                if(fd!=null && (fd.getStatSize()<0 || fd.getStatSize()==c.getLong(1)))return true;
            }catch(FileNotFoundException missing){
                // Unplugged media is not a deleted photo: preserve its receipt.
                storage.require(volume);
            }catch(IOException|SecurityException e){throw new IOException("저장된 사진을 확인할 수 없어요. 저장소 연결 상태를 확인해 주세요.",e);}
        }
        storage.require(volume);
        getWritableDatabase().delete("photos","hash=? AND volume=?",new String[]{hash,volume});return false;
    }
    int count(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM photos",null)){c.moveToFirst();return c.getInt(0);}}
    private void record(String hash,String volume,Uri uri,String name,long size)throws IOException{
        ContentValues v=new ContentValues();v.put("hash",hash);v.put("volume",volume);v.put("uri",uri.toString());v.put("name",name);v.put("size",size);v.put("saved",System.currentTimeMillis());
        if(getWritableDatabase().insertWithOnConflict("photos",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IOException("사진 수신 기록을 저장하지 못했어요.");
    }
    boolean receive(InputStream in,long length,String hash,String name)throws Exception{
        // Capture the destination once; changing the setting affects the next upload.
        final String volume=storage.selected();
        synchronized(uploadLock){
            StorageTargets.Target target=storage.require(volume);
            File stage=storage.staging(target);
            if(!has(hash,volume)&&stage.getUsableSpace()<length*2+8L*1024*1024)throw new IOException("선택한 저장소의 공간이 부족해요. 사진 크기의 두 배와 여유 공간 8MB가 필요해요.");
            try{return IncomingPhoto.receive(in,length,hash,name,stage,new IncomingPhoto.Sink(){
                public boolean has(String h)throws Exception{return PhotoStore.this.has(h,volume);}
                public boolean save(File file,String h,String n)throws Exception{return accept(file,h,n,volume).getBoolean("duplicate");}
            });}catch(Exception e){storage.require(volume);throw e;}
        }
    }
    synchronized JSONObject accept(File temp,String hash,String name,String volume) throws Exception {
        if(has(hash,volume))return result(hash,true);
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.getPath(),options);
        if(options.outWidth<=0 || options.outHeight<=0 || (long)options.outWidth*options.outHeight>100000000L)throw new IOException("지원하지 않는 사진이거나 이미지 크기가 너무 커요.");
        String mime=options.outMimeType, ext;
        if("image/jpeg".equals(mime))ext="jpg";else if("image/png".equals(mime))ext="png";else if("image/webp".equals(mime))ext="webp";
        else throw new IOException("JPEG, PNG, WebP 사진을 선택해 주세요.");
        ContentResolver resolver=context.getContentResolver();
        Uri collection=MediaStore.Images.Media.getContentUri(volume);
        String filename=hash+"."+ext;
        // Recover a published file if the process died between publication and ledger commit.
        try(Cursor c=resolver.query(collection,new String[]{"_id","is_pending"},"relative_path=? AND _display_name=?",new String[]{FOLDER,filename},null)){
            if(c!=null)while(c.moveToNext()){
                Uri existing=ContentUris.withAppendedId(collection,c.getLong(0));
                boolean valid=false;
                try(InputStream in=resolver.openInputStream(existing)){valid=in!=null && Wire.equal(hash,Wire.hash(in));}catch(Exception ignored){}
                if(valid){ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);if(resolver.update(existing,ready,null,null)<1)throw new IOException("사진 목록을 복구하지 못했어요.");record(hash,volume,existing,name,temp.length());return result(hash,true);}
                if(c.getInt(1)==1)resolver.delete(existing,null,null);
            }
        }
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,filename);values.put(MediaStore.Images.Media.MIME_TYPE,mime);
        // MediaStore creates the missing shared Pictures/SmartFrame directory on insert/open.
        // Do not use private app directories or require the user to create a folder first.
        values.put(MediaStore.Images.Media.RELATIVE_PATH,FOLDER);values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=resolver.insert(collection,values);if(uri==null)throw new IOException("사진 저장 공간을 만들지 못했어요.");
        boolean published=false;
        try{
            try(InputStream in=new FileInputStream(temp);OutputStream out=resolver.openOutputStream(uri,"w")){
                if(out==null)throw new IOException("사진을 저장하지 못했어요.");Wire.copyExactly(in,out,temp.length());
            }
            ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);
            if(resolver.update(uri,ready,null,null)<1)throw new IOException("사진 목록을 갱신하지 못했어요.");
            published=true;record(hash,volume,uri,name,temp.length());resolver.notifyChange(collection,null);
            resolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,null);return result(hash,false);
        }finally{if(!published)try{resolver.delete(uri,null,null);}catch(Exception ignored){}}
    }
    private String managedVolume(String requested)throws IOException{
        String selected=storage.selected();PhotoDeletion.requireVolume(requested,selected);storage.require(selected);return selected;
    }
    private Uri managedUri(String hash,String volume)throws Exception{
        if(!Wire.validHash(hash))throw new IOException("잘못된 사진 정보예요.");
        String value=null;try(Cursor c=getReadableDatabase().rawQuery("SELECT uri FROM photos WHERE hash=? AND volume=?",new String[]{hash,volume})){if(c.moveToFirst())value=c.getString(0);}
        if(value==null)return null;
        Uri uri=Uri.parse(value);String prefix=MediaStore.Images.Media.getContentUri(volume).toString()+"/";
        if(!value.startsWith(prefix)||!value.substring(prefix.length()).matches("[0-9]+"))throw new IOException("이 앱이 관리하는 사진 주소가 아니에요.");
        try(Cursor c=context.getContentResolver().query(uri,new String[]{"relative_path","_display_name"},null,null,null)){
            if(c==null)throw new IOException("사진 위치를 확인하지 못했어요.");
            if(!c.moveToFirst()){storage.require(volume);return null;}
            if(!FOLDER.equals(c.getString(0))||!c.getString(1).matches(hash+"\\.(jpg|png|webp)"))throw new IOException("사진 위치가 바뀌어서 자동으로 관리할 수 없어요.");
        }
        return uri;
    }
    synchronized JSONObject listManaged(JSONObject request)throws Exception{
        String volume=request.optString("volume",storage.selected());managedVolume(volume);
        long before=request.optLong("before",Long.MAX_VALUE);String beforeHash=request.optString("beforeHash","z");
        if(before<0||!(beforeHash.equals("z")||Wire.validHash(beforeHash)))throw new IOException("사진 목록 위치가 올바르지 않아요.");
        JSONArray items=new JSONArray();java.util.ArrayList<String[]> page=new java.util.ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT hash,name,size,saved FROM photos WHERE volume=? AND (saved<? OR (saved=? AND hash<?)) ORDER BY saved DESC,hash DESC LIMIT 51",new String[]{volume,Long.toString(before),Long.toString(before),beforeHash})){while(c.moveToNext())page.add(new String[]{c.getString(0),c.getString(1),Long.toString(c.getLong(2)),Long.toString(c.getLong(3))});}
        int end=Math.min(50,page.size());for(int i=0;i<end;i++){String[] p=page.get(i);if(has(p[0],volume))items.put(new JSONObject().put("hash",p[0]).put("name",p[1]).put("size",Long.parseLong(p[2])));}
        JSONObject result=new JSONObject().put("volume",volume).put("storage",storage.require(volume).label).put("photos",items).put("more",page.size()>50);
        if(end>0)result.put("before",Long.parseLong(page.get(end-1)[3])).put("beforeHash",page.get(end-1)[0]);return result;
    }
    synchronized JSONObject thumbnail(String volume,String hash)throws Exception{
        managedVolume(volume);Uri uri=managedUri(hash,volume);if(uri==null)throw new IOException("액자에 사진이 없어요.");
        android.graphics.Bitmap bitmap=context.getContentResolver().loadThumbnail(uri,new android.util.Size(256,256),null);
        try{ByteArrayOutputStream out=new ByteArrayOutputStream();if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,75,out))throw new IOException("미리보기를 만들지 못했어요.");return new JSONObject().put("jpeg",android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP));}finally{bitmap.recycle();}
    }
    synchronized JSONObject deleteManaged(String volume,java.util.List<String> hashes)throws Exception{
        managedVolume(volume);
        PhotoDeletion.Result result=PhotoDeletion.delete(hashes,hash->{
            storage.require(volume);Uri uri=managedUri(hash,volume);
            if(uri==null){getWritableDatabase().delete("photos","hash=? AND volume=?",new String[]{hash,volume});return false;}
            if(context.getContentResolver().delete(uri,null,null)<1){if(managedUri(hash,volume)!=null)throw new IOException("액자 사진을 삭제하지 못했어요.");}
            getWritableDatabase().delete("photos","hash=? AND volume=?",new String[]{hash,volume});return true;
        });
        context.getContentResolver().notifyChange(MediaStore.Images.Media.getContentUri(volume),null);context.getContentResolver().notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,null);
        return new JSONObject().put("deleted",new JSONArray(result.deleted)).put("missing",new JSONArray(result.missing)).put("failed",new JSONObject(result.failed));
    }
    private JSONObject result(String hash,boolean duplicate)throws JSONException{return new JSONObject().put("hash",hash).put("stored",true).put("duplicate",duplicate);}
}
