# JMemoryBuddy
[![Build Status](https://travis-ci.com/Sandec/JMemoryBuddy.svg?branch=master)](https://travis-ci.com/Sandec/JMemoryBuddy)

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
  <version>0.2.0</version>
  <scope>test</scope>
</dependency>
```

#### Gradle
```
dependencies {
    compile "de.sandec:JMemoryBuddy:0.2.0"
}
```

### Quickstart:
```
@Test
public void simpleTest() {
    A referenced = new A();
    JMemoryBuddy.memoryTest(checker -> {
        A notReferenced = new A();
        checker.assertCollectable(notReferenced); // not referenced should be collectable
    });
}
```

It's easy to find the object, which were marked with "assertColectable" but weren't collected.
Just open the heapdump which was mentioned in the console output and filter for "AssertCollectable".
After this you will find Instances which contains the problematic objects.

![visualvm](/screenshot-visualvm.png "Logo Title Text 1")

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
This makes it hard and undeterministic to test for collectability. Nevertheless, **JMemoryBuddy makes testing for memory leaks reliable!**


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
