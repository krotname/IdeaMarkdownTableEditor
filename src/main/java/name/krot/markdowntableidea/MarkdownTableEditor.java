package name.krot.markdowntableidea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import name.krot.markdowntableidea.core.MarkdownTableCore;

import java.util.ArrayList;
import java.util.List;

public final class MarkdownTableEditor {
	private static final String COMMAND_NAME = "Markdown Table Editor";

	private MarkdownTableEditor() {
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

		int firstLine = currentLine;
		while (firstLine > 0 && MarkdownTableCore.isPotentialTableLine(getLineText(document, firstLine - 1))) {
			firstLine--;
		}

		int lastLine = currentLine;
		while (lastLine + 1 < document.getLineCount() && MarkdownTableCore.isPotentialTableLine(getLineText(document, lastLine + 1))) {
			lastLine++;
		}

		List<String> tableLines = new ArrayList<>();
		for (int line = firstLine; line <= lastLine; line++) {
			tableLines.add(getLineText(document, line));
		}

		int row = currentLine - firstLine;
		int columnInLine = editor.getCaretModel().getOffset() - document.getLineStartOffset(currentLine);
		int column = MarkdownTableCore.columnFromCursor(currentLineText, columnInLine);
		MarkdownTableCore.EditResult edit = MarkdownTableCore.apply(tableLines, row, column, action);
		if (!edit.ok) {
			if (!quiet) {
				Messages.showInfoMessage(project, "Could not edit the Markdown table.", COMMAND_NAME);
			}
			return false;
		}

		int replaceStart = document.getLineStartOffset(firstLine);
		int replaceEnd = document.getLineEndOffset(lastLine);
		String eol = chooseEol(document, firstLine, lastLine);
		String replacement = String.join(eol, edit.lines);
		int targetOffset = positionForLineColumn(replaceStart, edit.lines, eol, edit.targetRow, edit.targetColumnOffset);

		Runnable change = () -> {
			document.replaceString(replaceStart, replaceEnd, replacement);
			int safeTargetOffset = Math.min(Math.max(targetOffset, 0), document.getTextLength());
			editor.getCaretModel().moveToOffset(safeTargetOffset);
			editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
		};

		if (project != null) {
			WriteCommandAction.runWriteCommandAction(project, COMMAND_NAME, null, change);
		} else {
			ApplicationManager.getApplication().runWriteAction(change);
		}

		return true;
	}

	private static String getLineText(Document document, int line) {
		int start = document.getLineStartOffset(line);
		int end = document.getLineEndOffset(line);
		return document.getImmutableCharSequence().subSequence(start, end).toString();
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
}
