plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

val examplePluginJar =
    project(":example-plugin").layout.buildDirectory.file("libs/example-plugin.jar")

dependencies {
    api(libs.classgraph)
    api(libs.guice)
    implementation(libs.kotlin.inline.logger)
    runtimeOnly(libs.logback.classic)
    implementation(projects.engine.events)
    implementation(projects.engine.game)

    // Mirrors what the real server classpath provides to an external plugin's parent classloader
    // (the example plugin uses the onCommand DSL from api.cheat). Test-only so the engine module
    // itself keeps its layering.
    testImplementation(projects.api.pluginCommons)
}

tasks.withType<Test>().configureEach {
    dependsOn(":example-plugin:jar")

    // ExternalPluginLoader resolves `plugins/` against the process working directory and caches
    // it on first use; keep the test run out of the real repo root's plugins/ directory.
    val testWorkingDir = layout.buildDirectory.dir("test-working").get().asFile
    testWorkingDir.mkdirs()
    workingDir(testWorkingDir)
    // ExternalPluginLoaderIntegrationTest shares one `plugins/` working directory across tests,
    // so this module's tests run same-thread rather than in parallel.
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    systemProperty("example.plugin.jar", examplePluginJar.get().asFile.absolutePath)
    inputs.file(examplePluginJar)
}
