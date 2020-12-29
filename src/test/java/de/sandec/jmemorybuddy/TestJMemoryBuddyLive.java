package de.sandec.jmemorybuddy;

import org.junit.Assert;
import org.junit.Test;
import de.sandec.jmemorybuddy.JMemoryBuddy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestJMemoryBuddyLive {
    @Test
    public void testMarkKeepsGCBehaviour() throws InterruptedException {

        int initialCollected = JMemoryBuddyLive.getReport().collectedEntries;
        JMemoryBuddy.memoryTest(checker -> {
            Object o = new Object();
            JMemoryBuddyLive.markCollectable("test", o);
            checker.assertCollectable(o);
        });
        Thread.sleep(50); // we might have a slight delay,
                                   // because processing the collection might happen slightly delayed.
        Assert.assertEquals(initialCollected + 1, JMemoryBuddyLive.getReport().collectedEntries);
    }
}
