package local.smartframe.transfer.core;

import java.io.*;
import java.security.*;

/** Only fully received, hash-verified files can reach the storage transaction. */
public final class IncomingPhoto {
    public interface Sink {
        boolean has(String hash) throws Exception;
        /** Atomically publish, or return true if another upload already published it. */
        boolean save(File file,String hash,String name) throws Exception;
    }
    public static boolean receive(InputStream in,long length,String hash,String name,File tempDir,Sink sink)throws Exception{
        if(!Wire.validHash(hash)||length<1||length>Wire.MAX_PHOTO)throw new IOException("Invalid photo");
        MessageDigest digest=Wire.digest();
        // Consume and validate duplicate bodies too: clients may still be sending when we detect a duplicate.
        if(sink.has(hash)){
            OutputStream discard=new OutputStream(){public void write(int b){}public void write(byte[] b,int off,int len){}};
            Wire.copyExactly(in,new DigestOutputStream(discard,digest),length);
            if(!Wire.equal(hash,Wire.hex(digest.digest())))throw new IOException("사진 검증에 실패했어요. 다시 전송해 주세요.");
            return true;
        }
        File temp=File.createTempFile("receive-",".part",tempDir);
        try{
            try(OutputStream out=new DigestOutputStream(new FileOutputStream(temp),digest)){Wire.copyExactly(in,out,length);}
            if(!Wire.equal(hash,Wire.hex(digest.digest())))throw new IOException("사진 검증에 실패했어요. 다시 전송해 주세요.");
            return sink.save(temp,hash,name);
        }finally{temp.delete();}
    }
}
