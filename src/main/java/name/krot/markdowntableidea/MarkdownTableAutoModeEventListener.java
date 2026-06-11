// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;

import java.util.List;

public final class MarkdownTableAutoModeEventListener implements StartupActivity, DumbAware {
	@Override
	public void runActivity(Project project) {
		if (project == null || project.isDefault()) {
			return;
		}

		MarkdownTableEditor.handleSelectedEditorActivated(project);
		project.getMessageBus()
			.connect(project)
			.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new AutoFileEditorListener(project));
		ApplicationManager.getApplication()
			.getMessageBus()
			.connect(project)
			.subscribe(EditorColorsManager.TOPIC, new AutoEditorColorsListener(project));
		ApplicationManager.getApplication()
			.getMessageBus()
			.connect(project)
			.subscribe(VirtualFileManager.VFS_CHANGES, new AutoBulkFileListener(project));
	}

	private static final class AutoFileEditorListener implements FileEditorManagerListener {
		private final Project project;

		private AutoFileEditorListener(Project project) {
			this.project = project;
		}

		@Override
		public void fileOpened(FileEditorManager source, VirtualFile file) {
			MarkdownTableEditor.scheduleAutoFormatFile(project, file);
			MarkdownTableEditor.handleSelectedEditorActivated(project);
		}

		@Override
		public void selectionChanged(FileEditorManagerEvent event) {
			MarkdownTableEditor.handleSelectedEditorActivated(project);
		}
	}

	private static final class AutoEditorColorsListener implements EditorColorsListener {
		private final Project project;

		private AutoEditorColorsListener(Project project) {
			this.project = project;
		}

		@Override
		public void globalSchemeChange(EditorColorsScheme scheme) {
			MarkdownTableEditor.handleEditorMetricsChanged(project);
		}
	}

	private static final class AutoBulkFileListener implements BulkFileListener {
		private final Project project;

		private AutoBulkFileListener(Project project) {
			this.project = project;
		}

		@Override
		public void after(List<? extends VFileEvent> events) {
			for (VFileEvent event : events) {
				if (event instanceof VFileContentChangeEvent || event instanceof VFileMoveEvent) {
					schedule(event.getFile());
				} else if (event instanceof VFilePropertyChangeEvent propertyEvent) {
					if (VirtualFile.PROP_NAME.equals(propertyEvent.getPropertyName())) {
						schedule(propertyEvent.getFile());
					}
				} else if (event instanceof VFileCopyEvent copyEvent) {
					schedule(copyEvent.getFile());
					schedule(copyEvent.findCreatedFile());
				}
			}
		}

		private void schedule(VirtualFile file) {
			MarkdownTableEditor.scheduleAutoFormatFile(project, file);
		}
	}
}
