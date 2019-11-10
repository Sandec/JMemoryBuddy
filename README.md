# JMemoryBuddy
[![Build Status](https://travis-ci.com/Sandec/JMemoryBuddy.svg?branch=master)](https://travis-ci.com/Sandec/JMemoryBuddy)

This library provides an API to unit-test your code for memory leaks.

## Dependency
The library is published at MavenCentral
#### Maven 
```
<dependency>
  <groupId>de.sandec</groupId>
  <artifactId>JMemoryBuddy</artifactId>
  <version>0.1.2</version>
</dependency>
```

#### Gradle
```
dependencies {
    compile "de.sandec:JMemoryBuddy:0.1.2"
}
```

### Quickstart:
```
@Test
public void simpleTest() {
    A referenced = new A();
    JMemoryBuddy.doMemTest(checker -> {
        A notReferenced = new A();
        checker.assertCollectable(notReferenced); // not referenced should be collectable
    });
}
```


## FAQ - Why is no one else writing unit-tests for memory leaks?

There are various reasons for this. By spec the command `System.gc()` doesn't have to do anything, 
This makes it hard and undeterministic to test for collectability. It's doable in practice but it's hard. There are a lot of pitfalls.
Typical problems **(which are solved by this library)**
 * People who want to test for memory-leaks usually don't put a lot of effort in writing a good implementation. By having a separate library, we can make sure it works in every setup.
 * The GC is undeterministic. This makes the enduser paranoid that their tests are unstable. For this reason, the developer might increase the timeout further and further. 
  In the end, the tests either take too long, or the developer loses his trust and stops testing.
 This library warns the user when tests aren't finished fast. The end user knows how close to "unstable" the test is.
 * Badly written tests for leaks are often very slow. These tests take about 0.1 seconds on average. This way we can write a lot of tests without slowing down the other unit tests too much.
 * Optimizations in the JVM can sometimes change the GC-behaviour. This makes writing a test-framework hard.
 * The GC usually doesn't do anything when there is not enough Garbage. 
   This is something the average developer usually doesn't now but is crucial for writing these tests.
   The workaround is to artificially generate some garbage when using JMemoryBuddy. This way the developer doesn't have to know the confusing details about the JVM.
 * Writing tests for memory leaks usually end in ugly code which is hard to verify to be correct. With this library, the unit-test only contains simple code and the tricky implementation is hidden inside JMemoryBuddy.

There are various reason


## Projects using JMemoryBuddy:
* [https://jpro.one/](jpro.one - aka javafx for the web)
* [https://github.com/Sandec/JMemoryBuddy/pulls](Your project?)

##### internal developer
publish local:
```
./gradlew publishToMavenLocal
```

publish to sonatype:
```
./gradlew uploadArchives
```