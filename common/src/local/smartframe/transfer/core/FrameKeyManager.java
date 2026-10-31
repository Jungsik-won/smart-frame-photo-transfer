package local.smartframe.transfer.core;

import java.net.Socket;
import java.security.*;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/** Both Android engine-backed sockets and ordinary JSSE sockets must select the same identity. */
public final class FrameKeyManager extends X509ExtendedKeyManager {
    private final String alias;
    private final PrivateKey key;
    private final X509Certificate certificate;
    public FrameKeyManager(String alias,PrivateKey key,X509Certificate certificate){this.alias=alias;this.key=key;this.certificate=certificate;}
    private String serverAlias(String type){return type!=null&&type.startsWith("RSA")?alias:null;}
    public String[] getClientAliases(String type,Principal[] issuers){return null;}
    public String chooseClientAlias(String[] types,Principal[] issuers,Socket socket){return null;}
    public String chooseEngineClientAlias(String[] types,Principal[] issuers,SSLEngine engine){return null;}
    public String[] getServerAliases(String type,Principal[] issuers){return serverAlias(type)==null?null:new String[]{alias};}
    public String chooseServerAlias(String type,Principal[] issuers,Socket socket){return serverAlias(type);}
    public String chooseEngineServerAlias(String type,Principal[] issuers,SSLEngine engine){return serverAlias(type);}
    public X509Certificate[] getCertificateChain(String name){return alias.equals(name)?new X509Certificate[]{certificate}:null;}
    public PrivateKey getPrivateKey(String name){return alias.equals(name)?key:null;}
}
