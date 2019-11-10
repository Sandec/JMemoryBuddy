package de.sandec.jmemorybuddy;


import org.junit.Assert;
import org.junit.Test;

public class TestJMemoryBuddy {

    class A {

    }

    @Test
    public void simpleTest() {
        A referenced = new A();
        JMemoryBuddy.doMemTest(checker -> {
            A notReferenced = new A();
            checker.assertCollectable(notReferenced); // not referenced should be collectable
        });
    }

    @Test
    public void simpleTest2() {
        JMemoryBuddy.doMemTest(checker -> {
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
            JMemoryBuddy.doMemTest(checker -> {
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
            JMemoryBuddy.doMemTest(checker -> {
                A notReferenced = new A();
                checker.assertCollectable(notReferenced); // not referenced should be collectable
            });
        }
    }

    @Test
    public void negativeTest() {
        boolean exceptionThrown = false;
        try {
            A referenced = new A();
            JMemoryBuddy.doMemTest(checker -> {
                checker.assertCollectable(referenced);
            });
        } catch (AssertionError e) {
            exceptionThrown = true;
        }
        Assert.assertTrue("No exception was thrown!", exceptionThrown);
    }

    @Test
    public void testCreateHeapDump() {
        JMemoryBuddy.doHeapDump(); // shouldn't throw an exception
    }
}
