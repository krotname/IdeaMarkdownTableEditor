// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@State(name = "MarkdownTableEditorSettings", storages = @Storage("markdownTableEditor.xml"))
public final class MarkdownTableSettings implements PersistentStateComponent<MarkdownTableSettings.OptionState> {
	private static final MarkdownTableSettings FALLBACK = new MarkdownTableSettings();

	private OptionState state = new OptionState();

	public static MarkdownTableSettings getInstance() {
		Application application = ApplicationManager.getApplication();
		if (application == null) {
			return FALLBACK;
		}
		MarkdownTableSettings service = application.getService(MarkdownTableSettings.class);
		return service == null ? FALLBACK : service;
	}

	static void resetFallbackForTests() {
		FALLBACK.state = new OptionState();
	}

	@Override
	public OptionState getState() {
		enforceAutoModeDependency();
		return state;
	}

	@Override
	public void loadState(@NotNull OptionState state) {
		this.state = state;
		enforceAutoModeDependency();
	}

	public boolean isAutoAlignEnabled() {
		enforceAutoModeDependency();
		return state.autoAlignEnabled;
	}

	public void setAutoAlignEnabled(boolean enabled) {
		if (state.autoFitEnabled && !enabled) {
			state.autoAlignEnabled = true;
			return;
		}
		state.autoAlignEnabled = enabled;
	}

	public boolean isAutoFitEnabled() {
		return state.autoFitEnabled;
	}

	public void setAutoFitEnabled(boolean enabled) {
		state.autoFitEnabled = enabled;
		if (enabled) {
			state.autoAlignEnabled = true;
		}
	}

	public int getDebounceMs() {
		return Math.max(1, state.debounceMs);
	}

	private void enforceAutoModeDependency() {
		if (state.autoFitEnabled) {
			state.autoAlignEnabled = true;
		}
	}

	public static final class OptionState {
		public boolean autoAlignEnabled = true;
		public boolean autoFitEnabled = true;
		public int debounceMs = 160;
	}
}
