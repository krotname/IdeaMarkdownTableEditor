# JetBrains Marketplace submission

Status: package is prepared, but initial Marketplace submission requires the owner's JetBrains Account login, Vendor profile, Developer Agreement acceptance, and EULA/license confirmation.

## Artifact

- Version: `0.6.0`
- ZIP: `build/distributions/MarkdownTableEditorIdea-0.6.0.zip`
- GitHub release: `https://github.com/krotname/IdeaMarkdownTableEditor/releases/tag/v0.6.0`
- Download URL: `https://github.com/krotname/IdeaMarkdownTableEditor/releases/download/v0.6.0/MarkdownTableEditorIdea-0.6.0.zip`

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

## Short description

Edit Markdown pipe tables directly in JetBrains IDEs: align with Tab, sort rows, convert CSV/TSV, insert tables by size, and manage rows or columns without leaving the editor.

## Manual submission

1. Log in to JetBrains Marketplace.
2. Open `Upload plugin` / `Add new plugin`.
3. Select or create the Vendor profile.
4. Accept the JetBrains Marketplace Developer Agreement if prompted.
5. Upload `build/distributions/MarkdownTableEditorIdea-0.6.0.zip`.
6. Choose MIT/open-source license and provide the license URL above.
7. Provide the source code URL above.
8. Submit for review.

## API upload note

For a new plugin, JetBrains documents first upload through Marketplace UI. API upload requires a permanent Marketplace token and plugin/vendor context.

If token-based upload is available for your account, set:

```powershell
$env:JETBRAINS_MARKETPLACE_TOKEN = "perm:..."
```

Do not commit or paste this token into the repository.
