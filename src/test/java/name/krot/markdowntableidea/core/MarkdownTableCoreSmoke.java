// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea.core;

import name.krot.markdowntableidea.MarkdownTableEditorScenarios;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MarkdownTableCoreSmoke {
	private static int checks;
	private static int failures;

	public static void main(String[] args) throws Exception {
		String pluginXml = Files.readString(Path.of("src", "main", "resources", "META-INF", "plugin.xml"));
		String projectVersion = Files.readString(Path.of("VERSION")).trim();
		expectContains("dynamic plugin descriptor", pluginXml, "require-restart=\"false\"");
		expectContains("plugin version matches VERSION", pluginXml, "<version>" + projectVersion + "</version>");
		expectContains("plugin compatibility starts at IDEA 2024.2", pluginXml, "<idea-version since-build=\"242\"");
		expectNotContains("plugin compatibility is not limited to 242.*", pluginXml, "until-build=\"242.*\"");
		expectNotContains("plugin compatibility has no upper build cap", pluginXml, "until-build=\"");
		expectContains("plugin uses platform-only dependency", pluginXml, "<depends>com.intellij.modules.platform</depends>");
		expectNotContains("plugin has no Java module dependency", pluginXml, "<depends>com.intellij.modules.java</depends>");
		expectNotContains("plugin has no IDEA-specific dependency", pluginXml, "<depends>com.intellij.modules.idea</depends>");
		expectContains("dynamic tab handler descriptor", pluginXml, "<editorActionHandler action=\"EditorTab\"");
		expectContains("dynamic tab handler implementation", pluginXml, "implementationClass=\"name.krot.markdowntableidea.MarkdownTableTabHandler\"");
		expectContains("tab handler runs before default tab processors", pluginXml, "order=\"first\"");
		expectContains("tab shortcut action descriptor", pluginXml, "MarkdownTableEditor.TabAlign");
		expectContains("tab shortcut action key", pluginXml, "first-keystroke=\"TAB\"");
		expectContains("align shortcut matches documentation", pluginXml, "first-keystroke=\"ctrl alt A\"");
		expectNotContains("align shortcut no longer uses old ctrl alt m", pluginXml, "ctrl alt M");
		expectContains("sort action descriptor", pluginXml, "MarkdownTableEditor.SortAscending");
		expectContains("csv action descriptor", pluginXml, "MarkdownTableEditor.ConvertCsvTsv");
		expectContains("insert table action descriptor", pluginXml, "MarkdownTableEditor.InsertTable");
		expectContains("marketplace english description", pluginXml, "Markdown Table Editor keeps Markdown pipe tables readable");
		expectContains("marketplace change notes", pluginXml, "<change-notes><![CDATA[");
		expectTrue("plugin icon exists", Files.exists(Path.of("src", "main", "resources", "META-INF", "pluginIcon.svg")));
		expectTrue("license exists", Files.exists(Path.of("LICENSE")));
		expectNotContains("no startup activity descriptor", pluginXml, "postStartupActivity");

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
		expectTrue("empty csv is rejected", !MarkdownTableCore.fromDelimited("  \r\n  ").ok);
		expectTrue("invalid table size is rejected", !MarkdownTableCore.newTable(0, 2).ok);
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

		failures += MarkdownTableCoreScenarios.run();
		failures += MarkdownTableEditorScenarios.run();

		if (failures != 0) {
			System.err.println(failures + " test(s) failed");
			System.exit(1);
		}

		System.out.println("Core smoke tests passed (" + checks + " checks)");
	}

	private static void expectTrue(String name, boolean value) {
		checks++;
		if (!value) {
			fail(name, "expected true");
		}
	}

	private static void expectInt(String name, int actual, int expected) {
		checks++;
		if (actual != expected) {
			fail(name, "expected " + expected + ", got " + actual);
		}
	}

	private static void expectLines(String name, List<String> actual, List<String> expected) {
		checks++;
		if (!actual.equals(expected)) {
			fail(name, "expected:\n" + String.join("\n", expected) + "\nactual:\n" + String.join("\n", actual));
		}
	}

	private static void expectContains(String name, String actual, String expected) {
		checks++;
		if (!actual.contains(expected)) {
			fail(name, "expected to contain: " + expected);
		}
	}

	private static void expectNotContains(String name, String actual, String unexpected) {
		checks++;
		if (actual.contains(unexpected)) {
			fail(name, "expected not to contain: " + unexpected);
		}
	}

	private static void fail(String name, String message) {
		failures++;
		System.err.println(name + " failed: " + message);
	}
}
