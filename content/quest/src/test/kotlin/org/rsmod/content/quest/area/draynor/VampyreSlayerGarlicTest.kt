package org.rsmod.content.quest.area.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

/**
 * Garlic drains the Count's Attack by [VampyreSlayer.GARLIC_ATTACK_DRAIN] and reapplies once those
 * levels are back, so its cadence is the drain size against the regeneration the cache gives him.
 * A point costs `regenRate + 1` cycles - see `NpcRegenProcessorTest` - so the config value is one
 * less than the period the wiki and the capture describe.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class VampyreSlayerGarlicTest {
    @Test
    fun `the count regenerates a point every five cycles`() {
        assertEquals(WikiRegenPeriod, cachedRegenPeriod)
        assertEquals(WikiRegenPeriod - 1, cachedRegenRate)
    }

    @Test
    fun `garlic reapplies on the captured cadence`() {
        val cadence = VampyreSlayer.GARLIC_ATTACK_DRAIN * cachedRegenPeriod
        assertTrue(cadence in CapturedCadence) {
            "garlic reapplies every $cadence cycles; the capture shows it every $CapturedCadence"
        }
    }

    @Test
    fun `garlicDefenceRecoversOnTheWikiCadence`() {
        val drain = minOf(VampyreSlayer.GARLIC_DEFENCE_DRAIN, cachedDefence)
        assertEquals(150, drain * cachedRegenPeriod) {
            "defence drained to zero takes $drain levels at $cachedRegenPeriod cycles each"
        }
    }

    @Test
    fun `garlicDefenceDrainFloorsTheCountsDefence`() {
        assertTrue(cachedDefence < VampyreSlayer.GARLIC_DEFENCE_DRAIN) {
            "Count defence $cachedDefence survives the garlic drain"
        }
    }

    private val cachedRegenPeriod: Int
        get() = cachedRegenRate + 1

    private val cachedRegenRate: Int
        get() {
            val cache = ServerCacheManager.init(240)
            return try {
                npc().regenRate
            } finally {
                cache.close()
            }
        }

    private fun npc() = checkNotNull(ServerCacheManager.getNpc("npc.count_draynor".asRSCM(RSCMType.NPC)))

    private val cachedDefence: Int
        get() {
            val cache = ServerCacheManager.init(240)
            return try {
                npc().defence
            } finally {
                cache.close()
            }
        }

    private companion object {
        /** Message spacing in the Vampyre Slayer capture: 8139, 8185 and 8239. */
        private val CapturedCadence = 45..55

        /** The wiki's "one point per five ticks", against the usual one per hundred. */
        private const val WikiRegenPeriod = 5
    }
}
