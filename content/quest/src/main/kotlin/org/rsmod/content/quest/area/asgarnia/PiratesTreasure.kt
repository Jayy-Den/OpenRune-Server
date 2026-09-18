package org.rsmod.content.quest.area.asgarnia

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.SpadeDigScript
import org.rsmod.content.quest.manager.SpadeDigScript.DigSite
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pirate's Treasure.
 *
 * Stages (stored in `varp.ahoy_questvar`, endstate 4 from `dbrow.quest_piratestreasure`; the
 * capture shows 1 -> 2 -> 3 -> 4 at each step):
 * - [STAGE_RUM_TASK]: Redbeard Frank asked for Karamjan rum.
 * - [STAGE_RUM_DELIVERED]: Frank took the rum and gave Hector's key.
 * - [STAGE_HAS_KEY]: The key unlocked Hector's chest; the pirate message points at Falador.
 * - [STAGE_COMPLETE]: The treasure was dug up in Falador Park.
 */
class PiratesTreasure @Inject constructor(private val spadeDigs: SpadeDigScript) : QuestScript(
    "quest_piratestreasure",
    "varp.ahoy_questvar",
    rewards {
        extra("Access to One-Eyed Hector's treasure chest")
        item("obj.pirate_casket", 1)
    },
    ItemRewardDisplay("obj.pirate_casket"),
) {
    override fun ScriptContext.init() {
        onOpNpc1("npc.redbeard_frank") { startDialogue(it.npc) { frankDialogue(it.npc) } }
        onOpLocU("loc.piratechest", "obj.chest_key") { unlockChest(it.loc) }
        onOpLoc1("loc.piratechest") { searchChest() }
        onOpHeld1("obj.piratemessage") { readPirateMessage() }
        spadeDigs.registerQuestSite(
            DigSite(
                matches = {
                    quest.getQuestStage(player) == STAGE_HAS_KEY &&
                        player.coords == TREASURE_SPOT
                },
                dig = { digTreasure() },
            ),
            quest,
        )
    }

    override fun subTitle(): String =
        "talking to <col=800000>Redbeard Frank</col> at the northernmost dock in " +
            "<col=800000>Port Sarim</col>."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        description(
            "I can start this quest by talking to <red>Redbeard Frank</red> at the " +
                "<red>northernmost dock</red> in <red>Port Sarim</red>.",
        ) {
            hideWhenQuestStarted()
        }

        objective(
            "<red>Redbeard Frank</red> will tell me where to find treasure if I bring him a " +
                "bottle of <red>Karamjan rum</red>. The rum is sold on <red>Karamja</red>, but " +
                "the customs officers confiscate any alcohol brought off the island...",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_RUM_TASK }
        }

        objective(
            "Frank gave me <red>Hector's key</red>. It opens the chest in <red>Hector's old " +
                "room</red> in the <red>Blue Moon Inn</red> in <red>Varrock</red>.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_RUM_DELIVERED }
        }

        objective(
            "The pirate message I found points to <red>Falador Park</red>: 'Visit the city of " +
                "the White Knights. In the park, Saradomin points to the X which marks the " +
                "spot.' I should dig where the statue's gaze meets the centre of the dirt X.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_HAS_KEY }
        }
    }

    override fun completedLog(player: ProtectedAccess): String = completionJournal(player) {
        line("Redbeard Frank traded the location of a treasure for a bottle of Karamjan rum.")
        line("I smuggled the rum off Karamja inside a banana crate bound for Wydin's shop.")
        line("Hector's key opened his chest in the Blue Moon Inn, revealing a pirate message.")
        line("I dug up the treasure in Falador Park, under the gaze of the statue of Saradomin.")
    }

    private suspend fun Dialogue.frankDialogue(npc: Npc) {
        when {
            quest.isQuestCompleted(player) -> chatNpc(happy, "Arr, matey! The treasure looked good on ye!")
            quest.questState(player) == QuestProgressState.IN_PROGRESS -> dialogDuringQuest(npc)
            else -> dialogQuestNotStarted(npc)
        }
    }

    private suspend fun Dialogue.dialogQuestNotStarted(npc: Npc) {
        chatNpc(neutral, "Arr, Matey!")
        chatPlayer(quiz, "I'm in search of treasure.")
        chatNpc(
            neutral,
            "Arr, treasure you be after eh? Well I might be able to tell you where to find " +
                "some... For a price...",
        )
        chatPlayer(quiz, "What sort of price?")
        chatNpc(
            neutral,
            "Well for example if you can get me a bottle of rum... Not just any rum mind...",
        )
        chatNpc(
            neutral,
            "I'd like some rum made on Karamja Island. There's no rum like Karamja Rum!",
        )
        chatPlayer(happy, "Ok, I will bring you some Karamja Rum.")
        chatNpc(
            neutral,
            "Yer a saint, although it'll take a miracle to get it off Karamja.",
        )
        chatPlayer(quiz, "What do you mean?")
        chatNpc(
            neutral,
            "The Customs office has been clampin' down on the export of spirits. You seem like " +
                "a resourceful young lad, I'm sure ye'll be able to find a way to do it.",
        )
        when (
            choice2(
                "Well I'll give it a shot.", 1,
                "I don't know if I can do that.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Well I'll give it a shot.")
                chatNpc(happy, "Arr, that's the spirit!")
                quest.advanceQuestStage(access)
            }
            2 -> chatPlayer(sad, "I don't know if I can do that.")
        }
    }

    private suspend fun Dialogue.dialogDuringQuest(npc: Npc) {
        if (player.inv.contains("obj.karamja_rum")) {
            chatNpc(happy, "Is that rum I smell? Let's have it then.")
            access.invDel(access.inv, "obj.karamja_rum")
            access.invAdd(access.inv, "obj.chest_key")
            access.mes("Frank happily takes the rum... and hands you a key.")
            quest.advanceQuestStage(access)
            chatNpc(neutral, "Now a deal's a deal, I'll tell ye about the treasure.")
            chatNpc(
                neutral,
                "I used to serve under a pirate captain called One-Eyed Hector.",
            )
            chatNpc(
                neutral,
                "Hector were very successful and became very rich. But about a year ago we were " +
                    "boarded by the Customs and Excise Agents.",
            )
            chatNpc(
                neutral,
                "Hector were killed along with many of the crew, I were one of the few to " +
                    "escape and I escaped with this.",
            )
            chatNpc(
                neutral,
                "This be Hector's key. I believe it opens his chest in his old room in the " +
                    "Blue Moon Inn in Varrock.",
            )
            chatNpc(neutral, "With any luck his treasure will be in there.")
            chatPlayer(quiz, "So why didn't you ever get it?")
            chatNpc(
                neutral,
                "I'm not allowed in the Blue Moon Inn. Apparently I'm a drunken trouble maker.",
            )
        } else {
            chatNpc(neutral, "Have ye got me rum yet?")
            chatPlayer(sad, "Not yet, I'm still working on a way to smuggle it off Karamja.")
            chatNpc(
                neutral,
                "The customs officers search everyone who boards the ship. If only the rum were " +
                    "hidden in somethin' they don't search...",
            )
        }
    }

    private suspend fun ProtectedAccess.unlockChest(chest: BoundLocInfo) {
        arriveDelay()

        if (quest.getQuestStage(player) < STAGE_RUM_DELIVERED || "obj.chest_key" !in inv) {
            mes("The chest is locked.")
            return
        }

        invReplace(inv, "obj.chest_key", 1, "obj.piratemessage")
        mes("You unlock the chest.")
        quest.advanceQuestStage(this)
        searchChest()
    }

    private suspend fun ProtectedAccess.searchChest() {
        if (quest.getQuestStage(player) < STAGE_RUM_DELIVERED) {
            mes("The chest is locked.")
        } else if ("obj.piratemessage" in inv) {
            mes("All that's in the chest is a message...")
        } else {
            mes("You search the chest but find nothing.")
        }
    }

    private suspend fun ProtectedAccess.readPirateMessage() {
        mesbox(
            "Visit the city of the White Knights. In the park, " +
                "Saradomin points to the X which marks the spot.",
        )
    }

    private suspend fun ProtectedAccess.digTreasure() {
        anim("seq.human_dig")
        soundSynth("synth.digspade")
        delay(3)

        mes("You dig a hole in the ground...")
        delay(2)
        mes("and find a little chest of treasure.")
        quest.complete(this)
    }

    companion object {
        const val STAGE_RUM_TASK = 1
        const val STAGE_RUM_DELIVERED = 2
        const val STAGE_HAS_KEY = 3

        /** Falador Park dig spot: the centre of the dirt X, where Saradomin's gaze falls. */
        val TREASURE_SPOT = CoordGrid(2999, 3383)
    }
}
