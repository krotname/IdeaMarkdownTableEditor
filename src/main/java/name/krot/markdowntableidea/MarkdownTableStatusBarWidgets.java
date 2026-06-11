// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntableidea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import name.krot.markdowntableidea.core.MarkdownTableCore;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;

public final class MarkdownTableStatusBarWidgets {
	private static final Logger LOG = Logger.getInstance(MarkdownTableStatusBarWidgets.class);
	private static final int INSTALL_RETRY_COUNT = 8;
	private static final int INSTALL_RETRY_DELAY_MS = 250;
	static final String AUTO_ALIGN_WIDGET_ID = "markdownTableAutoAlignWidget";
	static final String AUTO_FIT_WIDGET_ID = "markdownTableAutoFitWidget";

	private MarkdownTableStatusBarWidgets() {
	}

	static void update(Project project) {
		if (project == null) {
			return;
		}

		StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
		if (statusBar != null) {
			update(statusBar);
		}
	}

	private static void update(StatusBar statusBar) {
		statusBar.updateWidget(AUTO_ALIGN_WIDGET_ID);
		statusBar.updateWidget(AUTO_FIT_WIDGET_ID);
	}

	private static Method findMethod(Class<?> ownerClass, String name, Class<?>... parameterTypes) {
		Class<?> current = ownerClass;
		while (current != null) {
			try {
				return current.getDeclaredMethod(name, parameterTypes);
			} catch (NoSuchMethodException ignored) {
				current = current.getSuperclass();
			}
		}
		return null;
	}

	public static final class LeftInstaller implements StartupActivity, DumbAware {
		@Override
		public void runActivity(Project project) {
			installLeft(project, INSTALL_RETRY_COUNT);
		}
	}

	private static void installLeft(Project project, int attemptsRemaining) {
		if (project == null || project.isDefault()) {
			return;
		}

		ApplicationManager.getApplication().invokeLater(() -> installLeftNow(project, attemptsRemaining));
	}

	private static void installLeftNow(Project project, int attemptsRemaining) {
		if (project == null || project.isDefault() || !project.isOpen()) {
			return;
		}

		StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
		if (statusBar == null) {
			scheduleInstallRetry(project, attemptsRemaining);
			return;
		}

		installLeftWidget(statusBar, project, AutoMode.AUTO_ALIGN);
		installLeftWidget(statusBar, project, AutoMode.AUTO_FIT);
	}

	private static void scheduleInstallRetry(Project project, int attemptsRemaining) {
		if (attemptsRemaining <= 0) {
			LOG.warn("Markdown Table Editor status bar is not ready; auto mode buttons were not installed.");
			return;
		}

		Timer timer = new Timer(INSTALL_RETRY_DELAY_MS, event -> installLeft(project, attemptsRemaining - 1));
		timer.setRepeats(false);
		timer.start();
	}

	private static void installLeftWidget(StatusBar statusBar, Project project, AutoMode mode) {
		if (statusBar.getWidget(mode.widgetId) != null) {
			statusBar.updateWidget(mode.widgetId);
			return;
		}

		AutoModeWidget widget = new AutoModeWidget(project, mode);
		if (tryAddWidgetToLeft(statusBar, widget, project) || tryAddWidgetWithInternalPosition(statusBar, widget, project)) {
			return;
		}

		LOG.warn("Could not place Markdown Table Editor status widget on the left; falling back to the default status bar area.");
		tryAddDefaultWidget(statusBar, widget, project);
	}

	private static boolean tryAddWidgetToLeft(StatusBar statusBar, StatusBarWidget widget, Disposable parentDisposable) {
		try {
			Method method = findMethod(statusBar.getClass(), "addWidgetToLeft", StatusBarWidget.class, Disposable.class);
			if (method == null) {
				return false;
			}

			method.setAccessible(true);
			method.invoke(statusBar, widget, parentDisposable);
			return true;
		} catch (ReflectiveOperationException | RuntimeException error) {
			LOG.warn("Could not use statusBar.addWidgetToLeft for Markdown Table Editor status widget.", error);
			return false;
		}
	}

	private static boolean tryAddWidgetWithInternalPosition(StatusBar statusBar, StatusBarWidget widget, Disposable parentDisposable) {
		try {
			ClassLoader classLoader = statusBar.getClass().getClassLoader();
			Class<?> positionClass = Class.forName("com.intellij.openapi.wm.impl.status.Position", false, classLoader);
			Object leftPosition = Enum.valueOf(positionClass.asSubclass(Enum.class), "LEFT");
			Class<?> loadingOrderClass = Class.forName("com.intellij.openapi.extensions.LoadingOrder", false, classLoader);
			Object firstOrder = loadingOrderClass.getField("FIRST").get(null);
			Method method = findMethod(statusBar.getClass(), "addWidget", StatusBarWidget.class, positionClass, loadingOrderClass);
			if (method == null) {
				return false;
			}

			method.setAccessible(true);
			method.invoke(statusBar, widget, leftPosition, firstOrder);
			Disposer.register(parentDisposable, () -> removeWidgetQuietly(statusBar, widget.ID()));
			return true;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
			LOG.warn("Could not use internal left status bar position for Markdown Table Editor status widget.", error);
			return false;
		}
	}

	private static void tryAddDefaultWidget(StatusBar statusBar, StatusBarWidget widget, Disposable parentDisposable) {
		try {
			Method method = findMethod(statusBar.getClass(), "addWidget", StatusBarWidget.class, String.class, Disposable.class);
			if (method == null) {
				return;
			}

			method.setAccessible(true);
			method.invoke(statusBar, widget, "first", parentDisposable);
		} catch (ReflectiveOperationException | RuntimeException error) {
			LOG.warn("Could not install Markdown Table Editor status widget.", error);
		}
	}

	private static void removeWidgetQuietly(StatusBar statusBar, String widgetId) {
		try {
			Method method = findMethod(statusBar.getClass(), "removeWidget", String.class);
			if (method != null) {
				method.setAccessible(true);
				method.invoke(statusBar, widgetId);
			}
		} catch (ReflectiveOperationException | RuntimeException error) {
			LOG.debug(error);
		}
	}

	public static final class AutoAlignFactory extends AutoModeWidgetFactory {
		public AutoAlignFactory() {
			super(AutoMode.AUTO_ALIGN);
		}
	}

	public static final class AutoFitFactory extends AutoModeWidgetFactory {
		public AutoFitFactory() {
			super(AutoMode.AUTO_FIT);
		}
	}

	private abstract static class AutoModeWidgetFactory implements StatusBarWidgetFactory, DumbAware {
		private final AutoMode mode;

		private AutoModeWidgetFactory(AutoMode mode) {
			this.mode = mode;
		}

		@Override
		public String getId() {
			return mode.widgetId;
		}

		@Override
		public String getDisplayName() {
			return MarkdownTableEditorBundle.message(mode.displayNameKey);
		}

		@Override
		public boolean isAvailable(Project project) {
			return true;
		}

		@Override
		public StatusBarWidget createWidget(Project project) {
			return new AutoModeWidget(project, mode);
		}

		@Override
		public void disposeWidget(StatusBarWidget widget) {
			Disposer.dispose(widget);
		}

		@Override
		public boolean canBeEnabledOn(StatusBar statusBar) {
			return true;
		}

		@Override
		public boolean isEnabledByDefault() {
			return true;
		}
	}

	private static final class AutoModeWidget implements StatusBarWidget, StatusBarWidget.TextPresentation, Disposable {
		private final Project project;
		private final AutoMode mode;
		private StatusBar statusBar;
		private MessageBusConnection messageBusConnection;

		private AutoModeWidget(Project project, AutoMode mode) {
			this.project = project;
			this.mode = mode;
		}

		@Override
		public String ID() {
			return mode.widgetId;
		}

		@Override
		public WidgetPresentation getPresentation() {
			return this;
		}

		@Override
		public void install(StatusBar statusBar) {
			this.statusBar = statusBar;
			if (project != null) {
				messageBusConnection = project.getMessageBus().connect(this);
				messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
					@Override
					public void selectionChanged(FileEditorManagerEvent event) {
						refreshStatusBar();
					}

					@Override
					public void fileOpened(FileEditorManager source, com.intellij.openapi.vfs.VirtualFile file) {
						refreshStatusBar();
					}

					@Override
					public void fileClosed(FileEditorManager source, com.intellij.openapi.vfs.VirtualFile file) {
						refreshStatusBar();
					}
				});
			}
			refreshStatusBar();
		}

		@Override
		public void dispose() {
			if (messageBusConnection != null) {
				messageBusConnection.disconnect();
				messageBusConnection = null;
			}
			statusBar = null;
		}

		@Override
		public String getText() {
			return MarkdownTableEditorBundle.message(mode.textKey(isSelected()));
		}

		@Override
		public float getAlignment() {
			return 0.5f;
		}

		@Override
		public String getTooltipText() {
			return MarkdownTableEditorBundle.message(mode.tooltipKey(isSelected()));
		}

		@Override
		public Consumer<MouseEvent> getClickConsumer() {
			return event -> toggle();
		}

		private boolean isSelected() {
			MarkdownTableSettings settings = MarkdownTableSettings.getInstance();
			return mode == AutoMode.AUTO_ALIGN ? settings.isAutoAlignEnabled() : settings.isAutoFitEnabled();
		}

		private void toggle() {
			if (!isMarkdownContext()) {
				updateVisibilityLater();
				return;
			}

			MarkdownTableSettings settings = MarkdownTableSettings.getInstance();
			boolean enabled = !isSelected();
			if (mode == AutoMode.AUTO_ALIGN) {
				settings.setAutoAlignEnabled(enabled);
			} else {
				settings.setAutoFitEnabled(enabled);
			}

			if (enabled && project != null) {
				if (mode == AutoMode.AUTO_ALIGN) {
					MarkdownTableEditor.run(
						FileEditorManager.getInstance(project).getSelectedTextEditor(),
						project,
						MarkdownTableCore.Action.ALIGN,
						true
					);
				} else {
					MarkdownTableEditor.fitToEditorWidth(FileEditorManager.getInstance(project).getSelectedTextEditor(), project, true);
				}
			}

			if (statusBar != null) {
				update(statusBar);
			} else {
				update(project);
			}
		}

		private void refreshStatusBar() {
			if (statusBar != null) {
				update(statusBar);
			}
			updateVisibilityLater();
		}

		private void updateVisibilityLater() {
			Runnable update = this::updateVisibility;
			if (ApplicationManager.getApplication() == null) {
				update.run();
				return;
			}

			ApplicationManager.getApplication().invokeLater(update);
		}

		private void updateVisibility() {
			if (statusBar == null) {
				return;
			}

			JComponent component = widgetComponent();
			if (component == null) {
				return;
			}

			boolean visible = isMarkdownContext();
			if (component.isVisible() != visible) {
				component.setVisible(visible);
				Container parent = component.getParent();
				if (parent != null) {
					parent.revalidate();
					parent.repaint();
				}
			}
		}

		private JComponent widgetComponent() {
			try {
				Method method = findMethod(statusBar.getClass(), "getWidgetComponent", String.class);
				if (method == null) {
					return null;
				}

				method.setAccessible(true);
				Object component = method.invoke(statusBar, ID());
				return component instanceof JComponent jComponent ? jComponent : null;
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				return null;
			}
		}

		private boolean isMarkdownContext() {
			if (project == null || project.isDefault()) {
				return true;
			}
			if (!project.isOpen()) {
				return false;
			}

			Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
			return MarkdownTableEditor.isMarkdownEditor(editor);
		}
	}

	private enum AutoMode {
		AUTO_ALIGN(
			AUTO_ALIGN_WIDGET_ID,
			"status.MarkdownTableEditor.AutoAlign.displayName",
			"status.MarkdownTableEditor.AutoAlign.on",
			"status.MarkdownTableEditor.AutoAlign.off",
			"status.MarkdownTableEditor.AutoAlign.tooltip.on",
			"status.MarkdownTableEditor.AutoAlign.tooltip.off"
		),
		AUTO_FIT(
			AUTO_FIT_WIDGET_ID,
			"status.MarkdownTableEditor.AutoFit.displayName",
			"status.MarkdownTableEditor.AutoFit.on",
			"status.MarkdownTableEditor.AutoFit.off",
			"status.MarkdownTableEditor.AutoFit.tooltip.on",
			"status.MarkdownTableEditor.AutoFit.tooltip.off"
		);

		private final String widgetId;
		private final String displayNameKey;
		private final String textOnKey;
		private final String textOffKey;
		private final String tooltipOnKey;
		private final String tooltipOffKey;

		AutoMode(
			String widgetId,
			String displayNameKey,
			String textOnKey,
			String textOffKey,
			String tooltipOnKey,
			String tooltipOffKey
		) {
			this.widgetId = widgetId;
			this.displayNameKey = displayNameKey;
			this.textOnKey = textOnKey;
			this.textOffKey = textOffKey;
			this.tooltipOnKey = tooltipOnKey;
			this.tooltipOffKey = tooltipOffKey;
		}

		private String textKey(boolean enabled) {
			return enabled ? textOnKey : textOffKey;
		}

		private String tooltipKey(boolean enabled) {
			return enabled ? tooltipOnKey : tooltipOffKey;
		}
	}
}
