package local.smartframe.receiver;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** TLS diagnostics only. Never records pairing codes, tokens, or photo data. */
final class ConnectionDiagnostics {
    private static volatile String latest="아직 오류 기록이 없어요.";
    static String reason(Throwable e){
        StringBuilder b=new StringBuilder();
        for(int i=0;e!=null&&i<8;i++,e=e.getCause()){
            if(i>0)b.append("\n원인: ");b.append(e.getClass().getSimpleName());
            String message=e.getMessage();if(message!=null&&!message.isEmpty())b.append(": ").append(message);
        }
        return b.length()>6000?b.substring(0,6000):b.toString();
    }
    static synchronized void record(Context c,String stage,Throwable e){
        latest="수신 앱 0.1.5 · Android "+Build.VERSION.RELEASE+" · "+Build.MODEL+"\n"+stage+"\n"+reason(e);
        Log.w("FrameReceive",stage,e);
        try(FileOutputStream out=c.openFileOutput("connection-diagnostic.txt",Context.MODE_PRIVATE)){out.write(latest.getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){}
    }
    static String read(Context c){
        StringBuilder text=new StringBuilder("자체 검사: "+ReceiverService.selfCheck+"\n최근 연결: "+ReceiverService.lastConnection+"\n\n");
        try(FileInputStream in=c.openFileInput("connection-diagnostic.txt")){byte[] b=new byte[8192];int n=in.read(b);if(n>0)return text.append(new String(b,0,n,StandardCharsets.UTF_8)).toString();}catch(IOException ignored){}
        return text.append(latest).toString();
    }
}
