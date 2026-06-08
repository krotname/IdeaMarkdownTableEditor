// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarkdownTableGoldenFixtures {
	private MarkdownTableGoldenFixtures() {
	}

	static void run() throws IOException {
		Map<String, Object> root = asObject(Json.parse(Files.readString(
			Path.of("test-fixtures", "markdown-table-core-golden.json"),
			StandardCharsets.UTF_8
		)));

		for (Map<String, Object> scenario : asObjectList(root.get("conversion"))) {
			String name = asString(scenario.get("name"));
			MarkdownTableCore.EditResult result = MarkdownTableCore.fromDelimited(asString(scenario.get("input")));
			assertTrue(result.ok, name + " should convert: " + result.message);
			assertEquals(asStringList(scenario.get("lines")), result.lines, name);
		}

		for (Map<String, Object> scenario : asObjectList(root.get("edits"))) {
			String name = asString(scenario.get("name"));
			MarkdownTableCore.EditResult result = MarkdownTableCore.apply(
				asStringList(scenario.get("input")),
				asInt(scenario.get("row")),
				asInt(scenario.get("column")),
				MarkdownTableCore.Action.valueOf(asString(scenario.get("action")))
			);
			assertTrue(result.ok, name + " should apply: " + result.message);
			assertEquals(asStringList(scenario.get("lines")), result.lines, name);
			if (scenario.containsKey("targetRow")) {
				assertEquals(asInt(scenario.get("targetRow")), result.targetRow, name + " targetRow");
			}
			if (scenario.containsKey("targetColumn")) {
				assertEquals(asInt(scenario.get("targetColumn")), result.targetColumn, name + " targetColumn");
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asObject(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> asObjectList(Object value) {
		return (List<Map<String, Object>>) value;
	}

	@SuppressWarnings("unchecked")
	private static List<String> asStringList(Object value) {
		return (List<String>) value;
	}

	private static String asString(Object value) {
		return (String) value;
	}

	private static int asInt(Object value) {
		return ((Number) value).intValue();
	}

	private static final class Json {
		private final String text;
		private int offset;

		private Json(String text) {
			this.text = text;
		}

		static Object parse(String text) {
			Json parser = new Json(text);
			Object value = parser.parseValue();
			parser.skipWhitespace();
			if (parser.offset != parser.text.length()) {
				throw parser.error("Unexpected trailing data");
			}
			return value;
		}

		private Object parseValue() {
			skipWhitespace();
			if (offset >= text.length()) {
				throw error("Unexpected end of JSON");
			}

			char ch = text.charAt(offset);
			if (ch == '{') {
				return parseObject();
			}
			if (ch == '[') {
				return parseArray();
			}
			if (ch == '"') {
				return parseString();
			}
			if (ch == '-' || Character.isDigit(ch)) {
				return parseNumber();
			}
			if (text.startsWith("true", offset)) {
				offset += 4;
				return Boolean.TRUE;
			}
			if (text.startsWith("false", offset)) {
				offset += 5;
				return Boolean.FALSE;
			}
			if (text.startsWith("null", offset)) {
				offset += 4;
				return null;
			}
			throw error("Unexpected value");
		}

		private Map<String, Object> parseObject() {
			expect('{');
			Map<String, Object> result = new LinkedHashMap<>();
			skipWhitespace();
			if (peek('}')) {
				offset++;
				return result;
			}

			while (true) {
				String key = parseString();
				skipWhitespace();
				expect(':');
				result.put(key, parseValue());
				skipWhitespace();
				if (peek('}')) {
					offset++;
					return result;
				}
				expect(',');
				skipWhitespace();
			}
		}

		private List<Object> parseArray() {
			expect('[');
			List<Object> result = new ArrayList<>();
			skipWhitespace();
			if (peek(']')) {
				offset++;
				return result;
			}

			while (true) {
				result.add(parseValue());
				skipWhitespace();
				if (peek(']')) {
					offset++;
					return result;
				}
				expect(',');
			}
		}

		private String parseString() {
			expect('"');
			StringBuilder result = new StringBuilder();
			while (offset < text.length()) {
				char ch = text.charAt(offset++);
				if (ch == '"') {
					return result.toString();
				}
				if (ch != '\\') {
					result.append(ch);
					continue;
				}
				if (offset >= text.length()) {
					throw error("Unterminated escape sequence");
				}
				char escaped = text.charAt(offset++);
				switch (escaped) {
					case '"':
					case '\\':
					case '/':
						result.append(escaped);
						break;
					case 'b':
						result.append('\b');
						break;
					case 'f':
						result.append('\f');
						break;
					case 'n':
						result.append('\n');
						break;
					case 'r':
						result.append('\r');
						break;
					case 't':
						result.append('\t');
						break;
					case 'u':
						result.append((char) Integer.parseInt(readHexDigits(), 16));
						break;
					default:
						throw error("Unsupported escape sequence");
				}
			}
			throw error("Unterminated string");
		}

		private String readHexDigits() {
			if (offset + 4 > text.length()) {
				throw error("Invalid unicode escape");
			}
			String digits = text.substring(offset, offset + 4);
			offset += 4;
			return digits;
		}

		private Number parseNumber() {
			int start = offset;
			if (peek('-')) {
				offset++;
			}
			while (offset < text.length() && Character.isDigit(text.charAt(offset))) {
				offset++;
			}
			boolean floating = false;
			if (peek('.')) {
				floating = true;
				offset++;
				while (offset < text.length() && Character.isDigit(text.charAt(offset))) {
					offset++;
				}
			}
			if (peek('e') || peek('E')) {
				floating = true;
				offset++;
				if (peek('+') || peek('-')) {
					offset++;
				}
				while (offset < text.length() && Character.isDigit(text.charAt(offset))) {
					offset++;
				}
			}
			String value = text.substring(start, offset);
			return floating ? Double.parseDouble(value) : Long.parseLong(value);
		}

		private void expect(char expected) {
			skipWhitespace();
			if (offset >= text.length() || text.charAt(offset) != expected) {
				throw error("Expected '" + expected + "'");
			}
			offset++;
		}

		private boolean peek(char expected) {
			return offset < text.length() && text.charAt(offset) == expected;
		}

		private void skipWhitespace() {
			while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) {
				offset++;
			}
		}

		private IllegalArgumentException error(String message) {
			return new IllegalArgumentException(message + " at offset " + offset);
		}
	}
}
