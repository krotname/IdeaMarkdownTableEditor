# Release notes

The release workflow appends this file to the generated `Builds / Downloads` section of the GitHub
release for the current tag. Rewrite it in the same commit that bumps `VERSION`, so a release can
never ship the previous version's notes.

## What's Changed

- No change in behavior. The JetBrains, Notepad++ and Visual Studio Code editions of Markdown Table
  Editor now expose the same table engine surface: the Notepad++ core gained `findTableRanges` and
  `isPotentialSeparatorLine`, which the Java and TypeScript cores already had.
- The shared golden fixture grew a `separatorLines` and a `ranges` section (`schemaVersion` 2), and
  all three repositories run them, so table-range scanning and separator detection are checked
  against one file.
- The bundled table engine stays `name.krot:markdown-table-core:0.3.1`.

## Validation

- `gradlew check` passed: unit tests, release metadata checks, and the Jacoco 90% line coverage gate
  on the core.
- `gradlew verifyPlugin` (baseline, IC-223.8836.41): Compatible.
- Cross-core parity harness: 51629 generated scenarios run through the Java, C++ and TypeScript
  cores produced byte-identical reports (234504 lines, same SHA-256). Two mutation controls confirm
  the harness detects divergence.
