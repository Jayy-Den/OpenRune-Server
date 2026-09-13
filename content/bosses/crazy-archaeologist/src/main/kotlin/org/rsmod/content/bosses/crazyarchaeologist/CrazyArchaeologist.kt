package org.rsmod.content.bosses.crazyarchaeologist

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.Melee
import org.rsmod.api.bosses.dsl.Ranged
import org.rsmod.api.bosses.dsl.Roll
import org.rsmod.api.bosses.dsl.WithinMeleeRange
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Bellock, the crazy archaeologist (level 23 wilderness ruins), attacks with Ranged (max 14,
 * negated by Protect from Missiles) and Crush melee (same 14 max, negated by Protect from Melee)
 * when the player is adjacent. Periodically he yells "Rain of knowledge!" and lobs three books in
 * an arc at the player's tile; each explodes in a 3x3 area (max 24), and one book splits into two
 * further books on impact, which also explode after a delay.
 *
 * Basic attacks are spec-driven; the book storm is an external so it can chain the split impact
 * across multiple world-queue ticks. Books are spec-driven parallel projectiles against tiles,
 * which the interpreter resolves as map telegraphs with a queued AoE damage application.
 */
private object BellockTuning {
    const val ATTACK_RATE = 3
    const val AGGRO_RADIUS = 8

    const val BASIC_MAX_HIT = 14
    const val BOOK_MAX_HIT = 24

    const val BOOK_SCATTER_RADIUS = 2
    const val BOOK_IMPACT_RADIUS = 1
    const val BOOK_WINDUP_TICKS = 4

    /** The initial volley plus the two split books from the centre book. */
    const val SPLIT_SCATTER_RADIUS = 1
    const val SPLIT_WINDUP_TICKS = 3

    const val BOOK_PROJECTILE_DELAY = 10
    const val BOOK_PROJECTILE_TRAVEL = 90
    const val BOOK_PROJECTILE_CURVE = 16
    const val BOOK_START_HEIGHT = 40
    const val BOOK_END_HEIGHT = 0
    const val BOOK_IMPACT_HEIGHT = 30

    /** Weighted pick out of 15: 10 basic, 5 book storm. */
    const val WEIGHT_BASIC = 10
    const val WEIGHT_BOOKS = 5

    /** The engine-level cooldown between book storms (matches OSRS's periodic cadence). */
    const val BOOK_COOLDOWN_TICKS = 15
}

internal class BellockEncounter(private val deps: BossDeps) {

    private val bookSpecialSpot: Int by lazy {
        "spotanim.crazy_archaeologist_book_special".asRSCM(RSCMType.SPOTANIM)
    }
    private val bookImpactType: SpotanimType by lazy {
        SpotanimType("spotanim.crazy_archaeologist_book".asRSCM(RSCMType.SPOTANIM))
    }

    fun registerExternals() {
        deps.extensionRegistry.register("bellock.book_storm") { _, npc, target, _ ->
            bookStorm(npc, target)
        }
    }

    /**
     * "Rain of knowledge!": three books arc at the player's tile and two scatter tiles; each
     * bursts in a 3x3 explosion, and the centre book splits into two further books which also
     * burst after a delay.
     */
    private fun bookStorm(npc: Npc, target: Player) {
        val center = target.coords
        val scatter =
            buildList {
                    for (dx in -BellockTuning.BOOK_SCATTER_RADIUS..BellockTuning.BOOK_SCATTER_RADIUS) {
                        for (dz in -BellockTuning.BOOK_SCATTER_RADIUS..BellockTuning.BOOK_SCATTER_RADIUS) {
                            if (dx == 0 && dz == 0) continue
                            add(center.translate(dx, dz))
                        }
                    }
                }
                .shuffled()
                .take(2)
        for (tile in listOf(center) + scatter) {
            launchBook(npc, tile, BellockTuning.BOOK_WINDUP_TICKS)
        }
        // The centre book splits into two further books on impact.
        deps.worldQueues.add(BellockTuning.BOOK_WINDUP_TICKS) {
            if (!npc.isSlotAssigned) return@add
            val splitCenter = center
            val splitScatter =
                buildList {
                        for (dx in -BellockTuning.SPLIT_SCATTER_RADIUS..BellockTuning.SPLIT_SCATTER_RADIUS) {
                            for (dz in -BellockTuning.SPLIT_SCATTER_RADIUS..BellockTuning.SPLIT_SCATTER_RADIUS) {
                                if (dx == 0 && dz == 0) continue
                                add(splitCenter.translate(dx, dz))
                            }
                        }
                    }
                    .shuffled()
                    .take(2)
            for (tile in splitScatter) {
                launchBook(npc, tile, BellockTuning.SPLIT_WINDUP_TICKS)
            }
        }
    }

    private fun launchBook(npc: Npc, tile: CoordGrid, windup: Int) {
        deps.bossProjectile(
            spotanim = bookSpecialSpot,
            src = npc.coords,
            target = tile,
            startHeight = BellockTuning.BOOK_START_HEIGHT,
            endHeight = BellockTuning.BOOK_END_HEIGHT,
            delay = BellockTuning.BOOK_PROJECTILE_DELAY,
            travel = BellockTuning.BOOK_PROJECTILE_TRAVEL,
            curve = BellockTuning.BOOK_PROJECTILE_CURVE,
        )
        deps.worldQueues.add(windup) { explodeAt(npc, tile) }
    }

    private fun explodeAt(npc: Npc, tile: CoordGrid) {
        if (!npc.isSlotAssigned) return
        deps.worldRepo.spotanimMap(bookImpactType, tile, BellockTuning.BOOK_IMPACT_HEIGHT)
        val splash =
            deps.playerList.filter {
                it.hitpoints > 0 &&
                    it.coords.level == tile.level &&
                    it.coords.chebyshevDistance(tile) <= BellockTuning.BOOK_IMPACT_RADIUS
            }
        for (player in splash) {
            // The book explosion is magical: Protect from Magic fully negates it.
            if (player.vars["varbit.prayer_protectfrommagic"] == 1) continue
            val damage = deps.random.of(BellockTuning.BOOK_MAX_HIT + 1)
            player.finishNpcHit(npc, 1, HitType.Magic, damage, deps.playerHitModifier)
        }
    }
}

class CrazyArchaeologist
@Inject
constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val encounter = BellockEncounter(deps)

    override val spec: BossSpec by lazy { bellockSpec() }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        encounter.registerExternals()
    }
}

private fun bellockSpec(): BossSpec = boss("npc.crazy_archaeologist") {
    stats(attackRate = BellockTuning.ATTACK_RATE, aggressionRadius = BellockTuning.AGGRO_RADIUS)

    val ranged =
        ability("ranged_attack") {
            anim("seq.human_caststrike")
            include(
                Effect.Projectile(
                    spotanim = "spotanim.troll_rock_travel",
                    config =
                        ProjectileConfig(
                            startHeight = 40,
                            endHeight = 35,
                            startDelay = 10,
                            travelTime = 0,
                            angle = 16,
                            progress = 0,
                            stepMultiplier = 5,
                        ),
                    hit =
                        Effect.Hit(
                            damage = Roll(0..BellockTuning.BASIC_MAX_HIT),
                            type = Ranged,
                        ),
                )
            )
        }

    val melee =
        ability("melee_attack") {
            anim("seq.human_unarmedpunch")
            include(
                Effect.Hit(
                    damage = Roll(0..BellockTuning.BASIC_MAX_HIT),
                    type = Melee,
                )
            )
        }

    val bookStorm =
        ability("book_storm") {
            say("Rain of knowledge!")
            anim("seq.book_chuck")
            include(external("bellock.book_storm"))
        }

    phase("combat") {
        weightedSelectorRandom {
            // Bellock mostly uses his ranged rock throw; melee is only available adjacent; books
            // fire on a periodic cooldown.
            +random(ranged, weight = BellockTuning.WEIGHT_BASIC, requires = Condition.Not(WithinMeleeRange))
            +random(ranged, weight = BellockTuning.WEIGHT_BASIC, requires = WithinMeleeRange)
            +random(melee, weight = BellockTuning.WEIGHT_BASIC, requires = WithinMeleeRange)
            +random(
                bookStorm,
                weight = BellockTuning.WEIGHT_BOOKS,
                cooldown = BellockTuning.BOOK_COOLDOWN_TICKS,
            )
        }
    }
}
