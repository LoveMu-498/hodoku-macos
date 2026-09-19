package sudoku;

import java.lang.reflect.*;
import javax.swing.SwingUtilities;

/** Java 9+ public API via reflection preserves the Java 8 compilation contract. */
final class ReplaySleepMonitor implements AutoCloseable {
    private Object desktop,listener;private Class<?> eventType;
    boolean install(final Runnable checkpoint)throws Exception{
        Class<?> d=Class.forName("java.awt.Desktop"), action=Class.forName("java.awt.Desktop$Action");
        desktop=d.getMethod("getDesktop").invoke(null);Object sleep=null;
        for(Object a:action.getEnumConstants())if(a.toString().equals("APP_EVENT_SYSTEM_SLEEP"))sleep=a;
        if(sleep==null||!Boolean.TRUE.equals(d.getMethod("isSupported",action).invoke(desktop,sleep)))return false;
        eventType=Class.forName("java.awt.desktop.SystemEventListener");Class<?> type=Class.forName("java.awt.desktop.SystemSleepListener");
        listener=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,args)->{
            if(m.getName().equals("hashCode"))return System.identityHashCode(p);
            if(m.getName().equals("equals"))return p==args[0];
            if(m.getName().equals("toString"))return "ReplaySleepMonitor";
            // macOS nanoTime excludes sleep. Notifications can arrive after wake: never double-subtract.
            if(m.getName().equals("systemAboutToSleep")||m.getName().equals("systemAwoke"))SwingUtilities.invokeLater(checkpoint);
            return null;
        });
        d.getMethod("addAppEventListener",eventType).invoke(desktop,listener);return true;
    }
    public void close(){try{if(listener!=null)desktop.getClass().getMethod("removeAppEventListener",eventType).invoke(desktop,listener);}catch(Exception ignored){}listener=null;}
}
