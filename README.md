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

##### internal developer
publish local:
```
./gradlew publishToMavenLocal
```

publish to bintray:
```
./gradlew bintrayUpload
```