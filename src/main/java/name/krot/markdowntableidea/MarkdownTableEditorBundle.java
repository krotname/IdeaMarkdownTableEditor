// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.DynamicBundle;

public final class MarkdownTableEditorBundle extends DynamicBundle {
	private static final String BUNDLE = "messages.MarkdownTableEditorBundle";
	private static final MarkdownTableEditorBundle INSTANCE = new MarkdownTableEditorBundle();

	private MarkdownTableEditorBundle() {
		super(BUNDLE);
	}

	public static String message(String key, Object... params) {
		return INSTANCE.getMessage(key, params);
	}
}
