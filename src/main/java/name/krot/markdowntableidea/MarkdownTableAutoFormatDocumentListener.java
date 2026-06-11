// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;

public final class MarkdownTableAutoFormatDocumentListener implements DocumentListener {
	@Override
	public void documentChanged(DocumentEvent event) {
		MarkdownTableEditor.scheduleAutoFormatChangedDocument(event.getDocument());
	}
}
