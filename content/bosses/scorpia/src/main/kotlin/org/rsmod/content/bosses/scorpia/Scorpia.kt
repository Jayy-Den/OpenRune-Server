package org.rsmod.content.bosses.scorpia

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.Melee
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.repeatTick
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.npc.heal
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Scorpia (beneath the Scorpion Pit, level 53-55 wilderness) is a multi-combat melee boss with a
 * max hit of 16 on a 4-tick attack rate. Her sting drains 2 prayer points whenever the target has
 * Protect from Melee active, whether the attack lands or not. Both she and her offspring poison
 * (starting at 20 and 6 damage respectively, wired via poisonous-npcs.toml).
 *
 * Once she drops to 99 hitpoints or lower (half of her 200), the next attack summons two Scorpia's
 * guardians, which heal her 8 hitpoints every 3 ticks from up to 3 tiles away with a curse
 * projectile. A guardian that has not healed her for 15 seconds despawns; killed guardians never
 * respawn.
 */
private object ScorpiaTuning {
    const val ATTACK_RATE = 4
    const val AGGRO_RADIUS = 8

    const val STING_MAX_HIT = 16
    const val STING_PRAYER_DRAIN = 2
    const val STING_POISON_START = 20

    /** Guardians spawn once Scorpia is at 99 hp or lower (half her 200 base hp). */
    const val GUARDIAN_TRIGGER_FRACTION = 0.5

    const val GUARDIAN_COUNT = 2
    const val GUARDIAN_SPAWN_RADIUS = 2
    const val GUARDIAN_HEAL_RANGE = 3
    const val GUARDIAN_HEAL_AMOUNT = 8
    const val GUARDIAN_HEAL_INTERVAL_TICKS = 3

    /** A guardian that has not healed Scorpia for 15 seconds (25 ticks) despawns. */
    const val GUARDIAN_DESPAWN_AFTER_TICKS = 25

    /** Hard lifetime cap on summoned guardians; the despawn rule normally ends them sooner. */
    const val GUARDIAN_LIFETIME_TICKS = 600
}

internal class ScorpiaEncounter(private val deps: BossDeps) {

    private class Guardian(val npc: Npc) {
        var outOfRangeTicks = 0
    }

    private class FightState {
        val guardians = mutableListOf<Guardian>()
        var tickerRunning = false
    }

    private val fights = mutableMapOf<Int, FightState>()

    private val curseSpot: SpotanimType by lazy {
        SpotanimType("spotanim.curse_travel".asRSCM(RSCMType.SPOTANIM))
    }
    private val curseProjType: ProjAnimType by lazy {
        ProjAnimType("projanim.magic_spell".asRSCM(RSCMType.PROJANIM))
    }

    private fun state(npc: Npc): FightState = fights.getOrPut(npc.slotId) { FightState() }

    fun reset(npc: Npc) {
        val state = fights.remove(npc.slotId) ?: return
        for (guardian in state.guardians) {
            if (guardian.npc.isSlotAssigned) {
                deps.npcRepo.del(guardian.npc, Int.MAX_VALUE)
            }
        }
    }

    fun registerExternals() {
        deps.extensionRegistry.register("scorpia.sting") { _, npc, target, _ ->
            sting(npc, target)
        }
        deps.extensionRegistry.register("scorpia.summon_guardians") { _, npc, _, _ ->
            summonGuardians(npc)
        }
    }

    /**
     * Scorpia's melee sting: a standard (prayer-respecting) hit, plus a 2-point prayer drain when
     * the target has Protect from Melee active — the drain applies whether the attack lands or not.
     * Landed stings also poison (starting at 20) with the pipeline's usual 1-in-4 odds.
     */
    private fun sting(npc: Npc, target: Player) {
        val damage = deps.random.of(ScorpiaTuning.STING_MAX_HIT + 1)
        target.finishNpcHit(npc, 1, HitType.Melee, damage, deps.playerHitModifier)
        if (target.vars["varbit.prayer_protectfrommelee"] == 1) {
            CombatEffects.statDrain(target, listOf("stat.prayer"), ScorpiaTuning.STING_PRAYER_DRAIN)
        }
        if (damage > 0 && deps.random.of(4) == 1) {
            CombatEffects.poison(target, ScorpiaTuning.STING_POISON_START)
        }
    }

    /** Two guardians rise beside her and begin their healing routine. */
    private fun summonGuardians(npc: Npc) {
        val typeId = "npc.scorpia_guardian".asRSCM(RSCMType.NPC)
        val type = ServerCacheManager.getNpc(typeId) ?: return
        val state = state(npc)
        val tiles = buildList {
            for (dx in -ScorpiaTuning.GUARDIAN_SPAWN_RADIUS..ScorpiaTuning.GUARDIAN_SPAWN_RADIUS) {
                for (dz in -ScorpiaTuning.GUARDIAN_SPAWN_RADIUS..ScorpiaTuning.GUARDIAN_SPAWN_RADIUS) {
                    if (dx == 0 && dz == 0) continue
                    val coord = npc.coords.translate(dx, dz)
                    if (!deps.collision.isWalkBlocked(coord)) add(coord)
                }
            }
        }.shuffled()
        repeat(ScorpiaTuning.GUARDIAN_COUNT) { i ->
            val coord = tiles.getOrNull(i) ?: return@repeat
            val guardian = Npc(type, coord)
            guardian.mode = NpcMode.None
            deps.npcRepo.add(guardian, ScorpiaTuning.GUARDIAN_LIFETIME_TICKS)
            state.guardians += Guardian(guardian)
        }
        startHealTicker(npc)
    }

    private fun startHealTicker(npc: Npc) {
        val state = state(npc)
        if (state.tickerRunning) return
        state.tickerRunning = true
        deps.repeatTick(
            ticks = ScorpiaTuning.GUARDIAN_LIFETIME_TICKS,
            onTick = { _ ->
                if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                    state.tickerRunning = false
                    return@repeatTick false
                }
                if (deps.mapClock.cycle % ScorpiaTuning.GUARDIAN_HEAL_INTERVAL_TICKS != 0) {
                    return@repeatTick true
                }
                val healedThisRound = healFromGuardians(npc, state)
                state.guardians.removeAll { guardian ->
                    if (!guardian.npc.isSlotAssigned) return@removeAll true
                    if (healedThisRound.contains(guardian.npc)) {
                        guardian.outOfRangeTicks = 0
                        return@removeAll false
                    }
                    guardian.outOfRangeTicks += ScorpiaTuning.GUARDIAN_HEAL_INTERVAL_TICKS
                    if (guardian.outOfRangeTicks >= ScorpiaTuning.GUARDIAN_DESPAWN_AFTER_TICKS) {
                        deps.npcRepo.del(guardian.npc, Int.MAX_VALUE)
                        return@removeAll true
                    }
                    false
                }
                if (state.guardians.isEmpty()) {
                    state.tickerRunning = false
                    return@repeatTick false
                }
                true
            },
            onStop = { state.tickerRunning = false },
        )
    }

    /** Heals [npc] from every living guardian in range; returns the guardians that healed. */
    private fun healFromGuardians(npc: Npc, state: FightState): Set<Npc> {
        val healed = mutableSetOf<Npc>()
        for (guardian in state.guardians) {
            val guardianNpc = guardian.npc
            if (!guardianNpc.isSlotAssigned) continue
            if (guardianNpc.coords.chebyshevDistance(npc.coords) > ScorpiaTuning.GUARDIAN_HEAL_RANGE) {
                continue
            }
            npc.heal(ScorpiaTuning.GUARDIAN_HEAL_AMOUNT)
            deps.worldRepo.projAnimSourced(guardianNpc, npc, curseSpot, curseProjType)
            healed += guardianNpc
        }
        return healed
    }
}

class Scorpia
@Inject
constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val encounter = ScorpiaEncounter(deps)

    private val scorpiaId: Int by lazy { "npc.scorpia".asRSCM(RSCMType.NPC) }

    override val spec: BossSpec by lazy { scorpiaSpec() }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        encounter.registerExternals()
        onEvent<NpcStateEvents.Respawn> { if (npc.id == scorpiaId) encounter.reset(npc) }
    }
}

private fun scorpiaSpec(): BossSpec = boss("npc.scorpia") {
    stats(attackRate = ScorpiaTuning.ATTACK_RATE, aggressionRadius = ScorpiaTuning.AGGRO_RADIUS)

    val sting = ability("sting") { include(external("scorpia.sting")) }

    phase("combat") {
        weightedSelectorRandom {
            +random(sting, weight = 1)
        }
    }

    triggers {
        // Half her 200 base hp = the wiki's "99 health or lower" threshold; fires once per fight.
        on(Condition.HpBelow(ScorpiaTuning.GUARDIAN_TRIGGER_FRACTION)) runs
            external("scorpia.summon_guardians")
    }
}
