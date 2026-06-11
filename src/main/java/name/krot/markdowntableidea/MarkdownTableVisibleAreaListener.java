// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.VisibleAreaListener;

import javax.swing.JComponent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeListener;
import java.util.IdentityHashMap;
import java.util.Map;

public final class MarkdownTableVisibleAreaListener implements EditorFactoryListener {
	private final Map<Editor, AttachedListeners> listeners = new IdentityHashMap<>();

	public MarkdownTableVisibleAreaListener() {
		for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
			attach(editor);
		}
	}

	@Override
	public void editorCreated(EditorFactoryEvent event) {
		attach(event.getEditor());
	}

	@Override
	public void editorReleased(EditorFactoryEvent event) {
		detach(event.getEditor());
	}

	private void attach(Editor editor) {
		if (listeners.containsKey(editor)) {
			return;
		}
		VisibleAreaListener listener = visibleAreaEvent ->
			MarkdownTableEditor.handleVisibleAreaChanged(editor, visibleAreaEvent.getNewRectangle());
		ComponentListener componentListener = new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent event) {
				MarkdownTableEditor.handleVisibleAreaChanged(editor, null);
			}

			@Override
			public void componentShown(ComponentEvent event) {
				MarkdownTableEditor.rememberVisibleEditorWidth(editor);
				MarkdownTableEditor.scheduleAutoFormatEditor(editor, editor.getProject(), 0);
			}
		};
		CaretListener caretListener = new CaretListener() {
			@Override
			public void caretPositionChanged(CaretEvent event) {
				scheduleCaretAutoFormat(editor);
			}

			@Override
			public void caretAdded(CaretEvent event) {
				scheduleCaretAutoFormat(editor);
			}

			@Override
			public void caretRemoved(CaretEvent event) {
				scheduleCaretAutoFormat(editor);
			}
		};
		PropertyChangeListener fontListener = event -> {
			if ("font".equals(event.getPropertyName())) {
				MarkdownTableEditor.handleVisibleAreaChanged(editor, null);
			}
		};

		listeners.put(editor, new AttachedListeners(listener, componentListener, caretListener, fontListener));
		MarkdownTableEditor.rememberVisibleEditorWidth(editor);
		editor.getScrollingModel().addVisibleAreaListener(listener);
		editor.getCaretModel().addCaretListener(caretListener);
		JComponent component = editor.getContentComponent();
		if (component != null) {
			component.addComponentListener(componentListener);
			component.addPropertyChangeListener("font", fontListener);
		}
	}

	private void detach(Editor editor) {
		AttachedListeners attached = listeners.remove(editor);
		if (attached != null) {
			editor.getScrollingModel().removeVisibleAreaListener(attached.visibleAreaListener);
			editor.getCaretModel().removeCaretListener(attached.caretListener);
			JComponent component = editor.getContentComponent();
			if (component != null) {
				component.removeComponentListener(attached.componentListener);
				component.removePropertyChangeListener("font", attached.fontListener);
			}
		}
		MarkdownTableEditor.forgetVisibleEditorWidth(editor);
	}

	private static void scheduleCaretAutoFormat(Editor editor) {
		MarkdownTableEditor.scheduleAutoFormatEditor(
			editor,
			editor == null ? null : editor.getProject(),
			MarkdownTableSettings.getInstance().getDebounceMs()
		);
	}

	private record AttachedListeners(
		VisibleAreaListener visibleAreaListener,
		ComponentListener componentListener,
		CaretListener caretListener,
		PropertyChangeListener fontListener
	) {
	}
}
