package org.rsmod.api.game.process.npc

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

/**
 * Content configures [NpcServerType.regenRate] as if it were the number of cycles between stat
 * points, but the processor refills its clock on the tick it regenerates and then counts that
 * clock down to zero, so a point actually costs `regenRate + 1` cycles. These tests exist so that
 * changing the processor (or a config derived from it) fails loudly.
 */
class NpcRegenProcessorTest {
    private val processor = NpcRegenProcessor()

    @Test
    fun `a stat point costs regenRate plus one cycles`() {
        val npc = drained(regenRate = 4)
        processor.process(npc)
        assertEquals(1, npc.attackLvl, "the first tick regenerates: the clock starts at zero")

        var cycles = 0
        while (npc.attackLvl == 1) {
            processor.process(npc)
            cycles++
        }

        assertEquals(5, cycles)
    }

    @Test
    fun `the period scales with regenRate`() {
        assertEquals(101, cyclesPerPoint(regenRate = 100))
        assertEquals(5, cyclesPerPoint(regenRate = 4))
    }

    @Test
    fun `a regenRate of zero disables regeneration`() {
        val npc = drained(regenRate = 0)
        repeat(100) { processor.process(npc) }
        assertEquals(0, npc.attackLvl)
    }

    @Test
    fun `every stat comes back on the same clock`() {
        val npc = drained(regenRate = 4)
        npc.defenceLvl = 0
        npc.hitpoints = 1
        processor.process(npc)

        assertEquals(1, npc.attackLvl)
        assertEquals(1, npc.defenceLvl)
        assertEquals(2, npc.hitpoints)
    }

    private fun cyclesPerPoint(regenRate: Int): Int {
        val npc = drained(regenRate)
        processor.process(npc)
        var cycles = 0
        while (npc.attackLvl == 1) {
            processor.process(npc)
            cycles++
        }
        return cycles
    }

    private fun drained(regenRate: Int): Npc {
        val type = NpcServerType().apply {
            this.regenRate = regenRate
            attack = 50
            defence = 50
            hitpoints = 50
        }
        return Npc(type, CoordGrid(3222, 3218)).apply { attackLvl = 0 }
    }
}
