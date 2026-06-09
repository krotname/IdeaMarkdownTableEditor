// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MarkdownTableCorePerformance {
	private static final int WARMUP_RUNS = 3;
	private static final int MEASURED_RUNS = 5;
	private static volatile int sink;

	@Test
	void corePerformanceBenchmarks() {
		List<String> largeTable = table(3_000, 8, false);
		List<String> unicodeTable = table(1_000, 24, true);
		String csv = delimited(3_000, 10, ',');
		String tsv = delimited(5_000, 8, '\t');
		List<String> sortableTable = sortableTable(5_000, 8);
		List<String> operationTable = table(1_500, 16, true);

		runBenchmark("align 3000x8 table", 500, () ->
			consume(MarkdownTableCore.apply(largeTable, 1_502, 3, MarkdownTableCore.Action.ALIGN)));
		runBenchmark("align 1000x24 unicode table", 700, () ->
			consume(MarkdownTableCore.apply(unicodeTable, 502, 12, MarkdownTableCore.Action.ALIGN)));
		runBenchmark("convert 3000x10 CSV", 800, () ->
			consume(MarkdownTableCore.fromDelimited(csv)));
		runBenchmark("convert 5000x8 TSV", 900, () ->
			consume(MarkdownTableCore.fromDelimited(tsv)));
		runBenchmark("sort 5000 rows", 800, () -> {
			consume(MarkdownTableCore.apply(sortableTable, 2_500, 0, MarkdownTableCore.Action.SORT_ASCENDING));
			consume(MarkdownTableCore.apply(sortableTable, 2_500, 1, MarkdownTableCore.Action.SORT_DESCENDING));
		});
		runBenchmark("row and column operations", 1_000, () -> {
			consume(MarkdownTableCore.apply(operationTable, 700, 6, MarkdownTableCore.Action.INSERT_ROW_BELOW));
			consume(MarkdownTableCore.apply(operationTable, 700, 6, MarkdownTableCore.Action.DELETE_ROW));
			consume(MarkdownTableCore.apply(operationTable, 700, 6, MarkdownTableCore.Action.INSERT_COLUMN_RIGHT));
			consume(MarkdownTableCore.apply(operationTable, 700, 6, MarkdownTableCore.Action.DELETE_COLUMN));
			consume(MarkdownTableCore.apply(operationTable, 700, 6, MarkdownTableCore.Action.MOVE_ROW_UP));
			consume(MarkdownTableCore.apply(operationTable, 700, 6, MarkdownTableCore.Action.MOVE_ROW_DOWN));
			consume(MarkdownTableCore.apply(operationTable, 700, 6, MarkdownTableCore.Action.MOVE_COLUMN_LEFT));
			consume(MarkdownTableCore.apply(operationTable, 700, 6, MarkdownTableCore.Action.MOVE_COLUMN_RIGHT));
		});
		assertTrue(sink != 0, "benchmark results must be consumed");
	}

	private static void runBenchmark(String name, long thresholdMillis, Runnable action) {
		for (int i = 0; i < WARMUP_RUNS; i++) {
			action.run();
		}

		long[] samples = new long[MEASURED_RUNS];
		for (int i = 0; i < MEASURED_RUNS; i++) {
			long started = System.nanoTime();
			action.run();
			samples[i] = System.nanoTime() - started;
		}

		Arrays.sort(samples);
		double scale = thresholdScale();
		long medianMillis = TimeUnit.NANOSECONDS.toMillis(samples[samples.length / 2]);
		long scaledThreshold = Math.max(1, Math.round(thresholdMillis * scale));
		System.out.printf(
			Locale.ROOT,
			"core performance: %-30s median=%d ms threshold=%d ms samples=%s%n",
			name,
			medianMillis,
			scaledThreshold,
			samplesMillis(samples)
		);
		assertTrue(
			medianMillis <= scaledThreshold,
			() -> name + " median " + medianMillis + " ms exceeded threshold " + scaledThreshold + " ms"
		);
	}

	private static String samplesMillis(long[] samples) {
		StringBuilder result = new StringBuilder("[");
		for (int i = 0; i < samples.length; i++) {
			if (i > 0) {
				result.append(", ");
			}
			result.append(TimeUnit.NANOSECONDS.toMillis(samples[i]));
		}
		return result.append("] ms").toString();
	}

	private static double thresholdScale() {
		String value = System.getProperty("corePerformanceThresholdScale", "1.0");
		try {
			double parsed = Double.parseDouble(value);
			return parsed > 0 ? parsed : 1.0;
		} catch (NumberFormatException ignored) {
			return 1.0;
		}
	}

	private static void consume(MarkdownTableCore.EditResult result) {
		assertTrue(result.ok, () -> "core operation failed: " + result.message);
		int value = result.lines.size() + result.targetRow + result.targetColumn + result.targetColumnOffset;
		if (!result.lines.isEmpty()) {
			value += result.lines.get(0).length();
			value += result.lines.get(result.lines.size() - 1).length();
		}
		sink += value;
	}

	private static List<String> table(int dataRows, int columns, boolean unicode) {
		List<String> lines = new ArrayList<>(dataRows + 2);
		lines.add(row(-1, columns, unicode));
		lines.add(separator(columns));
		for (int row = 0; row < dataRows; row++) {
			lines.add(row(row, columns, unicode));
		}
		return lines;
	}

	private static List<String> sortableTable(int dataRows, int columns) {
		List<String> lines = new ArrayList<>(dataRows + 2);
		lines.add(row(-1, columns, true));
		lines.add(separator(columns));
		for (int row = dataRows; row > 0; row--) {
			StringBuilder line = new StringBuilder("|");
			for (int column = 0; column < columns; column++) {
				String value = column == 0
					? unicodeToken(row, column) + "-" + row
					: Integer.toString((row * (column + 17)) % 100_000);
				line.append(' ').append(value).append(" |");
			}
			lines.add(line.toString());
		}
		return lines;
	}

	private static String row(int row, int columns, boolean unicode) {
		StringBuilder line = new StringBuilder("|");
		for (int column = 0; column < columns; column++) {
			String value = row < 0 ? "Column " + (column + 1) : cell(row, column, unicode);
			line.append(' ').append(value).append(" |");
		}
		return line.toString();
	}

	private static String separator(int columns) {
		StringBuilder line = new StringBuilder("|");
		for (int column = 0; column < columns; column++) {
			if (column % 4 == 1) {
				line.append(" ---: |");
			} else if (column % 4 == 2) {
				line.append(" :---: |");
			} else {
				line.append(" --- |");
			}
		}
		return line.toString();
	}

	private static String cell(int row, int column, boolean unicode) {
		String value = unicode
			? unicodeToken(row, column) + "-" + row + "-" + column
			: "row-" + row + "-column-" + column;
		if ((row + column) % 13 == 0) {
			value += " escaped \\| pipe";
		}
		if (column % 5 == 0) {
			value += " value-" + ((row + 1) * (column + 3) % 10_000);
		}
		return value;
	}

	private static String delimited(int rows, int columns, char delimiter) {
		StringBuilder text = new StringBuilder(rows * columns * 14);
		for (int row = 0; row < rows; row++) {
			if (row > 0) {
				text.append('\n');
			}
			for (int column = 0; column < columns; column++) {
				if (column > 0) {
					text.append(delimiter);
				}
				String value = row == 0
					? "Column " + (column + 1)
					: unicodeToken(row, column) + " value " + row + "-" + column;
				if (delimiter == ',' && (row + column) % 9 == 0) {
					text.append('"').append(value).append(", quoted \"\"cell\"\"").append('"');
				} else {
					text.append(value);
				}
			}
		}
		return text.toString();
	}

	private static String unicodeToken(int row, int column) {
		String[] values = {
			codePoints(0x8868),
			codePoints(0x30C6, 0x30B9, 0x30C8),
			codePoints(0x1F680),
			codePoints(0x0442, 0x0435, 0x0441, 0x0442),
			codePoints(0x03A3, 0x03C4, 0x03AE, 0x03BB, 0x03B7),
			codePoints(0x1F469, 0x200D, 0x1F4BB),
			codePoints(0x0645, 0x0631, 0x062D, 0x0628, 0x0627),
			codePoints(0x65E5, 0x672C, 0x8A9E)
		};
		return values[Math.floorMod(row + column, values.length)];
	}

	private static String codePoints(int... values) {
		StringBuilder text = new StringBuilder(values.length);
		for (int value : values) {
			text.appendCodePoint(value);
		}
		return text.toString();
	}
}
