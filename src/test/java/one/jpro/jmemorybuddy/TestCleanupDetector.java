package one.jpro.jmemorybuddy;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestCleanupDetector {

    CountDownLatch latch = new CountDownLatch(1);
    Runnable r = () -> latch.countDown();

    @Test
    public void isRunnableCalled() throws Exception {
        JMemoryBuddy.memoryTest(checker -> {
            Object o = new Object();
            CleanupDetector.onCleanup(o, r);
            checker.assertCollectable(o);
        });
        latch.await(1, TimeUnit.SECONDS);
    }

    @Test
    public void isPhantomRefCollectable() throws Exception {
        JMemoryBuddy.memoryTest(checker -> {
            CleanupDetector.WeakReferenceWithRunnable pRef =
                    new CleanupDetector.WeakReferenceWithRunnable(new Object(), () -> {
                    });
            CleanupDetector.onCleanup(pRef);
            checker.assertCollectable(pRef);
        });
        latch.await(1, TimeUnit.SECONDS);
    }
}