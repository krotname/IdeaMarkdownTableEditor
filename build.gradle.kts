import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.util.zip.ZipFile

plugins {
	java
	jacoco
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

abstract class GenerateMarketplaceSubmission : DefaultTask() {
	@get:InputFile
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val templateFile: RegularFileProperty

	@get:OutputFile
	abstract val outputFile: RegularFileProperty

	@get:Input
	abstract val pluginVersion: Property<String>

	@TaskAction
	fun generate() {
		val version = pluginVersion.get()
		val template = templateFile.asFile.get().readText(Charsets.UTF_8)
		check(template.contains("@PLUGIN_VERSION@")) {
			"MARKETPLACE_SUBMISSION.md must contain @PLUGIN_VERSION@ placeholders."
		}
		val generatedFile = outputFile.asFile.get()
		generatedFile.parentFile.mkdirs()
		generatedFile.writeText(template.replace("@PLUGIN_VERSION@", version), Charsets.UTF_8)
	}
}

abstract class VerifyReleaseMetadata : DefaultTask() {
	@get:InputFile
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val sourcePluginXmlFile: RegularFileProperty

	@get:InputFile
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val processedPluginXmlFile: RegularFileProperty

	@get:InputFile
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val generatedMarketplaceSubmissionFile: RegularFileProperty

	@get:Input
	abstract val pluginVersion: Property<String>

	@TaskAction
	fun verify() {
		val version = pluginVersion.get()
		logger.lifecycle("Verifying release metadata for version $version.")
		val sourcePluginXml = sourcePluginXmlFile.asFile.get().readText(Charsets.UTF_8)
		check(sourcePluginXml.contains("<version>@PLUGIN_VERSION@</version>")) {
			"Source plugin.xml must keep @PLUGIN_VERSION@ as the only version placeholder."
		}

		val processedFile = processedPluginXmlFile.asFile.get()
		logger.lifecycle("Checking processed plugin descriptor: ${processedFile.absolutePath}")
		val processedPluginXml = processedFile.readText(Charsets.UTF_8)
		check(processedPluginXml.contains("<version>$version</version>")) {
			"Processed plugin.xml must contain release version $version."
		}
		check(!processedPluginXml.contains("@PLUGIN_VERSION@")) {
			"Processed plugin.xml still contains @PLUGIN_VERSION@."
		}

		val generatedMarketplace = generatedMarketplaceSubmissionFile.asFile.get().readText(Charsets.UTF_8)
		check(generatedMarketplace.contains("Version: `$version`")) {
			"Generated Marketplace submission must contain release version $version."
		}
		check(generatedMarketplace.contains("MarkdownTableEditorIdea-$version.zip")) {
			"Generated Marketplace submission must point at the release ZIP for $version."
		}
		check(!generatedMarketplace.contains("@PLUGIN_VERSION@")) {
			"Generated Marketplace submission still contains @PLUGIN_VERSION@."
		}
	}
}

val pluginVersion = providers.gradleProperty("pluginVersion")
	.orElse(providers.provider { file("VERSION").readText(Charsets.UTF_8).trim() })
	.map { it.trim() }
val resolvedPluginVersion = pluginVersion.get()
check(Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?""").matches(resolvedPluginVersion)) {
	"pluginVersion must be a semantic version, got '$resolvedPluginVersion'."
}
val platformVersion = providers.gradleProperty("platformVersion").orElse("2022.3")
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
val verificationIdeVersion = providers.gradleProperty("verificationIdeVersion").orElse("2022.3.3")
val androidStudioVerificationIdeVersion = providers.gradleProperty("androidStudioVerificationIdeVersion").orElse("2024.2.2.13")
val baselineJetBrainsIdeVerificationTypes = listOf(
	IntelliJPlatformType.IntellijIdeaCommunity
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
version = resolvedPluginVersion

base {
	archivesName = "MarkdownTableEditorIdea"
}

jacoco {
	toolVersion = "0.8.13"
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

dependencies {
	testImplementation(platform("org.junit:junit-bom:6.1.0"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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
			sinceBuild = "223"
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
	options.release = 17
}

val sourcePluginXmlPath = layout.projectDirectory.file("src/main/resources/META-INF/plugin.xml")
val processedPluginXmlPath = layout.buildDirectory.file("resources/main/META-INF/plugin.xml")
val marketplaceSubmissionTemplate = layout.projectDirectory.file("MARKETPLACE_SUBMISSION.md")
val generatedMarketplaceSubmission = layout.buildDirectory.file("release/MARKETPLACE_SUBMISSION.md")
val goldenFixtureFile = layout.projectDirectory.file("test-fixtures/markdown-table-core-golden.json")
val corePerformanceThresholdScale = providers.gradleProperty("corePerformanceThresholdScale")
	.orElse(providers.environmentVariable("CORE_PERFORMANCE_THRESHOLD_SCALE"))
	.orElse("1.0")
val coreCoverageClassDirectories = layout.buildDirectory.dir("instrumented/instrumentCode").map {
	fileTree(it) {
		include("name/krot/markdowntableidea/core/MarkdownTableCore*.class")
	}
}
val coreCoverageSourceDirectories = files("src/main/java")
val coreCoverageExecutionData = layout.buildDirectory.file("jacoco/test.exec")
val ideaExecutable = providers.gradleProperty("ideaExecutable")
	.orElse("C:\\Program Files\\JetBrains\\IntelliJ IDEA 2026.1.3\\bin\\idea64.exe")
val ideaPlaybackKeepInstalled = providers.gradleProperty("ideaPlaybackKeepInstalled")
	.map { it.toBoolean() }
	.orElse(false)

val generateMarketplaceSubmission by tasks.registering(GenerateMarketplaceSubmission::class) {
	group = "distribution"
	description = "Generates JetBrains Marketplace submission notes with the resolved plugin version."
	templateFile.set(marketplaceSubmissionTemplate)
	outputFile.set(generatedMarketplaceSubmission)
	pluginVersion.set(resolvedPluginVersion)
}

val verifyReleaseMetadata by tasks.registering(VerifyReleaseMetadata::class) {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Checks generated plugin and Marketplace metadata use the resolved release version."
	dependsOn(tasks.named("processResources"))
	dependsOn(generateMarketplaceSubmission)
	sourcePluginXmlFile.set(sourcePluginXmlPath)
	processedPluginXmlFile.set(processedPluginXmlPath)
	generatedMarketplaceSubmissionFile.set(generatedMarketplaceSubmission)
	pluginVersion.set(resolvedPluginVersion)
}

tasks.named<Test>("test") {
	useJUnitPlatform()
	include("**/*Smoke.class", "**/*Test.class", "**/*Tests.class")
	failOnNoDiscoveredTests = true
	inputs.file(goldenFixtureFile).withPathSensitivity(PathSensitivity.RELATIVE)
	systemProperty("pluginVersion", resolvedPluginVersion)
	workingDir = projectDir
	classpath += fileTree(intellijPlatform.platformPath) {
		include("lib/*.jar")
	}
	extensions.configure(JacocoTaskExtension::class) {
		isIncludeNoLocationClasses = true
		excludes = listOf("jdk.internal.*")
	}
}

val corePerformance by tasks.registering(Test::class) {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Runs core performance benchmarks with time thresholds."
	useJUnitPlatform()
	include("**/*Performance.class")
	failOnNoDiscoveredTests = true
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	workingDir = projectDir
	shouldRunAfter(tasks.named("test"))
	systemProperty("corePerformanceThresholdScale", corePerformanceThresholdScale.get())
	outputs.upToDateWhen { false }
	testLogging {
		events("passed", "failed", "skipped")
		showStandardStreams = true
	}
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

val ideaPlaybackSmoke by tasks.registering(Exec::class) {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Runs live IntelliJ IDEA UI playback smoke against the packaged plugin ZIP."
	dependsOn(tasks.named("buildPlugin"))
	val playbackScript = layout.projectDirectory.file("scripts/Invoke-IdeaPlaybackSmoke.ps1")
	val pluginZip = layout.buildDirectory.file("distributions/MarkdownTableEditorIdea-$resolvedPluginVersion.zip")
	inputs.file(playbackScript)
	inputs.file(pluginZip)
	outputs.dir(layout.buildDirectory.dir("idea-playback-smoke"))
	commandLine(
		"powershell",
		"-NoProfile",
		"-ExecutionPolicy",
		"Bypass",
		"-File",
		playbackScript.asFile.absolutePath,
		"-IdeaExe",
		ideaExecutable.get(),
		"-PluginZip",
		pluginZip.get().asFile.absolutePath
	)
	if (ideaPlaybackKeepInstalled.get()) {
		args("-KeepInstalledPlugin")
	}
}

tasks.named<JacocoReport>("jacocoTestReport") {
	dependsOn(tasks.named("test"))
	classDirectories.setFrom(coreCoverageClassDirectories)
	sourceDirectories.setFrom(coreCoverageSourceDirectories)
	additionalSourceDirs.setFrom(coreCoverageSourceDirectories)
	executionData.setFrom(coreCoverageExecutionData)
	reports {
		xml.required.set(true)
		xml.outputLocation.set(layout.buildDirectory.file("reports/coverage/jacoco.xml"))
		csv.required.set(true)
		csv.outputLocation.set(layout.buildDirectory.file("reports/coverage/jacoco.csv"))
		html.required.set(true)
		html.outputLocation.set(layout.buildDirectory.dir("reports/coverage/html"))
	}
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
	dependsOn(tasks.named("test"))
	classDirectories.setFrom(coreCoverageClassDirectories)
	sourceDirectories.setFrom(coreCoverageSourceDirectories)
	executionData.setFrom(coreCoverageExecutionData)
	violationRules {
		rule {
			limit {
				counter = "LINE"
				value = "COVEREDRATIO"
				minimum = "0.70".toBigDecimal()
			}
		}
	}
}

tasks.named("check") {
	dependsOn(verifyPackagedLicense)
	dependsOn(verifyReleaseMetadata)
	dependsOn(tasks.named("jacocoTestReport"))
	dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

tasks.named("buildPlugin") {
	dependsOn(generateMarketplaceSubmission)
}
