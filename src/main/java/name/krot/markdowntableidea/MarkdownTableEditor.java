// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import name.krot.markdowntableidea.core.MarkdownTableCore;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class MarkdownTableEditor {
	private static final int DEFAULT_VISIBLE_COLUMNS = 120;
	private static final int VISIBLE_WIDTH_MARGIN_COLUMNS = 2;
	private static final Map<Editor, Integer> visibleEditorWidths = Collections.synchronizedMap(new IdentityHashMap<>());
	private static final Map<Editor, Timer> visibleEditorTimers = Collections.synchronizedMap(new IdentityHashMap<>());
	private static final Map<Editor, Timer> autoEditorTimers = Collections.synchronizedMap(new IdentityHashMap<>());
	private static final Map<Document, Timer> autoDocumentTimers = Collections.synchronizedMap(new IdentityHashMap<>());
	private static CommandRunner commandRunner = MarkdownTableEditor::runWriteCommand;
	private static int pluginEditDepth;
	private static boolean autoFormatInProgress;

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
		return runTableEdit(editor, project, action, quiet, false, CaretPlacement.ACTION_TARGET);
	}

	public static boolean fitToEditorWidth(Editor editor, Project project, boolean quiet) {
		return runTableEdit(editor, project, MarkdownTableCore.Action.ALIGN, quiet, true, CaretPlacement.ACTION_TARGET);
	}

	static boolean autoFormatEditorForTests(Editor editor, Project project, boolean markdownFile) {
		return autoFormatEditor(editor, project, markdownFile);
	}

	static boolean shouldRunFitAfterWidthChange(
		boolean enabled,
		boolean inProgress,
		boolean activeEditor,
		int previousColumns,
		int currentColumns
	) {
		return enabled && !inProgress && activeEditor && previousColumns > 0 && currentColumns > 0 && previousColumns != currentColumns;
	}

	static int availableDisplayColumnsForTests(Editor editor, Rectangle visibleArea) {
		return availableDisplayColumns(editor, visibleArea);
	}

	static boolean hasPendingAutoFormatForTests(Editor editor) {
		return editor != null && (visibleEditorTimers.containsKey(editor) ||
			autoEditorTimers.containsKey(editor) ||
			autoDocumentTimers.containsKey(editor.getDocument()));
	}

	static boolean handleVisibleAreaChangedForTests(Editor editor, Rectangle visibleArea, boolean markdownFile) {
		return handleVisibleAreaChanged(editor, visibleArea, markdownFile);
	}

	private static boolean runTableEdit(
		Editor editor,
		Project project,
		MarkdownTableCore.Action action,
		boolean quiet,
		boolean fitToEditorWidth,
		CaretPlacement caretPlacement
	) {
		if (editor == null || editor.isDisposed() || editor.isViewer() || editor.getCaretModel().getCaretCount() != 1) {
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

		int currentOffset = safeOffset(document, editor.getCaretModel().getOffset());
		int currentLine = lineNumberAtOffset(document, currentOffset);

		String currentLineText = getLineText(document, currentLine);
		if (!MarkdownTableCore.isPotentialTableLine(currentLineText)) {
			if (!quiet) {
				Messages.showInfoMessage(project, MarkdownTableEditorBundle.message("message.putCaretInsideTable"), commandName());
			}
			return false;
		}

		LineRange tableRange = findTableLineRange(document, currentLine);
		if (tableRange == null) {
			if (!quiet) {
				Messages.showInfoMessage(project, MarkdownTableEditorBundle.message("message.couldNotEditTable"), commandName());
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
		CellCaretSnapshot preservedCaret = caretPlacement == CaretPlacement.PRESERVE_CELL_OFFSET
			? captureLogicalCellCaret(tableLines, row, column, columnInLine)
			: new CellCaretSnapshot();
		MarkdownTableCore.EditResult edit = fitToEditorWidth
			? MarkdownTableCore.applyWrappedToWidth(tableLines, row, column, availableDisplayColumns(editor, null))
			: MarkdownTableCore.apply(tableLines, row, column, action);
		if (!edit.ok) {
			if (!quiet) {
				Messages.showInfoMessage(project, MarkdownTableEditorBundle.message("message.couldNotEditTable"), commandName());
			}
			return false;
		}

		if (caretPlacement == CaretPlacement.PRESERVE_CELL_OFFSET && preservedCaret.valid) {
			edit.lines = new ArrayList<>(edit.lines);
			ensureTrailingCellCaretSpaces(edit.lines, preservedCaret);
			CellCaretPosition preservedPosition = cellCaretPositionInLines(edit.lines, preservedCaret);
			if (preservedPosition.found) {
				edit.targetRow = preservedPosition.row;
				edit.targetColumnOffset = preservedPosition.columnOffset;
			}
		}

		int replaceStart = document.getLineStartOffset(tableRange.firstLine);
		int replaceEnd = document.getLineEndOffset(tableRange.lastLine);
		String eol = chooseEol(document, tableRange.firstLine, tableRange.lastLine);
		String replacement = String.join(eol, edit.lines);
		int targetOffset = positionForLineColumn(replaceStart, edit.lines, eol, edit.targetRow, edit.targetColumnOffset);
		String original = document.getImmutableCharSequence().subSequence(replaceStart, replaceEnd).toString();
		int safeTargetOffset = Math.min(Math.max(targetOffset, 0), document.getTextLength());
		if (replacement.equals(original) && safeTargetOffset == currentOffset) {
			return false;
		}

		Runnable change = () -> runPluginEdit(editor, () -> {
			if (!replacement.equals(original)) {
				replaceDocumentText(document, replaceStart, replaceEnd, replacement);
			}
			int target = Math.min(Math.max(targetOffset, 0), document.getTextLength());
			editor.getCaretModel().moveToOffset(target);
			editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
		});

		commandRunner.run(project, commandName(), change);

		return true;
	}

	public static boolean formatAllTables(Document document) {
		return formatTablesInRange(document, 0, document == null ? 0 : document.getTextLength());
	}

	static boolean formatTablesInRange(Document document, int startOffset, int endOffset) {
		if (document == null || !document.isWritable() || document.getLineCount() == 0) {
			return false;
		}
		int rangeStart = safeOffset(document, Math.min(startOffset, endOffset));
		int rangeEnd = safeOffset(document, Math.max(startOffset, endOffset));

		List<TableReplacement> replacements = new ArrayList<>();
		for (LineRange tableRange : findAllTableLineRanges(document)) {
			int replaceStart = document.getLineStartOffset(tableRange.firstLine);
			int replaceEnd = document.getLineEndOffset(tableRange.lastLine);
			if (!intersectsRange(rangeStart, rangeEnd, replaceStart, replaceEnd)) {
				continue;
			}

			List<String> tableLines = new ArrayList<>();
			for (int line = tableRange.firstLine; line <= tableRange.lastLine; line++) {
				tableLines.add(getLineText(document, line));
			}

			MarkdownTableCore.EditResult edit = MarkdownTableCore.apply(tableLines, 0, 0, MarkdownTableCore.Action.ALIGN);
			if (!edit.ok) {
				continue;
			}

			String eol = chooseEol(document, tableRange.firstLine, tableRange.lastLine);
			String replacement = String.join(eol, edit.lines);
			String original = document.getImmutableCharSequence().subSequence(replaceStart, replaceEnd).toString();
			if (!replacement.equals(original)) {
				replacements.add(new TableReplacement(replaceStart, replaceEnd, replacement));
			}
		}

		for (int i = replacements.size() - 1; i >= 0; i--) {
			TableReplacement replacement = replacements.get(i);
			replaceDocumentText(document, replacement.start(), replacement.end(), replacement.text());
		}
		return !replacements.isEmpty();
	}

	private static boolean intersectsRange(int rangeStart, int rangeEnd, int tableStart, int tableEnd) {
		if (rangeStart == rangeEnd) {
			return rangeStart >= tableStart && rangeStart <= tableEnd;
		}
		return rangeStart < tableEnd && rangeEnd > tableStart;
	}

	public static boolean isInsidePotentialTable(Editor editor) {
		if (editor == null || editor.isDisposed() || editor.isViewer() || editor.getCaretModel().getCaretCount() != 1) {
			return false;
		}

		Document document = editor.getDocument();
		if (document.getLineCount() == 0 || document.getTextLength() == 0) {
			return false;
		}

		int currentOffset = safeOffset(document, editor.getCaretModel().getOffset());
		int currentLine = lineNumberAtOffset(document, currentOffset);

		return findTableLineRange(document, currentLine) != null;
	}

	public static boolean autoFormatChangedDocument(Document document) {
		if (document == null || pluginEditDepth > 0 || autoFormatInProgress) {
			return false;
		}

		Editor[] editors = EditorFactory.getInstance().getEditors(document);
		for (Editor editor : editors) {
			if (!isMarkdownEditor(editor)) {
				continue;
			}
			if (autoFormatEditor(editor, editor.getProject(), true)) {
				return true;
			}
		}
		return false;
	}

	public static void scheduleAutoFormatChangedDocument(Document document) {
		MarkdownTableSettings settings = MarkdownTableSettings.getInstance();
		if (document == null || pluginEditDepth > 0 || autoFormatInProgress ||
			(!settings.isAutoAlignEnabled() && !settings.isAutoFitEnabled())) {
			return;
		}
		Application application = ApplicationManager.getApplication();
		if (application == null) {
			return;
		}
		VirtualFile file = FileDocumentManager.getInstance().getFile(document);
		if (file == null || !isMarkdownFileName(file.getName())) {
			return;
		}

		Timer previous = autoDocumentTimers.remove(document);
		if (previous != null) {
			previous.stop();
		}

		Timer timer = new Timer(settings.getDebounceMs(), null);
		timer.addActionListener(event -> application.invokeLater(() -> {
			if (autoDocumentTimers.remove(document, timer)) {
				autoFormatChangedDocument(document);
			}
		}, ModalityState.defaultModalityState()));
		timer.setRepeats(false);
		autoDocumentTimers.put(document, timer);
		timer.start();
	}

	public static void scheduleAutoFormatEditor(Editor editor, Project project, int delayMs) {
		MarkdownTableSettings settings = MarkdownTableSettings.getInstance();
		if (editor == null || editor.isDisposed() || pluginEditDepth > 0 || autoFormatInProgress ||
			(!settings.isAutoAlignEnabled() && !settings.isAutoFitEnabled())) {
			return;
		}

		Timer previous = autoEditorTimers.remove(editor);
		if (previous != null) {
			previous.stop();
		}

		Project targetProject = project != null ? project : editor.getProject();
		if (targetProject != null && targetProject.isDisposed()) {
			return;
		}
		Timer timer = new Timer(Math.max(0, delayMs), null);
		timer.addActionListener(event -> ApplicationManager.getApplication().invokeLater(() -> {
			if (autoEditorTimers.remove(editor, timer)) {
				autoFormatEditor(editor, targetProject, isMarkdownEditor(editor));
			}
		}, ModalityState.defaultModalityState()));
		timer.setRepeats(false);
		autoEditorTimers.put(editor, timer);
		timer.start();
	}

	public static void handleSelectedEditorActivated(Project project) {
		if (project == null || project.isDisposed() || project.isDefault()) {
			return;
		}

		Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
		if (editor == null || editor.isDisposed()) {
			return;
		}

		rememberVisibleEditorWidth(editor);
		scheduleAutoFormatEditor(editor, project, 0);
	}

	public static void handleEditorMetricsChanged(Project project) {
		if (project == null || project.isDisposed()) {
			return;
		}
		for (Editor editor : projectEditors(project)) {
			handleVisibleAreaChanged(editor, null);
		}
	}

	public static void scheduleAutoFormatFile(Project project, VirtualFile file) {
		if (project == null || project.isDisposed() || file == null || !isMarkdownFileName(file.getName())) {
			return;
		}

		Document document = FileDocumentManager.getInstance().getCachedDocument(file);
		if (document != null) {
			scheduleAutoFormatChangedDocument(document);
			return;
		}

		handleSelectedEditorActivated(project);
	}

	public static void rememberVisibleEditorWidth(Editor editor) {
		if (editor == null || editor.isDisposed()) {
			return;
		}

		int columns = availableDisplayColumns(editor, null);
		if (columns > 0) {
			visibleEditorWidths.put(editor, columns);
		}
	}

	public static void forgetVisibleEditorWidth(Editor editor) {
		if (editor == null) {
			return;
		}

		visibleEditorWidths.remove(editor);
		Timer timer = visibleEditorTimers.remove(editor);
		if (timer != null) {
			timer.stop();
		}
		Timer autoTimer = autoEditorTimers.remove(editor);
		if (autoTimer != null) {
			autoTimer.stop();
		}
	}

	public static boolean handleVisibleAreaChanged(Editor editor, Rectangle visibleArea) {
		return handleVisibleAreaChanged(editor, visibleArea, isMarkdownEditor(editor));
	}

	private static boolean handleVisibleAreaChanged(Editor editor, Rectangle visibleArea, boolean markdownFile) {
		if (editor == null || editor.isDisposed()) {
			return false;
		}
		MarkdownTableSettings settings = MarkdownTableSettings.getInstance();
		int columns = availableDisplayColumns(editor, visibleArea);
		Integer previous = visibleEditorWidths.get(editor);
		if (previous == null || previous <= 0) {
			visibleEditorWidths.put(editor, columns);
			return false;
		}

		visibleEditorWidths.put(editor, columns);
		if (!shouldRunFitAfterWidthChange(
			settings.isAutoFitEnabled(),
			autoFormatInProgress,
			isAutomaticEditorCandidate(editor, markdownFile),
			previous,
			columns
		)) {
			return false;
		}

		scheduleVisibleWidthFit(editor, settings.getDebounceMs());
		return true;
	}

	private static boolean autoFormatEditor(Editor editor, Project project, boolean markdownFile) {
		if (project != null && project.isDisposed()) {
			return false;
		}
		MarkdownTableSettings settings = MarkdownTableSettings.getInstance();
		if (!settings.isAutoAlignEnabled() && !settings.isAutoFitEnabled()) {
			return false;
		}
		if (!isAutomaticEditorCandidate(editor, markdownFile)) {
			return false;
		}

		autoFormatInProgress = true;
		try {
			if (settings.isAutoFitEnabled()) {
				return runTableEdit(editor, project, MarkdownTableCore.Action.ALIGN, true, true, CaretPlacement.PRESERVE_CELL_OFFSET);
			}
			return runTableEdit(editor, project, MarkdownTableCore.Action.ALIGN, true, false, CaretPlacement.PRESERVE_CELL_OFFSET);
		} finally {
			autoFormatInProgress = false;
		}
	}

	public static boolean convertDelimited(Editor editor, Project project, boolean quiet) {
		if (editor == null || editor.isDisposed() || editor.isViewer() || editor.getCaretModel().getCaretCount() != 1) {
			return false;
		}

		Document document = editor.getDocument();
		if (!document.isWritable()) {
			if (!quiet) {
				document.fireReadOnlyModificationAttempt();
			}
			return false;
		}

		Range range = selectedOrCurrentDelimitedBlock(editor);
		if (range.start >= range.end) {
			if (!quiet) {
				Messages.showInfoMessage(project, MarkdownTableEditorBundle.message("message.selectCsvTsv"), commandName());
			}
			return false;
		}

		String source = document.getImmutableCharSequence().subSequence(range.start, range.end).toString();
		MarkdownTableCore.EditResult edit = MarkdownTableCore.fromDelimited(source);
		if (!edit.ok) {
			if (!quiet) {
				Messages.showInfoMessage(project, MarkdownTableEditorBundle.message("message.couldNotConvertCsvTsv"), commandName());
			}
			return false;
		}

		String eol = chooseEol(source, chooseEolForRange(document, range.start, range.end));
		String replacement = String.join(eol, edit.lines);
		replaceRange(editor, project, range.start, range.end, replacement, range.start + edit.targetColumnOffset);
		return true;
	}

	public static boolean insertTable(Editor editor, Project project, int columns, int dataRows) {
		if (editor == null || editor.isDisposed() || editor.isViewer() || editor.getCaretModel().getCaretCount() != 1) {
			return false;
		}

		Document document = editor.getDocument();
		if (!document.isWritable()) {
			document.fireReadOnlyModificationAttempt();
			return false;
		}

		MarkdownTableCore.EditResult edit = MarkdownTableCore.newTable(columns, dataRows);
		if (!edit.ok) {
			Messages.showInfoMessage(project, MarkdownTableEditorBundle.message("message.couldNotCreateTable"), commandName());
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

		String eol = chooseEol(document, lineNumberAtOffset(document, start), document.getLineCount() - 1);
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

	private static List<LineRange> findAllTableLineRanges(Document document) {
		List<LineRange> ranges = new ArrayList<>();
		List<String> lines = new ArrayList<>(document.getLineCount());
		for (int line = 0; line < document.getLineCount(); line++) {
			lines.add(getLineText(document, line));
		}
		for (MarkdownTableCore.TableRange range : MarkdownTableCore.findTableRanges(lines)) {
			ranges.add(new LineRange(range.firstRow, range.lastRow));
		}
		return ranges;
	}

	private static Range selectedOrCurrentDelimitedBlock(Editor editor) {
		SelectionModel selection = editor.getSelectionModel();
		if (selection.hasSelection()) {
			return new Range(selection.getSelectionStart(), selection.getSelectionEnd());
		}

		Document document = editor.getDocument();
		if (document.getTextLength() == 0) {
			return new Range(0, 0);
		}

		int currentOffset = safeOffset(document, editor.getCaretModel().getOffset());
		int currentLine = lineNumberAtOffset(document, currentOffset);
		int blockStart = -1;
		boolean inQuotes = false;
		for (int line = 0; line <= document.getLineCount(); line++) {
			DelimitedLineScan scan = line < document.getLineCount()
				? scanDelimitedLine(getLineText(document, line), inQuotes)
				: new DelimitedLineScan(false, false);

			if (blockStart == -1) {
				if (scan.hasDelimiter) {
					blockStart = line;
					inQuotes = scan.inQuotes;
				}
				continue;
			}

			if (inQuotes || scan.hasDelimiter) {
				inQuotes = scan.inQuotes;
				continue;
			}

			int blockEnd = line - 1;
			if (currentLine >= blockStart && currentLine <= blockEnd && blockEnd > blockStart) {
				return new Range(document.getLineStartOffset(blockStart), document.getLineEndOffset(blockEnd));
			}
			blockStart = -1;
			inQuotes = false;
		}
		return new Range(0, 0);
	}

	private static DelimitedLineScan scanDelimitedLine(String line, boolean startsInQuotes) {
		boolean inQuotes = startsInQuotes;
		boolean cellBlank = !startsInQuotes;
		boolean hasDelimiter = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (inQuotes) {
				if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					i++;
				} else if (ch == '"') {
					inQuotes = false;
				}
			} else if (ch == '"' && cellBlank) {
				inQuotes = true;
			} else if (ch == ',' || ch == '\t') {
				hasDelimiter = true;
				cellBlank = true;
			} else if (!markdownSpace(ch)) {
				cellBlank = false;
			}
		}
		return new DelimitedLineScan(hasDelimiter, inQuotes);
	}

	private static String chooseEol(Document document, int firstLine, int lastLine) {
		String ranged = chooseEolInRange(document, firstLine, lastLine);
		return ranged.isEmpty() ? chooseDocumentEol(document) : ranged;
	}

	private static String chooseDocumentEol(Document document) {
		String eol = chooseEolInRange(document, 0, document.getLineCount() - 1);
		return eol.isEmpty() ? "\n" : eol;
	}

	private static String chooseEolInRange(Document document, int firstLine, int lastLine) {
		if (document.getLineCount() <= 1) {
			return "";
		}

		int first = Math.max(0, Math.min(firstLine, document.getLineCount() - 1));
		int last = Math.max(0, Math.min(lastLine, document.getLineCount() - 1));
		if (last < first) {
			int swap = first;
			first = last;
			last = swap;
		}

		for (int line = first; line <= last && line + 1 < document.getLineCount(); line++) {
			String separator = separatorAfterLine(document, line);
			if (!separator.isEmpty()) {
				return separator;
			}
		}
		return "";
	}

	private static String chooseEol(String text) {
		return chooseEol(text, "\n");
	}

	private static String chooseEol(String text, String fallback) {
		int crlf = text.indexOf("\r\n");
		if (crlf >= 0) {
			return "\r\n";
		}
		int cr = text.indexOf('\r');
		if (cr >= 0) {
			return "\r";
		}
		int lf = text.indexOf('\n');
		if (lf >= 0) {
			return "\n";
		}
		return fallback;
	}

	private static String chooseEolForRange(Document document, int start, int end) {
		int firstLine = lineNumberAtOffset(document, start);
		int effectiveEnd = start == end ? end : Math.max(start, end - 1);
		int lastLine = lineNumberAtOffset(document, effectiveEnd);
		return chooseEol(document, firstLine, lastLine);
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
		Runnable change = () -> runPluginEdit(editor, () -> {
			replaceDocumentText(document, start, end, replacement);
			editor.getSelectionModel().removeSelection();
			int safeTargetOffset = Math.min(Math.max(targetOffset, 0), document.getTextLength());
			editor.getCaretModel().moveToOffset(safeTargetOffset);
			editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
		});

		commandRunner.run(project, commandName(), change);
	}

	private static void runPluginEdit(Editor editor, Runnable change) {
		pluginEditDepth++;
		cancelPendingAutoFormat(editor);
		try {
			change.run();
		} finally {
			pluginEditDepth--;
		}
	}

	private static void replaceDocumentText(Document document, int start, int end, String replacement) {
		pluginEditDepth++;
		try {
			document.replaceString(start, end, replacement);
		} finally {
			pluginEditDepth--;
		}
	}

	private static void cancelPendingAutoFormat(Editor editor) {
		if (editor == null) {
			return;
		}
		stopTimer(visibleEditorTimers.remove(editor));
		stopTimer(autoEditorTimers.remove(editor));
		stopTimer(autoDocumentTimers.remove(editor.getDocument()));
	}

	private static void stopTimer(Timer timer) {
		if (timer != null) {
			timer.stop();
		}
	}

	private static void scheduleVisibleWidthFit(Editor editor, int delayMs) {
		if (editor == null || editor.isDisposed() || pluginEditDepth > 0 || autoFormatInProgress) {
			return;
		}

		Timer previous = visibleEditorTimers.remove(editor);
		if (previous != null) {
			previous.stop();
		}

		Timer timer = new Timer(Math.max(0, delayMs), null);
		timer.addActionListener(event -> ApplicationManager.getApplication().invokeLater(() -> {
			if (visibleEditorTimers.remove(editor, timer)) {
				autoFormatEditor(editor, editor.getProject(), isMarkdownEditor(editor));
			}
		}, ModalityState.defaultModalityState()));
		timer.setRepeats(false);
		visibleEditorTimers.put(editor, timer);
		timer.start();
	}

	private static boolean isAutomaticEditorCandidate(Editor editor, boolean markdownFile) {
		if (editor == null || editor.isDisposed() || editor.isViewer() || !markdownFile || pluginEditDepth > 0 || autoFormatInProgress) {
			return false;
		}

		Document document = editor.getDocument();
		if (document == null || !document.isWritable() || document.getLineCount() == 0) {
			return false;
		}
		if (editor.getSelectionModel().hasSelection() || editor.getCaretModel().getCaretCount() != 1) {
			return false;
		}
		if (!isActiveVisibleEditor(editor)) {
			return false;
		}
		return isInsidePotentialTable(editor);
	}

	private static boolean isActiveVisibleEditor(Editor editor) {
		JComponent component = editor.getContentComponent();
		if (component == null) {
			return true;
		}
		return component.isShowing();
	}

	private static List<Editor> projectEditors(Project project) {
		List<Editor> result = new ArrayList<>();
		for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
			if (editor == null || editor.isDisposed()) {
				continue;
			}
			Project editorProject = editor.getProject();
			if (project == null ? editorProject == null : project.equals(editorProject)) {
				result.add(editor);
			}
		}
		return result;
	}

	static boolean isMarkdownEditor(Editor editor) {
		if (editor == null || editor.isDisposed()) {
			return false;
		}
		VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
		return file != null && isMarkdownFileName(file.getName());
	}

	static boolean isMarkdownFileName(String name) {
		if (name == null) {
			return false;
		}
		String lower = name.toLowerCase(java.util.Locale.ROOT);
		return lower.endsWith(".md") ||
			lower.endsWith(".markdown") ||
			lower.endsWith(".mdown") ||
			lower.endsWith(".mkd") ||
			lower.endsWith(".mkdn");
	}

	private static int availableDisplayColumns(Editor editor, Rectangle visibleArea) {
		if (editor == null || editor.isDisposed()) {
			return DEFAULT_VISIBLE_COLUMNS;
		}

		int visibleWidth = visibleArea == null ? 0 : visibleArea.width;
		JComponent component = editor.getContentComponent();
		if (visibleWidth <= 0 && component != null) {
			visibleWidth = component.getVisibleRect().width;
		}
		if (visibleWidth <= 0) {
			visibleWidth = editor.getScrollingModel().getVisibleArea().width;
		}
		if (visibleWidth <= 0 && component != null) {
			visibleWidth = component.getWidth();
		}
		if (visibleWidth <= 0) {
			return DEFAULT_VISIBLE_COLUMNS;
		}

		int spaceWidth = 1;
		try {
			spaceWidth = Math.max(1, EditorUtil.getPlainSpaceWidth(editor));
		} catch (RuntimeException ignored) {
			spaceWidth = 1;
		}

		return Math.max(1, (visibleWidth / spaceWidth) - VISIBLE_WIDTH_MARGIN_COLUMNS);
	}

	private static void runWriteCommand(Project project, String name, Runnable change) {
		if (project != null) {
			WriteCommandAction.runWriteCommandAction(project, name, null, change);
		} else {
			CommandProcessor.getInstance().executeCommand(null, () -> ApplicationManager.getApplication().runWriteAction(change), name, null);
		}
	}

	private static String commandName() {
		return MarkdownTableEditorBundle.message("plugin.name");
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
		int safeOffset = safeOffset(document, offset);
		return document.getLineStartOffset(lineNumberAtOffset(document, safeOffset)) == safeOffset;
	}

	private static boolean isLineEnd(Document document, int offset) {
		if (offset >= document.getTextLength()) {
			return true;
		}
		int safeOffset = Math.min(offset, Math.max(document.getTextLength() - 1, 0));
		return document.getLineEndOffset(document.getLineNumber(safeOffset)) == offset;
	}

	private static int safeOffset(Document document, int offset) {
		return Math.max(0, Math.min(offset, document.getTextLength()));
	}

	private static int lineNumberAtOffset(Document document, int offset) {
		if (document.getLineCount() == 0) {
			return 0;
		}
		int safeOffset = safeOffset(document, offset);
		if (safeOffset == document.getTextLength()) {
			return document.getLineCount() - 1;
		}
		int line = document.getLineNumber(safeOffset);
		return Math.max(0, Math.min(line, document.getLineCount() - 1));
	}

	private static boolean markdownSpace(char ch) {
		return ch == ' ' || ch == '\t';
	}

	private static boolean escapedAt(String line, int index) {
		int backslashes = 0;
		while (index > 0 && line.charAt(index - 1) == '\\') {
			backslashes++;
			index--;
		}
		return (backslashes % 2) == 1;
	}

	private static String trimMarkdownSpaces(String text) {
		int first = 0;
		while (first < text.length() && markdownSpace(text.charAt(first))) {
			first++;
		}
		int last = text.length();
		while (last > first && markdownSpace(text.charAt(last - 1))) {
			last--;
		}
		return text.substring(first, last);
	}

	private static String trimLeadingMarkdownSpaces(String text) {
		int first = 0;
		while (first < text.length() && markdownSpace(text.charAt(first))) {
			first++;
		}
		return text.substring(first);
	}

	private static String withoutMarkdownSpaces(String text) {
		StringBuilder compact = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			if (!markdownSpace(text.charAt(i))) {
				compact.append(text.charAt(i));
			}
		}
		return compact.toString();
	}

	private static int trailingMarkdownSpaceCount(String text) {
		int count = 0;
		for (int i = text.length(); i > 0 && markdownSpace(text.charAt(i - 1)); i--) {
			count++;
		}
		return count;
	}

	private static String withoutTrailingMarkdownSpaces(String text) {
		int trailing = trailingMarkdownSpaceCount(text);
		return trailing == 0 ? text : text.substring(0, text.length() - trailing);
	}

	private static int nextCharOffset(String text, int offset) {
		if (offset >= text.length()) {
			return text.length();
		}
		return offset + Character.charCount(text.codePointAt(offset));
	}

	private static int originalOffsetForCompactPrefix(String text, int compactChars) {
		int offset = 0;
		int seen = 0;
		while (offset < text.length() && seen < compactChars) {
			if (markdownSpace(text.charAt(offset))) {
				offset++;
				continue;
			}
			int next = nextCharOffset(text, offset);
			seen += next - offset;
			offset = next;
		}
		return offset;
	}

	private static int simpleDisplayWidth(String text) {
		int width = 0;
		for (int offset = 0; offset < text.length(); offset = nextCharOffset(text, offset)) {
			width++;
		}
		return width;
	}

	private static boolean isSeparatorCell(String cell) {
		String value = trimMarkdownSpaces(cell);
		boolean hasDash = false;
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (ch == '-') {
				hasDash = true;
			} else if (ch != ':') {
				return false;
			}
		}
		return hasDash;
	}

	private static boolean isSeparatorRow(List<String> cells) {
		if (cells.isEmpty()) {
			return false;
		}
		for (String cell : cells) {
			if (!isSeparatorCell(cell)) {
				return false;
			}
		}
		return true;
	}

	private static boolean cellHasText(String cell) {
		return !trimMarkdownSpaces(cell).isEmpty();
	}

	private static int nonEmptyCellCount(List<String> cells) {
		int count = 0;
		for (String cell : cells) {
			if (cellHasText(cell)) {
				count++;
			}
		}
		return count;
	}

	private static boolean isAsciiAlphaNumeric(int codePoint) {
		return (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z') || (codePoint >= '0' && codePoint <= '9');
	}

	private static boolean isWordContinuationStart(String value) {
		if (value.isEmpty()) {
			return false;
		}
		int codePoint = value.codePointAt(0);
		return isAsciiAlphaNumeric(codePoint) || codePoint == '_' || codePoint >= 0x80;
	}

	private static boolean isWordContinuationEnd(String value) {
		String trimmed = trimMarkdownSpaces(value);
		if (trimmed.isEmpty()) {
			return false;
		}
		int codePoint = trimmed.codePointBefore(trimmed.length());
		return isAsciiAlphaNumeric(codePoint) || codePoint == '_' || codePoint == '-' || codePoint >= 0x80;
	}

	private static String firstToken(String value) {
		int end = 0;
		while (end < value.length() && !markdownSpace(value.charAt(end))) {
			end = nextCharOffset(value, end);
		}
		return value.substring(0, end);
	}

	private static boolean looksLikeSplitWordRemainder(String token) {
		if (token.isEmpty()) {
			return false;
		}
		int width = simpleDisplayWidth(token);
		int first = token.codePointAt(0);
		return first >= 0x80 ? width <= 4 : width <= 2;
	}

	private static boolean shouldJoinContinuationWithoutSpace(String target, String continuation) {
		String targetValue = trimMarkdownSpaces(target);
		String continuationValue = trimMarkdownSpaces(continuation);
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

	private static String appendContinuationCell(String target, String continuation, boolean preserveTrailingSpaces) {
		String value = preserveTrailingSpaces ? trimLeadingMarkdownSpaces(continuation) : trimMarkdownSpaces(continuation);
		if (value.isEmpty()) {
			return target;
		}
		if (!trimMarkdownSpaces(target).isEmpty() && !shouldJoinContinuationWithoutSpace(target, value)) {
			target += " ";
		}
		return target + value;
	}

	private static CellBounds cellBoundsForColumn(String line, int column) {
		CellBounds bounds = new CellBounds();
		int first = 0;
		while (first < line.length() && markdownSpace(line.charAt(first))) {
			first++;
		}

		int last = line.length();
		while (last > first && markdownSpace(line.charAt(last - 1))) {
			last--;
		}

		if (first < last && line.charAt(first) == '|' && !escapedAt(line, first)) {
			first++;
		}
		if (last > first && line.charAt(last - 1) == '|' && !escapedAt(line, last - 1)) {
			last--;
		}

		int cellStart = first;
		int currentColumn = 0;
		for (int i = first; i <= last; i++) {
			boolean atCellEnd = i == last || (line.charAt(i) == '|' && !escapedAt(line, i));
			if (!atCellEnd) {
				continue;
			}

			if (currentColumn == column) {
				int contentStart = cellStart;
				while (contentStart < i && markdownSpace(line.charAt(contentStart))) {
					contentStart++;
				}
				int contentEnd = i;
				while (contentEnd > contentStart && markdownSpace(line.charAt(contentEnd - 1))) {
					contentEnd--;
				}
				bounds.found = true;
				bounds.cellStart = cellStart;
				bounds.cellEnd = i;
				bounds.contentStart = contentStart;
				bounds.contentEnd = contentEnd;
				return bounds;
			}

			currentColumn++;
			cellStart = i + 1;
		}

		return bounds;
	}

	private static String fullCellValueForColumn(String line, int column) {
		CellBounds bounds = cellBoundsForColumn(line, column);
		if (!bounds.found) {
			return "";
		}
		return line.substring(bounds.contentStart, bounds.contentEnd);
	}

	private static String cellPrefixForColumn(String line, int column, int charColumn) {
		CellBounds bounds = cellBoundsForColumn(line, column);
		if (!bounds.found) {
			return "";
		}
		int clampedColumn = Math.min(Math.max(charColumn, 0), line.length());
		if (clampedColumn <= bounds.contentStart) {
			return "";
		}
		return line.substring(bounds.contentStart, Math.min(clampedColumn, bounds.cellEnd));
	}

	private static int tableColumnCount(List<String> lines) {
		int columns = 0;
		for (String line : lines) {
			for (int column = 0;; column++) {
				if (!cellBoundsForColumn(line, column).found) {
					break;
				}
				columns = Math.max(columns, column + 1);
			}
		}
		return columns;
	}

	private static List<String> rowCellsForColumns(String line, int columns) {
		List<String> cells = new ArrayList<>(columns);
		for (int column = 0; column < columns; column++) {
			cells.add(fullCellValueForColumn(line, column));
		}
		return cells;
	}

	private static boolean isLikelyContinuationRow(List<String> row, List<String> baseRow, int columns) {
		if (columns < 2 || row.size() < columns || baseRow.size() < columns) {
			return false;
		}
		int nonEmpty = nonEmptyCellCount(row);
		if (nonEmpty == 0 || nonEmpty == columns) {
			return false;
		}

		int emptyWhereBaseHasText = 0;
		for (int column = 0; column < columns; column++) {
			if (!cellHasText(row.get(column)) && cellHasText(baseRow.get(column))) {
				emptyWhereBaseHasText++;
			}
		}
		return emptyWhereBaseHasText >= Math.max(1, columns / 3);
	}

	private static List<String> appendContinuationCells(List<String> target, List<String> continuation, int columns) {
		List<String> result = new ArrayList<>(target);
		while (result.size() < columns) {
			result.add("");
		}
		for (int column = 0; column < columns && column < continuation.size(); column++) {
			result.set(column, appendContinuationCell(result.get(column), continuation.get(column), false));
		}
		return result;
	}

	private static LogicalRowMap buildLogicalRowMap(List<String> lines) {
		LogicalRowMap map = new LogicalRowMap(lines.size());
		map.columns = tableColumnCount(lines);
		if (map.columns == 0) {
			return map;
		}

		int separatorRow = -1;
		List<List<String>> cellsByRow = new ArrayList<>();
		for (int row = 0; row < lines.size(); row++) {
			List<String> cells = rowCellsForColumns(lines.get(row), map.columns);
			cellsByRow.add(cells);
			if (separatorRow == -1 && isSeparatorRow(cells)) {
				separatorRow = row;
			}
		}

		int baseRow = -1;
		int baseLogicalRow = -1;
		int nextLogicalRow = 0;
		List<String> baseCells = Collections.emptyList();
		for (int row = 0; row < lines.size(); row++) {
			boolean separator = row == separatorRow;
			boolean canBeContinuation = separatorRow != -1 && row > separatorRow && baseRow != -1 && !separator;
			boolean continuation = canBeContinuation && isLikelyContinuationRow(cellsByRow.get(row), baseCells, map.columns);
			if (continuation) {
				map.baseRowForRow[row] = baseRow;
				map.logicalRowForRow[row] = baseLogicalRow;
				baseCells = appendContinuationCells(baseCells, cellsByRow.get(row), map.columns);
				continue;
			}

			baseRow = row;
			baseLogicalRow = nextLogicalRow++;
			baseCells = new ArrayList<>(cellsByRow.get(row));
			map.baseRowForRow[row] = baseRow;
			map.logicalRowForRow[row] = baseLogicalRow;
		}
		return map;
	}

	private static CellCaretSnapshot captureCellCaret(String line, int row, int column, int charColumn) {
		CellCaretSnapshot snapshot = new CellCaretSnapshot();
		CellBounds bounds = cellBoundsForColumn(line, column);
		if (!bounds.found) {
			return snapshot;
		}
		snapshot.valid = true;
		snapshot.row = row;
		snapshot.logicalRow = row;
		snapshot.column = column;
		snapshot.prefix = cellPrefixForColumn(line, column, charColumn);
		snapshot.offset = snapshot.prefix.length();
		return snapshot;
	}

	private static CellCaretSnapshot captureLogicalCellCaret(List<String> lines, int row, int column, int charColumn) {
		if (row < 0 || row >= lines.size()) {
			return new CellCaretSnapshot();
		}

		CellCaretSnapshot snapshot = captureCellCaret(lines.get(row), row, column, charColumn);
		if (!snapshot.valid) {
			return snapshot;
		}

		LogicalRowMap map = buildLogicalRowMap(lines);
		if (row >= map.baseRowForRow.length || map.baseRowForRow[row] == -1) {
			return snapshot;
		}

		int baseRow = map.baseRowForRow[row];
		snapshot.logicalRow = map.logicalRowForRow[row];
		snapshot.prefix = "";
		for (int physicalRow = baseRow; physicalRow <= row && physicalRow < lines.size(); physicalRow++) {
			if (map.baseRowForRow[physicalRow] != baseRow) {
				continue;
			}
			boolean current = physicalRow == row;
			String fragment = current
				? cellPrefixForColumn(lines.get(physicalRow), column, charColumn)
				: fullCellValueForColumn(lines.get(physicalRow), column);
			snapshot.prefix = appendContinuationCell(snapshot.prefix, fragment, current);
		}
		snapshot.offset = snapshot.prefix.length();
		return snapshot;
	}

	private static CellCaretPosition cellCaretPositionInLines(List<String> lines, CellCaretSnapshot snapshot) {
		CellCaretPosition position = new CellCaretPosition();
		if (!snapshot.valid || lines.isEmpty()) {
			return position;
		}

		int trailingSpaces = trailingMarkdownSpaceCount(snapshot.prefix);
		if (trailingSpaces > 0) {
			CellCaretSnapshot visibleSnapshot = snapshot.copy();
			visibleSnapshot.prefix = withoutTrailingMarkdownSpaces(snapshot.prefix);
			visibleSnapshot.offset = visibleSnapshot.prefix.length();
			position = cellCaretPositionInLines(lines, visibleSnapshot);
			if (position.found && position.row < lines.size()) {
				CellBounds bounds = cellBoundsForColumn(lines.get(position.row), snapshot.column);
				if (bounds.found) {
					position.columnOffset = Math.min(position.columnOffset + trailingSpaces, bounds.cellEnd);
				}
			}
			return position;
		}

		LogicalRowMap map = buildLogicalRowMap(lines);
		if (map.logicalRowForRow.length == 0) {
			return position;
		}

		int baseRow = -1;
		for (int row = 0; row < map.logicalRowForRow.length; row++) {
			if (map.logicalRowForRow[row] == snapshot.logicalRow && map.baseRowForRow[row] == row) {
				baseRow = row;
				break;
			}
		}
		if (baseRow == -1) {
			if (snapshot.row >= lines.size()) {
				return position;
			}
			baseRow = snapshot.row;
		}

		String logicalPrefix = "";
		for (int row = baseRow; row < lines.size(); row++) {
			if (row >= map.baseRowForRow.length || map.baseRowForRow[row] != baseRow) {
				break;
			}

			String value = fullCellValueForColumn(lines.get(row), snapshot.column);
			if (value.isEmpty()) {
				continue;
			}

			boolean joinWithoutSpace = trimMarkdownSpaces(logicalPrefix).isEmpty()
				|| shouldJoinContinuationWithoutSpace(logicalPrefix, value);
			String separator = joinWithoutSpace ? "" : " ";
			String candidate = logicalPrefix + separator + value;
			String compactSnapshotPrefix = withoutMarkdownSpaces(snapshot.prefix);
			String compactCandidate = withoutMarkdownSpaces(candidate);
			boolean compactMatch = !compactSnapshotPrefix.isEmpty()
				&& compactSnapshotPrefix.length() <= compactCandidate.length()
				&& compactCandidate.startsWith(compactSnapshotPrefix);

			if (snapshot.prefix.length() <= candidate.length() && candidate.startsWith(snapshot.prefix)) {
				return positionInAddedCellText(lines.get(row), snapshot.column, row, snapshot.prefix.length() - logicalPrefix.length(), separator.length());
			}
			if (compactMatch) {
				int offsetInCandidate = originalOffsetForCompactPrefix(candidate, compactSnapshotPrefix.length());
				if (offsetInCandidate >= logicalPrefix.length()) {
					return positionInAddedCellText(lines.get(row), snapshot.column, row, offsetInCandidate - logicalPrefix.length(), separator.length());
				}
			}

			if (snapshot.prefix.length() >= candidate.length() && snapshot.prefix.startsWith(candidate)) {
				logicalPrefix = candidate;
				continue;
			}
			if (compactSnapshotPrefix.length() >= compactCandidate.length() && compactSnapshotPrefix.startsWith(compactCandidate)) {
				logicalPrefix = candidate;
				continue;
			}
			break;
		}

		int fallbackRow = Math.min(baseRow, lines.size() - 1);
		CellBounds bounds = cellBoundsForColumn(lines.get(fallbackRow), snapshot.column);
		if (!bounds.found) {
			return position;
		}
		position.found = true;
		position.row = fallbackRow;
		position.columnOffset = Math.min(bounds.contentStart + snapshot.offset, bounds.contentEnd);
		return position;
	}

	private static CellCaretPosition positionInAddedCellText(String line, int column, int row, int offsetInAddedText, int separatorLength) {
		CellCaretPosition position = new CellCaretPosition();
		CellBounds bounds = cellBoundsForColumn(line, column);
		if (!bounds.found) {
			return position;
		}

		int offsetInValue = offsetInAddedText > separatorLength ? offsetInAddedText - separatorLength : 0;
		position.found = true;
		position.row = row;
		position.columnOffset = Math.min(bounds.contentStart + offsetInValue, bounds.contentEnd);
		return position;
	}

	private static void ensureTrailingCellCaretSpaces(List<String> lines, CellCaretSnapshot snapshot) {
		int trailingSpaces = trailingMarkdownSpaceCount(snapshot.prefix);
		if (trailingSpaces == 0) {
			return;
		}

		CellCaretSnapshot visibleSnapshot = snapshot.copy();
		visibleSnapshot.prefix = withoutTrailingMarkdownSpaces(snapshot.prefix);
		visibleSnapshot.offset = visibleSnapshot.prefix.length();
		CellCaretPosition position = cellCaretPositionInLines(lines, visibleSnapshot);
		if (!position.found || position.row >= lines.size()) {
			return;
		}

		CellBounds bounds = cellBoundsForColumn(lines.get(position.row), snapshot.column);
		if (!bounds.found) {
			return;
		}

		int insertAt = Math.min(Math.max(position.columnOffset, bounds.contentStart), bounds.cellEnd);
		String line = lines.get(position.row);
		lines.set(position.row, line.substring(0, insertAt) + " ".repeat(trailingSpaces) + line.substring(insertAt));
	}

	private record Range(int start, int end) {
	}

	private record LineRange(int firstLine, int lastLine) {
	}

	private record TableReplacement(int start, int end, String text) {
	}

	private record InsertText(String text, int caretDelta) {
	}

	private record DelimitedLineScan(boolean hasDelimiter, boolean inQuotes) {
	}

	private enum CaretPlacement {
		ACTION_TARGET,
		PRESERVE_CELL_OFFSET
	}

	private static final class CellBounds {
		private boolean found;
		private int cellStart;
		private int cellEnd;
		private int contentStart;
		private int contentEnd;
	}

	private static final class CellCaretSnapshot {
		private boolean valid;
		private int row;
		private int logicalRow;
		private int column;
		private int offset;
		private String prefix = "";

		private CellCaretSnapshot copy() {
			CellCaretSnapshot copy = new CellCaretSnapshot();
			copy.valid = valid;
			copy.row = row;
			copy.logicalRow = logicalRow;
			copy.column = column;
			copy.offset = offset;
			copy.prefix = prefix;
			return copy;
		}
	}

	private static final class CellCaretPosition {
		private boolean found;
		private int row;
		private int columnOffset;
	}

	private static final class LogicalRowMap {
		private final int[] baseRowForRow;
		private final int[] logicalRowForRow;
		private int columns;

		private LogicalRowMap(int rows) {
			baseRowForRow = new int[rows];
			logicalRowForRow = new int[rows];
			java.util.Arrays.fill(baseRowForRow, -1);
			java.util.Arrays.fill(logicalRowForRow, -1);
		}
	}
}
