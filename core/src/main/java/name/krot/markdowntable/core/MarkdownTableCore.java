// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 krotname

package name.krot.markdowntable.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dependency-free operations for GitHub-flavored Markdown pipe tables.
 *
 * <p>The editor integration supplies a document as a list of lines and a zero-based caret
 * position. Returned results are immutable snapshots and never expose the engine's mutable
 * working state.</p>
 */
public final class MarkdownTableCore {
	/**
	 * Editing operation to apply to the table containing the requested row.
	 */
	public enum Action {
		/** Align columns and separator markers. */
		ALIGN,
		/** Move the target to the next cell, adding a row when needed. */
		NEXT_CELL,
		/** Move the target to the previous editable cell. */
		PREVIOUS_CELL,
		/** Insert an empty row below the current row. */
		INSERT_ROW_BELOW,
		/** Delete the current data row when the table can remain valid. */
		DELETE_ROW,
		/** Insert an empty column to the right. */
		INSERT_COLUMN_RIGHT,
		/** Delete the current column. */
		DELETE_COLUMN,
		/** Reduce the current column by one display column. */
		NARROW_COLUMN,
		/** Increase the current column by one display column. */
		WIDEN_COLUMN,
		/** Move the current data row up. */
		MOVE_ROW_UP,
		/** Move the current data row down. */
		MOVE_ROW_DOWN,
		/** Move the current column left. */
		MOVE_COLUMN_LEFT,
		/** Move the current column right. */
		MOVE_COLUMN_RIGHT,
		/** Sort data rows by the current column in ascending order. */
		SORT_ASCENDING,
		/** Sort data rows by the current column in descending order. */
		SORT_DESCENDING,
		/** Wrap long cell contents while preserving Markdown constructs. */
		WRAP_LONG_CELLS
	}

	/**
	 * Immutable result of a table edit or conversion.
	 *
	 * <p>{@link #lines} holds only the affected table, not the whole document, and
	 * {@link #targetRow} indexes into those lines. Callers that pass a whole document to
	 * {@link #apply} therefore add {@link TableRange#firstRow} to map the target back onto the
	 * document.</p>
	 */
	public static final class EditResult {
		/**
		 * Whether the returned lines differ from the table the operation started from.
		 *
		 * <p>Successful no-ops such as aligning an already aligned table, or moving the first data
		 * row further up, report {@code false} while {@link #ok} stays {@code true}.</p>
		 */
		public final boolean changed;
		/** Whether the operation completed successfully. */
		public final boolean ok;
		/** Human-readable failure or status detail; empty on normal success. */
		public final String message;
		/** Immutable formatted or converted lines; empty when {@link #ok} is {@code false}. */
		public final List<String> lines;
		/** Zero-based target row within {@link #lines} after the operation. */
		public final int targetRow;
		/** Zero-based target column after the operation. */
		public final int targetColumn;
		/** Character offset within the target column after formatting. */
		public final int targetColumnOffset;

		private EditResult(MarkdownTableEngine.EditResult result) {
			changed = result.changed;
			ok = result.ok;
			message = result.message;
			lines = List.copyOf(result.lines);
			targetRow = result.targetRow;
			targetColumn = result.targetColumn;
			targetColumnOffset = result.targetColumnOffset;
		}

		/** @return whether the returned text changed */
		public boolean changed() {
			return changed;
		}

		/** @return whether the operation succeeded */
		public boolean ok() {
			return ok;
		}

		/** @return operation status detail */
		public String message() {
			return message;
		}

		/** @return immutable result lines */
		public List<String> lines() {
			return lines;
		}

		/** @return zero-based target row */
		public int targetRow() {
			return targetRow;
		}

		/** @return zero-based target column */
		public int targetColumn() {
			return targetColumn;
		}

		/** @return character offset within the target column */
		public int targetColumnOffset() {
			return targetColumnOffset;
		}
	}

	/**
	 * Immutable inclusive row range for one detected Markdown table.
	 */
	public static final class TableRange {
		/** Whether a table was found. */
		public final boolean found;
		/** Inclusive zero-based first row. */
		public final int firstRow;
		/** Inclusive zero-based last row. */
		public final int lastRow;

		private TableRange(MarkdownTableEngine.TableRange range) {
			found = range.found;
			firstRow = range.firstRow;
			lastRow = range.lastRow;
		}

		/** @return whether a table was found */
		public boolean found() {
			return found;
		}

		/** @return inclusive zero-based first row */
		public int firstRow() {
			return firstRow;
		}

		/** @return inclusive zero-based last row */
		public int lastRow() {
			return lastRow;
		}
	}

	private MarkdownTableCore() {
	}

	/**
	 * Returns whether a line contains at least one unescaped table delimiter.
	 *
	 * @param line source line
	 * @return whether the line can participate in a table
	 */
	public static boolean isPotentialTableLine(String line) {
		return MarkdownTableEngine.isPotentialTableLine(requireText(line, "line"));
	}

	/**
	 * Converts a character offset in a table row to a zero-based cell index.
	 *
	 * @param line table row text
	 * @param charColumn zero-based character offset
	 * @return zero-based cell index
	 */
	public static int columnFromCursor(String line, int charColumn) {
		return MarkdownTableEngine.columnFromCursor(requireText(line, "line"), charColumn);
	}

	/**
	 * Finds the table containing {@code row}; {@link TableRange#found} is false when none exists.
	 *
	 * @param lines document lines
	 * @param row zero-based document row
	 * @return containing range or a not-found range
	 */
	public static TableRange findTableRange(List<String> lines, int row) {
		return new TableRange(MarkdownTableEngine.findTableRange(requireLines(lines), row));
	}

	/**
	 * Finds every Markdown table in document order.
	 *
	 * <p>The returned ranges are strictly ordered and never overlap, so a caller may rewrite each
	 * one independently. Scanning resumes past the end of a table even when its last row still
	 * carries pipes, so a trailing row is never reused as the header of the next table.</p>
	 *
	 * @param lines document lines
	 * @return immutable ranges in document order
	 */
	public static List<TableRange> findTableRanges(List<String> lines) {
		List<MarkdownTableEngine.TableRange> ranges = MarkdownTableEngine.findTableRanges(requireLines(lines));
		List<TableRange> results = new ArrayList<>(ranges.size());
		for (MarkdownTableEngine.TableRange range : ranges) {
			results.add(new TableRange(range));
		}
		return List.copyOf(results);
	}

	/**
	 * Returns whether a line can be a Markdown table separator row.
	 *
	 * @param line source line
	 * @return whether the line has valid separator syntax
	 */
	public static boolean isPotentialSeparatorLine(String line) {
		return MarkdownTableEngine.isPotentialSeparatorLine(requireText(line, "line"));
	}

	/**
	 * Applies an editing action to the table containing {@code row}.
	 *
	 * <p>Out-of-range coordinates are clamped into the table rather than rejected. The result
	 * carries only the rewritten table; see {@link EditResult} for how its coordinates relate to
	 * the document.</p>
	 *
	 * @param lines document lines
	 * @param row zero-based document row
	 * @param column zero-based cell index
	 * @param action operation to apply
	 * @return immutable operation result; {@link EditResult#ok} is {@code false} when {@code row}
	 *         is not inside a Markdown table
	 */
	public static EditResult apply(List<String> lines, int row, int column, Action action) {
		Objects.requireNonNull(action, "action");
		return new EditResult(MarkdownTableEngine.apply(
			requireLines(lines),
			row,
			column,
			MarkdownTableEngine.Action.valueOf(action.name())
		));
	}

	/**
	 * Formats a table and wraps cells until it fits the requested display width when possible.
	 *
	 * @param lines document lines
	 * @param row zero-based document row
	 * @param column zero-based cell index
	 * @param maxTableWidth maximum display width
	 * @return immutable operation result
	 */
	public static EditResult applyWrappedToWidth(
		List<String> lines,
		int row,
		int column,
		int maxTableWidth
	) {
		return new EditResult(MarkdownTableEngine.applyWrappedToWidth(
			requireLines(lines),
			row,
			column,
			maxTableWidth
		));
	}

	/**
	 * Converts CSV or TSV text into a Markdown table.
	 *
	 * @param text CSV or TSV text
	 * @return immutable conversion result
	 */
	public static EditResult fromDelimited(String text) {
		return new EditResult(MarkdownTableEngine.fromDelimited(requireText(text, "text")));
	}

	/**
	 * Creates a Markdown table with one header row and {@code dataRows} empty data rows.
	 *
	 * @param columns number of columns
	 * @param dataRows number of data rows
	 * @return immutable creation result
	 */
	public static EditResult newTable(int columns, int dataRows) {
		return new EditResult(MarkdownTableEngine.newTable(columns, dataRows));
	}

	private static String requireText(String text, String name) {
		return Objects.requireNonNull(text, name);
	}

	private static List<String> requireLines(List<String> lines) {
		Objects.requireNonNull(lines, "lines");
		for (String line : lines) {
			Objects.requireNonNull(line, "lines must not contain null elements");
		}
		return lines;
	}
}
