// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea.core;

import name.krot.markdowntableidea.MarkdownTableEditorScenarios;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.HashSet;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MarkdownTableCoreSmoke {
	@Test
	void coreSmokeScenarios() throws Exception {
		String sourcePluginXml = Files.readString(Path.of("src", "main", "resources", "META-INF", "plugin.xml"));
		Path processedPluginXmlPath = Path.of("build", "resources", "main", "META-INF", "plugin.xml");
		expectTrue("processed plugin descriptor exists", Files.exists(processedPluginXmlPath));
		String pluginXml = Files.readString(processedPluginXmlPath);
		String projectVersion = Files.readString(Path.of("VERSION")).trim();
		String releaseVersion = System.getProperty("pluginVersion", projectVersion);
		expectContains("dynamic plugin descriptor", pluginXml, "require-restart=\"false\"");
		expectContains("source plugin version is generated", sourcePluginXml, "<version>@PLUGIN_VERSION@</version>");
		expectNotContains("source plugin version is not pinned to VERSION", sourcePluginXml, "<version>" + projectVersion + "</version>");
		expectContains("plugin version matches release version", pluginXml, "<version>" + releaseVersion + "</version>");
		expectNotContains("plugin version placeholder is replaced", pluginXml, "@PLUGIN_VERSION@");
		expectContains("plugin compatibility starts at IDEA 2022.3", pluginXml, "<idea-version since-build=\"223\"");
		expectNotContains("plugin compatibility is not limited to 223.*", pluginXml, "until-build=\"223.*\"");
		expectNotContains("plugin compatibility has no upper build cap", pluginXml, "until-build=\"");
		expectContains("plugin uses platform-only dependency", pluginXml, "<depends>com.intellij.modules.platform</depends>");
		expectNotContains("plugin has no Java module dependency", pluginXml, "<depends>com.intellij.modules.java</depends>");
		expectNotContains("plugin has no IDEA-specific dependency", pluginXml, "<depends>com.intellij.modules.idea</depends>");
		expectContains("dynamic tab handler descriptor", pluginXml, "<editorActionHandler action=\"EditorTab\"");
		expectContains("dynamic tab handler implementation", pluginXml, "implementationClass=\"name.krot.markdowntableidea.MarkdownTableTabHandler\"");
		expectContains("tab handler runs before default tab processors", pluginXml, "order=\"first\"");
		expectContains("markdown tables participate in IDE reformat", pluginXml, "<postFormatProcessor implementation=\"name.krot.markdowntableidea.MarkdownTableFormatProcessor\"");
		expectContains(
			"left status bar startup activity descriptor",
			pluginXml,
			"<postStartupActivity implementation=\"name.krot.markdowntableidea.MarkdownTableStatusBarWidgets$LeftInstaller\""
		);
		expectContains(
			"auto mode event startup activity descriptor",
			pluginXml,
			"<postStartupActivity implementation=\"name.krot.markdowntableidea.MarkdownTableAutoModeEventListener\""
		);
		expectNotContains("auto mode widgets are not registered in the right status bar area", pluginXml, "<statusBarWidgetFactory");
		expectContains("plugin declares resource bundle", pluginXml, "<resource-bundle>messages.MarkdownTableEditorBundle</resource-bundle>");
		expectNotContains("actions do not hardcode English text", pluginXml, "text=\"Align Table\"");
		expectNotContains("actions do not hardcode English descriptions", pluginXml, "description=\"Align the Markdown table at the caret\"");
		expectActionBundleKeys();
		expectEquals("default action shortcuts", expectedDefaultShortcuts(), readActionShortcuts(processedPluginXmlPath.toFile()));
		expectEquals("status bar widget factories are not used for left buttons", expectedStatusBarWidgetFactories(), readStatusBarWidgetFactories(processedPluginXmlPath.toFile()));
		for (String forbiddenShortcut : List.of("ctrl alt A", "ctrl alt LEFT", "ctrl alt RIGHT", "ctrl alt UP", "ctrl alt DOWN", "ctrl alt shift LEFT", "ctrl alt shift RIGHT")) {
			expectNotContains("conflicting shortcut is removed: " + forbiddenShortcut, pluginXml, "first-keystroke=\"" + forbiddenShortcut + "\"");
		}
		expectContains("sort action descriptor", pluginXml, "MarkdownTableEditor.SortAscending");
		expectContains("csv action descriptor", pluginXml, "MarkdownTableEditor.ConvertCsvTsv");
		expectContains("insert table action descriptor", pluginXml, "MarkdownTableEditor.InsertTable");
		expectContains("marketplace english description", pluginXml, "Markdown Table Editor keeps Markdown pipe tables readable");
		expectContains("marketplace change notes", pluginXml, "<change-notes><![CDATA[");
		expectTrue("plugin icon exists", Files.exists(Path.of("src", "main", "resources", "META-INF", "pluginIcon.svg")));
		expectTrue("license exists", Files.exists(Path.of("LICENSE")));
		MarkdownTableGoldenFixtures.run();

		List<String> input = List.of(
			"| Name | Age |",
			"| --- | ---: |",
			"| Anna | 20 |",
			"| Alexander | 7 |"
		);

		MarkdownTableCore.EditResult aligned = MarkdownTableCore.apply(input, 2, 0, MarkdownTableCore.Action.ALIGN);
		expectTrue("align ok", aligned.ok);
		expectLines("align", aligned.lines, List.of(
			"| Name      | Age |",
			"| --------- | --: |",
			"| Anna      |  20 |",
			"| Alexander |   7 |"
		));

		MarkdownTableCore.EditResult plainPipe = MarkdownTableCore.apply(List.of("Use A | B in text"), 0, 0, MarkdownTableCore.Action.ALIGN);
		expectTrue("plain pipe is not table", !plainPipe.ok);
		expectTrue("plain pipe keeps empty result", plainPipe.lines.isEmpty());
		expectTrue("negative row range is rejected", !MarkdownTableCore.findTableRange(input, -1).found);
		expectLines("negative row and column clamp to first cell", MarkdownTableCore.apply(input, -1, -1, MarkdownTableCore.Action.ALIGN).lines, List.of(
			"| Name      | Age |",
			"| --------- | --: |",
			"| Anna      |  20 |",
			"| Alexander |   7 |"
		));
		expectTrue("pipe-only separator is not table", !MarkdownTableCore.findTableRange(List.of("| A | B |", "|   |   |", "| 1 | 2 |"), 2).found);
		expectTrue("equals short separator is table", MarkdownTableCore.findTableRange(List.of("| A | B |", "| === | === |", "| 1 | 2 |"), 2).found);

		List<String> adjacentPipeText = List.of(
			"Use A | B in text",
			"| H | V |",
			"| --- | --- |",
			"| a | b |",
			"Next A | B"
		);
		MarkdownTableCore.TableRange adjacentTable = MarkdownTableCore.findTableRange(adjacentPipeText, 3);
		expectTrue("adjacent pipe range found", adjacentTable.found);
		expectInt("adjacent pipe range start", adjacentTable.firstRow, 1);
		expectInt("adjacent pipe range end", adjacentTable.lastRow, 3);
		expectTrue("adjacent pipe text before rejected", !MarkdownTableCore.findTableRange(adjacentPipeText, 0).found);
		expectTrue("adjacent pipe text after rejected", !MarkdownTableCore.findTableRange(adjacentPipeText, 4).found);
		expectTrue("empty eof line after table rejected", !MarkdownTableCore.findTableRange(List.of("| A |", "| --- |", "| 1 |", ""), 3).found);
		MarkdownTableCore.EditResult adjacentApply = MarkdownTableCore.apply(adjacentPipeText, 3, 0, MarkdownTableCore.Action.ALIGN);
		expectTrue("adjacent pipe apply ok", adjacentApply.ok);
		expectLines("adjacent pipe apply excludes text", adjacentApply.lines, List.of(
			"| H   | V   |",
			"| --- | --- |",
			"| a   | b   |"
		));
		expectTrue("adjacent pipe text before apply rejected", !MarkdownTableCore.apply(adjacentPipeText, 0, 0, MarkdownTableCore.Action.ALIGN).ok);
		expectTrue("adjacent pipe text after apply rejected", !MarkdownTableCore.apply(adjacentPipeText, 4, 0, MarkdownTableCore.Action.ALIGN).ok);

		MarkdownTableCore.EditResult next = MarkdownTableCore.apply(input, 3, 1, MarkdownTableCore.Action.NEXT_CELL);
		expectTrue("next cell ok", next.ok);
		expectInt("next cell row count", next.lines.size(), 5);
		expectInt("next cell target row", next.targetRow, 4);
		expectInt("next cell target column", next.targetColumn, 0);

		MarkdownTableCore.EditResult previous = MarkdownTableCore.apply(input, 2, 0, MarkdownTableCore.Action.PREVIOUS_CELL);
		expectTrue("previous cell ok", previous.ok);
		expectInt("previous cell skips separator", previous.targetRow, 0);
		expectInt("previous cell wraps to last column", previous.targetColumn, 1);

		MarkdownTableCore.EditResult insertColumn = MarkdownTableCore.apply(input, 2, 0, MarkdownTableCore.Action.INSERT_COLUMN_RIGHT);
		expectTrue("insert column ok", insertColumn.ok);
		expectLines("insert column", insertColumn.lines, List.of(
			"| Name      |     | Age |",
			"| --------- | --- | --: |",
			"| Anna      |     |  20 |",
			"| Alexander |     |   7 |"
		));

		expectLines("delete column preserves alignment", MarkdownTableCore.apply(input, 2, 0, MarkdownTableCore.Action.DELETE_COLUMN).lines, List.of(
			"| Age |",
			"| --: |",
			"|  20 |",
			"|   7 |"
		));

		MarkdownTableCore.EditResult deleteLastColumn = MarkdownTableCore.apply(input, 2, 1, MarkdownTableCore.Action.DELETE_COLUMN);
		expectTrue("delete last column ok", deleteLastColumn.ok);
		expectInt("delete last column target", deleteLastColumn.targetColumn, 0);
		expectLines("delete last column", deleteLastColumn.lines, List.of(
			"| Name      |",
			"| --------- |",
			"| Anna      |",
			"| Alexander |"
		));

		expectLines("move column preserves alignment", MarkdownTableCore.apply(input, 2, 1, MarkdownTableCore.Action.MOVE_COLUMN_LEFT).lines, List.of(
			"| Age | Name      |",
			"| --: | --------- |",
			"|  20 | Anna      |",
			"|   7 | Alexander |"
		));

		expectLines("move column right", MarkdownTableCore.apply(
			List.of(
				"| A | B | C |",
				"| --- | --- | --- |",
				"| 1 | 2 | 3 |"
			),
			2,
			0,
			MarkdownTableCore.Action.MOVE_COLUMN_RIGHT
		).lines, List.of(
			"| B   | A   | C   |",
			"| --- | --- | --- |",
			"| 2   | 1   | 3   |"
		));

		expectLines("sort rows ascending text", MarkdownTableCore.apply(
			List.of(
				"| Name | Age |",
				"| --- | ---: |",
				"| Zoe | 9 |",
				"| Anna | 12 |",
				"| Bob | 3 |"
			),
			2,
			0,
			MarkdownTableCore.Action.SORT_ASCENDING
		).lines, List.of(
			"| Name | Age |",
			"| ---- | --: |",
			"| Anna |  12 |",
			"| Bob  |   3 |",
			"| Zoe  |   9 |"
		));

		expectLines("sort rows descending numeric", MarkdownTableCore.apply(
			List.of(
				"| Name | Age |",
				"| --- | ---: |",
				"| Zoe | 9 |",
				"| Anna | 12 |",
				"| Bob | 3 |"
			),
			2,
			1,
			MarkdownTableCore.Action.SORT_DESCENDING
		).lines, List.of(
			"| Name | Age |",
			"| ---- | --: |",
			"| Anna |  12 |",
			"| Zoe  |   9 |",
			"| Bob  |   3 |"
		));

		MarkdownTableCore.EditResult sortTarget = MarkdownTableCore.apply(
			List.of(
				"| Name | Score |",
				"| --- | ---: |",
				"| Anna | 42 |",
				"| Dmitry | 7 |",
				"| Chen | 100 |"
			),
			2,
			1,
			MarkdownTableCore.Action.SORT_ASCENDING
		);
		expectTrue("sort target ok", sortTarget.ok);
		expectInt("sort target follows current row", sortTarget.targetRow, 3);

		expectLines("sort rows ascending keeps stable ties", MarkdownTableCore.apply(
			List.of(
				"| Name | Score |",
				"| --- | ---: |",
				"| Anna | 2 |",
				"| Boris | 10 |",
				"| Chen | 2 |"
			),
			2,
			1,
			MarkdownTableCore.Action.SORT_ASCENDING
		).lines, List.of(
			"| Name  | Score |",
			"| ----- | ----: |",
			"| Anna  |     2 |",
			"| Chen  |     2 |",
			"| Boris |    10 |"
		));

		expectLines("sort rows ascending decimal numbers", MarkdownTableCore.apply(
			List.of(
				"| Item | Value |",
				"| --- | ---: |",
				"| A | -1.5 |",
				"| B | 10 |",
				"| C | 2.25 |"
			),
			2,
			1,
			MarkdownTableCore.Action.SORT_ASCENDING
		).lines, List.of(
			"| Item | Value |",
			"| ---- | ----: |",
			"| A    |  -1.5 |",
			"| C    |  2.25 |",
			"| B    |    10 |"
		));

		expectLines("sort rows ascending cyrillic case fold", MarkdownTableCore.apply(
			List.of(
				"| Name |",
				"| --- |",
				"| Яна |",
				"| борис |",
				"| Анна |"
			),
			2,
			0,
			MarkdownTableCore.Action.SORT_ASCENDING
		).lines, List.of(
			"| Name  |",
			"| ----- |",
			"| Анна  |",
			"| борис |",
			"| Яна   |"
		));

		expectLines("sort table without data rows", MarkdownTableCore.apply(
			List.of(
				"| H |",
				"| --- |"
			),
			0,
			0,
			MarkdownTableCore.Action.SORT_ASCENDING
		).lines, List.of(
			"| H   |",
			"| --- |"
		));

		expectLines("csv to markdown table", MarkdownTableCore.fromDelimited("""
			Name,Role,Note
			Anna,Developer,"uses, commas"
			Bob,QA,"pipe | escaped"
			""").lines, List.of(
			"| Name | Role      | Note            |",
			"| ---- | --------- | --------------- |",
			"| Anna | Developer | uses, commas    |",
			"| Bob  | QA        | pipe \\| escaped |"
		));

		expectLines("quoted csv to markdown table", MarkdownTableCore.fromDelimited("Name,Note\nAnna,\"A, B\"\nBob,\"said \"\"hi\"\"\"").lines, List.of(
			"| Name | Note      |",
			"| ---- | --------- |",
			"| Anna | A, B      |",
			"| Bob  | said \"hi\" |"
		));

		expectLines("csv escaped quotes and crlf", MarkdownTableCore.fromDelimited(
			"Name,Quote,Raw\r\n" +
				"Anna,\"He said \"\"Hi\"\"\",a\"b\r\n" +
				"\r\n" +
				"Bob,\"line\r\nbreak\",x"
		).lines, List.of(
			"| Name | Quote        | Raw |",
			"| ---- | ------------ | --- |",
			"| Anna | He said \"Hi\" | a\"b |",
			"| Bob  | line break   | x   |"
		));

		expectLines("csv crlf and multiline quoted cell", MarkdownTableCore.fromDelimited("Name,Note\r\nAnna,\"line 1\r\nline 2\"\r\nBob,done\r\n").lines, List.of(
			"| Name | Note          |",
			"| ---- | ------------- |",
			"| Anna | line 1 line 2 |",
			"| Bob  | done          |"
		));

		expectLines("delimiter ignores commas inside quotes", MarkdownTableCore.fromDelimited("Name\tNote\nAnna\t\"A,B\"").lines, List.of(
			"| Name | Note |",
			"| ---- | ---- |",
			"| Anna | A,B  |"
		));

		expectLines("tsv to markdown table", MarkdownTableCore.fromDelimited("Name\tScore\nAnna\t10\nBob\t2").lines, List.of(
			"| Name | Score |",
			"| ---- | ----- |",
			"| Anna | 10    |",
			"| Bob  | 2     |"
		));

		expectLines("tsv with commas", MarkdownTableCore.fromDelimited("Name\tNote\nAnna\tuses, commas\nBob\tkeeps tabs").lines, List.of(
			"| Name | Note         |",
			"| ---- | ------------ |",
			"| Anna | uses, commas |",
			"| Bob  | keeps tabs   |"
		));

		expectLines("tsv to table pads uneven rows", MarkdownTableCore.fromDelimited("A\tB\tC\n1\t2\n3\t4\t5").lines, List.of(
			"| A   | B   | C   |",
			"| --- | --- | --- |",
			"| 1   | 2   |     |",
			"| 3   | 4   | 5   |"
		));
		expectLines("csv trims surrounding blank lines", MarkdownTableCore.fromDelimited("  \r\nName,Role\r\nAnna,Engineer\r\n  ").lines, List.of(
			"| Name | Role     |",
			"| ---- | -------- |",
			"| Anna | Engineer |"
		));

		MarkdownTableCore.EditResult largeFishCsv = MarkdownTableCore.fromDelimited(
			"Name,Story,Score\r\n" +
				"Anna,\"Lorem ipsum dolor sit amet, consectetur adipiscing elit, with comma\",42\r\n" +
				"Борис,\"Длинная рыба с переносом\r\nвнутри кавычек и pipe |\",7\r\n" +
				"Chen,\"CJK 表 with comma, quote \"\"ok\"\", and more filler text\",100\r\n" +
				"Delta,\"one two three four five six seven eight nine ten\",-12.5\r\n"
		);
		expectTrue("large fish csv ok", largeFishCsv.ok);
		expectInt("large fish csv line count", largeFishCsv.lines.size(), 6);
		String largeFishCsvText = String.join("\n", largeFishCsv.lines);
		expectContains("large fish csv keeps lorem", largeFishCsvText, "Lorem ipsum dolor sit amet");
		expectContains("large fish csv flattens newline", largeFishCsvText, "переносом внутри кавычек");
		expectContains("large fish csv escapes pipe", largeFishCsvText, "pipe \\|");
		expectContains("large fish csv keeps quotes", largeFishCsvText, "quote \"ok\"");

		StringBuilder hugeCsv = new StringBuilder("Id,Text,Score\r\n");
		for (int i = 0; i < 220; i++) {
			hugeCsv
				.append("row-").append(i).append(",\"")
				.append("Lorem ipsum dolor sit amet, consectetur adipiscing elit, with comma, ")
				.append("quote \"\"ok\"\", pipe |, кириллица, 表, and generated chunk ").append(i)
				.append("\",").append(i - 110).append("\r\n");
		}
		MarkdownTableCore.EditResult hugeCsvTable = MarkdownTableCore.fromDelimited(hugeCsv.toString());
		expectTrue("huge generated csv ok", hugeCsvTable.ok);
		expectInt("huge generated csv line count", hugeCsvTable.lines.size(), 222);
		String hugeCsvText = String.join("\n", hugeCsvTable.lines);
		expectContains("huge generated csv keeps first row", hugeCsvText, "row-0");
		expectContains("huge generated csv keeps last row", hugeCsvText, "row-219");
		expectContains("huge generated csv escapes pipe", hugeCsvText, "pipe \\|");
		expectContains("huge generated csv keeps quotes", hugeCsvText, "quote \"ok\"");

		expectTrue("plain text is not csv", !MarkdownTableCore.fromDelimited("just a note").ok);
		expectTrue("plain multiline text is not csv", !MarkdownTableCore.fromDelimited("first line\nsecond line").ok);
		expectTrue("single quoted comma cell is not csv", !MarkdownTableCore.fromDelimited("\"just, a note\"").ok);
		expectTrue("empty csv is rejected", !MarkdownTableCore.fromDelimited("  \r\n  ").ok);
		expectTrue("invalid table size is rejected", !MarkdownTableCore.newTable(0, 2).ok);
		expectTrue("negative column count is rejected", !MarkdownTableCore.newTable(-1, 2).ok);
		expectTrue("negative table size is rejected", !MarkdownTableCore.newTable(2, -1).ok);

		expectLines("new table by size", MarkdownTableCore.newTable(3, 2).lines, List.of(
			"| Column 1 | Column 2 | Column 3 |",
			"| -------- | -------- | -------- |",
			"|          |          |          |",
			"|          |          |          |"
		));
		MarkdownTableCore.EditResult created = MarkdownTableCore.newTable(3, 2);
		expectTrue("new table target ok", created.ok);
		expectInt("new table target row", created.targetRow, 2);
		expectInt("new table target column", created.targetColumn, 0);
		MarkdownTableCore.EditResult headerOnlyTable = MarkdownTableCore.newTable(1, 0);
		expectTrue("new table without data ok", headerOnlyTable.ok);
		expectInt("new table without data target row", headerOnlyTable.targetRow, 0);

		List<String> unwrapped = List.of(
			"Name | Age",
			"--- | ---:",
			"Anna | 20"
		);
		expectInt("unwrapped cursor first cell", MarkdownTableCore.columnFromCursor(unwrapped.get(2), 1), 0);
		expectInt("unwrapped cursor second cell", MarkdownTableCore.columnFromCursor(unwrapped.get(2), 7), 1);
		expectLines("unwrapped align", MarkdownTableCore.apply(unwrapped, 2, 1, MarkdownTableCore.Action.ALIGN).lines, List.of(
			"Name | Age",
			"---- | --:",
			"Anna |  20"
		));

		MarkdownTableCore.EditResult wrappedLongCells = MarkdownTableCore.apply(
			List.of(
				"| Key | Value |",
				"| --- | --- |",
				"| row | alpha beta gamma delta epsilon zeta eta theta |"
			),
			2,
			1,
			MarkdownTableCore.Action.WRAP_LONG_CELLS
		);
		expectTrue("wrap long cells ok", wrappedLongCells.ok);
		expectInt("wrap long cells target row", wrappedLongCells.targetRow, 2);
		expectInt("wrap long cells target column", wrappedLongCells.targetColumn, 1);
		expectLines("wrap long cells", wrappedLongCells.lines, List.of(
			"| Key | Value                  |",
			"| --- | ---------------------- |",
			"| row | alpha beta gamma delta |",
			"|     | epsilon zeta eta theta |"
		));

		MarkdownTableCore.EditResult narrowedColumn = MarkdownTableCore.apply(
			List.of(
				"| Key | Value |",
				"| --- | --- |",
				"| row | alpha beta gamma |"
			),
			2,
			1,
			MarkdownTableCore.Action.NARROW_COLUMN
		);
		expectTrue("narrow column ok", narrowedColumn.ok);
		expectInt("narrow column target row", narrowedColumn.targetRow, 2);
		expectInt("narrow column target column", narrowedColumn.targetColumn, 1);
		expectLines("narrow column wraps current column", narrowedColumn.lines, List.of(
			"| Key | Value           |",
			"| --- | --------------- |",
			"| row | alpha beta      |",
			"|     | gamma           |"
		));

		MarkdownTableCore.EditResult widenedColumn = MarkdownTableCore.apply(narrowedColumn.lines, 2, 1, MarkdownTableCore.Action.WIDEN_COLUMN);
		expectTrue("widen column ok", widenedColumn.ok);
		expectLines("widen column rejoins current column", widenedColumn.lines, List.of(
			"| Key | Value            |",
			"| --- | ---------------- |",
			"| row | alpha beta gamma |"
		));

		MarkdownTableCore.EditResult narrowedCjkColumn = MarkdownTableCore.apply(
			List.of(
				"| A | B |",
				"| --- | --- |",
				"| x | 漢字 alpha |"
			),
			2,
			1,
			MarkdownTableCore.Action.NARROW_COLUMN
		);
		expectTrue("narrow column cjk ok", narrowedCjkColumn.ok);
		expectLines("narrow column uses cjk display width", narrowedCjkColumn.lines, List.of(
			"| A   | B         |",
			"| --- | --------- |",
			"| x   | 漢字      |",
			"|     | alpha     |"
		));

		MarkdownTableCore.EditResult autoWrapped = MarkdownTableCore.applyWrappedToWidth(
			List.of(
				"| Summary | Type | Priority | Reason | Id |",
				"| --- | --- | --- | --- | --- |",
				"| alpha beta gamma delta epsilon zeta eta theta | Task | High | one two three four five six seven | `abcdef0123456789` |"
			),
			2,
			0,
			90
		);
		expectTrue("auto wrap to table width ok", autoWrapped.ok);
		expectTrue("auto wrap creates inner cell continuation lines", autoWrapped.lines.size() > 3);
		expectLineLengthAtMost("auto wrap keeps physical lines inside width", autoWrapped.lines, 90);
		expectTrue("auto wrap keeps following columns on first segment", autoWrapped.lines.get(2).contains("| Task | High"));
		expectTrue("auto wrap moves text continuation inside the table", autoWrapped.lines.get(3).contains("|"));

		MarkdownTableCore.EditResult expandedWrapped = MarkdownTableCore.applyWrappedToWidth(
			autoWrapped.lines,
			2,
			0,
			150
		);
		expectTrue("expanded auto wrap ok", expandedWrapped.ok);
		expectTrue("expanded auto wrap reduces continuation rows", expandedWrapped.lines.size() < autoWrapped.lines.size());
		expectLineLengthAtMost("expanded auto wrap keeps physical lines inside wider width", expandedWrapped.lines, 150);

		MarkdownTableCore.EditResult veryNarrowWrapped = MarkdownTableCore.applyWrappedToWidth(
			List.of(
				"| A | B | C |",
				"| --- | --- | --- |",
				"| supercalifragilistic | word wrap here | tail |"
			),
			2,
			0,
			18
		);
		expectTrue("very narrow auto wrap ok", veryNarrowWrapped.ok);
		expectTrue("very narrow auto wrap splits word by letters", veryNarrowWrapped.lines.size() > 8);
		expectLineLengthAtMost("very narrow auto wrap keeps right edge visible", veryNarrowWrapped.lines, 18);

		MarkdownTableCore.EditResult joinedSplitWord = MarkdownTableCore.applyWrappedToWidth(
			List.of(
				"| A | B |",
				"| --- | --- |",
				"| scrip | keep |",
				"| t already | |"
			),
			2,
			0,
			80
		);
		expectTrue("expanded auto wrap rejoins split word ok", joinedSplitWord.ok);
		expectContains("expanded auto wrap rejoins split word without inserted space", joinedSplitWord.lines.get(2), "script already");
		expectNotContains("expanded auto wrap does not keep split word space", joinedSplitWord.lines.get(2), "scrip t");

		MarkdownTableCore.EditResult joinedSpacedWords = MarkdownTableCore.applyWrappedToWidth(
			List.of(
				"| A | B |",
				"| --- | --- |",
				"| word | keep |",
				"| wrap here | |"
			),
			2,
			0,
			80
		);
		expectTrue("expanded auto wrap preserves word-boundary space ok", joinedSpacedWords.ok);
		expectContains("expanded auto wrap preserves word-boundary space", joinedSpacedWords.lines.get(2), "word wrap here");
		expectNotContains("expanded auto wrap does not join separate words", joinedSpacedWords.lines.get(2), "wordwrap");

		MarkdownTableCore.EditResult registryWrapped = MarkdownTableCore.applyWrappedToWidth(
			List.of(
				"| patch_id | date | component | target_path | change | evidence | rollback | status | notes |",
				"| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
				"| codex-admin-launcher-2026-06-01 | 2026-06-01 | Codex launcher | C:/Users/KRT/.codex/launchers/Start-Codex-Elevated.ps1 | Created local launcher that fixes RunAs shortcuts and starts Codex with elevated rights | Service mark and log file confirm successful launch | Delete managed shortcuts and use stock package | active | prefers patched local shell |"
			),
			2,
			0,
			112
		);
		expectTrue("registry auto wrap ok", registryWrapped.ok);
		expectTrue("registry auto wrap creates continuation rows", registryWrapped.lines.size() > 3);
		expectLineLengthAtMost("registry auto wrap keeps physical lines inside width", registryWrapped.lines, 112);
		boolean registryRightEdgeVisible = true;
		for (String line : registryWrapped.lines) {
			registryRightEdgeVisible = registryRightEdgeVisible && !line.isEmpty() && line.charAt(line.length() - 1) == '|';
		}
		expectTrue("registry auto wrap keeps right table edge on every physical line", registryRightEdgeVisible);
		expectTrue("registry auto wrap keeps status column on first segment", registryWrapped.lines.get(2).contains("| active |"));

		MarkdownTableCore.EditResult wrappedProtectedTokens = MarkdownTableCore.apply(
			List.of(
				"| Key | Value | Other |",
				"| --- | --- | --- |",
				"| protected | before [Codex Desktop registry](<C:/tmp/patch registry.md>) after ``code span with spaces`` alpha beta gamma delta epsilon zeta | empty |",
				"| empty | | [broken bracket text keeps moving across words |"
			),
			2,
			1,
			MarkdownTableCore.Action.WRAP_LONG_CELLS
		);
		expectTrue("wrap protected tokens ok", wrappedProtectedTokens.ok);
		expectTrue("wrap protected tokens expands rows", wrappedProtectedTokens.lines.size() > 5);
		String wrappedProtectedText = String.join("\n", wrappedProtectedTokens.lines);
		expectContains("wrap splits long markdown link token", wrappedProtectedText, "[Codex Desktop registry](");
		expectContains("wrap keeps markdown link remainder", wrappedProtectedText, "/patch registry.md>)");
		expectNotContains("wrap no longer keeps overwide markdown link token", wrappedProtectedText, "[Codex Desktop registry](<C:/tmp/patch registry.md>)");
		expectContains("wrap keeps code span token", wrappedProtectedText, "``code span with spaces``");
		expectContains("wrap keeps malformed bracket token text", wrappedProtectedText, "[broken");

		String escaped = "| a \\| b | c |";
		expectTrue("escaped pipe potential", MarkdownTableCore.isPotentialTableLine(escaped));
		expectTrue("only escaped pipe is not table", !MarkdownTableCore.isPotentialTableLine("a \\| b"));
		expectTrue("double escaped pipe is table", MarkdownTableCore.isPotentialTableLine("a \\\\| b"));
		expectInt("escaped pipe cursor", MarkdownTableCore.columnFromCursor(escaped, escaped.indexOf("c")), 1);
		expectInt("cursor with leading spaces and pipe", MarkdownTableCore.columnFromCursor("  | a | b |", 8), 1);
		expectLines("escaped pipe align", MarkdownTableCore.apply(
			List.of(
				"| H | X |",
				"| --- | --- |",
				"| a \\| b | c |"
			),
			2,
			0,
			MarkdownTableCore.Action.ALIGN
		).lines, List.of(
			"| H      | X   |",
			"| ------ | --- |",
			"| a \\| b | c   |"
		));

		expectLines("unicode width", MarkdownTableCore.apply(
			List.of(
				"| Word | Value |",
				"| --- | --- |",
				"| тест | 1 |",
				"| 表 | 22 |"
			),
			2,
			0,
			MarkdownTableCore.Action.ALIGN
		).lines, List.of(
			"| Word | Value |",
			"| ---- | ----- |",
			"| тест | 1     |",
			"| 表   | 22    |"
		));

		expectLines("alignment variants", MarkdownTableCore.apply(
			List.of(
				"| Left | Center | Right | Plain |",
				"| :--- | :---: | ---: | --- |",
				"| a | b | c | d |",
				"| long | wide | 123 | text |"
			),
			2,
			0,
			MarkdownTableCore.Action.ALIGN
		).lines, List.of(
			"| Left | Center | Right | Plain |",
			"| :--- | :----: | ----: | ----- |",
			"| a    |   b    |     c | d     |",
			"| long |  wide  |   123 | text  |"
		));

		MarkdownTableCore.EditResult largeFishTable = MarkdownTableCore.apply(
			List.of(
				"| Topic | Description | Note |",
				"| --- | --- | --- |",
				"| Lorem | Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. | alpha |",
				"| Рыба | Съешь еще этих мягких французских булок, да выпей чаю; длинная проверка ширины кириллицы. | beta \\| pipe |",
				"| CJK | 表示確認のための長いセルと punctuation, commas, and quotes \"ok\". | gamma |",
				"| Mixed | Markdown \\| escaped pipe stays in one cell while text keeps going for a wider table. | delta |",
				"| Numbers | 1234567890 9876543210 3.1415926535 -42.75 and padded numeric-looking prose. | epsilon |",
				"| URL | https://example.com/path/to/a/very/long/resource?query=markdown-table-editor&fixture=fish | zeta |"
			),
			2,
			0,
			MarkdownTableCore.Action.ALIGN
		);
		expectTrue("large fish table ok", largeFishTable.ok);
		expectInt("large fish table line count", largeFishTable.lines.size(), 8);
		String largeFishText = String.join("\n", largeFishTable.lines);
		expectContains("large fish keeps lorem", largeFishText, "Lorem ipsum dolor sit amet");
		expectContains("large fish keeps cyrillic", largeFishText, "Съешь еще этих мягких французских булок");
		expectContains("large fish keeps cjk", largeFishText, "表示確認");
		expectContains("large fish keeps escaped pipe", largeFishText, "Markdown \\| escaped pipe");
		expectContains("large fish keeps url", largeFishText, "https://example.com/path/to/a/very/long/resource");

		List<String> hugeTableLines = new java.util.ArrayList<>();
		hugeTableLines.add("| Id | Payload | Note |");
		hugeTableLines.add("| --- | --- | --- |");
		String hugePayload =
			"Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua; " +
				"Съешь еще этих мягких французских булок для проверки длинной кириллицы; " +
				"表示確認 and Markdown \\| escaped pipe remain stable.";
		for (int i = 0; i < 160; i++) {
			hugeTableLines.add("| row-" + i + " | " + hugePayload + " chunk-" + i + " | note-" + i + " |");
		}
		MarkdownTableCore.EditResult hugeTable = MarkdownTableCore.apply(hugeTableLines, 80, 1, MarkdownTableCore.Action.ALIGN);
		expectTrue("huge generated table ok", hugeTable.ok);
		expectInt("huge generated table line count", hugeTable.lines.size(), 162);
		String hugeTableText = String.join("\n", hugeTable.lines);
		expectContains("huge generated table keeps first row", hugeTableText, "row-0");
		expectContains("huge generated table keeps last row", hugeTableText, "row-159");
		expectContains("huge generated table keeps long payload", hugeTableText, "Lorem ipsum dolor sit amet");
		expectContains("huge generated table keeps escaped pipe", hugeTableText, "Markdown \\| escaped pipe");

		MarkdownTableCore.EditResult deleteLast = MarkdownTableCore.apply(
			List.of(
				"| H | V |",
				"| --- | --- |",
				"| a | b |"
			),
			2,
			0,
			MarkdownTableCore.Action.DELETE_ROW
		);
		expectTrue("delete last data row ok", deleteLast.ok);
		expectInt("delete last data row target", deleteLast.targetRow, 0);
		expectLines("delete last data row", deleteLast.lines, List.of(
			"| H   | V   |",
			"| --- | --- |"
		));

		expectLines("keep markdown header row", MarkdownTableCore.apply(
			List.of(
				"| H | V |",
				"| --- | --- |",
				"| a | b |"
			),
			0,
			0,
			MarkdownTableCore.Action.DELETE_ROW
		).lines, List.of(
			"| H   | V   |",
			"| --- | --- |",
			"| a   | b   |"
		));

		expectLines("keep separator row", MarkdownTableCore.apply(
			List.of(
				"| H | V |",
				"| --- | --- |",
				"| a | b |"
			),
			1,
			0,
			MarkdownTableCore.Action.DELETE_ROW
		).lines, List.of(
			"| H   | V   |",
			"| --- | --- |",
			"| a   | b   |"
		));

		MarkdownTableCore.EditResult insertRowBelowHeader = MarkdownTableCore.apply(
			List.of(
				"| H | V |",
				"| --- | --- |"
			),
			0,
			0,
			MarkdownTableCore.Action.INSERT_ROW_BELOW
		);
		expectTrue("insert row below header ok", insertRowBelowHeader.ok);
		expectInt("insert row below header target", insertRowBelowHeader.targetRow, 2);
		expectLines("insert row below header", insertRowBelowHeader.lines, List.of(
			"| H   | V   |",
			"| --- | --- |",
			"|     |     |"
		));

		expectLines("keep only editable row", MarkdownTableCore.apply(
			List.of(
				"| H | V |",
				"| --- | --- |"
			),
			0,
			0,
			MarkdownTableCore.Action.DELETE_ROW
		).lines, List.of(
			"| H   | V   |",
			"| --- | --- |"
		));

		expectLines("move column left", MarkdownTableCore.apply(
			List.of(
				"| A | B | C |",
				"| --- | --- | --- |",
				"| 1 | 2 | 3 |"
			),
			2,
			2,
			MarkdownTableCore.Action.MOVE_COLUMN_LEFT
		).lines, List.of(
			"| A   | C   | B   |",
			"| --- | --- | --- |",
			"| 1   | 3   | 2   |"
		));

		expectLines("move row up", MarkdownTableCore.apply(
			List.of(
				"| A |",
				"| --- |",
				"| 1 |",
				"| 2 |"
			),
			3,
			0,
			MarkdownTableCore.Action.MOVE_ROW_UP
		).lines, List.of(
			"| A   |",
			"| --- |",
			"| 2   |",
			"| 1   |"
		));

		expectLines("keep header above separator", MarkdownTableCore.apply(
			List.of(
				"| A |",
				"| --- |",
				"| 1 |"
			),
			0,
			0,
			MarkdownTableCore.Action.MOVE_ROW_DOWN
		).lines, List.of(
			"| A   |",
			"| --- |",
			"| 1   |"
		));

		expectLines("move row up blocked by separator", MarkdownTableCore.apply(
			List.of(
				"| A |",
				"| --- |",
				"| 1 |"
			),
			2,
			0,
			MarkdownTableCore.Action.MOVE_ROW_UP
		).lines, List.of(
			"| A   |",
			"| --- |",
			"| 1   |"
		));

		expectLines("move row down", MarkdownTableCore.apply(
			List.of(
				"| A |",
				"| --- |",
				"| 1 |",
				"| 2 |"
			),
			2,
			0,
			MarkdownTableCore.Action.MOVE_ROW_DOWN
		).lines, List.of(
			"| A   |",
			"| --- |",
			"| 2   |",
			"| 1   |"
		));

		expectLines("delete only column keeps table", MarkdownTableCore.apply(
			List.of(
				"| A |",
				"| --- |",
				"| 1 |"
			),
			2,
			0,
			MarkdownTableCore.Action.DELETE_COLUMN
		).lines, List.of(
			"| A   |",
			"| --- |",
			"| 1   |"
		));

		MarkdownTableCoreScenarios.run();
		MarkdownTableEditorScenarios.run();
	}

	private static void expectTrue(String name, boolean value) {
		assertTrue(value, name);
	}

	private static void expectInt(String name, int actual, int expected) {
		assertEquals(expected, actual, name);
	}

	private static void expectEquals(String name, Map<String, String> expected, Map<String, String> actual) {
		assertEquals(expected, actual, name);
	}

	private static void expectEquals(String name, String expected, String actual) {
		assertEquals(expected, actual, name);
	}

	private static void expectLines(String name, List<String> actual, List<String> expected) {
		assertEquals(expected, actual, name);
	}

	private static void expectLineLengthAtMost(String name, List<String> lines, int maximum) {
		for (int i = 0; i < lines.size(); i++) {
			final int index = i;
			final String line = lines.get(i);
			final int length = line.length();
			assertTrue(length <= maximum, () -> name + " line " + index + " is " + length + ", expected at most " + maximum + ": " + line);
		}
	}

	private static void expectContains(String name, String actual, String expected) {
		assertTrue(actual.contains(expected), () -> name + " expected to contain: " + expected);
	}

	private static void expectNotContains(String name, String actual, String unexpected) {
		assertFalse(actual.contains(unexpected), () -> name + " expected not to contain: " + unexpected);
	}

	private static Map<String, String> expectedDefaultShortcuts() {
		LinkedHashMap<String, String> shortcuts = new LinkedHashMap<>();
		shortcuts.put("MarkdownTableEditor.TabAlign", "TAB");
		shortcuts.put("MarkdownTableEditor.Align", "ctrl alt shift 1");
		shortcuts.put("MarkdownTableEditor.AutoAlign", "ctrl alt shift A");
		shortcuts.put("MarkdownTableEditor.WrapLongCells", "ctrl alt shift W");
		shortcuts.put("MarkdownTableEditor.AutoFit", "ctrl alt shift F");
		shortcuts.put("MarkdownTableEditor.NextCell", "ctrl alt shift 2");
		shortcuts.put("MarkdownTableEditor.PreviousCell", "ctrl alt shift 3");
		shortcuts.put("MarkdownTableEditor.SortAscending", "ctrl alt shift EQUALS");
		shortcuts.put("MarkdownTableEditor.SortDescending", "ctrl alt shift MINUS");
		shortcuts.put("MarkdownTableEditor.ConvertCsvTsv", "ctrl alt shift 0");
		shortcuts.put("MarkdownTableEditor.InsertTable", "ctrl alt shift BACK_SLASH");
		shortcuts.put("MarkdownTableEditor.InsertRowBelow", "ctrl alt shift 4");
		shortcuts.put("MarkdownTableEditor.DeleteRow", "ctrl alt shift 5");
		shortcuts.put("MarkdownTableEditor.InsertColumnRight", "ctrl alt shift 6");
		shortcuts.put("MarkdownTableEditor.DeleteColumn", "ctrl alt shift 7");
		shortcuts.put("MarkdownTableEditor.NarrowColumn", "ctrl alt shift COMMA");
		shortcuts.put("MarkdownTableEditor.WidenColumn", "ctrl alt shift PERIOD");
		shortcuts.put("MarkdownTableEditor.MoveRowUp", "ctrl alt shift 8");
		shortcuts.put("MarkdownTableEditor.MoveRowDown", "ctrl alt shift 9");
		shortcuts.put("MarkdownTableEditor.MoveColumnLeft", "ctrl alt shift OPEN_BRACKET");
		shortcuts.put("MarkdownTableEditor.MoveColumnRight", "ctrl alt shift CLOSE_BRACKET");
		return shortcuts;
	}

	private static Map<String, String> expectedStatusBarWidgetFactories() {
		return new LinkedHashMap<>();
	}

	private static void expectActionBundleKeys() {
		File[] bundleFiles = Path.of("src", "main", "resources", "messages").toFile().listFiles((dir, name) ->
			name.startsWith("MarkdownTableEditorBundle") && name.endsWith(".properties"));
		expectInt("top speaker locale bundle count", bundleFiles == null ? 0 : bundleFiles.length, 20);

		ResourceBundle english = ResourceBundle.getBundle("messages.MarkdownTableEditorBundle", Locale.ROOT);
		ResourceBundle russian = ResourceBundle.getBundle("messages.MarkdownTableEditorBundle", Locale.forLanguageTag("ru"));
		expectEquals("english action text", "Align Table", english.getString("action.MarkdownTableEditor.Align.text"));
		expectEquals("english action description", "Align the Markdown table at the caret", english.getString("action.MarkdownTableEditor.Align.description"));
		expectEquals("english fit action text", "Fit Table Width to Editor", english.getString("action.MarkdownTableEditor.WrapLongCells.text"));
		expectEquals("english auto align action text", "Light Auto Align After Edit", english.getString("action.MarkdownTableEditor.AutoAlign.text"));
		expectEquals("english auto fit action text", "Power Auto Fit Table Width to Editor", english.getString("action.MarkdownTableEditor.AutoFit.text"));
		expectEquals("english narrow column action text", "Narrow Column", english.getString("action.MarkdownTableEditor.NarrowColumn.text"));
		expectEquals("english widen column action text", "Widen Column", english.getString("action.MarkdownTableEditor.WidenColumn.text"));
		expectEquals("english auto align status on", "Light Auto Align: On", english.getString("status.MarkdownTableEditor.AutoAlign.on"));
		expectEquals("english auto fit status on", "Power Auto Fit: On", english.getString("status.MarkdownTableEditor.AutoFit.on"));
		expectEquals("english group text", "Markdown Table Editor", english.getString("group.MarkdownTableEditor.Group.text"));
		expectEquals("russian action text", "Выровнять таблицу", russian.getString("action.MarkdownTableEditor.Align.text"));
		expectEquals("russian action description", "Выровнять Markdown-таблицу под курсором", russian.getString("action.MarkdownTableEditor.Align.description"));
		expectEquals("russian fit action text", "Подогнать ширину таблицы под редактор", russian.getString("action.MarkdownTableEditor.WrapLongCells.text"));
		expectEquals("russian auto align action text", "Light Автовыравнивание после правки", russian.getString("action.MarkdownTableEditor.AutoAlign.text"));
		expectEquals("russian auto fit action text", "Power Автоподгонка ширины таблицы под редактор", russian.getString("action.MarkdownTableEditor.AutoFit.text"));
		expectEquals("russian narrow column action text", "Сузить столбец", russian.getString("action.MarkdownTableEditor.NarrowColumn.text"));
		expectEquals("russian widen column action text", "Расширить столбец", russian.getString("action.MarkdownTableEditor.WidenColumn.text"));
		expectEquals("russian auto align status on", "Light Автовыравнивание: вкл", russian.getString("status.MarkdownTableEditor.AutoAlign.on"));
		expectEquals("russian auto fit status on", "Power Автоподгонка: вкл", russian.getString("status.MarkdownTableEditor.AutoFit.on"));
		expectEquals("russian group text", "Редактор Markdown-таблиц", russian.getString("group.MarkdownTableEditor.Group.text"));

		LinkedHashMap<String, String> localizedAlignText = new LinkedHashMap<>();
		localizedAlignText.put("zh-CN", "对齐表格");
		localizedAlignText.put("hi", "तालिका संरेखित करें");
		localizedAlignText.put("es", "Alinear tabla");
		localizedAlignText.put("ar", "محاذاة الجدول");
		localizedAlignText.put("fr", "Aligner le tableau");
		localizedAlignText.put("bn", "টেবিল সারিবদ্ধ করুন");
		localizedAlignText.put("pt", "Alinhar tabela");
		localizedAlignText.put("id", "Ratakan tabel");
		localizedAlignText.put("ur", "جدول سیدھا کریں");
		localizedAlignText.put("de", "Tabelle ausrichten");
		localizedAlignText.put("ja", "テーブルを整列");
		localizedAlignText.put("pcm", "Arrange table");
		localizedAlignText.put("mr", "तक्ता संरेखित करा");
		localizedAlignText.put("te", "పట్టికను సరిపరచు");
		localizedAlignText.put("tr", "Tabloyu hizala");
		localizedAlignText.put("ta", "அட்டவணையை ஒழுங்குபடுத்து");
		localizedAlignText.put("yue", "對齊表格");
		localizedAlignText.put("vi", "Căn chỉnh bảng");
		for (Map.Entry<String, String> entry : localizedAlignText.entrySet()) {
			ResourceBundle bundle = ResourceBundle.getBundle("messages.MarkdownTableEditorBundle", Locale.forLanguageTag(entry.getKey()));
			expectEquals(entry.getKey() + " action text", entry.getValue(), bundle.getString("action.MarkdownTableEditor.Align.text"));
			expectTrue(entry.getKey() + " action description exists", !bundle.getString("action.MarkdownTableEditor.Align.description").isBlank());
			expectTrue(entry.getKey() + " auto align action exists", !bundle.getString("action.MarkdownTableEditor.AutoAlign.text").isBlank());
			expectTrue(entry.getKey() + " auto fit action exists", !bundle.getString("action.MarkdownTableEditor.AutoFit.text").isBlank());
			expectTrue(entry.getKey() + " narrow column action exists", !bundle.getString("action.MarkdownTableEditor.NarrowColumn.text").isBlank());
			expectTrue(entry.getKey() + " widen column action exists", !bundle.getString("action.MarkdownTableEditor.WidenColumn.text").isBlank());
			expectTrue(entry.getKey() + " auto align status exists", !bundle.getString("status.MarkdownTableEditor.AutoAlign.on").isBlank());
			expectTrue(entry.getKey() + " auto fit status exists", !bundle.getString("status.MarkdownTableEditor.AutoFit.on").isBlank());
		}
	}

	private static Map<String, String> readActionShortcuts(File pluginXmlFile) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		Document document = factory.newDocumentBuilder().parse(pluginXmlFile);
		NodeList actions = document.getElementsByTagName("action");
		LinkedHashMap<String, String> shortcuts = new LinkedHashMap<>();
		Set<String> usedShortcuts = new HashSet<>();
		for (int actionIndex = 0; actionIndex < actions.getLength(); actionIndex++) {
			Element action = (Element)actions.item(actionIndex);
			String actionId = action.getAttribute("id");
			if (!actionId.startsWith("MarkdownTableEditor.")) {
				continue;
			}
			NodeList children = action.getChildNodes();
			int shortcutCount = 0;
			for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
				Node child = children.item(childIndex);
				if (!(child instanceof Element)) {
					continue;
				}
				Element shortcut = (Element)child;
				if (!"keyboard-shortcut".equals(shortcut.getTagName())) {
					continue;
				}
				shortcutCount++;
				String firstKeystroke = shortcut.getAttribute("first-keystroke");
				expectTrue(actionId + " shortcut uses default keymap", "$default".equals(shortcut.getAttribute("keymap")));
				expectTrue(actionId + " shortcut is unique: " + firstKeystroke, usedShortcuts.add(firstKeystroke));
				shortcuts.put(actionId, firstKeystroke);
			}
			expectInt(actionId + " has one shortcut", shortcutCount, 1);
		}
		return shortcuts;
	}

	private static Map<String, String> readStatusBarWidgetFactories(File pluginXmlFile) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		Document document = factory.newDocumentBuilder().parse(pluginXmlFile);
		NodeList widgets = document.getElementsByTagName("statusBarWidgetFactory");
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		for (int index = 0; index < widgets.getLength(); index++) {
			Element widget = (Element)widgets.item(index);
			String widgetId = widget.getAttribute("id");
			if (!widgetId.startsWith("markdownTable")) {
				continue;
			}
			result.put(widgetId, widget.getAttribute("implementation") + "|" + widget.getAttribute("order"));
		}
		return result;
	}
}
