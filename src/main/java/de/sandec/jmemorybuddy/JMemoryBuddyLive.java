package de.sandec.jmemorybuddy;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.stream.Collectors;

public class JMemoryBuddyLive {

    static int collectedEntrys = 0;
    static Set<CollectableEntry> collectables = new HashSet<>();

    /**
     * This class can be used to mark objects as collectable. As long as this object isn't collected,
     * it can be found by searching for the class AssertCollectableLive in the heap dump.
     * It can also be found the report accessible by the method getReport.
     */
    synchronized static public void markCollectable(String name, Object ref) {
        Objects.requireNonNull(ref);

        CollectableEntry entry = new CollectableEntry(new Date(), name);
        AssertCollectableLive pRef = new AssertCollectableLive(name, ref, () -> {
            collectedEntrys += 1;
            collectables.remove(entry);
        });
        collectables.add(entry);
        CleanupDetector.onCleanup(pRef);
    }

    /**
     * This clas provides a report,
     * containing a list with details about object marked as collectable with markCollectable.
     * @return The report.
     */
    synchronized static public Report getReport() {
        return new Report(
                collectedEntrys,
                collectables.stream().sorted((a, b) -> a.collectableSince.compareTo(b.collectableSince))
                        .collect(Collectors.toList()));
    }

    public static class CollectableEntry {
        public Date collectableSince;
        public String name;
        CollectableEntry(Date time, String name) {
            collectableSince = time;
            this.name = name;
        }

        @Override
        public String toString() {
            return "CollectableEntry{" +
                    "collectableSince=" + collectableSince +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    static class AssertCollectableLive extends CleanupDetector.WeakReferenceWithRunnable {
        String name;
        AssertCollectableLive(String name, Object ref, Runnable r) {
            super(ref, r);
            this.name = name;
        }
    }

    public static class Report {
        public int collectedEntries;
        public List<CollectableEntry> uncollectedEntries;
        Report(int collectedEntries, List<CollectableEntry> uncollectedEntries) {
            this.collectedEntries = collectedEntries;
            this.uncollectedEntries = uncollectedEntries;
        }

        @Override
        public String toString() {
            return "Report{" +
                    "collectedEntries=" + this.collectedEntries +
                    ", uncollectedEntries=" + this.uncollectedEntries +
                    '}';
        }
    }

}
