import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
	`java-library`
	jacoco
	id("com.vanniktech.maven.publish") version "0.37.0"
}

abstract class VerifyCentralPublicationTask : DefaultTask() {
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val artifactFiles: ConfigurableFileCollection

	@get:InputFile
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val pomFile: RegularFileProperty

	@get:Input
	abstract val requiredPomFragments: ListProperty<String>

	@TaskAction
	fun verify() {
		artifactFiles.files.forEach {
			check(it.isFile && it.length() > 0) { "Missing or empty Central artifact: ${it.absolutePath}" }
		}

		val generatedPom = pomFile.asFile.get()
		check(generatedPom.isFile) { "Missing generated POM: ${generatedPom.absolutePath}" }
		val pom = generatedPom.readText(Charsets.UTF_8)
		requiredPomFragments.get().forEach { required ->
			check(pom.contains(required)) { "Generated POM is missing $required" }
		}
	}
}

val coreVersion = providers.gradleProperty("coreVersion")
	.orElse(providers.provider { file("VERSION").readText(Charsets.UTF_8).trim() })
	.map { it.trim() }
val resolvedCoreVersion = coreVersion.get()
check(Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?""").matches(resolvedCoreVersion)) {
	"coreVersion must be a semantic version, got '$resolvedCoreVersion'."
}

group = "name.krot"
version = resolvedCoreVersion

base {
	archivesName = "markdown-table-core"
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

dependencies {
	testImplementation(platform("org.junit:junit-bom:6.1.2"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release = 17
}

tasks.withType<Javadoc>().configureEach {
	options.encoding = "UTF-8"
}

tasks.named<Test>("test") {
	useJUnitPlatform()
	exclude("**/*Performance.class")
	failOnNoDiscoveredTests = true
}

jacoco {
	toolVersion = "0.8.13"
}

tasks.named<JacocoReport>("jacocoTestReport") {
	dependsOn(tasks.named("test"))
	dependsOn("corePerformance")
	executionData(
		layout.buildDirectory.file("jacoco/test.exec"),
		layout.buildDirectory.file("jacoco/corePerformance.exec")
	)
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
	dependsOn("corePerformance")
	executionData(
		layout.buildDirectory.file("jacoco/test.exec"),
		layout.buildDirectory.file("jacoco/corePerformance.exec")
	)
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

val corePerformanceThresholdScale = providers.gradleProperty("corePerformanceThresholdScale")
	.orElse(providers.environmentVariable("CORE_PERFORMANCE_THRESHOLD_SCALE"))
	.orElse("1.0")

val corePerformance = tasks.register<Test>("corePerformance") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Runs core performance benchmarks with time thresholds."
	useJUnitPlatform()
	include("**/*Performance.class")
	shouldRunAfter(tasks.named("test"))
	failOnNoDiscoveredTests = true
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	workingDir = projectDir
	systemProperty("corePerformanceThresholdScale", corePerformanceThresholdScale.get())
	outputs.upToDateWhen { false }
	testLogging {
		events("passed", "failed", "skipped")
		showStandardStreams = true
	}
}

tasks.named<Jar>("jar") {
	from(rootProject.layout.projectDirectory.file("LICENSE")) {
		into("META-INF")
	}
	manifest {
		attributes["Automatic-Module-Name"] = "name.krot.markdowntable.core"
	}
}

mavenPublishing {
	publishToMavenCentral()
	signAllPublications()
	coordinates("name.krot", "markdown-table-core", resolvedCoreVersion)

	pom {
		name = "Markdown Table Core"
		description = "Dependency-free parsing, formatting, conversion, and editing for Markdown pipe tables."
		inceptionYear = "2026"
		url = "https://github.com/krotname/IdeaMarkdownTableEditor"
		licenses {
			license {
				name = "MIT License"
				url = "https://opensource.org/license/mit"
				distribution = "https://opensource.org/license/mit"
			}
		}
		developers {
			developer {
				id = "krotname"
				name = "Andrei Ovcharenko"
				url = "https://github.com/krotname"
			}
		}
		scm {
			url = "https://github.com/krotname/IdeaMarkdownTableEditor"
			connection = "scm:git:https://github.com/krotname/IdeaMarkdownTableEditor.git"
			developerConnection = "scm:git:ssh://git@github.com/krotname/IdeaMarkdownTableEditor.git"
		}
	}
}

val verifyCentralPublication = tasks.register<VerifyCentralPublicationTask>("verifyCentralPublication") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Builds and validates the Maven Central publication without uploading it."
	dependsOn(tasks.named("jar"))
	dependsOn(tasks.named("sourcesJar"))
	dependsOn(tasks.named("plainJavadocJar"))
	dependsOn(tasks.withType<GenerateMavenPom>())
	artifactFiles.from(
		layout.buildDirectory.file("libs/markdown-table-core-$resolvedCoreVersion.jar"),
		layout.buildDirectory.file("libs/markdown-table-core-$resolvedCoreVersion-sources.jar"),
		layout.buildDirectory.file("libs/markdown-table-core-$resolvedCoreVersion-javadoc.jar")
	)
	pomFile.set(layout.buildDirectory.file("publications/maven/pom-default.xml"))
	requiredPomFragments.set(listOf(
		"<groupId>name.krot</groupId>",
		"<artifactId>markdown-table-core</artifactId>",
		"<version>$resolvedCoreVersion</version>",
		"<name>Markdown Table Core</name>",
		"<licenses>",
		"<developers>",
		"<scm>"
	))
}

tasks.named("check") {
	dependsOn(tasks.named("jacocoTestReport"))
	dependsOn(tasks.named("jacocoTestCoverageVerification"))
	dependsOn(verifyCentralPublication)
}
