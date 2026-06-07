package name.krot.markdowntableidea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public final class MarkdownTableStartupActivity implements StartupActivity.DumbAware {
	@Override
	public void runActivity(@NotNull Project project) {
		MarkdownTableTabHandler.install();
	}
}
