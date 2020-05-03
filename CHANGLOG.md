
### 0.2.0
 * Added "Automatic-Module-Name" for better compatibility with jigsaw / Java11.
 * Various variables are now configurable with systemproperties.
 * The method testMemory now shares it's counter when checking whether a element has been collected. 
 This makes the time required to check constant independent of the number of references to checked.

### 0.1.2
 * Changed the error from RuntimeException to AssertionError. This is the same behaviour as junit.