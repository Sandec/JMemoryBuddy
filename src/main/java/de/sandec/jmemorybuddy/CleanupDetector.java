package de.sandec.jmemorybuddy;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CleanupDetector {

    private static final Set<WeakReferenceWithRunnable> references = ConcurrentHashMap.newKeySet();
    private static final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    static {
        Thread cleanupDetectorThread = new Thread(() -> {
            while (true) {
                try {
                    WeakReferenceWithRunnable r = (WeakReferenceWithRunnable) queue.remove();
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

    /**
     * The runnable gets executed after the object has been collected by the GC.
     */
    public static void onCleanup(Object obj, Runnable r) {
        onCleanup(new WeakReferenceWithRunnable(obj, r));
    }

    /**
     * This version of the method can be used to provide more information
     * in the heap dump by extending WeakReferenceWithRunnable.
     */
    public static void onCleanup(WeakReferenceWithRunnable weakRef) {
        references.add(weakRef);
    }

    /**
     * This class can be extended to provide more meta information to the method onCleanup.
     */
    public static class WeakReferenceWithRunnable extends WeakReference<Object> {
        Runnable r;

        WeakReferenceWithRunnable(Object ref, Runnable r) {
            super(ref, queue);
            this.r = r;
        }
    }
}
