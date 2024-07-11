package de.sandec.jmemorybuddy;

import com.sun.management.HotSpotDiagnosticMXBean;

import javax.management.MBeanServer;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * JMemoryBuddy provides various methods to test for memory leaks.
 * It makes it easy to verify the memory behavior in a unit test ensuring the stability and quality of your code.
 * Checkout <a href="https://github.com/Sandec/JMemoryBuddy">https://github.com/Sandec/JMemoryBuddy</a> for more documentation.
 */
public class JMemoryBuddy {

    private static final int steps;
    private static final int testDuration;
    private static final int sleepDuration;
    private static final boolean createHeapdump;
    private static final int garbageAmount;
    private static String mxBeanProxyName = "com.sun.management:type=HotSpotDiagnostic";
    private static String outputFolderString = ".";

    static {
        outputFolderString = System.getProperty("jmemorybuddy.output", getDefaultOutputFolder());
        testDuration = Integer.parseInt(System.getProperty("jmemorybuddy.testDuration","1000"));
        steps = Integer.parseInt(System.getProperty("jmemorybuddy.steps", "10"));
        createHeapdump = Boolean.parseBoolean(System.getProperty("jmemorybuddy.createHeapdump", "true"));
        garbageAmount = Integer.parseInt(System.getProperty("jmemorybuddy.garbageAmount", "99999"));

        sleepDuration = testDuration / steps;
    }

    private static String getDefaultOutputFolder() {
        File folder1 = new File("target");
        File folder2 = new File("build");

        if (folder1.exists()) return folder1.getAbsolutePath();
        if (folder2.exists()) return folder2.getAbsolutePath();
        return ".";
    }

    static void createGarbage() {
        LinkedList<Integer> list = new LinkedList<>();
        int counter = 0;
        while (counter < garbageAmount) {
            counter += 1;
            list.add(1);
        }
    }

    /**
     * Checks whether the content of the WeakReference can be collected.
     * @param weakReference The WeakReference to check.
     */
    public static void assertCollectable(WeakReference<?> weakReference) {
        if (!checkCollectable(weakReference)) {
            AssertCollectable assertCollectable = new AssertCollectable(weakReference);
            createHeapDump();
            throw new AssertionError("Content of WeakReference was not collected. content: " + weakReference.get());
        }
    }

    /**
     * Checks whether the content of the WeakReference can be collected.
     * @param weakReference The WeakReference to check.
     * @return Returns true, when the provided WeakReference can be collected.
     */
    public static boolean checkCollectable(WeakReference<?> weakReference) {
        return checkCollectable(steps, weakReference) > 0;
    }

    private static int checkCollectable(int stepsLeft, WeakReference<?> weakReference) {
        int counter = stepsLeft;

        if (weakReference.get() != null) {
            createGarbage();
            System.gc();
            System.runFinalization();
        }

        while (counter > 0 && weakReference.get() != null) {
            try {
                Thread.sleep(sleepDuration);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            counter = counter - 1;
            createGarbage();
            System.gc();
            System.runFinalization();
        }

        if (weakReference.get() == null && counter < steps / 3) {
            int percentageUsed = (steps - counter) / steps * 100;
            System.out.println("Warning test seems to be unstable. time used: " + percentageUsed + "%");
        }

        return counter;
    }

    /**
     * Checks whether the content of the WeakReference cannot be collected.
     * @param weakReference The WeakReference to check.
     */
    public static void assertNotCollectable(WeakReference<Object> weakReference) {
        if (!checkNotCollectable(weakReference)) {
            throw new AssertionError("Content of WeakReference was collected!");
        }
    }

    /**
     * Checks whether the content of the WeakReference cannot be collected.
     * @param weakReference The WeakReference to check.
     * @return Returns true, when the provided WeakReference cannot be collected.
     */
    public static boolean checkNotCollectable(WeakReference<?> weakReference) {
        createGarbage();
        System.gc();
        System.runFinalization();
        createGarbage();
        System.gc();
        return weakReference.get() != null;
    }

    /**
     * A standard method to define a test which checks code for specific memory semantic.
     * The parameter of the lambda provides an API to define the required memory semantic.
     * @param f A function which gets executed with the API to define the required memory semantic.
     */
    public static void memoryTest(Consumer<MemoryTestAPI> f) {
        LinkedList<WeakReference<?>> toBeCollected = new LinkedList<>();
        LinkedList<AssertNotCollectable> toBeNotCollected = new LinkedList<>();
        LinkedList<SetAsReferenced> toBeReferenced = new LinkedList<>();

        f.accept(new MemoryTestAPI() {
            public void assertCollectable(Object ref) {
                Objects.requireNonNull(ref);
                toBeCollected.add(new WeakReference<>(ref));
            }
            public void assertNotCollectable(Object ref) {
                Objects.requireNonNull(ref);
                toBeNotCollected.add(new AssertNotCollectable(ref));
            }
            public void setAsReferenced(Object ref) {
                Objects.requireNonNull(ref);
                toBeReferenced.add(new SetAsReferenced(ref));
            }
        });

        int stepsLeft = steps;
        boolean failed = false;

        for (WeakReference<?> wRef: toBeCollected) {
            stepsLeft = checkCollectable(stepsLeft, wRef);
        }
        if (stepsLeft == 0) {
            failed = true;
        }
        for (AssertNotCollectable wRef: toBeNotCollected) {
            if (!checkNotCollectable(wRef.getWeakReference())) {
                failed = true;
            };
        }

        if (failed) {
            LinkedList<AssertCollectable> toBeCollectedMarked = new LinkedList<>();
            LinkedList<AssertNotCollectable> toBeNotCollectedMarked = new LinkedList<>();

            for (WeakReference<?> wRef: toBeCollected) {
                if (wRef.get() != null) {
                    toBeCollectedMarked.add(new AssertCollectable(wRef));
                }
            }
            for (AssertNotCollectable wRef: toBeNotCollected) {
                if (wRef.getWeakReference().get() == null) {
                    toBeNotCollectedMarked.add(wRef);
                }
            }
            createHeapDump();
            if (toBeNotCollectedMarked.isEmpty()) {
                throw new AssertionError("The following references should be collected: " + toBeCollectedMarked);
            } else if(toBeCollectedMarked.isEmpty()) {
                throw new AssertionError("The following references should not be collected: " + toBeNotCollectedMarked);
            } else {
                throw new AssertionError("The following references should be collected: " + toBeCollectedMarked + " and " + toBeNotCollected.size() + " should not be collected: " + toBeNotCollectedMarked);
            }
        }
    }

    static void createHeapDump() {
        if (createHeapdump) {
            try {
                String dateString = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                String fileName = "heapdump_jmemb_" + dateString + ".hprof";
                File outputFolder = new File(outputFolderString);
                String heapdumpFile = new java.io.File(outputFolder, fileName).getAbsolutePath();
                System.out.println("Creating Heapdump at: " + heapdumpFile);
                getHotspotMBean().dumpHeap(heapdumpFile, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("No Heapdump was created. You might want to change the configuration to get a HeapDump.");
        }
    }

    private static void setMxBeanProxyName(String mxBeanName) {
        mxBeanProxyName = mxBeanName;
    }

    private static HotSpotDiagnosticMXBean getHotspotMBean() throws IOException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        return ManagementFactory.newPlatformMXBeanProxy(server,
                mxBeanProxyName, HotSpotDiagnosticMXBean.class);
    }

    /**
     * This class provides different methods, which can be used to declare memory-constraints.
     * You can get an instance through the lambda of the method JMemoryBuddy.memoryTest.
     */
    public interface MemoryTestAPI {

        /**
         * After executing the lambda, the provided ref must be collectable. Otherwise, an Exception is thrown.
         * @param ref The reference which should be collectable.
         */
        void assertCollectable(Object ref);

        /**
         * After executing the lambda, the provided ref must be not collectable. Otherwise, an Exception is thrown.
         * @param ref The reference which should not be collectable.
         */
        void assertNotCollectable(Object ref);

        /**
         * The provided reference will be reference hard, so it won't be collected, until memoryTest finishes.
         * @param ref The reference which should get a hard reference for this test.
         */
        void setAsReferenced(Object ref);
    }

    static class AssertCollectable {
        WeakReference<?> assertCollectable;

        AssertCollectable(WeakReference<?> ref) {
            this.assertCollectable = ref;
        }

        WeakReference<?> getWeakReference() {
            return assertCollectable;
        }

        @Override
        public String toString() {
            Object el = assertCollectable.get();
            return el != null ? el.toString() : "null";
        }
    }

    private static class AssertNotCollectable {
        WeakReference<?> assertNotCollectable;
        String originalResultOfToString;

        AssertNotCollectable(Object ref) {
            this.assertNotCollectable = new WeakReference<>(ref);
            originalResultOfToString = ref.toString();
        }

        WeakReference<?> getWeakReference() {
            return assertNotCollectable;
        }

        @Override
        public String toString() {
            return originalResultOfToString;
        }
    }

    private static class SetAsReferenced {
        Object setAsReferenced;

        SetAsReferenced(Object ref) {
            this.setAsReferenced = ref;
        }
    }
}
