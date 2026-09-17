package org.rsmod.content.quest.area.wizardtower

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Imp Catcher.
 *
 * Wizard Mizgog's rival Grayzag summoned an army of imps that stole his four magical beads
 * (red, yellow, black, white). Retrieve one of each for an amulet of accuracy. Dialogue is
 * verbatim from a live-OSRS RSProx capture; the reward block (1 QP, 875 Magic XP) and the
 * completion-screen item (amulet of accuracy, obj 1478) are from the recording. The varp
 * (`varp.imp`, OSRS varp 160) was confirmed moving 1 -> 2 at completion in the same capture.
 */
class ImpCatcher : QuestScript(
    "quest_impcatcher",
    "varp.imp",
    rewards {
        xp("stat.magic", 875.0)
    },
    ItemRewardDisplay("obj.amulet_of_accuracy"),
) {
    override fun ScriptContext.init() {
        onOpNpc1("npc.wizard_mizgog") { startDialogue(it.npc) { mizgogDialogue(it.npc) } }
    }

    private fun Player.hasAllBeads(): Boolean =
        inv.contains("obj.red_bead") &&
            inv.contains("obj.yellow_bead") &&
            inv.contains("obj.black_bead") &&
            inv.contains("obj.white_bead")

    override fun subTitle(): String =
        "by talking to <col=800000>Wizard Mizgog</col> on the top floor of the " +
            "<col=800000>Wizards' Tower</col>."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        description(
            "I can start this quest by talking to <red>Wizard Mizgog</red> on the top floor of " +
                "the <red>Wizards' Tower</red>, south-west of Lumbridge.",
        ) {
            hideWhenQuestStarted()
        }

        objective(
            "<red>Wizard Mizgog</red>'s rival, the wizard Grayzag, summoned an army of imps " +
                "which stole four of his magical beads: a <red>red</red>, a <red>yellow</red>, a " +
                "<red>black</red> and a <red>white</red> one. The imps are scattered all over " +
                "the kingdom.",
        ) {
            visibleWhen { quest.questState(access.player) == QuestProgressState.IN_PROGRESS }
            custom(
                access.player.hasAllBeads(),
                "I have one bead of each colour. I should take them to Wizard Mizgog now.",
            )
        }
    }

    override fun completedLog(player: ProtectedAccess): String = completionJournal(player) {
        line(
            "Wizard Grayzag summoned an army of imps that stole four of Wizard Mizgog's " +
                "magical beads.",
        )
        line("I hunted down the imps and recovered the red, yellow, black and white beads.")
        line("Mizgog rewarded me with an amulet of accuracy.")
    }

    private suspend fun Dialogue.mizgogDialogue(npc: Npc) {
        when {
            quest.isQuestCompleted(player) -> dialogAfterQuest(npc)
            quest.questState(player) == QuestProgressState.IN_PROGRESS -> dialogDuringQuest(npc)
            else -> dialogQuestNotStarted(npc)
        }
    }

    private suspend fun Dialogue.dialogQuestNotStarted(npc: Npc) {
        chatNpc(happy, "Hello there, wizard wannabe. What can I do for you?")
        when (
            choice3(
                "Give me a quest!", 1,
                "Can you teach me about magic?", 2,
                "Nothing, thanks.", 3,
            )
        ) {
            1 -> {
                chatPlayer(angry, "Give me a quest!")
                chatNpc(confused, "Give me a quest what?")
                chatPlayer(angry, "Give me a quest please.")
                chatNpc(happy, "Well seeing as you asked nicely... I could do with some help.")
                explainQuest(npc)
                acceptOffer(npc)
            }
            2 -> {
                chatPlayer(quiz, "Can you teach me about magic?")
                chatNpc(
                    sad,
                    "I don't think so, the type of magic I study involves years of meditation " +
                        "and research.",
                )
            }
            3 -> chatNpc(neutral, "Very well. See you soon!")
        }
    }

    private suspend fun Dialogue.explainQuest(npc: Npc) {
        chatNpc(
            sad,
            "The wizard Grayzag next door decided he didn't like me so he enlisted an army of " +
                "hundreds of imps.",
        )
        chatNpc(
            sad,
            "These imps stole all sorts of my things. Most of these things I don't really care " +
                "about, just eggs and balls of string and things.",
        )
        chatNpc(
            worried,
            "But they stole my four magical beads. There was a red one, a yellow one, a black " +
                "one, and a white one.",
        )
        chatNpc(worried, "These imps have now spread out all over the kingdom. Could you get my beads back for me?")
    }

    private suspend fun Dialogue.acceptOffer(npc: Npc) {
        when (
            choice2(
                "I'll get your beads back.", 1,
                "That sounds like too much effort.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "I'll get your beads back.")
                quest.advanceQuestStage(access)
                chatNpc(happy, "Thank you! A red, yellow, black and white bead - don't mix them up.")
            }
            2 -> chatNpc(sad, "Very well. See you soon!")
        }
    }

    private suspend fun Dialogue.dialogDuringQuest(npc: Npc) {
        if (player.hasAllBeads()) {
            chatPlayer(happy, "Well I just so happen to have all of those beads on me!")
            chatNpc(
                shocked,
                "Are you saying that you stole my beads all this time and I've been blaming " +
                    "these imps!?",
            )
            chatNpc(angry, "Bah! Fine.")
            chatNpc(
                neutral,
                "Give them here and I'll check that they really are MY beads, before I give " +
                    "you your reward. You'll like it, it's an amulet of accuracy.",
            )
            access.invDel(access.inv, "obj.red_bead")
            access.invDel(access.inv, "obj.yellow_bead")
            access.invDel(access.inv, "obj.black_bead")
            access.invDel(access.inv, "obj.white_bead")
            mesbox("You give four coloured beads to Wizard Mizgog.")
            access.invAdd(access.inv, "obj.amulet_of_accuracy")
            mesbox("The wizard hands you an amulet.")
            quest.complete(access)
            return
        }
        chatNpc(quiz, "So how are you doing with finding my beads?")
        chatPlayer(
            sad,
            "I haven't got all four yet. I'm still looking for the imps that took them.",
        )
        chatNpc(
            worried,
            "Well, get a move on! Imps can be found all over the kingdom - remember: red, " +
                "yellow, black and white.",
        )
    }

    private suspend fun Dialogue.dialogAfterQuest(npc: Npc) {
        chatNpc(happy, "Hello there, wizard wannabe. What can I do for you?")
        when (
            choice2(
                "Have you any more quests for me?", 1,
                "Can I have another amulet of accuracy?", 2,
            )
        ) {
            1 -> chatNpc(neutral, "No, everything is good with the world today.")
            2 -> {
                chatPlayer(quiz, "Can I have another amulet of accuracy?")
                if (player.hasAllBeads()) {
                    chatNpc(
                        happy,
                        "I have a few spare. I'd like one of each coloured bead again in return, " +
                            "though! Black, white, yellow and red.",
                    )
                    chatPlayer(happy, "I have all of those beads on me!")
                    handInBeadsForAmulet(npc)
                } else {
                    chatNpc(
                        happy,
                        "I have a few spare. I'd like one of each coloured bead again in return, " +
                            "though! Black, white, yellow and red.",
                    )
                    chatNpc(
                        neutral,
                        "You don't seem to have one of each coloured bead on you at the moment. " +
                            "Return when you have a black, white, yellow and red in your backpack!",
                    )
                }
            }
        }
    }

    private suspend fun Dialogue.handInBeadsForAmulet(npc: Npc) {
        chatNpc(happy, "Give them here and I'll check that they really are MY beads.")
        access.invDel(access.inv, "obj.red_bead")
        access.invDel(access.inv, "obj.yellow_bead")
        access.invDel(access.inv, "obj.black_bead")
        access.invDel(access.inv, "obj.white_bead")
        access.invAdd(access.inv, "obj.amulet_of_accuracy")
        mesbox("The wizard hands you an amulet.")
    }

    companion object {
        val REQUIRED_BEADS = listOf("obj.red_bead", "obj.yellow_bead", "obj.black_bead", "obj.white_bead")
    }
}
