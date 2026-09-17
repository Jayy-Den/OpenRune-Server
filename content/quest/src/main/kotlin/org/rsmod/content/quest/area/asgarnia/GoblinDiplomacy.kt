package org.rsmod.content.quest.area.asgarnia

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Goblin Diplomacy.
 *
 * Generals Wartface and Bentnoze can't agree what colour armour goblins should wear. The player
 * referees: orange armour first, then blue, then brown. All general dialogue and the mail/dye
 * mechanics are verbatim from a live-OSRS RSProx capture (including the crate search message and
 * the "You dye the goblin mail" results); the reward block (5 QP, 200 Crafting XP, a gold bar) and
 * the completion-screen item (plain goblin mail, obj 288) come from the recorded completion screen.
 *
 * Sources of materials:
 * - Goblin mail: one of the three crates in the Goblin Village outpost holds a suit per run.
 * - Red dye: 2 redberries + 25 coins. Yellow dye: 2 onions + 25 coins.
 * - Blue dye: 2 woad leaves (Wyson the gardener, 20 coins each) + 25 coins.
 * - Orange dye: red dye + yellow dye + 25 coins.
 * All dye-making happens through Aggie in Draynor Village; dyeing mail is an item-on-item op
 * (`You dye the goblin mail <colour>.`), exactly as recorded in the capture. Plain mail (288) is
 * already the brown armour the generals finally settle on, so the last hand-in takes it undyed.
 */
class GoblinDiplomacy : QuestScript(
    "quest_goblindiplomacy",
    "varp.goblinquest",
    rewards {
        item("obj.gold_bar")
        xp("stat.crafting", 200.0)
    },
    ItemRewardDisplay("obj.goblin_armour"),
) {
    /** Which of the three crates holds this player's goblin mail; -1 until decided. */
    private val ownedCrate = quest.attribute(name = "OWNED_CRATE", default = -1)

    /** Crates the player has already searched (so they don't re-roll the mail crate). */
    private val crateSearched = arrayOf(
        quest.attribute(name = "CRATE1_SEARCHED", default = false),
        quest.attribute(name = "CRATE2_SEARCHED", default = false),
        quest.attribute(name = "CRATE3_SEARCHED", default = false),
    )

    override fun ScriptContext.init() {
        onOpNpc1("npc.general_wartface") { startDialogue(it.npc) { generalsDialogue(it.npc) } }
        onOpNpc1("npc.general_bentnoze") { startDialogue(it.npc) { generalsDialogue(it.npc) } }
        // The Goblin Village spawns the recoloured variants.
        onOpNpc1("npc.general_wartface_green") { startDialogue(it.npc) { generalsDialogue(it.npc) } }
        onOpNpc1("npc.general_bentnoze_red") { startDialogue(it.npc) { generalsDialogue(it.npc) } }
        onOpNpc1("npc.wyson") { startDialogue(it.npc) { wysonDialogue(it.npc) } }
        onOpNpc1("npc.aggie") { startDialogue(it.npc) { aggieDialogue(it.npc) } }

        onOpHeldU("obj.goblin_armour", "obj.reddye") {
            dyeMail("obj.reddye", "obj.goblin_armour_red", "red")
        }
        onOpHeldU("obj.goblin_armour", "obj.bluedye") {
            dyeMail("obj.bluedye", "obj.goblin_armour_darkblue", "blue")
        }
        onOpHeldU("obj.goblin_armour", "obj.yellowdye") {
            dyeMail("obj.yellowdye", "obj.goblin_armour_yellow", "yellow")
        }
        onOpHeldU("obj.goblin_armour", "obj.orangedye") {
            dyeMail("obj.orangedye", "obj.goblin_armour_orange", "orange")
        }

        onOpLoc1("loc.goblin_outpost_large_crate_armour1") { searchMailCrate(0) }
        onOpLoc1("loc.goblin_outpost_large_crate_armour2") { searchMailCrate(1) }
        onOpLoc1("loc.goblin_outpost_large_crate_armour3") { searchMailCrate(2) }
    }

    /** Plain mail doubles as the brown armour; the dyed suits are the orange/blue ones. */
    private class MailCounts(val plain: Int, val orange: Int, val blue: Int)

    private fun Player.mailCounts() = MailCounts(
        plain = inv.count("obj.goblin_armour"),
        orange = inv.count("obj.goblin_armour_orange"),
        blue = inv.count("obj.goblin_armour_darkblue"),
    )

    override fun subTitle(): String =
        "by talking to the <col=800000>goblin generals</col> in the " +
            "<col=800000>Goblin Village</col>."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        description(
            "I can start this quest by talking to the <red>goblin generals</red> in the " +
                "<red>Goblin Village</red>, north of Falador.",
        ) {
            hideWhenQuestStarted()
        }

        objective(
            "The <red>goblin generals</red> are arguing about what colour armour goblins " +
                "should wear. I should see what colour they want me to bring them.",
        ) {
            visibleWhen { quest.questState(access.player) == QuestProgressState.IN_PROGRESS }
            stageAtLeast(
                STAGE_WANT_ORANGE,
                "The generals first wanted <red>orange armour</red>. Goblin mail can be dyed " +
                    "with <red>orange dye</red>; Aggie the witch in Draynor Village makes dyes.",
            )
            stageAtLeast(
                STAGE_WANT_BLUE,
                "The generals didn't like orange. Now they want <red>blue armour</red>. Blue " +
                    "dye needs woad leaves - Wyson the gardener in Falador park sells them.",
            )
            stageAtLeast(
                STAGE_WANT_BROWN,
                "The generals didn't like blue either. Now they want <red>brown armour</red> " +
                    "to settle the argument.",
            )
        }
    }

    override fun completedLog(player: ProtectedAccess): String = completionJournal(player) {
        line(
            "The goblin generals could not agree on what colour armour goblins should wear: " +
                "Wartface favoured green, Bentnoze favoured red.",
        )
        line("I brought them orange, then blue, then brown armour.")
        line("The brown armour settled the argument and the generals rewarded me.")
    }

    /* ------------------------------------------------------------------ */
    /* The generals                                                        */
    /* ------------------------------------------------------------------ */

    private suspend fun Dialogue.generalsDialogue(npc: Npc) {
        when {
            quest.isQuestCompleted(player) -> dialogAfterQuest(npc)
            quest.questState(player) == QuestProgressState.IN_PROGRESS -> dialogDuringQuest(npc)
            else -> dialogQuestNotStarted(npc)
        }
    }

    private suspend fun Dialogue.dialogQuestNotStarted(npc: Npc) {
        chatNpc(angry, "All goblins should wear red armour!")
        chatNpc(angry, "Not red! Red armour make you look fat.")
        chatNpc(angry, "Everything make YOU look fat!")
        chatNpc(angry, "Shut up!")
        chatNpc(angry, "Fatty!")
        chatNpc(angry, "SHUT UP!")

        when (
            choice3(
                "Do you want me to pick an armour colour for you?", 1,
                "Wartface is fatter than you!", 2,
                "You're both stupid.", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Do you want me to pick an armour colour for you?")
                chatNpc(angry, "Yes, as long as you pick green.")
                chatNpc(angry, "No you have to pick red!")
                chatNpc(angry, "Shut up! My goblins will decide what colour they want!")
                startQuestOffer(npc)
            }
            2 -> {
                chatPlayer(laugh, "Wartface is fatter than you!")
                chatNpc(angry, "Human! You die! ... maybe not, actually human could help settle argument.")
                startQuestOffer(npc)
            }
            3 -> {
                chatPlayer(neutral, "You're both stupid.")
                chatNpc(angry, "Human! You're the one that's looked stupid!")
                chatNpc(angry, "Yeah, stupid human!")
            }
        }
    }

    private suspend fun Dialogue.startQuestOffer(npc: Npc) {
        chatNpc(angry, "That would mean me wrong... but at least Wartface not right!")
        chatNpc(
            angry,
            "Well Bentnoze never been right in his life. Still, maybe new colour good, but " +
                "will have to see armour before decide.",
        )
        chatNpc(angry, "Human! You bring us armour in new colour!")
        chatNpc(quiz, "What colour we try?")
        chatNpc(angry, "Orange armour might be good.")
        chatNpc(angry, "Yep, bring us orange armour.")
        quest.advanceQuestStage(access)
    }

    private suspend fun Dialogue.dialogDuringQuest(npc: Npc) {
        val stage = quest.getQuestStage(player)
        val mail = player.mailCounts()
        when (stage) {
            STAGE_WANT_ORANGE -> {
                chatNpc(quiz, "Have you got some orange armour for we yet?")
                if (mail.orange > 0) {
                    chatPlayer(happy, "I have some orange armour here.")
                    chatNpc(angry, "Grubfoot!")
                    chatNpc(quiz, "Yes General Wartface?")
                    chatNpc(angry, "Put on this armour!")
                    access.invDel(access.inv, "obj.goblin_armour_orange")
                    chatNpc(sad, "No I don't like that much.")
                    chatNpc(sad, "It clashes with skin colour.")
                    chatNpc(angry, "We need darker colour, like blue.")
                    chatNpc(angry, "Yeah blue might be good.")
                    chatNpc(angry, "Human! Get us blue armour!")
                    quest.advanceQuestStage(access)
                } else {
                    chatPlayer(neutral, "I haven't got any yet.")
                    chatNpc(
                        angry,
                        "Bring us orange armour! Goblin mail dyed orange - the witch Aggie " +
                            "make dyes.",
                    )
                }
            }
            STAGE_WANT_BLUE -> {
                chatNpc(quiz, "Have you got some blue armour for we yet?")
                if (mail.blue > 0) {
                    chatPlayer(happy, "I have some blue armour here.")
                    chatNpc(sad, "That not right. Not goblin colour at all.")
                    chatNpc(angry, "Goblins wear dark earthy colours like brown.")
                    chatNpc(angry, "Yeah brown might be good.")
                    chatNpc(angry, "Human! Get us brown armour!")
                    access.invDel(access.inv, "obj.goblin_armour_darkblue")
                    quest.advanceQuestStage(access)
                } else {
                    chatPlayer(neutral, "I haven't got any yet.")
                    chatNpc(angry, "Bring us blue armour!")
                }
            }
            STAGE_WANT_BROWN -> {
                chatNpc(quiz, "Have you got some brown armour for we yet?")
                // The "brown armour" the generals settle on is a regular suit of goblin mail -
                // plain mail is already brown, exactly as recorded in the capture.
                if (mail.plain > 0) {
                    chatPlayer(happy, "I have some brown armour here.")
                    chatNpc(happy, "That colour quite nice. Me can see myself wearing that.")
                    chatNpc(happy, "It a deal then. Brown armour it is.")
                    access.invDel(access.inv, "obj.goblin_armour")
                    chatNpc(happy, "Thank you for sorting out argument, human. You have reward now.")
                    quest.complete(access)
                } else {
                    chatPlayer(confused, "But I thought brown was the armour you were changing from...")
                    chatNpc(angry, "Red armour best.")
                    chatNpc(angry, "No it has to be green!")
                    chatNpc(angry, "Go away human, we busy.")
                }
            }
        }
    }

    private suspend fun Dialogue.dialogAfterQuest(npc: Npc) {
        chatNpc(happy, "Brown armour good. All goblins wear brown now!")
        chatNpc(happy, "Thanks for sorting out argument, human.")
    }

    /* ------------------------------------------------------------------ */
    /* Wyson the gardener - sells woad leaves                              */
    /* ------------------------------------------------------------------ */

    private suspend fun Dialogue.wysonDialogue(npc: Npc) {
        chatNpc(
            neutral,
            "I'm Wyson the gardener. I grow flowers for the park - and the odd woad leaf, if " +
                "you know what to ask for.",
        )
        when (
            choice3(
                "I'm looking for woad leaves.", 1,
                "Nice place you have here.", 2,
                "Nothing, thanks.", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "I'm looking for woad leaves.")
                chatNpc(happy, "Well, I happen to have some. I'll sell you one for 20 coins.")
                when (
                    choice2(
                        "OK, here's 20 coins.", 1,
                        "No thanks, that's too expensive.", 2,
                    )
                ) {
                    1 -> {
                        if (player.inv.count("obj.coins") >= WOAD_PRICE) {
                            access.invDel(access.inv, "obj.coins", WOAD_PRICE)
                            access.invAdd(access.inv, "obj.woadleaf")
                            chatPlayer(happy, "Thanks, Wyson!")
                            chatNpc(happy, "Pleasure doing business with you.")
                        } else {
                            chatPlayer(sad, "I haven't got 20 coins on me.")
                            chatNpc(sad, "Come back when you do.")
                        }
                    }
                    2 -> chatPlayer(sad, "No thanks, that's too expensive.")
                }
            }
            2 -> {
                chatPlayer(happy, "Nice place you have here.")
                chatNpc(happy, "Aye, it's a fine garden. Been tending it for many a year.")
            }
            3 -> chatNpc(neutral, "Very well. Good day to you.")
        }
    }

    /* ------------------------------------------------------------------ */
    /* Aggie - dye services                                                */
    /* ------------------------------------------------------------------ */

    private suspend fun Dialogue.aggieDialogue(npc: Npc) {
        chatNpc(
            neutral,
            "Hello there. I'm Aggie. I see you're the adventurous type - I can make dyes for " +
                "you if you bring me the right ingredients.",
        )
        when (
            choice3(
                "What dyes can you make?", 1,
                "Tell me about yourself.", 2,
                "Nothing, thanks.", 3,
            )
        ) {
            1 -> aggieDyeMenu(npc)
            2 -> chatNpc(happy, "I've been a witch for many a year. Potions and dyes are my specialty.")
            3 -> chatNpc(neutral, "Very well, dearie.")
        }
    }

    private suspend fun Dialogue.aggieDyeMenu(npc: Npc) {
        chatPlayer(quiz, "What dyes can you make?")
        when (
            choice5(
                "Red dye", 1,
                "Blue dye", 2,
                "Yellow dye", 3,
                "Orange dye", 4,
                "Nothing, thanks.", 5,
            )
        ) {
            1 -> aggieMakeDye(npc, "red", "obj.reddye", listOf("obj.redberries"), listOf(2))
            2 -> aggieMakeDye(npc, "blue", "obj.bluedye", listOf("obj.woadleaf"), listOf(2))
            3 -> aggieMakeDye(npc, "yellow", "obj.yellowdye", listOf("obj.onion"), listOf(2))
            4 -> aggieMakeDye(npc, "orange", "obj.orangedye", listOf("obj.reddye", "obj.yellowdye"), listOf(1, 1))
            5 -> chatNpc(neutral, "Very well, dearie.")
        }
    }

    private suspend fun Dialogue.aggieMakeDye(
        npc: Npc,
        colour: String,
        dye: String,
        ingredients: List<String>,
        counts: List<Int>,
    ) {
        val ingredientText = ingredients
            .zip(counts)
            .joinToString(" and ") { (item, count) ->
                "$count " + item.removePrefix("obj.").replace('_', ' ')
            }
        chatNpc(
            neutral,
            "For $colour dye I need $ingredientText, and $DYE_COST coins.",
        )
        for ((item, count) in ingredients.zip(counts)) {
            if (player.inv.count(item) < count) {
                chatPlayer(sad, "I haven't got the ingredients with me.")
                chatNpc(neutral, "Come back when you do.")
                return
            }
        }
        if (player.inv.count("obj.coins") < DYE_COST) {
            chatPlayer(sad, "I haven't got $DYE_COST coins on me.")
            chatNpc(neutral, "Come back when you do.")
            return
        }
        chatPlayer(happy, "Here you go.")
        for ((item, count) in ingredients.zip(counts)) {
            access.invDel(access.inv, item, count)
        }
        access.invDel(access.inv, "obj.coins", DYE_COST)
        access.invAdd(access.inv, dye)
        mesbox("Aggie waves her hands about, and hands you a bottle of $colour dye.")
    }

    /* ------------------------------------------------------------------ */
    /* Crates and dyed mail                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Searching the three armour crates in the Goblin Village: per player, exactly one holds a
     * suit of goblin mail (decided by the first crate searched, as with the capture's crate
     * hunt). The message matches the recorded `You find some goblin mail in the crate.`
     */
    private suspend fun ProtectedAccess.searchMailCrate(crateIndex: Int) {
        arriveDelay()
        anim("seq.human_pickuptable")
        delay(1)

        if (crateSearched[crateIndex].get(player)) {
            mes("You search the crate, but find nothing of interest.")
            return
        }
        crateSearched[crateIndex].set(player, true)
        if (ownedCrate.get(player) == -1) {
            ownedCrate.set(player, crateIndex)
        }
        if (ownedCrate.get(player) != crateIndex) {
            mes("You search the crate, but find nothing of interest.")
            return
        }
        if (inv.freeSpace() < 1) {
            mes("You find some goblin mail in the crate, but you don't have room to carry it.")
            return
        }
        invAdd(inv, "obj.goblin_armour")
        mes("You find some goblin mail in the crate.")
    }

    /** Dye a plain suit of goblin mail with a bottle of dye (either order in the inventory). */
    private suspend fun ProtectedAccess.dyeMail(dye: String, dyedMail: String, colour: String) {
        if (inv.count("obj.goblin_armour") < 1) {
            return
        }
        invDel(inv, "obj.goblin_armour")
        invDel(inv, dye)
        invAdd(inv, dyedMail)
        mes("You dye the goblin mail $colour.")
    }

    companion object {
        const val STAGE_WANT_ORANGE = 1
        const val STAGE_WANT_BLUE = 2
        const val STAGE_WANT_BROWN = 3

        const val DYE_COST = 25
        const val WOAD_PRICE = 20
    }
}
