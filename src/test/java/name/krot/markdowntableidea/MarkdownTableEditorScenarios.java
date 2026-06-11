// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.TimerListener;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import name.krot.markdowntableidea.core.MarkdownTableCore;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MarkdownTableEditorScenarios {
	private static final ActionManager TEST_ACTION_MANAGER = new TestActionManager();

	private MarkdownTableEditorScenarios() {
	}

	public static void run() {
		MarkdownTableEditor.setCommandRunnerForTests((project, name, change) -> change.run());
		MarkdownTableActions.setDialogServiceForTests(new TestDialogService());
		MarkdownTableSettings.resetFallbackForTests();
		MarkdownTableSettings.getInstance().setAutoAlignEnabled(true);
		MarkdownTableSettings.getInstance().setAutoFitEnabled(true);
		try {
			editorRunScenarios();
			delimitedAndInsertScenarios();
			actionScenarios();
			statusBarWidgetScenarios();
			automaticScenarios();
			tabHandlerScenarios();
		} finally {
			MarkdownTableEditor.setCommandRunnerForTests(null);
			MarkdownTableActions.setDialogServiceForTests(null);
		}
	}

	private static void editorRunScenarios() {
		expectTrue("run rejects null editor", !MarkdownTableEditor.run(null, null, MarkdownTableCore.Action.ALIGN, true));

		TestEditor viewer = new TestEditor("| A |\n| --- |", true, true);
		expectTrue("run rejects viewer", !MarkdownTableEditor.run(viewer.editor, null, MarkdownTableCore.Action.ALIGN, true));

		TestEditor readOnly = new TestEditor("| A |\n| --- |", false, false);
		expectTrue("run rejects read-only editor", !MarkdownTableEditor.run(readOnly.editor, null, MarkdownTableCore.Action.ALIGN, false));
		expectInt("read-only attempt fired", readOnly.document.readOnlyAttempts, 1);

		TestEditor plain = new TestEditor("plain text", false, true);
		plain.setCaretAt("text");
		expectTrue("run rejects non-table line quietly", !MarkdownTableEditor.run(plain.editor, null, MarkdownTableCore.Action.ALIGN, true));

		TestEditor table = new TestEditor("Before\n| Name | Age |\n| --- | ---: |\n| Anna | 20 |\nAfter", false, true);
		table.setCaretAt("Anna");
		expectTrue("run aligns table", MarkdownTableEditor.run(table.editor, null, MarkdownTableCore.Action.ALIGN, true));
		expectString("aligned document", table.text(),
			"Before\n| Name | Age |\n| ---- | --: |\n| Anna |  20 |\nAfter");
		expectInt("align caret offset", table.caretOffset(), table.text().indexOf("Anna"));

		TestEditor tableAtStart = new TestEditor("| H | V |\n| --- | --- |\n| a | bb |\nAfter", false, true);
		tableAtStart.setCaretAt("bb");
		expectTrue("run replaces table at document start", MarkdownTableEditor.run(tableAtStart.editor, null, MarkdownTableCore.Action.ALIGN, true));
		expectString("document start replacement", tableAtStart.text(),
			"| H   | V   |\n| --- | --- |\n| a   | bb  |\nAfter");
		expectInt("document start replacement caret", tableAtStart.caretOffset(), tableAtStart.text().indexOf("bb"));

		TestEditor tableAtEnd = new TestEditor("Before\r\n| H | V |\r\n| --- | --- |\r\n| aa | b |", false, true);
		tableAtEnd.setCaretAt("b |");
		expectTrue("run replaces table at document end", MarkdownTableEditor.run(tableAtEnd.editor, null, MarkdownTableCore.Action.ALIGN, true));
		expectString("document end replacement keeps crlf", tableAtEnd.text(),
			"Before\r\n| H   | V   |\r\n| --- | --- |\r\n| aa  | b   |");
		expectInt("document end replacement caret", tableAtEnd.caretOffset(), tableAtEnd.text().lastIndexOf("b   |"));

		TestEditor mixedEol = new TestEditor("Top\r\n| H | V |\n| --- | --- |\r\n| a | b |\nBottom", false, true);
		mixedEol.setCaretAt("b |");
		expectTrue("run replaces table near mixed eol boundary", MarkdownTableEditor.run(mixedEol.editor, null, MarkdownTableCore.Action.ALIGN, true));
		expectString("mixed eol boundary replacement", mixedEol.text(),
			"Top\r\n| H   | V   |\n| --- | --- |\n| a   | b   |\nBottom");
		expectInt("mixed eol boundary caret", mixedEol.caretOffset(), mixedEol.text().lastIndexOf("b   |"));

		TestEditor adjacentPipeText = new TestEditor("Use A | B in text\n| H | V |\n| --- | --- |\n| a | b |\nNext A | B", false, true);
		adjacentPipeText.setCaretAt("a | b");
		expectTrue("run aligns table without adjacent pipe text", MarkdownTableEditor.run(adjacentPipeText.editor, null, MarkdownTableCore.Action.ALIGN, true));
		expectString("adjacent pipe text stays outside table", adjacentPipeText.text(),
			"Use A | B in text\n| H   | V   |\n| --- | --- |\n| a   | b   |\nNext A | B");
		adjacentPipeText.setCaretAt("Use A");
		expectTrue("adjacent plain pipe text is outside table", !MarkdownTableEditor.isInsidePotentialTable(adjacentPipeText.editor));

		TestEditor allTables = new TestEditor("Intro\n| A | B |\n| --- | --- |\n| 1 | 20 |\n\nText\n| Name | Score |\n| --- | ---: |\n| Bo | 7 |", false, true);
		expectTrue("format all tables in document", MarkdownTableEditor.formatAllTables(allTables.document.document));
		expectString("all document tables formatted", allTables.text(),
			"Intro\n| A   | B   |\n| --- | --- |\n| 1   | 20  |\n\nText\n| Name | Score |\n| ---- | ----: |\n| Bo   |     7 |");

		TestEditor nextCell = new TestEditor("| Name | Age |\n| --- | ---: |\n| Anna | 20 |", false, true);
		nextCell.setCaretAt("20");
		expectTrue("run next cell appends row", MarkdownTableEditor.run(nextCell.editor, null, MarkdownTableCore.Action.NEXT_CELL, true));
		expectString("next cell document", nextCell.text(),
			"| Name | Age |\n| ---- | --: |\n| Anna |  20 |\n|      |     |");
		expectInt("next cell caret target", nextCell.caretOffset(), nextCell.text().lastIndexOf("|      |") + 2);

		TestEditor inside = new TestEditor("| A | B |\n| --- | --- |\n| 1 | 2 |", false, true);
		inside.setCaretAt("2");
		expectTrue("inside potential table", MarkdownTableEditor.isInsidePotentialTable(inside.editor));
		expectTrue("null is not inside table", !MarkdownTableEditor.isInsidePotentialTable(null));
		expectTrue("viewer is not inside table", !MarkdownTableEditor.isInsidePotentialTable(viewer.editor));
		expectTrue("empty document is not inside table", !MarkdownTableEditor.isInsidePotentialTable(new TestEditor("", false, true).editor));

		TestEditor eofBlankLine = new TestEditor("| A |\n| --- |\n| 1 |\n", false, true);
		eofBlankLine.setCaretOffset(eofBlankLine.text().length());
		String eofBefore = eofBlankLine.text();
		expectTrue("eof blank line after table is outside table", !MarkdownTableEditor.isInsidePotentialTable(eofBlankLine.editor));
		expectTrue("run rejects eof blank line after table", !MarkdownTableEditor.run(eofBlankLine.editor, null, MarkdownTableCore.Action.ALIGN, true));
		expectString("eof blank line leaves table unchanged", eofBlankLine.text(), eofBefore);
	}

	private static void delimitedAndInsertScenarios() {
		TestEditor selectedCsv = new TestEditor("Name,Score\r\nAnna,10", false, true);
		selectedCsv.select(0, selectedCsv.text().length());
		expectTrue("convert selected csv", MarkdownTableEditor.convertDelimited(selectedCsv.editor, null, true));
		expectString("selected csv table", selectedCsv.text(),
			"| Name | Score |\r\n| ---- | ----- |\r\n| Anna | 10    |");
		expectTrue("selection removed after convert", !selectedCsv.hasSelection());

		TestEditor singleLineCsvInCrLfDocument = new TestEditor("intro\r\nName,Score", false, true);
		int singleLineCsvStart = singleLineCsvInCrLfDocument.text().indexOf("Name");
		singleLineCsvInCrLfDocument.select(singleLineCsvStart, singleLineCsvInCrLfDocument.text().length());
		expectTrue("convert single csv line keeps document eol fallback", MarkdownTableEditor.convertDelimited(singleLineCsvInCrLfDocument.editor, null, true));
		expectString("single csv line uses crlf fallback", singleLineCsvInCrLfDocument.text(),
			"intro\r\n| Name | Score |\r\n| ---- | ----- |");

		TestEditor blockCsv = new TestEditor("top\n\nName\tNote\nAnna\tA|B\n\nbottom", false, true);
		blockCsv.setCaretAt("Anna");
		expectTrue("convert current tsv block", MarkdownTableEditor.convertDelimited(blockCsv.editor, null, true));
		expectString("current block table", blockCsv.text(),
			"top\n\n| Name | Note |\n| ---- | ---- |\n| Anna | A\\|B |\n\nbottom");

		TestEditor inlineBlockCsv = new TestEditor("top\nName,Score\nAnna,10\nbottom", false, true);
		inlineBlockCsv.setCaretAt("Anna");
		expectTrue("convert current csv block skips adjacent text", MarkdownTableEditor.convertDelimited(inlineBlockCsv.editor, null, true));
		expectString("inline current block table", inlineBlockCsv.text(),
			"top\n| Name | Score |\n| ---- | ----- |\n| Anna | 10    |\nbottom");

		TestEditor singleCommaLine = new TestEditor("just, a note", false, true);
		singleCommaLine.setCaretAt("note");
		expectTrue("single comma line is not auto-converted", !MarkdownTableEditor.convertDelimited(singleCommaLine.editor, null, true));
		expectString("single comma line unchanged", singleCommaLine.text(), "just, a note");

		TestEditor invalidCsv = new TestEditor("just a note", false, true);
		invalidCsv.setCaretAt("note");
		expectTrue("plain text is not converted", !MarkdownTableEditor.convertDelimited(invalidCsv.editor, null, true));
		expectString("plain text unchanged", invalidCsv.text(), "just a note");

		TestEditor readOnly = new TestEditor("Name,Score\nAnna,10", false, false);
		readOnly.select(0, readOnly.text().length());
		expectTrue("convert rejects read-only", !MarkdownTableEditor.convertDelimited(readOnly.editor, null, false));
		expectInt("convert read-only attempt fired", readOnly.document.readOnlyAttempts, 1);

		TestEditor insertMiddle = new TestEditor("prefix suffix", false, true);
		insertMiddle.setCaretOffset("prefix".length());
		expectTrue("insert table in middle", MarkdownTableEditor.insertTable(insertMiddle.editor, null, 2, 1));
		expectString("insert middle text", insertMiddle.text(),
			"prefix\n| Column 1 | Column 2 |\n| -------- | -------- |\n|          |          |\n suffix");
		expectInt("insert middle caret", insertMiddle.caretOffset(), "prefix\n| ".length());

		TestEditor insertAtStart = new TestEditor("suffix", false, true);
		insertAtStart.setCaretOffset(0);
		expectTrue("insert table at document start", MarkdownTableEditor.insertTable(insertAtStart.editor, null, 1, 0));
		expectString("insert start text", insertAtStart.text(),
			"| Column 1 |\n| -------- |\nsuffix");
		expectInt("insert start caret", insertAtStart.caretOffset(), "| ".length());

		TestEditor insertAtEnd = new TestEditor("prefix", false, true);
		insertAtEnd.setCaretOffset(insertAtEnd.text().length());
		expectTrue("insert table at document end", MarkdownTableEditor.insertTable(insertAtEnd.editor, null, 1, 0));
		expectString("insert end text", insertAtEnd.text(),
			"prefix\n| Column 1 |\n| -------- |");
		expectInt("insert end caret", insertAtEnd.caretOffset(), "prefix\n| ".length());

		TestEditor replaceSelection = new TestEditor("replace", false, true);
		replaceSelection.select(0, replaceSelection.text().length());
		expectTrue("insert table replaces selection", MarkdownTableEditor.insertTable(replaceSelection.editor, null, 1, 0));
		expectString("selection replaced by table", replaceSelection.text(), "| Column 1 |\n| -------- |");

		TestEditor insertAtLastCrLfLine = new TestEditor("first\r\nlast", false, true);
		insertAtLastCrLfLine.setCaretOffset(insertAtLastCrLfLine.text().length());
		expectTrue("insert table at last crlf line", MarkdownTableEditor.insertTable(insertAtLastCrLfLine.editor, null, 1, 0));
		expectString("insert table uses document crlf fallback", insertAtLastCrLfLine.text(),
			"first\r\nlast\r\n| Column 1 |\r\n| -------- |");

		TestEditor insertAtEmptyFinalCrLfLine = new TestEditor("first\r\n", false, true);
		insertAtEmptyFinalCrLfLine.setCaretOffset(insertAtEmptyFinalCrLfLine.text().length());
		expectTrue("insert table at empty final crlf line", MarkdownTableEditor.insertTable(insertAtEmptyFinalCrLfLine.editor, null, 1, 0));
		expectString("insert table on empty final line avoids extra separator", insertAtEmptyFinalCrLfLine.text(),
			"first\r\n| Column 1 |\r\n| -------- |");

		TestEditor readOnlyInsert = new TestEditor("x", false, false);
		expectTrue("insert rejects read-only", !MarkdownTableEditor.insertTable(readOnlyInsert.editor, null, 1, 1));
		expectInt("insert read-only attempt fired", readOnlyInsert.document.readOnlyAttempts, 1);
	}

	private static void actionScenarios() {
		MarkdownTableSettings settings = MarkdownTableSettings.getInstance();
		expectTrue("auto align defaults on", settings.isAutoAlignEnabled());
		expectTrue("auto fit defaults on", settings.isAutoFitEnabled());
		expectInt("auto debounce default", settings.getDebounceMs(), 160);

		TestEditor disabledManualEditor = new TestEditor("| A | B |\n| --- | --- |\n| 1 | 20 |", false, true);
		Presentation disabledAlign = new Presentation();
		new MarkdownTableActions.Align().update(event(disabledManualEditor, disabledAlign));
		expectTrue("manual align hidden while auto align is on", !disabledAlign.isEnabled() && !disabledAlign.isVisible());
		Presentation disabledFit = new Presentation();
		new MarkdownTableActions.WrapLongCells().update(event(disabledManualEditor, disabledFit));
		expectTrue("manual fit hidden while auto fit is on", !disabledFit.isEnabled() && !disabledFit.isVisible());

		settings.setAutoAlignEnabled(false);
		settings.setAutoFitEnabled(false);
		MarkdownTableActions.Base[] baseActions = {
			new MarkdownTableActions.Align(),
			new MarkdownTableActions.NextCell(),
			new MarkdownTableActions.PreviousCell(),
			new MarkdownTableActions.InsertRowBelow(),
			new MarkdownTableActions.DeleteRow(),
			new MarkdownTableActions.InsertColumnRight(),
			new MarkdownTableActions.DeleteColumn(),
			new MarkdownTableActions.MoveRowUp(),
			new MarkdownTableActions.MoveRowDown(),
			new MarkdownTableActions.MoveColumnLeft(),
			new MarkdownTableActions.MoveColumnRight(),
			new MarkdownTableActions.SortAscending(),
			new MarkdownTableActions.SortDescending(),
			new MarkdownTableActions.WrapLongCells()
		};
		expectInt("base action wrappers constructed", baseActions.length, 14);

		MarkdownTableActions.Align align = new MarkdownTableActions.Align();
		expectSame("base action update thread", align.getActionUpdateThread(), ActionUpdateThread.EDT);
		TestEditor editor = new TestEditor("| A | B |\n| --- | --- |\n| 1 | 20 |", false, true);
		editor.setCaretAt("20");
		Presentation presentation = new Presentation();
		AnActionEvent event = event(editor, presentation);
		align.update(event);
		expectTrue("base action update enabled", presentation.isEnabled());
		expectTrue("base action update visible", presentation.isVisible());
		align.actionPerformed(event);
		expectString("base action aligns table", editor.text(), "| A   | B   |\n| --- | --- |\n| 1   | 20  |");

		TestEditor twoTables = new TestEditor("| A | B |\n| --- | --- |\n| 1 | 20 |\n\n| Name | Score |\n| --- | ---: |\n| Bo | 7 |", false, true);
		twoTables.setCaretAt("20");
		align.actionPerformed(event(twoTables, new Presentation()));
		expectString("base action still aligns only current table", twoTables.text(),
			"| A   | B   |\n| --- | --- |\n| 1   | 20  |\n\n| Name | Score |\n| --- | ---: |\n| Bo | 7 |");

		TestEditor fitEditor = new TestEditor(
			"| Key | Value | Other |\n" +
				"| --- | --- | --- |\n" +
				"| row | alpha beta gamma delta epsilon zeta eta theta | tail |",
			false,
			true
		);
		fitEditor.setVisibleColumns(46);
		fitEditor.setCaretAt("alpha");
		MarkdownTableActions.WrapLongCells fit = new MarkdownTableActions.WrapLongCells();
		fit.actionPerformed(event(fitEditor, new Presentation()));
		expectContains("fit action creates continuation row", fitEditor.text(), "|     | epsilon");
		for (String line : fitEditor.text().split("\\R")) {
			expectTrue("fit action keeps line inside visible width: " + line, line.length() <= 46);
		}

		MarkdownTableActions.AutoAlign autoAlign = new MarkdownTableActions.AutoAlign();
		MarkdownTableActions.AutoFit autoFit = new MarkdownTableActions.AutoFit();
		settings.setAutoAlignEnabled(false);
		settings.setAutoFitEnabled(false);
		TestEditor toggleEditor = new TestEditor("| A | B |\n| --- | --- |\n| 1 | 20 |", false, true);
		toggleEditor.setCaretAt("20");
		autoAlign.setSelected(event(toggleEditor, new Presentation()), true);
		expectTrue("auto align toggle turns on", settings.isAutoAlignEnabled());
		expectString("auto align toggle formats current table", toggleEditor.text(), "| A   | B   |\n| --- | --- |\n| 1   | 20  |");
		autoAlign.setSelected(event(toggleEditor, new Presentation()), false);
		expectTrue("auto align toggle turns off", !settings.isAutoAlignEnabled());

		TestEditor toggleFitEditor = new TestEditor(
			"| Key | Value |\n| --- | --- |\n| row | alpha beta gamma delta epsilon zeta eta theta |",
			false,
			true
		);
		toggleFitEditor.setVisibleColumns(36);
		toggleFitEditor.setCaretAt("alpha");
		autoFit.setSelected(event(toggleFitEditor, new Presentation()), true);
		expectTrue("auto fit toggle turns on", settings.isAutoFitEnabled());
		expectContains("auto fit toggle formats current table", toggleFitEditor.text(), "|     |");
		autoFit.setSelected(event(toggleFitEditor, new Presentation()), false);
		expectTrue("auto fit toggle turns off", !settings.isAutoFitEnabled());

		settings.setAutoAlignEnabled(false);
		settings.setAutoFitEnabled(false);
		Presentation hidden = new Presentation();
		new MarkdownTableActions.Align().update(event(null, hidden));
		expectTrue("base update hides without editor", !hidden.isEnabled() && !hidden.isVisible());

		MarkdownTableActions.TabAlign tabAlign = new MarkdownTableActions.TabAlign();
		TestEditor tabEditor = new TestEditor("| A |\n| --- |\n| 1 |", false, true);
		tabEditor.setCaretAt("1");
		Presentation tabPresentation = new Presentation();
		tabAlign.update(event(tabEditor, tabPresentation));
		expectTrue("tab action enabled inside table", tabPresentation.isEnabled());
		tabAlign.actionPerformed(event(tabEditor, new Presentation()));
		expectContains("tab action aligns", tabEditor.text(), "| A   |");
		expectSame("tab action update thread", tabAlign.getActionUpdateThread(), ActionUpdateThread.EDT);

		MarkdownTableActions.ConvertCsvTsv convert = new MarkdownTableActions.ConvertCsvTsv();
		TestEditor csv = new TestEditor("Name,Score\nAnna,10", false, true);
		csv.select(0, csv.text().length());
		Presentation csvPresentation = new Presentation();
		convert.update(event(csv, csvPresentation));
		expectTrue("convert action enabled", csvPresentation.isEnabled() && csvPresentation.isVisible());
		convert.actionPerformed(event(csv, new Presentation()));
		expectContains("convert action result", csv.text(), "| Anna | 10");
		expectSame("convert action update thread", convert.getActionUpdateThread(), ActionUpdateThread.EDT);

		TestDialogService dialog = new TestDialogService();
		MarkdownTableActions.setDialogServiceForTests(dialog);
		MarkdownTableActions.InsertTable insert = new MarkdownTableActions.InsertTable();
		expectSame("insert action update thread", insert.getActionUpdateThread(), ActionUpdateThread.EDT);
		TestEditor target = new TestEditor("", false, true);
		dialog.nextValue = null;
		insert.actionPerformed(event(target, new Presentation()));
		expectString("cancel keeps document empty", target.text(), "");
		dialog.nextValue = "bad";
		insert.actionPerformed(event(target, new Presentation()));
		expectString("bad size error", dialog.lastError, MarkdownTableEditorBundle.message("dialog.insertTable.error.format"));
		dialog.nextValue = "51x1";
		insert.actionPerformed(event(target, new Presentation()));
		expectString("range size error", dialog.lastError, MarkdownTableEditorBundle.message("dialog.insertTable.error.range"));
		dialog.nextValue = "2x1";
		insert.actionPerformed(event(target, new Presentation()));
		expectContains("insert action created table", target.text(), "| Column 1 | Column 2 |");
		Presentation insertPresentation = new Presentation();
		insert.update(event(target, insertPresentation));
		expectTrue("insert action update enabled", insertPresentation.isEnabled() && insertPresentation.isVisible());
	}

	private static void statusBarWidgetScenarios() {
		MarkdownTableSettings settings = MarkdownTableSettings.getInstance();
		settings.setAutoAlignEnabled(true);
		settings.setAutoFitEnabled(true);

		MarkdownTableStatusBarWidgets.AutoAlignFactory autoAlignFactory = new MarkdownTableStatusBarWidgets.AutoAlignFactory();
		MarkdownTableStatusBarWidgets.AutoFitFactory autoFitFactory = new MarkdownTableStatusBarWidgets.AutoFitFactory();
		expectString("auto align widget factory id", autoAlignFactory.getId(), "markdownTableAutoAlignWidget");
		expectString("auto fit widget factory id", autoFitFactory.getId(), "markdownTableAutoFitWidget");
		expectString(
			"auto align widget display name",
			autoAlignFactory.getDisplayName(),
			MarkdownTableEditorBundle.message("status.MarkdownTableEditor.AutoAlign.displayName")
		);
		expectString(
			"auto fit widget display name",
			autoFitFactory.getDisplayName(),
			MarkdownTableEditorBundle.message("status.MarkdownTableEditor.AutoFit.displayName")
		);
		expectTrue("auto align widget enabled by default", autoAlignFactory.isEnabledByDefault());
		expectTrue("auto fit widget enabled by default", autoFitFactory.isEnabledByDefault());

		StatusBarWidget autoAlignWidget = autoAlignFactory.createWidget(null);
		StatusBarWidget.TextPresentation autoAlignPresentation = (StatusBarWidget.TextPresentation)autoAlignWidget.getPresentation();
		expectString("auto align widget id", autoAlignWidget.ID(), "markdownTableAutoAlignWidget");
		expectString(
			"auto align widget on text",
			autoAlignPresentation.getText(),
			MarkdownTableEditorBundle.message("status.MarkdownTableEditor.AutoAlign.on")
		);
		expectString(
			"auto align widget on tooltip",
			autoAlignPresentation.getTooltipText(),
			MarkdownTableEditorBundle.message("status.MarkdownTableEditor.AutoAlign.tooltip.on")
		);
		autoAlignPresentation.getClickConsumer().consume(null);
		expectTrue("auto align widget click turns setting off", !settings.isAutoAlignEnabled());
		expectString(
			"auto align widget off text",
			autoAlignPresentation.getText(),
			MarkdownTableEditorBundle.message("status.MarkdownTableEditor.AutoAlign.off")
		);
		autoAlignPresentation.getClickConsumer().consume(null);
		expectTrue("auto align widget click turns setting on", settings.isAutoAlignEnabled());

		StatusBarWidget autoFitWidget = autoFitFactory.createWidget(null);
		StatusBarWidget.TextPresentation autoFitPresentation = (StatusBarWidget.TextPresentation)autoFitWidget.getPresentation();
		expectString("auto fit widget id", autoFitWidget.ID(), "markdownTableAutoFitWidget");
		expectString(
			"auto fit widget on text",
			autoFitPresentation.getText(),
			MarkdownTableEditorBundle.message("status.MarkdownTableEditor.AutoFit.on")
		);
		expectString(
			"auto fit widget on tooltip",
			autoFitPresentation.getTooltipText(),
			MarkdownTableEditorBundle.message("status.MarkdownTableEditor.AutoFit.tooltip.on")
		);
		autoFitPresentation.getClickConsumer().consume(null);
		expectTrue("auto fit widget click turns setting off", !settings.isAutoFitEnabled());
		expectString(
			"auto fit widget off text",
			autoFitPresentation.getText(),
			MarkdownTableEditorBundle.message("status.MarkdownTableEditor.AutoFit.off")
		);
		autoFitPresentation.getClickConsumer().consume(null);
		expectTrue("auto fit widget click turns setting on", settings.isAutoFitEnabled());
	}

	private static void automaticScenarios() {
		MarkdownTableSettings settings = MarkdownTableSettings.getInstance();
		settings.setAutoAlignEnabled(true);
		settings.setAutoFitEnabled(true);

		TestEditor autoFit = new TestEditor(
			"| Key | Value |\n| --- | --- |\n| row | alpha beta gamma delta epsilon zeta eta theta |",
			false,
			true
		);
		autoFit.setVisibleColumns(48);
		autoFit.setCaretAt("epsilon");
		expectTrue("auto fit runs after document change", MarkdownTableEditor.autoFormatEditorForTests(autoFit.editor, null, true));
		expectContains("auto fit creates continuation rows", autoFit.text(), "|     |");
		expectTrue("auto fit keeps caret inside table", MarkdownTableEditor.isInsidePotentialTable(autoFit.editor));

		settings.setAutoFitEnabled(false);
		TestEditor autoAlign = new TestEditor("| A | B |\n| --- | --- |\n| 1 | 20 |", false, true);
		autoAlign.setCaretAt("20");
		expectTrue("auto align runs after document change", MarkdownTableEditor.autoFormatEditorForTests(autoAlign.editor, null, true));
		expectString("auto align formats without fitting", autoAlign.text(), "| A   | B   |\n| --- | --- |\n| 1   | 20  |");

		TestEditor nonMarkdown = new TestEditor("| A | B |\n| --- | --- |\n| 1 | 20 |", false, true);
		nonMarkdown.setCaretAt("20");
		String nonMarkdownBefore = nonMarkdown.text();
		expectTrue("auto skips non markdown file", !MarkdownTableEditor.autoFormatEditorForTests(nonMarkdown.editor, null, false));
		expectString("non markdown unchanged", nonMarkdown.text(), nonMarkdownBefore);

		TestEditor selected = new TestEditor("| A | B |\n| --- | --- |\n| 1 | 20 |", false, true);
		selected.select(0, 1);
		expectTrue("auto skips selection", !MarkdownTableEditor.autoFormatEditorForTests(selected.editor, null, true));

		TestEditor multiCaret = new TestEditor("| A | B |\n| --- | --- |\n| 1 | 20 |", false, true);
		multiCaret.setCaretCount(2);
		multiCaret.setCaretAt("20");
		expectTrue("auto skips multi caret", !MarkdownTableEditor.autoFormatEditorForTests(multiCaret.editor, null, true));

		TestEditor readOnly = new TestEditor("| A | B |\n| --- | --- |\n| 1 | 20 |", false, false);
		readOnly.setCaretAt("20");
		expectTrue("auto skips read only", !MarkdownTableEditor.autoFormatEditorForTests(readOnly.editor, null, true));

		TestEditor outside = new TestEditor("plain\n| A | B |\n| --- | --- |\n| 1 | 20 |", false, true);
		outside.setCaretAt("plain");
		expectTrue("auto skips outside table", !MarkdownTableEditor.autoFormatEditorForTests(outside.editor, null, true));

		expectTrue("resize ignores disabled mode", !MarkdownTableEditor.shouldRunFitAfterWidthChange(false, false, true, 100, 90));
		expectTrue("resize ignores in-progress mode", !MarkdownTableEditor.shouldRunFitAfterWidthChange(true, true, true, 100, 90));
		expectTrue("resize ignores inactive editor", !MarkdownTableEditor.shouldRunFitAfterWidthChange(true, false, false, 100, 90));
		expectTrue("resize records first width", !MarkdownTableEditor.shouldRunFitAfterWidthChange(true, false, true, 0, 90));
		expectTrue("resize ignores unchanged width", !MarkdownTableEditor.shouldRunFitAfterWidthChange(true, false, true, 90, 90));
		expectTrue("resize runs after width change", MarkdownTableEditor.shouldRunFitAfterWidthChange(true, false, true, 100, 90));

		TestEditor widthProbe = new TestEditor("", false, true);
		widthProbe.setVisibleColumns(80);
		expectInt("available display columns uses margin", MarkdownTableEditor.availableDisplayColumnsForTests(widthProbe.editor, new Rectangle(0, 0, 82, 20)), 80);

		settings.setAutoFitEnabled(true);
		TestEditor rememberedResize = new TestEditor(
			"| Key | Value |\n| --- | --- |\n| row | alpha beta gamma delta epsilon zeta eta theta |",
			false,
			true
		);
		rememberedResize.setVisibleColumns(90);
		rememberedResize.setCaretAt("alpha");
		MarkdownTableEditor.rememberVisibleEditorWidth(rememberedResize.editor);
		rememberedResize.setVisibleColumns(60);
		expectTrue(
			"remembered width lets first real resize trigger fit",
			MarkdownTableEditor.handleVisibleAreaChangedForTests(rememberedResize.editor, null, true)
		);
		MarkdownTableEditor.forgetVisibleEditorWidth(rememberedResize.editor);

		settings.setAutoFitEnabled(false);
		TestEditor alreadyAligned = new TestEditor("| A   | B   |\n| --- | --- |\n| 1   | 20  |", false, true);
		alreadyAligned.setCaretAt("20");
		expectTrue("auto align skips no-op caret triggered formatting", !MarkdownTableEditor.autoFormatEditorForTests(alreadyAligned.editor, null, true));
		expectString("no-op auto align leaves text unchanged", alreadyAligned.text(), "| A   | B   |\n| --- | --- |\n| 1   | 20  |");

		expectTrue("markdown file md", MarkdownTableEditor.isMarkdownFileName("README.md"));
		expectTrue("markdown file markdown", MarkdownTableEditor.isMarkdownFileName("notes.markdown"));
		expectTrue("plain file not markdown", !MarkdownTableEditor.isMarkdownFileName("notes.txt"));

		settings.setAutoAlignEnabled(false);
		settings.setAutoFitEnabled(false);
	}

	private static void tabHandlerScenarios() {
		RecordingHandler original = new RecordingHandler(false);
		MarkdownTableTabHandler handler = new MarkdownTableTabHandler(original);
		TestEditor table = new TestEditor("| A |\n| --- |\n| 1 |", false, true);
		table.setCaretAt("1");
		DataContext context = context(table);
		expectTrue("tab handler enabled in table", handler.isEnabled(table.editor, table.caret(), context));
		handler.doExecute(table.editor, table.caret(), context);
		expectInt("tab handler does not call original on success", original.executions, 0);
		expectContains("tab handler aligns table", table.text(), "| A   |");

		TestEditor plain = new TestEditor("plain", false, true);
		plain.setCaretAt("plain");
		handler.doExecute(plain.editor, plain.caret(), context(plain));
		expectInt("tab handler calls original on failure", original.executions, 1);

		RecordingHandler enabledOriginal = new RecordingHandler(true);
		MarkdownTableTabHandler fallback = new MarkdownTableTabHandler(enabledOriginal);
		expectTrue("tab handler delegates enabled state", fallback.isEnabled(plain.editor, plain.caret(), context(plain)));
		expectTrue("tab handler enabled when original missing", new MarkdownTableTabHandler(null).isEnabled(plain.editor, plain.caret(), context(plain)));
		String beforeNoOriginal = plain.text();
		new MarkdownTableTabHandler(null).doExecute(plain.editor, plain.caret(), context(plain));
		expectString("tab handler without original leaves plain text unchanged", plain.text(), beforeNoOriginal);
	}

	private static AnActionEvent event(TestEditor editor, Presentation presentation) {
		return new AnActionEvent(null, context(editor), "test", presentation, TEST_ACTION_MANAGER, 0);
	}

	@SuppressWarnings("removal")
	private static DataContext context(TestEditor editor) {
		return new DataContext() {
			@Override
			public Object getData(String dataId) {
				if (CommonDataKeys.EDITOR.is(dataId)) {
					return editor == null ? null : editor.editor;
				}
				if (CommonDataKeys.PROJECT.is(dataId)) {
					return null;
				}
				return null;
			}
		};
	}

	private static void expectTrue(String name, boolean value) {
		assertTrue(value, name);
	}

	private static void expectInt(String name, int actual, int expected) {
		assertEquals(expected, actual, name);
	}

	private static void expectString(String name, String actual, String expected) {
		assertEquals(expected, actual, name);
	}

	private static void expectContains(String name, String actual, String expected) {
		assertTrue(actual.contains(expected), () -> name + " expected to contain: " + expected + "\nactual:\n" + actual);
	}

	private static void expectSame(String name, Object actual, Object expected) {
		assertSame(expected, actual, name);
	}

	private static final class RecordingHandler extends EditorActionHandler {
		private final boolean enabled;
		private int executions;

		private RecordingHandler(boolean enabled) {
			this.enabled = enabled;
		}

		@Override
		protected boolean isEnabledForCaret(Editor editor, Caret caret, DataContext dataContext) {
			return enabled;
		}

		@Override
		protected void doExecute(Editor editor, Caret caret, DataContext dataContext) {
			executions++;
		}
	}

	private static final class TestActionManager extends ActionManager {
		@Override
		public ActionPopupMenu createActionPopupMenu(String place, ActionGroup group) {
			return null;
		}

		@Override
		public ActionToolbar createActionToolbar(String place, ActionGroup group, boolean horizontal) {
			return null;
		}

		@Override
		public AnAction getAction(String id) {
			return null;
		}

		@Override
		public String getId(AnAction action) {
			return null;
		}

		@Override
		public void registerAction(String actionId, AnAction action) {
		}

		@Override
		public void registerAction(String actionId, AnAction action, PluginId pluginId) {
		}

		@Override
		public void unregisterAction(String actionId) {
		}

		@Override
		public void replaceAction(String actionId, AnAction newAction) {
		}

		@Override
		public String[] getActionIds(String idPrefix) {
			return new String[0];
		}

		@Override
		public List<String> getActionIdList(String idPrefix) {
			return Collections.emptyList();
		}

		@Override
		public boolean isGroup(String id) {
			return false;
		}

		@Override
		public JComponent createButtonToolbar(String place, ActionGroup group) {
			return null;
		}

		@Override
		public AnAction getActionOrStub(String id) {
			return null;
		}

		@Override
		public void addTimerListener(TimerListener listener) {
		}

		@Override
		public void removeTimerListener(TimerListener listener) {
		}

		@Override
		public void addAnActionListener(AnActionListener listener) {
		}

		@Override
		public void removeAnActionListener(AnActionListener listener) {
		}

		@Override
		public com.intellij.openapi.util.ActionCallback tryToExecute(
			AnAction action,
			InputEvent inputEvent,
			Component contextComponent,
			String place,
			boolean now
		) {
			return null;
		}

		@Override
		public KeyboardShortcut getKeyboardShortcut(String actionId) {
			return null;
		}
	}

	private static final class TestDialogService implements MarkdownTableActions.DialogService {
		private String nextValue = "2x1";
		private String lastError = "";

		@Override
		public String askTableSize(Project project) {
			return nextValue;
		}

		@Override
		public void showError(Project project, String message) {
			lastError = message;
		}
	}

	private static final class TestEditor {
		private final TestDocument document;
		private final TestCaretModel caretModel = new TestCaretModel();
		private final TestSelectionModel selectionModel = new TestSelectionModel();
		private final TestScrollingModel scrollingModel = new TestScrollingModel();
		private final TestEditorComponent component = new TestEditorComponent();
		private final Editor editor;
		private final boolean viewer;

		private TestEditor(String text, boolean viewer, boolean writable) {
			this.document = new TestDocument(text, writable);
			this.viewer = viewer;
			this.editor = proxy(Editor.class, this::invokeEditor);
			this.caretModel.editor = editor;
			this.selectionModel.editor = editor;
			this.scrollingModel.editor = this;
		}

		private String text() {
			return document.text.toString();
		}

		private int caretOffset() {
			return caretModel.offset;
		}

		private Caret caret() {
			return caretModel.caret;
		}

		private void setCaretAt(String value) {
			int index = text().indexOf(value);
			setCaretOffset(index < 0 ? 0 : index);
		}

		private void setCaretOffset(int offset) {
			caretModel.offset = Math.max(0, Math.min(offset, document.text.length()));
		}

		private void select(int start, int end) {
			selectionModel.hasSelection = true;
			selectionModel.start = start;
			selectionModel.end = end;
			setCaretOffset(end);
		}

		private boolean hasSelection() {
			return selectionModel.hasSelection;
		}

		private void setVisibleColumns(int columns) {
			component.visibleWidth = Math.max(1, columns + 2);
		}

		private void setCaretCount(int count) {
			caretModel.caretCount = count;
		}

		private Object invokeEditor(Object proxy, Method method, Object[] args) {
			return switch (method.getName()) {
				case "getDocument" -> document.document;
				case "isViewer" -> viewer;
				case "getCaretModel" -> caretModel.caretModel;
				case "getSelectionModel" -> selectionModel.selectionModel;
				case "getScrollingModel" -> scrollingModel.scrollingModel;
				case "getContentComponent" -> component;
				case "getProject" -> null;
				default -> defaultValue(method.getReturnType());
			};
		}
	}

	private static final class TestEditorComponent extends JComponent {
		private int visibleWidth = 122;
		private boolean showing = true;

		@Override
		public Rectangle getVisibleRect() {
			return new Rectangle(0, 0, visibleWidth, 100);
		}

		@Override
		public int getWidth() {
			return visibleWidth;
		}

		@Override
		public boolean isShowing() {
			return showing;
		}
	}

	private static final class TestDocument {
		private final Document document;
		private final StringBuilder text;
		private boolean writable;
		private long modificationStamp;
		private int readOnlyAttempts;

		private TestDocument(String text, boolean writable) {
			this.text = new StringBuilder(text);
			this.writable = writable;
			this.document = proxy(Document.class, this::invoke);
		}

		private Object invoke(Object proxy, Method method, Object[] args) {
			switch (method.getName()) {
				case "getImmutableCharSequence":
				case "getCharsSequence":
				case "getText":
					return text.toString();
				case "getTextLength":
					return text.length();
				case "getLineCount":
					return lines().size();
				case "getLineNumber":
					return lineNumber((Integer) args[0]);
				case "getLineStartOffset":
					return lines().get((Integer) args[0]).start;
				case "getLineEndOffset":
					return lines().get((Integer) args[0]).end;
				case "replaceString":
					text.replace((Integer) args[0], (Integer) args[1], args[2].toString());
					modificationStamp++;
					return null;
				case "insertString":
					text.insert((Integer) args[0], args[1].toString());
					modificationStamp++;
					return null;
				case "deleteString":
					text.delete((Integer) args[0], (Integer) args[1]);
					modificationStamp++;
					return null;
				case "isWritable":
					return writable;
				case "setReadOnly":
					writable = !((Boolean) args[0]);
					return null;
				case "setText":
					text.setLength(0);
					text.append(args[0].toString());
					modificationStamp++;
					return null;
				case "fireReadOnlyModificationAttempt":
					readOnlyAttempts++;
					return null;
				case "getModificationStamp":
					return modificationStamp;
				default:
					return defaultValue(method.getReturnType());
			}
		}

		private int lineNumber(int offset) {
			int safeOffset = Math.max(0, Math.min(offset, text.length()));
			List<Line> lines = lines();
			for (int i = 0; i < lines.size(); i++) {
				Line line = lines.get(i);
				if (safeOffset >= line.start && safeOffset <= line.end) {
					return i;
				}
			}
			return Math.max(0, lines.size() - 1);
		}

		private List<Line> lines() {
			if (text.length() == 0) {
				return Collections.singletonList(new Line(0, 0));
			}

			List<Line> lines = new ArrayList<>();
			int start = 0;
			for (int i = 0; i < text.length(); i++) {
				char ch = text.charAt(i);
				if (ch == '\r' || ch == '\n') {
					lines.add(new Line(start, i));
					if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
						i++;
					}
					start = i + 1;
				}
			}
			lines.add(new Line(start, text.length()));
			return lines;
		}
	}

	private record Line(int start, int end) {
	}

	private static final class TestCaretModel {
		private final CaretModel caretModel = proxy(CaretModel.class, this::invoke);
		private final Caret caret = proxy(Caret.class, this::invokeCaret);
		private Editor editor;
		private int offset;
		private int caretCount = 1;

		private Object invoke(Object proxy, Method method, Object[] args) {
			return switch (method.getName()) {
				case "getOffset" -> offset;
				case "moveToOffset" -> {
					offset = (Integer) args[0];
					yield null;
				}
				case "getCurrentCaret", "getPrimaryCaret" -> caret;
				case "getAllCarets" -> List.of(caret);
				case "getCaretsAndSelections" -> Collections.emptyList();
				case "getCaretCount", "getMaxCaretCount" -> caretCount;
				case "supportsMultipleCarets" -> false;
				default -> defaultValue(method.getReturnType());
			};
		}

		private Object invokeCaret(Object proxy, Method method, Object[] args) {
			return switch (method.getName()) {
				case "getEditor" -> editor;
				case "getCaretModel" -> caretModel;
				case "isValid", "isUpToDate" -> true;
				case "getOffset", "getSelectionStart", "getSelectionEnd", "getLeadSelectionOffset" -> offset;
				case "moveToOffset" -> {
					offset = (Integer) args[0];
					yield null;
				}
				case "hasSelection", "isAtRtlLocation", "isAtBidiRunBoundary", "isDisposed" -> false;
				case "clone" -> caret;
				default -> defaultValue(method.getReturnType());
			};
		}
	}

	private static final class TestSelectionModel {
		private final SelectionModel selectionModel = proxy(SelectionModel.class, this::invoke);
		private Editor editor;
		private boolean hasSelection;
		private int start;
		private int end;

		private Object invoke(Object proxy, Method method, Object[] args) {
			return switch (method.getName()) {
				case "getEditor" -> editor;
				case "hasSelection" -> hasSelection;
				case "getSelectionStart", "getLeadSelectionOffset" -> start;
				case "getSelectionEnd" -> end;
				case "setSelection" -> {
					hasSelection = true;
					start = (Integer) args[0];
					end = (Integer) args[args.length - 1];
					yield null;
				}
				case "removeSelection" -> {
					hasSelection = false;
					start = end;
					yield null;
				}
				case "getBlockSelectionStarts" -> new int[0];
				case "getBlockSelectionEnds" -> new int[0];
				default -> defaultValue(method.getReturnType());
			};
		}
	}

	private static final class TestScrollingModel {
		private final ScrollingModel scrollingModel = proxy(ScrollingModel.class, this::invoke);
		private TestEditor editor;
		private ScrollType lastScrollType;

		private Object invoke(Object proxy, Method method, Object[] args) {
			if ("scrollToCaret".equals(method.getName())) {
				lastScrollType = (ScrollType) args[0];
				return null;
			}
			if ("getVisibleArea".equals(method.getName()) || "getVisibleAreaOnScrollingFinished".equals(method.getName())) {
				int width = editor == null ? 0 : editor.component.visibleWidth;
				return new java.awt.Rectangle(0, 0, width, 100);
			}
			return defaultValue(method.getReturnType());
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
			if ("toString".equals(method.getName())) {
				return type.getSimpleName() + Arrays.toString(args == null ? new Object[0] : args);
			}
			if ("hashCode".equals(method.getName())) {
				return System.identityHashCode(proxy);
			}
			if ("equals".equals(method.getName())) {
				return proxy == args[0];
			}
			return handler.invoke(proxy, method, args == null ? new Object[0] : args);
		});
	}

	private static Object defaultValue(Class<?> type) {
		if (type == Void.TYPE) {
			return null;
		}
		if (type == Boolean.TYPE) {
			return false;
		}
		if (type == Byte.TYPE || type == Short.TYPE || type == Integer.TYPE || type == Character.TYPE) {
			return 0;
		}
		if (type == Long.TYPE) {
			return 0L;
		}
		if (type == Float.TYPE) {
			return 0.0f;
		}
		if (type == Double.TYPE) {
			return 0.0d;
		}
		return null;
	}
}
