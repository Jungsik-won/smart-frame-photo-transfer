package local.smartframe.sender;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.File;
import java.util.*;

final class SenderDb extends SQLiteOpenHelper {
    static final String PHOTOS_SQL="SELECT p.hash,p.name,p.path,p.size,r.status,r.error FROM photos p LEFT JOIN receipts r ON r.hash=p.hash AND r.frame=? AND r.updated>=p.added ORDER BY p.added DESC";
    static final String PENDING_SQL="SELECT p.hash,p.name,p.path,p.size,r.status,r.error FROM photos p LEFT JOIN receipts r ON r.hash=p.hash AND r.frame=? AND r.updated>=p.added WHERE r.status IS NULL OR r.status NOT IN ('done','deleted') ORDER BY p.added DESC";
    static final String ARCHIVE_SQL="SELECT p.hash,p.path FROM photos p JOIN receipts r ON r.hash=p.hash AND r.frame=? AND r.updated>=p.added WHERE r.status IN ('done','deleted') AND NOT EXISTS (SELECT 1 FROM receipts other WHERE other.hash=p.hash AND other.frame<>r.frame AND other.updated>=p.added AND other.status NOT IN ('done','deleted'))";
    private final File staging;
    static final class Frame {String id,name,host,fingerprint,token;public String toString(){return name;}}
    static final class Photo {String hash,name,path,status,error;long size;}
    SenderDb(Context c){super(c,"sender.db",null,1);staging=new File(c.getFilesDir(),"photos");}
    public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE frames(id TEXT PRIMARY KEY,name TEXT,host TEXT,fingerprint TEXT,token TEXT)");
        d.execSQL("CREATE TABLE photos(hash TEXT PRIMARY KEY,name TEXT,path TEXT,size INTEGER,added INTEGER)");
        d.execSQL("CREATE TABLE receipts(frame TEXT,hash TEXT,name TEXT,status TEXT,error TEXT,updated INTEGER,PRIMARY KEY(frame,hash))");
    }
    public void onUpgrade(SQLiteDatabase d,int a,int b){}
    public void onOpen(SQLiteDatabase d){super.onOpen(d);d.execSQL("CREATE INDEX IF NOT EXISTS receipt_history ON receipts(frame,updated DESC)");}
    synchronized void frame(Frame f){ContentValues v=new ContentValues();v.put("id",f.id);v.put("name",f.name);v.put("host",f.host);v.put("fingerprint",f.fingerprint);v.put("token",f.token);getWritableDatabase().insertWithOnConflict("frames",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    synchronized List<Frame> frames(){ArrayList<Frame> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,host,fingerprint,token FROM frames ORDER BY name",null)){while(c.moveToNext()){Frame f=new Frame();f.id=c.getString(0);f.name=c.getString(1);f.host=c.getString(2);f.fingerprint=c.getString(3);f.token=c.getString(4);out.add(f);}}return out;}
    synchronized Frame frame(String id){for(Frame f:frames())if(f.id.equals(id))return f;return null;}
    synchronized void address(String id,String host){ContentValues v=new ContentValues();v.put("host",host);getWritableDatabase().update("frames",v,"id=?",new String[]{id});}
    synchronized boolean photoExists(String hash){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM photos WHERE hash=?",new String[]{hash})){return c.moveToFirst();}}
    private long selectionTime(String hash){try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(updated) FROM receipts WHERE hash=?",new String[]{hash})){c.moveToFirst();return Math.max(System.currentTimeMillis(),c.isNull(0)?0:c.getLong(0)+1);}}
    synchronized void add(String hash,String name,File file){ContentValues v=new ContentValues();v.put("hash",hash);v.put("name",name);v.put("path",file.getPath());v.put("size",file.length());v.put("added",selectionTime(hash));getWritableDatabase().insertWithOnConflict("photos",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    synchronized void reselect(String hash){ContentValues v=new ContentValues();v.put("added",selectionTime(hash));getWritableDatabase().update("photos",v,"hash=?",new String[]{hash});}
    synchronized List<Photo> photos(String frame){return readPhotos(PHOTOS_SQL,frame);}
    synchronized List<Photo> pending(String frame){return readPhotos(PENDING_SQL,frame);}
    private List<Photo> readPhotos(String sql,String frame){ArrayList<Photo> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{frame==null?"":frame})){while(c.moveToNext()){Photo p=new Photo();p.hash=c.getString(0);p.name=c.getString(1);p.path=c.getString(2);p.size=c.getLong(3);p.status=c.isNull(4)?"ready":c.getString(4);p.error=c.isNull(5)?"":c.getString(5);out.add(p);}}return out;}
    // Called only while no import can run: before service.running=false, or during initial activity setup.
    synchronized void archiveDone(String frame){
        ArrayList<String[]> done=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(ARCHIVE_SQL,new String[]{frame==null?"":frame})){while(c.moveToNext())done.add(new String[]{c.getString(0),c.getString(1)});}
        for(String[] row:done){
            try{
                if(!row[0].matches("[0-9a-f]{64}"))continue;
                File expected=new File(staging,row[0]+".photo").getCanonicalFile();File file=new File(row[1]).getCanonicalFile();
                if(!file.equals(expected)||!expected.getParentFile().equals(staging.getCanonicalFile()))continue;
                if(file.exists()&&!file.delete())continue;
                getWritableDatabase().delete("photos","hash=?",new String[]{row[0]});
            }catch(java.io.IOException ignored){}
        }
    }
    synchronized void state(String frame,Photo p,String state,String message){long now=System.currentTimeMillis();try(Cursor c=getReadableDatabase().rawQuery("SELECT added FROM photos WHERE hash=?",new String[]{p.hash})){if(c.moveToFirst())now=Math.max(now,c.getLong(0));}ContentValues v=new ContentValues();v.put("frame",frame);v.put("hash",p.hash);v.put("name",p.name);v.put("status",state);v.put("error",message);v.put("updated",now);getWritableDatabase().insertWithOnConflict("receipts",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    synchronized void interrupted(){getWritableDatabase().execSQL("UPDATE receipts SET status='failed',error='전송이 중단됐어요. 다시 보내면 액자에서 중복을 확인합니다.' WHERE status IN ('sending','checking','queued')");}
    synchronized void clearSelection(){for(Photo p:photos(null))new File(p.path).delete();getWritableDatabase().delete("photos",null,null);}
    synchronized int historyCount(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM receipts WHERE frame=?",new String[]{id})){c.moveToFirst();return c.getInt(0);}}
    synchronized String history(String id,int page){StringBuilder s=new StringBuilder();java.text.DateFormat fmt=new java.text.SimpleDateFormat("MM.dd HH:mm",Locale.KOREA);try(Cursor c=getReadableDatabase().rawQuery("SELECT name,status,updated FROM receipts WHERE frame=? ORDER BY updated DESC,hash LIMIT 50 OFFSET ?",new String[]{id,Integer.toString(Math.max(0,page)*50)})){while(c.moveToNext())s.append(fmt.format(new Date(c.getLong(2)))).append("  ").append("done".equals(c.getString(1))?"전송 완료":"deleted".equals(c.getString(1))?"액자에서 삭제":"미완료").append("\n").append(c.getString(0)).append("\n\n");}return s.length()==0?"아직 전송 기록이 없어요.":s.toString();}
    synchronized void remoteDeleted(String frame,List<String> hashes){for(String hash:hashes){ContentValues v=new ContentValues();v.put("status","deleted");v.put("error","액자에서 삭제됨");v.put("updated",selectionTime(hash));getWritableDatabase().update("receipts",v,"frame=? AND hash=?",new String[]{frame,hash});}}
}
