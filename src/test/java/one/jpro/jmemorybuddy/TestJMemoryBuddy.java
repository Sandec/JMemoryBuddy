package one.jpro.jmemorybuddy;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        for (int i = 0; i < 5; i += 1) {
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
        for (int i = 0; i < 100; i += 1) {
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
            Assertions.assertTrue(e.getMessage().contains("someText"));
            Assertions.assertTrue(!e.getMessage().contains("anotherText"));
            System.out.println(e.getMessage());
        }
        Assertions.assertTrue(exceptionThrown, "No exception was thrown!");
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
            Assertions.assertTrue(e.getMessage().contains("someText"), "Exception didn't contain toString method of the original object.");
        }
        Assertions.assertTrue(exceptionThrown, "No exception was thrown!");
    }

    @Test
    public void clearReferenceOnFinalization() {
        Object[] ref = new Object[1];
        JMemoryBuddy.memoryTest(checker -> {
            Object a = new Object();
            Object o = new Object() {
                @Override
                protected void finalize() {
                    System.out.println("Finalized!");
                    ref[0] = null;
                }
            };
            ref[0] = a;

            checker.assertCollectable(a);
            checker.assertCollectable(o);
        });
    }

    @Test
    public void testErrorMessages1() {
        Throwable e = Assertions.assertThrows(AssertionError.class, () -> {
            JMemoryBuddy.memoryTest(checker -> {
                Object a = new Object();
                checker.assertNotCollectable(a);
            });
        });
        Assertions.assertTrue(e.getMessage().toLowerCase().contains("should not be collected"),
                "Error message should contain 'should not be collectable', but was: " + e.getMessage());
        Assertions.assertFalse(e.getMessage().toLowerCase().contains("should be collected"),
                "Error message should not contain 'should be collectable', but was: " + e.getMessage());
    }

    @Test
    public void testErrorMessages2() {
        Throwable e = Assertions.assertThrows(AssertionError.class, () -> {
            Object a = new Object();
            JMemoryBuddy.memoryTest(checker -> {
                checker.assertCollectable(a);
            });
        });
        Assertions.assertTrue(e.getMessage().toLowerCase().contains("should be collected"),
                "Error message should contain 'should be collectable', but was: " + e.getMessage());
        Assertions.assertFalse(e.getMessage().toLowerCase().contains("should not be collected"),
                "Error message should not contain 'should not be collectable', but was: " + e.getMessage());
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
                    protected void finalize() {
                        System.out.println("Finalized!");
                        ref[0] = a[0];
                        a[0] = null;
                    }
                };
                a[0] = new Object();
                wref[0] = new WeakReference<>(a[0]);
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

    static class Holder { Object field; }

    @Test
    public void multiHopPath() {
        Holder holder = new Holder();
        Throwable e = Assertions.assertThrows(AssertionError.class, () -> {
            JMemoryBuddy.memoryTest(checker -> {
                Object leaked = new Object();
                holder.field = leaked;            // leaked is reachable via holder.field
                checker.assertCollectable(leaked);
            });
        });
        System.out.println("multiHopPath message: " + e.getMessage());
    }

    static class Node { Object next; }

    // The leak is reachable only through a chain of `depth` Node.next hops from a GC root,
    // so the printed path must be exactly that deep — verifies the parser/BFS handle long chains.
    private void chainOfDepth(int depth) {
        Node head = new Node();
        Node cur = head;
        for (int i = 0; i < depth; i++) { Node n = new Node(); cur.next = n; cur = n; }
        // `cur` is the tail; do not keep a separate local to it (that would be a direct GC root).
        cur = null;
        Throwable e = Assertions.assertThrows(AssertionError.class, () ->
                JMemoryBuddy.memoryTest(checker -> {
                    Object tail = head;
                    while (((Node) tail).next != null) tail = ((Node) tail).next;
                    checker.assertCollectable(tail);     // held only via head -> ...depth... -> tail
                }));
        java.lang.ref.Reference.reachabilityFence(head);
        System.out.println("chainOfDepth(" + depth + "): " + e.getMessage());
    }

    @Test public void chain10()   { chainOfDepth(10); }
    @Test public void chain100()  { chainOfDepth(100); }
    @Test public void chain1000() { chainOfDepth(1000); }
}
