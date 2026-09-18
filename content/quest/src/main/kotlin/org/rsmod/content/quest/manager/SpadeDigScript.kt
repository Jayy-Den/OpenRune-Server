package org.rsmod.content.quest.manager

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Routes spade "Dig" ops to every registered quest dig site.
 *
 * The event bus allows only one handler per key, so quest plugins must not bind
 * `onOpHeld1("obj.spade")` themselves; they register a [DigSite] here instead and the
 * dispatcher runs the first matching site after its own guards pass.
 */
@Singleton
public class SpadeDigScript @Inject constructor() : PluginScript() {
    private val sites = mutableListOf<RegisteredDigSite>()

    public fun register(site: DigSite) {
        sites += RegisteredDigSite(site, null)
    }

    public fun registerQuestSite(site: DigSite, quest: Quest) {
        sites += RegisteredDigSite(site, quest)
    }

    override fun ScriptContext.startup() {
        onOpHeld1("obj.spade") { dig() }
    }

    private suspend fun ProtectedAccess.dig() {
        if ("obj.spade" !in inv) {
            return
        }
        val snapshot = sites.toList()
        for (site in snapshot) {
            val quest = site.quest
            if (quest != null && quest.isQuestCompleted(player)) {
                continue
            }
            if (site.site.matches(this)) {
                site.site.dig(this)
                return
            }
        }
    }

    private data class RegisteredDigSite(val site: DigSite, val quest: Quest?)

    public class DigSite(
        public val matches: ProtectedAccess.() -> Boolean,
        public val dig: suspend ProtectedAccess.() -> Unit,
    )
}
