package de.sandec.jmemorybuddy;


import org.junit.Assert;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.List;

public class TestJMemoryBuddy {

    class A {

    }

    @Test
    public void simpleTest() {
        A referenced = new A();
        JMemoryBuddy.memoryTest(checker -> {
            A notReferenced = new A();
            checker.assertCollectable(notReferenced); // not referenced should be collectable
        });
    }

    @Test
    public void simpleTest2() {
        JMemoryBuddy.memoryTest(checker -> {
            A referenced = new A();
            A notReferenced = new A();
            checker.setAsReferenced(referenced);
            checker.assertNotCollectable(referenced);
            checker.assertCollectable(notReferenced); // not referenced should be collectable
        });
    }

    @Test
    public void simpleTest3() {
        for(int i = 0; i < 5; i += 1) {
            A referenced = new A();
            JMemoryBuddy.memoryTest(checker -> {
                A notReferenced = new A();
                checker.assertCollectable(notReferenced); // not referenced should be collectable
                for (int j = 0; j <= 10; j += 1) {
                    JMemoryBuddy.createGarbage();
                    try {
                        Thread.sleep(10);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    @Test
    public void simpleTestRepeated() {
        for(int i = 0; i < 100; i += 1) {
            A referenced = new A();
            JMemoryBuddy.memoryTest(checker -> {
                A notReferenced = new A();
                checker.assertCollectable(notReferenced); // not referenced should be collectable
            });
        }
    }

    @Test
    public void negativeTest() {
        boolean exceptionThrown = false;
        try {
            String referenced = "someText";
            JMemoryBuddy.memoryTest(checker -> {
                List<String> notReferenced = new java.util.LinkedList<>();
                notReferenced.add("anotherText");
                checker.assertCollectable(referenced);
                checker.assertCollectable(notReferenced);
            });
        } catch (AssertionError e) {
            exceptionThrown = true;
            Assert.assertTrue(e.getMessage().contains("someText"));
            Assert.assertTrue(!e.getMessage().contains("anotherText"));
            System.out.println(e.getMessage());
        }
        Assert.assertTrue("No exception was thrown!", exceptionThrown);
    }

    @Test
    public void negativeTest2() {
        boolean exceptionThrown = false;
        try {
            JMemoryBuddy.memoryTest(checker -> {
                List<String> referenced = new java.util.LinkedList<>();
                referenced.add("someText");
                checker.assertNotCollectable(referenced);
            });
        } catch (AssertionError e) {
            exceptionThrown = true;
            System.out.println(e.getMessage());
            Assert.assertTrue("Exception didn't contain toString method of the original object.", e.getMessage().contains("someText"));
        }
        Assert.assertTrue("No exception was thrown!", exceptionThrown);
    }

    @Test
    public void clearReferenceOnFinalization() {
        Object[] ref = new Object[1];
        JMemoryBuddy.memoryTest(checker -> {
            Object a = new Object();
            Object o = new Object() {
                @Override
                public void finalize() {
                    System.out.println("Finalized!");
                    ref[0] = null;
                }
            };
            ref[0] = a;

            checker.assertCollectable(a);
            checker.assertCollectable(o);
        });
    }

    /* This basically demonstrates why finalize is deprecated. It's not a real test. */
    public void setReferenceOnFinalization() {
        Object[] ref = new Object[1];
        WeakReference[] wref = new WeakReference[1];
        try {
            JMemoryBuddy.memoryTest(checker -> {
                Object[] a = new Object[1];
                Object o = new Object() {
                    @Override
                    public void finalize() {
                        System.out.println("Finalized!");
                        ref[0] = a[0];
                        a[0] = null;
                    }
                };
                a[0] = new Object();
                wref[0] = new WeakReference(a[0]);
                checker.assertCollectable(o);
                System.out.println("Not collectable: " + a[0]);
                checker.assertNotCollectable(a[0]);
            });
        } catch (Throwable e) {
            System.out.println("ref[0]: " + ref[0]);
            System.out.println("wref[0]: " + wref[0].get());
            System.out.println("The WeakReference got cleared, but the object is still referenced.");
        }
    }

    @Test
    public void testCreateHeapDump() {
        JMemoryBuddy.createHeapDump(); // shouldn't throw an exception
    }
}
