package local.smartframe.sender;

import java.io.*;
import java.net.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;
import org.json.*;
import local.smartframe.transfer.core.Wire;

final class Api {
    static final class Probe {String host,fingerprint,name,id;}
    interface Progress {void bytes(long done,long total)throws IOException;}
    static String address(String input)throws Exception{
        String s=input.trim();if(s.startsWith("https://"))s=s.substring(8);
        URI uri=new URI("https://"+s);
        if(uri.getHost()==null || uri.getUserInfo()!=null || (uri.getRawPath()!=null&&!uri.getRawPath().isEmpty()) || uri.getRawQuery()!=null || uri.getFragment()!=null)throw new IOException("액자 화면의 주소를 입력해 주세요. 예: 192.168.0.10:8844");
        int port=uri.getPort()<0?Wire.PORT:uri.getPort();if(port<1||port>65535)throw new IOException("주소의 포트 번호를 확인해 주세요.");
        return uri.getHost()+":"+port;
    }
    private static HttpsURLConnection connection(String host,String path,String fingerprint,String[] observed)throws Exception{
        URI uri=new URI("https://"+address(host));
        InetAddress a=InetAddress.getByName(uri.getHost());if(!a.isSiteLocalAddress()&&!a.isLinkLocalAddress()&&!a.isLoopbackAddress())throw new IOException("같은 와이파이의 액자 주소를 입력해 주세요.");
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] c,String t)throws CertificateException{throw new CertificateException();}
            public void checkServerTrusted(X509Certificate[] c,String t)throws CertificateException{
                if(c==null||c.length==0)throw new CertificateException("인증서 없음");
                String h=Wire.hex(Wire.digest().digest(c[0].getEncoded()));
                if(fingerprint!=null&&!Wire.equal(h,fingerprint))throw new CertificateException("액자의 연결 정보가 바뀌었어요. 다시 등록해 주세요.");
                if(observed!=null)observed[0]=h;
            }
        };
        SSLContext tls=SSLContext.getInstance("TLSv1.2");tls.init(null,new TrustManager[]{trust},null);
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://"+address(host)+path).openConnection(Proxy.NO_PROXY);
        c.setSSLSocketFactory(tls.getSocketFactory());c.setHostnameVerifier((h,s)->true); // Identity is verified by exact certificate pin above.
        c.setConnectTimeout(12000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Connection","close");return c;
    }
    private static JSONObject read(HttpsURLConnection c)throws Exception{
        int code=c.getResponseCode();InputStream source=code>=400?c.getErrorStream():c.getInputStream();
        if(source==null)throw new IOException("액자 응답을 받지 못했어요 ("+code+").");
        ByteArrayOutputStream buf=new ByteArrayOutputStream();try(InputStream in=source){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(buf.size()+n>1024*1024)throw new IOException("응답이 너무 커요.");buf.write(b,0,n);}}
        JSONObject j=new JSONObject(buf.toString("UTF-8"));if(code!=200)throw new IOException(j.optString("error","액자 연결을 확인해 주세요 ("+code+")."));return j;
    }
    static Probe probe(String host)throws Exception{
        String[] observed=new String[1];HttpsURLConnection c=connection(host,"/v1/info",null,observed);
        try{JSONObject j=read(c);if(j.optInt("protocol")!=1||observed[0]==null)throw new IOException("지원하는 액자 수신 앱이 아니에요.");Probe p=new Probe();p.host=address(host);p.fingerprint=observed[0];p.id=j.getString("id");p.name=j.getString("name");return p;}finally{c.disconnect();}
    }
    static SenderDb.Frame pair(Probe p,String pin)throws Exception{
        JSONObject j=json(p.host,p.fingerprint,null,"/v1/pair",new JSONObject().put("pin",pin));
        SenderDb.Frame f=new SenderDb.Frame();f.id=j.getString("id");f.name=j.getString("name");f.host=p.host;f.fingerprint=p.fingerprint;f.token=j.getString("token");return f;
    }
    private static JSONObject json(String host,String fingerprint,String token,String path,JSONObject body)throws Exception{
        HttpsURLConnection c=connection(host,path,fingerprint,null);
        try{c.setRequestMethod("POST");c.setDoOutput(true);if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");byte[] b=body.toString().getBytes("UTF-8");c.setFixedLengthStreamingMode(b.length);try(OutputStream out=c.getOutputStream()){out.write(b);}return read(c);}finally{c.disconnect();}
    }
    static Set<String> check(SenderDb.Frame f,List<SenderDb.Photo> photos)throws Exception{
        JSONArray a=new JSONArray();for(SenderDb.Photo p:photos)a.put(p.hash);
        JSONArray present=json(f.host,f.fingerprint,f.token,"/v1/check",new JSONObject().put("hashes",a)).getJSONArray("present");Set<String> set=new HashSet<>();for(int i=0;i<present.length();i++)set.add(present.getString(i));return set;
    }
    static boolean upload(SenderDb.Frame f,SenderDb.Photo p,Progress progress)throws Exception{
        File file=new File(p.path);if(!file.isFile()||file.length()!=p.size)throw new IOException("휴대폰의 전송 대기 파일이 없어요. 사진을 다시 선택해 주세요.");
        HttpsURLConnection c=connection(f.host,"/v1/upload",f.fingerprint,null);
        try{c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+f.token);c.setRequestProperty("Content-Type","application/octet-stream");c.setRequestProperty("X-Photo-Sha256",p.hash);c.setRequestProperty("X-Photo-Name",URLEncoder.encode(p.name,"UTF-8"));c.setFixedLengthStreamingMode(file.length());
            try(InputStream in=new FileInputStream(file);OutputStream out=c.getOutputStream()){byte[] b=new byte[32768];int n;long done=0;while((n=in.read(b))!=-1){progress.bytes(done,file.length());out.write(b,0,n);done+=n;}progress.bytes(done,file.length());}
            JSONObject result=read(c);if(!result.optBoolean("stored")||!Wire.equal(p.hash,result.optString("hash")))throw new IOException("액자 저장 확인을 받지 못했어요. 다시 보내기를 눌러 주세요.");return result.optBoolean("duplicate");
        }finally{c.disconnect();}
    }
    static JSONObject framePhotos(SenderDb.Frame f,JSONObject cursor)throws Exception{return json(f.host,f.fingerprint,f.token,"/v1/photos",cursor);}
    static byte[] frameThumbnail(SenderDb.Frame f,String volume,String hash)throws Exception{return Base64.getDecoder().decode(json(f.host,f.fingerprint,f.token,"/v1/photos/thumbnail",new JSONObject().put("volume",volume).put("hash",hash)).getString("jpeg"));}
    static JSONObject deleteFramePhotos(SenderDb.Frame f,String volume,List<String> hashes)throws Exception{return json(f.host,f.fingerprint,f.token,"/v1/photos/delete",new JSONObject().put("volume",volume).put("hashes",new JSONArray(hashes)));}
}
