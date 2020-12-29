package de.sandec.jmemorybuddy;

import org.junit.Assert;
import org.junit.Test;
import de.sandec.jmemorybuddy.JMemoryBuddy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestJMemoryBuddyLive {
    @Test
    public void testMarkKeepsGCBehaviour() {

        int initialCollected = JMemoryBuddyLive.getReport().collectedEntries;
        JMemoryBuddy.memoryTest(checker -> {
            Object o = new Object();
            JMemoryBuddyLive.markCollectable("test", o);
            checker.assertCollectable(o);
        });
        Assert.assertEquals(initialCollected + 1, JMemoryBuddyLive.getReport().collectedEntries);
    }
}
