// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 krotname

package name.krot.markdowntable.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarkdownTableCoreTest {
	@Test
	void behavioralScenarios() {
		MarkdownTableCoreScenarios.run();
	}

	@Test
	void goldenFixtures() throws Exception {
		MarkdownTableGoldenFixtures.run();
	}

	@Test
	void publicFacadeExposesImmutableValueContracts() {
		List<String> table = List.of(
			"| A | B |",
			"| --- | --- |",
			"| 1 | 2 |"
		);

		assertTrue(MarkdownTableCore.isPotentialTableLine(table.get(0)));
		assertFalse(MarkdownTableCore.isPotentialTableLine("plain text"));
		assertTrue(MarkdownTableCore.isPotentialSeparatorLine(table.get(1)));
		assertFalse(MarkdownTableCore.isPotentialSeparatorLine(table.get(0)));
		assertEquals(0, MarkdownTableCore.columnFromCursor(table.get(0), 2));
		assertEquals(1, MarkdownTableCore.columnFromCursor(table.get(0), 6));

		MarkdownTableCore.TableRange range = MarkdownTableCore.findTableRange(table, 1);
		assertEquals(range.found, range.found());
		assertEquals(range.firstRow, range.firstRow());
		assertEquals(range.lastRow, range.lastRow());
		assertTrue(range.found());
		assertEquals(0, range.firstRow());
		assertEquals(2, range.lastRow());

		List<MarkdownTableCore.TableRange> ranges = MarkdownTableCore.findTableRanges(table);
		assertEquals(1, ranges.size());
		assertThrows(UnsupportedOperationException.class, () -> ranges.add(range));

		MarkdownTableCore.EditResult result = MarkdownTableCore.apply(
			table,
			0,
			0,
			MarkdownTableCore.Action.ALIGN
		);
		assertEquals(result.changed, result.changed());
		assertEquals(result.ok, result.ok());
		assertEquals(result.message, result.message());
		assertEquals(result.lines, result.lines());
		assertEquals(result.targetRow, result.targetRow());
		assertEquals(result.targetColumn, result.targetColumn());
		assertEquals(result.targetColumnOffset, result.targetColumnOffset());
		assertThrows(UnsupportedOperationException.class, () -> result.lines().add("unexpected"));
	}

	@Test
	void publicFacadeRejectsNullInputs() {
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.isPotentialTableLine(null));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.columnFromCursor(null, 0));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.findTableRange(null, 0));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.findTableRanges(Arrays.asList("line", null)));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.isPotentialSeparatorLine(null));
		assertThrows(NullPointerException.class,
			() -> MarkdownTableCore.apply(null, 0, 0, MarkdownTableCore.Action.ALIGN));
		assertThrows(NullPointerException.class,
			() -> MarkdownTableCore.apply(List.of(), 0, 0, null));
		assertThrows(NullPointerException.class, () -> MarkdownTableCore.fromDelimited(null));
	}
}
