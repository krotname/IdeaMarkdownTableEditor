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
		MOVE_ROW_UP,
		MOVE_ROW_DOWN,
		MOVE_COLUMN_LEFT,
		MOVE_COLUMN_RIGHT,
		SORT_ASCENDING,
		SORT_DESCENDING
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

	private enum Align {
		NONE,
		LEFT,
		CENTER,
		RIGHT
	}

	private static final class Row {
		final List<String> cells = new ArrayList<>();
		boolean separator;
	}

	private static final class Table {
		final List<Row> rows = new ArrayList<>();
		final List<Align> alignments = new ArrayList<>();
		int columns;
		int separatorRow = -1;
	}

	private static final class FormatResult {
		final List<String> lines = new ArrayList<>();
		final List<List<Integer>> starts = new ArrayList<>();
	}

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

	public static EditResult apply(List<String> lines, int row, int column, Action action) {
		EditResult result = new EditResult();
		if (lines.isEmpty()) {
			result.message = "No table found";
			return result;
		}

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
				targetRow = sortRows(table, column, true, table.rows.get(row));
				break;
			case SORT_DESCENDING:
				targetRow = sortRows(table, column, false, table.rows.get(row));
				break;
			case ALIGN:
				break;
		}

		FormatResult formatted = formatTable(table);
		setResultFromFormat(result, formatted, targetRow, targetColumn);
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
		FormatResult formatted = formatTable(table);
		setResultFromFormat(result, formatted, 0, 0);
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
		FormatResult formatted = formatTable(table);
		setResultFromFormat(result, formatted, dataRows > 0 ? 2 : 0, 0);
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

	private static boolean startsWithUnescapedPipe(String line) {
		int pos = 0;
		while (pos < line.length() && isSpace(line.charAt(pos))) {
			pos++;
		}
		return pos < line.length() && line.charAt(pos) == '|' && !isEscaped(line, pos);
	}

	private static List<String> splitCells(String line) {
		String body = trim(line);
		if (!body.isEmpty() && body.charAt(0) == '|') {
			body = body.substring(1);
		}
		if (endsWithUnescapedPipe(body)) {
			body = body.substring(0, body.length() - 1);
		}

		List<String> cells = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < body.length(); i++) {
			if (body.charAt(i) == '|' && !isEscaped(body, i)) {
				cells.add(trim(body.substring(start, i)));
				start = i + 1;
			}
		}
		cells.add(trim(body.substring(start)));
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

		for (int i = 1; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (ch != '-' && ch != '=' && ch != '|' && ch != ':' && !isSpace(ch)) {
				return false;
			}
		}
		return true;
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

	private static Table parseTable(List<String> lines) {
		Table table = new Table();
		for (String line : lines) {
			Row row = new Row();
			row.cells.addAll(splitCells(line));
			table.columns = Math.max(table.columns, row.cells.size());
			table.rows.add(row);
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
		for (int column = 0; column < table.columns; column++) {
			header.cells.add(column < rows.get(0).size() ? sanitizeMarkdownCell(rows.get(0).get(column)) : "");
		}
		table.rows.add(header);

		Row separator = new Row();
		separator.separator = true;
		for (int column = 0; column < table.columns; column++) {
			separator.cells.add("---");
			table.alignments.add(Align.NONE);
		}
		table.rows.add(separator);

		for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
			Row row = new Row();
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
			if (ch == '|') {
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
		String value = text == null ? "" : text.strip();
		if (value.isEmpty()) {
			return Collections.emptyList();
		}
		if (!value.contains(",") && !value.contains("\t") && !value.contains("\n") && !value.contains("\r")) {
			return Collections.emptyList();
		}

		char delimiter = detectDelimiter(value);
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (inQuotes) {
				if (ch == '"') {
					if (i + 1 < value.length() && value.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else {
						inQuotes = false;
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

			if (ch == '"' && isBlank(cell)) {
				cell.setLength(0);
				inQuotes = true;
			} else if (ch == delimiter) {
				row.add(cell.toString());
				cell.setLength(0);
			} else if (ch == '\r' || ch == '\n') {
				row.add(cell.toString());
				addDelimitedRow(rows, row);
				row = new ArrayList<>();
				cell.setLength(0);
				if (ch == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n') {
					i++;
				}
			} else {
				cell.append(ch);
			}
		}

		row.add(cell.toString());
		addDelimitedRow(rows, row);
		return rows;
	}

	private static char detectDelimiter(String text) {
		int tabs = 0;
		int commas = 0;
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
				commas++;
				cellBlank = true;
			} else if (ch == '\r' || ch == '\n') {
				cellBlank = true;
			} else if (!isSpace(ch)) {
				cellBlank = false;
			}
		}
		return tabs > commas ? '\t' : ',';
	}

	private static boolean isBlank(StringBuilder value) {
		for (int i = 0; i < value.length(); i++) {
			if (!isSpace(value.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static void addDelimitedRow(List<List<String>> rows, List<String> row) {
		boolean hasValue = false;
		for (String cell : row) {
			if (!trim(cell).isEmpty()) {
				hasValue = true;
				break;
			}
		}
		if (hasValue) {
			rows.add(row);
		}
	}

	private static boolean isMarkdownTable(Table table) {
		return table.separatorRow > 0;
	}

	private static boolean isCjkCodePoint(int cp) {
		return (cp >= 0x1100 && cp <= 0x115F) ||
			(cp >= 0x2E80 && cp <= 0xA4CF) ||
			(cp >= 0xAC00 && cp <= 0xD7A3) ||
			(cp >= 0xF900 && cp <= 0xFAFF) ||
			(cp >= 0xFE10 && cp <= 0xFE6F) ||
			(cp >= 0xFF00 && cp <= 0xFF60) ||
			(cp >= 0xFFE0 && cp <= 0xFFE6);
	}

	private static int displayWidth(String text) {
		int width = 0;
		for (int offset = 0; offset < text.length();) {
			int cp = text.codePointAt(offset);
			width += isCjkCodePoint(cp) ? 2 : 1;
			offset += Character.charCount(cp);
		}
		return width;
	}

	private static String spaces(int count) {
		return " ".repeat(Math.max(count, 0));
	}

	private static String separatorCell(Align align, int width) {
		int target = Math.max(width, 3);
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

	private static FormatResult formatTable(Table table) {
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

		FormatResult result = new FormatResult();
		for (Row row : table.rows) {
			StringBuilder line = new StringBuilder("|");
			List<Integer> starts = new ArrayList<>();

			for (int column = 0; column < table.columns; column++) {
				if (row.separator) {
					String value = separatorCell(table.alignments.get(column), widths.get(column));
					line.append(' ').append(value).append(" |");
					starts.add(Math.max(line.length() - value.length() - 2, 0));
				} else {
					int[] contentOffset = new int[1];
					String value = paddedCell(row.cells.get(column), table.alignments.get(column), widths.get(column), contentOffset);
					int cellStart = line.length() + 1;
					line.append(' ').append(value).append(" |");
					starts.add(cellStart + contentOffset[0]);
				}
			}

			result.lines.add(line.toString());
			result.starts.add(starts);
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

	private static void insertEmptyRow(Table table, int index) {
		Row row = new Row();
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

	private static int sortRows(Table table, int column, boolean ascending, Row currentRow) {
		int firstDataRow = table.separatorRow + 1;
		if (firstDataRow >= table.rows.size()) {
			return table.rows.indexOf(currentRow);
		}

		List<Row> dataRows = table.rows.subList(firstDataRow, table.rows.size());
		Comparator<Row> comparator = (left, right) -> compareCells(left.cells.get(column), right.cells.get(column));
		if (!ascending) {
			comparator = comparator.reversed();
		}
		dataRows.sort(comparator);

		int targetRow = table.rows.indexOf(currentRow);
		return targetRow >= 0 ? targetRow : firstDataRow;
	}

	private static int compareCells(String left, String right) {
		String leftValue = trim(left);
		String rightValue = trim(right);
		Double leftNumber = parseNumber(leftValue);
		Double rightNumber = parseNumber(rightValue);
		if (leftNumber != null && rightNumber != null) {
			int numeric = Double.compare(leftNumber, rightNumber);
			if (numeric != 0) {
				return numeric;
			}
		}

		int text = leftValue.compareToIgnoreCase(rightValue);
		if (text != 0) {
			return text;
		}
		return leftValue.compareTo(rightValue);
	}

	private static Double parseNumber(String value) {
		if (value.isEmpty()) {
			return null;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static void setResultFromFormat(EditResult result, FormatResult formatted, int row, int column) {
		result.lines = formatted.lines;
		result.targetRow = row < formatted.starts.size() ? row : 0;
		result.targetColumn = column;

		if (!formatted.starts.isEmpty() && result.targetRow < formatted.starts.size()) {
			List<Integer> rowStarts = formatted.starts.get(result.targetRow);
			if (result.targetColumn >= rowStarts.size()) {
				result.targetColumn = rowStarts.isEmpty() ? 0 : rowStarts.size() - 1;
			}
			result.targetColumnOffset = rowStarts.isEmpty() ? 0 : rowStarts.get(result.targetColumn);
		}
	}
}
