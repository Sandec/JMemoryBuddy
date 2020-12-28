package de.sandec.jmemorybuddy;

import org.junit.Test;
import de.sandec.jmemorybuddy.JMemoryBuddy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestCleanupDetector {

    CountDownLatch latch = new CountDownLatch(1);
    Runnable r = () -> latch.countDown();

    @Test
    public void isRunnableCalled() throws Exception{
        JMemoryBuddy.memoryTest(checker -> {
            Object o = new Object();
            CleanupDetector.onCleanup(o, r);
            checker.assertCollectable(o);
        });
        latch.await(1, TimeUnit.SECONDS);
    }
}