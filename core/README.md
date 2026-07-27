# Markdown Table Core

Dependency-free Java 17 library for parsing, formatting, converting, and editing
GitHub-flavored Markdown pipe tables.

## Coordinates

```kotlin
dependencies {
    implementation("name.krot:markdown-table-core:0.1.0")
}
```

The artifact is prepared for Maven Central but is not published yet.

## Usage

```java
import name.krot.markdowntable.core.MarkdownTableCore;

var result = MarkdownTableCore.fromDelimited("""
    Name,Score
    Anna,10
    Bob,2
    """);

if (result.ok()) {
    result.lines().forEach(System.out::println);
}
```

## Verification

```powershell
.\gradlew.bat :core:clean :core:check :core:corePerformance :core:verifyCentralPublication
```

`verifyCentralPublication` builds and validates the main, sources, Javadoc, and POM
artifacts without uploading them.

## Maven Central release

Prerequisites:

- verified `name.krot` namespace in Central Portal;
- Central Portal user token;
- armored in-memory GPG key and its password.

Keep credentials outside the repository:

```text
ORG_GRADLE_PROJECT_mavenCentralUsername=...
ORG_GRADLE_PROJECT_mavenCentralPassword=...
ORG_GRADLE_PROJECT_signingInMemoryKey=...
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=...
```

Upload for manual approval:

```powershell
.\gradlew.bat :core:publishToMavenCentral
```

Upload and release automatically:

```powershell
.\gradlew.bat :core:publishAndReleaseToMavenCentral
```

Maven Central releases are immutable. Run the complete verification command before
uploading a new version.
