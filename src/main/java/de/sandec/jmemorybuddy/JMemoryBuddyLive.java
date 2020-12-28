package de.sandec.jmemorybuddy;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JMemoryBuddyLive {

    static int collectedEntrys = 0;
    static Set<CollectableEntry> collectables = new HashSet<>();

    synchronized static public void markCollectable(String name, Object ref) {
        CollectableEntry entry = new CollectableEntry(System.currentTimeMillis(), name);
        AssertCollectableLive pRef = new AssertCollectableLive(ref, () -> {
            collectables.remove(entry);
        });
        collectables.add(entry);
        CleanupDetector.onCleanup(pRef);
    }

    synchronized static private void unmarkCollectable(CollectableEntry entry) {
        collectables.remove(entry);
        collectedEntrys += 1;
    }

    static class CollectableEntry {
        public long collectableSince;
        public String name;
        CollectableEntry(long time, String name) {
            collectableSince = time;
            this.name = name;
        }
    }

    static class AssertCollectableLive extends CleanupDetector.PhantomReferenceWithRunnable {
        AssertCollectableLive(Object ref, Runnable r) {
            super(ref, r);
        }
    }
}
