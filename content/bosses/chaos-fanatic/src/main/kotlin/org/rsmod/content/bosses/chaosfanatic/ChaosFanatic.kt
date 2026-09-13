package org.rsmod.content.bosses.chaosfanatic

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.Magic
import org.rsmod.api.bosses.dsl.Roll
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Chaos Fanatic (level 42 wilderness, west of the Lava Maze) is a magic-only demi-boss: a red
 * basic spell (max 21, fully negated by Protect from Magic), a periodic unavoidable disarm, and a
 * three-book ground explosion (max 31, also fully negated by Protect from Magic). The attack picker
 * is 10/15 basic, 4/15 explosion, 1/15 disarm, and any special is followed by a 12-attack red-only
 * cooldown (24 game ticks at his 2-tick attack rate).
 *
 * The basic attack is fully spec-driven (the interpreter applies the standard prayer-respecting hit
 * modifier); the two specials are externals so they can disarm equipment and schedule the slow
 * book flights. The engine-level per-ability `cooldown` on the weighted selector implements the
 * red-only window.
 */
private object FanaticTuning {
    const val ATTACK_RATE = 2
    const val AGGRO_RADIUS = 8

    const val BASIC_MAX_HIT = 21
    const val EXPLOSION_MAX_HIT = 31

    /** Weighted pick out of 15: 10 basic, 4 explosion, 1 disarm. */
    const val WEIGHT_BASIC = 10
    const val WEIGHT_EXPLOSION = 4
    const val WEIGHT_DISARM = 1

    /** The red-only window after any special (12 attacks at [ATTACK_RATE] ticks each). */
    const val SPECIAL_COOLDOWN_TICKS = 12 * ATTACK_RATE

    /** Wiki: the books burst on impact with the ground a short while after being launched. */
    const val BOOK_WINDUP_TICKS = 4

    const val BOOK_SCATTER_RADIUS = 2
    const val EXPLOSION_SPLASH_RADIUS = 1
    const val BOOK_PROJECTILE_DELAY = 10
    const val BOOK_PROJECTILE_TRAVEL = 80
    const val BOOK_PROJECTILE_CURVE = 16
    const val BOOK_START_HEIGHT = 40
    const val BOOK_END_HEIGHT = 0
    const val BOOK_IMPACT_HEIGHT = 30
}

/**
 * Shared combat mechanics for the fanatic. All per-fight state is keyed by the npc's slot id and
 * cleared on respawn, so nothing leaks between kills.
 */
internal class FanaticEncounter(private val deps: BossDeps, private val unequipOp: WornUnequipOp) {

    private class State {
        val pendingBooks = mutableSetOf<CoordGrid>()
    }

    private val fights = mutableMapOf<Int, State>()

    private val bookSpecialSpot: Int by lazy {
        "spotanim.crazy_archaeologist_book_special".asRSCM(RSCMType.SPOTANIM)
    }
    private val bookImpactType: SpotanimType by lazy {
        SpotanimType("spotanim.crazy_archaeologist_book".asRSCM(RSCMType.SPOTANIM))
    }

    private fun state(npc: Npc): State = fights.getOrPut(npc.slotId) { State() }

    fun reset(npc: Npc) {
        fights.remove(npc.slotId)
    }

    fun registerExternals() {
        deps.extensionRegistry.register("chaosfanatic.disarm") { _, npc, target, _ ->
            disarm(npc, target)
        }
        deps.extensionRegistry.register("chaosfanatic.book_explosion") { _, npc, target, _ ->
            bookExplosion(npc, target)
        }
    }

    /**
     * The unavoidable green projectile that throws the target's wielded weapon into their
     * inventory. With no free inventory space the weapon stays equipped (wiki: a full inventory
     * negates the disarm entirely).
     */
    private fun disarm(npc: Npc, target: Player) {
        val worn = target.worn
        val weaponSlot = Wearpos.RightHand.slot
        if (worn[weaponSlot] == null) return
        val result = unequipOp.unequip(target, weaponSlot, worn, target.inv)
        if (result is WornUnequipResult.Fail.NotEnoughInvSpace) return
        deps.worldRepo.spotanimMap(bookImpactType, target.coords, FanaticTuning.BOOK_IMPACT_HEIGHT)
    }

    /**
     * Three slow green books arc toward the target: one always lands exactly on the tile the
     * player occupies at launch, the other two scatter nearby. Each bursts in a 3x3 explosion
     * after [FanaticTuning.BOOK_WINDUP_TICKS]. The burst is magical: Protect from Magic fully
     * negates it.
     */
    private fun bookExplosion(npc: Npc, target: Player) {
        val state = state(npc)
        val center = target.coords
        val scatter =
            buildList {
                    for (dx in -FanaticTuning.BOOK_SCATTER_RADIUS..FanaticTuning.BOOK_SCATTER_RADIUS) {
                        for (dz in -FanaticTuning.BOOK_SCATTER_RADIUS..FanaticTuning.BOOK_SCATTER_RADIUS) {
                            if (dx == 0 && dz == 0) continue
                            add(center.translate(dx, dz))
                        }
                    }
                }
                .shuffled()
                .take(2)
        for (tile in listOf(center) + scatter) {
            launchBook(npc, tile, state)
        }
    }

    private fun launchBook(npc: Npc, tile: CoordGrid, state: State) {
        state.pendingBooks += tile
        deps.bossProjectile(
            spotanim = bookSpecialSpot,
            src = npc.coords,
            target = tile,
            startHeight = FanaticTuning.BOOK_START_HEIGHT,
            endHeight = FanaticTuning.BOOK_END_HEIGHT,
            delay = FanaticTuning.BOOK_PROJECTILE_DELAY,
            travel = FanaticTuning.BOOK_PROJECTILE_TRAVEL,
            curve = FanaticTuning.BOOK_PROJECTILE_CURVE,
        )
        deps.worldQueues.add(FanaticTuning.BOOK_WINDUP_TICKS) { explodeAt(npc, tile, state) }
    }

    private fun explodeAt(npc: Npc, tile: CoordGrid, state: State) {
        state.pendingBooks.remove(tile)
        if (!npc.isSlotAssigned) return
        deps.worldRepo.spotanimMap(bookImpactType, tile, FanaticTuning.BOOK_IMPACT_HEIGHT)
        val splash =
            deps.playerList.filter {
                it.hitpoints > 0 &&
                    it.coords.level == tile.level &&
                    it.coords.chebyshevDistance(tile) <= FanaticTuning.EXPLOSION_SPLASH_RADIUS
            }
        for (player in splash) {
            // The burst is magical: Protect from Magic fully negates it.
            if (player.vars["varbit.prayer_protectfrommagic"] == 1) continue
            val damage = deps.random.of(FanaticTuning.EXPLOSION_MAX_HIT + 1)
            player.finishNpcHit(npc, 1, HitType.Magic, damage, deps.playerHitModifier)
        }
    }
}

class ChaosFanatic
@Inject
constructor(deps: BossDeps, unequipOp: WornUnequipOp) : BossPluginScript(deps) {

    private val encounter = FanaticEncounter(deps, unequipOp)

    override val spec: BossSpec by lazy { fanaticSpec() }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        encounter.registerExternals()
        onEvent<NpcStateEvents.Respawn> { encounter.reset(npc) }
    }
}

private fun fanaticSpec(): BossSpec = boss("npc.chaos_fanatic") {
    stats(attackRate = FanaticTuning.ATTACK_RATE, aggressionRadius = FanaticTuning.AGGRO_RADIUS)

    val basic =
        ability("basic_attack") {
            anim("seq.human_caststrike_staff")
            include(
                Effect.Projectile(
                    spotanim = "spotanim.windstrike_travel",
                    config =
                        ProjectileConfig(
                            startHeight = 40,
                            endHeight = 40,
                            startDelay = 10,
                            travelTime = 0,
                            angle = 16,
                            progress = 0,
                            stepMultiplier = 5,
                        ),
                    impact = "spotanim.windstrike_impact",
                    hit =
                        Effect.Hit(
                            damage = Roll(0..FanaticTuning.BASIC_MAX_HIT),
                            type = Magic,
                        ),
                )
            )
        }
    val disarm = ability("disarm_attack") { include(external("chaosfanatic.disarm")) }
    val explosion = ability("book_explosion") { include(external("chaosfanatic.book_explosion")) }

    phase("combat") {
        weightedSelectorRandom {
            // The wiki picker: 10/15 red, 4/15 explosion, 1/15 disarm. Both specials carry the
            // red-only window as an engine-level cooldown, so a red streak follows every special.
            +random(basic, weight = FanaticTuning.WEIGHT_BASIC)
            +random(
                explosion,
                weight = FanaticTuning.WEIGHT_EXPLOSION,
                cooldown = FanaticTuning.SPECIAL_COOLDOWN_TICKS,
            )
            +random(
                disarm,
                weight = FanaticTuning.WEIGHT_DISARM,
                cooldown = FanaticTuning.SPECIAL_COOLDOWN_TICKS,
            )
        }
    }
}
