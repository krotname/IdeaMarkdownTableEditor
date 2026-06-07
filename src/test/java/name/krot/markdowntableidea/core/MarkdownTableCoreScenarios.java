package name.krot.markdowntableidea.core;

import java.util.ArrayList;
import java.util.List;

final class MarkdownTableCoreScenarios {
	private static int checks;
	private static int failures;

	private MarkdownTableCoreScenarios() {
	}

	static int run() {
		checks = 0;
		failures = 0;

		editorCommandScenarios();
		delimitedScenarios();
		largeDataScenarios();

		if (failures == 0) {
			System.out.println("Scenario unit tests passed (" + checks + " checks)");
		} else {
			System.err.println(failures + " scenario test(s) failed");
		}
		return failures;
	}

	private static void editorCommandScenarios() {
		List<String> table = List.of(
			"| Name | Age |",
			"| --- | ---: |",
			"| Anna | 20 |",
			"| Alexander | 7 |"
		);

		expectLines("align table", MarkdownTableCore.apply(table, 2, 0, MarkdownTableCore.Action.ALIGN).lines, List.of(
			"| Name      | Age |",
			"| --------- | --: |",
			"| Anna      |  20 |",
			"| Alexander |   7 |"
		));
		expectTrue("plain pipe rejected", !MarkdownTableCore.apply(List.of("Use A | B in text"), 0, 0, MarkdownTableCore.Action.ALIGN).ok);
		expectLines("next cell appends row", MarkdownTableCore.apply(table, 3, 1, MarkdownTableCore.Action.NEXT_CELL).lines, List.of(
			"| Name      | Age |",
			"| --------- | --: |",
			"| Anna      |  20 |",
			"| Alexander |   7 |",
			"|           |     |"
		));
		expectLines("previous cell realigns", MarkdownTableCore.apply(table, 2, 0, MarkdownTableCore.Action.PREVIOUS_CELL).lines, List.of(
			"| Name      | Age |",
			"| --------- | --: |",
			"| Anna      |  20 |",
			"| Alexander |   7 |"
		));
		expectLines("insert row below", MarkdownTableCore.apply(
			List.of("| Name | Age |", "| --- | --- |", "| Anna | 20 |"),
			2,
			0,
			MarkdownTableCore.Action.INSERT_ROW_BELOW
		).lines, List.of(
			"| Name | Age |",
			"| ---- | --- |",
			"| Anna | 20  |",
			"|      |     |"
		));
		expectLines("delete data row", MarkdownTableCore.apply(
			List.of("| Name | Age |", "| --- | --- |", "| Anna | 20 |", "| Bob | 7 |"),
			2,
			0,
			MarkdownTableCore.Action.DELETE_ROW
		).lines, List.of(
			"| Name | Age |",
			"| ---- | --- |",
			"| Bob  | 7   |"
		));
		expectLines("delete header is blocked", MarkdownTableCore.apply(
			List.of("| H | V |", "| --- | --- |", "| a | b |"),
			0,
			0,
			MarkdownTableCore.Action.DELETE_ROW
		).lines, List.of(
			"| H   | V   |",
			"| --- | --- |",
			"| a   | b   |"
		));
		expectLines("insert column right", MarkdownTableCore.apply(table, 2, 0, MarkdownTableCore.Action.INSERT_COLUMN_RIGHT).lines, List.of(
			"| Name      |     | Age |",
			"| --------- | --- | --: |",
			"| Anna      |     |  20 |",
			"| Alexander |     |   7 |"
		));
		expectLines("delete column", MarkdownTableCore.apply(table, 2, 0, MarkdownTableCore.Action.DELETE_COLUMN).lines, List.of(
			"| Age |",
			"| --: |",
			"|  20 |",
			"|   7 |"
		));
		expectLines("move row up", MarkdownTableCore.apply(
			List.of("| A |", "| --- |", "| 1 |", "| 2 |"),
			3,
			0,
			MarkdownTableCore.Action.MOVE_ROW_UP
		).lines, List.of("| A   |", "| --- |", "| 2   |", "| 1   |"));
		expectLines("move row down", MarkdownTableCore.apply(
			List.of("| A |", "| --- |", "| 1 |", "| 2 |"),
			2,
			0,
			MarkdownTableCore.Action.MOVE_ROW_DOWN
		).lines, List.of("| A   |", "| --- |", "| 2   |", "| 1   |"));
		expectLines("move column left", MarkdownTableCore.apply(
			List.of("| A | B | C |", "| --- | --- | --- |", "| 1 | 2 | 3 |"),
			2,
			2,
			MarkdownTableCore.Action.MOVE_COLUMN_LEFT
		).lines, List.of("| A   | C   | B   |", "| --- | --- | --- |", "| 1   | 3   | 2   |"));
		expectLines("move column right", MarkdownTableCore.apply(
			List.of("| A | B | C |", "| --- | --- | --- |", "| 1 | 2 | 3 |"),
			2,
			0,
			MarkdownTableCore.Action.MOVE_COLUMN_RIGHT
		).lines, List.of("| B   | A   | C   |", "| --- | --- | --- |", "| 2   | 1   | 3   |"));

		List<String> sortTable = List.of(
			"| Name | Score |",
			"| --- | ---: |",
			"| Anna | 42 |",
			"| Dmitry | 7 |",
			"| Chen | 100 |"
		);
		expectLines("sort rows ascending by score", MarkdownTableCore.apply(sortTable, 2, 1, MarkdownTableCore.Action.SORT_ASCENDING).lines, List.of(
			"| Name   | Score |",
			"| ------ | ----: |",
			"| Dmitry |     7 |",
			"| Anna   |    42 |",
			"| Chen   |   100 |"
		));
		expectLines("sort rows descending by name", MarkdownTableCore.apply(sortTable, 2, 0, MarkdownTableCore.Action.SORT_DESCENDING).lines, List.of(
			"| Name   | Score |",
			"| ------ | ----: |",
			"| Dmitry |     7 |",
			"| Chen   |   100 |",
			"| Anna   |    42 |"
		));
		expectLines("insert table dialog result", MarkdownTableCore.newTable(2, 1).lines, List.of(
			"| Column 1 | Column 2 |",
			"| -------- | -------- |",
			"|          |          |"
		));

		List<String> unwrapped = List.of(
			"Name | Age",
			"--- | ---:",
			"Anna | 20"
		);
		expectLines("unwrapped align preserves style", MarkdownTableCore.apply(unwrapped, 2, 1, MarkdownTableCore.Action.ALIGN).lines, List.of(
			"Name | Age",
			"---- | --:",
			"Anna |  20"
		));
	}

	private static void delimitedScenarios() {
		expectLines("convert csv selection", MarkdownTableCore.fromDelimited("Name,Role,Score\nAnna,Engineer,42\nDmitry,QA,7").lines, List.of(
			"| Name   | Role     | Score |",
			"| ------ | -------- | ----- |",
			"| Anna   | Engineer | 42    |",
			"| Dmitry | QA       | 7     |"
		));
		expectLines("convert tsv selection", MarkdownTableCore.fromDelimited("Name\tNote\nAnna\tA|B").lines, List.of(
			"| Name | Note |",
			"| ---- | ---- |",
			"| Anna | A\\|B |"
		));
	}

	private static void largeDataScenarios() {
		MarkdownTableCore.EditResult largeTable = MarkdownTableCore.apply(
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
		expectTrue("large fish table ok", largeTable.ok);
		expectInt("large fish table line count", largeTable.lines.size(), 8);
		String largeText = String.join("\n", largeTable.lines);
		expectContains("large fish table keeps cyrillic", largeText, "Съешь еще этих мягких французских булок");
		expectContains("large fish table keeps cjk", largeText, "表示確認");
		expectContains("large fish table keeps escaped pipe", largeText, "Markdown \\| escaped pipe");

		List<String> hugeLines = new ArrayList<>();
		hugeLines.add("| Id | Payload | Note |");
		hugeLines.add("| --- | --- | --- |");
		String payload = "Lorem ipsum dolor sit amet, consectetur adipiscing elit; " +
			"Съешь еще этих мягких французских булок; " +
			"表示確認 and Markdown \\| escaped pipe remain stable.";
		for (int i = 0; i < 160; i++) {
			hugeLines.add("| row-" + i + " | " + payload + " chunk-" + i + " | note-" + i + " |");
		}
		MarkdownTableCore.EditResult hugeTable = MarkdownTableCore.apply(hugeLines, 80, 1, MarkdownTableCore.Action.ALIGN);
		expectTrue("huge generated table ok", hugeTable.ok);
		expectInt("huge generated table line count", hugeTable.lines.size(), 162);
		String hugeText = String.join("\n", hugeTable.lines);
		expectContains("huge generated table keeps first row", hugeText, "row-0");
		expectContains("huge generated table keeps last row", hugeText, "row-159");
		expectContains("huge generated table keeps escaped pipe", hugeText, "Markdown \\| escaped pipe");

		MarkdownTableCore.EditResult largeCsv = MarkdownTableCore.fromDelimited(
			"Name,Story,Score\r\n" +
				"Anna,\"Lorem ipsum dolor sit amet, consectetur adipiscing elit, with comma\",42\r\n" +
				"Борис,\"Длинная рыба с переносом\r\nвнутри кавычек и pipe |\",7\r\n" +
				"Chen,\"CJK 表 with comma, quote \"\"ok\"\", and more filler text\",100\r\n" +
				"Delta,\"one two three four five six seven eight nine ten\",-12.5\r\n"
		);
		expectTrue("large fish csv ok", largeCsv.ok);
		expectInt("large fish csv line count", largeCsv.lines.size(), 6);
		String largeCsvText = String.join("\n", largeCsv.lines);
		expectContains("large fish csv flattens newline", largeCsvText, "переносом внутри кавычек");
		expectContains("large fish csv escapes pipe", largeCsvText, "pipe \\|");
		expectContains("large fish csv keeps quotes", largeCsvText, "quote \"ok\"");

		StringBuilder hugeCsv = new StringBuilder("Id,Text,Score\r\n");
		for (int i = 0; i < 220; i++) {
			hugeCsv
				.append("row-").append(i).append(",\"")
				.append("Lorem ipsum dolor sit amet, with comma, ")
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

	private static void fail(String name, String message) {
		failures++;
		System.err.println("scenario " + name + " failed: " + message);
	}
}
