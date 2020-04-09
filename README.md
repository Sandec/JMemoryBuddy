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
  <version>0.1.4</version>
  <scope>test</scope>
</dependency>
```

#### Gradle
```
dependencies {
    compile "de.sandec:JMemoryBuddy:0.1.4"
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

    
## FAQ - Why is no one else writing unit-tests for memory leaks?

There are various reasons for this. By spec the command `System.gc()` doesn't have to do anything, 
This makes it hard and undeterministic to test for collectability. Nevertheless, **JMemoryBuddy makes testing for memory leaks reliable!**


## Projects using JMemoryBuddy:
* [jpro.one](https://jpro.one/) - aka javafx for the web
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