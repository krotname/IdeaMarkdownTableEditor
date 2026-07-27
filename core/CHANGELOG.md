# Markdown Table Core changelog

All notable changes to `name.krot:markdown-table-core` are recorded here.
The module follows [semantic versioning](https://semver.org/).

## 0.2.0

Source and binary compatible with `0.1.0`: no public type, method, field, or enum constant was
removed, added, or changed. Only the observed behaviour of existing methods changed, as listed
below.

### Fixed

- `findTableRanges` could return overlapping ranges when a table was directly followed by a line
  that was itself a valid separator. The trailing row of one table was reused as the header of the
  next one, so a caller that rewrote every range independently corrupted the document. Scanning
  now resumes past the end of each table.
- The formatter could emit a table it was unable to parse back. When a table carried no outer
  pipes and an edge cell was empty - for example right after inserting a column into
  `a | b` - the padded row ended with a pipe that re-parsing consumed as an outer pipe, dropping a
  column and breaking header/separator agreement. Rows now keep the outer pipe whenever the first
  or last column contains an empty cell.
- Deleting the second column of a table without outer pipes left rows with no pipe at all, turning
  the table into plain text. A single-column table without a leading pipe now always renders a
  trailing one.
- A header made only of dashes, such as `| --- | --- |` above its separator, was mistaken for the
  separator row, and every edit on that table was rejected with `No Markdown table found`. The
  separator search now starts below the header.
- `EditResult.changed` was always `true`. It now reports whether the returned lines actually differ
  from the table the operation started from, so successful no-ops such as aligning an already
  aligned table report `false` while `ok` stays `true`.

### Changed

- `findTableRange` stops scanning once the document is past the requested row instead of always
  mapping the whole document, which speeds up edits in long files.

### Documentation

- The Javadoc of `EditResult` now states that the result carries only the affected table and that
  `targetRow` indexes into `lines`, not into the document.
- `findTableRanges` documents the non-overlap guarantee, and `apply` documents that out-of-range
  coordinates are clamped rather than rejected.

## 0.1.0

- First public release: parsing, formatting, conversion, and editing for GitHub-flavored Markdown
  pipe tables, with no runtime dependencies on Java 17.
