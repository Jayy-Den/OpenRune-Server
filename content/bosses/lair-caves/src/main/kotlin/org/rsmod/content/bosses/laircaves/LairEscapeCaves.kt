package org.rsmod.content.bosses.laircaves

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The wilderness boss lair escape caves (per osrs wiki: Escape Caves):
 *
 * - The three multi dens (Callisto's Den, Venenatis' Silk Chasm, Vet'ion's Rest) each contain a
 *   cave exit that drops the player into the shared multi Escape Caves dungeon - at one of three
 *   random arrival points. A player leaving through the same lair within 10 seconds of the previous
 *   player arrives at the same exit, so pursuers can follow their target through.
 * - The three singles lairs (Hunter's End, Web Chasm, Skeletal Tomb) exit straight back to their
 *   surface entrance tiles.
 * - On the surface, four rubble mounds around the boss ruins lead into the interior tunnels, and
 *   each interior tunnel leads back out to its mound.
 * - Entering the caves through a surface mound afflicts the player with a prayer-draining curse
 *   (the "escape cave chill"); entering through a boss lair does not.
 *
 * Note: the 50,000-coin entry fee is shared across all lairs and caves; it is charged by the lair
 * entry scripts (spindel today, callisto/venenatis/vetion when implemented), not here.
 */
class LairEscapeCaves
@Inject
constructor(
    private val playerList: PlayerList,
    private val random: GameRandom,
    private val worldClock: MapClock,
) : PluginScript() {

    /** A rubble mound on the surface paired with the interior tunnel it leads to. */
    private data class Tunnel(val mound: CoordGrid, val tunnel: CoordGrid)

    /** A boss-lair exit loc that leads into the shared Escape Caves. */
    private data class LairCaveExit(val loc: String, val label: String)

    private class LairArrival(val arrival: CoordGrid, val cycle: Int)

    private val tunnels =
        listOf(
            // Callisto's Den (multi): four surface mounds west of the ruins, each paired with an
            // interior tunnel mouth inside the escape caves. The cache's exit_noop locs are
            // decorative arrival markers, not usable tunnels.
            Tunnel(CoordGrid(3260, 3832, 0), CoordGrid(3336, 10287, 0)),
            Tunnel(CoordGrid(3282, 3774, 0), CoordGrid(3358, 10244, 0)),
            Tunnel(CoordGrid(3284, 3807, 0), CoordGrid(3361, 10273, 0)),
            Tunnel(CoordGrid(3320, 3830, 0), CoordGrid(3382, 10287, 0)),
        )

    /** Interior tunnel -> surface counterpart, derived from [tunnels]. */
    private val tunnelExits: Map<CoordGrid, CoordGrid> = tunnels.associate { it.tunnel to it.mound }

    private val moundSet: Set<CoordGrid> = tunnels.mapTo(mutableSetOf()) { it.mound }

    /** Random cave arrival points (tunnel mouths) used when entering via a boss-lair exit. */
    private val caveArrivals: List<CoordGrid> = tunnels.map { it.tunnel }

    private val lairCaveExits =
        listOf(
            LairCaveExit("loc.wild_callisto_exit01", "Callisto's Den"),
            LairCaveExit("loc.wild_venanatis_exit", "Venenatis' lair"),
            LairCaveExit("loc.wild_vetion_exit01", "Vet'ion's Rest"),
        )

    /** Singles lair exit tile -> surface entrance tile. Web Chasm's exit is handled by the
     *  spindel module, which already routes it to the surface. */
    private val singlesExits =
        mapOf(
            CoordGrid(1758, 11531, 0) to CoordGrid(3115, 3676, 0), // Hunter's End (Artio)
            CoordGrid(1886, 11534, 1) to CoordGrid(3180, 3683, 0), // Skeletal Tomb (Calvar'ion)
        )

    /** Most recent arrival per lair exit loc, for the 10-second pursuit rule. */
    private val lastArrivals = mutableMapOf<String, LairArrival>()

    /** Players afflicted by the surface-entrance cave chill, mapped to their entry tunnel. */
    private val chilled = mutableMapOf<Player, CoordGrid>()

    override fun ScriptContext.startup() {
        // Surface mounds (down into the caves; two-way in osrs).
        onOpLoc1("loc.wild_cave_exit01") { onSurfaceMound(it.loc) }
        // Interior tunnel mouths (back up to their surface mound).
        onOpLoc1("loc.wild_boss_escape_cave_exit01") { onTunnelMouth(it.loc) }
        onOpLoc1("loc.wild_boss_escape_cave_exit02") { onTunnelMouth(it.loc) }
        onOpLoc1("loc.wild_boss_escape_cave_exit03") { onTunnelMouth(it.loc) }

        // Multi lair exits -> Escape Caves (random arrival, 10s pursuit rule). The exit locs are
        // shared with the singles lairs' copies, so each interaction is resolved by its tile.
        onOpLoc1("loc.wild_callisto_exit01") { onLairExit(it.loc) }
        onOpLoc1("loc.wild_venanatis_exit") { onLairExit(it.loc) }
        onOpLoc1("loc.wild_vetion_exit01") { onLairExit(it.loc) }

        onEvent<GameLifecycle.LateCycle> { drainChill() }
    }

    private suspend fun ProtectedAccess.onLairExit(loc: BoundLocInfo) {
        when (loc.coords) {
            CALLISTO_MULTI_EXIT -> leaveThroughLairCave(lairCaveExits[0])
            VENENATIS_MULTI_EXIT -> leaveThroughLairCave(lairCaveExits[1])
            VETION_MULTI_EXIT -> leaveThroughLairCave(lairCaveExits[2])
            else -> leaveSinglesLair(loc)
        }
    }

    private suspend fun ProtectedAccess.leaveSinglesLair(loc: BoundLocInfo) {
        val surface = singlesExits[loc.coords] ?: return
        arriveDelay()
        telejump(surface, TeleportType.Exempt)
        player.mes("You climb up out of the lair.")
    }

    private suspend fun ProtectedAccess.onSurfaceMound(loc: BoundLocInfo) {
        val tunnel = tunnels.firstOrNull { it.mound == loc.coords } ?: return
        enterCave(tunnel.tunnel)
    }

    private suspend fun ProtectedAccess.onTunnelMouth(loc: BoundLocInfo) {
        val surface = tunnelExits[loc.coords] ?: return
        leaveCave(surface)
    }

    private suspend fun ProtectedAccess.enterCave(tunnel: CoordGrid) {
        arriveDelay()
        telejump(tunnel, TeleportType.Exempt)
        chilled[player] = tunnel
        player.mes("You climb into the caves... a chilling draught saps your strength.")
    }

    private suspend fun ProtectedAccess.leaveCave(surface: CoordGrid) {
        arriveDelay()
        chilled.remove(player)
        telejump(surface, TeleportType.Exempt)
        player.mes("You squeeze through the tunnel and emerge on the surface.")
    }

    private suspend fun ProtectedAccess.leaveThroughLairCave(exit: LairCaveExit) {
        arriveDelay()
        val arrival = resolvePursuitArrival(exit)
        telejump(arrival, TeleportType.Exempt)
        player.mes("You crawl through the tunnel and drop into the escape caves...")
    }

    /**
     * A player leaving through the same lair within 10 seconds of the previous player arrives at
     * the same cave exit; otherwise a random one is picked. Either way, the chosen exit becomes the
     * pursuit anchor for the next 10 seconds.
     */
    private fun ProtectedAccess.resolvePursuitArrival(exit: LairCaveExit): CoordGrid {
        val last = lastArrivals[exit.loc]
        val arrival =
            if (last != null && worldClock.cycle - last.cycle <= PURSUIT_WINDOW_TICKS) {
                last.arrival
            } else {
                caveArrivals[random.of(caveArrivals.size)]
            }
        lastArrivals[exit.loc] = LairArrival(arrival, worldClock.cycle)
        return arrival
    }

    /**
     * The cave chill: players who entered through the surface mounds gradually lose prayer points
     * (and run energy) while they linger in the caves, discouraging pkers from camping the
     * entrance tunnels. Affliction ends as soon as the player leaves the cave footprint (tunnels
     * cover all standing exits; death and logout are caught by the bounds check below).
     */
    private fun drainChill() {
        if (chilled.isEmpty() || worldClock.cycle % CHILL_INTERVAL_TICKS != 0) return
        val exited = mutableListOf<Player>()
        for ((player, _) in chilled) {
            if (!player.online || player.hitpoints <= 0 || player.coords !in caveBounds) {
                exited += player
                continue
            }
            CombatEffects.statDrain(player, listOf("stat.prayer"), CHILL_PRAYER_DRAIN)
            player.runEnergy = (player.runEnergy - CHILL_RUN_DRAIN).coerceAtLeast(0)
        }
        for (player in exited) {
            chilled.remove(player)
        }
    }

    private val Player.online: Boolean
        get() = playerList.any { it === this }

    private companion object {
        private const val PURSUIT_WINDOW_TICKS = 10
        private const val CHILL_INTERVAL_TICKS = 5
        private const val CHILL_PRAYER_DRAIN = 5

        /** Run energy is 0-1000 (per-mille), matching the spindel web drain's scale. */
        private const val CHILL_RUN_DRAIN = 100

        /** Multi den lair-exit tiles (distinct from the singles dens' copies of the same loc ids). */
        private val CALLISTO_MULTI_EXIT = CoordGrid(3358, 10315, 0)
        private val VENENATIS_MULTI_EXIT = CoordGrid(3422, 10183, 2)
        private val VETION_MULTI_EXIT = CoordGrid(3294, 10190, 1)

        /** Escape Caves dungeon footprint (x3328-3391, z10240-10303, ground level). */
        private val caveBounds: Set<CoordGrid> =
            buildSet {
                for (x in 3328..3391) {
                    for (z in 10240..10303) {
                        add(CoordGrid(x, z, 0))
                    }
                }
            }
    }
}
