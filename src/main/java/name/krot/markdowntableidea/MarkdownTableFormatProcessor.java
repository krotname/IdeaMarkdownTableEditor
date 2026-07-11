// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;

import java.util.Locale;

public final class MarkdownTableFormatProcessor implements PostFormatProcessor {
	@Override
	public PsiElement processElement(PsiElement source, CodeStyleSettings settings) {
		formatMarkdownTables(source);
		return source;
	}

	@Override
	public TextRange processText(PsiFile source, TextRange rangeToReformat, CodeStyleSettings settings) {
		Document document = documentFor(source);
		if (document == null || !isMarkdownFile(source)) {
			return rangeToReformat;
		}

		boolean changed = MarkdownTableEditor.formatTablesInRange(document, rangeToReformat.getStartOffset(), rangeToReformat.getEndOffset());
		return changed ? TextRange.create(0, document.getTextLength()) : rangeToReformat;
	}

	private static boolean formatMarkdownTables(PsiElement source) {
		if (source == null) {
			return false;
		}
		PsiFile file = source instanceof PsiFile psiFile ? psiFile : source.getContainingFile();
		Document document = documentFor(file);
		TextRange range = source.getTextRange();
		return document != null && range != null && isMarkdownFile(file) &&
			MarkdownTableEditor.formatTablesInRange(document, range.getStartOffset(), range.getEndOffset());
	}

	private static Document documentFor(PsiFile file) {
		if (file == null) {
			return null;
		}
		return PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
	}

	private static boolean isMarkdownFile(PsiFile file) {
		if (file == null) {
			return false;
		}

		String name = file.getName().toLowerCase(Locale.ROOT);
		return name.endsWith(".md") ||
			name.endsWith(".markdown") ||
			name.endsWith(".mdown") ||
			name.endsWith(".mkd") ||
			name.endsWith(".mkdn");
	}
}
