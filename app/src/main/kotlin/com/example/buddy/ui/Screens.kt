package com.example.buddy.ui

/** Name -> screen, for pushing screens by name. */
object Screens {
    fun create(name: String, act: UiActivity): Screen? = when (name) {
        "setup" -> SetupScreen(act)
        "permissions" -> PermissionsScreen(act)
        "ai" -> AiScreen(act)
        "outfits" -> OutfitsScreen(act)
        "music" -> MusicScreen(act)
        "call-assistant" -> CallAssistantScreen(act)
        "fun" -> FunScreen(act)
        "game-guess" -> GameScreen(act, "guess")
        "game-rps" -> GameScreen(act, "rps")
        "game-memory" -> GameScreen(act, "memory")
        "game-twenty" -> GameScreen(act, "twenty")
        "game-room" -> RoomScreen(act)
        "game-room-shop" -> RoomShopScreen(act)
        "game-match" -> MatchMapScreen(act)
        "game-runner" -> RunnerScreen(act)
        "game-battle" -> BattleScreen(act)
        "game-beat" -> BeatScreen(act)
        "game-word" -> WordScreen(act)
        "game-english" -> EnglishScreen(act)
        "agents" -> AgentsScreen(act)
        "runs" -> RunsScreen(act)
        "vocab" -> com.example.buddy.ui.vocab.VocabScreen(act)
        else -> null
    }
}
