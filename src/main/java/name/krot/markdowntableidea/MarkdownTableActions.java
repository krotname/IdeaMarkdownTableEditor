package name.krot.markdowntableidea;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import name.krot.markdowntableidea.core.MarkdownTableCore;

public final class MarkdownTableActions {
	private MarkdownTableActions() {
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
}
