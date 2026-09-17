package org.rsmod.plugin.loader

import com.google.inject.Guice
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.streams.asSequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Drives [ExternalPluginLoader]'s boot, hot-load and unload paths against a fixture copy of the
 * example plugin (see `example-plugin/`), using a directory source built from the plugin's jar
 * (its path is passed from the build via the `example.plugin.jar` system property).
 *
 * The loader resolves `plugins/` against the process working directory and caches it on first
 * use, so the test task pins the working directory to a scratch folder (`build/test-working`,
 * see the build script) and every test rebuilds `plugins/` from scratch. The class is
 * a result, the class must run in isolation from other tests in this module (the loader object
 * also keeps state across tests).
 */
// Runs same-thread (see the build script): the loader resolves `plugins/` from the process
// working directory, so parallel tests would race on the same directory.
class ExternalPluginLoaderIntegrationTest {

    private val pluginsDir: Path = Paths.get("plugins")

    // Unique per test: the loader object caches its enabled/disabled state for the whole JVM, so
    // a fresh source name keeps tests independent without a production reset hook.
    private lateinit var sourceId: String

    @BeforeEach
    fun resetPluginsDir() {
        pluginsDir.toFile().deleteRecursively()
        pluginsDir.toFile().mkdirs()
        sourceId = "example-plugin-" + System.nanoTime()
        installExamplePluginFixture()
    }

    @AfterEach
    fun releaseClassLoaders() {
        ExternalPluginLoader.releaseAllClassLoaders()
    }

    @Test
    fun `boot path discovers fixture, starts its script and registers its command`() {
        val statuses = ExternalPluginLoader.listStatuses()
        check(statuses.size == 1) { "Expected only the fixture source, got: $statuses" }
        val status = statuses.single()
        check(status.id == sourceId) { "Unexpected source id: $status" }
        check(status.manifest != null) { "Manifest not parsed: $status" }
        check(status.enabled) { "Fresh source should default to enabled: $status" }
        check(!status.loaded) { "Source must not be marked loaded before boot: $status" }

        val modules = ExternalPluginLoader.loadModulesAtBoot()
        check(modules.isEmpty()) { "Example plugin declares no PluginModule: $modules" }

        val injector = Guice.createInjector()
        val scripts = ExternalPluginLoader.loadScriptsAtBoot(injector)
        check(scripts.size == 1) { "Expected one started script, got: $scripts" }

        val context = testContext()
        with(scripts.single()) { context.startup() }
        check(context.cheatCommandMap["example"] != null) {
            "startup() did not register the ::example command"
        }
        check(ExternalPluginLoader.listStatuses().single().loaded) { "Status not updated after boot" }
    }

    @Test
    fun `hot load runs startup and registers commands like boot does`() {
        val injector = Guice.createInjector()
        val context = testContext()
        val scripts = ExternalPluginLoader.load(sourceId, injector, context)
        check(scripts != null) { "Hot load of a valid enabled source must succeed" }
        check(scripts.orEmpty().isNotEmpty()) { "Hot load produced no scripts" }
        check(context.cheatCommandMap["example"] != null) { "::example not registered after hot load" }
    }

    @Test
    fun `reload replaces the running plugin without refusing`() {
        val injector = Guice.createInjector()
        val context = testContext()
        checkNotNull(ExternalPluginLoader.load(sourceId, injector, context))
        check(context.cheatCommandMap["example"] != null)

        // Bump the manifest revision on disk (directory source), then reload over the old load.
        val manifest = pluginsDir.resolve("$sourceId/plugin.properties")
        val updated = Files.readAllLines(manifest).joinToString(System.lineSeparator()) { line ->
            if (line.startsWith("revision=")) "revision=2" else line
        }
        Files.write(manifest, updated.toByteArray())

        val reloaded = ExternalPluginLoader.load(sourceId, injector, context)
        check(reloaded != null) { "Reload must treat an already-loaded source as a reload" }
        check(reloaded.orEmpty().isNotEmpty()) { "Reload produced no scripts" }
    }

    @Test
    fun `source without a manifest is listed but refused`() {
        val noManifest = pluginsDir.resolve("broken-plugin-" + System.nanoTime())
        noManifest.toFile().mkdirs()
        noManifest.resolve("plugin.properties").toFile().writeText("name=Broken\n")

        val statuses = ExternalPluginLoader.listStatuses()
        val broken = statuses.single { it.manifest == null }
        check(broken.manifest == null) { "Incomplete manifest must surface as null: $broken" }

        val injector = Guice.createInjector()
        check(ExternalPluginLoader.load("broken-plugin", injector, testContext()) == null) {
            "load() must refuse a source with an incomplete manifest"
        }
    }

    @Test
    fun `disabled source is refused and enable state persists`() {
        ExternalPluginLoader.setEnabled(sourceId, false)
        val injector = Guice.createInjector()
        val context = testContext()
        val refused = ExternalPluginLoader.load(sourceId, injector, context)
        check(refused == null) { "load() must refuse a disabled source" }
        check(!ExternalPluginLoader.listStatuses().single { it.manifest != null }.enabled) { "Disable not reflected" }

        ExternalPluginLoader.setEnabled(sourceId, true)
        check(ExternalPluginLoader.load(sourceId, injector, context) != null) {
            "Re-enabled source must load again"
        }
    }

    private fun testContext() =
        ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())

    /**
     * Unpacks the example plugin jar into `plugins/<sourceId>/` (a directory source, as
     * documented in `docs/external-plugins.md`) and writes a valid manifest for it.
     */
    private fun installExamplePluginFixture() {
        val location =
            Paths.get(checkNotNull(System.getProperty("example.plugin.jar")))

        val fixture = pluginsDir.resolve(sourceId).toFile()
        fixture.mkdirs()
        if (location.toFile().isFile) {
            // A jar code source: unpack it into the directory source (a nested jar wouldn't be
            // scanned). A directory code source is copied as-is.
            java.util.zip.ZipFile(location.toFile()).use { zip ->
                for (entry in zip.entries().asSequence()) {
                    if (entry.isDirectory) continue
                    val target = fixture.toPath().resolve(entry.name)
                    Files.createDirectories(target.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        } else {
            Files.walk(location).use { paths ->
                for (source in paths.filter(Files::isRegularFile).asSequence()) {
                    val target =
                        fixture.toPath().resolve(location.relativize(source).toString())
                    Files.createDirectories(target.parent)
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        fixture.resolve("plugin.properties").writeText(
            """
            name=Example Plugin
            description=Test fixture copy of the example plugin
            revision=1
            author=ExternalPluginLoaderIntegrationTest
            """.trimIndent(),
        )
    }
}
