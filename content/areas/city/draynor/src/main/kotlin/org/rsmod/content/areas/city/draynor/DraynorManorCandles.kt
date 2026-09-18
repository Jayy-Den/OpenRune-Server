package org.rsmod.content.areas.city.draynor

import jakarta.inject.Inject
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.events.PlayerTimerEvent
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The candles lying on the floor of Draynor Manor's basement. Standing on one burns the player's
 * feet every [BurnInterval] cycles: a random cry plus
 * "The candles on the floor burn your feet!". No hitpoints are lost, matching both the wiki and
 * the real-OSRS capture, where consecutive burns land exactly 8 cycles apart (ticks 8040, 8048,
 * 8056, ... in `20260816T164303`) with no hitsplat against the player's health.
 */
class DraynorManorCandles
@Inject
constructor(private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerSoftTimer(BurnTimer) { burnTick() }
        onEvent<PlayerMovementEvent.CoordsMovedEvent> { onMoved() }
    }

    private fun PlayerTimerEvent.Soft.burnTick() {
        if (!player.isOnCandles()) {
            player.clearSoftTimer(BurnTimer)
            return
        }
        player.burn()
    }

    private fun PlayerMovementEvent.CoordsMovedEvent.onMoved() {
        if (!player.isOnCandles()) {
            player.clearSoftTimer(BurnTimer)
            return
        }
        if (player.softTimerMap.contains(BurnTimer)) {
            return
        }
        player.softTimer(BurnTimer, BurnInterval)
        player.burn()
    }

    private fun Player.isOnCandles(): Boolean = CandleTiles.contains(coords)

    private fun Player.burn() {
        mes(BurnMessage)
        say(Cries[random.of(Cries.indices)])
        queueHit(
            delay = 1,
            type = HitType.Typeless,
            damage = BurnDamage,
            modifier = NoopPlayerHitModifier,
            hitmark = hitmark_groups.zero_damage,
        )
    }

    companion object {
        private const val BurnTimer = "timer.draynor_candles"
        private const val BurnInterval = 8
        private const val BurnDamage = 0

        private const val BurnMessage = "The candles on the floor burn your feet!"

        private val Cries = arrayOf("Eeek!", "Gah!", "Oooch!", "Ow!")

        /**
         * Tiles occupied by `loc.draynor_candles_1` (11472) and `loc.draynor_candles_2` (11473) in
         * the manor's lower level, next to Count Draynor's coffin. Asserted against the map data by
         * [DraynorManorCandlesTest].
         */
        internal val CandleTiles = setOf(
            CoordGrid(3075, 9772, 0),
            CoordGrid(3075, 9777, 0),
            CoordGrid(3075, 9778, 0),
            CoordGrid(3076, 9771, 0),
            CoordGrid(3076, 9772, 0),
            CoordGrid(3076, 9773, 0),
            CoordGrid(3076, 9778, 0),
            CoordGrid(3077, 9772, 0),
            CoordGrid(3078, 9772, 0),
            CoordGrid(3079, 9771, 0),
            CoordGrid(3079, 9772, 0),
            CoordGrid(3079, 9773, 0),
            CoordGrid(3079, 9778, 0),
            CoordGrid(3080, 9772, 0),
            CoordGrid(3080, 9777, 0),
            CoordGrid(3080, 9778, 0),
        )
    }
}
