/**
 * Module descriptor for the JMemoryBuddy module.
 */
module one.jpro.jmemorybuddy {
    requires java.management;
    requires jdk.management;

    exports de.sandec.jmemorybuddy;
}