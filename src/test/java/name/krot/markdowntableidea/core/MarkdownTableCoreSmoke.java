package name.krot.markdowntableidea.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MarkdownTableCoreSmoke {
	private static int failures;

	public static void main(String[] args) throws Exception {
		String pluginXml = Files.readString(Path.of("src", "main", "resources", "META-INF", "plugin.xml"));
		expectContains("dynamic plugin descriptor", pluginXml, "<idea-plugin require-restart=\"false\">");
		expectContains("dynamic tab handler descriptor", pluginXml, "<editorActionHandler action=\"EditorTab\"");
		expectContains("dynamic tab handler implementation", pluginXml, "implementationClass=\"name.krot.markdowntableidea.MarkdownTableTabHandler\"");
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

		List<String> unwrapped = List.of(
			"Name | Age",
			"--- | ---:",
			"Anna | 20"
		);
		expectInt("unwrapped cursor first cell", MarkdownTableCore.columnFromCursor(unwrapped.get(2), 1), 0);
		expectInt("unwrapped cursor second cell", MarkdownTableCore.columnFromCursor(unwrapped.get(2), 7), 1);
		expectLines("unwrapped align", MarkdownTableCore.apply(unwrapped, 2, 1, MarkdownTableCore.Action.ALIGN).lines, List.of(
			"| Name | Age |",
			"| ---- | --: |",
			"| Anna |  20 |"
		));

		String escaped = "| a \\| b | c |";
		expectTrue("escaped pipe potential", MarkdownTableCore.isPotentialTableLine(escaped));
		expectTrue("only escaped pipe is not table", !MarkdownTableCore.isPotentialTableLine("a \\| b"));
		expectInt("escaped pipe cursor", MarkdownTableCore.columnFromCursor(escaped, escaped.indexOf("c")), 1);
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

		if (failures != 0) {
			System.err.println(failures + " test(s) failed");
			System.exit(1);
		}

		System.out.println("Core smoke tests passed");
	}

	private static void expectTrue(String name, boolean value) {
		if (!value) {
			fail(name, "expected true");
		}
	}

	private static void expectInt(String name, int actual, int expected) {
		if (actual != expected) {
			fail(name, "expected " + expected + ", got " + actual);
		}
	}

	private static void expectLines(String name, List<String> actual, List<String> expected) {
		if (!actual.equals(expected)) {
			fail(name, "expected:\n" + String.join("\n", expected) + "\nactual:\n" + String.join("\n", actual));
		}
	}

	private static void expectContains(String name, String actual, String expected) {
		if (!actual.contains(expected)) {
			fail(name, "expected to contain: " + expected);
		}
	}

	private static void expectNotContains(String name, String actual, String unexpected) {
		if (actual.contains(unexpected)) {
			fail(name, "expected not to contain: " + unexpected);
		}
	}

	private static void fail(String name, String message) {
		failures++;
		System.err.println(name + " failed: " + message);
	}
}
