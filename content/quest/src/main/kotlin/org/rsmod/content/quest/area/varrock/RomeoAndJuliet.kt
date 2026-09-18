package org.rsmod.content.quest.area.varrock

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Romeo & Juliet.
 *
 * Stages (stored in `varp.rjquest`, endstate 100 from `dbrow.quest_romeoandjuliet`; the capture
 * shows 10 -> 20 -> 30 -> 40 -> 50 -> 60 -> 100):
 * - [STAGE_TALKED_ROMEO]: Romeo asked me to deliver a message to Juliet.
 * - [STAGE_MET_JULIET]: Juliet gave me a message for Romeo; Father Lawrence may help.
 * - [STAGE_MET_LAWRENCE]: Father Lawrence devised the cadava potion plan.
 * - [STAGE_HAS_POTION]: The Apothecary brewed the potion; deliver it to Juliet.
 * - [STAGE_TOLD_ROMEO]: Juliet drank the potion; Romeo must be told.
 * - [STAGE_COMPLETE]: The tale is told.
 */
class RomeoAndJuliet : QuestScript(
    "quest_romeoandjuliet",
    "varp.rjquest",
    rewards {
        xp("stat.crafting", 300.0)
        item("obj.coins", 300)
    },
    ItemRewardDisplay("obj.cadava"),
) {
    override fun ScriptContext.init() {
        onOpNpc1("npc.romeo") { startDialogue(it.npc) { romeoDialogue(it.npc) } }
        onOpNpc1("npc.juliet") { startDialogue(it.npc) { julietDialogue(it.npc) } }
        onOpNpc1("npc.father_lawrence") { startDialogue(it.npc) { lawrenceDialogue(it.npc) } }
        onOpNpc1("npc.apothecary") { startDialogue(it.npc) { apothecaryDialogue(it.npc) } }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Romeo</col> in <col=800000>Varrock</col> central square."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        description(
            "I can start this quest by talking to <red>Romeo</red> in <red>Varrock</red> " +
                "central square.",
        ) {
            hideWhenQuestStarted()
        }

        objective(
            "<red>Romeo</red> has lost contact with his love <red>Juliet</red>. I should " +
                "deliver his message to her. She lives in the house just inside Varrock's " +
                "western wall.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_TALKED_ROMEO }
        }

        objective(
            "<red>Juliet</red> fears her cousin's threats and is kept locked away. She asked " +
                "me to tell <red>Romeo</red> that <red>Father Lawrence</red> may know a way.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_MET_JULIET }
        }

        objective(
            "<red>Father Lawrence</red> devised a plan: a <red>Cadava potion</red> will make " +
                "Juliet appear dead so she can be rescued from the crypt. The " +
                "<red>Apothecary</red> in south-west Varrock can brew it, but he needs " +
                "<red>Cadava berries</red>.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_MET_LAWRENCE }
        }

        objective(
            "I have the <red>Cadava potion</red>. I must give it to <red>Juliet</red> without " +
                "delay.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_HAS_POTION }
        }

        objective(
            "Juliet drank the potion and has been taken to the crypt. I must tell " +
                "<red>Romeo</red> where to find her.",
        ) {
            visibleWhen { quest.getQuestStage(access.player) == STAGE_TOLD_ROMEO }
        }
    }

    override fun completedLog(player: ProtectedAccess): String = completionJournal(player) {
        line("Romeo and Juliet's love was thwarted by their families' feud.")
        line("Father Lawrence devised a Cadava potion to fake Juliet's death.")
        line("Juliet drank it and escaped her father's tower for good.")
        line("Romeo, ever the romantic, had already forgotten her by the crypt...")
    }

    private suspend fun Dialogue.romeoDialogue(npc: Npc) {
        when {
            quest.isQuestCompleted(player) -> chatNpc(sad, "Blub! Blub... where is my Juliet?")
            quest.questState(player) == QuestProgressState.IN_PROGRESS -> romeoDuringQuest(npc)
            else -> romeoNotStarted(npc)
        }
    }

    private suspend fun Dialogue.romeoNotStarted(npc: Npc) {
        chatNpc(sad, "Juliet! Juliet! Wherefore art thou Juliet?")
        chatPlayer(quiz, "Are you alright?")
        chatNpc(shocked, "No! Everything is ruined!")
        chatPlayer(quiz, "What's the matter?")
        chatNpc(
            sad,
            "It's Juliet. We are in love, but our families hate each other. Her father has " +
                "locked her away and I fear I will never see her again!",
        )
        chatNpc(sad, "Blub! Blub... where is my Juliet? Have you seen her?")
        chatPlayer(quiz, "You were asking me about Juliet? You seemed to know her?")
        chatNpc(neutral, "Oh yes, Juliet!")
        chatNpc(
            confused,
            "The fox...could you tell her that she is the love of my long and that I life to " +
                "be with her?",
        )
        chatPlayer(
            confused,
            "What? Surely you mean that she is the love of your life and that you long to be " +
                "with her?",
        )
        chatNpc(
            neutral,
            "Oh yeah...what you said...tell her that, it sounds much better! Oh you're so " +
                "good at this!",
        )
        chatPlayer(happy, "Yes, okay. I'll let her know.")
        chatNpc(neutral, "Oh great! And tell her that I want to kiss her a give.")
        chatPlayer(confused, "You mean you want to give her a kiss!")
        chatNpc(neutral, "Oh you're good...you are good!")
        chatNpc(neutral, "I see I've picked a true professional...!")
        chatPlayer(neutral, "Ok, thanks.")
        quest.advanceQuestStage(access)
    }

    private suspend fun Dialogue.romeoDuringQuest(npc: Npc) {
        when (quest.getQuestStage(player)) {
            STAGE_TALKED_ROMEO -> {
                chatNpc(worried, "Did you find Juliet?")
                chatPlayer(neutral, "Not yet. I'll bring her your message.")
            }
            STAGE_MET_JULIET -> {
                chatNpc(worried, "Have you seen Juliet?")
                chatPlayer(neutral, "She says Father Lawrence may know a way to help.")
                chatNpc(shocked, "Thank you! Please speak to him for me!")
                quest.advanceQuestStage(access)
            }
            STAGE_MET_LAWRENCE -> {
                chatNpc(worried, "Any word from Father Lawrence?")
                chatPlayer(neutral, "The Apothecary is brewing a potion. Keep your hopes up.")
            }
            STAGE_HAS_POTION -> {
                chatNpc(worried, "What news of Juliet?")
                chatPlayer(neutral, "I have the potion from the Apothecary. I shall deliver it.")
            }
            else -> {
                chatNpc(sad, "Juliet... Juliet...")
                chatPlayer(sad, "You may want to sit down for this...")
            }
        }
    }

    private suspend fun Dialogue.julietDialogue(npc: Npc) {
        val stage = quest.getQuestStage(player)
        when {
            quest.isQuestCompleted(player) -> chatNpc(happy, "I am safe now, thanks to you.")
            stage == STAGE_TALKED_ROMEO -> {
                chatPlayer(neutral, "Juliet, I come from Romeo. He begs me to tell you that he " +
                    "cares still.")
                chatNpc(
                    happy,
                    "Oh how my heart soars to hear this news! Please take this message to him " +
                        "with great haste.",
                )
                access.invAdd(access.inv, "obj.julietmessage")
                quest.advanceQuestStage(access)
            }
            stage < STAGE_HAS_POTION -> chatNpc(
                worried,
                "I must wait for word from Romeo. Do not lose hope.",
            )
            stage == STAGE_HAS_POTION && player.inv.contains("obj.cadavapotion") ->
                julietPotionScene(npc)
            else -> chatNpc(
                worried,
                "Father Lawrence has a plan? Then hurry, before my cousin makes good her " +
                    "threat.",
            )
        }
    }

    private suspend fun Dialogue.julietPotionScene(npc: Npc) {
        chatPlayer(
            happy,
            "Hi Juliet! I have an interesting proposition for you...suggested by Father " +
                "Lawrence. It may be the only way you'll be able to escape from this house " +
                "and be with Romeo.",
        )
        chatNpc(quiz, "Go on....")
        chatPlayer(
            neutral,
            "I have a Cadava potion here, suggested by Father Lawrence. If you drink it, it " +
                "will make you appear dead!",
        )
        chatNpc(quiz, "Yes...")
        chatPlayer(
            neutral,
            "And when you appear dead...your still and lifeless corpse will be removed to " +
                "the crypt!",
        )
        chatNpc(quiz, "Oooooh, a cold dark creepy crypt...")
        chatNpc(happy, "...sounds just peachy!")
        chatPlayer(
            neutral,
            "Then...Romeo can steal into the crypt and rescue you just as you wake up!",
        )
        chatNpc(quiz, "...and this is the great idea for getting me out of here?")
        chatPlayer(
            neutral,
            "To be fair, I can't take all the credit...in fact...it was all Father " +
                "Lawrence's suggestion...",
        )
        chatNpc(neutral, "Ok...if this is the best we can do...hand over the potion!")
        access.invDel(access.inv, "obj.cadavapotion")
        chatNpc(
            happy,
            "Wonderful! I just hope Romeo can remember to get me from the crypt.",
        )
        chatNpc(
            neutral,
            " Please go to Romeo and make sure he understands. Although I love his gormless, " +
                "lovelorn soppy ways, he can be a bit dense sometimes and I don't want to " +
                "wake up in that crypt on my own.",
        )
        chatNpc(happy, "Oh, here's Phillipa, my cousin...she's in on the plot too!")
        chatNpc(happy, "She's going to make it seem even more convincing!")
        chatNpc(happy, "Yes, I'm quite the actress! Good luck dear cousin!")
        chatNpc(neutral, "Right...bottoms up!")
        chatNpc(shocked, "Urk!")
        chatNpc(shocked, "Oh no...Juliet has...died!")
        chatPlayer(confused, "You might be more believable if you're not smiling when you say it...")
        quest.advanceQuestStage(access)
    }

    private suspend fun Dialogue.lawrenceDialogue(npc: Npc) {
        val stage = quest.getQuestStage(player)
        when {
            quest.isQuestCompleted(player) -> chatNpc(happy, "A tragic tale, but love endures.")
            stage < STAGE_MET_JULIET -> chatNpc(neutral, "May Saradomin walk with you.")
            stage == STAGE_MET_JULIET -> {
                chatNpc(neutral, "'...and let Saradomin light the way for you... ' Urgh!")
                chatNpc(angry, "Can't you see that I'm in the middle of a sermon?!")
                chatPlayer(neutral, "But Romeo sent me!")
                chatNpc(angry, "But I'm busy delivering a sermon to my congregation!")
                chatNpc(silent, "Zzzzzzzzz")
                chatNpc(
                    neutral,
                    "Ok, okay... what do you want so I can get rid of you and continue with " +
                        "my sermon?",
                )
                chatPlayer(neutral, "Romeo sent me. He says you may be able to help.")
                chatNpc(neutral, "Ah Romeo, yes. A fine lad, but a little bit confused.")
                chatPlayer(
                    neutral,
                    "Yes, very confused.... Anyway, Romeo wishes to be married to Juliet! " +
                        "She must be rescued from her father's control!",
                )
                chatNpc(
                    happy,
                    "I agree, and I think I have an idea! A potion to make her appear dead...",
                )
                chatPlayer(shocked, "Dead! Sounds a bit creepy to me... but please, continue.")
                chatNpc(
                    neutral,
                    "The potion will only make Juliet 'appear' dead... then she'll be taken " +
                        "to the crypt...",
                )
                chatPlayer(shocked, "Crypt! Again... very creepy! You must have some strange hobbies.")
                chatNpc(
                    neutral,
                    "Then Romeo can collect her from the crypt! Go to the Apothecary, tell him " +
                        "I sent you and that you'll need a 'Cadava' potion.",
                )
                chatPlayer(
                    neutral,
                    "Apart from the strong overtones of death, this is turning out to be a " +
                        "real love story.",
                )
                quest.advanceQuestStage(access)
            }
            else -> chatNpc(
                neutral,
                "The Apothecary brews the Cadava potion. Romeo must collect Juliet from the " +
                    "crypt!",
            )
        }
    }

    private suspend fun Dialogue.apothecaryDialogue(npc: Npc) {
        val stage = quest.getQuestStage(player)
        when {
            quest.isQuestCompleted(player) -> chatNpc(
                neutral,
                "You're welcome. I hope it helps that young couple find happiness.",
            )
            stage < STAGE_MET_LAWRENCE -> {
                chatNpc(neutral, "I am the Apothecary. I brew potions. Do you need anything specific?")
                chatPlayer(quiz, "Can you make potions for me?")
                chatNpc(neutral, "I am the Apothecary. I brew potions. Do you need anything specific?")
            }
            stage == STAGE_MET_LAWRENCE -> {
                chatPlayer(
                    neutral,
                    "Apothecary, Father Lawrence sent me. I need a Cadava potion to help " +
                        "Romeo and Juliet.",
                )
                chatNpc(neutral, "Cadava potion. It's pretty nasty. And hard to make.")
                chatNpc(neutral, "Wing of rat, tail of frog. Ear of snake and horn of dog.")
                chatNpc(neutral, "I have all that, but I need some Cadava berries.")
                chatNpc(
                    neutral,
                    "You will have to find them while I get the rest ready. Bring them here " +
                        "when you have them. But be careful. They are nasty.",
                )
                if (player.inv.contains("obj.cadavaberries")) {
                    chatPlayer(happy, "Conveniently, I have some here.")
                    chatNpc(happy, "Well done. You have the berries.")
                    access.invDel(access.inv, "obj.cadavaberries")
                    chatNpc(happy, "Phew! Here is what you need.")
                    access.invAdd(access.inv, "obj.cadavapotion")
                    access.mes("The Apothecary gives you a Cadava potion.")
                    quest.advanceQuestStage(access)
                } else {
                    quest.advanceQuestStage(access)
                }
            }
            else -> {
                if (player.inv.contains("obj.cadavaberries")) {
                    chatPlayer(happy, "I have the Cadava berries now.")
                    chatNpc(happy, "Well done. You have the berries.")
                    access.invDel(access.inv, "obj.cadavaberries")
                    chatNpc(happy, "Phew! Here is what you need.")
                    access.invAdd(access.inv, "obj.cadavapotion")
                    access.mes("The Apothecary gives you a Cadava potion.")
                    quest.advanceQuestStage(access)
                } else {
                    chatNpc(
                        neutral,
                        "I still need Cadava berries for the potion. They grow south-west of " +
                            "Draynor Village.",
                    )
                }
            }
        }
    }

    companion object {
        const val STAGE_TALKED_ROMEO = 10
        const val STAGE_MET_JULIET = 20
        const val STAGE_MET_LAWRENCE = 30
        const val STAGE_HAS_POTION = 40
        const val STAGE_TOLD_ROMEO = 50
    }
}
