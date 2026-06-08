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
val platformVersion = providers.gradleProperty("platformVersion").orElse("2024.2")
val platformLocalPath = providers.gradleProperty("platformLocalPath")
	.orElse("")
val verifyRecommendedIdes = providers.gradleProperty("verifyRecommendedIdes")
	.map { it.toBoolean() }
	.orElse(providers.environmentVariable("GITHUB_ACTIONS").map { it.equals("true", ignoreCase = true) }.orElse(false))
val verifyCurrentIde = providers.gradleProperty("verifyCurrentIde")
	.map { it.toBoolean() }
	.orElse(false)
val verifyAllJetBrainsIdes = providers.gradleProperty("verifyAllJetBrainsIdes")
	.map { it.toBoolean() }
	.orElse(false)
val verificationIdeVersion = providers.gradleProperty("verificationIdeVersion").orElse("2024.2")
val androidStudioVerificationIdeVersion = providers.gradleProperty("androidStudioVerificationIdeVersion").orElse("2024.2.2.13")
val baselineJetBrainsIdeVerificationTypes = listOf(
	IntelliJPlatformType.IntellijIdeaCommunity,
	IntelliJPlatformType.WebStorm,
	IntelliJPlatformType.PyCharmCommunity,
	IntelliJPlatformType.CLion
)
val allJetBrainsIdeVerificationTypes = listOf(
	IntelliJPlatformType.AndroidStudio,
	IntelliJPlatformType.IntellijIdeaCommunity,
	IntelliJPlatformType.IntellijIdeaUltimate,
	IntelliJPlatformType.WebStorm,
	IntelliJPlatformType.PyCharmCommunity,
	IntelliJPlatformType.PyCharmProfessional,
	IntelliJPlatformType.PhpStorm,
	IntelliJPlatformType.GoLand,
	IntelliJPlatformType.CLion,
	IntelliJPlatformType.Rider,
	IntelliJPlatformType.RubyMine
)
val jetBrainsIdeVerificationTypes = if (verifyAllJetBrainsIdes.get()) {
	allJetBrainsIdeVerificationTypes
} else {
	baselineJetBrainsIdeVerificationTypes
}

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
			jetBrainsIdeVerificationTypes.forEach {
				val version = if (it == IntelliJPlatformType.AndroidStudio) {
					androidStudioVerificationIdeVersion.get()
				} else {
					verificationIdeVersion.get()
				}
				create(it, version) {
					if (it == IntelliJPlatformType.AndroidStudio) {
						useInstaller = false
					}
					if (it == IntelliJPlatformType.Rider) {
						useInstaller = false
					}
				}
			}
			if (verifyCurrentIde.get()) {
				current()
			}
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
