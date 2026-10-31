package local.smartframe.sender;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import local.smartframe.transfer.core.Wire;

public final class TransferService extends Service {
    static volatile boolean running;static volatile String message="";
    private volatile boolean cancel;private PowerManager.WakeLock wake;
    private SenderDb db;private NotificationManager nm;
    public void onCreate(){super.onCreate();db=new SenderDb(this);nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("send","사진 전송",NotificationManager.IMPORTANCE_LOW));}
    private Notification notification(String text){return new Notification.Builder(this,"send").setSmallIcon(local.smartframe.sender.R.drawable.ic_frame).setContentTitle("액자에 사진 보내기").setContentText(text).setOngoing(running).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,SenderActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)).build();}
    public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(201,notification("액자 확인 중…"));
        if(running)return START_NOT_STICKY;
        running=true;cancel=false;wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SmartFrame:send");wake.acquire(60*60*1000L);
        String id=intent==null?null:intent.getStringExtra("frame");boolean onlyCheck=intent!=null&&intent.getBooleanExtra("checkOnly",false);
        new Thread(()->transfer(id,onlyCheck),"photo-send").start();return START_NOT_STICKY;
    }
    private void transfer(String id,boolean onlyCheck){
        List<SenderDb.Photo> all=db.pending(id);int sent=0,duplicate=0,failed=0;
        try{
            SenderDb.Frame frame=db.frame(id);if(frame==null)throw new IOException("액자를 먼저 연결해 주세요.");
            update("액자에 이미 있는 사진을 확인하고 있어요…");
            String discovered=LocalDiscovery.find(this,frame.id);if(discovered!=null)frame.host=discovered;
            Set<String> present=Api.check(frame,all);
            db.address(frame.id,frame.host);
            for(SenderDb.Photo p:all){if(present.contains(p.hash)){db.state(id,p,"done","액자에 저장됨 · 확인 완료");duplicate++;}else db.state(id,p,"ready","액자에 없는 사진");}
            if(onlyCheck){update("액자에 "+duplicate+"장 저장됨 · 보낼 사진 "+(all.size()-duplicate)+"장");return;}
            for(SenderDb.Photo p:all){
                if(cancel)throw new IOException("전송이 중단됐어요.");if(present.contains(p.hash))continue;
                db.state(id,p,"sending","0%");update((sent+failed+1)+" / "+(all.size()-duplicate)+"장 · "+p.name);
                try{
                    final long[] last={0};Api.upload(frame,p,(done,total)->{if(cancel)throw new IOException("전송이 중단됐어요.");long now=System.currentTimeMillis();if(now-last[0]>500||done==total){db.state(id,p,"sending",(total==0?0:done*100/total)+"%");last[0]=now;}});
                    db.state(id,p,"done","액자 저장 확인 완료");sent++;
                }catch(Exception e){db.state(id,p,"failed",friendly(e));failed++;}
            }
            update("완료 "+sent+"장 · 이미 있음 "+duplicate+"장"+(failed>0?" · 실패 "+failed+"장":""));
        }catch(Exception e){update(friendly(e));}
        finally{
            try{db.archiveDone(id);}catch(Exception e){android.util.Log.w("FrameSend","Completed staging cleanup deferred",e);}
            running=false;if(wake!=null&&wake.isHeld())wake.release();
            stopForeground(true);nm.notify(202,notification(message));stopSelf();
        }
    }
    private String friendly(Exception e){if(e instanceof javax.net.ssl.SSLException)return "액자 인증서가 다르거나 연결에 실패했어요. 액자 주소와 연결 등록을 확인해 주세요.";String m=e.getMessage();return m==null?"같은 와이파이인지 확인한 뒤 다시 보내 주세요.":m;}
    private void update(String s){message=s;nm.notify(201,notification(s));}
    public IBinder onBind(Intent i){return null;}
    public void onDestroy(){cancel=true;if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
}
