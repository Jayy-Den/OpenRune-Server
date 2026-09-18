package org.rsmod.content.quest.area.lumbridge

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.SpadeDigScript
import org.rsmod.content.quest.manager.SpadeDigScript.DigSite
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * X Marks the Spot.
 *
 * Stages (stored in `varp.veos_quest`, endstate 8 from `dbrow.quest_xmarksthespot`; the varp is
 * shared with later Veos quests, which write their own values into it):
 * - [STAGE_SCROLL_GIVEN]: Veos handed over the treasure scroll.
 * - [STAGE_DIG_BOB]: the first dig yielded a scroll; dig behind Lumbridge Castle next.
 * - [STAGE_DIG_CASTLE]: the second dig yielded an orb; dig by the Draynor jail next.
 * - [STAGE_DIG_JAIL]: the third dig yielded a scroll; dig in the Draynor pig pen next.
 * - [STAGE_DIG_PIGPEN]: the fourth dig yielded the ancient casket; take it to Veos.
 *
 * Dig spots, item progression (clue1 -> clue2 -> clue3 -> casket) and the verbatim dig messages
 * are taken from a live-OSRS RSProx capture of a full playthrough.
 */
class XMarksTheSpot @Inject constructor(private val spadeDigs: SpadeDigScript) : QuestScript(
    "quest_xmarksthespot",
    "varp.veos_quest",
    rewards {
        extra("A Beginner clue scroll")
        extra("A 300 XP antique lamp")
    },
    ItemRewardDisplay("obj.cluequest_lamp"),
) {
    override fun ScriptContext.init() {
        onOpNpc1("npc.veos_lumbridge") { startDialogue(it.npc) { veosDialogue(it.npc) } }
        for ((stage, step) in DIG_STAGES) {
            spadeDigs.registerQuestSite(
                DigSite(
                    matches = { quest.getQuestStage(player) == stage && player.coords == step.coord },
                    dig = { xdig(step) },
                ),
                quest,
            )
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Veos</col> in <col=800000>The Sheared Ram</col> pub " +
            "north of <col=800000>Lumbridge</col> castle."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        description(
            "I can start this quest by talking to <red>Veos</red> in <red>The Sheared " +
                "Ram</red> pub north of <red>Lumbridge</red> castle.",
        ) {
            hideWhenQuestStarted()
        }

        objective(
            "<red>Veos</red> asked me to work out the <red>treasure scroll</red> he found in " +
                "Great Kourend. I should dig <red>north of Bob's Brilliant Axes</red> in " +
                "Lumbridge, on the west side of the plant against the wall of his house.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_SCROLL_GIVEN }
        }

        objective(
            "The first dig yielded another <red>treasure scroll</red>. I should dig " +
                "<red>behind Lumbridge Castle</red>, just outside the kitchen door.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_DIG_BOB }
        }

        objective(
            "The scroll led me to a <red>mysterious orb</red>. I should dig <red>north-west " +
                "of the Draynor Village jail</red>, just by the wheat farm.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_DIG_CASTLE }
        }

        objective(
            "The orb pointed me to the <red>Draynor Market</red>: I should dig just inside " +
                "the <red>pig pen</red>.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_DIG_JAIL }
        }

        objective(
            "I have found an <red>ancient casket</red>! As I dug it up, I heard a faint " +
                "whispering. I should take it back to <red>Veos</red> in Lumbridge.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_DIG_PIGPEN }
        }
    }

    override fun completedLog(player: ProtectedAccess): String = completionJournal(player) {
        line("Veos asked for help working out a treasure scroll he found in Great Kourend.")
        line("The scroll led me on a treasure hunt around Lumbridge and Draynor.")
        line("I dug up an ancient casket and returned it to Veos.")
        line("As promised, Veos rewarded me for my help.")
    }

    private suspend fun Dialogue.veosDialogue(npc: Npc) {
        when (quest.getQuestStage(player)) {
            STAGE_COMPLETE -> chatNpc(
                happy,
                "If you ever fancy visiting the kingdom, come find me here. I can take you " +
                    "there whenever you're ready.",
            )
            STAGE_DIG_PIGPEN -> casketHandIn(npc)
            in PROGRESS_STAGES -> dialogDuringQuest(npc)
            else -> dialogQuestNotStarted(npc)
        }
    }

    private suspend fun Dialogue.dialogQuestNotStarted(npc: Npc) {
        chatNpc(neutral, "Hello there.")
        when (
            choice3(
                "Who are you?", 1,
                "I'm looking for a quest.", 2,
                "I have to go.", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(
                    neutral,
                    "The name's Veos. I'm a treasure hunter from the wonderous Kingdom of " +
                        "Great Kourend.",
                )
                chatPlayer(quiz, "Great Kourend? Where's that?")
                chatNpc(
                    neutral,
                    "Across the sea to the far west. It is a truly magnificent place.",
                )
                chatPlayer(quiz, "Interesting. So what brings you to Lumbridge?")
                chatNpc(
                    neutral,
                    "I am here on a bit of a hunt. The hunt for treasure. Back in my home of " +
                        "Great Kourend I came across a scroll. I believe it will lead me to " +
                        "something of great value.",
                )
                chatNpc(
                    neutral,
                    "Alas, I lack the talent to extract its meaning. Are you interested?",
                )
                offerQuest(npc)
            }
            2 -> {
                chatPlayer(quiz, "I'm looking for a quest.")
                chatNpc(
                    neutral,
                    "Hmmm. Maybe you can. You probably know this area better than I do. You " +
                        "might be able to work the scroll out. I'd be happy to reward you for " +
                        "your trouble.",
                )
                offerQuest(npc)
            }
        }
    }

    private suspend fun Dialogue.offerQuest(npc: Npc) {
        when (choice2("Sounds good, what should I do?", 1, "Can I help?", 2)) {
            1, 2 -> {
                chatPlayer(quiz, "Sounds good, what should I do?")
                chatNpc(
                    neutral,
                    "Take this scroll. It should lead you to the treasure I seek. Once you " +
                        "have a general idea of where the treasure is, use a spade to dig in " +
                        "the area and see what you find.",
                )
                chatPlayer(quiz, "Okay, thanks Veos.")
                chatNpc(neutral, "Good luck.")
                access.invAdd(access.inv, "obj.cluequest_clue1")
                quest.advanceQuestStage(access)
            }
        }
    }

    private suspend fun Dialogue.dialogDuringQuest(npc: Npc) {
        chatNpc(neutral, "Hello there.")
        chatPlayer(neutral, "Hello Veos.")
        chatNpc(neutral, "How's the treasure hunt going?")
        if (quest.getQuestStage(player) == STAGE_SCROLL_GIVEN && !player.inv.contains("obj.cluequest_clue1")) {
            chatPlayer(sad, "I seem to have lost the scroll...")
            chatNpc(
                neutral,
                "This is the scroll I gave you. I don't know how you manage to lose so many " +
                    "things. Take another one and be more careful this time.",
            )
            access.invAdd(access.inv, "obj.cluequest_clue1")
            return
        }
        chatPlayer(neutral, "I'm following the clues. I'll keep digging.")
        chatNpc(neutral, "Good luck.")
    }

    private suspend fun Dialogue.casketHandIn(npc: Npc) {
        chatNpc(neutral, "Hello there.")
        chatPlayer(happy, "I have your casket, Veos.")
        chatNpc(
            happy,
            "Brilliant. This is just what I was looking for. Thank you so much for your help.",
        )
        chatPlayer(quiz, "So what was in it?")
        chatNpc(
            neutral,
            "Oh err... nothing important. Just something that might be of use to me back in " +
                "Great Kourend.",
        )
        access.invDel(access.inv, "obj.cluequest_casket")
        chatNpc(happy, "Anyway, as promised, a reward for you.")
        quest.complete(access)
        chatNpc(
            happy,
            "Anyway, I must be off. If you ever fancy visiting the kingdom, come find me " +
                "here. I can take you there whenever you're ready.",
        )
    }

    private suspend fun ProtectedAccess.xdig(step: DigStage) {
        anim("seq.human_dig")
        soundSynth("synth.digspade")
        delay(3)

        invDel(inv, step.consumes)
        invAdd(inv, step.yields)
        mesbox(step.message)
        if (step.nextStage != null) {
            quest.advanceQuestStage(this)
        } else {
            quest.complete(this)
        }
    }

    private data class DigStage(
        val coord: CoordGrid,
        val consumes: String,
        val yields: String,
        val message: String,
        val nextStage: Int?,
    )

    companion object {
        const val STAGE_SCROLL_GIVEN = 1
        const val STAGE_DIG_BOB = 2
        const val STAGE_DIG_CASTLE = 3
        const val STAGE_DIG_JAIL = 4
        const val STAGE_DIG_PIGPEN = 5
        const val STAGE_COMPLETE = 8

        private val PROGRESS_STAGES = setOf(
            STAGE_SCROLL_GIVEN,
            STAGE_DIG_BOB,
            STAGE_DIG_CASTLE,
            STAGE_DIG_JAIL,
        )

        private val DIG_STAGES = mapOf(
            // North of Bob's Brilliant Axes, on the west side of the plant against the wall.
            STAGE_SCROLL_GIVEN to DigStage(
                coord = CoordGrid(3230, 3209),
                consumes = "obj.cluequest_clue1",
                yields = "obj.cluequest_clue2",
                message = "You dig up a Treasure Scroll.",
                nextStage = STAGE_DIG_BOB,
            ),
            // Behind Lumbridge Castle, just outside the kitchen door.
            STAGE_DIG_BOB to DigStage(
                coord = CoordGrid(3203, 3212),
                consumes = "obj.cluequest_clue2",
                yields = "obj.cluequest_clue3",
                message = "You dig up a Mysterious Orb.",
                nextStage = STAGE_DIG_CASTLE,
            ),
            // North-west of the Draynor Village jail, just by the wheat farm.
            STAGE_DIG_CASTLE to DigStage(
                coord = CoordGrid(3109, 3264),
                consumes = "obj.cluequest_clue3",
                yields = "obj.cluequest_clue4",
                message = "You dig up a Treasure Scroll.",
                nextStage = STAGE_DIG_JAIL,
            ),
            // Just inside the pig pen in the Draynor Market.
            STAGE_DIG_JAIL to DigStage(
                coord = CoordGrid(3078, 3259),
                consumes = "obj.cluequest_clue4",
                yields = "obj.cluequest_casket",
                message =
                    "You dig up an Ancient Casket. As you do, you hear a faint whispering. " +
                        "You can't make out what it says though...",
                nextStage = null,
            ),
        )
    }
}
