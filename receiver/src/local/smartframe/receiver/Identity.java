package local.smartframe.receiver;

import android.content.*;
import android.security.keystore.*;
import java.math.BigInteger;
import java.net.Socket;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import javax.crypto.Cipher;
import local.smartframe.transfer.core.Wire;
import local.smartframe.transfer.core.FrameKeyManager;

final class Identity {
    private final SharedPreferences prefs;
    final SSLContext tls;
    final String fingerprint, id;
    private String pin;
    private long pinExpires, blockedUntil;
    private int failures;
    Identity(Context c) throws Exception {
        prefs=c.getSharedPreferences("identity",0);
        String saved=prefs.getString("id",null);
        if(saved==null){ saved=UUID.randomUUID().toString(); prefs.edit().putString("id",saved).commit(); }
        id=saved;
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        // Authorizations of an existing AndroidKeyStore key cannot be expanded in place.
        // Retain the old alias for rollback; a new TLS-capable key needs one fresh pairing.
        final String alias="frame-transfer-tls-v2";
        if(!ks.containsAlias(alias)) {
            KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
            gen.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048)
                // Conscrypt hashes/pads handshake data before delegating to the hardware key.
                .setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE,KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=Smart Frame Local"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(new Date(System.currentTimeMillis()-86400000L))
                .setCertificateNotAfter(new Date(System.currentTimeMillis()+10L*365*86400000L)).build());
            gen.generateKeyPair();
        }
        final PrivateKey key=(PrivateKey)ks.getKey(alias,null);
        final X509Certificate cert=(X509Certificate)ks.getCertificate(alias);
        checkTlsKey(key,cert);
        fingerprint=Wire.hex(Wire.digest().digest(cert.getEncoded()));
        X509ExtendedKeyManager km=new FrameKeyManager(alias,key,cert);
        tls=SSLContext.getInstance("TLSv1.2"); tls.init(new KeyManager[]{km},null,new SecureRandom());
        renewPin();
    }
    private static void checkTlsKey(PrivateKey key,X509Certificate cert)throws Exception{
        byte[] message="SmartFrame local TLS key check".getBytes("UTF-8");
        Signature signer=Signature.getInstance("NONEwithRSA");signer.initSign(key);signer.update(message);byte[] signature=signer.sign();
        Signature verifier=Signature.getInstance("NONEwithRSA");verifier.initVerify(cert);verifier.update(message);
        if(!verifier.verify(signature))throw new GeneralSecurityException("TLS raw signature verification failed");
        // Conscrypt can apply RSA-PSS padding itself, requiring the raw RSA operation too.
        Cipher raw=Cipher.getInstance("RSA/ECB/NoPadding");raw.init(Cipher.ENCRYPT_MODE,key);
        byte[] block=new byte[256];block[255]=42;byte[] signed=raw.doFinal(block);
        Cipher verify=Cipher.getInstance("RSA/ECB/NoPadding");verify.init(Cipher.DECRYPT_MODE,cert.getPublicKey());
        if(!MessageDigest.isEqual(block,verify.doFinal(signed)))throw new GeneralSecurityException("TLS raw RSA verification failed");
    }
    synchronized String pin(){ return System.currentTimeMillis()<pinExpires?pin:"만료됨 — 새 연결번호를 눌러 주세요"; }
    synchronized void renewPin(){pin=String.format(Locale.US,"%06d",new SecureRandom().nextInt(1000000)); pinExpires=System.currentTimeMillis()+600000;}
    synchronized String pair(String given) throws Exception {
        long now=System.currentTimeMillis();
        if(now<blockedUntil)throw new Exception("잠시 뒤 연결번호를 다시 입력해 주세요.");
        if(now>=pinExpires || !Wire.equal(pin,given)) {
            if(++failures>=5){blockedUntil=now+60000;failures=0;}
            throw new Exception("연결번호가 다르거나 만료됐어요. 액자 화면을 확인해 주세요.");
        }
        Set<String> tokens=new HashSet<>(prefs.getStringSet("tokens",new HashSet<>()));
        if(tokens.size()>=20)throw new Exception("등록 가능한 휴대폰 수를 초과했어요. 액자에서 연결을 초기화해 주세요.");
        String token=Wire.randomToken(); tokens.add(Wire.hash(token));
        if(!prefs.edit().putStringSet("tokens",tokens).commit())throw new Exception("연결 정보를 저장하지 못했어요.");
        failures=0; renewPin(); return token;
    }
    synchronized boolean authorized(String header) {
        if(header==null || !header.startsWith("Bearer "))return false;
        String candidate=Wire.hash(header.substring(7));
        for(String h:prefs.getStringSet("tokens",new HashSet<>()))if(Wire.equal(h,candidate))return true;
        return false;
    }
    synchronized void revoke(){prefs.edit().remove("tokens").commit();renewPin();}
    String name(){return prefs.getString("name","우리집 액자");}
    void name(String s){prefs.edit().putString("name",s.trim().isEmpty()?"우리집 액자":s.trim().substring(0,Math.min(40,s.trim().length()))).apply();}
}
