package name.krot.markdowntableidea;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.TimerListener;
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
import name.krot.markdowntableidea.core.MarkdownTableCore;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MarkdownTableEditorScenarios {
	private static final ActionManager TEST_ACTION_MANAGER = new TestActionManager();
	private static int checks;
	private static int failures;

	private MarkdownTableEditorScenarios() {
	}

	public static int run() {
		checks = 0;
		failures = 0;
		MarkdownTableEditor.setCommandRunnerForTests((project, name, change) -> change.run());
		MarkdownTableActions.setDialogServiceForTests(new TestDialogService());
		try {
			editorRunScenarios();
			delimitedAndInsertScenarios();
			actionScenarios();
			tabHandlerScenarios();
		} finally {
			MarkdownTableEditor.setCommandRunnerForTests(null);
			MarkdownTableActions.setDialogServiceForTests(null);
		}

		if (failures == 0) {
			System.out.println("Editor integration scenarios passed (" + checks + " checks)");
		} else {
			System.err.println(failures + " editor integration scenario(s) failed");
		}
		return failures;
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
	}

	private static void delimitedAndInsertScenarios() {
		TestEditor selectedCsv = new TestEditor("Name,Score\r\nAnna,10", false, true);
		selectedCsv.select(0, selectedCsv.text().length());
		expectTrue("convert selected csv", MarkdownTableEditor.convertDelimited(selectedCsv.editor, null, true));
		expectString("selected csv table", selectedCsv.text(),
			"| Name | Score |\r\n| ---- | ----- |\r\n| Anna | 10    |");
		expectTrue("selection removed after convert", !selectedCsv.hasSelection());

		TestEditor blockCsv = new TestEditor("top\n\nName\tNote\nAnna\tA|B\n\nbottom", false, true);
		blockCsv.setCaretAt("Anna");
		expectTrue("convert current tsv block", MarkdownTableEditor.convertDelimited(blockCsv.editor, null, true));
		expectString("current block table", blockCsv.text(),
			"top\n\n| Name | Note |\n| ---- | ---- |\n| Anna | A\\|B |\n\nbottom");

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

		TestEditor replaceSelection = new TestEditor("replace", false, true);
		replaceSelection.select(0, replaceSelection.text().length());
		expectTrue("insert table replaces selection", MarkdownTableEditor.insertTable(replaceSelection.editor, null, 1, 0));
		expectString("selection replaced by table", replaceSelection.text(), "| Column 1 |\n| -------- |");

		TestEditor readOnlyInsert = new TestEditor("x", false, false);
		expectTrue("insert rejects read-only", !MarkdownTableEditor.insertTable(readOnlyInsert.editor, null, 1, 1));
		expectInt("insert read-only attempt fired", readOnlyInsert.document.readOnlyAttempts, 1);
	}

	private static void actionScenarios() {
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
			new MarkdownTableActions.SortDescending()
		};
		expectInt("base action wrappers constructed", baseActions.length, 13);

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
		expectString("bad size error", dialog.lastError, "Введите размер в формате 3x4.");
		dialog.nextValue = "51x1";
		insert.actionPerformed(event(target, new Presentation()));
		expectString("range size error", dialog.lastError, "Допустимо: 1-50 столбцов и 0-200 строк данных.");
		dialog.nextValue = "2x1";
		insert.actionPerformed(event(target, new Presentation()));
		expectContains("insert action created table", target.text(), "| Column 1 | Column 2 |");
		Presentation insertPresentation = new Presentation();
		insert.update(event(target, insertPresentation));
		expectTrue("insert action update enabled", insertPresentation.isEnabled() && insertPresentation.isVisible());
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
		return new AnActionEvent(context(editor), presentation, "test", ActionUiKind.NONE, null, 0, TEST_ACTION_MANAGER);
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

	private static void expectString(String name, String actual, String expected) {
		checks++;
		if (!actual.equals(expected)) {
			fail(name, "expected:\n" + expected + "\nactual:\n" + actual);
		}
	}

	private static void expectContains(String name, String actual, String expected) {
		checks++;
		if (!actual.contains(expected)) {
			fail(name, "expected to contain: " + expected + "\nactual:\n" + actual);
		}
	}

	private static void expectSame(String name, Object actual, Object expected) {
		checks++;
		if (actual != expected) {
			fail(name, "expected same value");
		}
	}

	private static void fail(String name, String message) {
		failures++;
		System.err.println("editor scenario " + name + " failed: " + message);
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
		private final Editor editor;
		private final boolean viewer;

		private TestEditor(String text, boolean viewer, boolean writable) {
			this.document = new TestDocument(text, writable);
			this.viewer = viewer;
			this.editor = proxy(Editor.class, this::invokeEditor);
			this.caretModel.editor = editor;
			this.selectionModel.editor = editor;
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

		private Object invokeEditor(Object proxy, Method method, Object[] args) {
			return switch (method.getName()) {
				case "getDocument" -> document.document;
				case "isViewer" -> viewer;
				case "getCaretModel" -> caretModel.caretModel;
				case "getSelectionModel" -> selectionModel.selectionModel;
				case "getScrollingModel" -> scrollingModel.scrollingModel;
				case "getProject" -> null;
				default -> defaultValue(method.getReturnType());
			};
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
			int safeOffset = Math.max(0, Math.min(offset, Math.max(text.length() - 1, 0)));
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
				case "getCaretCount", "getMaxCaretCount" -> 1;
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
		private ScrollType lastScrollType;

		private Object invoke(Object proxy, Method method, Object[] args) {
			if ("scrollToCaret".equals(method.getName())) {
				lastScrollType = (ScrollType) args[0];
				return null;
			}
			if ("getVisibleArea".equals(method.getName()) || "getVisibleAreaOnScrollingFinished".equals(method.getName())) {
				return new java.awt.Rectangle();
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
