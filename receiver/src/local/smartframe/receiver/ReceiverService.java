package local.smartframe.receiver;

import android.app.*;
import android.content.*;
import android.net.nsd.*;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import javax.net.ssl.*;
import org.json.*;
import local.smartframe.transfer.core.Wire;

public final class ReceiverService extends Service {
    static volatile ReceiverService current;
    static volatile String state="시작 중…";
    static volatile String lastConnection="아직 연결 요청이 없어요";
    static volatile String selfCheck="검사 준비 중…";
    Identity identity;PhotoStore photos;
    private SSLServerSocket server;
    private volatile boolean closing;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private NsdManager nsd;private NsdManager.RegistrationListener registration;
    private PowerManager.WakeLock wake;
    static void start(Context c){c.startForegroundService(new Intent(c,ReceiverService.class));}
    public void onCreate(){
        super.onCreate();current=this;
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("receiver","사진 받기",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,ReceiverActivity.class);
        Notification n=new Notification.Builder(this,"receiver").setSmallIcon(local.smartframe.receiver.R.drawable.ic_frame).setContentTitle("액자 사진 받기").setContentText("같은 와이파이의 등록된 휴대폰에서 사진을 받을 수 있어요.").setContentIntent(PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)).setOngoing(true).build();
        startForeground(101,n);
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SmartFrame:receiver");wake.acquire();
        new Thread(()->runServer(),"frame-server").start();
    }
    private void runServer(){
        try{
            identity=new Identity(this);photos=new PhotoStore(this);
            photos.storage.cleanStaleTemps();
            File[] old=getCacheDir().listFiles((dir,name)->name.endsWith(".part"));if(old!=null)for(File f:old)f.delete();
            server=(SSLServerSocket)identity.tls.getServerSocketFactory().createServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(Wire.PORT));server.setEnabledProtocols(new String[]{"TLSv1.2"});
            if(closing){server.close();return;}
            advertise();state="암호화 연결을 자체 검사하고 있어요";
            new Thread(()->selfTest(),"frame-tls-self-test").start();
            while(!closing){
                SSLSocket socket=(SSLSocket)server.accept();socket.setSoTimeout(60000);
                try{workers.execute(()->handle(socket));}catch(RejectedExecutionException e){socket.close();}
            }
        }catch(Exception e){if(!closing){state="수신 시작 실패 · 연결 진단 보기를 눌러 주세요";selfCheck="실패: "+e.getClass().getSimpleName();ConnectionDiagnostics.record(this,"서버/보안 키 초기화",e);}}
    }
    private void selfTest(){
        selfCheck="이 프레임에서 실제 TLS 연결 검사 중…";
        try{
            X509TrustManager trust=new X509TrustManager(){
                public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
                public void checkClientTrusted(X509Certificate[] cert,String type)throws CertificateException{throw new CertificateException("Not a client identity");}
                public void checkServerTrusted(X509Certificate[] cert,String type)throws CertificateException{
                    if(cert.length==0||!Wire.equal(identity.fingerprint,Wire.hex(Wire.digest().digest(cert[0].getEncoded()))))throw new CertificateException("Self-test identity mismatch");
                }
            };
            SSLContext client=SSLContext.getInstance("TLSv1.2");client.init(null,new TrustManager[]{trust},null);
            try(SSLSocket socket=(SSLSocket)client.getSocketFactory().createSocket()){
                socket.connect(new InetSocketAddress("127.0.0.1",Wire.PORT),5000);socket.setSoTimeout(5000);socket.setEnabledProtocols(new String[]{"TLSv1.2"});socket.startHandshake();
                socket.getOutputStream().write("GET /v1/info HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));socket.getOutputStream().flush();
                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));String first=reader.readLine();
                if(first==null||!first.startsWith("HTTP/1.1 200"))throw new IOException("Self-test HTTP response missing");
                int total=0;String line;while((line=reader.readLine())!=null){total+=line.length();if(total>16384)throw new IOException("Self-test response too large");}
            }
            selfCheck="통과 · 보안 키 서명과 실제 TLS 연결 성공";state="사진 받을 준비가 됐어요";
        }catch(Exception e){selfCheck="실패 · 연결 진단 보기에서 원인 확인";state="암호화 연결 확인이 필요해요";ConnectionDiagnostics.record(this,"프레임 내부 TLS 자체 검사",e);}
    }
    private void advertise(){
        nsd=getSystemService(NsdManager.class);
        NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("SmartFrame-"+identity.id.substring(0,8));info.setServiceType("_smartframe._tcp.");info.setPort(Wire.PORT);
        info.setAttribute("id",identity.id);info.setAttribute("name",identity.name());
        registration=new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo i){}public void onRegistrationFailed(NsdServiceInfo i,int e){}public void onServiceUnregistered(NsdServiceInfo i){}public void onUnregistrationFailed(NsdServiceInfo i,int e){}};
        try{nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);}catch(Exception ignored){}
    }
    private void handle(SSLSocket socket){
        try(SSLSocket s=socket){
            lastConnection="휴대폰과 암호화 연결 확인 중…";
            s.startHandshake();
            lastConnection="암호화 연결 성공 · 요청 확인 중…";
            InputStream in=new BufferedInputStream(s.getInputStream());OutputStream out=s.getOutputStream();
            try{
                Wire.Request r=Wire.request(in);
                if(r.method.equals("GET") && r.path.equals("/v1/info")){
                    lastConnection="휴대폰에서 액자 정보를 확인했어요";
                    Wire.response(out,200,new JSONObject().put("protocol",1).put("id",identity.id).put("name",identity.name()).toString());return;
                }
                if(r.method.equals("POST") && r.path.equals("/v1/pair")){
                    JSONObject body=new JSONObject(Wire.body(in,r.length));String token=identity.pair(body.optString("pin"));
                    lastConnection="휴대폰 연결 등록 완료";
                    Wire.response(out,200,new JSONObject().put("token",token).put("id",identity.id).put("name",identity.name()).toString());return;
                }
                if(!identity.authorized(r.header("authorization"))){Wire.response(out,401,"{\"error\":\"액자 연결을 다시 등록해 주세요.\"}");return;}
                if(r.method.equals("POST")&&r.path.equals("/v1/photos")){Wire.response(out,200,photos.listManaged(new JSONObject(Wire.body(in,r.length))).toString());return;}
                if(r.method.equals("POST")&&r.path.equals("/v1/photos/thumbnail")){
                    JSONObject body=new JSONObject(Wire.body(in,r.length));Wire.response(out,200,photos.thumbnail(body.getString("volume"),body.getString("hash")).toString());return;
                }
                if(r.method.equals("POST")&&r.path.equals("/v1/photos/delete")){
                    JSONObject body=new JSONObject(Wire.body(in,r.length));JSONArray list=body.getJSONArray("hashes");java.util.ArrayList<String> hashes=new java.util.ArrayList<>();for(int i=0;i<list.length();i++)hashes.add(list.getString(i));
                    Wire.response(out,200,photos.deleteManaged(body.getString("volume"),hashes).toString());lastConnection="휴대폰에서 선택한 액자 사진 삭제 요청 처리";return;
                }
                if(r.method.equals("POST") && r.path.equals("/v1/check")){
                    JSONArray list=new JSONObject(Wire.body(in,r.length)).getJSONArray("hashes");if(list.length()>200)throw new IOException("한 번에 200장까지 확인할 수 있어요.");
                    String volume=photos.storage.selected();photos.storage.require(volume);
                    JSONArray existing=new JSONArray();for(int i=0;i<list.length();i++){String h=list.getString(i);if(!Wire.validHash(h))throw new IOException("Invalid hash");if(photos.has(h,volume))existing.put(h);}
                    Wire.response(out,200,new JSONObject().put("present",existing).toString());return;
                }
                if(r.method.equals("POST") && r.path.equals("/v1/upload")){
                    String hash=r.header("x-photo-sha256");if(!Wire.validHash(hash)||r.length<1)throw new IOException("Invalid photo");
                    String raw=r.header("x-photo-name");String name=raw==null?"사진":URLDecoder.decode(raw,"UTF-8");if(name.length()>200)name=name.substring(0,200);
                    boolean duplicate=photos.receive(in,r.length,hash,name);
                    lastConnection=duplicate?"이미 저장된 사진 확인 완료":"사진 저장 완료 · Fotoo 사진 목록 갱신";
                    Wire.response(out,200,new JSONObject().put("hash",hash).put("stored",true).put("duplicate",duplicate).toString());return;
                }
                Wire.response(out,404,"{\"error\":\"지원하지 않는 요청입니다.\"}");
            }catch(Exception e){lastConnection="요청 처리 실패: "+e.getClass().getSimpleName();Log.w("FrameReceive","Request handling failed",e);try{Wire.response(out,400,new JSONObject().put("error",e.getMessage()==null?"사진을 받지 못했어요.":e.getMessage()).toString());}catch(Exception ignored){}}
        }catch(Exception e){lastConnection="연결 실패: "+e.getClass().getSimpleName()+" · 연결 진단 보기";ConnectionDiagnostics.record(this,"외부 TLS 연결",e);}
    }
    static String addresses(){
        ArrayList<String> list=new ArrayList<>();
        try{for(NetworkInterface nif:Collections.list(NetworkInterface.getNetworkInterfaces()))for(InetAddress a:Collections.list(nif.getInetAddresses()))if(a instanceof Inet4Address && a.isSiteLocalAddress()&&!a.isLoopbackAddress())list.add(a.getHostAddress()+":"+Wire.PORT);}catch(Exception ignored){}
        return list.isEmpty()?"와이파이에 연결해 주세요":android.text.TextUtils.join("\n",list);
    }
    public int onStartCommand(Intent i,int flags,int id){return START_STICKY;}
    public IBinder onBind(Intent i){return null;}
    public void onDestroy(){closing=true;current=null;state="수신 중지";try{if(server!=null)server.close();}catch(Exception ignored){}workers.shutdownNow();try{if(nsd!=null&&registration!=null)nsd.unregisterService(registration);}catch(Exception ignored){}if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
}
