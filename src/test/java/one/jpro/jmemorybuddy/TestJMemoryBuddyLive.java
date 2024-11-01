package one.jpro.jmemorybuddy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        Assertions.assertEquals(initialCollected + 1, JMemoryBuddyLive.getReport().collectedEntries);
    }
}
