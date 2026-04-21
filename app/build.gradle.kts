plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
	id("com.gradleup.shadow") version "9.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)
    implementation("org.xerial:sqlite-jdbc:3.44.0.0")
    implementation("org.slf4j:slf4j-api:1.7.36")
}

application {
    // Define the main class for the application.
    mainClass = "org.example.App"
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}