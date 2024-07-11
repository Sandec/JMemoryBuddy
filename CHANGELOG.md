### 0.5.5
* Parameterize classes and methods providing better type safety
* Set minimum Java version to 11
* Enable JUnit 5 tests running in the CI builds

### 0.5.4
* Slightly improved error messages
* Updated CI to use also use Java17 and Java21
* Updated to JUnit 5
* Removed accidentally committed jol dependency.

### 0.5.3
* Updated build

### 0.5.2
 * Fixed ConcurrentModificationException in JMemoryBuddyLife.
 * Upated the Gradle Build and how the publishing works.

### 0.5.1
 * Replace the PhantomReference in JMemoryBuddyLife with a WeakReference, 
   because it wasn't necessary.

### 0.5.0
 * Added JMemoryBuddyLife, to monitor a running JVM for memory leaks.

### 0.2.6
 * Minor updates and refactoring.

### 0.2.5
 * The heap dumps are now saved by default in the directory `target` or `build` if they exist.

### 0.2.4
 * Updated JavaDoc.

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