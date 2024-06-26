plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("java-gradle-plugin")
    id("io.gitlab.arturbosch.detekt")
    id("com.automattic.android.publish-to-s3")
    id("com.gradle.plugin-publish")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation("com.gradle:gradle-enterprise-gradle-plugin:3.15.1")

    implementation("io.ktor:ktor-client-core:1.6.4")
    implementation("io.ktor:ktor-client-cio:1.6.4")
    implementation("io.ktor:ktor-client-logging:1.6.4")
    implementation("io.ktor:ktor-client-serialization:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleTestKit())
    testImplementation("org.assertj:assertj-core:3.24.2")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.3")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/detekt.yml")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

fun getVersionFromGitTag(): String {
    val process = ProcessBuilder("git", "describe", "--abbrev=0", "--tags").start()
    return process.inputStream.bufferedReader().readText().trim()
}
// The project version will be used as plugin version in Gradle Plugin Portal
version = getVersionFromGitTag()
group = "com.automattic.android"

gradlePlugin {
    website.set("https://github.com/Automattic/measure-builds-gradle-plugin")
    vcsUrl.set("https://github.com/Automattic/measure-builds-gradle-plugin")
    plugins.register("measure-builds") {
        id = "com.automattic.android.measure-builds"
        displayName = "Measure builds"
        description = "Gradle plugin to measure and report build times and insights."
        tags.set(listOf("build", "metrics", "reports", "buildscan"))
        implementationClass = "com.automattic.android.measure.BuildTimePlugin"
    }
}

tasks.create("setupPluginUploadFromEnvironment") {
    doLast {
        val key = System.getenv("GRADLE_PUBLISH_KEY")
        val secret = System.getenv("GRADLE_PUBLISH_SECRET")

        if (key == null || secret == null) {
            throw GradleException("gradlePublishKey and/or gradlePublishSecret are not defined environment variables")
        }

        System.setProperty("gradle.publish.key", key)
        System.setProperty("gradle.publish.secret", secret)
    }
}

tasks.register("preMerge") {
    description = "Runs all the verification tasks."

    dependsOn(":check")
    dependsOn(":validatePlugins")
}
