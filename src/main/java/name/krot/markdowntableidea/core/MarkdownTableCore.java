// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MarkdownTableCore {
	public enum Action {
		ALIGN,
		NEXT_CELL,
		PREVIOUS_CELL,
		INSERT_ROW_BELOW,
		DELETE_ROW,
		INSERT_COLUMN_RIGHT,
		DELETE_COLUMN,
		NARROW_COLUMN,
		WIDEN_COLUMN,
		MOVE_ROW_UP,
		MOVE_ROW_DOWN,
		MOVE_COLUMN_LEFT,
		MOVE_COLUMN_RIGHT,
		SORT_ASCENDING,
		SORT_DESCENDING,
		WRAP_LONG_CELLS
	}

	public static final class EditResult {
		public boolean changed;
		public boolean ok;
		public String message = "";
		public List<String> lines = Collections.emptyList();
		public int targetRow;
		public int targetColumn;
		public int targetColumnOffset;
	}

	public static final class TableRange {
		public boolean found;
		public int firstRow;
		public int lastRow;
	}

	private enum Align {
		NONE,
		LEFT,
		CENTER,
		RIGHT
	}

	private static final class Row {
		final List<String> cells = new ArrayList<>();
		boolean separator;
		int id;
	}

	private static final class Table {
		final List<Row> rows = new ArrayList<>();
		final List<Align> alignments = new ArrayList<>();
		int columns;
		int separatorRow = -1;
		boolean leadingPipe = true;
		boolean trailingPipe = true;
	}

	private static final class FormatResult {
		final List<String> lines = new ArrayList<>();
		int targetRow;
		int targetColumn;
		int targetColumnOffset;
	}

	private static final class SortKey {
		final boolean numeric;
		final double number;
		final String foldedText;
		final String text;

		SortKey(boolean numeric, double number, String foldedText, String text) {
			this.numeric = numeric;
			this.number = number;
			this.foldedText = foldedText;
			this.text = text;
		}
	}

	private static final class SortEntry {
		final Row row;
		final SortKey key;

		SortEntry(Row row, SortKey key) {
			this.row = row;
			this.key = key;
		}
	}

	private static final int HARD_WRAP_CELL_WIDTH = 26;
	private static final int MINIMUM_AUTO_WRAP_CELL_WIDTH = 1;

	private MarkdownTableCore() {
	}

	public static boolean isPotentialTableLine(String line) {
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == '|' && !isEscaped(line, i)) {
				return true;
			}
		}
		return false;
	}

	public static int columnFromCursor(String line, int charColumn) {
		int column = 0;
		boolean skippedLeadingPipe = false;
		boolean hasLeadingPipe = startsWithUnescapedPipe(line);
		int limit = Math.min(Math.max(charColumn, 0), line.length());

		for (int i = 0; i < limit; i++) {
			if (line.charAt(i) == '|' && !isEscaped(line, i)) {
				if (hasLeadingPipe && !skippedLeadingPipe) {
					skippedLeadingPipe = true;
				} else {
					column++;
				}
			}
		}
		return column;
	}

	public static TableRange findTableRange(List<String> lines, int row) {
		TableRange result = new TableRange();
		if (lines.isEmpty() || row < 0 || row >= lines.size()) {
			return result;
		}

		for (TableRange range : findTableRanges(lines)) {
			if (row >= range.firstRow && row <= range.lastRow) {
				return range;
			}
		}

		return result;
	}

	public static List<TableRange> findTableRanges(List<String> lines) {
		List<TableRange> ranges = new ArrayList<>();
		for (int separatorRow = 1; separatorRow < lines.size(); separatorRow++) {
			int firstRow = separatorRow - 1;
			if (!isPotentialTableLine(lines.get(firstRow)) ||
				!isSeparatorForHeader(lines.get(firstRow), lines.get(separatorRow))) {
				continue;
			}

			TableRange range = new TableRange();
			range.found = true;
			range.firstRow = firstRow;
			range.lastRow = tableRangeEnd(lines, firstRow, separatorRow);
			ranges.add(range);
			separatorRow = range.lastRow;
		}
		return ranges;
	}

	private static boolean isSeparatorForHeader(String headerLine, String separatorLine) {
		if (!isPotentialSeparatorLine(separatorLine)) {
			return false;
		}
		return splitCells(headerLine).size() == splitCells(separatorLine).size();
	}

	public static boolean isPotentialSeparatorLine(String line) {
		if (!isPotentialTableLine(line)) {
			return false;
		}
		Row separator = new Row();
		separator.cells.addAll(splitCells(line));
		return isSeparatorRow(separator) || isShortSeparatorLine(line);
	}

	public static EditResult apply(List<String> lines, int row, int column, Action action) {
		EditResult result = new EditResult();
		if (lines.isEmpty()) {
			result.message = "No table found";
			return result;
		}

		if (row >= lines.size()) {
			row = lines.size() - 1;
		}
		if (row < 0) {
			row = 0;
		}

		TableRange tableRange = findTableRange(lines, row);
		if (!tableRange.found) {
			result.message = "No Markdown table found";
			return result;
		}

		lines = lines.subList(tableRange.firstRow, tableRange.lastRow + 1);
		row -= tableRange.firstRow;

		Table table = parseTable(lines);
		if (!isMarkdownTable(table)) {
			result.message = "No Markdown table found";
			return result;
		}

		if (row >= table.rows.size()) {
			row = table.rows.size() - 1;
		}
		if (row < 0) {
			row = 0;
		}
		if (column >= table.columns) {
			column = table.columns - 1;
		}
		if (column < 0) {
			column = 0;
		}

		int targetRow = row;
		int targetColumn = column;
		int currentRowId = table.rows.get(row).id;
		List<Integer> minimumWidths = null;

		switch (action) {
			case NEXT_CELL:
				if (targetColumn + 1 < table.columns) {
					targetColumn++;
				} else {
					targetColumn = 0;
					targetRow = nextEditableRow(table, targetRow);
					if (targetRow >= table.rows.size()) {
						insertEmptyRow(table, table.rows.size());
					}
				}
				break;
			case PREVIOUS_CELL:
				if (targetColumn > 0) {
					targetColumn--;
				} else {
					targetColumn = table.columns - 1;
					targetRow = previousEditableRow(table, targetRow);
				}
				break;
			case INSERT_ROW_BELOW:
				targetRow = nextEditableRow(table, row);
				insertEmptyRow(table, targetRow);
				break;
			case DELETE_ROW:
				if (canDeleteRow(table, row)) {
					table.rows.remove(row);
					if (table.separatorRow != -1 && row < table.separatorRow) {
						table.separatorRow--;
					}
					targetRow = closestEditableRow(table, row);
				}
				break;
			case INSERT_COLUMN_RIGHT:
				insertColumn(table, column + 1);
				targetColumn = column + 1;
				break;
			case DELETE_COLUMN:
				removeColumn(table, column);
				targetColumn = column >= table.columns ? table.columns - 1 : column;
				break;
			case NARROW_COLUMN:
				minimumWidths = new ArrayList<>();
				targetRow = resizeColumnWidth(table, row, column, false, minimumWidths);
				break;
			case WIDEN_COLUMN:
				minimumWidths = new ArrayList<>();
				targetRow = resizeColumnWidth(table, row, column, true, minimumWidths);
				break;
			case MOVE_ROW_UP:
				if (row > 0 && !table.rows.get(row).separator && !table.rows.get(row - 1).separator) {
					Collections.swap(table.rows, row, row - 1);
					targetRow = row - 1;
				}
				break;
			case MOVE_ROW_DOWN:
				if (row + 1 < table.rows.size() && !table.rows.get(row).separator && !table.rows.get(row + 1).separator) {
					Collections.swap(table.rows, row, row + 1);
					targetRow = row + 1;
				}
				break;
			case MOVE_COLUMN_LEFT:
				if (column > 0) {
					moveColumn(table, column, column - 1);
					targetColumn = column - 1;
				}
				break;
			case MOVE_COLUMN_RIGHT:
				if (column + 1 < table.columns) {
					moveColumn(table, column, column + 1);
					targetColumn = column + 1;
				}
				break;
			case SORT_ASCENDING:
				targetRow = sortRows(table, column, true, currentRowId, row);
				break;
			case SORT_DESCENDING:
				targetRow = sortRows(table, column, false, currentRowId, row);
				break;
			case WRAP_LONG_CELLS:
				targetRow = wrapLongCells(table, row);
				break;
			case ALIGN:
				break;
		}

		FormatResult formatted = formatTable(table, targetRow, targetColumn, minimumWidths);
		setResultFromFormat(result, formatted);
		result.ok = true;
		result.changed = true;
		return result;
	}

	public static EditResult applyWrappedToWidth(List<String> lines, int row, int column, int maxTableWidth) {
		EditResult result = new EditResult();
		if (lines.isEmpty()) {
			result.message = "No table found";
			return result;
		}

		if (row >= lines.size()) {
			row = lines.size() - 1;
		}
		if (row < 0) {
			row = 0;
		}

		TableRange tableRange = findTableRange(lines, row);
		if (!tableRange.found) {
			result.message = "No Markdown table found";
			return result;
		}

		lines = lines.subList(tableRange.firstRow, tableRange.lastRow + 1);
		row -= tableRange.firstRow;

		Table table = parseTable(lines);
		if (!isMarkdownTable(table)) {
			result.message = "No Markdown table found";
			return result;
		}

		if (row >= table.rows.size()) {
			row = table.rows.size() - 1;
		}
		if (row < 0) {
			row = 0;
		}
		if (column >= table.columns) {
			column = table.columns - 1;
		}
		if (column < 0) {
			column = 0;
		}

		row = unwrapContinuationRows(table, row);
		List<Integer> columnWidths = targetColumnWidthsForTableWidth(table, Math.max(maxTableWidth, 0));
		int targetRow = wrapCellsToColumnWidths(table, row, columnWidths);
		FormatResult formatted = formatTable(table, targetRow, column, columnWidths);
		setResultFromFormat(result, formatted);
		result.ok = true;
		result.changed = true;
		return result;
	}

	public static EditResult fromDelimited(String text) {
		EditResult result = new EditResult();
		List<List<String>> rows = parseDelimited(text);
		if (rows.isEmpty()) {
			result.message = "No CSV or TSV data found";
			return result;
		}

		Table table = tableFromCells(rows);
		FormatResult formatted = formatTable(table, 0, 0);
		setResultFromFormat(result, formatted);
		result.ok = true;
		result.changed = true;
		return result;
	}

	public static EditResult newTable(int columns, int dataRows) {
		EditResult result = new EditResult();
		if (columns < 1 || dataRows < 0) {
			result.message = "Invalid table size";
			return result;
		}

		List<List<String>> rows = new ArrayList<>();
		List<String> header = new ArrayList<>();
		for (int column = 1; column <= columns; column++) {
			header.add("Column " + column);
		}
		rows.add(header);
		for (int row = 0; row < dataRows; row++) {
			List<String> values = new ArrayList<>();
			for (int column = 0; column < columns; column++) {
				values.add("");
			}
			rows.add(values);
		}

		Table table = tableFromCells(rows);
		FormatResult formatted = formatTable(table, dataRows > 0 ? 2 : 0, 0);
		setResultFromFormat(result, formatted);
		result.ok = true;
		result.changed = true;
		return result;
	}

	private static boolean isSpace(char ch) {
		return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n';
	}

	private static String trim(String value) {
		int first = 0;
		while (first < value.length() && isSpace(value.charAt(first))) {
			first++;
		}

		int last = value.length();
		while (last > first && isSpace(value.charAt(last - 1))) {
			last--;
		}

		return value.substring(first, last);
	}

	private static String trimRange(String value, int first, int last) {
		while (first < last && isSpace(value.charAt(first))) {
			first++;
		}
		while (last > first && isSpace(value.charAt(last - 1))) {
			last--;
		}
		return value.substring(first, last);
	}

	private static boolean isEscaped(String line, int pos) {
		int slashCount = 0;
		while (pos > slashCount && line.charAt(pos - slashCount - 1) == '\\') {
			slashCount++;
		}
		return (slashCount % 2) == 1;
	}

	private static boolean endsWithUnescapedPipe(String line) {
		return !line.isEmpty() && line.charAt(line.length() - 1) == '|' && !isEscaped(line, line.length() - 1);
	}

	private static boolean endsWithUnescapedPipeTrimmed(String line) {
		int last = line.length();
		while (last > 0 && isSpace(line.charAt(last - 1))) {
			last--;
		}
		return last > 0 && line.charAt(last - 1) == '|' && !isEscaped(line, last - 1);
	}

	private static boolean startsWithUnescapedPipe(String line) {
		int pos = 0;
		while (pos < line.length() && isSpace(line.charAt(pos))) {
			pos++;
		}
		return pos < line.length() && line.charAt(pos) == '|' && !isEscaped(line, pos);
	}

	private static List<String> splitCells(String line) {
		int first = 0;
		while (first < line.length() && isSpace(line.charAt(first))) {
			first++;
		}

		int last = line.length();
		while (last > first && isSpace(line.charAt(last - 1))) {
			last--;
		}

		if (first < last && line.charAt(first) == '|') {
			first++;
		}
		if (last > first && line.charAt(last - 1) == '|' && !isEscaped(line, last - 1)) {
			last--;
		}

		List<String> cells = new ArrayList<>();
		int start = first;
		for (int i = first; i < last; i++) {
			if (line.charAt(i) == '|' && !isEscaped(line, i)) {
				cells.add(trimRange(line, start, i));
				start = i + 1;
			}
		}
		cells.add(trimRange(line, start, last));
		return cells;
	}

	private static boolean isSeparatorCell(String cell) {
		String value = trim(cell);
		if (value.isEmpty()) {
			return false;
		}

		boolean hasDash = false;
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (ch == '-') {
				hasDash = true;
				continue;
			}
			if (ch == ':' || isSpace(ch)) {
				continue;
			}
			return false;
		}
		return hasDash;
	}

	private static boolean isSeparatorRow(Row row) {
		if (row.cells.isEmpty()) {
			return false;
		}
		for (String cell : row.cells) {
			if (!isSeparatorCell(cell)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isShortSeparatorLine(String line) {
		String value = trim(line);
		if (value.isEmpty() || value.charAt(0) != '|') {
			return false;
		}

		boolean hasRule = false;
		for (int i = 1; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (ch == '-' || ch == '=') {
				hasRule = true;
			}
			if (ch != '-' && ch != '=' && ch != '|' && ch != ':' && !isSpace(ch)) {
				return false;
			}
		}
		return hasRule;
	}

	private static Align parseAlignment(String cell) {
		String value = trim(cell);
		boolean left = !value.isEmpty() && value.charAt(0) == ':';
		boolean right = !value.isEmpty() && value.charAt(value.length() - 1) == ':';

		if (left && right) {
			return Align.CENTER;
		}
		if (left) {
			return Align.LEFT;
		}
		if (right) {
			return Align.RIGHT;
		}
		return Align.NONE;
	}

	private static int tableRangeEnd(List<String> lines, int firstRow, int separatorRow) {
		boolean requireLeadingPipe = startsWithUnescapedPipe(lines.get(firstRow)) && startsWithUnescapedPipe(lines.get(separatorRow));
		int lastRow = separatorRow;
		for (int row = separatorRow + 1; row < lines.size(); row++) {
			String line = lines.get(row);
			if (!isPotentialTableLine(line)) {
				break;
			}
			if (requireLeadingPipe && !startsWithUnescapedPipe(line)) {
				break;
			}
			lastRow = row;
		}
		return lastRow;
	}

	private static Table parseTable(List<String> lines) {
		Table table = new Table();
		int leadingPipeRows = 0;
		int trailingPipeRows = 0;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			Row row = new Row();
			row.id = i;
			row.cells.addAll(splitCells(line));
			table.columns = Math.max(table.columns, row.cells.size());
			table.rows.add(row);
			if (startsWithUnescapedPipe(line)) {
				leadingPipeRows++;
			}
			if (endsWithUnescapedPipeTrimmed(line)) {
				trailingPipeRows++;
			}
		}

		if (!lines.isEmpty()) {
			table.leadingPipe = leadingPipeRows * 2 >= lines.size();
			table.trailingPipe = trailingPipeRows * 2 >= lines.size();
		}

		for (int i = 0; i < table.rows.size(); i++) {
			if (isSeparatorRow(table.rows.get(i)) || (i == 1 && isShortSeparatorLine(lines.get(i)))) {
				table.separatorRow = i;
				table.rows.get(i).separator = true;
				break;
			}
		}

		if (table.columns == 0) {
			table.columns = 1;
		}

		for (Row row : table.rows) {
			while (row.cells.size() < table.columns) {
				row.cells.add("");
			}
		}

		for (int i = 0; i < table.columns; i++) {
			table.alignments.add(Align.NONE);
		}
		if (table.separatorRow != -1) {
			Row separator = table.rows.get(table.separatorRow);
			for (int i = 0; i < table.columns; i++) {
				table.alignments.set(i, parseAlignment(separator.cells.get(i)));
			}
		}

		return table;
	}

	private static Table tableFromCells(List<List<String>> rows) {
		Table table = new Table();
		table.separatorRow = 1;
		table.columns = 1;
		for (List<String> row : rows) {
			table.columns = Math.max(table.columns, row.size());
		}

		Row header = new Row();
		header.id = table.rows.size();
		for (int column = 0; column < table.columns; column++) {
			header.cells.add(column < rows.get(0).size() ? sanitizeMarkdownCell(rows.get(0).get(column)) : "");
		}
		table.rows.add(header);

		Row separator = new Row();
		separator.separator = true;
		separator.id = table.rows.size();
		for (int column = 0; column < table.columns; column++) {
			separator.cells.add("---");
			table.alignments.add(Align.NONE);
		}
		table.rows.add(separator);

		for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
			Row row = new Row();
			row.id = table.rows.size();
			List<String> source = rows.get(rowIndex);
			for (int column = 0; column < table.columns; column++) {
				row.cells.add(column < source.size() ? sanitizeMarkdownCell(source.get(column)) : "");
			}
			table.rows.add(row);
		}

		return table;
	}

	private static String sanitizeMarkdownCell(String value) {
		StringBuilder result = new StringBuilder();
		String text = trim(value == null ? "" : value);
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == '|' && !isEscaped(text, i)) {
				result.append("\\|");
			} else if (ch == '\r' || ch == '\n') {
				result.append(' ');
			} else {
				result.append(ch);
			}
		}
		return result.toString();
	}

	private static List<List<String>> parseDelimited(String text) {
		String value = trim(text == null ? "" : text);
		if (value.isEmpty()) {
			return Collections.emptyList();
		}
		if (!hasDelimiterOutsideQuotes(value)) {
			return Collections.emptyList();
		}

		char delimiter = detectDelimiter(value);
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean inQuotes = false;
		boolean closedQuotedField = false;
		boolean rowHasDelimitedSyntax = false;

		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (inQuotes) {
				if (ch == '"') {
					if (i + 1 < value.length() && value.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else {
						inQuotes = false;
						closedQuotedField = true;
					}
				} else if (ch == '\r' || ch == '\n') {
					cell.append(' ');
					if (ch == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n') {
						i++;
					}
				} else {
					cell.append(ch);
				}
				continue;
			}

			if (closedQuotedField) {
				if (ch == delimiter) {
					row.add(cell.toString());
					cell.setLength(0);
					closedQuotedField = false;
					rowHasDelimitedSyntax = true;
				} else if (ch == '\r' || ch == '\n') {
					row.add(cell.toString());
					addDelimitedRow(rows, row, rowHasDelimitedSyntax);
					row = new ArrayList<>();
					cell.setLength(0);
					closedQuotedField = false;
					rowHasDelimitedSyntax = false;
					if (ch == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n') {
						i++;
					}
				} else if (isSpace(ch)) {
					cell.append(ch);
				} else {
					return Collections.emptyList();
				}
			} else if (ch == '"' && isBlank(cell)) {
				cell.setLength(0);
				inQuotes = true;
				rowHasDelimitedSyntax = true;
			} else if (ch == delimiter) {
				row.add(cell.toString());
				cell.setLength(0);
				rowHasDelimitedSyntax = true;
			} else if (ch == '\r' || ch == '\n') {
				row.add(cell.toString());
				addDelimitedRow(rows, row, rowHasDelimitedSyntax);
				row = new ArrayList<>();
				cell.setLength(0);
				rowHasDelimitedSyntax = false;
				if (ch == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n') {
					i++;
				}
			} else {
				cell.append(ch);
			}
		}

		if (inQuotes) {
			return Collections.emptyList();
		}

		row.add(cell.toString());
		addDelimitedRow(rows, row, rowHasDelimitedSyntax);
		return rows;
	}

	private static char detectDelimiter(String text) {
		int tabs = 0;
		boolean inQuotes = false;
		boolean cellBlank = true;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (inQuotes) {
				if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
					i++;
				} else if (ch == '"') {
					inQuotes = false;
				}
			} else if (ch == '"' && cellBlank) {
				inQuotes = true;
			} else if (ch == '\t') {
				tabs++;
				cellBlank = true;
			} else if (ch == ',') {
				cellBlank = true;
			} else if (ch == '\r' || ch == '\n') {
				cellBlank = true;
			} else if (!isSpace(ch)) {
				cellBlank = false;
			}
		}
		return tabs > 0 ? '\t' : ',';
	}

	private static boolean hasDelimiterOutsideQuotes(String text) {
		boolean inQuotes = false;
		boolean cellBlank = true;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (inQuotes) {
				if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
					i++;
				} else if (ch == '"') {
					inQuotes = false;
				}
			} else if (ch == '"' && cellBlank) {
				inQuotes = true;
			} else if (ch == '\t' || ch == ',') {
				return true;
			} else if (ch == '\r' || ch == '\n') {
				cellBlank = true;
			} else if (!isSpace(ch)) {
				cellBlank = false;
			}
		}
		return false;
	}

	private static boolean isBlank(StringBuilder value) {
		for (int i = 0; i < value.length(); i++) {
			if (!isSpace(value.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static void addDelimitedRow(List<List<String>> rows, List<String> row, boolean hasDelimitedSyntax) {
		boolean hasValue = false;
		for (String cell : row) {
			if (!trim(cell).isEmpty()) {
				hasValue = true;
				break;
			}
		}
		if (hasValue || hasDelimitedSyntax) {
			rows.add(row);
		}
	}

	private static boolean isMarkdownTable(Table table) {
		return table.separatorRow > 0;
	}

	private static boolean isWideCodePoint(int cp) {
		return cp == 0x2329 || cp == 0x232A ||
			(cp >= 0x1100 && cp <= 0x115F) ||
			(cp >= 0x2E80 && cp <= 0xA4CF && cp != 0x303F) ||
			(cp >= 0xAC00 && cp <= 0xD7A3) ||
			(cp >= 0xF900 && cp <= 0xFAFF) ||
			(cp >= 0xFE10 && cp <= 0xFE19) ||
			(cp >= 0xFE30 && cp <= 0xFE6F) ||
			(cp >= 0xFF00 && cp <= 0xFF60) ||
			(cp >= 0xFFE0 && cp <= 0xFFE6) ||
			(cp >= 0x20000 && cp <= 0x3FFFD);
	}

	private static boolean isEmojiPresentationCodePoint(int cp) {
		return (cp >= 0x1F000 && cp <= 0x1FAFF) ||
			(cp >= 0x231A && cp <= 0x231B) ||
			(cp >= 0x23E9 && cp <= 0x23EC) ||
			cp == 0x23F0 || cp == 0x23F3 ||
			(cp >= 0x25FD && cp <= 0x25FE) ||
			(cp >= 0x2614 && cp <= 0x2615) ||
			(cp >= 0x2648 && cp <= 0x2653) ||
			cp == 0x267F || cp == 0x2693 || cp == 0x26A1 ||
			(cp >= 0x26AA && cp <= 0x26AB) ||
			(cp >= 0x26BD && cp <= 0x26BE) ||
			(cp >= 0x26C4 && cp <= 0x26C5) ||
			cp == 0x26CE || cp == 0x26D4 || cp == 0x26EA ||
			(cp >= 0x26F2 && cp <= 0x26F3) ||
			cp == 0x26F5 || cp == 0x26FA || cp == 0x26FD ||
			cp == 0x2705 || (cp >= 0x270A && cp <= 0x270B) ||
			cp == 0x2728 || cp == 0x274C || cp == 0x274E ||
			(cp >= 0x2753 && cp <= 0x2755) ||
			cp == 0x2757 || (cp >= 0x2795 && cp <= 0x2797) ||
			cp == 0x27B0 || cp == 0x27BF ||
			(cp >= 0x2B1B && cp <= 0x2B1C) ||
			cp == 0x2B50 || cp == 0x2B55 ||
			cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299;
	}

	private static boolean isEmojiVariationBase(int cp) {
		return isEmojiPresentationCodePoint(cp) ||
			cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 ||
			cp == 0x2122 || cp == 0x2139 ||
			(cp >= 0x2194 && cp <= 0x2199) ||
			(cp >= 0x21A9 && cp <= 0x21AA) ||
			cp == 0x2328 || cp == 0x23CF ||
			(cp >= 0x23ED && cp <= 0x23EF) ||
			(cp >= 0x23F1 && cp <= 0x23F2) ||
			(cp >= 0x23F8 && cp <= 0x23FA) ||
			cp == 0x24C2 || (cp >= 0x25AA && cp <= 0x25AB) ||
			cp == 0x25B6 || cp == 0x25C0 ||
			(cp >= 0x25FB && cp <= 0x25FE) ||
			(cp >= 0x2600 && cp <= 0x27BF) ||
			(cp >= 0x2934 && cp <= 0x2935) ||
			(cp >= 0x2B05 && cp <= 0x2B07) ||
			(cp >= 0x2B1B && cp <= 0x2B1C) ||
			cp == 0x2B50 || cp == 0x2B55 ||
			cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299;
	}

	private static boolean isVariationSelector(int cp) {
		return (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0xE0100 && cp <= 0xE01EF);
	}

	private static boolean isEmojiModifier(int cp) {
		return cp >= 0x1F3FB && cp <= 0x1F3FF;
	}

	private static boolean isEmojiTag(int cp) {
		return cp >= 0xE0020 && cp <= 0xE007F;
	}

	private static boolean isRegionalIndicator(int cp) {
		return cp >= 0x1F1E6 && cp <= 0x1F1FF;
	}

	private static boolean isKeycapBase(int cp) {
		return cp == '#' || cp == '*' || (cp >= '0' && cp <= '9');
	}

	// Mirrors the static table in the Notepad++ plugin core (MarkdownTableCore.cpp) so both
	// cores measure identical widths regardless of the JDK's Unicode version.
	private static final int[] COMBINING_RANGES = {
		0x0300, 0x036F, 0x0483, 0x0489, 0x0591, 0x05BD, 0x05BF, 0x05BF,
		0x05C1, 0x05C2, 0x05C4, 0x05C5, 0x05C7, 0x05C7, 0x0610, 0x061A,
		0x064B, 0x065F, 0x0670, 0x0670, 0x06D6, 0x06DC, 0x06DF, 0x06E4,
		0x06E7, 0x06E8, 0x06EA, 0x06ED, 0x0711, 0x0711, 0x0730, 0x074A,
		0x07A6, 0x07B0, 0x07EB, 0x07F3, 0x0816, 0x0819, 0x081B, 0x0823,
		0x0825, 0x0827, 0x0829, 0x082D, 0x0859, 0x085B, 0x08D3, 0x08E1,
		0x08E3, 0x0903, 0x093A, 0x093C, 0x093E, 0x094F, 0x0951, 0x0957,
		0x0962, 0x0963, 0x0981, 0x0983, 0x09BC, 0x09BC, 0x09BE, 0x09C4,
		0x09C7, 0x09C8, 0x09CB, 0x09CD, 0x09D7, 0x09D7, 0x09E2, 0x09E3,
		0x0A01, 0x0A03, 0x0A3C, 0x0A3C, 0x0A3E, 0x0A42, 0x0A47, 0x0A48,
		0x0A4B, 0x0A4D, 0x0A51, 0x0A51, 0x0A70, 0x0A71, 0x0A75, 0x0A75,
		0x0A81, 0x0A83, 0x0ABC, 0x0ABC, 0x0ABE, 0x0AC5, 0x0AC7, 0x0AC9,
		0x0ACB, 0x0ACD, 0x0AE2, 0x0AE3, 0x0B01, 0x0B03, 0x0B3C, 0x0B3C,
		0x0B3E, 0x0B44, 0x0B47, 0x0B48, 0x0B4B, 0x0B4D, 0x0B56, 0x0B57,
		0x0B62, 0x0B63, 0x0B82, 0x0B82, 0x0BBE, 0x0BC2, 0x0BC6, 0x0BC8,
		0x0BCA, 0x0BCD, 0x0BD7, 0x0BD7, 0x0C00, 0x0C04, 0x0C3E, 0x0C44,
		0x0C46, 0x0C48, 0x0C4A, 0x0C4D, 0x0C55, 0x0C56, 0x0C62, 0x0C63,
		0x0C81, 0x0C83, 0x0CBC, 0x0CBC, 0x0CBE, 0x0CC4, 0x0CC6, 0x0CC8,
		0x0CCA, 0x0CCD, 0x0CD5, 0x0CD6, 0x0CE2, 0x0CE3, 0x0D00, 0x0D03,
		0x0D3B, 0x0D3C, 0x0D3E, 0x0D44, 0x0D46, 0x0D48, 0x0D4A, 0x0D4D,
		0x0D57, 0x0D57, 0x0D62, 0x0D63, 0x0D82, 0x0D83, 0x0DCA, 0x0DCA,
		0x0DCF, 0x0DD4, 0x0DD6, 0x0DD6, 0x0DD8, 0x0DDF, 0x0DF2, 0x0DF3,
		0x0E31, 0x0E31, 0x0E34, 0x0E3A, 0x0E47, 0x0E4E, 0x0EB1, 0x0EB1,
		0x0EB4, 0x0EBC, 0x0EC8, 0x0ECD, 0x0F18, 0x0F19, 0x0F35, 0x0F35,
		0x0F37, 0x0F37, 0x0F39, 0x0F39, 0x0F3E, 0x0F3F, 0x0F71, 0x0F84,
		0x0F86, 0x0F87, 0x0F8D, 0x0F97, 0x0F99, 0x0FBC, 0x0FC6, 0x0FC6,
		0x102B, 0x103E, 0x1056, 0x1059, 0x105E, 0x1060, 0x1062, 0x1064,
		0x1067, 0x106D, 0x1071, 0x1074, 0x1082, 0x108D, 0x108F, 0x108F,
		0x109A, 0x109D, 0x135D, 0x135F, 0x1712, 0x1715, 0x1732, 0x1734,
		0x1752, 0x1753, 0x1772, 0x1773, 0x17B4, 0x17D3, 0x17DD, 0x17DD,
		0x180B, 0x180D, 0x1885, 0x1886, 0x18A9, 0x18A9, 0x1920, 0x192B,
		0x1930, 0x193B, 0x1A17, 0x1A1B, 0x1A55, 0x1A5E, 0x1A60, 0x1A7C,
		0x1A7F, 0x1A7F, 0x1AB0, 0x1ACE, 0x1B00, 0x1B04, 0x1B34, 0x1B44,
		0x1B6B, 0x1B73, 0x1B80, 0x1B82, 0x1BA1, 0x1BAD, 0x1BE6, 0x1BF3,
		0x1C24, 0x1C37, 0x1CD0, 0x1CD2, 0x1CD4, 0x1CE8, 0x1CED, 0x1CED,
		0x1CF4, 0x1CF4, 0x1CF7, 0x1CF9, 0x1DC0, 0x1DFF, 0x20D0, 0x20FF,
		0x2CEF, 0x2CF1, 0x2D7F, 0x2D7F, 0x2DE0, 0x2DFF, 0x302A, 0x302F,
		0x3099, 0x309A, 0xA66F, 0xA672, 0xA674, 0xA67D, 0xA69E, 0xA69F,
		0xA6F0, 0xA6F1, 0xA802, 0xA802, 0xA806, 0xA806, 0xA80B, 0xA80B,
		0xA823, 0xA827, 0xA880, 0xA881, 0xA8B4, 0xA8C5, 0xA8E0, 0xA8F1,
		0xA926, 0xA92D, 0xA947, 0xA953, 0xA980, 0xA983, 0xA9B3, 0xA9C0,
		0xA9E5, 0xA9E5, 0xAA29, 0xAA36, 0xAA43, 0xAA43, 0xAA4C, 0xAA4D,
		0xAA7B, 0xAA7D, 0xAAB0, 0xAAB0, 0xAAB2, 0xAAB4, 0xAAB7, 0xAAB8,
		0xAABE, 0xAABF, 0xAAC1, 0xAAC1, 0xAAEB, 0xAAEF, 0xAAF5, 0xAAF6,
		0xABE3, 0xABEA, 0xABEC, 0xABED, 0xFB1E, 0xFB1E, 0xFE00, 0xFE0F,
		0xFE20, 0xFE2F, 0x101FD, 0x101FD, 0x102E0, 0x102E0, 0x10376, 0x1037A,
		0x10A01, 0x10A03, 0x10A05, 0x10A06, 0x10A0C, 0x10A0F, 0x10A38, 0x10A3A,
		0x10A3F, 0x10A3F, 0x10AE5, 0x10AE6, 0x10D24, 0x10D27, 0x10EAB, 0x10EAC,
		0x10F46, 0x10F50, 0x11000, 0x11002, 0x11038, 0x11046, 0x11070, 0x11070,
		0x11073, 0x11074, 0x1107F, 0x11082, 0x110B0, 0x110BA, 0x11100, 0x11102,
		0x11127, 0x11134, 0x11145, 0x11146, 0x11173, 0x11173, 0x11180, 0x11182,
		0x111B3, 0x111C0, 0x111C9, 0x111CC, 0x1122C, 0x11237, 0x1123E, 0x1123E,
		0x112DF, 0x112EA, 0x11300, 0x11303, 0x1133B, 0x1133C, 0x1133E, 0x11344,
		0x11347, 0x11348, 0x1134B, 0x1134D, 0x11357, 0x11357, 0x11362, 0x11363,
		0x11366, 0x1136C, 0x11370, 0x11374, 0x11435, 0x11446, 0x1145E, 0x1145E,
		0x114B0, 0x114C3, 0x115AF, 0x115B5, 0x115B8, 0x115C0, 0x11630, 0x11640,
		0x116AB, 0x116B7, 0x1171D, 0x1172B, 0x1182C, 0x1183A, 0x11930, 0x11935,
		0x11937, 0x11938, 0x1193B, 0x1193E, 0x11940, 0x11940, 0x11942, 0x11943,
		0x119D1, 0x119D7, 0x119DA, 0x119E0, 0x119E4, 0x119E4, 0x11A01, 0x11A0A,
		0x11A33, 0x11A39, 0x11A3B, 0x11A3E, 0x11A47, 0x11A47, 0x11A51, 0x11A5B,
		0x11A8A, 0x11A99, 0x11C2F, 0x11C36, 0x11C38, 0x11C3F, 0x11C92, 0x11CA7,
		0x11CA9, 0x11CB6, 0x11D31, 0x11D36, 0x11D3A, 0x11D3A, 0x11D3C, 0x11D3D,
		0x11D3F, 0x11D45, 0x11D47, 0x11D47, 0x11D8A, 0x11D8E, 0x11D90, 0x11D91,
		0x11D93, 0x11D97, 0x11EF3, 0x11EF6, 0x16AF0, 0x16AF4, 0x16B30, 0x16B36,
		0x16F4F, 0x16F4F, 0x16F51, 0x16F87, 0x16F8F, 0x16F92, 0x16FE4, 0x16FE4,
		0x1BC9D, 0x1BC9E, 0x1D165, 0x1D169, 0x1D16D, 0x1D172, 0x1D17B, 0x1D182,
		0x1D185, 0x1D18B, 0x1D1AA, 0x1D1AD, 0x1D242, 0x1D244, 0x1DA00, 0x1DA36,
		0x1DA3B, 0x1DA6C, 0x1DA75, 0x1DA75, 0x1DA84, 0x1DA84, 0x1DA9B, 0x1DA9F,
		0x1DAA1, 0x1DAAF, 0x1E000, 0x1E006, 0x1E008, 0x1E018, 0x1E01B, 0x1E021,
		0x1E023, 0x1E024, 0x1E026, 0x1E02A, 0x1E130, 0x1E136, 0x1E2EC, 0x1E2EF,
		0x1E8D0, 0x1E8D6, 0x1E944, 0x1E94A, 0xE0100, 0xE01EF
	};

	private static boolean inCodePointRanges(int cp, int[] ranges) {
		for (int i = 0; i < ranges.length; i += 2) {
			if (cp < ranges[i]) {
				return false;
			}
			if (cp <= ranges[i + 1]) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCombiningCodePoint(int cp) {
		return inCodePointRanges(cp, COMBINING_RANGES);
	}

	private static boolean isFormatCodePoint(int cp) {
		return cp == 0x00AD || cp == 0x061C || cp == 0x180E ||
			(cp >= 0x200B && cp <= 0x200F) ||
			(cp >= 0x202A && cp <= 0x202E) ||
			(cp >= 0x2060 && cp <= 0x206F) ||
			cp == 0xFEFF ||
			(cp >= 0xFFF9 && cp <= 0xFFFB) ||
			cp == 0x110BD || cp == 0x110CD ||
			(cp >= 0xE0000 && cp <= 0xE007F);
	}

	private static boolean isZeroWidthCodePoint(int cp) {
		return cp == 0 ||
			cp == 0x200C || cp == 0x200D ||
			cp == 0x20E3 ||
			isFormatCodePoint(cp) ||
			isCombiningCodePoint(cp) ||
			isVariationSelector(cp) ||
			isEmojiModifier(cp) ||
			isEmojiTag(cp);
	}

	private static int codePointDisplayWidth(int cp) {
		if (isZeroWidthCodePoint(cp)) {
			return 0;
		}
		if (cp < 0x1100) {
			return 1;
		}
		return isWideCodePoint(cp) || isEmojiPresentationCodePoint(cp) ? 2 : 1;
	}

	private static int skipClusterModifiers(String text, int offset, int baseCodePoint, int[] clusterWidth) {
		while (offset < text.length()) {
			int cp = text.codePointAt(offset);
			if (!isVariationSelector(cp) && !isCombiningCodePoint(cp) && !isEmojiModifier(cp) && !isEmojiTag(cp)) {
				break;
			}
			if (cp == 0xFE0F && isEmojiVariationBase(baseCodePoint)) {
				clusterWidth[0] = Math.max(clusterWidth[0], 2);
			}
			offset += Character.charCount(cp);
		}
		return offset;
	}

	private static int skipKeycapCluster(String text, int offset) {
		int next = offset;
		while (next < text.length() && isVariationSelector(text.codePointAt(next))) {
			next += Character.charCount(text.codePointAt(next));
		}
		if (next < text.length() && text.codePointAt(next) == 0x20E3) {
			return next + Character.charCount(0x20E3);
		}
		return -1;
	}

	private static int displayWidth(String text) {
		int width = 0;
		for (int offset = 0; offset < text.length();) {
			int cp = text.codePointAt(offset);
			offset += Character.charCount(cp);

			if (isZeroWidthCodePoint(cp)) {
				continue;
			}

			if (isKeycapBase(cp)) {
				int keycapEnd = skipKeycapCluster(text, offset);
				if (keycapEnd != -1) {
					width += 2;
					offset = keycapEnd;
					continue;
				}
			}

			if (isRegionalIndicator(cp) && offset < text.length()) {
				int next = text.codePointAt(offset);
				if (isRegionalIndicator(next)) {
					width += 2;
					offset += Character.charCount(next);
					continue;
				}
			}

			int[] clusterWidth = { codePointDisplayWidth(cp) };
			offset = skipClusterModifiers(text, offset, cp, clusterWidth);
			boolean joined = false;
			while (offset < text.length() && text.codePointAt(offset) == 0x200D) {
				joined = true;
				offset += Character.charCount(0x200D);
				if (offset >= text.length()) {
					break;
				}
				int joinedCp = text.codePointAt(offset);
				offset += Character.charCount(joinedCp);
				clusterWidth[0] = Math.max(clusterWidth[0], codePointDisplayWidth(joinedCp));
				offset = skipClusterModifiers(text, offset, joinedCp, clusterWidth);
			}
			width += joined && clusterWidth[0] > 0 ? Math.max(clusterWidth[0], 2) : clusterWidth[0];
		}
		return width;
	}

	private static boolean startsMarkdownLinkAt(String text, int offset) {
		if (offset >= text.length() || text.charAt(offset) != '[') {
			return false;
		}

		int closeBracket = offset + 1;
		while (closeBracket < text.length()) {
			if (text.charAt(closeBracket) == ']' && !isEscaped(text, closeBracket)) {
				break;
			}
			closeBracket += Character.charCount(text.codePointAt(closeBracket));
		}
		return closeBracket + 1 < text.length() && text.charAt(closeBracket + 1) == '(';
	}

	private static int markdownLinkEnd(String text, int offset) {
		int closeBracket = offset + 1;
		while (closeBracket < text.length()) {
			if (text.charAt(closeBracket) == ']' && !isEscaped(text, closeBracket)) {
				break;
			}
			closeBracket += Character.charCount(text.codePointAt(closeBracket));
		}
		if (closeBracket + 1 >= text.length() || text.charAt(closeBracket + 1) != '(') {
			return offset + 1;
		}

		int pos = closeBracket + 2;
		int depth = 1;
		while (pos < text.length()) {
			char ch = text.charAt(pos);
			if (ch == '(' && !isEscaped(text, pos)) {
				depth++;
			} else if (ch == ')' && !isEscaped(text, pos)) {
				depth--;
				if (depth == 0) {
					return pos + 1;
				}
			}
			pos += Character.charCount(text.codePointAt(pos));
		}
		return offset + 1;
	}

	private static int markdownCodeSpanEnd(String text, int offset) {
		if (offset >= text.length() || text.charAt(offset) != '`') {
			return offset + 1;
		}

		int tickCount = 0;
		while (offset + tickCount < text.length() && text.charAt(offset + tickCount) == '`') {
			tickCount++;
		}

		int pos = offset + tickCount;
		while (pos < text.length()) {
			if (text.charAt(pos) == '`') {
				int closingTicks = 0;
				while (pos + closingTicks < text.length() && text.charAt(pos + closingTicks) == '`') {
					closingTicks++;
				}
				if (closingTicks == tickCount) {
					return pos + closingTicks;
				}
				pos += closingTicks;
			} else {
				pos += Character.charCount(text.codePointAt(pos));
			}
		}
		return offset + tickCount;
	}

	private static int longTokenChunkEnd(String token, int offset, int width) {
		int targetWidth = Math.max(1, width);
		int end = offset;
		int chunkWidth = 0;
		while (end < token.length()) {
			int after = nextDisplayClusterEnd(token, end);
			int clusterWidth = displayWidth(token.substring(end, after));
			if (end > offset && chunkWidth + clusterWidth > targetWidth) {
				return end;
			}
			chunkWidth += clusterWidth;
			end = after;
			if (chunkWidth >= targetWidth) {
				return end;
			}
		}
		return end > offset ? end : offset + Character.charCount(token.codePointAt(offset));
	}

	private static int nextDisplayClusterEnd(String text, int offset) {
		int baseCodePoint = text.codePointAt(offset);
		int end = offset + Character.charCount(baseCodePoint);

		if (isKeycapBase(baseCodePoint)) {
			int keycapEnd = skipKeycapCluster(text, end);
			if (keycapEnd != -1) {
				return keycapEnd;
			}
		}

		if (isRegionalIndicator(baseCodePoint) && end < text.length()) {
			int next = text.codePointAt(end);
			if (isRegionalIndicator(next)) {
				return end + Character.charCount(next);
			}
		}

		int[] ignoredWidth = { codePointDisplayWidth(baseCodePoint) };
		end = skipClusterModifiers(text, end, baseCodePoint, ignoredWidth);
		while (end < text.length() && text.codePointAt(end) == 0x200D) {
			end += Character.charCount(0x200D);
			if (end >= text.length()) {
				break;
			}
			int joinedCodePoint = text.codePointAt(end);
			end += Character.charCount(joinedCodePoint);
			end = skipClusterModifiers(text, end, joinedCodePoint, ignoredWidth);
		}
		return end;
	}

	private static void appendWrappedToken(List<String> segments, StringBuilder current, String token, int width) {
		int tokenWidth = displayWidth(token);
		int currentWidth = displayWidth(current.toString());
		int candidateWidth = current.isEmpty() ? tokenWidth : currentWidth + 1 + tokenWidth;
		if (tokenWidth <= width) {
			if (!current.isEmpty() && candidateWidth > width) {
				segments.add(current.toString());
				current.setLength(0);
			}
			if (!current.isEmpty()) {
				current.append(' ');
			}
			current.append(token);
			return;
		}

		if (!current.isEmpty()) {
			segments.add(current.toString());
			current.setLength(0);
		}

		for (int offset = 0; offset < token.length();) {
			int end = longTokenChunkEnd(token, offset, width);
			String chunk = token.substring(offset, end);
			if (end < token.length()) {
				segments.add(chunk);
			} else {
				current.append(chunk);
			}
			offset = end;
		}
	}

	private static List<String> wrapCellSegments(String cell, int width) {
		String value = trim(cell);
		if (value.isEmpty()) {
			return List.of("");
		}

		List<String> segments = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int offset = 0;
		while (offset < value.length()) {
			while (offset < value.length() && isSpace(value.charAt(offset))) {
				offset++;
			}
			if (offset >= value.length()) {
				break;
			}

			int end = offset;
			if (value.charAt(offset) == '`') {
				end = markdownCodeSpanEnd(value, offset);
			} else if (startsMarkdownLinkAt(value, offset)) {
				end = markdownLinkEnd(value, offset);
			} else {
				while (end < value.length() && !isSpace(value.charAt(end))) {
					end += Character.charCount(value.codePointAt(end));
				}
			}

			appendWrappedToken(segments, current, value.substring(offset, end), width);
			offset = end;
		}

		if (!current.isEmpty()) {
			segments.add(current.toString());
		}
		if (segments.isEmpty()) {
			segments.add("");
		}
		return segments;
	}

	private static int wrapLongCells(Table table, int originalTargetRow) {
		if (table.separatorRow == -1) {
			return originalTargetRow;
		}

		List<Row> wrappedRows = new ArrayList<>();
		int wrappedTargetRow = originalTargetRow;
		int nextId = nextRowId(table);
		for (int rowIndex = 0; rowIndex < table.rows.size(); rowIndex++) {
			Row row = table.rows.get(rowIndex);
			if (row.separator || rowIndex <= table.separatorRow) {
				if (rowIndex == originalTargetRow) {
					wrappedTargetRow = wrappedRows.size();
				}
				wrappedRows.add(row);
				continue;
			}

			List<List<String>> cellSegments = new ArrayList<>();
			int segmentCount = 1;
			for (int column = 0; column < table.columns; column++) {
				List<String> segments = wrapCellSegments(row.cells.get(column), HARD_WRAP_CELL_WIDTH);
				cellSegments.add(segments);
				segmentCount = Math.max(segmentCount, segments.size());
			}

			if (rowIndex == originalTargetRow) {
				wrappedTargetRow = wrappedRows.size();
			}

			for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
				Row wrapped = new Row();
				wrapped.id = segmentIndex == 0 ? row.id : nextId++;
				for (int column = 0; column < table.columns; column++) {
					List<String> segments = cellSegments.get(column);
					wrapped.cells.add(segmentIndex < segments.size() ? segments.get(segmentIndex) : "");
				}
				wrappedRows.add(wrapped);
			}
		}

		table.rows.clear();
		table.rows.addAll(wrappedRows);
		return wrappedTargetRow;
	}

	private static List<Integer> naturalColumnWidths(Table table) {
		List<Integer> widths = new ArrayList<>();
		for (int i = 0; i < table.columns; i++) {
			widths.add(3);
		}

		for (Row row : table.rows) {
			if (row.separator) {
				continue;
			}
			for (int column = 0; column < table.columns; column++) {
				widths.set(column, Math.max(widths.get(column), displayWidth(row.cells.get(column))));
			}
		}
		return widths;
	}

	private static List<Integer> currentColumnWidths(Table table) {
		List<Integer> widths = new ArrayList<>();
		for (int i = 0; i < table.columns; i++) {
			widths.add(1);
		}

		for (Row row : table.rows) {
			for (int column = 0; column < table.columns && column < row.cells.size(); column++) {
				widths.set(column, Math.max(widths.get(column), displayWidth(row.cells.get(column))));
			}
		}
		return widths;
	}

	private static List<Integer> headerColumnWidths(Table table) {
		List<Integer> widths = new ArrayList<>();
		for (int i = 0; i < table.columns; i++) {
			widths.add(3);
		}

		int headerEnd = table.separatorRow == -1 ? table.rows.size() : table.separatorRow;
		for (int rowIndex = 0; rowIndex < headerEnd && rowIndex < table.rows.size(); rowIndex++) {
			Row row = table.rows.get(rowIndex);
			for (int column = 0; column < table.columns; column++) {
				widths.set(column, Math.max(widths.get(column), displayWidth(row.cells.get(column))));
			}
		}
		return widths;
	}

	private static int formattedTableOverhead(Table table) {
		int overhead = table.leadingPipe ? 1 : 0;
		for (int column = 0; column < table.columns; column++) {
			if (column > 0) {
				overhead += 2;
			}
			if (table.leadingPipe || column > 0) {
				overhead++;
			}
			if (table.trailingPipe && column + 1 == table.columns) {
				overhead += 2;
			}
		}
		return overhead;
	}

	private static long widthSum(List<Integer> widths) {
		long sum = 0;
		for (int width : widths) {
			sum += width;
		}
		return sum;
	}

	private static boolean containsSpace(String value) {
		for (int i = 0; i < value.length(); i++) {
			if (isSpace(value.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	private static List<Boolean> wrappableColumns(Table table, List<Integer> headerWidths) {
		List<Boolean> result = new ArrayList<>();
		for (int i = 0; i < table.columns; i++) {
			result.add(false);
		}

		for (int rowIndex = 0; rowIndex < table.rows.size(); rowIndex++) {
			Row row = table.rows.get(rowIndex);
			if (row.separator || rowIndex < table.separatorRow) {
				continue;
			}

			for (int column = 0; column < table.columns; column++) {
				int width = displayWidth(row.cells.get(column));
				int minimum = column < headerWidths.size() ? headerWidths.get(column) : 3;
				if (width > minimum && containsSpace(row.cells.get(column))) {
					result.set(column, true);
				}
			}
		}
		return result;
	}

	private static void shrinkColumnsToBudget(List<Integer> widths, List<Integer> minimums, List<Boolean> allowed, int budget) {
		long requestedReduction = widthSum(widths) - budget;
		if (requestedReduction <= 0) {
			return;
		}

		long totalSlack = 0;
		int maxSlack = 0;
		for (int column = 0; column < widths.size(); column++) {
			if (!allowed.get(column)) {
				continue;
			}
			int slack = Math.max(0, widths.get(column) - minimums.get(column));
			totalSlack += slack;
			maxSlack = Math.max(maxSlack, slack);
		}
		long reduction = Math.min(requestedReduction, totalSlack);
		if (reduction <= 0) {
			return;
		}

		int low = 0;
		int high = maxSlack;
		while (low < high) {
			int cap = low + (high - low) / 2;
			if (reductionToSlackCap(widths, minimums, allowed, cap) <= reduction) {
				high = cap;
			} else {
				low = cap + 1;
			}
		}

		int slackCap = low;
		long remaining = reduction - reductionToSlackCap(widths, minimums, allowed, slackCap);
		for (int column = 0; column < widths.size(); column++) {
			if (!allowed.get(column)) {
				continue;
			}
			int minimum = minimums.get(column);
			int slack = Math.max(0, widths.get(column) - minimum);
			int targetSlack = Math.min(slack, slackCap);
			if (remaining > 0 && targetSlack == slackCap && targetSlack > 0) {
				targetSlack--;
				remaining--;
			}
			widths.set(column, minimum + targetSlack);
		}
	}

	private static long reductionToSlackCap(
		List<Integer> widths,
		List<Integer> minimums,
		List<Boolean> allowed,
		int slackCap
	) {
		long reduction = 0;
		for (int column = 0; column < widths.size(); column++) {
			if (!allowed.get(column)) {
				continue;
			}
			int slack = Math.max(0, widths.get(column) - minimums.get(column));
			if (slack > slackCap) {
				reduction += slack - slackCap;
			}
		}
		return reduction;
	}

	private static int bestGrowableColumn(List<Integer> widths, List<Integer> naturalWidths, List<Boolean> allowed) {
		int best = -1;
		int bestSlack = 0;
		for (int column = 0; column < widths.size(); column++) {
			if (!allowed.get(column) || widths.get(column) >= naturalWidths.get(column)) {
				continue;
			}

			int slack = naturalWidths.get(column) - widths.get(column);
			if (best == -1 || slack > bestSlack) {
				best = column;
				bestSlack = slack;
			}
		}
		return best;
	}

	private static List<Integer> targetColumnWidthsForTableWidth(Table table, int maxTableWidth) {
		List<Integer> naturalWidths = naturalColumnWidths(table);
		if (naturalWidths.isEmpty()) {
			return naturalWidths;
		}

		int overhead = formattedTableOverhead(table);
		int minimumBudget = naturalWidths.size();
		int budget = maxTableWidth > overhead ? Math.max(maxTableWidth - overhead, minimumBudget) : minimumBudget;
		if (widthSum(naturalWidths) <= budget) {
			return naturalWidths;
		}

		List<Integer> headerWidths = headerColumnWidths(table);
		List<Boolean> canWrap = wrappableColumns(table, headerWidths);
		List<Integer> widths = new ArrayList<>();
		List<Integer> minimums = new ArrayList<>();
		for (int column = 0; column < naturalWidths.size(); column++) {
			int headerWidth = column < headerWidths.size() ? headerWidths.get(column) : 3;
			if (canWrap.get(column)) {
				int minimum = Math.min(naturalWidths.get(column), Math.max(headerWidth, MINIMUM_AUTO_WRAP_CELL_WIDTH));
				minimums.add(minimum);
				widths.add(minimum);
			} else {
				minimums.add(Math.min(naturalWidths.get(column), Math.max(headerWidth, 3)));
				widths.add(naturalWidths.get(column));
			}
		}

		if (widthSum(widths) > budget) {
			shrinkColumnsToBudget(widths, minimums, canWrap, budget);
		}

		if (widthSum(widths) > budget) {
			List<Boolean> allColumns = new ArrayList<>();
			List<Integer> hardMinimums = new ArrayList<>();
			for (int column = 0; column < widths.size(); column++) {
				allColumns.add(true);
				hardMinimums.add(Math.min(widths.get(column), MINIMUM_AUTO_WRAP_CELL_WIDTH));
			}
			shrinkColumnsToBudget(widths, hardMinimums, allColumns, budget);
		}

		while (widthSum(widths) < budget) {
			int column = bestGrowableColumn(widths, naturalWidths, canWrap);
			if (column == -1) {
				break;
			}
			widths.set(column, widths.get(column) + 1);
		}
		return widths;
	}

	private static int wrapCellsToColumnWidths(Table table, int originalTargetRow, List<Integer> columnWidths) {
		if (table.separatorRow == -1 || columnWidths.size() < table.columns) {
			return originalTargetRow;
		}

		List<Row> wrappedRows = new ArrayList<>();
		int wrappedTargetRow = originalTargetRow;
		int nextId = nextRowId(table);
		for (int rowIndex = 0; rowIndex < table.rows.size(); rowIndex++) {
			Row row = table.rows.get(rowIndex);
			if (row.separator || rowIndex <= table.separatorRow) {
				if (rowIndex == originalTargetRow) {
					wrappedTargetRow = wrappedRows.size();
				}
				wrappedRows.add(row);
				continue;
			}

			List<List<String>> cellSegments = new ArrayList<>();
			int segmentCount = 1;
			for (int column = 0; column < table.columns; column++) {
				List<String> segments = wrapCellSegments(row.cells.get(column), columnWidths.get(column));
				cellSegments.add(segments);
				segmentCount = Math.max(segmentCount, segments.size());
			}

			if (rowIndex == originalTargetRow) {
				wrappedTargetRow = wrappedRows.size();
			}

			for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
				Row wrapped = new Row();
				wrapped.id = segmentIndex == 0 ? row.id : nextId++;
				for (int column = 0; column < table.columns; column++) {
					List<String> segments = cellSegments.get(column);
					wrapped.cells.add(segmentIndex < segments.size() ? segments.get(segmentIndex) : "");
				}
				wrappedRows.add(wrapped);
			}
		}

		table.rows.clear();
		table.rows.addAll(wrappedRows);
		return wrappedTargetRow;
	}

	private static boolean[] continuationRowsToPreserve(Table table, int originalTargetRow) {
		boolean[] preserve = new boolean[table.rows.size()];
		if (table.separatorRow == -1 || originalTargetRow < 0 || originalTargetRow >= table.rows.size()) {
			return preserve;
		}

		int[] continuationBaseForRow = new int[table.rows.size()];
		for (int i = 0; i < continuationBaseForRow.length; i++) {
			continuationBaseForRow[i] = -1;
		}

		int baseRowIndex = -1;
		Row baseRow = null;
		for (int rowIndex = 0; rowIndex < table.rows.size(); rowIndex++) {
			Row row = table.rows.get(rowIndex);
			if (row.separator || rowIndex <= table.separatorRow || baseRowIndex == -1 || !isLikelyContinuationRow(row, baseRow, table.columns)) {
				if (!row.separator && rowIndex > table.separatorRow) {
					baseRowIndex = rowIndex;
					baseRow = copyRow(row);
				}
				continue;
			}

			continuationBaseForRow[rowIndex] = baseRowIndex;
			for (int column = 0; column < table.columns; column++) {
				baseRow.cells.set(column, appendContinuationCell(baseRow.cells.get(column), row.cells.get(column)));
			}
		}

		int targetBaseRow = continuationBaseForRow[originalTargetRow];
		if (targetBaseRow == -1) {
			return preserve;
		}
		for (int rowIndex = 0; rowIndex < continuationBaseForRow.length; rowIndex++) {
			preserve[rowIndex] = continuationBaseForRow[rowIndex] == targetBaseRow;
		}
		return preserve;
	}

	private static int minimumManualColumnWidth(Table table, int column, List<Integer> naturalWidths) {
		List<Integer> headerWidths = headerColumnWidths(table);
		int headerWidth = column < headerWidths.size() ? headerWidths.get(column) : 3;
		int naturalWidth = column < naturalWidths.size() ? naturalWidths.get(column) : headerWidth;
		return Math.min(naturalWidth, Math.max(headerWidth, 3));
	}

	private static int resizeColumnWidth(Table table, int originalTargetRow, int column, boolean widen, List<Integer> columnWidths) {
		if (table.separatorRow == -1 || column >= table.columns) {
			return originalTargetRow;
		}

		columnWidths.clear();
		columnWidths.addAll(currentColumnWidths(table));
		originalTargetRow = unwrapContinuationRows(table, originalTargetRow);
		if (column >= columnWidths.size()) {
			return originalTargetRow;
		}

		List<Integer> naturalWidths = naturalColumnWidths(table);
		if (widen) {
			columnWidths.set(column, columnWidths.get(column) + 1);
		} else {
			int minimumWidth = minimumManualColumnWidth(table, column, naturalWidths);
			if (columnWidths.get(column) > minimumWidth) {
				columnWidths.set(column, columnWidths.get(column) - 1);
			}
		}

		return wrapCellsToColumnWidths(table, originalTargetRow, columnWidths);
	}

	private static boolean cellHasText(String cell) {
		return !trim(cell).isEmpty();
	}

	private static int nonEmptyCellCount(Row row) {
		int count = 0;
		for (String cell : row.cells) {
			if (cellHasText(cell)) {
				count++;
			}
		}
		return count;
	}

	private static boolean isLikelyContinuationRow(Row row, Row baseRow, int columns) {
		if (columns < 2 || row.cells.size() < columns || baseRow.cells.size() < columns) {
			return false;
		}

		int nonEmpty = nonEmptyCellCount(row);
		if (nonEmpty == 0 || nonEmpty == columns) {
			return false;
		}

		int emptyWhereBaseHasText = 0;
		for (int column = 0; column < columns; column++) {
			if (!cellHasText(row.cells.get(column)) && cellHasText(baseRow.cells.get(column))) {
				emptyWhereBaseHasText++;
			}
		}

		int requiredAnchors = Math.max(1, columns / 3);
		return emptyWhereBaseHasText >= requiredAnchors;
	}

	private static Row copyRow(Row row) {
		Row copy = new Row();
		copy.id = row.id;
		copy.separator = row.separator;
		copy.cells.addAll(row.cells);
		return copy;
	}

	private static boolean isAsciiAlphaNumeric(int codePoint) {
		return codePoint >= 'A' && codePoint <= 'Z' ||
			codePoint >= 'a' && codePoint <= 'z' ||
			codePoint >= '0' && codePoint <= '9';
	}

	private static boolean isWordContinuationStart(String value) {
		if (value.isEmpty()) {
			return false;
		}
		int codePoint = value.codePointAt(0);
		return isAsciiAlphaNumeric(codePoint) || codePoint == '_' || codePoint >= 0x80;
	}

	private static boolean isWordContinuationEnd(String value) {
		String trimmed = trim(value);
		if (trimmed.isEmpty()) {
			return false;
		}
		int codePoint = trimmed.codePointBefore(trimmed.length());
		return isAsciiAlphaNumeric(codePoint) || codePoint == '_' || codePoint == '-' || codePoint >= 0x80;
	}

	private static String firstToken(String value) {
		int end = 0;
		while (end < value.length() && !isSpace(value.charAt(end))) {
			end += Character.charCount(value.codePointAt(end));
		}
		return value.substring(0, end);
	}

	private static boolean looksLikeSplitWordRemainder(String token) {
		if (token.isEmpty()) {
			return false;
		}

		int width = displayWidth(token);
		int first = token.codePointAt(0);
		if (first >= 0x80) {
			return width <= 4;
		}
		return width <= 2;
	}

	private static boolean shouldJoinContinuationWithoutSpace(String target, String continuation) {
		String targetValue = trim(target);
		String continuationValue = trim(continuation);
		if (targetValue.isEmpty() || continuationValue.isEmpty()) {
			return false;
		}
		if (!isWordContinuationEnd(targetValue) || !isWordContinuationStart(continuationValue)) {
			return false;
		}

		int targetEnd = targetValue.codePointBefore(targetValue.length());
		int continuationStart = continuationValue.codePointAt(0);
		if (targetEnd == '-' && (isAsciiAlphaNumeric(continuationStart) || continuationStart >= 0x80)) {
			return true;
		}

		return looksLikeSplitWordRemainder(firstToken(continuationValue));
	}

	private static String appendContinuationCell(String target, String continuation) {
		String value = trim(continuation);
		if (value.isEmpty()) {
			return target;
		}
		if (!trim(target).isEmpty() && !shouldJoinContinuationWithoutSpace(target, value)) {
			return target + " " + value;
		}
		if (!trim(target).isEmpty()) {
			return target + value;
		}
		return value;
	}

	private static int unwrapContinuationRows(Table table, int originalTargetRow) {
		if (table.separatorRow == -1) {
			return originalTargetRow;
		}

		boolean[] preserveContinuationRows = continuationRowsToPreserve(table, originalTargetRow);
		List<Row> unwrappedRows = new ArrayList<>();
		int targetRow = originalTargetRow;
		int baseRowIndex = -1;
		for (int rowIndex = 0; rowIndex < table.rows.size(); rowIndex++) {
			Row row = table.rows.get(rowIndex);
			if (row.separator || rowIndex <= table.separatorRow || preserveContinuationRows[rowIndex] || baseRowIndex == -1 ||
				!isLikelyContinuationRow(row, unwrappedRows.get(baseRowIndex), table.columns)) {
				if (rowIndex == originalTargetRow) {
					targetRow = unwrappedRows.size();
				}
				if (!row.separator && rowIndex > table.separatorRow) {
					baseRowIndex = unwrappedRows.size();
				}
				unwrappedRows.add(row);
				continue;
			}

			if (rowIndex == originalTargetRow) {
				targetRow = baseRowIndex;
			}

			Row baseRow = unwrappedRows.get(baseRowIndex);
			for (int column = 0; column < table.columns; column++) {
				baseRow.cells.set(column, appendContinuationCell(baseRow.cells.get(column), row.cells.get(column)));
			}
		}

		table.rows.clear();
		table.rows.addAll(unwrappedRows);
		return targetRow;
	}

	private static String spaces(int count) {
		return " ".repeat(Math.max(count, 0));
	}

	private static String separatorCell(Align align, int width, int minimumWidth) {
		int target = Math.max(width, minimumWidth);
		if (target <= 1) {
			return "-";
		}
		if (target == 2) {
			if (align == Align.LEFT || align == Align.CENTER) {
				return ":-";
			}
			if (align == Align.RIGHT) {
				return "-:";
			}
			return "--";
		}
		if (align == Align.CENTER) {
			return ":" + "-".repeat(target - 2) + ":";
		}
		if (align == Align.LEFT) {
			return ":" + "-".repeat(target - 1);
		}
		if (align == Align.RIGHT) {
			return "-".repeat(target - 1) + ":";
		}
		return "-".repeat(target);
	}

	private static String paddedCell(String cell, Align align, int width, int[] contentOffset) {
		int current = displayWidth(cell);
		int pad = width > current ? width - current : 0;
		int leftPad = 0;
		int rightPad = pad;

		if (align == Align.RIGHT) {
			leftPad = pad;
			rightPad = 0;
		} else if (align == Align.CENTER) {
			leftPad = pad / 2;
			rightPad = pad - leftPad;
		}

		if (contentOffset != null && contentOffset.length > 0) {
			contentOffset[0] = leftPad;
		}
		return spaces(leftPad) + cell + spaces(rightPad);
	}

	private static FormatResult formatTable(Table table, int targetRow, int targetColumn) {
		return formatTable(table, targetRow, targetColumn, null);
	}

	private static FormatResult formatTable(Table table, int targetRow, int targetColumn, List<Integer> minimumWidths) {
		int separatorMinimumWidth = minimumWidths == null ? 3 : 1;
		List<Integer> widths = new ArrayList<>();
		for (int i = 0; i < table.columns; i++) {
			widths.add(separatorMinimumWidth);
		}

		for (Row row : table.rows) {
			if (row.separator) {
				continue;
			}
			for (int column = 0; column < table.columns; column++) {
				widths.set(column, Math.max(widths.get(column), displayWidth(row.cells.get(column))));
			}
		}
		if (minimumWidths != null) {
			for (int column = 0; column < table.columns && column < minimumWidths.size(); column++) {
				widths.set(column, Math.max(widths.get(column), minimumWidths.get(column)));
			}
		}

		FormatResult result = new FormatResult();
		result.targetRow = targetRow < table.rows.size() ? targetRow : 0;
		result.targetColumn = targetColumn < table.columns ? targetColumn : 0;

		for (int rowIndex = 0; rowIndex < table.rows.size(); rowIndex++) {
			Row row = table.rows.get(rowIndex);
			StringBuilder line = new StringBuilder(table.leadingPipe ? "|" : "");

			for (int column = 0; column < table.columns; column++) {
				if (column > 0) {
					line.append(" |");
				}
				if (table.leadingPipe || column > 0) {
					line.append(' ');
				}

				if (row.separator) {
					String value = separatorCell(table.alignments.get(column), widths.get(column), separatorMinimumWidth);
					int valueStart = line.length();
					line.append(value);
					if (rowIndex == result.targetRow && column == result.targetColumn) {
						result.targetColumnOffset = valueStart;
					}
				} else {
					int[] contentOffset = new int[1];
					String value = paddedCell(row.cells.get(column), table.alignments.get(column), widths.get(column), contentOffset);
					int cellStart = line.length();
					line.append(value);
					if (rowIndex == result.targetRow && column == result.targetColumn) {
						result.targetColumnOffset = cellStart + contentOffset[0];
					}
				}

				if (table.trailingPipe && column + 1 == table.columns) {
					line.append(" |");
				}
			}

			result.lines.add(line.toString());
		}

		return result;
	}

	private static int nextEditableRow(Table table, int row) {
		int next = row + 1;
		while (next < table.rows.size() && table.rows.get(next).separator) {
			next++;
		}
		return next;
	}

	private static int previousEditableRow(Table table, int row) {
		if (row == 0) {
			return 0;
		}

		int previous = row - 1;
		while (previous >= 0 && table.rows.get(previous).separator) {
			previous--;
		}
		return previous >= 0 ? previous : row;
	}

	private static int editableRowCount(Table table) {
		int count = 0;
		for (Row row : table.rows) {
			if (!row.separator) {
				count++;
			}
		}
		return count;
	}

	private static boolean canDeleteRow(Table table, int row) {
		if (table.rows.get(row).separator || editableRowCount(table) <= 1) {
			return false;
		}
		return table.separatorRow == -1 || row + 1 != table.separatorRow;
	}

	private static int closestEditableRow(Table table, int row) {
		if (table.rows.isEmpty()) {
			return 0;
		}

		if (row >= table.rows.size()) {
			row = table.rows.size() - 1;
		}
		if (!table.rows.get(row).separator) {
			return row;
		}

		for (int next = row + 1; next < table.rows.size(); next++) {
			if (!table.rows.get(next).separator) {
				return next;
			}
		}

		while (row > 0) {
			row--;
			if (!table.rows.get(row).separator) {
				return row;
			}
		}

		return 0;
	}

	private static int nextRowId(Table table) {
		int id = 0;
		for (Row row : table.rows) {
			id = Math.max(id, row.id + 1);
		}
		return id;
	}

	private static int rowById(Table table, int id, int fallbackRow) {
		for (int rowIndex = 0; rowIndex < table.rows.size(); rowIndex++) {
			if (table.rows.get(rowIndex).id == id) {
				return rowIndex;
			}
		}
		return closestEditableRow(table, fallbackRow);
	}

	private static void insertEmptyRow(Table table, int index) {
		Row row = new Row();
		row.id = nextRowId(table);
		for (int i = 0; i < table.columns; i++) {
			row.cells.add("");
		}
		if (index > table.rows.size()) {
			index = table.rows.size();
		}
		table.rows.add(index, row);
		if (table.separatorRow != -1 && index <= table.separatorRow) {
			table.separatorRow++;
		}
	}

	private static void removeColumn(Table table, int column) {
		if (table.columns <= 1 || column >= table.columns) {
			return;
		}

		for (Row row : table.rows) {
			row.cells.remove(column);
		}
		table.alignments.remove(column);
		table.columns--;
	}

	private static void insertColumn(Table table, int column) {
		if (column > table.columns) {
			column = table.columns;
		}

		for (Row row : table.rows) {
			row.cells.add(column, "");
		}
		table.alignments.add(column, Align.NONE);
		table.columns++;
	}

	private static void moveColumn(Table table, int from, int to) {
		if (from >= table.columns || to >= table.columns || from == to) {
			return;
		}

		for (Row row : table.rows) {
			String value = row.cells.remove(from);
			row.cells.add(to, value);
		}

		Align align = table.alignments.remove(from);
		table.alignments.add(to, align);
	}

	private static int sortRows(Table table, int column, boolean ascending, int currentRowId, int fallbackRow) {
		if (column >= table.columns || table.rows.isEmpty()) {
			return closestEditableRow(table, fallbackRow);
		}

		int firstDataRow = table.separatorRow + 1;
		if (firstDataRow >= table.rows.size()) {
			return rowById(table, currentRowId, fallbackRow);
		}

		List<SortEntry> entries = new ArrayList<>(table.rows.size() - firstDataRow);
		for (int rowIndex = firstDataRow; rowIndex < table.rows.size(); rowIndex++) {
			Row row = table.rows.get(rowIndex);
			entries.add(new SortEntry(row, makeSortKey(row.cells.get(column))));
		}

		Comparator<SortEntry> comparator = (left, right) -> {
			int compared = compareSortKeys(left.key, right.key);
			return ascending ? compared : -compared;
		};
		entries.sort(comparator);

		int targetRow = firstDataRow;
		for (int i = 0; i < entries.size(); i++) {
			SortEntry entry = entries.get(i);
			int rowIndex = firstDataRow + i;
			table.rows.set(rowIndex, entry.row);
		}
		return rowById(table, currentRowId, targetRow);
	}

	private static SortKey makeSortKey(String value) {
		String text = trim(value);
		Double number = parseNumber(text);
		return new SortKey(number != null, number == null ? 0 : number, foldCaseForSort(text), text);
	}

	private static int compareSortKeys(SortKey left, SortKey right) {
		if (left.numeric != right.numeric) {
			return left.numeric ? -1 : 1;
		}
		if (left.numeric) {
			if (left.number < right.number) {
				return -1;
			}
			if (left.number > right.number) {
				return 1;
			}
		}

		int text = compareCodePointOrder(left.foldedText, right.foldedText);
		if (text != 0) {
			return text;
		}
		return compareCodePointOrder(left.text, right.text);
	}

	private static String foldCaseForSort(String value) {
		StringBuilder folded = new StringBuilder(value.length());
		for (int offset = 0; offset < value.length();) {
			int cp = value.codePointAt(offset);
			offset += Character.charCount(cp);
			appendSortCodePoint(folded, cp);
		}
		return folded.toString();
	}

	private static int compareCodePointOrder(String left, String right) {
		int leftOffset = 0;
		int rightOffset = 0;
		while (leftOffset < left.length() && rightOffset < right.length()) {
			int leftCp = left.codePointAt(leftOffset);
			int rightCp = right.codePointAt(rightOffset);
			if (leftCp != rightCp) {
				return Integer.compare(leftCp, rightCp);
			}
			leftOffset += Character.charCount(leftCp);
			rightOffset += Character.charCount(rightCp);
		}
		if (leftOffset < left.length()) {
			return 1;
		}
		if (rightOffset < right.length()) {
			return -1;
		}
		return 0;
	}

	private static void appendSortCodePoint(StringBuilder folded, int cp) {
		if (isSortIgnorableCodePoint(cp)) {
			return;
		}

		switch (cp) {
			case 0x00C0: case 0x00C1: case 0x00C2: case 0x00C3: case 0x00C4: case 0x00C5:
			case 0x00E0: case 0x00E1: case 0x00E2: case 0x00E3: case 0x00E4: case 0x00E5:
				folded.append('a');
				return;
			case 0x00C6: case 0x00E6:
				folded.append("ae");
				return;
			case 0x00C7: case 0x00E7:
				folded.append('c');
				return;
			case 0x00D0: case 0x00F0:
				folded.append('d');
				return;
			case 0x00C8: case 0x00C9: case 0x00CA: case 0x00CB:
			case 0x00E8: case 0x00E9: case 0x00EA: case 0x00EB:
				folded.append('e');
				return;
			case 0x00CC: case 0x00CD: case 0x00CE: case 0x00CF:
			case 0x00EC: case 0x00ED: case 0x00EE: case 0x00EF:
				folded.append('i');
				return;
			case 0x00D1: case 0x00F1:
				folded.append('n');
				return;
			case 0x00D2: case 0x00D3: case 0x00D4: case 0x00D5: case 0x00D6: case 0x00D8:
			case 0x00F2: case 0x00F3: case 0x00F4: case 0x00F5: case 0x00F6: case 0x00F8:
				folded.append('o');
				return;
			case 0x00D9: case 0x00DA: case 0x00DB: case 0x00DC:
			case 0x00F9: case 0x00FA: case 0x00FB: case 0x00FC:
				folded.append('u');
				return;
			case 0x00DD: case 0x00FD: case 0x00FF:
				folded.append('y');
				return;
			case 0x00DE: case 0x00FE:
				folded.append("th");
				return;
			case 0x00DF:
				folded.append("ss");
				return;
			default:
				folded.appendCodePoint(foldCaseCodePoint(cp));
		}
	}

	private static boolean isSortIgnorableCodePoint(int cp) {
		return isCombiningCodePoint(cp) ||
			isVariationSelector(cp) ||
			isEmojiModifier(cp) ||
			isEmojiTag(cp) ||
			isFormatCodePoint(cp);
	}

	private static int foldCaseCodePoint(int cp) {
		if (cp >= 'A' && cp <= 'Z') {
			return cp + ('a' - 'A');
		}
		if (cp >= 0x00C0 && cp <= 0x00D6) {
			return cp + 0x20;
		}
		if (cp >= 0x00D8 && cp <= 0x00DE) {
			return cp + 0x20;
		}
		if (cp == 0x0178) {
			return 0x00FF;
		}
		if (cp >= 0x0391 && cp <= 0x03A1) {
			return cp + 0x20;
		}
		if (cp >= 0x03A3 && cp <= 0x03AB) {
			return cp + 0x20;
		}
		if (cp >= 0x0400 && cp <= 0x040F) {
			return cp + 0x50;
		}
		if (cp >= 0x0410 && cp <= 0x042F) {
			return cp + 0x20;
		}

		switch (cp) {
			case 0x0386:
				return 0x03AC;
			case 0x0388:
				return 0x03AD;
			case 0x0389:
				return 0x03AE;
			case 0x038A:
				return 0x03AF;
			case 0x038C:
				return 0x03CC;
			case 0x038E:
				return 0x03CD;
			case 0x038F:
				return 0x03CE;
			default:
				return cp;
		}
	}

	private static boolean isAsciiDigit(char ch) {
		return ch >= '0' && ch <= '9';
	}

	// Both plugin cores accept the same strict grammar: [+-]? digits [. digits?] | [+-]? . digits,
	// with an optional [eE][+-]digits exponent. Hex floats, "Infinity"/"NaN" spellings, and
	// Java-only suffixes like "1.5f" stay textual so both plugins sort them identically.
	private static boolean isStrictDecimalNumber(String value) {
		int length = value.length();
		int pos = 0;
		if (pos < length && (value.charAt(pos) == '+' || value.charAt(pos) == '-')) {
			pos++;
		}
		int mantissaDigits = 0;
		while (pos < length && isAsciiDigit(value.charAt(pos))) {
			pos++;
			mantissaDigits++;
		}
		if (pos < length && value.charAt(pos) == '.') {
			pos++;
			while (pos < length && isAsciiDigit(value.charAt(pos))) {
				pos++;
				mantissaDigits++;
			}
		}
		if (mantissaDigits == 0) {
			return false;
		}
		if (pos < length && (value.charAt(pos) == 'e' || value.charAt(pos) == 'E')) {
			pos++;
			if (pos < length && (value.charAt(pos) == '+' || value.charAt(pos) == '-')) {
				pos++;
			}
			int exponentDigits = 0;
			while (pos < length && isAsciiDigit(value.charAt(pos))) {
				pos++;
				exponentDigits++;
			}
			if (exponentDigits == 0) {
				return false;
			}
		}
		return pos == length;
	}

	private static Double parseNumber(String value) {
		if (!isStrictDecimalNumber(value)) {
			return null;
		}
		return Double.parseDouble(value);
	}

	private static void setResultFromFormat(EditResult result, FormatResult formatted) {
		result.lines = formatted.lines;
		result.targetRow = formatted.targetRow;
		result.targetColumn = formatted.targetColumn;
		result.targetColumnOffset = formatted.targetColumnOffset;
	}
}
