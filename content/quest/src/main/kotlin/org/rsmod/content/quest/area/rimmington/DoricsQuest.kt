package org.rsmod.content.quest.area.rimmington

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
 * Doric's Quest.
 *
 * Doric the dwarf won't let adventurers use his anvils unless they bring him materials for his
 * pickaxe business: 6 clay, 4 copper ore and 2 iron ore. He hands out a bronze pickaxe when the
 * quest is accepted. Dialogue is verbatim from a live-OSRS RSProx capture; the reward block
 * (1 QP, 1,300 Mining XP, 180 coins) is from the recorded completion screen.
 */
class DoricsQuest : QuestScript(
    "quest_dorics",
    "varp.doricquest",
    rewards {
        xp("stat.mining", 1300.0)
        item("obj.coins", 180)
        // The completion screen shows a pickaxe; Doric's own use-your-anvils perk is the other.
        extra("Use of Doric's anvils")
    },
    ItemRewardDisplay("obj.steel_pickaxe"),
) {
    override fun ScriptContext.init() {
        onOpNpc1("npc.doric") { startDialogue(it.npc) { doricDialogue(it.npc) } }
    }

    private fun Player.hasAllOres(): Boolean =
        inv.count("obj.clay") >= REQUIRED_CLAY &&
            inv.count("obj.copper_ore") >= REQUIRED_COPPER &&
            inv.count("obj.iron_ore") >= REQUIRED_IRON

    override fun subTitle(): String =
        "talking to <col=800000>Doric</col> the dwarf, north of <col=800000>Falador</col>."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        description(
            "I can start this quest by talking to <red>Doric</red>, the dwarf north of " +
                "<red>Falador</red>. He might let me use his anvils.",
        ) {
            hideWhenQuestStarted()
        }

        objective(
            "<red>Doric</red> will let me use his anvils if I bring him some materials for his " +
                "pickaxe business: <red>6 clay</red>, <red>4 copper ore</red> and <red>2 iron " +
                "ore</red>. He gave me a pickaxe to help.",
        ) {
            visibleWhen { quest.questState(access.player) == QuestProgressState.IN_PROGRESS }
            custom(
                access.player.hasAllOres(),
                "I have all the materials Doric asked for. I should take them to him now.",
            )
        }
    }

    override fun completedLog(player: ProtectedAccess): String = completionJournal(player) {
        line(
            "Doric the dwarf needed materials for his pickaxe business: clay, copper ore and " +
                "iron ore.",
        )
        line("I brought him 6 clay, 4 copper ore and 2 iron ore, and he paid me for my trouble.")
        line("He now lets me use his anvils whenever I want.")
    }

    private suspend fun Dialogue.doricDialogue(npc: Npc) {
        when {
            quest.isQuestCompleted(player) -> dialogAfterQuest(npc)
            quest.questState(player) == QuestProgressState.IN_PROGRESS -> dialogDuringQuest(npc)
            else -> dialogQuestNotStarted(npc)
        }
    }

    private suspend fun Dialogue.dialogQuestNotStarted(npc: Npc) {
        chatNpc(happy, "Hello traveller, what brings you to my humble smithy?")
        when (
            choice2(
                "I wanted to use your anvils.", 1,
                "Nothing, thanks. Just passing through.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I wanted to use your anvils.")
                chatNpc(
                    neutral,
                    "My anvils get enough work with my own use. I make pickaxes, and it takes a " +
                        "lot of hard work. If you could get me some more materials, then I could " +
                        "let you use them.",
                )
                when (
                    choice2(
                        "Yes, I will get you the materials.", 1,
                        "No, hitting rocks is far too much like hard work.", 2,
                    )
                ) {
                    1 -> acceptOffer(npc)
                    2 -> {
                        chatPlayer(sad, "No, hitting rocks is far too much like hard work.")
                        chatNpc(neutral, "That is your choice.")
                    }
                }
            }
            2 -> {
                chatPlayer(neutral, "Nothing, thanks. Just passing through.")
                chatNpc(happy, "Fair enough. Mind the anvils on your way out!")
            }
        }
    }

    private suspend fun Dialogue.acceptOffer(npc: Npc) {
        chatPlayer(happy, "Yes, I will get you the materials.")
        if (player.inv.freeSpace() > 0 && !player.inv.contains("obj.bronze_pickaxe")) {
            access.invAdd(access.inv, "obj.bronze_pickaxe")
            chatNpc(happy, "Take this pickaxe with you, just in case.")
        }
        chatNpc(
            neutral,
            "Clay is what I use more than anything, to make casts. Could you get me 6 clay, " +
                "4 copper ore, and 2 iron ore, please? I could pay a little, and let you use my " +
                "anvils.",
        )
        quest.advanceQuestStage(access)
    }

    private suspend fun Dialogue.dialogDuringQuest(npc: Npc) {
        chatNpc(neutral, "Have you got my materials yet, traveller?")
        if (player.hasAllOres()) {
            chatPlayer(happy, "I have everything you asked for.")
            chatNpc(happy, "Many thanks! Pass them here, please.")
            mesbox("You hand the clay, copper, and iron to Doric.")
            access.invDel(access.inv, "obj.clay", REQUIRED_CLAY)
            access.invDel(access.inv, "obj.copper_ore", REQUIRED_COPPER)
            access.invDel(access.inv, "obj.iron_ore", REQUIRED_IRON)
            chatNpc(
                happy,
                "I can spare you some coins for your trouble, and please use my anvils any time " +
                    "you want.",
            )
            quest.advanceQuestStage(access, quest.maxSteps - quest.getQuestStage(access.player))
        } else {
            chatPlayer(neutral, "I haven't got all of them yet, I'm still working on it.")
            chatNpc(
                neutral,
                "Well, come back when you have. Remember: 6 clay, 4 copper ore and 2 iron ore.",
            )
        }
    }

    private suspend fun Dialogue.dialogAfterQuest(npc: Npc) {
        chatNpc(happy, "Hello traveller, how are you doing?")
        chatNpc(happy, "Remember, you're welcome to use my anvils any time.")
    }

    companion object {
        const val REQUIRED_CLAY = 6
        const val REQUIRED_COPPER = 4
        const val REQUIRED_IRON = 2
    }
}
