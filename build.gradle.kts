plugins {
    id("java-library")
}

group = "org.glavo"
version = "1.0-SNAPSHOT"
val mainClassName = "org.glavo.javafx.webp.WebPViewerApp"

val osName = System.getProperty("os.name").lowercase()
val javafxPlatform = when {
    osName.contains("win") -> "win"
    osName.contains("mac") -> "mac"
    else -> "linux"
}
val javafxVersion = "25.0.2"

repositories {
    mavenCentral()
}

dependencies {
    api("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    api("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")
    api("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.13.1")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    modularity.inferModulePath.set(true)
    withSourcesJar()
}

val mainSourceSet = sourceSets.named("main")

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = mainClassName
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the JavaFX WebP viewer."
    dependsOn(tasks.classes)
    classpath = mainSourceSet.get().runtimeClasspath
    mainClass.set(mainClassName)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}
