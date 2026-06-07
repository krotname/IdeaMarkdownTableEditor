import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import java.util.zip.ZipFile

plugins {
	java
	id("org.jetbrains.intellij.platform")
}

abstract class VerifyPackagedLicense : DefaultTask() {
	@get:InputFile
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val licenseFile: RegularFileProperty

	@get:InputFile
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val jarFile: RegularFileProperty

	@TaskAction
	fun verify() {
		val expectedLicense = licenseFile.asFile.get().readText(Charsets.UTF_8).trim()
		ZipFile(jarFile.asFile.get()).use { zip ->
			val entry = zip.getEntry("META-INF/LICENSE")
				?: error("Packaged JAR is missing META-INF/LICENSE: ${jarFile.asFile.get().absolutePath}")
			val packagedLicense = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
			check(packagedLicense == expectedLicense) {
				"Packaged META-INF/LICENSE does not match project LICENSE."
			}
		}
	}
}

val pluginVersion = providers.gradleProperty("pluginVersion")
	.orElse(providers.provider { file("VERSION").readText().trim() })
val platformVersion = providers.gradleProperty("platformVersion").orElse("2026.1.3")
val defaultLocalPlatformPath = "C:\\Program Files\\JetBrains\\IntelliJ IDEA 2026.1.3"
val platformLocalPath = providers.gradleProperty("platformLocalPath")
	.orElse(providers.provider { defaultLocalPlatformPath.takeIf { file(it).exists() } ?: "" })
val verifyRecommendedIdes = providers.gradleProperty("verifyRecommendedIdes")
	.map { it.toBoolean() }
	.orElse(providers.environmentVariable("GITHUB_ACTIONS").map { it.equals("true", ignoreCase = true) }.orElse(false))

group = "name.krot"
version = pluginVersion.get()

base {
	archivesName = "MarkdownTableEditorIdea"
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

dependencies {
	intellijPlatform {
		if (platformLocalPath.get().isNotBlank()) {
			local(platformLocalPath.get())
		} else {
			intellijIdea(platformVersion)
		}
	}
}

intellijPlatform {
	projectName = "MarkdownTableEditorIdea"
	buildSearchableOptions = false

	pluginConfiguration {
		version = pluginVersion

		ideaVersion {
			sinceBuild = "242"
			untilBuild = provider { null }
		}
	}

	pluginVerification {
		ides {
			create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2")
			current()
			if (verifyRecommendedIdes.get()) {
				recommended()
			}
		}
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release = 21
}

tasks.named<Test>("test") {
	failOnNoDiscoveredTests = false
}

val jarTask = tasks.named<Jar>("jar") {
	from(layout.projectDirectory.file("LICENSE")) {
		into("META-INF")
	}
}
val projectLicenseFile = layout.projectDirectory.file("LICENSE")
val pluginJarArchive = jarTask.flatMap { it.archiveFile }

val verifyPackagedLicense by tasks.registering(VerifyPackagedLicense::class) {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Checks that the distributable plugin JAR contains the project license."
	dependsOn(jarTask)
	licenseFile.set(projectLicenseFile)
	jarFile.set(pluginJarArchive)
}

val smokeTest by tasks.registering(JavaExec::class) {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Runs Markdown Table Editor smoke tests."
	dependsOn(tasks.named("testClasses"))
	mainClass = "name.krot.markdowntableidea.core.MarkdownTableCoreSmoke"
	classpath = sourceSets["test"].runtimeClasspath + fileTree(intellijPlatform.platformPath) {
		include("lib/*.jar")
	}
	workingDir = projectDir
}

tasks.named("check") {
	dependsOn(smokeTest)
	dependsOn(verifyPackagedLicense)
}
