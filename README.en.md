# Markdown Table Editor for JetBrains IDEs

[![Release](https://github.com/krotname/IdeaMarkdownTableEditor/actions/workflows/release.yml/badge.svg)](https://github.com/krotname/IdeaMarkdownTableEditor/actions/workflows/release.yml)
[![codecov](https://codecov.io/gh/krotname/IdeaMarkdownTableEditor/branch/main/graph/badge.svg)](https://codecov.io/gh/krotname/IdeaMarkdownTableEditor)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/32159-markdown-table-editor?label=JetBrains%20Marketplace)](https://plugins.jetbrains.com/plugin/32159-markdown-table-editor)
[![License](https://img.shields.io/github/license/krotname/IdeaMarkdownTableEditor)](LICENSE)

Markdown Table Editor turns JetBrains IDEs on the IntelliJ Platform into convenient Markdown table editors.
Paste a messy table from someone else or from an AI tool, press `Tab`, and the plugin aligns the columns, preserves Markdown formatting,
and helps you quickly rearrange rows, columns, and data directly in the IDE.

## Related Projects

- Notepad++ version: [NppMarkdownTableEditor](https://github.com/krotname/NppMarkdownTableEditor)

## Demo

![Markdown table alignment example in a JetBrains IDE](docs/demo.gif)

The GIF is built from real JetBrains IDE screenshots on Windows: a regular `.md` file is open, and the `Align Table` command is triggered with `Ctrl+Alt+A`.

## Why Use It

- You do not need to leave your JetBrains IDE for a separate Markdown editor just to fix tables.
- Large pipe tables stay readable as plain text.
- `Tab`, sorting, and row or column operations save manual spacing work.
- CSV/TSV text can be converted into a clean Markdown table quickly.
- Commands are available from the `Tools` menu, the editor context menu, and IDE action search.

## Features

- `Tab` inside a Markdown table aligns the table.
- Outside Markdown tables, `Tab` keeps working as the normal IDE indent action.
- Align the table around the caret.
- Move to the next or previous cell.
- Insert, delete, and move rows.
- Insert, delete, and move columns.
- Sort rows by the current column in ascending or descending order.
- Convert selected CSV/TSV text or the current CSV/TSV block into a Markdown table.
- Insert a new table with a selected number of columns and rows.
- Preserve Markdown alignment markers: `---`, `:---`, `---:`, `:---:`.
- Correctly handle escaped pipes: `\|`.

## Installation

1. Download the ZIP archive from the latest release: https://github.com/krotname/IdeaMarkdownTableEditor/releases/latest
2. Open a compatible JetBrains IDE.
3. Go to `Settings | Plugins`.
4. Click the gear icon and choose `Install Plugin from Disk...`.
5. Select the downloaded ZIP file.

The plugin is packaged as a dynamic plugin and is designed to install without restarting compatible JetBrains IDEs. If the IDE asks for a restart, the platform has detected a loading or unloading limitation in the current session.

## Publication

- JetBrains Marketplace versions page: https://plugins.jetbrains.com/plugin/32159-markdown-table-editor/edit/versions

## Commands

Commands are available from `Tools > Markdown Table Editor` and from the editor context menu.

| Command                                        | What It Does                                                         |
| ---------------------------------------------- | -------------------------------------------------------------------- |
| `Tab: Align Markdown Table`                    | Aligns the table at the caret; outside tables it works as normal Tab |
| `Align Table`                                  | Aligns the current Markdown table                                    |
| `Next Cell` / `Previous Cell`                  | Moves the caret between cells                                        |
| `Insert Row Below` / `Delete Row`              | Adds or deletes a row                                                |
| `Insert Column Right` / `Delete Column`        | Adds or deletes a column                                             |
| `Move Row Up` / `Move Row Down`                | Moves the current row                                                |
| `Move Column Left` / `Move Column Right`       | Moves the current column                                             |
| `Sort Rows Ascending` / `Sort Rows Descending` | Sorts rows by the current column                                     |
| `Convert CSV/TSV to Table`                     | Converts selected CSV/TSV or the current block to a Markdown table   |
| `Insert New Table`                             | Inserts a new table with the requested size                          |

For example, select `Name,Score` and the next line `Anna,10`, or place the caret inside such a block.
Run `Tools > Markdown Table Editor > Convert CSV/TSV to Table`.
You will get a Markdown table with `Name` and `Score` columns.

Default keyboard shortcuts:

| Command                      | Shortcut     |
| ---------------------------- | ------------ |
| `Tab: Align Markdown Table`  | `Tab`        |
| `Align Table`                | `Ctrl+Alt+A` |

You can assign shortcuts for the other commands in `Settings | Keymap`.

## Build and Tests

You need JDK 21. The IntelliJ Platform SDK used for compilation is downloaded by the Gradle IntelliJ Platform plugin.

```powershell
.\gradlew.bat check buildPlugin
```

The built ZIP appears in `build/distributions`.
If a JetBrains IDE is installed locally and you do not want to wait for the platform download, pass its path:

```powershell
.\gradlew.bat check buildPlugin -PplatformLocalPath="C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3"
```

For Marketplace compatibility verification:

```powershell
.\gradlew.bat verifyPlugin
```

In GitHub Actions, the verifier also uses recommended IDEs. Locally, enable that explicitly with:

```powershell
.\gradlew.bat verifyPlugin -PverifyRecommendedIdes=true
```

Full release build:

```powershell
.\gradlew.bat clean check verifyPlugin buildPlugin
```

For a faster local build without Plugin Verifier:

```powershell
.\gradlew.bat clean check buildPlugin
```

For local installation, use the ZIP from `build/distributions` through `Settings | Plugins | Install Plugin from Disk...`.

## License

The plugin code is distributed under the MIT License. The full license text is in [LICENSE](LICENSE).
Third-party build tooling is listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
