import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import local.smartframe.transfer.core.Wire;

public class WireTest {
    private static int checks;
    static void ok(boolean v,String msg){checks++;if(!v)throw new AssertionError(msg);}
    static Wire.Request parse(String s)throws Exception{return Wire.request(new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII)));}
    static void rejects(String s)throws Exception{try{parse(s);throw new AssertionError("accepted unsafe input");}catch(IOException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        ok(Wire.hash("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"SHA-256 vector");
        ok(Wire.validHash(Wire.hash("photo"))&&!Wire.validHash("../file"),"hash validation");
        ok(Wire.equal("same","same")&&!Wire.equal("same","other")&&!Wire.equal(null,""),"token comparison");
        String photo="binary photo\u0000\u00ff";byte[] body=photo.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream req=new ByteArrayOutputStream();req.write(("POST /v1/upload HTTP/1.1\r\nContent-Length: "+body.length+"\r\nX-Photo-Sha256: "+Wire.hash(photo)+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));req.write(body);
        InputStream in=new ByteArrayInputStream(req.toByteArray());Wire.Request r=Wire.request(in);ByteArrayOutputStream result=new ByteArrayOutputStream();Wire.copyExactly(in,result,r.length);
        ok(r.method.equals("POST")&&r.path.equals("/v1/upload")&&Arrays.equals(body,result.toByteArray()),"header parsing does not consume binary body");
        rejects("POST / HTTP/1.1\r\nContent-Length: 1\r\ncontent-length: 2\r\n\r\n");
        rejects("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\nContent-Length: 1\r\n\r\n");
        rejects("POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n");
        rejects("POST / HTTP/1.1\r\nContent-Length: 52428801\r\n\r\n");
        rejects("POST / HTTP/1.1\r\n\r\n");
        rejects("GET / HTTP/1.1\n\n");
        rejects("GET / HTTP/1.1\r\nHost: incomplete");
        rejects("GET / HTTP/1.1\r\nX: "+String.join("",Collections.nCopies(5000,"a"))+"\r\n\r\n");
        try{Wire.copyExactly(new ByteArrayInputStream(new byte[3]),new ByteArrayOutputStream(),4);throw new AssertionError("truncated upload accepted");}catch(EOFException expected){checks++;}
        byte[] large=new byte[100003];new Random(42).nextBytes(large);ByteArrayOutputStream copied=new ByteArrayOutputStream();Wire.copyExactly(new ByteArrayInputStream(large),copied,large.length);
        ok(Arrays.equals(large,copied.toByteArray()),"multi-buffer upload");
        ok(Wire.hash(new ByteArrayInputStream(large)).equals(Wire.hex(Wire.digest().digest(large))),"stream hash matches bytes");
        ByteArrayOutputStream response=new ByteArrayOutputStream();Wire.response(response,200,"{\"이름\":\"액자\"}");byte[] resp=response.toByteArray();String text=new String(resp,StandardCharsets.UTF_8);
        ok(text.contains("Content-Length: "+"{\"이름\":\"액자\"}".getBytes(StandardCharsets.UTF_8).length+"\r\n"),"response uses UTF-8 byte count");
        System.out.println("PASS: "+checks+" transport/integrity checks");
    }
}
