package org.rsmod.content.bosses.callisto

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.runtime.repeatTick
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.hit.PlayerAbsorption
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.ScriptContext

data class BearLairConfig(
    val key: String,
    val bossNpc: String,
    val level: Int,
    val minX: Int,
    val maxX: Int,
    val minZ: Int,
    val maxZ: Int,
    val bossSpawn: CoordGrid,
    val meleeMinHit: Int,
    val meleeMaxHit: Int,
    val rangedMaxHit: Int,
    val trapCount: Int,
    val trapRefreshCount: Int,
) {
    fun contains(coord: CoordGrid, margin: Int = 0): Boolean =
        coord.level == level &&
            coord.x in (minX - margin)..(maxX + margin) &&
            coord.z in (minZ - margin)..(maxZ + margin)
}

/** Multi-way Callisto's Den (level 40 wilderness, escape-cave exit at 3358,10315). */
val CALLISTO_LAIR =
    BearLairConfig(
        key = "callisto",
        bossNpc = "npc.callisto",
        level = 0,
        minX = 3341,
        maxX = 3377,
        minZ = 10308,
        maxZ = 10347,
        bossSpawn = CoordGrid(3359, 10324, 0),
        meleeMinHit = 35,
        meleeMaxHit = 55,
        rangedMaxHit = 31,
        trapCount = 24,
        trapRefreshCount = 6,
    )

/** Singles-plus Hunter's End (level 21 wilderness, exact 1600/-1216 tile copy of the den). */
val ARTIO_LAIR =
    BearLairConfig(
        key = "artio",
        bossNpc = "npc.callisto_singles",
        level = 0,
        minX = 1741,
        maxX = 1777,
        minZ = 11524,
        maxZ = 11563,
        bossSpawn = CoordGrid(1759, 11544, 0),
        meleeMinHit = 15,
        meleeMaxHit = 35,
        rangedMaxHit = 17,
        trapCount = 16,
        trapRefreshCount = 4,
    )

private object BearTuning {
    const val CALLISTO_ATTACK_RATE = 4
    const val ARTIO_ATTACK_RATE = 5
    const val AGGRO_RADIUS = 10
    const val MELEE_SPLASH_RADIUS = 1
    const val MELEE_PRAYER_PIERCE_REDUCTION = 50

    const val ARENA_ATTACK_RADIUS = 15
    const val RANGED_IMPACT_HEIGHT = 30
    val RANGED_PROJECTILE_CONFIG =
        ProjectileConfig(
            startHeight = 150,
            endHeight = 90,
            startDelay = 25,
            travelTime = 0,
            angle = 14,
            progress = 48,
            stepMultiplier = 5,
        )

    const val ENRAGE_HP_FIRST = 0.66
    const val ENRAGE_HP_SECOND = 0.33
    const val ENRAGE_STAGE_FULL = 2

    const val SHOCKWAVE_ATTACK_INTERVAL = 5
    const val SHOCK_SPLASH_RADIUS = 1
    const val SHOCK_KNOCKBACK_TILES = 3
    const val SHOCK_BASE_DAMAGE = 5
    const val SHOCK_DAMAGE_PER_BLOCKED_TILE = 15
    const val SHOCK_STUN_TICKS = 4
    const val SHOCK_PROJ_DELAY = 20
    const val SHOCK_PROJ_TRAVEL = 100
    const val SHOCK_PROJ_ANGLE = 14
    const val SHOCK_START_HEIGHT = 60
    const val SHOCK_END_HEIGHT = 30
    const val SHOCK_IMPACT_TICKS = 3
    const val SHOCK_MOVE_DELAY1 = 0
    const val SHOCK_MOVE_DELAY2 = 20

    const val TRAP_SCATTER_RADIUS = 14
    const val TRAP_MIN_BOSS_DISTANCE = 3
    const val TRAP_DURATION_TICKS = 100
    const val TRAP_TICKER_WINDOW = 1000
    const val TRAP_TRIGGER_OUT_OF = 3
    const val TRAP_BIND_TICKS = 10
    const val TRAP_DAMAGE_MIN = 5
    const val TRAP_DAMAGE_MAX = 15
    const val TRAP_REFRESH_INTERVAL = 4
}

class Callisto
@Inject
constructor(deps: BossDeps, locRepo: LocRepository) : BossPluginScript(deps) {

    private val helpers = BearEncounter(deps, locRepo)

    private val bearNpcIds: Set<Int> by lazy {
        setOf(
            CALLISTO_LAIR.bossNpc.asRSCM(RSCMType.NPC),
            ARTIO_LAIR.bossNpc.asRSCM(RSCMType.NPC),
        )
    }

    override val spec = bearSpec(CALLISTO_LAIR, attackRate = BearTuning.CALLISTO_ATTACK_RATE)

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        helpers.registerExternals()
        // The packed cache spawns both bears on their surface-entrance tiles; move them into
        // their dens before players ever see them, and reset per-fight state on respawn.
        for (typeId in bearNpcIds) {
            onEvent<NpcStateEvents.Spawn>(typeId) { helpers.relocateAndReset(npc) }
        }
        onEvent<NpcStateEvents.Respawn> { if (npc.id in bearNpcIds) helpers.relocateAndReset(npc) }
    }
}

class Artio
@Inject
constructor(deps: BossDeps) : BossPluginScript(deps) {

    // Artio shares the encounter handlers registered by [Callisto]; only the spec differs.
    override val spec = bearSpec(ARTIO_LAIR, attackRate = BearTuning.ARTIO_ATTACK_RATE)
}

/**
 * The bear specs are identical apart from their numbers: a melee swipe that pierces Protect from
 * Melee, a crescent ranged attack used when the boss cannot reach its target, and enrage triggers
 * at 66%/33% health. The melee damage itself is applied by the `callisto.melee_strike` external so
 * it can bypass the standard (full-block) prayer reduction with a custom hit modifier.
 */
private fun bearSpec(lair: BearLairConfig, attackRate: Int): BossSpec = boss(lair.bossNpc) {
    stats(attackRate = attackRate, aggressionRadius = BearTuning.AGGRO_RADIUS)

    val melee =
        ability("melee") {
            anim("seq.bear_rework_attack")
            include(external("callisto.melee_strike"))
        }

    val rangedAttack =
        ability("ranged_attack") {
            anim("seq.bear_rework_attack")
            include(
                onEach(
                    AllInRadius(radius = BearTuning.ARENA_ATTACK_RADIUS),
                    Effect.Projectile(
                        spotanim = "spotanim.fx_callisto_ranged_projectile",
                        config = BearTuning.RANGED_PROJECTILE_CONFIG,
                        hit =
                            Effect.Hit(
                                damage = Roll(0..lair.rangedMaxHit),
                                type = Ranged,
                                spotanim = "spotanim.fx_callisto_ranged_impact",
                                spotanimHeight = BearTuning.RANGED_IMPACT_HEIGHT,
                            ),
                    ),
                )
            )
        }

    phase("combat") {
        weightedSelectorRandom {
            +random(melee, weight = 12, requires = WithinMeleeRange)
            +random(rangedAttack, weight = 3, requires = Condition.Not(WithinMeleeRange))
        }
    }

    triggers {
        on(Condition.HpBelow(BearTuning.ENRAGE_HP_FIRST)) runs
            sequence(spotanim("spotanim.fx_callisto_enrage"), external("callisto.enrage"))
        on(Condition.HpBelow(BearTuning.ENRAGE_HP_SECOND)) runs
            sequence(spotanim("spotanim.fx_callisto_enrage"), external("callisto.enrage"))
    }
}

/**
 * Shared combat mechanics for both bear bosses. All per-fight state is keyed by the npc's slot id
 * and cleared on spawn/respawn, so nothing leaks between kills.
 */
internal class BearEncounter(private val deps: BossDeps, private val locRepo: LocRepository) {

    private class FightState {
        var attacks = 0
        var enrageStage = 0
        var tickerRunning = false
        val traps = mutableMapOf<CoordGrid, Trap>()
    }

    private data class Trap(val loc: org.rsmod.game.loc.LocInfo, val spawnedCycle: Int)

    private val fights = mutableMapOf<Int, FightState>()

    private val lairsById: Map<Int, BearLairConfig> by lazy {
        listOf(CALLISTO_LAIR, ARTIO_LAIR).associateBy { it.bossNpc.asRSCM(RSCMType.NPC) }
    }

    private val trapSpawnSpot: SpotanimType by lazy {
        SpotanimType("spotanim.fx_callisto_trap".asRSCM(RSCMType.SPOTANIM))
    }

    private val shockwaveSpot: Int by lazy {
        "spotanim.fx_callisto_ranged_spotanim".asRSCM(RSCMType.SPOTANIM)
    }

    private fun state(npc: Npc): FightState = fights.getOrPut(npc.slotId) { FightState() }

    private fun lairFor(npc: Npc): BearLairConfig = lairsById.getValue(npc.id)

    fun relocateAndReset(npc: Npc) {
        fights.remove(npc.slotId)
        val lair = lairsById.getValue(npc.id)
        if (lair.contains(npc.spawnCoords)) return
        // The packed spawn sits on the surface-entrance tile; move the boss into its den and fix
        // its respawn point so every future respawn lands in the den as well.
        npc.telejump(deps.collision, lair.bossSpawn)
        npc.spawnCoords = lair.bossSpawn
    }

    fun registerExternals() {
        deps.extensionRegistry.register("callisto.melee_strike") { _, npc, target, _ ->
            meleeStrike(npc, target)
        }
        deps.extensionRegistry.register("callisto.enrage") { _, npc, _, _ -> onEnrage(npc) }
    }

    /**
     * Callisto's melee is extremely accurate, has a high minimum hit, and Protect from Melee only
     * halves it (unlike most bosses, whose protection prayers fully block npc hits). Anyone near
     * the main target is caught in the swipe.
     */
    private val pierceMeleeModifier =
        PlayerHitModifier { t ->
            if (t.adminGodMode) {
                damage = 0
                return@PlayerHitModifier
            }
            if (type == HitType.Melee && t.vars["varbit.prayer_protectfrommelee"] == 1) {
                damage = damage * BearTuning.MELEE_PRAYER_PIERCE_REDUCTION / 100
            }
            damage = PlayerAbsorption.absorb(player = t, incomingDamage = damage)
        }

    private fun meleeStrike(npc: Npc, target: Player) {
        val lair = lairFor(npc)
        val targets =
            deps.playerList.filter {
                it.hitpoints > 0 &&
                    it.coords.level == npc.coords.level &&
                    it.coords.chebyshevDistance(target.coords) <=
                        BearTuning.MELEE_SPLASH_RADIUS
            }
        for (player in targets) {
            val range = lair.meleeMaxHit - lair.meleeMinHit + 1
            val damage = lair.meleeMinHit + deps.random.of(range)
            player.finishNpcHit(npc, 1, HitType.Melee, damage, pierceMeleeModifier)
        }
    }

    /**
     * The shockwave: a slow magic orb at the current target, hurled every [BearTuning.SHOCKWAVE_ATTACK_INTERVAL]
     * attacks. Without Protect from Magic the blast hurls the player (and anyone within a 3x3 of
     * them) back up to 3 tiles; the damage scales with how much of the knockback was blocked by
     * walls, traps or other players.
     */
    private fun sendShockwave(npc: Npc, target: Player) {
        deps.bossProjectile(
            spotanim = shockwaveSpot,
            src = npc.coords,
            target = target.coords,
            startHeight = BearTuning.SHOCK_START_HEIGHT,
            endHeight = BearTuning.SHOCK_END_HEIGHT,
            delay = BearTuning.SHOCK_PROJ_DELAY,
            travel = BearTuning.SHOCK_PROJ_TRAVEL,
            curve = BearTuning.SHOCK_PROJ_ANGLE,
        )
        deps.worldQueues.add(BearTuning.SHOCK_IMPACT_TICKS) { resolveShockwave(npc, target) }
    }

    private fun resolveShockwave(npc: Npc, target: Player) {
        if (!npc.isSlotAssigned) return
        val splash =
            deps.playerList.filter {
                it.hitpoints > 0 &&
                    it.coords.level == target.coords.level &&
                    it.coords.chebyshevDistance(target.coords) <= BearTuning.SHOCK_SPLASH_RADIUS
            }
        for (player in splash) applyShockwave(npc, player)
    }

    private fun applyShockwave(npc: Npc, player: Player) {
        if (player.vars["varbit.prayer_protectfrommagic"] == 1) return
        val dir = knockbackDir(npc.coords, player.coords)
        var dest = player.coords
        var moved = 0
        repeat(BearTuning.SHOCK_KNOCKBACK_TILES) {
            val candidate = dest.translate(dir.first, dir.second)
            if (!knockbackAllowed(candidate)) return@repeat
            dest = candidate
            moved++
        }
        // Stun first (it cancels queued movement), then glide the player to the landing tile.
        CombatEffects.stun(player, BearTuning.SHOCK_STUN_TICKS)
        if (moved > 0) {
            PathingEntityCommon.exactMove(
                player,
                player.coords,
                dest,
                BearTuning.SHOCK_MOVE_DELAY1,
                BearTuning.SHOCK_MOVE_DELAY2,
                faceDir(dir),
                deps.collision,
            )
        }
        val blocked = BearTuning.SHOCK_KNOCKBACK_TILES - moved
        val damage = BearTuning.SHOCK_BASE_DAMAGE + blocked * BearTuning.SHOCK_DAMAGE_PER_BLOCKED_TILE
        player.finishNpcHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
    }

    private fun knockbackDir(from: CoordGrid, to: CoordGrid): Pair<Int, Int> {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return if (abs(dx) >= abs(dz)) {
            if (dx >= 0) 1 to 0 else -1 to 0
        } else {
            if (dz >= 0) 0 to 1 else 0 to -1
        }
    }

    private fun faceDir(dir: Pair<Int, Int>): Int =
        when {
            dir.first > 0 && dir.second > 0 -> constants.em_face_northeast
            dir.first > 0 && dir.second < 0 -> constants.em_face_southeast
            dir.first < 0 && dir.second > 0 -> constants.em_face_northwest
            dir.first < 0 && dir.second < 0 -> constants.em_face_southwest
            dir.first > 0 -> constants.em_face_east
            dir.first < 0 -> constants.em_face_west
            dir.second > 0 -> constants.em_face_north
            else -> constants.em_face_south
        }

    private fun knockbackAllowed(dest: CoordGrid): Boolean {
        if (deps.collision.isWalkBlocked(dest)) return false
        if (deps.playerList.any { it.coords == dest }) return false
        if (fights.values.any { it.traps.containsKey(dest) }) return false
        return true
    }

    /**
     * Enrage: the boss glows red (spec effect), breaks free of any bind, and hurls bear traps all
     * across its den. Traps spring when stepped on, damaging and binding the player.
     */
    private fun onEnrage(npc: Npc) {
        val state = state(npc)
        state.enrageStage++
        // Break free of any bind, as the roar shatters ice and entangles.
        npc.movementLocked = false
        npc.clearTimer("timer.combat_freeze")
        deployTraps(npc, lairFor(npc).trapCount)
    }

    private fun deployTraps(npc: Npc, count: Int) {
        val state = state(npc)
        val lair = lairFor(npc)
        state.traps.values.removeIf { deps.mapClock.cycle - it.spawnedCycle >= BearTuning.TRAP_DURATION_TICKS }
        val candidates =
            buildList {
                    for (dx in -BearTuning.TRAP_SCATTER_RADIUS..BearTuning.TRAP_SCATTER_RADIUS) {
                        for (dz in -BearTuning.TRAP_SCATTER_RADIUS..BearTuning.TRAP_SCATTER_RADIUS) {
                            val coord = lair.bossSpawn.translate(dx, dz)
                            if (!lair.contains(coord)) continue
                            if (coord.chebyshevDistance(npc.coords) <= BearTuning.TRAP_MIN_BOSS_DISTANCE) continue
                            if (deps.collision.isWalkBlocked(coord)) continue
                            if (coord in state.traps) continue
                            add(coord)
                        }
                    }
                }
                .toMutableList()
        repeat(count) {
            if (candidates.isEmpty()) return@repeat
            val coord = candidates.removeAt(deps.random.of(candidates.size))
            val loc =
                locRepo.add(
                    coord,
                    "loc.callisto_trap_loc",
                    BearTuning.TRAP_DURATION_TICKS,
                    LocAngle[deps.random.of(4)],
                    LocShape.GroundDecor,
                )
            state.traps[coord] = Trap(loc, deps.mapClock.cycle)
            deps.worldRepo.spotanimMap(trapSpawnSpot, coord)
        }
        startTrapTicker(npc)
    }

    private fun startTrapTicker(npc: Npc) {
        val state = state(npc)
        if (state.tickerRunning) return
        state.tickerRunning = true
        deps.repeatTick(
            ticks = BearTuning.TRAP_TICKER_WINDOW,
            onTick = { _ ->
                if (!npc.isSlotAssigned) {
                    state.tickerRunning = false
                    return@repeatTick false
                }
                state.traps.values.removeIf {
                    deps.mapClock.cycle - it.spawnedCycle >= BearTuning.TRAP_DURATION_TICKS
                }
                if (state.traps.isEmpty()) {
                    state.tickerRunning = false
                    return@repeatTick false
                }
                for (player in deps.playerList) {
                    if (player.hitpoints <= 0) continue
                    val trap = state.traps[player.coords] ?: continue
                    if (deps.random.of(BearTuning.TRAP_TRIGGER_OUT_OF) != 1) continue
                    triggerTrap(npc, player, trap, state)
                }
                true
            },
            onStop = { state.tickerRunning = false },
        )
    }

    private fun triggerTrap(npc: Npc, player: Player, trap: Trap, state: FightState) {
        val span = BearTuning.TRAP_DAMAGE_MAX - BearTuning.TRAP_DAMAGE_MIN + 1
        val damage = BearTuning.TRAP_DAMAGE_MIN + deps.random.of(span)
        player.finishNpcHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
        CombatEffects.freeze(player, BearTuning.TRAP_BIND_TICKS)
        deps.worldRepo.spotanimMap(trapSpawnSpot, player.coords)
        state.traps.remove(player.coords)
        // A sprung trap is spent; remove it immediately and ensure it never respawns.
        locRepo.del(trap.loc, Int.MAX_VALUE)
    }
}

class CallistoModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(BearAttackValidateHook::class.java)
    }
}

/** Hunter's End is a single-way area; Artio is a singles+ boss and stays attackable by groups. */
internal class BearAttackValidateHook
@Inject
constructor() : NpcAttackValidateHook {
    private val artioId by lazy { ARTIO_LAIR.bossNpc.asRSCM(RSCMType.NPC) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        if (npc.id == artioId) {
            NpcAttackValidateResult.BypassSingleWayPvnRestriction
        } else {
            NpcAttackValidateResult.Pass
        }
}
