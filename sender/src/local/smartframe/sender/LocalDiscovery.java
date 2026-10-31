package local.smartframe.sender;

import android.content.Context;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Locate a paired frame after DHCP address changes. Certificate pin still authenticates it. */
final class LocalDiscovery {
    static String find(Context context,String id){
        NsdManager nsd=context.getSystemService(NsdManager.class);CountDownLatch ready=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();AtomicBoolean active=new AtomicBoolean(true),resolving=new AtomicBoolean();
        ConcurrentLinkedQueue<NsdServiceInfo> queue=new ConcurrentLinkedQueue<>();WifiManager.MulticastLock lock=null;
        Runnable resolve=new Runnable(){public void run(){
            if(!active.get()||!resolving.compareAndSet(false,true))return;NsdServiceInfo next=queue.poll();if(next==null){resolving.set(false);return;}
            Runnable again=this;
            try{nsd.resolveService(next,new NsdManager.ResolveListener(){
                public void onResolveFailed(NsdServiceInfo i,int e){resolving.set(false);again.run();}
                public void onServiceResolved(NsdServiceInfo i){
                    byte[] found=i.getAttributes().get("id");
                    if(active.get()&&found!=null&&id.equals(new String(found,StandardCharsets.UTF_8))&&i.getHost() instanceof Inet4Address){result.set(i.getHost().getHostAddress()+":"+i.getPort());ready.countDown();}
                    resolving.set(false);again.run();
                }
            });}catch(Exception e){resolving.set(false);again.run();}
        }};
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){}
            public void onServiceFound(NsdServiceInfo i){if(active.get()&&i.getServiceType().contains("_smartframe._tcp")){queue.add(i);resolve.run();}}
            public void onServiceLost(NsdServiceInfo i){}
            public void onDiscoveryStopped(String t){}
            public void onStartDiscoveryFailed(String t,int e){ready.countDown();}
            public void onStopDiscoveryFailed(String t,int e){}
        };
        try{
            WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);lock=wifi.createMulticastLock("frame-reconnect");lock.setReferenceCounted(false);lock.acquire();
            nsd.discoverServices("_smartframe._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);ready.await(4,TimeUnit.SECONDS);
        }catch(Exception ignored){}finally{active.set(false);try{nsd.stopServiceDiscovery(listener);}catch(Exception ignored){}if(lock!=null&&lock.isHeld())lock.release();}
        return result.get();
    }
}
