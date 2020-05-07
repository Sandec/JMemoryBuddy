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
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Checkout <a href="https://github.com/Sandec/JMemoryBuddy">https://github.com/Sandec/JMemoryBuddy</a> for more documentation.
 */
public class JMemoryBuddy {

    private static int steps = 10;
    private static int overallTime = 1000;
    private static int sleepTime = overallTime / steps;
    private static boolean createHeapdump = true;
    private static int garbageAmount = 999999;
    private static String MX_BEAN_PROXY_TYPE = "com.sun.management:type=HotSpotDiagnostic";
    private static String outputFolderString = ".";

    static {
        outputFolderString = System.getProperty("jmemorybuddy.output",".");
        overallTime = Integer.parseInt(System.getProperty("jmemorybuddy.checktime","1000"));
        steps = Integer.parseInt(System.getProperty("jmemorybuddy.steps", "10"));
        createHeapdump = Boolean.parseBoolean(System.getProperty("jmemorybuddy.createHeapdump", "true"));
        garbageAmount = Integer.parseInt(System.getProperty("jmemorybuddy.garbageAmount", "10"));
    }

    private static void createGarbage() {
        LinkedList list = new LinkedList<Integer>();
        int counter = 0;
        while(counter < garbageAmount) {
            counter += 1;
            list.add(1);
        }
    }

    /**
     * Checks whether the content of the WeakReference can be collected.
     * @param weakReference The WeakReference to check.
     */
    public static void assertCollectable(WeakReference weakReference) {
        if(!checkCollectable(weakReference)) {
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
    public static boolean checkCollectable(WeakReference weakReference) {
        return checkCollectable(steps, weakReference) > 0;
    }

    private static int checkCollectable(int stepsLeft, WeakReference weakReference) {
        int counter = stepsLeft;

        if(weakReference.get() != null) {
            createGarbage();
            System.gc();
            System.runFinalization();
        }

        while(counter > 0 && weakReference.get() != null) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {}
            counter = counter - 1;
            createGarbage();
            System.gc();
            System.runFinalization();
        }

        if(weakReference.get() == null && counter < steps / 3) {
            int percentageUsed = (int) ((steps - counter) / steps * 100);
            System.out.println("Warning test seems to be unstable. time used: " + percentageUsed + "%");
        }

        return counter;
    }

    /**
     * Checks whether the content of the WeakReference can not be collected.
     * @param weakReference The WeakReference to check.
     */
    public static void assertNotCollectable(WeakReference weakReference) {
        if(!checkNotCollectable(weakReference)) {
            throw new AssertionError("Content of WeakReference was collected!");
        }
    }

    /**
     * Checks whether the content of the WeakReference can not be collected.
     * @param weakReference The WeakReference to check.
     * @return Returns true, when the provided WeakReference can be collected.
     */
    public static boolean checkNotCollectable(WeakReference weakReference) {
        createGarbage();
        System.gc();
        return weakReference.get() != null;
    }

    /**
     * A standard method to define a test which checks code for specific memory semantic.
     * The parameter of the lambda provides an API to define the required memory semantic.
     * @param f A function which get's executed with the API to define the required memory semantic.
     */
    public static void memoryTest(Consumer<MemoryTestAPI> f) {
        LinkedList<WeakReference> toBeCollected = new LinkedList<WeakReference>();
        LinkedList<AssertNotCollectable> toBeNotCollected = new LinkedList<AssertNotCollectable>();
        LinkedList<SetAsReferenced> toBeReferenced = new LinkedList<SetAsReferenced>();

        f.accept(new MemoryTestAPI() {
            public void assertCollectable(Object ref) {
                if(ref == null) throw new NullPointerException();
                toBeCollected.add(new WeakReference<Object>(ref));
            }
            public void assertNotCollectable(Object ref) {
                if(ref == null) throw new NullPointerException();
                toBeNotCollected.add(new AssertNotCollectable(ref));
            }
            public void setAsReferenced(Object ref) {
                if(ref == null) throw new NullPointerException();
                toBeReferenced.add(new SetAsReferenced(ref));
            }
        });

        int stepsLeft = steps;
        boolean failed = false;

        for(WeakReference wRef: toBeCollected) {
            stepsLeft = checkCollectable(stepsLeft, wRef);
        }
        if(stepsLeft == 0) {
            failed = true;
        }
        for(AssertNotCollectable wRef: toBeNotCollected) {
            if(!checkNotCollectable(wRef.getWeakReference())) {
                failed = true;
            };
        }

        if(failed) {
            LinkedList<AssertCollectable> toBeCollectedMarked = new LinkedList<AssertCollectable>();
            LinkedList<AssertNotCollectable> toBeNotCollectedMarked = new LinkedList<AssertNotCollectable>();

            for(WeakReference wRef: toBeCollected) {
                if(wRef.get() != null) {
                    toBeCollectedMarked.add(new AssertCollectable(wRef));
                }
            }
            for(AssertNotCollectable wRef: toBeNotCollected) {
                if(wRef.getWeakReference().get() == null) {
                    toBeNotCollectedMarked.add(wRef);
                }
            }
            createHeapDump();
            if(toBeNotCollectedMarked.isEmpty()) {
                throw new AssertionError("The following references should be collected: " + toBeCollectedMarked);
            } else {
                throw new AssertionError("The following references should be collected: " + toBeCollectedMarked + " and " + toBeNotCollected.size() + " should not be collected: " + toBeNotCollectedMarked);
            }
        }


    }


    private static void createHeapDump() {
        if(createHeapdump) {
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
        MX_BEAN_PROXY_TYPE = mxBeanName;
    }

    private static HotSpotDiagnosticMXBean getHotspotMBean() throws IOException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean bean =
                ManagementFactory.newPlatformMXBeanProxy(server,
                        MX_BEAN_PROXY_TYPE, HotSpotDiagnosticMXBean.class);
        return bean;
    }

    public static interface MemoryTestAPI {
        /**
         * After executing the lambda, the provided ref must be collectable. Otherwise an Exception is thrown.
         * @param ref The reference which should be collectable.
         */
        public void assertCollectable(Object ref);
        /**
         * After executing the lambda, the provided ref must be not collectable. Otherwise an Exception is thrown.
         * @param ref The reference which should not be collectable.
         */
        public void assertNotCollectable(Object ref);

        /**
         * The provided reference will be reference hard, so it won't be collected, until memoryTest finishes.
         * @param ref The reference which should get a hard reference for this test.
         */
        public void setAsReferenced(Object ref);
    }

    static class AssertCollectable {
        WeakReference<Object> assertCollectable;

        AssertCollectable(WeakReference<Object> ref) {
            this.assertCollectable = ref;
        }

        WeakReference<Object> getWeakReference() {
            return assertCollectable;
        }

        @Override
        public String toString() {
            Object el = assertCollectable.get();
            return el != null ? el.toString() : "null";
        }
    }

    private static class AssertNotCollectable {
        WeakReference<Object> assertNotCollectable;
        String originalResultOfToString;

        AssertNotCollectable(Object ref) {
            this.assertNotCollectable = new WeakReference<>(ref);
            originalResultOfToString = ref.toString();
        }

        WeakReference<Object> getWeakReference() {
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