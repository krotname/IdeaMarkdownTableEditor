# JetBrains Marketplace submission

Status: release metadata for version `@PLUGIN_VERSION@`; generated from Gradle's resolved `pluginVersion` value.

## Artifact

- Version: `@PLUGIN_VERSION@`
- ZIP: `build/distributions/MarkdownTableEditorIdea-@PLUGIN_VERSION@.zip`
- Marketplace update ID: fill after upload
- Marketplace review status: fill after upload
- GitHub release: fill after release creation
- Download URL: fill after release creation

## Plugin metadata

- Plugin name: `Markdown Table Editor`
- Plugin XML ID: `name.krot.markdown-table-editor-idea`
- Vendor: `krotname`
- Website: `https://github.com/krotname/IdeaMarkdownTableEditor`
- Source code: `https://github.com/krotname/IdeaMarkdownTableEditor`
- License: `MIT`
- License URL: `https://github.com/krotname/IdeaMarkdownTableEditor/blob/main/LICENSE`
- Suggested tags: `Markdown`, `Tables`, `Documentation`, `Editor`
- Suggested category: `Code tools`
- Compatibility baseline: JetBrains IDEs based on IntelliJ Platform `223` / `2022.3` and newer
- Java bytecode target: `17`
- Required platform module: `com.intellij.modules.platform`
- Marketplace compatibility scope: IntelliJ IDEA Community/Ultimate `2022.3+`, WebStorm `2022.3+`, PyCharm Community/Professional `2022.3+`, PhpStorm `2022.3+`, GoLand `2022.3+`, CLion `2022.3+`, Rider `2022.3+`, RubyMine `2022.3+`, DataGrip `2022.3+`, DataSpell `2022.3+`, Android Studio `Giraffe | 2022.3.1 Beta 1+`, RustRover `2024.1+`, Gateway `2022.3+`, JetBrains Client `1.0+`, Code With Me Guest `1.0+`, and other IntelliJ Platform IDEs
- Local verifier baseline: IntelliJ IDEA Community `2022.3.3`, WebStorm `2022.3.3`, CLion `2022.3.3`
- Verifier note: PyCharm Community `2022.3.3` is published as a Windows installer, but the Gradle verifier resolver searches for a `win.zip` artifact.

## Short description

Edit Markdown pipe tables directly in JetBrains IDEs: align with Tab, fit table width, narrow or widen columns, sort rows, convert CSV/TSV, insert tables by size, and manage rows or columns without leaving the editor.

## Marketplace update text

Use this for the JetBrains Marketplace version notes for `@PLUGIN_VERSION@` if the upload flow asks for release text:

```html
<ul>
  <li>Added Narrow Column and Widen Column commands with Ctrl+Alt+Shift+, and Ctrl+Alt+Shift+. shortcuts.</li>
  <li>Kept Java/C++ wrapping behavior in sync for manual column resizing, long-cell continuation rows, protected Markdown links, code spans, and narrow-width hard wrapping.</li>
  <li>Improved table fitting so continuation rows are rejoined before a table is fitted to a wider editor window.</li>
  <li>Added bottom-left IDE status bar buttons for Light Auto Align and Power Auto Fit modes.</li>
  <li>Preserved alignment, sorting, CSV/TSV conversion, escaped pipes, UTF-8/CJK width handling, caret placement, and row/column edits.</li>
</ul>
```

## Manual submission

1. Log in to JetBrains Marketplace.
2. Open `Upload plugin` / `Add new plugin`.
3. Select or create the Vendor profile.
4. Accept the JetBrains Marketplace Developer Agreement if prompted.
5. Upload `build/distributions/MarkdownTableEditorIdea-@PLUGIN_VERSION@.zip`.
6. Choose MIT/open-source license and provide the license URL above.
7. Provide the source code URL above.
8. Submit for review.

## API upload note

For a new plugin, JetBrains documents first upload through Marketplace UI. API upload requires a permanent Marketplace token and plugin/vendor context.

If token-based upload is available for your account, set:

```text
JETBRAINS_MARKETPLACE_TOKEN=perm:...
```

Do not commit or paste this token into the repository.
