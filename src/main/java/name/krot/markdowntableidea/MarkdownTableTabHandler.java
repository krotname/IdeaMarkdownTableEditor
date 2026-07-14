// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import name.krot.markdowntableidea.core.MarkdownTableCore;
import org.jetbrains.annotations.Nullable;

public final class MarkdownTableTabHandler extends EditorActionHandler {
	private final EditorActionHandler original;

	public MarkdownTableTabHandler(EditorActionHandler original) {
		this.original = original;
	}

	@Override
	protected boolean isEnabledForCaret(Editor editor, Caret caret, DataContext dataContext) {
		if (editor != null && editor.getCaretModel().getCaretCount() != 1) {
			return original == null || original.isEnabled(editor, caret, dataContext);
		}
		return MarkdownTableEditor.isInsidePotentialTable(editor) || original == null || original.isEnabled(editor, caret, dataContext);
	}

	@Override
	protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
		if (editor != null && editor.getCaretModel().getCaretCount() != 1) {
			if (original != null) {
				original.execute(editor, caret, dataContext);
			}
			return;
		}
		Project project = CommonDataKeys.PROJECT.getData(dataContext);
		if (MarkdownTableEditor.run(editor, project, MarkdownTableCore.Action.ALIGN, true)) {
			return;
		}

		if (original != null) {
			original.execute(editor, caret, dataContext);
		}
	}
}
