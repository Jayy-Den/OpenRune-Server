package org.rsmod.content.bosses.cowboss

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.instances.enterLocObjects
import org.rsmod.api.instances.exitLocObjects
import org.rsmod.api.instances.withInstanceEnterTransition
import org.rsmod.api.instances.withInstanceLeaveTransition
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class CowbossInstance
@Inject
constructor(
    registry: BossInstanceRegistry,
) : InstanceScript(registry) {

    override fun settingsRow(): String = "dbrow.instance_cowboss"

    override fun area(): InstanceArea = PRIVATE_AREA

    override fun ScriptContext.configure() {
        val row = settingsRowData()
        onEnterPrelude { _, enter ->
            withInstanceEnterTransition(InstanceEnterTransition(), enter)
        }

        row.enterLocObjects().forEach { loc ->
            onOpLoc2(loc) { defaultInstanceEntry() }
        }
        row.exitLocObjects().forEach { loc ->
            onOpLoc2(loc) { quickEscape() }
            onOpLoc3(loc) { peekPublicRoom() }
        }
    }

    private suspend fun ProtectedAccess.quickEscape() {
        withInstanceLeaveTransition {
            defaultLeaveFlow()
        }
    }

    private fun ProtectedAccess.peekPublicRoom() {
        val session = manager.sessionsForKey(key).firstOrNull { it.isServerOwned }
        val count = session?.occupants?.size ?: 0
        if (count == 0) {
            mes("The public cow pen is currently empty.")
        } else {
            mes(
                "There ${if (count == 1) "is" else "are"} $count player${if (count == 1) "" else "s"} in the public cow pen.",
            )
        }
    }

    private companion object {

        private fun seq(id: Int): String =
            RSCM.getReverseMapping(RSCMType.SEQ, id) ?: error("Missing seq id: $id")

        /** Lumbridge cow field region. */
        private const val COW_FIELD_REGION = 12851

        /**
         * Capture-verified spawn, expressed in **static map** coordinates.
         *
         * Instance coordinates are static map coordinates - the engine maps them onto the copied
         * region through `Region.normal` - so the capture's instance-space position (11933, 4535)
         * has to be translated back into the Lumbridge cow pen. In the capture Brutus stands one
         * tile west and seven tiles north of the player's entry tile, and the entry tile is the
         * start-gate coord from the DB row, (3258, 3292).
         */
        private const val BRUTUS_SPAWN_X = 3257

        /** See [BRUTUS_SPAWN_X]. */
        private const val BRUTUS_SPAWN_Z = 3285

        private val BRUTUS_SPAWN = CoordGrid(BRUTUS_SPAWN_X, BRUTUS_SPAWN_Z)

        /** Inside the cow pen, on the release gate's start tile. */
        private val PEN_ENTER = RegionLocal(0, 50, 51, 58, 28)

        /** Where players land when they leave or die in the instance. */
        private val PEN_EXIT = CoordGrid(3258, 3289, 0)

        private val PUBLIC_AREA =
            InstanceArea.Companion.copyRegions(
                centerRegionId = COW_FIELD_REGION,
                gridSize = 1,
                enterCoord = PEN_ENTER,
                exitCoord = PEN_EXIT,
                npcSpawns = listOf(InstanceNpc("npc.cowboss", BRUTUS_SPAWN)),
            )

        private val PRIVATE_AREA =
            InstanceArea.Companion.copyRegions(
                centerRegionId = COW_FIELD_REGION,
                gridSize = 1,
                enterCoord = PEN_ENTER,
                exitCoord = PEN_EXIT,
                npcSpawns = listOf(InstanceNpc("npc.cowboss", BRUTUS_SPAWN)),
            )
    }
}
