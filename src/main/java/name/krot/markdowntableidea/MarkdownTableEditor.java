package name.krot.markdowntableidea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import name.krot.markdowntableidea.core.MarkdownTableCore;

import java.util.ArrayList;
import java.util.List;

public final class MarkdownTableEditor {
	private static final String COMMAND_NAME = "Markdown Table Editor";
	private static CommandRunner commandRunner = MarkdownTableEditor::runWriteCommand;

	private MarkdownTableEditor() {
	}

	@FunctionalInterface
	interface CommandRunner {
		void run(Project project, String name, Runnable change);
	}

	static void setCommandRunnerForTests(CommandRunner runner) {
		commandRunner = runner == null ? MarkdownTableEditor::runWriteCommand : runner;
	}

	public static boolean run(Editor editor, Project project, MarkdownTableCore.Action action, boolean quiet) {
		if (editor == null || editor.isViewer()) {
			return false;
		}

		Document document = editor.getDocument();
		if (!document.isWritable()) {
			if (!quiet) {
				document.fireReadOnlyModificationAttempt();
			}
			return false;
		}

		if (document.getLineCount() == 0) {
			return false;
		}

		int currentOffset = Math.min(editor.getCaretModel().getOffset(), Math.max(document.getTextLength() - 1, 0));
		int currentLine = document.getLineNumber(currentOffset);
		if (currentLine >= document.getLineCount()) {
			currentLine = document.getLineCount() - 1;
		}

		String currentLineText = getLineText(document, currentLine);
		if (!MarkdownTableCore.isPotentialTableLine(currentLineText)) {
			if (!quiet) {
				Messages.showInfoMessage(project, "Put the caret inside a Markdown pipe table first.", COMMAND_NAME);
			}
			return false;
		}

		LineRange tableRange = findTableLineRange(document, currentLine);
		if (tableRange == null) {
			if (!quiet) {
				Messages.showInfoMessage(project, "Could not edit the Markdown table.", COMMAND_NAME);
			}
			return false;
		}

		List<String> tableLines = new ArrayList<>();
		for (int line = tableRange.firstLine; line <= tableRange.lastLine; line++) {
			tableLines.add(getLineText(document, line));
		}

		int row = currentLine - tableRange.firstLine;
		int columnInLine = editor.getCaretModel().getOffset() - document.getLineStartOffset(currentLine);
		int column = MarkdownTableCore.columnFromCursor(currentLineText, columnInLine);
		MarkdownTableCore.EditResult edit = MarkdownTableCore.apply(tableLines, row, column, action);
		if (!edit.ok) {
			if (!quiet) {
				Messages.showInfoMessage(project, "Could not edit the Markdown table.", COMMAND_NAME);
			}
			return false;
		}

		int replaceStart = document.getLineStartOffset(tableRange.firstLine);
		int replaceEnd = document.getLineEndOffset(tableRange.lastLine);
		String eol = chooseEol(document, tableRange.firstLine, tableRange.lastLine);
		String replacement = String.join(eol, edit.lines);
		int targetOffset = positionForLineColumn(replaceStart, edit.lines, eol, edit.targetRow, edit.targetColumnOffset);

		Runnable change = () -> {
			document.replaceString(replaceStart, replaceEnd, replacement);
			int safeTargetOffset = Math.min(Math.max(targetOffset, 0), document.getTextLength());
			editor.getCaretModel().moveToOffset(safeTargetOffset);
			editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
		};

		commandRunner.run(project, COMMAND_NAME, change);

		return true;
	}

	public static boolean isInsidePotentialTable(Editor editor) {
		if (editor == null || editor.isViewer()) {
			return false;
		}

		Document document = editor.getDocument();
		if (document.getLineCount() == 0 || document.getTextLength() == 0) {
			return false;
		}

		int currentOffset = Math.min(editor.getCaretModel().getOffset(), document.getTextLength() - 1);
		int currentLine = document.getLineNumber(currentOffset);
		if (currentLine >= document.getLineCount()) {
			currentLine = document.getLineCount() - 1;
		}

		return findTableLineRange(document, currentLine) != null;
	}

	public static boolean convertDelimited(Editor editor, Project project, boolean quiet) {
		if (editor == null || editor.isViewer()) {
			return false;
		}

		Document document = editor.getDocument();
		if (!document.isWritable()) {
			if (!quiet) {
				document.fireReadOnlyModificationAttempt();
			}
			return false;
		}

		Range range = selectedOrCurrentBlock(editor);
		if (range.start >= range.end) {
			if (!quiet) {
				Messages.showInfoMessage(project, "Select CSV/TSV text or put the caret inside a CSV/TSV block first.", COMMAND_NAME);
			}
			return false;
		}

		String source = document.getImmutableCharSequence().subSequence(range.start, range.end).toString();
		MarkdownTableCore.EditResult edit = MarkdownTableCore.fromDelimited(source);
		if (!edit.ok) {
			if (!quiet) {
				Messages.showInfoMessage(project, "Could not convert the selected CSV/TSV text.", COMMAND_NAME);
			}
			return false;
		}

		String eol = chooseEol(source);
		String replacement = String.join(eol, edit.lines);
		replaceRange(editor, project, range.start, range.end, replacement, range.start + edit.targetColumnOffset);
		return true;
	}

	public static boolean insertTable(Editor editor, Project project, int columns, int dataRows) {
		if (editor == null || editor.isViewer()) {
			return false;
		}

		Document document = editor.getDocument();
		if (!document.isWritable()) {
			document.fireReadOnlyModificationAttempt();
			return false;
		}

		MarkdownTableCore.EditResult edit = MarkdownTableCore.newTable(columns, dataRows);
		if (!edit.ok) {
			Messages.showInfoMessage(project, "Could not create a Markdown table.", COMMAND_NAME);
			return false;
		}

		SelectionModel selection = editor.getSelectionModel();
		int start;
		int end;
		if (selection.hasSelection()) {
			start = selection.getSelectionStart();
			end = selection.getSelectionEnd();
		} else {
			start = editor.getCaretModel().getOffset();
			end = start;
		}

		String eol = chooseEol(document, Math.max(0, document.getLineNumber(Math.min(start, Math.max(document.getTextLength() - 1, 0)))), document.getLineCount() - 1);
		InsertText insertText = tableInsertText(document, start, end, String.join(eol, edit.lines), eol);
		replaceRange(editor, project, start, end, insertText.text, start + insertText.caretDelta + edit.targetColumnOffset);
		return true;
	}

	private static String getLineText(Document document, int line) {
		int start = document.getLineStartOffset(line);
		int end = document.getLineEndOffset(line);
		return document.getImmutableCharSequence().subSequence(start, end).toString();
	}

	private static LineRange findTableLineRange(Document document, int currentLine) {
		if (!MarkdownTableCore.isPotentialTableLine(getLineText(document, currentLine))) {
			return null;
		}

		int firstLine = currentLine;
		while (firstLine > 0 && MarkdownTableCore.isPotentialTableLine(getLineText(document, firstLine - 1))) {
			firstLine--;
		}

		int lastLine = currentLine;
		while (lastLine + 1 < document.getLineCount() && MarkdownTableCore.isPotentialTableLine(getLineText(document, lastLine + 1))) {
			lastLine++;
		}

		List<String> candidateLines = new ArrayList<>();
		for (int line = firstLine; line <= lastLine; line++) {
			candidateLines.add(getLineText(document, line));
		}

		MarkdownTableCore.TableRange tableRange = MarkdownTableCore.findTableRange(candidateLines, currentLine - firstLine);
		if (!tableRange.found) {
			return null;
		}
		return new LineRange(firstLine + tableRange.firstRow, firstLine + tableRange.lastRow);
	}

	private static Range selectedOrCurrentBlock(Editor editor) {
		SelectionModel selection = editor.getSelectionModel();
		if (selection.hasSelection()) {
			return new Range(selection.getSelectionStart(), selection.getSelectionEnd());
		}

		Document document = editor.getDocument();
		if (document.getTextLength() == 0) {
			return new Range(0, 0);
		}

		int currentOffset = Math.min(editor.getCaretModel().getOffset(), Math.max(document.getTextLength() - 1, 0));
		int currentLine = document.getLineNumber(currentOffset);
		int firstLine = currentLine;
		while (firstLine > 0 && !getLineText(document, firstLine - 1).isBlank()) {
			firstLine--;
		}

		int lastLine = currentLine;
		while (lastLine + 1 < document.getLineCount() && !getLineText(document, lastLine + 1).isBlank()) {
			lastLine++;
		}

		return new Range(document.getLineStartOffset(firstLine), document.getLineEndOffset(lastLine));
	}

	private static String chooseEol(Document document, int firstLine, int lastLine) {
		for (int line = firstLine; line <= lastLine && line + 1 < document.getLineCount(); line++) {
			String separator = separatorAfterLine(document, line);
			if (!separator.isEmpty()) {
				return separator;
			}
		}
		return "\n";
	}

	private static String chooseEol(String text) {
		int crlf = text.indexOf("\r\n");
		if (crlf >= 0) {
			return "\r\n";
		}
		int cr = text.indexOf('\r');
		if (cr >= 0) {
			return "\r";
		}
		return "\n";
	}

	private static String separatorAfterLine(Document document, int line) {
		if (line + 1 >= document.getLineCount()) {
			return "";
		}
		int end = document.getLineEndOffset(line);
		int nextStart = document.getLineStartOffset(line + 1);
		if (nextStart <= end) {
			return "";
		}
		return document.getImmutableCharSequence().subSequence(end, nextStart).toString();
	}

	private static int positionForLineColumn(int firstLineStartOffset, List<String> replacementLines, String eol, int row, int columnOffset) {
		int position = firstLineStartOffset;
		for (int i = 0; i < row && i < replacementLines.size(); i++) {
			position += replacementLines.get(i).length() + eol.length();
		}
		return position + columnOffset;
	}

	private static void replaceRange(Editor editor, Project project, int start, int end, String replacement, int targetOffset) {
		Document document = editor.getDocument();
		Runnable change = () -> {
			document.replaceString(start, end, replacement);
			editor.getSelectionModel().removeSelection();
			int safeTargetOffset = Math.min(Math.max(targetOffset, 0), document.getTextLength());
			editor.getCaretModel().moveToOffset(safeTargetOffset);
			editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
		};

		commandRunner.run(project, COMMAND_NAME, change);
	}

	private static void runWriteCommand(Project project, String name, Runnable change) {
		if (project != null) {
			WriteCommandAction.runWriteCommandAction(project, name, null, change);
		} else {
			CommandProcessor.getInstance().executeCommand(null, () -> ApplicationManager.getApplication().runWriteAction(change), name, null);
		}
	}

	private static InsertText tableInsertText(Document document, int start, int end, String table, String eol) {
		StringBuilder text = new StringBuilder();
		int caretDelta = 0;
		if (start > 0 && !isLineStart(document, start)) {
			text.append(eol);
			caretDelta += eol.length();
		}
		text.append(table);
		if (end < document.getTextLength() && !isLineEnd(document, end)) {
			text.append(eol);
		}
		return new InsertText(text.toString(), caretDelta);
	}

	private static boolean isLineStart(Document document, int offset) {
		if (offset <= 0) {
			return true;
		}
		int safeOffset = Math.min(offset, Math.max(document.getTextLength() - 1, 0));
		return document.getLineStartOffset(document.getLineNumber(safeOffset)) == offset;
	}

	private static boolean isLineEnd(Document document, int offset) {
		if (offset >= document.getTextLength()) {
			return true;
		}
		int safeOffset = Math.min(offset, Math.max(document.getTextLength() - 1, 0));
		return document.getLineEndOffset(document.getLineNumber(safeOffset)) == offset;
	}

	private record Range(int start, int end) {
	}

	private record LineRange(int firstLine, int lastLine) {
	}

	private record InsertText(String text, int caretDelta) {
	}
}
