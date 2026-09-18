package org.rsmod.content.areas.city.draynor

import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The cupboard upstairs in Morgan's house. Searching it hands out garlic - the supply Vampyre
 * Slayer points the player at - so it is deliberately not gated on the quest: collecting garlic
 * here is a money making method in its own right.
 */
class MorganCupboard @Inject constructor(private val locRepo: LocRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(Closed) { openCupboard(it.loc) }
        onOpLoc1(Open) { searchCupboard() }
        onOpLoc2(Open) { closeCupboard(it.loc) }
    }

    private suspend fun ProtectedAccess.openCupboard(loc: BoundLocInfo) {
        arriveDelay()
        faceLoc(loc)
        soundSynth(OpenSound)
        player.attr[SEARCHED] = false
        locRepo.change(loc, Open, OpenTicks)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()
        if (invAdd(inv, Garlic, 1).failure) {
            mes(NoSpace)
            return
        }
        val searched = player.attr.getOrDefault(SEARCHED, false)
        mes(if (searched) TakeClove else ContainsGarlic)
        player.attr[SEARCHED] = true
    }

    private suspend fun ProtectedAccess.closeCupboard(loc: BoundLocInfo) {
        arriveDelay()
        locRepo.del(loc, Int.MAX_VALUE)
    }

    internal companion object {
        internal const val Closed = "loc.garliccupboardshut"
        internal const val Open = "loc.garliccupboardopen"

        private const val Garlic = "obj.garlic"
        private const val OpenSound = "synth.cupboard_open"

        /** How long the cupboard stays open before it swings shut again on its own. */
        internal const val OpenTicks = 50

        private const val ContainsGarlic = "The cupboard contains garlic. You take a clove."
        private const val TakeClove = "You take a clove of garlic."
        private const val NoSpace = "You don't have any inventory space."

        private val SEARCHED = AttributeKey<Boolean>(temp = true)
    }
}
