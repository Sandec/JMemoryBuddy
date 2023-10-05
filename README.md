# JMemoryBuddy
[![Build Status](https://travis-ci.com/Sandec/JMemoryBuddy.svg?branch=master)](https://travis-ci.com/Sandec/JMemoryBuddy) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sandec/JMemoryBuddy/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sandec/JMemoryBuddy)

JMemoryBuddy provides an API to unit-test your code for memory leaks.
It also provides an API to monitor a running JVM for memory leaks.
It is used for internal projects at Sandec, especially for [JPro](https://www.jpro.one/). 
We've made it public, so everyone can fix and test their code for memory leaks in a **professional** way.

**Together we can fix all memory leaks in the world. :-)** 

## Dependency
The library is published at MavenCentral
#### Maven 
```
<dependency>
  <groupId>de.sandec</groupId>
  <artifactId>JMemoryBuddy</artifactId>
  <version>0.5.2</version>
  <scope>test</scope>
</dependency>
```

#### Gradle
```
dependencies {
    compile "de.sandec:JMemoryBuddy:0.5.2"
}
```

## How to use:

#### Write unit tests for memory leaks!

The method `JMemoryBuddy.memoryTest` is the usual way to test for leaks with JMemoryBuddy.
A typicial test might look like the following:
```
@Test
public void simpleTest() {
    JMemoryBuddy.memoryTest(checker -> {
        A referenced = new A();
        checker.setAsReferenced(referenced);
        A notReferenced = new A();
        checker.assertCollectable(notReferenced); // notReferenced should be collectable
        checker.assertNotCollectable(referenced); // referenced should not be collectable
    });
}
```

The lambda provided to the memory test method is executed only once. The provided argument named "checker" provides an API to declare how the memory semantic should be.
| Method        |            |
| ------------- |:-------------:|
| assertCollectable(ref)     | After executing the lambda, the provided ref must be collectable. Otherwise, an exception is thrown. |
| assertNotCollectable(ref)     | After executing the lambda, the provided ref must be not collectable. Otherwise, an exception is thrown. |
| setAsReferenced(ref)     | The provided reference won't be collected, until memoryTest finishes all it's tests.|

#### Other utility methods:

You can also use the method `assertCollectable` and `assertNotCollectable` to check whether a single WeakReference can be collected, but usually, the `memoryTest` method is prefered because it results in more elegant tests.


#### Analyzing the heap dump:
JMemoryBuddy makes it easy to analyze the heap dump because all problematic instances are wrapped inside a class with the name `AssertCollectable`. Just search your heap dump with your prefered tool for this class name:
![visualvm](/screenshot-visualvm.png)





## Configure JMemoryBuddy

You can configure VisualVM with SystemProperties:

| Tables        | Effect           | Default  |
| ------------- |:-------------:| -----:|
| -Djmemorybuddy.createHeapDump    | Should a heap dump created on failure? | true |
| -Djmemorybuddy.output    | The folder where the heap dump gets saved. | if target exists, then "target" otherwise "build" |

The following values usually shouldn't be changed but might be useful to make tests more stable or reduce the time required.
| Tables        | Effect           | Default  |
| ------------- |:-------------:| -----:|
| -Djmemorybuddy.steps     | Maximum number of times we check whether something is collectable. You probably shouldn't change it. | 10 |
| -Djmemorybuddy.testDuration | Maximum time in ms used to check whether something is collectable. You probably shouldn't change it. | 1000 |
| -Djmemorybuddy.garbageAmount     | How much garbage is created to stimulate the garbage collector | 999999 |



## Monitor running systems:
Mark references, where you know they can be collected:
```
JMemoryBuddyLive.markCollectable("description",reference);
```
Afterwards you can create a report during runtime with details about leaks.
```
System.gc();
JMemoryBuddyLive.getReport();
```
or search for AssertCollectableLive in a HeapDump.
If the referent of an AssertCollectableLive is reachable, then you have a memory leak.
It's especially interesting to see which leaks happen, if an application runs for severy hours, days, weeks or months. 

    
## FAQ - Why is no one else writing unit-tests for memory leaks?

There are various reasons for this. By spec the command `System.gc()` doesn't have to do anything, 
This makes it hard and undeterministic to test for collectability. Nevertheless, **JMemoryBuddy makes testing for memory leaks reliably!**. Currently, all known cases reliable and don't cause false-negative test results.

* What can i do about SoftReferences? It's hard to check whether they are strongly reachable.

You can use the following JVM arugment: `-XX:SoftRefLRUPolicyMSPerMB=0`. With this argument, SoftReferences behave like WeakReferences.

* I'm getting Leaking references during development, but not during production. What could be the reason?

A common reason might be various debuging features of you IDE. If you use the debugger, the garbage collector doesn't work reliably anymore.

## Real test samples:
* [controlsfx](https://github.com/controlsfx/controlsfx/blob/master/controlsfx/src/test/java/org/controlsfx/control/action/TestActionUtils.java) - A simple test for a isolated JavaFX Components.
* CSSFX ([1](https://github.com/McFoggy/cssfx/blob/master/src/test/java/fr/brouillard/oss/cssfx/test/TestMemoryLeaks.java), [2](https://github.com/McFoggy/cssfx/blob/master/src/test/java/fr/brouillard/oss/cssfx/test/TestURIRegistrar.java)) - Various tests to make sure that a listener based code base doesn't have unwanted changes to the memory semantics.
* [JavaFX](https://github.com/openjdk/jfx/pull/204) - PR for JavaFX itself to simplify some of the existing tests for memory leaks.

## Projects using JMemoryBuddy:
* [jpro.one](https://jpro.one/) - aka JavaFX for the web
* [controlsfx](https://github.com/controlsfx/controlsfx) - A very often used Library for JavaFX
* [CSSFX](https://github.com/McFoggy/cssfx) - Every JavaFX Developer should use this library
* [Your project?](https://github.com/Sandec/JMemoryBuddy/pulls)

##### internal developer
publish local:
```
./gradlew publishToMavenLocal
```

publish to sonatype:
```
./gradlew publishToSonatype closeAndReleaseStagingRepository
```
