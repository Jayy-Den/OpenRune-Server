package org.rsmod.content.quest.area.lumbridge

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
 * Sheep Shearer.
 *
 * Fred the Farmer wants 20 balls of wool from his perpetually escaping sheep. All dialogue lines
 * are taken verbatim from a live-OSRS RSProx capture of a full playthrough; the reward block
 * (1 QP, 150 Crafting XP, 60 coins) is from the recorded completion screen.
 */
class SheepShearer : QuestScript(
    "quest_sheepshearer",
    "varp.sheep",
    rewards {
        xp("stat.crafting", 150.0)
        item("obj.coins", 60)
    },
    // The completion screen shows a pair of shears (obj 1735 in the capture).
    ItemRewardDisplay("obj.shears"),
) {
    override fun ScriptContext.init() {
        onOpNpc1("npc.fred_the_farmer") { startDialogue(it.npc) { fredDialogue(it.npc) } }
    }

    private fun ballsOfWool(player: Player): Int = player.inv.count("obj.ball_of_wool")

    override fun subTitle(): String =
        "talking to the <col=800000>Fred the Farmer</col> at his farm <col=800000>north-west " +
            "of Lumbridge</col>."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        description(
            "I can start this quest by talking to <red>Fred the Farmer</red> at his farm " +
                "just <red>north-west of Lumbridge</red>.",
        ) {
            hideWhenQuestStarted()
        }

        objective(
            "<red>Fred the Farmer</red> wants me to shear his <red>sheep</red> and spin the " +
                "<red>wool</red> into <red>balls of wool</red> for him. Sheep can be found in " +
                "his field; the nearest spinning wheel is on the first floor of Lumbridge Castle.",
        ) {
            visibleWhen { quest.questState(access.player) == QuestProgressState.IN_PROGRESS }
            custom(
                ballsOfWool(access.player) >= REQUIRED_WOOL,
                "I have all 20 <red>balls of wool</red> Fred asked for. I should take them to him now.",
            )
        }
    }

    override fun completedLog(player: ProtectedAccess): String = completionJournal(player) {
        line(
            "Fred the Farmer's sheep were getting mighty woolly, so he asked me to shear them " +
                "and spin the wool into balls for him.",
        )
        line("I brought Fred 20 balls of wool and he paid me for my trouble.")
        line("He never did tell me what The Thing actually was...")
    }

    private suspend fun Dialogue.fredDialogue(npc: Npc) {
        when {
            quest.isQuestCompleted(player) -> dialogAfterQuest(npc)
            quest.questState(player) == QuestProgressState.IN_PROGRESS -> dialogDuringQuest(npc)
            else -> dialogQuestNotStarted(npc)
        }
    }

    private suspend fun Dialogue.dialogQuestNotStarted(npc: Npc) {
        chatNpc(
            worried,
            "What are you doing on my land? You're not the one who keeps leaving all my gates " +
                "open and letting out all my sheep, are you?",
        )
        when (
            choice2(
                "I'm looking for a quest.", 1,
                "Who are you?", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "I'm looking for a quest.")
                chatNpc(neutral, "You're after a quest, you say? Actually, I could do with a bit of help.")
                chatNpc(
                    neutral,
                    "My sheep are getting mighty woolly. I'd be much obliged if you could shear " +
                        "them. And while you're at it, spin the wool for me too.",
                )
                chatNpc(
                    neutral,
                    "Yes, that's it. Bring me 20 balls of wool. And I'm sure I could sort out " +
                        "some sort of payment. Of course, there's the small matter of The Thing.",
                )
                chatPlayer(quiz, "What's The Thing?")
                chatNpc(
                    worried,
                    "Well now, no one has ever seen The Thing. That's why we call it The Thing, " +
                        "'cos we don't know what it is.",
                )
                chatNpc(
                    worried,
                    "Some say it's a black hearted shapeshifter, hungering for the souls of hard " +
                        "working decent folk like me. Others say it's just a sheep.",
                )
                chatNpc(angry, "Well I don't have all day to stand around and gossip. Are you going to shear my sheep or what!")
                acceptOffer(npc)
            }
            2 -> chatNpc(
                happy,
                "The name's Fred. I look after these sheep. Now, are you going to shear them or what!",
            )
        }
    }

    private suspend fun Dialogue.acceptOffer(npc: Npc) {
        when (
            choice2(
                "I'm happy to shear your sheep.", 1,
                "Not right now, Fred.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "I'm happy to shear your sheep.")
                quest.advanceQuestStage(access)
                chatNpc(happy, "Good! Now one more thing, do you actually know how to shear a sheep?")
                if (player.inv.contains("obj.shears")) {
                    chatNpc(
                        happy,
                        "Well, you're half way there already! You have a set of shears in your " +
                            "inventory. Just use those on a Sheep to shear it.",
                    )
                } else {
                    chatNpc(
                        happy,
                        "You'll need a set of shears to shear a sheep with. Have a look around " +
                            "the farm, I'm sure I have a spare pair somewhere.",
                    )
                }
                chatNpc(quiz, "Do you know how to spin wool?")
                chatPlayer(sad, "I don't know how to spin wool, sorry.")
                chatNpc(
                    happy,
                    "Don't worry, it's quite simple! The nearest Spinning Wheel can be found on " +
                        "the first floor of Lumbridge Castle. To get to Lumbridge Castle just " +
                        "follow the road east.",
                )
            }
            2 -> chatNpc(angry, "Fine. Come back when you have some time to spare!")
        }
    }

    private suspend fun Dialogue.dialogDuringQuest(npc: Npc) {
        chatNpc(worried, "What are you doing on my land?")
        chatPlayer(neutral, "I need to talk to you about shearing these sheep!")
        chatNpc(neutral, "Oh. How are you doing getting those balls of wool?")

        if (ballsOfWool(player) >= REQUIRED_WOOL) {
            chatNpc(happy, "Give 'em here then.")
            chatPlayer(happy, "Here you go, Fred.")
            repeat(REQUIRED_WOOL) { access.invDel(access.inv, "obj.ball_of_wool") }
            chatNpc(happy, "I guess I'd better pay you then.")
            quest.complete(access)
        } else {
            val count = ballsOfWool(player)
            if (count > 0) {
                chatPlayer(neutral, "I have $count ball(s) of wool so far.")
            } else {
                chatPlayer(sad, "I haven't got any yet, I'm still shearing.")
            }
            chatNpc(
                worried,
                "Well, get a move on! Use your shears on the sheep, then spin the wool on the " +
                    "spinning wheel in Lumbridge Castle.",
            )
        }
    }

    private suspend fun Dialogue.dialogAfterQuest(npc: Npc) {
        chatNpc(happy, "Hello again. The sheep are looking a lot less woolly thanks to you!")
    }

    companion object {
        const val REQUIRED_WOOL = 20
    }
}
