package org.rsmod.content.bosses.vardorvis

import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onPlayerInit
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class VardorvisInstance @Inject constructor(registry: BossInstanceRegistry) :
    InstanceScript(registry) {

    private var Player.stranglewoodProgress by intVarBit(STRANGLEWOOD_VARBIT)

    override fun settingsRow(): String = "dbrow.instance_vardorvis"

    override fun area(): InstanceArea = INSTANCE

    override fun ScriptContext.configure() {
        onPlayerInit {
            if (player.stranglewoodProgress < STRANGLEWOOD_UNLOCKED) {
                player.stranglewoodProgress = STRANGLEWOOD_UNLOCKED
            }
        }

        onEnterObject { defaultInstanceEntry() }
        onExitObject { defaultLeaveFlow() }
    }

    private companion object {
        private const val STRANGLEWOOD_VARBIT = "varbit.dt2_stranglewood"

        private const val STRANGLEWOOD_UNLOCKED = 37

        private const val ARENA_REGION = 4405

        private val INSTANCE =
            InstanceArea.copyRegions(
                regionIds = listOf(ARENA_REGION),
                npcSpawns = listOf(InstanceNpc("npc.vardorvis", CoordGrid(1128, 3417, 0))),
            )
    }
}
