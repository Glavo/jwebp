import de.undercouch.gradle.tasks.download.Download
import org.gradle.kotlin.dsl.sourceSets

plugins {
    id("java-library")
    id("jacoco")
    id("maven-publish")
    id("signing")
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.glavo.load-maven-publish-properties") version "0.1.0"
    id("de.undercouch.download") version "5.7.0"
}

group = "org.glavo"

if (version == Project.DEFAULT_VERSION) {
    version = "0.1.0" + "-SNAPSHOT"
}

description = "Pure Java implementation of WebP decoding library"

repositories {
    mavenCentral()
}

val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()

dependencies {
    compileOnlyApi("org.jetbrains:annotations:26.1.0")

    val javafxVersion = "21.0.10"
    val javafxOS = when {
        osName.contains("win") -> "win"
        osName.contains("mac") -> "mac"
        osName.contains("linux") -> "linux"
        else -> null
    }
    val javafxArch = when (osArch) {
        "amd64", "x86-64", "x64" -> ""
        "aarch64", "arm64" -> "-aarch64"
        else -> null
    }

    fun javafx(module: String) {
        if (javafxOS != null && javafxArch != null) {
            val notation = "org.openjfx:javafx-$module:$javafxVersion:${javafxOS}${javafxArch}"

            compileOnly(notation)
            testCompileOnly(notation)
            runtimeOnly(notation)
            testRuntimeOnly(notation)
        }
    }

    javafx("base")
    javafx("controls")
    javafx("graphics")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    modularity.inferModulePath.set(true)
    withSourcesJar()
    withJavadocJar()
}

tasks.withType()

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

val mainClassName = "org.glavo.webp.javafx.WebPViewerApp"

tasks.jar {
    manifest.attributes(
        "Main-Class" to mainClassName
    )
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).also {
        it.jFlags!!.addAll(listOf("-Duser.language=en", "-Duser.country=", "-Duser.variant="))

        it.encoding("UTF-8")
        it.addStringOption("link", "https://docs.oracle.com/en/java/javase/25/docs/api/")
        it.addBooleanOption("html5", true)
        it.addStringOption("Xdoclint:none", "-quiet")

        it.tags!!.addAll(
            listOf(
                "apiNote:a:API Note:",
                "implNote:a:Implementation Note:",
                "implSpec:a:Implementation Specification:",
            )
        )
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the JavaFX WebP viewer."
    dependsOn(tasks.classes)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(mainClassName)
}

// Test

// https://chromium.googlesource.com/webm/libwebp-test-data
val webpTestDataCommit = "53f4c95f055bf3509ceacce7e88894b78287a2f2"
val webpTestDataZip = layout.buildDirectory.file("downloads/libwebp-test-data-${webpTestDataCommit}.zip")

val downloadWebpTestData by tasks.registering(Download::class) {
    src("https://github.com/webmproject/libwebp-test-data/archive/${webpTestDataCommit}.zip")
    dest(webpTestDataZip)
    overwrite(false)
}

tasks.processTestResources {
    dependsOn(downloadWebpTestData)

    into("libwebp-test-data") {
        from(zipTree(webpTestDataZip)) {
            eachFile {
                relativePath = RelativePath(
                    true,
                    *relativePath.segments
                        .filter { it != "libwebp-test-data-$webpTestDataCommit" }
                        .toTypedArray()
                )
            }
            includeEmptyDirs = false
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        csv.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

publishing.publications.create<MavenPublication>("maven") {
    groupId = project.group.toString()
    version = project.version.toString()
    artifactId = project.name

    from(components["java"])

    pom {
        name.set(project.name)
        description.set(project.description)
        url.set("https://github.com/Glavo/jwebp")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }

        developers {
            developer {
                id.set("Glavo")
                name.set("Glavo")
                email.set("zjx001202@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/Glavo/jwebp")
        }
    }
}

if (System.getenv("JITPACK").isNullOrBlank() && rootProject.ext.has("signing.key")) {
    signing {
        useInMemoryPgpKeys(
            rootProject.ext["signing.keyId"].toString(),
            rootProject.ext["signing.key"].toString(),
            rootProject.ext["signing.password"].toString(),
        )
        sign(publishing.publications["maven"])
    }
}

// ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))

            username.set(rootProject.ext["sonatypeUsername"].toString())
            password.set(rootProject.ext["sonatypePassword"].toString())
        }
    }
}
