package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MorganCupboardTest {
    /** Upstairs in Morgan's house in Draynor Village. */
    private val cupboard = CoordGrid(3096, 3269, 1)

    @Test
    fun cupboardIsMappedOnceUpstairsAndOnlyInItsClosedForm() {
        val cache = ServerCacheManager.init(240)
        val closed = MorganCupboard.Closed.asRSCM()
        val open = MorganCupboard.Open.asRSCM()
        val tiles = mutableSetOf<CoordGrid>()
        var openFormMapped = false
        try {
            for (x in 45..52) {
                for (z in 48..54) {
                    val square = MapSquareKey(x, z)
                    val data = cache.data(MAPS, square.id, 1) ?: continue
                    val map = MapLocListDecoder.decode(InlineByteBuf(data))
                    for (spawn in map.spawns) {
                        val def = MapLocDefinition(spawn)
                        val at = square.toCoords(def.level).translate(def.localX, def.localZ)
                        when (def.id) {
                            closed -> tiles += at
                            open -> openFormMapped = true
                        }
                    }
                }
            }
        } finally {
            cache.close()
        }

        assertEquals(setOf(cupboard), tiles)
        assertEquals(
            false,
            openFormMapped,
            "the open cupboard must not be mapped: opening spawns it over the closed map loc",
        )
    }

    @Test
    fun opsMatchTheBoundHandlers() {
        val cache = ServerCacheManager.init(240)
        val closedOps: List<String?>
        val openOps: List<String?>
        try {
            closedOps = opsOf(MorganCupboard.Closed)
            openOps = opsOf(MorganCupboard.Open)
        } finally {
            cache.close()
        }

        assertEquals(listOf<String?>("Open", null, null, null, null), closedOps)
        assertEquals(listOf("Search", "Close", null, null, null), openOps)
        assertEquals(cupboard.level, 1)
    }

    private fun opsOf(internal: String): List<String?> {
        val type = checkNotNull(ServerCacheManager.getObject(internal.asRSCM()))
        return (0 until Ops).map { type.actions.getOpOrNull(it) }
    }

    private companion object {
        private const val Ops = 5
    }
}
