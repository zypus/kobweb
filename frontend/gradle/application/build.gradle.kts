import org.jetbrains.kotlin.cli.jvm.main

plugins {
    `kotlin-dsl`
    kotlin("jvm")
    id("com.varabyte.kobweb.internal.publish")
    `java-library`
    `java-gradle-plugin`
}

group = "com.varabyte.kobweb.gradle"
version = libs.versions.kobweb.get()

dependencies {
    implementation(kotlin("stdlib"))
    // Get access to Kotlin multiplatform source sets
    implementation(kotlin("gradle-plugin"))
    // For parsing code. Instead, use KSP someday? See also: Bug #4
    implementation(kotlin("compiler-embeddable"))

    // Note: These are compileOnly because we embed the classes directly into this plugin by modifying the jar class
    // below. We do this so that we don't have to publish these internal artifacts in our maven repository - they are
    // implementation details and shouldn't leak as public artifacts.
    compileOnly(project(":common:kobweb"))
    compileOnly(project(":backend:api"))
    // We need to add this dependency manually  so that the above two dependencies will work, since we don't add their
    // transitive dependencies into our jar (partly because that's complicated, we'd have to filter out kotlin
    // dependencies, and partly because I'm not a Gradle expert, so it's possible there's a better way).
    runtimeOnly(libs.kaml)
}
tasks.withType<Jar> {
    from(configurations.compileClasspath.get()
        .filter { it.isFile && it.absolutePath.startsWith(project.rootProject.projectDir.absolutePath) }
        .map { zipTree(it) })
}

val DESCRIPTION = "A Gradle plugin that completes a user's Kobweb app"
gradlePlugin {
    plugins {
        create("kobwebApplication") {
            id = "com.varabyte.kobweb.application"
            displayName = "Kobweb Application Plugin"
            description = DESCRIPTION
            implementationClass = "com.varabyte.kobweb.gradle.application.KobwebApplicationPlugin"
        }
    }
}

kobwebPublication {
    // Leave artifactId blank. It will be set to the name of this module, and then the gradlePlugin step does some
    // additional tweaking that we don't want to interfere with.
    description.set(DESCRIPTION)
}

/**
 * Embed a copy of the latest Kobweb server, naming it server.jar and putting it into the project's resources/ dir, so
 * we can run it from the plugin at runtime.
 */
tasks.register<Copy>("copyServerJar") {
    dependsOn(":backend:server:shadowJar")

    val serverJarName = "server-${libs.versions.kobweb.get()}-all.jar"
    val serverJarFile = file("${project(":backend:server").buildDir}/libs/$serverJarName")

    if (!serverJarFile.exists()) {
        throw GradleException("Could not find server fat jar at $serverJarFile")
    }

    from(file(serverJarFile))
    into(file("$projectDir/build/resources/main"))
    rename(serverJarName, "server.jar")
}
project.tasks.named("processResources") {
    dependsOn("copyServerJar")
}