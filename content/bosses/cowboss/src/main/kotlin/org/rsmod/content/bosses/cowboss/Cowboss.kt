package org.rsmod.content.bosses.cowboss

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.runtime.repeatTick
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

class Cowboss
@Inject
constructor(
    deps: BossDeps,
    private val aiPlayerInteractions: AiPlayerInteractions,
) : BossPluginScript(deps) {

    private fun engage(npc: Npc, preferred: Player) {
        val target =
            preferred.takeIf(Player::isValidTarget)
                ?: deps.playerList
                    .filter {
                        it.isValidTarget() &&
                            it.coords.chebyshevDistance(npc.coords) <= ARENA_RADIUS
                    }
                    .minByOrNull { it.coords.chebyshevDistance(npc.coords) }
        if (target != null) {
            npc.opPlayer2(target, aiPlayerInteractions)
        }
    }

    /**
     * Brutus' soft anti-safespot: when the player is out of melee reach for [SAFESPOT_GRACE] ticks
     * he charges through the obstacle on his next attack tick. Wiki: "if the player is out of
     * Brutus' attack range for 15 ticks (9s), he may use his charge attack to reach the player
     * which can pass through obstacles, or he will exclaim *huff* and move around obstacles."
     */
    private fun startSafespotWatch(npc: Npc, target: Player) {
        deps.repeatTick(
            ticks = SAFESPOT_GRACE,
            onTick = { remaining ->
                if (npc.hitpoints <= 0 || deps.encounter(npc).currentPhaseName != PHASE_COMBAT) {
                    return@repeatTick false
                }
                val reachable =
                    target.isValidTarget() &&
                        target.coords.chebyshevDistance(npc.coords) <= ENGAGE_AP_RANGE
                if (reachable) {
                    engage(npc, target)
                    return@repeatTick false
                }
                if (remaining == 1) {
                    // Out of reach too long: attack next tick (the weighted selector then offers
                    // the charge which pierces the obstacle).
                    deps.encounter(npc).attackRateOverride = 1
                    engage(npc, target)
                }
                true
            },
        )
    }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)

        deps.extensionRegistry.register("cowboss.post_spawn") { _, npc, target, _ ->
            deps.encounter(npc).transitionTo(PHASE_COMBAT, deps.mapClock.cycle)
            startSafespotWatch(npc, target)
            engage(npc, target)
        }
    }

    override val spec =
        boss("npc.cowboss") {
            stats(attackRate = ATTACK_RATE, aggressionRadius = AGGRO_RADIUS)

            val postSpawn = ability("post_spawn") { include(external("cowboss.post_spawn")) }

            val melee =
                ability("melee") {
                    anim("seq.cow_boss_attack")
                    hit {
                        damage(0..MELEE_MAX).roll()
                        type(Melee)
                    }
                }

            // Charge: *growls* + charge anim telegraph, 3-tick windup (wiki), then a hit that
            // ignores protection prayers (typeless). Dodgeable by moving out of the rush line
            // during the windup.
            val charge =
                ability("charge") {
                    say("*growls*")
                    anim("seq.cow_boss_charge")
                    delay(CHARGE_WINDUP)
                    hit {
                        damage(SPECIAL_MIN..SPECIAL_MAX).roll()
                        type(Typeless)
                    }
                }

            // Slam: *snorts* + stomp telegraph, 4-tick windup (wiki), splash damage on the tiles
            // around Brutus (and beneath him) that ignores protection prayers. The wiki's three
            // successive slams are approximated with the stomp impact spotanim during the AoE.
            val slam =
                ability("slam") {
                    say("*snorts*")
                    anim("seq.cow_boss_stomp")
                    delay(SLAM_WINDUP)
                    Effect.OnEach(
                        targets = TargetExpr.AllInRadius(radius = SLAM_RADIUS, of = TargetExpr.Self),
                        effect =
                            Effect.Hit(
                                damage = Roll(SPECIAL_MIN..SPECIAL_MAX),
                                type = Typeless,
                            ),
                    )
                }

            // Anti-safespot path-around variant: Brutus huffs and re-paths (wiki).
            val huff =
                ability("huff") {
                    say("*huff*")
                    anim("seq.cow_boss_heavy_breath")
                    delay(1)
                }

            phase(PHASE_SPAWN, nextPhase = PHASE_COMBAT) {}

            phase(PHASE_COMBAT) {
                weightedSelectorRandom {
                    +random(melee, weight = 5, requires = Condition.WithinMeleeRange)
                    +random(charge, weight = 1, requires = Condition.Not(Condition.WithinMeleeRange))
                    +random(slam, weight = 1)
                    +random(huff, weight = 1, cooldown = HUFF_COOLDOWN)
                }
                // Wiki: after four to five basic attacks Brutus uses one of his two specials,
                // then alternates between them.
                forceEveryAttacks(min = 4, max = 5, ability = charge)
                forceEveryAttacks(min = 8, max = 10, ability = slam)
            }
        }

    private companion object {
        private const val PHASE_SPAWN = "spawn"
        private const val PHASE_COMBAT = "combat"

        /** Wiki: attack speed 5 ticks. */
        private const val ATTACK_RATE = 5

        /** Wiki: melee max hit 3. */
        private const val MELEE_MAX = 3

        /** Wiki: special attacks scale with the player's HP level, up to 19. */
        private const val SPECIAL_MAX = 19
        private const val SPECIAL_MIN = 5

        /** Wiki: charge telegraph is 3 ticks, slam is 4 ticks. */
        private const val CHARGE_WINDUP = 3
        private const val SLAM_WINDUP = 4
        private const val SLAM_RADIUS = 1

        /** Wiki: anti-safespot grace of 15 ticks (9s). */
        private const val SAFESPOT_GRACE = 15
        private const val ENGAGE_AP_RANGE = 15
        private const val HUFF_COOLDOWN = 25
        private const val AGGRO_RADIUS = 8
        private const val ARENA_RADIUS = 20
    }
}
