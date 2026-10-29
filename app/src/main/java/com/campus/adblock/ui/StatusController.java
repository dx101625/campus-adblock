package com.campus.adblock.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;
import com.campus.adblock.Cfg;
import com.campus.adblock.StatusProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.xmlpull.v1.XmlPullParser;

/** Only observes existing heartbeats; never launches or changes either target app. */
public final class StatusController implements AutoCloseable {
    public static final String Q = "com.qiekj.user", Z = "com.klcxkj.zqxy";
    public static final class Beat {
        public final long time; public final String version;
        public Beat(long t, String v) { time=t;version=v==null?"":v; }
    }
    public interface Listener { void status(Beat q, Beat z, boolean checking); }
    private final Context context; private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r,"campus-ui-status"));
    private boolean running; private volatile boolean closed; private volatile Process process;
    private Beat q=new Beat(0,""), z=new Beat(0,"");
    public StatusController(Context c, Listener l){context=c.getApplicationContext();listener=l;}
    public void refresh() {
        if(closed||running)return;running=true;listener.status(q,z,true);
        worker.execute(() -> {
            Beat nextQ=probe(Q),nextZ=probe(Z);
            main.post(() -> {if(closed)return;q=nextQ;z=nextZ;running=false;listener.status(q,z,false);});
        });
    }
    private Beat probe(String pkg) {
        File file=new File(context.getApplicationInfo().dataDir,"shared_prefs/"+StatusProvider.GROUP+".xml");
        try(InputStream in=new FileInputStream(file)) {
            Map<String,String> entries=parse(in);
            Beat local=new Beat(number(entries.get("p."+pkg+".ts")),entries.get("p."+pkg+".ver"));
            if(local.time>0)return local;
        }catch(Exception ignored){}
        return root(pkg);
    }
    private Beat root(String pkg) {
        Process current=null;
        try {
            if(closed)return new Beat(0,"");
            current=new ProcessBuilder("su","-c","cat /data/data/"+pkg+"/shared_prefs/"+Cfg.LOCAL_STATUS+".xml").redirectErrorStream(true).start();
            process=current; long deadline=android.os.SystemClock.elapsedRealtime()+2500;
            while(true) {
                try {current.exitValue();break;} catch(IllegalThreadStateException waiting) {
                    if(closed||android.os.SystemClock.elapsedRealtime()>deadline){current.destroy();return new Beat(0,"");}
                    Thread.sleep(40);
                }
            }
            try(InputStream in=current.getInputStream()) {Map<String,String> e=parse(in);return new Beat(number(e.get("ts")),e.get("ver"));}
        }catch(Exception ignored){return new Beat(0,"");}
        finally{if(current!=null)current.destroy();process=null;}
    }
    private static long number(String value){try{return Long.parseLong(value);}catch(Exception e){return 0;}}
    private static Map<String,String> parse(InputStream in)throws Exception {
        Map<String,String> result=new HashMap<>();XmlPullParser p=Xml.newPullParser();p.setInput(in,"UTF-8");
        int event;while((event=p.next())!=XmlPullParser.END_DOCUMENT)if(event==XmlPullParser.START_TAG){
            String key=p.getAttributeValue(null,"name");if(key==null)continue;
            if("string".equals(p.getName()))result.put(key,p.nextText());
            else if("long".equals(p.getName()))result.put(key,p.getAttributeValue(null,"value"));
        }return result;
    }
    public static String ago(UiKit ui,long time){
        long min=Math.max(0,System.currentTimeMillis()-time)/60000;
        return min<1?ui.text("time_now"):min<60?ui.format("time_minutes",min):min<1440?ui.format("time_hours",min/60):ui.format("time_days",min/1440);
    }
    @Override public void close(){closed=true;Process p=process;if(p!=null)p.destroy();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
