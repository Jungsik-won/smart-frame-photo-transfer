import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import local.smartframe.transfer.core.FrameKeyManager;

/** Reproduces the engine-callback omission that a socket-only fixture did not exercise. */
public final class TlsEngineTest {
    static void tasks(SSLEngine e){Runnable task;while((task=e.getDelegatedTask())!=null)task.run();}
    static boolean unwrap(SSLEngine e,ByteBuffer network,ByteBuffer app)throws Exception{
        boolean progress=false;network.flip();
        while(network.hasRemaining()){
            app.clear();SSLEngineResult r=e.unwrap(network,app);tasks(e);
            if(r.getStatus()==SSLEngineResult.Status.CLOSED)throw new SSLException("Engine closed");
            if(r.bytesConsumed()==0&&r.bytesProduced()==0)break;progress=true;
        }
        network.compact();return progress;
    }
    static void handshake(X509ExtendedKeyManager km)throws Exception{
        SSLContext server=SSLContext.getInstance("TLSv1.2");server.init(new KeyManager[]{km},null,null);
        SSLContext client=SSLContext.getInstance("TLSv1.2");client.init(null,new TrustManager[]{new X509TrustManager(){public void checkClientTrusted(X509Certificate[] c,String t){}public void checkServerTrusted(X509Certificate[] c,String t){}public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}}},null);
        SSLEngine s=server.createSSLEngine(),c=client.createSSLEngine();s.setUseClientMode(false);c.setUseClientMode(true);
        String[] cipher={"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"};s.setEnabledCipherSuites(cipher);c.setEnabledCipherSuites(cipher);s.setEnabledProtocols(new String[]{"TLSv1.2"});c.setEnabledProtocols(new String[]{"TLSv1.2"});
        ByteBuffer cToS=ByteBuffer.allocate(262144),sToC=ByteBuffer.allocate(262144),app=ByteBuffer.allocate(65536),empty=ByteBuffer.allocate(0);
        c.beginHandshake();s.beginHandshake();
        for(int n=0;n<1000;n++){
            tasks(c);tasks(s);
            if(c.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_WRAP)c.wrap(empty,cToS);
            unwrap(s,cToS,app);tasks(s);
            if(s.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_WRAP)s.wrap(empty,sToC);
            unwrap(c,sToC,app);tasks(c);
            if(c.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING&&s.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING){
                byte[] payload="verified-photo-connection".getBytes("UTF-8");cToS.clear();c.wrap(ByteBuffer.wrap(payload),cToS);cToS.flip();app.clear();s.unwrap(cToS,app);app.flip();byte[] received=new byte[app.remaining()];app.get(received);
                if(!java.util.Arrays.equals(payload,received))throw new AssertionError("Application data differs");return;
            }
        }
        throw new SSLException("Handshake stalled");
    }
    public static void main(String[] args)throws Exception{
        KeyStore ks=KeyStore.getInstance("JKS");try(InputStream in=new FileInputStream(args[0])){ks.load(in,"local-test-only".toCharArray());}
        final PrivateKey key=(PrivateKey)ks.getKey("test","local-test-only".toCharArray());final X509Certificate cert=(X509Certificate)ks.getCertificate("test");
        X509ExtendedKeyManager old=new X509ExtendedKeyManager(){
            public String[] getClientAliases(String k,Principal[] p){return null;}public String chooseClientAlias(String[] k,Principal[] p,Socket s){return null;}
            public String[] getServerAliases(String k,Principal[] p){return k.startsWith("RSA")?new String[]{"test"}:null;}
            public String chooseServerAlias(String k,Principal[] p,Socket s){return k.startsWith("RSA")?"test":null;}
            public X509Certificate[] getCertificateChain(String a){return "test".equals(a)?new X509Certificate[]{cert}:null;}
            public PrivateKey getPrivateKey(String a){return "test".equals(a)?key:null;}
        };
        try{handshake(old);throw new AssertionError("Old implementation unexpectedly succeeded");}catch(SSLException expected){System.out.println("REPRODUCED old engine handshake failure: "+expected.getClass().getSimpleName());}
        handshake(new FrameKeyManager("test",key,cert));
        System.out.println("PASS: fixed engine-backed TLS handshake and encrypted application data");
    }
}
