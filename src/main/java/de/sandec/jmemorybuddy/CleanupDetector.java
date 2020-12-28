package de.sandec.jmemorybuddy;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;

public class CleanupDetector {

    private static HashSet<PhantomReferenceWithRunnable> references = new HashSet<PhantomReferenceWithRunnable>();
    private static ReferenceQueue queue = new ReferenceQueue();;

    static {
        Thread cleanupDetectorThread = new Thread(() -> {
            while (true) {
                try {
                    PhantomReferenceWithRunnable r = (PhantomReferenceWithRunnable) queue.remove();
                    references.remove(r);
                    r.r.run();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, "JMemoryBuddy-cleanup-detector");
        cleanupDetectorThread.setDaemon(true);
        cleanupDetectorThread.start();
    }

    private static PhantomReferenceWithRunnable pr = null;
    public static void onCleanup(Object obj, Runnable r) {
        onCleanup(new PhantomReferenceWithRunnable(obj,r));
    }
    public static void onCleanup(PhantomReferenceWithRunnable phantomref) {
        references.add(phantomref);
    }

    public static class PhantomReferenceWithRunnable extends PhantomReference {
        Runnable r = null;
        PhantomReferenceWithRunnable(Object ref, Runnable r) {
            super(ref, queue);
            this.r = r;
        }
    }
}
