// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 krotname

package name.krot.markdowntable.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import name.krot.markdowntable.core.MarkdownTableCore.Action;
import name.krot.markdowntable.core.MarkdownTableCore.EditResult;
import name.krot.markdowntable.core.MarkdownTableCore.TableRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract-level coverage of the public facade: alignment, structural edits, conversion,
 * Unicode measurement, argument handling, and the invariants every result must satisfy.
 */
final class MarkdownTableCoreContractTest {
	private static final List<String> SIMPLE = List.of(
		"| Name | Age |",
		"| --- | --- |",
		"| Anna | 20 |"
	);

	// ---------------------------------------------------------------- alignment

	@ParameterizedTest
	@CsvSource({
		"'| --- |', '| ---- |', '| a    |'",
		"'| :-- |', '| :--- |', '| a    |'",
		"'| --: |', '| ---: |', '|    a |'",
		"'| :-: |', '| :--: |', '|  a   |'"
	})
	void separatorAlignmentDrivesCellPadding(String separator, String expectedSeparator, String expectedDataRow) {
		EditResult result = MarkdownTableCore.apply(
			List.of("| head |", separator, "| a |"),
			2,
			0,
			Action.ALIGN
		);

		assertTrue(result.ok, result.message);
		assertEquals(List.of("| head |", expectedSeparator, expectedDataRow), result.lines);
	}

	@Test
	void alignmentMarkersSurviveRoundTrip() {
		List<String> table = List.of(
			"| a | b | c | d |",
			"| :--- | ---: | :---: | --- |",
			"| 1 | 2 | 3 | 4 |"
		);

		EditResult result = MarkdownTableCore.apply(table, 2, 0, Action.ALIGN);
		assertEquals(List.of(
			"| a   |   b |  c  | d   |",
			"| :-- | --: | :-: | --- |",
			"| 1   |   2 |  3  | 4   |"
		), result.lines);
		assertEquals(result.lines, MarkdownTableCore.apply(result.lines, 0, 0, Action.ALIGN).lines);
	}

	@Test
	void raggedRowsArePaddedToTheWidestRow() {
		EditResult result = MarkdownTableCore.apply(
			List.of("| a | b |", "|---|---|", "|", "| x |", "| p | q | r |"),
			2,
			0,
			Action.ALIGN
		);

		assertTrue(result.ok, result.message);
		assertEquals(5, result.lines.size());
		for (String line : result.lines) {
			assertEquals(3, countUnescapedPipes(line) - 1, "every row keeps three columns: " + line);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "| a | b |\r", "| a | b |", "  | a | b |  " })
	void trailingCarriageReturnsAndOuterSpacesAreIgnored(String header) {
		EditResult result = MarkdownTableCore.apply(
			List.of(header, "| --- | --- |", "| 1 | 2 |"),
			0,
			0,
			Action.ALIGN
		);

		assertTrue(result.ok, result.message);
		assertEquals("| a   | b   |", result.lines.get(0));
	}

	// ---------------------------------------------------------------- structure

	@ParameterizedTest
	@EnumSource(Action.class)
	void everyActionProducesAReParseableTable(Action action) {
		List<String> table = List.of(
			"| Name | Age | Note |",
			"| --- | ---: | :-: |",
			"| Anna | 20 | first |",
			"| Bob | 7 | second |"
		);

		EditResult result = MarkdownTableCore.apply(table, 2, 1, action);
		assertTrue(result.ok, action + ": " + result.message);
		assertResultInvariants(result);
		assertReParseable(result, action);
	}

	@Test
	void rowsCanBeInsertedMovedAndDeleted() {
		List<String> table = List.of("| A |", "| --- |", "| 1 |", "| 2 |");

		assertEquals(List.of("| A   |", "| --- |", "| 1   |", "| 2   |", "|     |"),
			MarkdownTableCore.apply(table, 3, 0, Action.INSERT_ROW_BELOW).lines);
		assertEquals(List.of("| A   |", "| --- |", "| 2   |", "| 1   |"),
			MarkdownTableCore.apply(table, 3, 0, Action.MOVE_ROW_UP).lines);
		assertEquals(List.of("| A   |", "| --- |", "| 2   |", "| 1   |"),
			MarkdownTableCore.apply(table, 2, 0, Action.MOVE_ROW_DOWN).lines);
		assertEquals(List.of("| A   |", "| --- |", "| 2   |"),
			MarkdownTableCore.apply(table, 2, 0, Action.DELETE_ROW).lines);
	}

	@Test
	void theHeaderAndTheSeparatorAreProtectedFromDeletion() {
		List<String> single = List.of("| A   |", "| --- |", "| 1   |");

		assertEquals(single, MarkdownTableCore.apply(single, 0, 0, Action.DELETE_ROW).lines,
			"the header must survive");
		assertFalse(MarkdownTableCore.apply(single, 0, 0, Action.DELETE_ROW).changed);
		assertEquals(single, MarkdownTableCore.apply(single, 1, 0, Action.DELETE_ROW).lines,
			"the separator must survive");
		assertFalse(MarkdownTableCore.apply(single, 1, 0, Action.DELETE_ROW).changed);
		assertEquals(List.of("| A   |", "| --- |"),
			MarkdownTableCore.apply(single, 2, 0, Action.DELETE_ROW).lines,
			"an empty table keeps its header and separator");
	}

	@Test
	void columnsCanBeInsertedMovedAndDeleted() {
		List<String> table = List.of("| A | B | C |", "| --- | --- | --- |", "| 1 | 2 | 3 |");

		assertEquals(List.of("| A   |     | B   | C   |", "| --- | --- | --- | --- |", "| 1   |     | 2   | 3   |"),
			MarkdownTableCore.apply(table, 2, 0, Action.INSERT_COLUMN_RIGHT).lines);
		assertEquals(List.of("| A   | C   |", "| --- | --- |", "| 1   | 3   |"),
			MarkdownTableCore.apply(table, 2, 1, Action.DELETE_COLUMN).lines);
		assertEquals(List.of("| B   | A   | C   |", "| --- | --- | --- |", "| 2   | 1   | 3   |"),
			MarkdownTableCore.apply(table, 2, 0, Action.MOVE_COLUMN_RIGHT).lines);
		assertEquals(List.of("| A   | C   | B   |", "| --- | --- | --- |", "| 1   | 3   | 2   |"),
			MarkdownTableCore.apply(table, 2, 2, Action.MOVE_COLUMN_LEFT).lines);
	}

	@Test
	void narrowAndWidenAdjustOneDisplayColumn() {
		List<String> table = List.of(
			"| h | text                 |",
			"| --- | ------------------ |",
			"| 1 | alpha beta gamma delta |"
		);

		int aligned = MarkdownTableCore.apply(table, 2, 1, Action.ALIGN).lines.get(0).length();
		int narrowed = MarkdownTableCore.apply(table, 2, 1, Action.NARROW_COLUMN).lines.get(0).length();
		int widened = MarkdownTableCore.apply(table, 2, 1, Action.WIDEN_COLUMN).lines.get(0).length();

		assertEquals(aligned - 1, narrowed, "narrowing removes exactly one display column");
		assertEquals(aligned + 1, widened, "widening adds exactly one display column");
	}

	// ---------------------------------------------------------------- sorting

	@Test
	void sortingOrdersNumbersBeforeTextInBothDirections() {
		List<String> table = List.of(
			"| v |", "| --- |", "| 10 |", "| 9 |", "| abc |", "| -2 |", "| 1e3 |", "| 0.5 |"
		);

		assertEquals(List.of("-2", "0.5", "9", "10", "1e3", "abc"),
			dataColumn(MarkdownTableCore.apply(table, 2, 0, Action.SORT_ASCENDING)));
		assertEquals(List.of("abc", "1e3", "10", "9", "0.5", "-2"),
			dataColumn(MarkdownTableCore.apply(table, 2, 0, Action.SORT_DESCENDING)));
	}

	@Test
	void sortingIsCaseFoldedAndDiacriticInsensitive() {
		List<String> table = List.of("| Name |", "| --- |", "| Яна |", "| борис |", "| Анна |", "| Émile |");

		assertEquals(List.of("Émile", "Анна", "борис", "Яна"),
			dataColumn(MarkdownTableCore.apply(table, 2, 0, Action.SORT_ASCENDING)));
	}

	@Test
	void sortingKeepsTheCaretOnItsOriginalRow() {
		List<String> table = List.of("| k | v |", "| --- | --- |", "| b | 1 |", "| a | 2 |", "| c | 3 |");

		EditResult result = MarkdownTableCore.apply(table, 2, 0, Action.SORT_ASCENDING);
		assertEquals("| b   | 1   |", result.lines.get(result.targetRow));
	}

	// ---------------------------------------------------------------- navigation

	@Test
	void nextCellAppendsARowAtTheEndOfTheTable() {
		EditResult result = MarkdownTableCore.apply(SIMPLE, 2, 1, Action.NEXT_CELL);

		assertEquals(4, result.lines.size());
		assertEquals(3, result.targetRow);
		assertEquals(0, result.targetColumn);
	}

	@Test
	void previousCellSkipsTheSeparatorRow() {
		EditResult result = MarkdownTableCore.apply(SIMPLE, 2, 0, Action.PREVIOUS_CELL);

		assertEquals(0, result.targetRow, "the caret lands on the header, not the separator");
		assertEquals(1, result.targetColumn);
	}

	@Test
	void theCaretOffsetPointsAtTheFirstCharacterOfTheTargetCell() {
		EditResult result = MarkdownTableCore.apply(
			List.of("| aaa | b |", "| --- | --- |", "| 1 | 2 |"),
			2,
			0,
			Action.NEXT_CELL
		);

		assertEquals('2', result.lines.get(result.targetRow).charAt(result.targetColumnOffset));
	}

	@ParameterizedTest
	@CsvSource({ "0, 0", "2, 0", "4, 0", "5, 1", "6, 1", "9, 2", "12, 2", "13, 3" })
	void columnFromCursorCountsUnescapedPipes(int charColumn, int expectedColumn) {
		assertEquals(expectedColumn, MarkdownTableCore.columnFromCursor("| a | b | c |", charColumn));
	}

	@Test
	void columnFromCursorIgnoresEscapedPipesAndClampsTheOffset() {
		assertEquals(0, MarkdownTableCore.columnFromCursor("| a \\| b | c |", 7));
		assertEquals(0, MarkdownTableCore.columnFromCursor("| a | b |", -5));
		assertEquals(2, MarkdownTableCore.columnFromCursor("| a | b |", 4_000));
	}

	// ---------------------------------------------------------------- wrapping

	@Test
	void wrapLongCellsSplitsOnWordBoundaries() {
		EditResult result = MarkdownTableCore.apply(
			List.of("| h | text |", "| --- | --- |",
				"| 1 | the quick brown fox jumps over the lazy dog again and again |"),
			2,
			1,
			Action.WRAP_LONG_CELLS
		);

		assertTrue(result.lines.size() > 3, "a long cell is spread over continuation rows");
		assertTrue(String.join(" ", result.lines).contains("the quick brown fox jumps"));
	}

	@Test
	void fittingKeepsSparseRowsThatWrappingCouldNotHaveProduced() {
		// The third row sets the column width, so "second" would still have fitted after "short".
		// Wrapping is greedy and never leaves that room, so these are two records, not one wrapped row.
		List<String> table = List.of(
			"| Name  | Note                     |",
			"| ----- | ------------------------ |",
			"| Alice | short                    |",
			"|       | second                   |",
			"| Bob   | a much longer value here |"
		);

		EditResult result = MarkdownTableCore.applyWrappedToWidth(table, 0, 0, 200);

		assertEquals(table, result.lines);
		assertFalse(result.changed, "a table that already fits is left alone");
	}

	@Test
	void fittingRejoinsRowsThatWrappingProduced() {
		List<String> wrapped = List.of(
			"| Key | Description        |",
			"| --- | ------------------ |",
			"| x   | alpha beta gamma   |",
			"|     | delta epsilon zeta |"
		);

		EditResult result = MarkdownTableCore.applyWrappedToWidth(wrapped, 0, 0, 120);

		assertEquals(3, result.lines.size(), "a widened table pulls its continuation rows back in");
		assertTrue(result.lines.get(2).contains("alpha beta gamma delta epsilon zeta"), result.lines.toString());
	}

	@Test
	void aSegmentUnderAnEmptyCellIsNeverWrappingOutput() {
		// Wrapping fills a cell's segments from the top, so "b" cannot be the second segment of an
		// empty cell.
		List<String> table = List.of("| A | B |", "| --- | --- |", "| a |  |", "|  | b |");

		EditResult result = MarkdownTableCore.applyWrappedToWidth(table, 0, 0, 200);

		assertEquals(4, result.lines.size(), result.lines.toString());
		assertTrue(result.lines.get(3).contains("b"), result.lines.toString());
	}

	@Test
	void fittingRejoinsBodyThatWasWrappedBelowTheHeaderWidth() {
		// The header is wider than the wrap target, so the rendered column is wider than the width
		// the body segments were actually split at.
		List<String> table = List.of(
			"| Identifier | Description |", "| --- | --- |", "| x | alpha beta gamma delta epsilon |");

		EditResult narrow = MarkdownTableCore.applyWrappedToWidth(table, 2, 0, 15);
		assertTrue(narrow.lines.size() > 3, narrow.lines.toString());

		EditResult wide = MarkdownTableCore.applyWrappedToWidth(narrow.lines, 0, 0, 200);
		assertEquals(3, wide.lines.size(), wide.lines.toString());
	}

	@Test
	void fittingRejoinsConstructsThatWereHardSplitMidToken() {
		// Wrapping cuts an over-wide link mid-token, so a fragment no longer parses as a link.
		List<String> table = List.of("| A | B |", "| --- | --- |", "| [x y](url) [x y](url) | a |");

		EditResult narrow = MarkdownTableCore.applyWrappedToWidth(table, 2, 0, 18);
		assertTrue(narrow.lines.size() > 3, narrow.lines.toString());

		EditResult wide = MarkdownTableCore.applyWrappedToWidth(narrow.lines, 0, 0, 200);
		assertEquals(3, wide.lines.size(), wide.lines.toString());
		assertTrue(wide.lines.get(2).contains("[x y](url) [x y](url)"), wide.lines.toString());
	}

	@Test
	void wrappingToAWidthIsStableWhenRepeated() {
		List<String> table = List.of("| h | text |", "| --- | --- |",
			"| 1 | the quick brown fox jumps over the lazy dog again and again |");

		EditResult first = MarkdownTableCore.applyWrappedToWidth(table, 2, 1, 40);
		EditResult second = MarkdownTableCore.applyWrappedToWidth(first.lines, 2, 1, 40);

		assertTrue(first.lines.stream().allMatch(line -> line.length() <= 40), first.lines.toString());
		assertEquals(first.lines, second.lines);
		assertFalse(second.changed, "re-wrapping an already wrapped table changes nothing");
	}

	@ParameterizedTest
	@ValueSource(ints = { -10, 0, 1, 5, 200, Integer.MAX_VALUE })
	void wrappingAcceptsAnyRequestedWidth(int width) {
		EditResult result = MarkdownTableCore.applyWrappedToWidth(SIMPLE, 2, 0, width);

		assertTrue(result.ok, result.message);
		assertResultInvariants(result);
	}

	@Test
	void wrappingKeepsCodeSpansAndLinksThatFitIntact() {
		EditResult result = MarkdownTableCore.applyWrappedToWidth(
			List.of("| h | v |", "| --- | --- |",
				"| 1 | alpha `a b c` beta [x y](u) gamma delta epsilon zeta eta theta |"),
			2,
			1,
			34
		);

		String joined = String.join("\n", result.lines);
		assertTrue(result.lines.size() > 3, joined);
		assertTrue(joined.contains("`a b c`"), "code span was split: " + joined);
		assertTrue(joined.contains("[x y](u)"), "link was split: " + joined);
	}

	// ---------------------------------------------------------------- conversion

	@Test
	void csvAndTsvBecomeAlignedMarkdown() {
		assertEquals(List.of(
			"| Name   | Role     | Score |",
			"| ------ | -------- | ----- |",
			"| Anna   | Engineer | 42    |",
			"| Dmitry | QA       | 7     |"
		), MarkdownTableCore.fromDelimited("Name,Role,Score\nAnna,Engineer,42\nDmitry,QA,7").lines);

		assertEquals(List.of(
			"| Name | Note |",
			"| ---- | ---- |",
			"| Anna | A\\|B |"
		), MarkdownTableCore.fromDelimited("Name\tNote\nAnna\tA|B").lines);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "no delimiters at all", "\"unterminated,cell", "line one\nline two" })
	void nonDelimitedTextIsRejected(String text) {
		EditResult result = MarkdownTableCore.fromDelimited(text);

		assertFalse(result.ok);
		assertEquals("No CSV or TSV data found", result.message);
		assertTrue(result.lines.isEmpty());
	}

	@Test
	void crlfAndQuotedNewlinesAreNormalisedInsideCells() {
		EditResult result = MarkdownTableCore.fromDelimited("a,b\r\n\"one\r\ntwo\",three\r\n");

		assertTrue(result.ok, result.message);
		assertTrue(String.join("\n", result.lines).contains("one two"));
		for (String line : result.lines) {
			assertFalse(line.contains("\r") || line.contains("\n"), line);
		}
	}

	@ParameterizedTest
	@CsvSource({ "0, 0", "-1, 2", "3, -1" })
	void newTableRejectsImpossibleSizes(int columns, int dataRows) {
		EditResult result = MarkdownTableCore.newTable(columns, dataRows);

		assertFalse(result.ok);
		assertEquals("Invalid table size", result.message);
	}

	@Test
	void newTableProducesAlignedPlaceholders() {
		assertEquals(List.of(
			"| Column 1 | Column 2 |",
			"| -------- | -------- |",
			"|          |          |"
		), MarkdownTableCore.newTable(2, 1).lines);
	}

	// ---------------------------------------------------------------- text handling

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = {
		"abcd; 4",
		"тест; 4",
		"表示; 4",
		"🚀🚀; 4",
		"👩‍💻; 2",
		"éééé; 4",
		"🇷🇺; 2",
		"1️⃣; 2"
	})
	void cellsArePaddedByDisplayWidthNotCodeUnits(String cell, int expectedWidth) {
		EditResult result = MarkdownTableCore.apply(
			List.of("| head |", "| --- |", "| " + cell + " |"),
			2,
			0,
			Action.ALIGN
		);

		String row = result.lines.get(2);
		int padding = row.length() - "| ".length() - cell.length() - " |".length();
		assertEquals(Math.max(0, 4 - expectedWidth), padding, "row was: " + row);
	}

	@Test
	void escapedPipesStayInsideTheirCell() {
		EditResult result = MarkdownTableCore.apply(
			List.of("| a | b |", "| --- | --- |", "| x \\| y | z |"),
			2,
			0,
			Action.ALIGN
		);

		assertEquals(List.of("| a      | b   |", "| ------ | --- |", "| x \\| y | z   |"), result.lines);
	}

	@Test
	void unescapedPipesInCodeSpansStillSplitCells() {
		EditResult result = MarkdownTableCore.apply(
			List.of("| a | b | c |", "| --- | --- | --- |", "| `x | y` | z |"),
			2,
			0,
			Action.ALIGN
		);

		assertEquals(List.of("| a   | b   | c   |", "| --- | --- | --- |", "| `x  | y`  | z   |"), result.lines);
	}

	@Test
	void linksAndImagesAreNeverReflowedByAlignment() {
		EditResult result = MarkdownTableCore.apply(
			List.of("| a | b |", "| --- | --- |", "| [t](http://e.com/a_b) | ![i](x.png) |"),
			2,
			0,
			Action.ALIGN
		);

		assertEquals("| [t](http://e.com/a_b) | ![i](x.png) |", result.lines.get(2));
	}

	@Test
	void tablesWithoutOuterPipesKeepTheirStyle() {
		EditResult result = MarkdownTableCore.apply(
			List.of("Name | Age", "--- | ---:", "Anna | 20"),
			2,
			1,
			Action.ALIGN
		);

		assertEquals(List.of("Name | Age", "---- | --:", "Anna |  20"), result.lines);
	}

	// ---------------------------------------------------------------- detection

	@ParameterizedTest
	@CsvSource({
		"'| --- |', true",
		"'|---|', true",
		"'| :-: |', true",
		"'| --- | --- |', true",
		"'---', false",
		"'|  |', false",
		"'| a |', false"
	})
	void separatorLinesAreRecognised(String line, boolean expected) {
		assertEquals(expected, MarkdownTableCore.isPotentialSeparatorLine(line));
	}

	@Test
	void potentialTableLinesRequireAnUnescapedPipe() {
		assertTrue(MarkdownTableCore.isPotentialTableLine("a | b"));
		assertFalse(MarkdownTableCore.isPotentialTableLine("plain text"));
		assertFalse(MarkdownTableCore.isPotentialTableLine("a \\| b"));
		assertTrue(MarkdownTableCore.isPotentialTableLine("a \\\\| b"));
	}

	@Test
	void tablesAreFoundInDocumentOrderWithoutOverlapping() {
		List<String> document = List.of(
			"intro",
			"| a | b |",
			"| --- | --- |",
			"| 1 | 2 |",
			"",
			"| c | d |",
			"| --- | --- |",
			"| 3 | 4 |"
		);

		List<TableRange> ranges = MarkdownTableCore.findTableRanges(document);
		assertEquals(2, ranges.size());
		assertEquals(1, ranges.get(0).firstRow);
		assertEquals(3, ranges.get(0).lastRow);
		assertEquals(5, ranges.get(1).firstRow);
		assertEquals(7, ranges.get(1).lastRow);
		assertNoOverlap(ranges);
	}

	@Test
	void aHeaderMismatchedWithItsSeparatorIsNotATable() {
		assertFalse(MarkdownTableCore.findTableRange(
			List.of("| a | b |", "| --- | --- | --- |", "| 1 | 2 |"), 0).found);
		assertFalse(MarkdownTableCore.findTableRange(
			List.of("| H | V |", "| :---x | --- |", "| a | b |"), 2).found);
	}

	@Test
	void findTableRangeAgreesWithFindTableRanges() {
		List<String> document = List.of(
			"| a | b |", "| --- | --- |", "| 1 | 2 |", "--- | ---", "x | y", "", "text"
		);

		for (TableRange range : MarkdownTableCore.findTableRanges(document)) {
			for (int row = range.firstRow; row <= range.lastRow; row++) {
				TableRange single = MarkdownTableCore.findTableRange(document, row);
				assertTrue(single.found, "row " + row);
				assertEquals(range.firstRow, single.firstRow, "row " + row);
				assertEquals(range.lastRow, single.lastRow, "row " + row);
			}
		}
	}

	// ---------------------------------------------------------------- arguments

	@Test
	void nullArgumentsAreRejected() {
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.isPotentialTableLine(null));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.isPotentialSeparatorLine(null));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.columnFromCursor(null, 0));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.findTableRange(null, 0));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.findTableRanges(null));
		assertThrows(NullPointerException.class,
			() -> MarkdownTableCore.findTableRanges(Arrays.asList("line", null)));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.apply(null, 0, 0, Action.ALIGN));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.apply(SIMPLE, 0, 0, null));
		assertThrows(NullPointerException.class,
			() -> MarkdownTableCore.applyWrappedToWidth(null, 0, 0, 80));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.fromDelimited(null));
	}

	@ParameterizedTest
	@CsvSource({ "-100, -100", "0, 0", "99, 99", "2147483647, 2147483647" })
	void outOfRangeCoordinatesAreClampedIntoTheTable(int row, int column) {
		EditResult result = MarkdownTableCore.apply(SIMPLE, row, column, Action.ALIGN);

		assertTrue(result.ok, result.message);
		assertResultInvariants(result);
	}

	@Test
	void documentsWithoutATableAreReportedNotThrown() {
		assertEquals("No table found", MarkdownTableCore.apply(List.of(), 0, 0, Action.ALIGN).message);
		assertEquals("No Markdown table found",
			MarkdownTableCore.apply(List.of("Use A | B in text"), 0, 0, Action.ALIGN).message);
		assertEquals("No table found", MarkdownTableCore.applyWrappedToWidth(List.of(), 0, 0, 80).message);
		assertFalse(MarkdownTableCore.findTableRange(List.of("nothing here"), 0).found);
		assertTrue(MarkdownTableCore.findTableRanges(List.of("nothing here")).isEmpty());
	}

	// ---------------------------------------------------------------- immutability

	@Test
	void resultsAreImmutableAndDoNotAliasTheInput() {
		List<String> input = new ArrayList<>(SIMPLE);
		List<String> snapshot = List.copyOf(input);

		EditResult result = MarkdownTableCore.apply(input, 2, 0, Action.INSERT_ROW_BELOW);

		assertEquals(snapshot, input, "the caller's list must not be touched");
		assertThrows(UnsupportedOperationException.class, () -> result.lines().add("x"));
		assertThrows(UnsupportedOperationException.class,
			() -> MarkdownTableCore.findTableRanges(input).add(null));
	}

	@Test
	void accessorsAgreeWithTheirFields() {
		EditResult result = MarkdownTableCore.apply(SIMPLE, 0, 0, Action.ALIGN);
		assertEquals(result.changed, result.changed());
		assertEquals(result.ok, result.ok());
		assertEquals(result.message, result.message());
		assertEquals(result.lines, result.lines());
		assertEquals(result.targetRow, result.targetRow());
		assertEquals(result.targetColumn, result.targetColumn());
		assertEquals(result.targetColumnOffset, result.targetColumnOffset());

		TableRange range = MarkdownTableCore.findTableRange(SIMPLE, 0);
		assertEquals(range.found, range.found());
		assertEquals(range.firstRow, range.firstRow());
		assertEquals(range.lastRow, range.lastRow());
	}

	@Test
	void changedDistinguishesRealEditsFromNoOps() {
		List<String> aligned = List.of("| a   | b   |", "| --- | --- |", "| 1   | 2   |");

		assertFalse(MarkdownTableCore.apply(aligned, 0, 0, Action.ALIGN).changed);
		assertFalse(MarkdownTableCore.apply(aligned, 2, 0, Action.MOVE_ROW_UP).changed,
			"the first data row cannot move above the separator");
		assertTrue(MarkdownTableCore.apply(aligned, 2, 0, Action.INSERT_ROW_BELOW).changed);
		assertTrue(MarkdownTableCore.fromDelimited("a,b\n1,2").changed);
		assertTrue(MarkdownTableCore.newTable(2, 1).changed);
	}

	// ---------------------------------------------------------------- regressions

	@Test
	void aTableIsNeverReusedAsTheHeaderOfTheNextTable() {
		// A pipe-bearing separator right after a table used to start a second, overlapping range
		// that shared the previous table's last row, corrupting whole-document reformatting.
		List<String> document = List.of("| a | b |", "| --- | --- |", "| 1 | 2 |", "--- | ---", "x | y");

		List<TableRange> ranges = MarkdownTableCore.findTableRanges(document);
		assertNoOverlap(ranges);
		assertEquals(1, ranges.size());
		assertEquals(0, ranges.get(0).firstRow);
		assertEquals(2, ranges.get(0).lastRow);
	}

	@Test
	void anEmptyEdgeColumnForcesTheOuterPipeBackOn() {
		// Without the outer pipe the trailing empty cell is re-parsed as a trailing pipe, so the
		// column count of header and separator diverge and the table stops being recognised.
		EditResult result = MarkdownTableCore.apply(
			List.of("a | b", "--- | ---", "1 | 2"),
			2,
			1,
			Action.INSERT_COLUMN_RIGHT
		);

		assertTrue(result.ok, result.message);
		for (String line : result.lines) {
			assertTrue(line.endsWith("|"), "row must close with a pipe: " + line);
		}
		assertTrue(MarkdownTableCore.findTableRange(result.lines, 0).found,
			"the produced table must still be a table: " + result.lines);
	}

	@Test
	void deletingDownToOneColumnKeepsAtLeastOnePipe() {
		// A pipe-less two column table degenerates into plain text once the separating pipe is the
		// only one left, so the formatter has to add an outer pipe.
		EditResult result = MarkdownTableCore.apply(
			List.of("a | b", "--- | ---", "1 | 2"),
			2,
			1,
			Action.DELETE_COLUMN
		);

		assertTrue(result.ok, result.message);
		for (String line : result.lines) {
			assertTrue(line.indexOf('|') >= 0, "row must keep a pipe: " + line);
		}
		assertTrue(MarkdownTableCore.findTableRange(result.lines, 0).found, result.lines.toString());
	}

	@Test
	void aHeaderMadeOfDashesIsStillAHeader() {
		// The separator search used to start at the header, so a dashes-only header was mistaken
		// for the separator and the table was rejected outright.
		List<String> table = List.of("| --- | --- |", "| --- | --- |", "| a | b |");

		assertTrue(MarkdownTableCore.findTableRange(table, 0).found);
		EditResult result = MarkdownTableCore.apply(table, 2, 0, Action.ALIGN);
		assertTrue(result.ok, result.message);
		assertEquals(List.of("| --- | --- |", "| --- | --- |", "| a   | b   |"), result.lines);
	}

	// ---------------------------------------------------------------- invariants

	@Test
	void randomisedDocumentsNeverBreakTheResultContract() {
		Random random = new Random(20260727L);
		String[] cells = { "a", "", " ", "1", "-1", "3.14", "x \\| y", "`co|de`", "[l](u)", "тест", "表示", "🚀", "--", ":" };
		String[] separators = { "---", ":---", "---:", ":---:", "-", ":-:" };

		for (int iteration = 0; iteration < 2_000; iteration++) {
			List<String> document = new ArrayList<>();
			int columns = random.nextInt(1, 5);
			document.add(randomRow(random, columns, cells));
			document.add(randomRow(random, columns, separators));
			for (int dataRow = random.nextInt(0, 4); dataRow > 0; dataRow--) {
				document.add(randomRow(random, random.nextInt(1, 6), cells));
			}

			assertNoOverlap(MarkdownTableCore.findTableRanges(document));

			int row = random.nextInt(-1, document.size() + 1);
			int column = random.nextInt(-1, 6);
			for (Action action : Action.values()) {
				EditResult result = MarkdownTableCore.apply(document, row, column, action);
				assertResultInvariants(result);
				if (result.ok) {
					assertReParseable(result, action);
				}
			}
			assertResultInvariants(
				MarkdownTableCore.applyWrappedToWidth(document, row, column, random.nextInt(-2, 100)));
		}
	}

	// ---------------------------------------------------------------- helpers

	private static String randomRow(Random random, int columns, String[] values) {
		StringBuilder line = new StringBuilder(random.nextBoolean() ? "|" : "");
		for (int column = 0; column < columns; column++) {
			if (column > 0) {
				line.append('|');
			}
			line.append(' ').append(values[random.nextInt(values.length)]).append(' ');
		}
		return random.nextBoolean() ? line.append('|').toString() : line.toString();
	}

	/** Every produced table must still be a table, and aligning it must reach a fixed point. */
	private static void assertReParseable(EditResult result, Action action) {
		EditResult aligned = MarkdownTableCore.apply(result.lines, 0, 0, Action.ALIGN);
		assertTrue(aligned.ok, action + " produced a table that no longer parses: " + result.lines);
		assertEquals(aligned.lines, MarkdownTableCore.apply(aligned.lines, 0, 0, Action.ALIGN).lines,
			action + ": alignment is not idempotent for " + aligned.lines);
	}

	private static void assertResultInvariants(EditResult result) {
		if (!result.ok) {
			assertTrue(result.lines.isEmpty(), "a failed result must not carry lines");
			assertNotEquals("", result.message, "a failed result must explain itself");
			return;
		}

		assertFalse(result.lines.isEmpty(), "a successful result must carry lines");
		assertTrue(result.targetRow >= 0 && result.targetRow < result.lines.size(),
			"targetRow " + result.targetRow + " outside 0.." + (result.lines.size() - 1));
		assertTrue(result.targetColumn >= 0, "targetColumn " + result.targetColumn);
		String targetLine = result.lines.get(result.targetRow);
		assertTrue(result.targetColumnOffset >= 0 && result.targetColumnOffset <= targetLine.length(),
			"targetColumnOffset " + result.targetColumnOffset + " outside '" + targetLine + "'");
		for (String line : result.lines) {
			assertFalse(line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0,
				"produced line contains a line break: '" + line + "'");
		}
	}

	private static void assertNoOverlap(List<TableRange> ranges) {
		for (int i = 0; i < ranges.size(); i++) {
			TableRange range = ranges.get(i);
			assertTrue(range.found && range.firstRow <= range.lastRow,
				"malformed range " + range.firstRow + ".." + range.lastRow);
			if (i > 0) {
				assertTrue(ranges.get(i - 1).lastRow < range.firstRow,
					"ranges overlap: " + ranges.get(i - 1).lastRow + " >= " + range.firstRow);
			}
		}
	}

	private static List<String> dataColumn(EditResult result) {
		List<String> values = new ArrayList<>();
		for (int row = 2; row < result.lines.size(); row++) {
			values.add(result.lines.get(row).replace("|", "").trim());
		}
		return values;
	}

	private static int countUnescapedPipes(String line) {
		int count = 0;
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == '|' && (i == 0 || line.charAt(i - 1) != '\\')) {
				count++;
			}
		}
		return count;
	}
}
