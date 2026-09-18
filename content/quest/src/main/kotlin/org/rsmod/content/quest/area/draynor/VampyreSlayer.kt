package org.rsmod.content.quest.area.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Vampyre Slayer.
 *
 * Stages (stored in `varp.vampire`, endstate 3 from `dbrow.quest_vampyreslayer`; the capture
 * shows 1 -> 2 -> 3):
 * - [STAGE_TALKED_MORGAN]: Morgan asked me to see Dr Harlow in the Blue Moon Inn.
 * - [STAGE_TALKED_HARLOW]: Harlow gave me a stake and his advice; the Count awaits.
 * - [STAGE_COMPLETE]: Count Draynor was staked.
 */
class VampyreSlayer
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val search: NpcSearch,
    private val collision: CollisionFlagMap,
    private val protectedAccess: ProtectedAccessLauncher,
    private val playerList: PlayerList,
) : QuestScript(
    "quest_vampyreslayer",
    "varp.vampire",
    rewards {
        xp("stat.attack", 4825.0)
    },
    ItemRewardDisplay("obj.stake"),
) {
    private lateinit var countType: NpcServerType

    override fun ScriptContext.init() {
        countType = ServerCacheManager.getNpc(COUNT.asRSCM(RSCMType.NPC))!!

        onOpNpc1("npc.morgan") { startDialogue(it.npc) { morganDialogue(it.npc) } }
        onOpNpc1("npc.dr_harlow") { startDialogue(it.npc) { harlowDialogue(it.npc) } }
        onOpLoc1(COFFIN) { openCoffin(it.loc) }
        onModifyNpcHit(countType) { onCountHit() }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Morgan</col> in his house in <col=800000>Draynor Village</col>."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        description(
            "I can start this quest by talking to <red>Morgan</red> in his house in " +
                "<red>Draynor Village</red>.",
        ) {
            hideWhenQuestStarted()
        }

        objective(
            "<red>Morgan</red> asked me to speak to his old friend <red>Dr Harlow</red>, a " +
                "retired vampyre hunter who spends his days drinking in the <red>Blue Moon " +
                "Inn</red> in <red>Varrock</red>.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_TALKED_MORGAN }
        }

        objective(
            "<red>Dr Harlow</red> gave me a <red>stake</red>. I must drive it into Count " +
                "Draynor's chest with a <red>hammer</red> once he is weak. <red>Garlic</red> " +
                "will weaken the vampyre. He lurks in the coffin in Draynor Manor's basement.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_TALKED_HARLOW }
        }
    }

    override fun completedLog(player: ProtectedAccess): String = completionJournal(player) {
        line("Morgan asked me to rid Draynor Village of the vampyre Count Draynor.")
        line("Dr Harlow gave me a stake and told me to weaken the Count with garlic.")
        line("I staked the Count in his coffin in the manor basement.")
        line("Draynor Village sleeps easy once more.")
    }

    private suspend fun Dialogue.morganDialogue(npc: Npc) {
        when {
            quest.isQuestCompleted(player) -> chatNpc(
                happy,
                "The whole village is in your debt! Count Draynor will never bother us again.",
            )
            quest.questState(player) == QuestProgressState.IN_PROGRESS -> dialogDuringQuest(npc)
            else -> dialogQuestNotStarted(npc)
        }
    }

    private suspend fun Dialogue.dialogQuestNotStarted(npc: Npc) {
        chatNpc(worried, "Could it be? A bold adventurer! Please, you must help us!")
        chatPlayer(quiz, "What is it? What's the problem?")
        chatNpc(
            worried,
            "It's the evil vampyre, Count Draynor! For too long he's terrorised us from his " +
                "manor to the north. Someone finally needs to put a stop to him once and for all!",
        )
        chatPlayer(quiz, "Sounds like a job for me. Where should I start?")
        chatNpc(
            neutral,
            "Oh, thank goodness! I've been hoping this day would come for a long time, so I've " +
                "made sure to do my research. I've heard of a retired vampyre hunter called " +
                "Dr Harlow who lives in Varrock.",
        )
        chatNpc(
            neutral,
            "If you speak to him, I'm sure he'll be able to help. I hear he's a bit of an old " +
                "soak these days, so he spends most of his time in the Blue Moon Inn.",
        )
        quest.advanceQuestStage(access)
    }

    private suspend fun Dialogue.dialogDuringQuest(npc: Npc) {
        if (quest.getQuestStage(player) == STAGE_TALKED_MORGAN) {
            chatNpc(worried, "Have you spoken to Dr Harlow yet?")
            chatPlayer(neutral, "Not yet. The Blue Moon Inn in Varrock, was it?")
            chatNpc(
                neutral,
                "That's the one. He can tell you how to stop the Count. And take some garlic " +
                    "with you - you can find some in the cupboard upstairs.",
            )
        } else {
            chatNpc(neutral, "How goes the hunt?")
            chatPlayer(neutral, "I have what I need. The Count's days are numbered.")
            chatNpc(happy, "Good luck! You'll find him in the coffin in the basement.")
        }
    }

    private suspend fun Dialogue.harlowDialogue(npc: Npc) {
        val stage = quest.getQuestStage(player)
        if (quest.isQuestCompleted(player)) {
            chatNpc(drunk, "Shome daysh I missh the old hunt... but not the hangoversh.")
            return
        }
        if (stage != STAGE_TALKED_MORGAN) {
            chatNpc(drunk, "Buy me a drink pleassh...")
            return
        }
        chatNpc(drunk, "Buy me a drink pleassh...")
        chatPlayer(quiz, "I need your help dealing with a vampyre.")
        chatNpc(drunk, "A vampyre you shhay...?")
        chatPlayer(quiz, "Not just any vampyre. Count Draynor.")
        chatNpc(drunk, "Draynor? Well, buy me a beer firsht...")
        chatPlayer(quiz, "Are you sure you've not had enough?")
        chatNpc(drunk, "Huh? No, I don't think ssho. Now, buy ush a beer.")
        if (!player.inv.contains("obj.beer")) {
            chatPlayer(sad, "I'll be right back.")
            return
        }
        access.invDel(access.inv, "obj.beer")
        access.mes("You give a beer to Dr Harlow.")
        chatNpc(drunk, "Cheersh, matey...")
        chatPlayer(quiz, "Now, about Count Draynor...")
        chatNpc(
            drunk,
            "Yesh, Count Draynor! The evil nashty vampyre that no one's ever sheen! Every nowsh " +
                "and then, some adventurer... theysh go and try to kill him, but none of 'em " +
                "ever comesh back.",
        )
        chatNpc(
            drunk,
            "You want to havesh a go? Then don't be likesh them! Be prepared! Vampyres... " +
                "Theysh hard to kill. Some... maybe imposhible.",
        )
        chatPlayer(quiz, "So how do I make sure I'm prepared?")
        chatNpc(
            drunk, "Most vampyres regenerate. Yoush need to stop them. I knowsh a few ways, but the " +
                "easiest ish a stake. Here, You can havesh thish one.",
        )
        access.invAdd(access.inv, "obj.stake")
        access.mes("Dr Harlow hands you a stake.")
        chatNpc(
            drunk,
            "Takesh that to Draynor Manor. Find the vampyre inshide and show him what for! " +
                "When hesh weak, use a hammer to drive the stake in! Mosht general stores have " +
                "them.",
        )
        chatNpc(
            drunk,
            "Oh, and yoush should take some garlic with you as well. Vampyres don't likesh garlic.",
        )
        chatPlayer(quiz, "Garlic? Hmm... I'll see if Morgan knows where I can get some.")
        quest.advanceQuestStage(access)
    }

    private suspend fun ProtectedAccess.openCoffin(loc: BoundLocInfo) {
        if (!quest.isQuestInProgress(player)) {
            mes("You search the coffin but find nothing but dust.")
            return
        }
        arriveDelay()
        faceLoc(loc)
        mes("You open the coffin...")
        delay(2)
        if (!spawnCount(loc.coords)) {
            return
        }
        mes("Count Draynor emerges!")
    }

    private fun spawnCount(coffin: CoordGrid): Boolean {
        if (search.find(coffin, COUNT, COUNT_SEARCH_RADIUS, HuntVis.Off) != null) {
            return true
        }
        val tile = freeTileBeside(coffin) ?: return false
        val count = Npc(countType, tile)
        count.mode = NpcMode.None
        npcRepo.add(count, COUNT_COFFIN_RETURN_TICKS)
        return true
    }

    private fun freeTileBeside(center: CoordGrid): CoordGrid? = ring(center)
        .filterNot(collision::isWalkBlocked)
        .minByOrNull { it.chebyshevDistance(center) }

    private fun ring(center: CoordGrid): List<CoordGrid> = buildList {
        for (dz in -1..1) {
            for (dx in -1..1) {
                if (dx != 0 || dz != 0) {
                    add(center.translate(dx, dz))
                }
            }
        }
    }

    /**
     * The Count cannot be beaten to death by damage alone: when a player would land the killing
     * blow and carries a stake and a hammer, the stake is driven in and the quest ends. Without
     * them he cannot be killed at all, as in the original.
     */
    @OptIn(InternalApi::class)
    private fun NpcHitEvents.Modify.onCountHit() {
        if (!hit.isFromPlayer) {
            return
        }
        val sourceUid = hit.sourceUid ?: return
        val source = PlayerUid(sourceUid).resolve(playerList) ?: return

        weakenWithGarlic(source)

        if (hit.damage < npc.hitpoints) {
            return
        }

        val hasStake = source.inv.contains(STAKE)
        val hasHammer = source.inv.contains(HAMMER)
        if (hasStake && hasHammer) {
            protectedAccess.launchLenient(source) { stakeCount() }
            return
        }

        hit.damage = npc.hitpoints - 1
        if (hasStake) {
            protectedAccess.launchLenient(source) { mes(NO_HAMMER) }
        }
    }

    /**
     * Garlic is carried rather than used: every hit from a player holding it deals an extra point,
     * and while the Count is not already drained it also drops his Attack and Strength by 10 and
     * his Defence by 40, with the message the capture shows. His cached [Npc.regenRate] brings the
     * drained levels back a point at a time, so a player who keeps attacking has the weakening
     * reapplied every 50 cycles - 10 Attack levels at 5 cycles each - as the capture does.
     */
    @OptIn(InternalApi::class)
    private fun NpcHitEvents.Modify.weakenWithGarlic(source: Player) {
        if (!source.inv.contains(GARLIC)) {
            return
        }
        hit.damage += GARLIC_DAMAGE_BONUS
        if (npc.attackLvl != npc.baseAttackLvl) {
            return
        }
        npc.attackLvl = (npc.attackLvl - GARLIC_ATTACK_DRAIN).coerceAtLeast(0)
        npc.strengthLvl = (npc.strengthLvl - GARLIC_STRENGTH_DRAIN).coerceAtLeast(0)
        npc.defenceLvl = (npc.defenceLvl - GARLIC_DEFENCE_DRAIN).coerceAtLeast(0)
        if (npc.hitpoints == npc.baseHitpointsLvl) {
            npc.queueHit(
                delay = GARLIC_HIT_DELAY,
                type = HitType.Typeless,
                damage = GARLIC_FULL_HEALTH_DAMAGE,
                modifier = NoModifier,
            )
        }
        protectedAccess.launchLenient(source) { mes(WEAKENED) }
    }

    private suspend fun ProtectedAccess.stakeCount() {
        mes(STAKE_HIT)
        delay(3)
        quest.complete(this)
    }

    companion object {
        const val STAGE_TALKED_MORGAN = 1
        const val STAGE_TALKED_HARLOW = 2

        private const val COFFIN = "loc.vampcoffin"
        private const val COUNT = "npc.count_draynor"
        private const val STAKE = "obj.stake"
        private const val HAMMER = "obj.hammer"
        private const val GARLIC = "obj.garlic"

        internal const val GARLIC_ATTACK_DRAIN = 10
        private const val GARLIC_STRENGTH_DRAIN = 10
        internal const val GARLIC_DEFENCE_DRAIN = 40
        private const val GARLIC_DAMAGE_BONUS = 1
        private const val GARLIC_FULL_HEALTH_DAMAGE = 10
        private const val GARLIC_HIT_DELAY = 1

        /**
         * Long enough to have a real fight, short of forever: the wiki has him return to his
         * coffin and recover to full health when a fight drags on, which is what reopening the
         * coffin gives you.
         */
        private const val COUNT_COFFIN_RETURN_TICKS = 100
        private const val COUNT_SEARCH_RADIUS = 12

        private const val STAKE_HIT =
            "<col=e00a19>You hammer the stake into the vampyre's chest!"
        private const val NO_HAMMER = "You are unable to push the stake far enough in."
        private const val WEAKENED =
            "The vampyre seems to be weakened by the garlic you're carrying."

        /** Garlic damage belongs to nobody: it must not pick up the attacker's modifiers. */
        private val NoModifier = NpcHitModifier {}
    }
}
