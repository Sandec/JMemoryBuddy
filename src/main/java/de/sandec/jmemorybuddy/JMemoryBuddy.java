package de.sandec.jmemorybuddy;

import com.sun.management.HotSpotDiagnosticMXBean;
import javax.management.MBeanServer;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.function.Function;

public class JMemoryBuddy {

    static int steps = 10;
    static int overallTime = 5000;
    static int sleepTime = overallTime / steps;

    public static void createGarbage() {
        LinkedList list = new LinkedList<Integer>();
        int counter = 0;
        while(counter < 999999) {
            counter += 1;
            list.add(1);
        }
    }

    public static void assertCollectable(WeakReference weakReference) {
        int counter = 0;

        createGarbage();
        System.gc();

        while(counter < steps && weakReference.get() != null) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {}
            counter = counter + 1;
            createGarbage();
            System.gc();
        }

        if(weakReference.get() != null) {
            createHeapDump();
            throw new AssertionError("Content of WeakReference was not collected. content: " + weakReference.get());
        } else {
            if(counter > steps / 3) {
                int percentageUsed = (int) (counter / steps * 100);
                System.out.println("Warning test seems to be unstable. time used: " + percentageUsed + "%");
            }
        }
    }
    public static void assertNotCollectable(WeakReference weakReference) {
        createGarbage();
        System.gc();
        if(weakReference.get() == null) {
            throw new AssertionError("Content of WeakReference was collected!");
        }
    }

    public static void memoryTest(Consumer<MemeoryTestAPI> f) {
        LinkedList<AssertCollectable> toBeCollected = new LinkedList<AssertCollectable>();
        LinkedList<AssertNotCollectable> toBeNotCollected = new LinkedList<AssertNotCollectable>();
        LinkedList<SetAsReferenced> toBeReferenced = new LinkedList<SetAsReferenced>();

        f.accept(new MemeoryTestAPI() {
            public void assertCollectable(Object ref) {
                if(ref == null) throw new NullPointerException();
                toBeCollected.add(new AssertCollectable(new WeakReference<Object>(ref)));
            }
            public void assertNotCollectable(Object ref) {
                if(ref == null) throw new NullPointerException();
                toBeNotCollected.add(new AssertNotCollectable(new WeakReference<Object>(ref)));
            }
            public void setAsReferenced(Object ref) {
                if(ref == null) throw new NullPointerException();
                toBeReferenced.add(new SetAsReferenced(ref));
            }
        });

        for(AssertCollectable wRef: toBeCollected) {
            assertCollectable(wRef.getWeakReference());
        }
        for(AssertNotCollectable wRef: toBeNotCollected) {
            assertNotCollectable(wRef.getWeakReference());
        }

    }


    public static void createHeapDump() {
        try {
            String dateString = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String fileName = "heapdump_jmemb_" + dateString + ".hprof";
            System.out.println("Creating Heapdump at: " + new java.io.File(fileName).getAbsolutePath());
            getHotspotMBean().dumpHeap(fileName, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static HotSpotDiagnosticMXBean getHotspotMBean() throws IOException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean bean =
                ManagementFactory.newPlatformMXBeanProxy(server,
                        "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
        return bean;
    }

    public static interface MemeoryTestAPI {
        public void assertCollectable(Object ref);
        public void assertNotCollectable(Object ref);
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
    }

    static class AssertNotCollectable {
        WeakReference<Object> assertNotCollectable;

        AssertNotCollectable(WeakReference<Object> ref) {
            this.assertNotCollectable = ref;
        }

        WeakReference<Object> getWeakReference() {
            return assertNotCollectable;
        }
    }

    static class SetAsReferenced {
        Object setAsReferenced;

        SetAsReferenced(Object ref) {
            this.setAsReferenced = ref;
        }
    }

}