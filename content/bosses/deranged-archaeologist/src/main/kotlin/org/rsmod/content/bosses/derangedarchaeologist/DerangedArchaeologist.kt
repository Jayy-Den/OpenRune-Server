package org.rsmod.content.bosses.derangedarchaeologist

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
 * The deranged archaeologist (southern Tar Swamp, Fossil Island) is a harder copy of the crazy
 * archaeologist: Ranged/Crush basics at max 25, and the "Learn to read!" book storm whose direct
 * hit maxes at 56 (wiki's 92 combined value includes follow-up split books at 18+ each). Books
 * burst four ticks after being thrown; the centre book splits into two further books.
 *
 * Basics are spec-driven; the book storm is an external so it can chain the split impact across
 * world-queue ticks.
 */
private object DerangedTuning {
    const val ATTACK_RATE = 3
    const val AGGRO_RADIUS = 8

    const val BASIC_MAX_HIT = 25
    const val BOOK_DIRECT_MAX_HIT = 56
    const val BOOK_SPLIT_MAX_HIT = 18

    const val BOOK_SCATTER_RADIUS = 2
    const val BOOK_IMPACT_RADIUS = 1
    const val BOOK_WINDUP_TICKS = 4

    /** The centre book splits into two further books from its impact tile. */
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

internal class DerangedEncounter(private val deps: BossDeps) {

    private val bookSpecialSpot: Int by lazy {
        "spotanim.crazy_archaeologist_book_special".asRSCM(RSCMType.SPOTANIM)
    }
    private val bookImpactType: SpotanimType by lazy {
        SpotanimType("spotanim.crazy_archaeologist_book".asRSCM(RSCMType.SPOTANIM))
    }

    fun registerExternals() {
        deps.extensionRegistry.register("deranged.book_storm") { _, npc, target, _ ->
            bookStorm(npc, target)
        }
    }

    /**
     * "Learn to read!": three books arc at the player's tile and two scatter tiles; each bursts
     * in a 3x3 explosion, and the centre book splits into two further books which also burst
     * after a delay.
     */
    private fun bookStorm(npc: Npc, target: Player) {
        val center = target.coords
        val scatter =
            buildList {
                    for (dx in -DerangedTuning.BOOK_SCATTER_RADIUS..DerangedTuning.BOOK_SCATTER_RADIUS) {
                        for (dz in -DerangedTuning.BOOK_SCATTER_RADIUS..DerangedTuning.BOOK_SCATTER_RADIUS) {
                            if (dx == 0 && dz == 0) continue
                            add(center.translate(dx, dz))
                        }
                    }
                }
                .shuffled()
                .take(2)
        for (tile in listOf(center) + scatter) {
            launchBook(npc, tile, DerangedTuning.BOOK_DIRECT_MAX_HIT, DerangedTuning.BOOK_WINDUP_TICKS)
        }
        // The centre book splits into two further books on impact.
        deps.worldQueues.add(DerangedTuning.BOOK_WINDUP_TICKS) {
            if (!npc.isSlotAssigned) return@add
            val splitScatter =
                buildList {
                        for (dx in -DerangedTuning.SPLIT_SCATTER_RADIUS..DerangedTuning.SPLIT_SCATTER_RADIUS) {
                            for (dz in -DerangedTuning.SPLIT_SCATTER_RADIUS..DerangedTuning.SPLIT_SCATTER_RADIUS) {
                                if (dx == 0 && dz == 0) continue
                                add(center.translate(dx, dz))
                            }
                        }
                    }
                    .shuffled()
                    .take(2)
            for (tile in splitScatter) {
                launchBook(npc, tile, DerangedTuning.BOOK_SPLIT_MAX_HIT, DerangedTuning.SPLIT_WINDUP_TICKS)
            }
        }
    }

    private fun launchBook(npc: Npc, tile: CoordGrid, maxHit: Int, windup: Int) {
        deps.bossProjectile(
            spotanim = bookSpecialSpot,
            src = npc.coords,
            target = tile,
            startHeight = DerangedTuning.BOOK_START_HEIGHT,
            endHeight = DerangedTuning.BOOK_END_HEIGHT,
            delay = DerangedTuning.BOOK_PROJECTILE_DELAY,
            travel = DerangedTuning.BOOK_PROJECTILE_TRAVEL,
            curve = DerangedTuning.BOOK_PROJECTILE_CURVE,
        )
        deps.worldQueues.add(windup) { explodeAt(npc, tile, maxHit) }
    }

    private fun explodeAt(npc: Npc, tile: CoordGrid, maxHit: Int) {
        if (!npc.isSlotAssigned) return
        deps.worldRepo.spotanimMap(bookImpactType, tile, DerangedTuning.BOOK_IMPACT_HEIGHT)
        val splash =
            deps.playerList.filter {
                it.hitpoints > 0 &&
                    it.coords.level == tile.level &&
                    it.coords.chebyshevDistance(tile) <= DerangedTuning.BOOK_IMPACT_RADIUS
            }
        for (player in splash) {
            // The book explosion is magical: Protect from Magic fully negates it.
            if (player.vars["varbit.prayer_protectfrommagic"] == 1) continue
            val damage = deps.random.of(maxHit + 1)
            player.finishNpcHit(npc, 1, HitType.Magic, damage, deps.playerHitModifier)
        }
    }
}

class DerangedArchaeologist
@Inject
constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val encounter = DerangedEncounter(deps)

    override val spec: BossSpec by lazy { derangedSpec() }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        encounter.registerExternals()
    }
}

private fun derangedSpec(): BossSpec = boss("npc.fossil_crazy_archaeologist") {
    stats(attackRate = DerangedTuning.ATTACK_RATE, aggressionRadius = DerangedTuning.AGGRO_RADIUS)

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
                            damage = Roll(0..DerangedTuning.BASIC_MAX_HIT),
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
                    damage = Roll(0..DerangedTuning.BASIC_MAX_HIT),
                    type = Melee,
                )
            )
        }

    val bookStorm =
        ability("book_storm") {
            say("Learn to read!")
            anim("seq.book_chuck")
            include(external("deranged.book_storm"))
        }

    phase("combat") {
        weightedSelectorRandom {
            // The deranged archaeologist mostly uses his ranged rock throw; melee is only
            // available adjacent; books fire on a periodic cooldown.
            +random(ranged, weight = DerangedTuning.WEIGHT_BASIC, requires = Condition.Not(WithinMeleeRange))
            +random(ranged, weight = DerangedTuning.WEIGHT_BASIC, requires = WithinMeleeRange)
            +random(melee, weight = DerangedTuning.WEIGHT_BASIC, requires = WithinMeleeRange)
            +random(
                bookStorm,
                weight = DerangedTuning.WEIGHT_BOOKS,
                cooldown = DerangedTuning.BOOK_COOLDOWN_TICKS,
            )
        }
    }
}
