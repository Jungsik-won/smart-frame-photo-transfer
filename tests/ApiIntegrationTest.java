package local.smartframe.sender;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.json.*;
import local.smartframe.transfer.core.*;

/** Executes the actual Android sender's Api class against a local TLS fixture. */
public final class ApiIntegrationTest {
    static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args)throws Exception{
        KeyStore ks=KeyStore.getInstance("JKS");try(InputStream in=new FileInputStream(args[0])){ks.load(in,"local-test-only".toCharArray());}
        FrameKeyManager km=new FrameKeyManager("test",(PrivateKey)ks.getKey("test","local-test-only".toCharArray()),(X509Certificate)ks.getCertificate("test"));SSLContext ssl=SSLContext.getInstance("TLSv1.2");ssl.init(new KeyManager[]{km},null,null);
        SSLServerSocket server=(SSLServerSocket)ssl.getServerSocketFactory().createServerSocket(0,20,InetAddress.getByName("127.0.0.1"));server.setEnabledProtocols(new String[]{"TLSv1.2"});
        Path base=Files.createTempDirectory(Paths.get(args[1]),"api-test-");Set<String> stored=Collections.synchronizedSet(new HashSet<>());String token=Wire.randomToken();
        ExecutorService pool=Executors.newCachedThreadPool();
        pool.execute(()->{while(!server.isClosed())try{SSLSocket socket=(SSLSocket)server.accept();pool.execute(()->{
            try(SSLSocket s=socket){s.setSoTimeout(5000);InputStream in=new BufferedInputStream(s.getInputStream());OutputStream out=s.getOutputStream();Wire.Request r=Wire.request(in);
                if(r.path.equals("/v1/info")){Wire.response(out,200,new JSONObject().put("protocol",1).put("id","test-frame").put("name","테스트 액자").toString());return;}
                if(r.path.equals("/v1/pair")){JSONObject j=new JSONObject(Wire.body(in,r.length));if(!j.optString("pin").equals("123456")){Wire.response(out,400,"{\"error\":\"연결번호 오류\"}");return;}Wire.response(out,200,new JSONObject().put("id","test-frame").put("name","테스트 액자").put("token",token).toString());return;}
                if(!Wire.equal("Bearer "+token,r.header("authorization"))){Wire.response(out,401,"{\"error\":\"다시 연결해 주세요\"}");return;}
                if(r.path.equals("/v1/photos")){Wire.body(in,r.length);JSONArray list=new JSONArray();for(String h:stored)list.put(new JSONObject().put("hash",h).put("name","사진.jpg"));Wire.response(out,200,new JSONObject().put("volume","usb").put("storage","USB").put("photos",list).put("more",false).toString());return;}
                if(r.path.equals("/v1/photos/thumbnail")){JSONObject body=new JSONObject(Wire.body(in,r.length));if(!"usb".equals(body.optString("volume"))||!stored.contains(body.optString("hash"))){Wire.response(out,400,"{\"error\":\"사진 없음\"}");return;}Wire.response(out,200,new JSONObject().put("jpeg",Base64.getEncoder().encodeToString(new byte[]{1,2,3})).toString());return;}
                if(r.path.equals("/v1/photos/delete")){
                    JSONObject body=new JSONObject(Wire.body(in,r.length));try{PhotoDeletion.requireVolume(body.optString("volume"),"usb");JSONArray array=body.getJSONArray("hashes");List<String> hashes=new ArrayList<>();for(int i=0;i<array.length();i++)hashes.add(array.getString(i));PhotoDeletion.Result result=PhotoDeletion.delete(hashes,h->stored.remove(h));Wire.response(out,200,new JSONObject().put("deleted",result.deleted).put("missing",result.missing).put("failed",result.failed).toString());}catch(IllegalArgumentException e){Wire.response(out,400,new JSONObject().put("error",e.getMessage()).toString());}return;
                }
                if(r.path.equals("/v1/check")){JSONArray hashes=new JSONObject(Wire.body(in,r.length)).getJSONArray("hashes"),present=new JSONArray();for(int i=0;i<hashes.length();i++)if(stored.contains(hashes.getString(i)))present.put(hashes.getString(i));Wire.response(out,200,new JSONObject().put("present",present).toString());return;}
                if(r.path.equals("/v1/upload")){String hash=r.header("x-photo-sha256");boolean duplicate=IncomingPhoto.receive(in,r.length,hash,"photo",base.toFile(),new IncomingPhoto.Sink(){public boolean has(String h){return stored.contains(h);}public boolean save(File f,String h,String n){return !stored.add(h);}});Wire.response(out,200,new JSONObject().put("hash",hash).put("stored",true).put("duplicate",duplicate).toString());return;}
                Wire.response(out,404,"{}");
            }catch(Exception ignored){}
        });}catch(Exception ignored){}});
        try{
            String address="127.0.0.1:"+server.getLocalPort();Api.Probe probe=Api.probe(address);
            String fingerprint=Wire.hex(Wire.digest().digest(((X509Certificate)ks.getCertificate("test")).getEncoded()));
            ok(probe.fingerprint.equals(fingerprint)&&probe.name.equals("테스트 액자"),"TLS probe and Korean JSON decode");
            try{Api.pair(probe,"000000");throw new AssertionError("wrong pairing accepted");}catch(IOException expected){ok(expected.getMessage().contains("연결번호"),"server pairing error is surfaced");}
            SenderDb.Frame frame=Api.pair(probe,"123456");ok(frame.token.equals(token),"pair token accepted through pinned TLS");
            byte[] bytes=new byte[98307];new Random(4).nextBytes(bytes);File file=base.resolve("photo.jpg").toFile();Files.write(file.toPath(),bytes);
            SenderDb.Photo photo=new SenderDb.Photo();photo.hash=Wire.hash(new ByteArrayInputStream(bytes));photo.name="우리 가족 사진.jpg";photo.path=file.getPath();photo.size=file.length();List<SenderDb.Photo> photos=Arrays.asList(photo);
            ok(Api.check(frame,photos).isEmpty(),"preflight reports missing photo");
            final long[] progress={0};ok(!Api.upload(frame,photo,(n,total)->progress[0]=n),"first actual client upload stores photo");
            ok(progress[0]==bytes.length,"upload progress reaches full byte count");
            ok(Api.check(frame,photos).contains(photo.hash),"postflight confirms hash on frame");
            ok(Api.upload(frame,photo,(n,total)->{}),"retry returns duplicate receipt without a second publication");
            ok(Api.framePhotos(frame,new JSONObject()).getJSONArray("photos").length()==1,"authenticated remote gallery lists uploaded photos");
            ok(Arrays.equals(Api.frameThumbnail(frame,"usb",photo.hash),new byte[]{1,2,3}),"thumbnail API decodes bounded JPEG payload");
            frame.token="wrong";try{Api.deleteFramePhotos(frame,"usb",Arrays.asList(photo.hash));throw new AssertionError("unauthorized deletion accepted");}catch(IOException expected){ok(stored.contains(photo.hash),"unauthorized request leaves frame photo intact");}frame.token=token;
            try{Api.deleteFramePhotos(frame,"different-usb",Arrays.asList(photo.hash));throw new AssertionError("wrong volume accepted");}catch(IOException expected){ok(stored.contains(photo.hash),"changed storage cannot delete prior selection");}
            try{Api.deleteFramePhotos(frame,"usb",Arrays.asList(photo.hash,"../bad"));throw new AssertionError("invalid selection accepted");}catch(IOException expected){ok(stored.contains(photo.hash),"whole malformed batch rejected before deletion");}
            ok(Api.deleteFramePhotos(frame,"usb",Arrays.asList(photo.hash)).getJSONArray("deleted").length()==1&&!stored.contains(photo.hash),"confirmed client request deletes selected frame photo");
            ok(file.exists()&&Arrays.equals(bytes,Files.readAllBytes(file.toPath())),"sender's source photo remains unchanged after remote deletion");
            ok(Api.deleteFramePhotos(frame,"usb",Arrays.asList(photo.hash)).getJSONArray("missing").length()==1,"repeat delete safely reports missing");
            ok(Api.framePhotos(frame,new JSONObject()).getJSONArray("photos").length()==0,"gallery refresh reflects deletion");
            frame.token="wrong";try{Api.check(frame,photos);throw new AssertionError("wrong token accepted");}catch(IOException expected){checks++;}frame.token=token;
            frame.fingerprint=Wire.hash("wrong certificate");try{Api.check(frame,photos);throw new AssertionError("wrong certificate accepted");}catch(SSLException expected){checks++;}
            try{Api.address("192.168.1.1:8844/path");throw new AssertionError("URL path accepted");}catch(IOException expected){checks++;}
            ok(Api.address("192.168.0.2").equals("192.168.0.2:8844"),"default local port");
        }finally{server.close();pool.shutdownNow();pool.awaitTermination(10,TimeUnit.SECONDS);try(java.util.stream.Stream<Path> paths=Files.walk(base)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception e){throw new RuntimeException(e);}});}}
        System.out.println("PASS: "+checks+" actual-client TLS/API integration checks");
    }
}
