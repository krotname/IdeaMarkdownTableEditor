package name.krot.markdowntableidea;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import name.krot.markdowntableidea.core.MarkdownTableCore;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MarkdownTableTabHandler extends EditorActionHandler {
	private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

	private final EditorActionHandler original;

	private MarkdownTableTabHandler(EditorActionHandler original) {
		this.original = original;
	}

	public static void install() {
		if (!INSTALLED.compareAndSet(false, true)) {
			return;
		}

		EditorActionManager manager = EditorActionManager.getInstance();
		EditorActionHandler current = manager.getActionHandler(IdeActions.ACTION_EDITOR_TAB);
		if (current instanceof MarkdownTableTabHandler) {
			return;
		}
		manager.setActionHandler(IdeActions.ACTION_EDITOR_TAB, new MarkdownTableTabHandler(current));
	}

	@Override
	protected boolean isEnabledForCaret(Editor editor, Caret caret, DataContext dataContext) {
		return original == null || original.isEnabled(editor, caret, dataContext);
	}

	@Override
	protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
		Project project = CommonDataKeys.PROJECT.getData(dataContext);
		if (MarkdownTableEditor.run(editor, project, MarkdownTableCore.Action.ALIGN, true)) {
			return;
		}

		if (original != null) {
			original.execute(editor, caret, dataContext);
		}
	}
}
