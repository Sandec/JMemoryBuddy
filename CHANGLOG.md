### 0.2.3
 * Added JavaDoc.
 * Made various fields and methods private.

### 0.2.2
 * If something get's collected but is marked with `assertNotCollectable`, then the original result of the toString method is used for the error output.
 * Minor improvements/bugfixes to the error messages.

### 0.2.1
 * The following values are now also configurable: `-Djmemorybuddy.createHeapDump` and `-Djmemorybuddy.garbageAmount` 
### 0.2.0
 * Added "Automatic-Module-Name" for better compatibility with jigsaw / Java11.
 * Various variables are now configurable with systemproperties.
 * The method testMemory now shares it's counter when checking whether a element has been collected. 
 This makes the time required to check constant independent of the number of references to checked.

### 0.1.2
 * Changed the error from RuntimeException to AssertionError. This is the same behaviour as junit.