package de.sandec.jmemorybuddy;


import org.junit.Assert;
import org.junit.Test;

public class TestMemoryLeakUtils {

    class A {

    }

    @Test
    public void simpleTest() {
        A referenced = new A();
        MemoryLeakUtils.doMemTest(checker -> {
            A notReferenced = new A();
            checker.accept(notReferenced); // not referenced should be collectable
        });
    }

    @Test
    public void negativeTest() {
        boolean exceptionThrown = false;
        try {
            A referenced = new A();
            MemoryLeakUtils.doMemTest(checker -> {
                checker.accept(referenced);
            });
        } catch (Exception e) {
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
    }

}
