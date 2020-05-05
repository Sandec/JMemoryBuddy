# JMemoryBuddy
[![Build Status](https://travis-ci.com/Sandec/JMemoryBuddy.svg?branch=master)](https://travis-ci.com/Sandec/JMemoryBuddy) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sandec/JMemoryBuddy/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sandec/JMemoryBuddy)

JMemoryBuddy provides an API to unit-test your code for memory leaks.
It was used for internal project at Sandec, especially for [JPro](https://www.jpro.one/). 
We've made it public, so everyone can fix and test their code for memoryleaks in a **proffesional** way.

**Together we can fix all memory leaks in the world. :-)** 

## Dependency
The library is published at MavenCentral
#### Maven 
```
<dependency>
  <groupId>de.sandec</groupId>
  <artifactId>JMemoryBuddy</artifactId>
  <version>0.2.2</version>
  <scope>test</scope>
</dependency>
```

#### Gradle
```
dependencies {
    compile "de.sandec:JMemoryBuddy:0.2.2"
}
```

## How to use:

#### MemoryTest:

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

The lambda provided to the memoryTest method is executed only once. The provided argument named "checker" provides an API to declare how the memory semantic should be.
| Method        |            |
| ------------- |:-------------:|
| assertCollectable(ref)     | After executing the lambda, the provided ref must be collectable. Otherwise an Exception is thrown. |
| assertNotCollectable(ref)     | After executing the lambda, the provided ref must be not collectable. Otherwise an Exception is thrown. |
| setAsReferenced(ref)     | The provided reference won't be collected, until memoryTest finishes all it's tests.|

#### Other utlity methods:

You can also use the method `assertCollectable` and `assertNotCollectable` to check whether a single WeakReference can be collected, but usually the `memoryTest` method is prefered because it results in more elegant tests.


#### Analyzing the heapdump:
JMemoryBuddy makes it easy to analyze the heapDump, because all problematic instances are wrapped inside a class with the name `AssertCollectable`. Just search your heapdump with your prefered tool for this classname:
![visualvm](/screenshot-visualvm.png)





## Configure JMemoryBuddy

You can configure VisualVM with SystemProperties:

| Tables        | Effect           | Default  |
| ------------- |:-------------:| -----:|
| -Djmemorybuddy.createHeapDump    | Should a heapdump created on failure? | true |
| -Djmemorybuddy.output    | The folder were the heapdump get's saved. | "." |

The following values usually shouldn't be changed but might be useful to make tests more stable or reduce the time required.
| Tables        | Effect           | Default  |
| ------------- |:-------------:| -----:|
| -Djmemorybuddy.steps     | Maximum number of times we check whether something is collectable. You probably shouldn't change it. | 10 |
| -Djmemorybuddy.checktime | Maximum time in ms used to check whether something is collectable. You probably shouldn't change it. | 1000 |
| -Djmemorybuddy.garbageAmount     | How much garbage is created to stimulate the garbage collector | 999999 |



    
## FAQ - Why is no one else writing unit-tests for memory leaks?

There are various reasons for this. By spec the command `System.gc()` doesn't have to do anything, 
This makes it hard and undeterministic to test for collectability. Nevertheless, **JMemoryBuddy makes testing for memory leaks reliable!**. Currently all known cases reliable and don't cause false negative test results.


## Projects using JMemoryBuddy:
* [jpro.one](https://jpro.one/) - aka JavaFX for the web
* [controlsfx](https://github.com/controlsfx/controlsfx) - A very often used Library for JavaFX
* [Your project?](https://github.com/Sandec/JMemoryBuddy/pulls)

##### internal developer
publish local:
```
./gradlew publishToMavenLocal
```

publish to sonatype:
```
./gradlew uploadArchives
```
