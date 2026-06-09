// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import name.krot.markdowntableidea.core.MarkdownTableCore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownTableActions {
	private static final Pattern TABLE_SIZE = Pattern.compile("\\s*(\\d+)\\s*[xXхХ*]\\s*(\\d+)\\s*");
	private static DialogService dialogService = new IdeDialogService();

	private MarkdownTableActions() {
	}

	interface DialogService {
		String askTableSize(com.intellij.openapi.project.Project project);
		void showError(com.intellij.openapi.project.Project project, String message);
	}

	static void setDialogServiceForTests(DialogService service) {
		dialogService = service == null ? new IdeDialogService() : service;
	}

	private static final class IdeDialogService implements DialogService {
		@Override
		public String askTableSize(com.intellij.openapi.project.Project project) {
			return Messages.showInputDialog(
				project,
				MarkdownTableEditorBundle.message("dialog.insertTable.prompt"),
				MarkdownTableEditorBundle.message("dialog.insertTable.title"),
				Messages.getQuestionIcon(),
				"3x3",
				null
			);
		}

		@Override
		public void showError(com.intellij.openapi.project.Project project, String message) {
			Messages.showErrorDialog(project, message, MarkdownTableEditorBundle.message("plugin.name"));
		}
	}

	public abstract static class Base extends AnAction implements DumbAware {
		private final MarkdownTableCore.Action action;

		protected Base(MarkdownTableCore.Action action) {
			this.action = action;
		}

		@Override
		public void actionPerformed(AnActionEvent event) {
			MarkdownTableEditor.run(event.getData(CommonDataKeys.EDITOR), event.getProject(), action, false);
		}

		@Override
		public void update(AnActionEvent event) {
			Editor editor = event.getData(CommonDataKeys.EDITOR);
			event.getPresentation().setEnabledAndVisible(editor != null && !editor.isViewer());
		}

		@Override
		public ActionUpdateThread getActionUpdateThread() {
			return ActionUpdateThread.EDT;
		}
	}

	public static final class Align extends Base {
		public Align() {
			super(MarkdownTableCore.Action.ALIGN);
		}
	}

	public static final class TabAlign extends AnAction implements DumbAware {
		@Override
		public void actionPerformed(AnActionEvent event) {
			MarkdownTableEditor.run(event.getData(CommonDataKeys.EDITOR), event.getProject(), MarkdownTableCore.Action.ALIGN, true);
		}

		@Override
		public void update(AnActionEvent event) {
			event.getPresentation().setEnabled(MarkdownTableEditor.isInsidePotentialTable(event.getData(CommonDataKeys.EDITOR)));
		}

		@Override
		public ActionUpdateThread getActionUpdateThread() {
			return ActionUpdateThread.EDT;
		}
	}

	public static final class NextCell extends Base {
		public NextCell() {
			super(MarkdownTableCore.Action.NEXT_CELL);
		}
	}

	public static final class PreviousCell extends Base {
		public PreviousCell() {
			super(MarkdownTableCore.Action.PREVIOUS_CELL);
		}
	}

	public static final class InsertRowBelow extends Base {
		public InsertRowBelow() {
			super(MarkdownTableCore.Action.INSERT_ROW_BELOW);
		}
	}

	public static final class DeleteRow extends Base {
		public DeleteRow() {
			super(MarkdownTableCore.Action.DELETE_ROW);
		}
	}

	public static final class InsertColumnRight extends Base {
		public InsertColumnRight() {
			super(MarkdownTableCore.Action.INSERT_COLUMN_RIGHT);
		}
	}

	public static final class DeleteColumn extends Base {
		public DeleteColumn() {
			super(MarkdownTableCore.Action.DELETE_COLUMN);
		}
	}

	public static final class MoveRowUp extends Base {
		public MoveRowUp() {
			super(MarkdownTableCore.Action.MOVE_ROW_UP);
		}
	}

	public static final class MoveRowDown extends Base {
		public MoveRowDown() {
			super(MarkdownTableCore.Action.MOVE_ROW_DOWN);
		}
	}

	public static final class MoveColumnLeft extends Base {
		public MoveColumnLeft() {
			super(MarkdownTableCore.Action.MOVE_COLUMN_LEFT);
		}
	}

	public static final class MoveColumnRight extends Base {
		public MoveColumnRight() {
			super(MarkdownTableCore.Action.MOVE_COLUMN_RIGHT);
		}
	}

	public static final class SortAscending extends Base {
		public SortAscending() {
			super(MarkdownTableCore.Action.SORT_ASCENDING);
		}
	}

	public static final class SortDescending extends Base {
		public SortDescending() {
			super(MarkdownTableCore.Action.SORT_DESCENDING);
		}
	}

	public static final class WrapLongCells extends Base {
		public WrapLongCells() {
			super(MarkdownTableCore.Action.WRAP_LONG_CELLS);
		}
	}

	public static final class ConvertCsvTsv extends AnAction implements DumbAware {
		@Override
		public void actionPerformed(AnActionEvent event) {
			MarkdownTableEditor.convertDelimited(event.getData(CommonDataKeys.EDITOR), event.getProject(), false);
		}

		@Override
		public void update(AnActionEvent event) {
			Editor editor = event.getData(CommonDataKeys.EDITOR);
			event.getPresentation().setEnabledAndVisible(editor != null && !editor.isViewer());
		}

		@Override
		public ActionUpdateThread getActionUpdateThread() {
			return ActionUpdateThread.EDT;
		}
	}

	public static final class InsertTable extends AnAction implements DumbAware {
		@Override
		public void actionPerformed(AnActionEvent event) {
			String value = dialogService.askTableSize(event.getProject());
			if (value == null) {
				return;
			}

			Matcher matcher = TABLE_SIZE.matcher(value);
			if (!matcher.matches()) {
				dialogService.showError(event.getProject(), MarkdownTableEditorBundle.message("dialog.insertTable.error.format"));
				return;
			}

			int columns = Integer.parseInt(matcher.group(1));
			int dataRows = Integer.parseInt(matcher.group(2));
			if (columns < 1 || columns > 50 || dataRows < 0 || dataRows > 200) {
				dialogService.showError(event.getProject(), MarkdownTableEditorBundle.message("dialog.insertTable.error.range"));
				return;
			}

			MarkdownTableEditor.insertTable(event.getData(CommonDataKeys.EDITOR), event.getProject(), columns, dataRows);
		}

		@Override
		public void update(AnActionEvent event) {
			Editor editor = event.getData(CommonDataKeys.EDITOR);
			event.getPresentation().setEnabledAndVisible(editor != null && !editor.isViewer());
		}

		@Override
		public ActionUpdateThread getActionUpdateThread() {
			return ActionUpdateThread.EDT;
		}
	}
}
