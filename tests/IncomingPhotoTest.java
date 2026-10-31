import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import local.smartframe.transfer.core.*;

public class IncomingPhotoTest {
    static int checks;
    static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static class Sink implements IncomingPhoto.Sink {
        final File dir;int saves;
        Sink(File d){dir=d;}
        public synchronized boolean has(String hash){return new File(dir,hash).isFile();}
        public synchronized boolean save(File file,String hash,String name)throws Exception{if(has(hash))return true;Files.copy(file.toPath(),new File(dir,hash).toPath());saves++;return false;}
    }
    public static void main(String[] args)throws Exception {
        Path base=Files.createTempDirectory(Paths.get(args[0]),"incoming-test-");File temp=Files.createDirectory(base.resolve("temp")).toFile();Sink sink=new Sink(Files.createDirectory(base.resolve("saved")).toFile());
        byte[] photo=new byte[90001];new Random(11).nextBytes(photo);String hash=Wire.hash(new ByteArrayInputStream(photo));
        ok(!IncomingPhoto.receive(new ByteArrayInputStream(photo),photo.length,hash,"a.jpg",temp,sink),"first upload publishes");
        ok(IncomingPhoto.receive(new ByteArrayInputStream(photo),photo.length,hash,"renamed.jpg",temp,sink)&&sink.saves==1,"retry after lost response and renamed duplicate do not publish twice");
        byte[] changed=photo.clone();changed[20]^=1;
        try{IncomingPhoto.receive(new ByteArrayInputStream(changed),changed.length,hash,"a.jpg",temp,sink);throw new AssertionError("bad duplicate body");}catch(IOException expected){checks++;}
        String nextHash=Wire.hash(new ByteArrayInputStream(changed));
        try{IncomingPhoto.receive(new ByteArrayInputStream(photo),photo.length,nextHash,"b.jpg",temp,sink);throw new AssertionError("hash mismatch published");}catch(IOException expected){checks++;}
        ok(!sink.has(nextHash)&&temp.list().length==0,"failed integrity check leaves no publication/temp files");
        try{IncomingPhoto.receive(new ByteArrayInputStream(Arrays.copyOf(changed,200)),changed.length,nextHash,"b.jpg",temp,sink);throw new AssertionError("truncated upload published");}catch(EOFException expected){checks++;}
        ok(!sink.has(nextHash)&&temp.list().length==0,"interrupted stream leaves no partial photo");
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();
        for(int i=0;i<2;i++)futures.add(executor.submit(()->{start.await();return IncomingPhoto.receive(new ByteArrayInputStream(changed),changed.length,nextHash,"same.jpg",temp,sink);}));start.countDown();int dup=0;for(Future<Boolean> f:futures)if(f.get())dup++;executor.shutdown();
        ok(dup==1&&sink.saves==2,"simultaneous identical uploads publish only one additional photo");
        ok(Arrays.equals(changed,Files.readAllBytes(new File(sink.dir,nextHash).toPath())),"published bytes exactly match source");
        ok(temp.list().length==0,"successful uploads clean temporary files");
        File removable=Files.createDirectory(base.resolve("usb")).toFile();
        File usbTemp=Files.createDirectory(removable.toPath().resolve("incoming")).toFile();
        Sink usbSink=new Sink(Files.createDirectory(removable.toPath().resolve("pictures")).toFile());
        ok(!IncomingPhoto.receive(new ByteArrayInputStream(photo),photo.length,hash,"a.jpg",usbTemp,usbSink),"same photo can be saved on a different storage target");
        ok(IncomingPhoto.receive(new ByteArrayInputStream(photo),photo.length,hash,"a.jpg",usbTemp,usbSink)&&usbSink.saves==1,"duplicate on selected storage is not saved again");
        IncomingPhoto.Sink detached=new IncomingPhoto.Sink(){
            public boolean has(String h){return false;}
            public boolean save(File f,String h,String n)throws Exception{
                ok(f.getParentFile().equals(usbTemp),"staging uses selected storage");
                throw new IOException("storage detached before publication");
            }
        };
        try{IncomingPhoto.receive(new ByteArrayInputStream(changed),changed.length,nextHash,"b.jpg",usbTemp,detached);throw new AssertionError("detached destination reported success");}catch(IOException expected){checks++;}
        ok(usbTemp.list().length==0&&temp.list().length==0&&!usbSink.has(nextHash),"publication failure cleans temporary file without fallback to internal storage");
        try(java.util.stream.Stream<Path> paths=Files.walk(base)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}
        System.out.println("PASS: "+checks+" incoming-photo integration checks");
    }
}
