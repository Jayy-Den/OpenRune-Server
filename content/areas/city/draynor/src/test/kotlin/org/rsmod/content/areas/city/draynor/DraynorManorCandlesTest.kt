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
class DraynorManorCandlesTest {
    @Test
    fun candleTilesMatchTheManorCandleScenery() {
        val cache = ServerCacheManager.init(240)
        val sceneryTiles = try {
            val square = MapSquareKey.from(CoordGrid(SampleX, SampleZ))
            val map =
                MapLocListDecoder.decode(
                    InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 1))),
                )
            val candleIds =
                setOf("loc.draynor_candles_1".asRSCM(), "loc.draynor_candles_2".asRSCM())
            map.spawns
                .map(::MapLocDefinition)
                .filter { it.id in candleIds }
                .map { square.toCoords(it.level).translate(it.localX, it.localZ) }
                .toSet()
        } finally {
            cache.close()
        }

        assertEquals(sceneryTiles, DraynorManorCandles.CandleTiles)
    }

    private companion object {
        private const val SampleX = 3077
        private const val SampleZ = 9772
    }
}
