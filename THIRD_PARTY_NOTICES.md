# Third-Party Notices

## Distributed Plugin

The built Markdown Table Editor plugin ZIP does not bundle third-party runtime libraries.
It contains the plugin classes, `META-INF/plugin.xml`, `META-INF/pluginIcon.svg`, and the project MIT license at `META-INF/LICENSE`.

The plugin is compiled against the IntelliJ Platform APIs. At runtime, those APIs are provided by the user's JetBrains IDE and are not redistributed inside this plugin package.

## Build Tooling

These tools are used to build the project but are not bundled into the plugin ZIP:

| Component | Use | License |
| --- | --- | --- |
| Gradle Wrapper | Local build bootstrap files under `gradle/wrapper`, `gradlew`, and `gradlew.bat` | Apache License 2.0 |
| Gradle IntelliJ Platform Plugin `2.16.0` | Gradle build plugin for compiling and packaging the JetBrains IDE plugin | Apache-2.0 |

The Gradle wrapper scripts include Apache License 2.0 headers, and `gradle-wrapper.jar` contains its license in `META-INF/LICENSE`.
