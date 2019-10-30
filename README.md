#JMemoryBuddy


Gradle
```
repositories {
    maven {
        url "http://sandec.bintray.com/repo"
    }
}
dependencies {
    compile "de.sandec:jmemorybuddy:0.0.1"
}
```

### Quickstart:
```
@Test
public void simpleTest() {
    A referenced = new A();
    MemoryLeakUtils.doMemTest(checker -> {
        A notReferenced = new A();
        checker.accept(notReferenced); // not referenced should be collectable
    });
}
```

##### internal developer
publish local:
```
./gradlew publishToMavenLocal
```

publish to bintray:
```
./gradlew bintrayUpload
```