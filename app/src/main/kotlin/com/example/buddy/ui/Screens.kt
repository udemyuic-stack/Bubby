package com.example.buddy.ui

/** Name -> screen, for pushing screens by name. */
object Screens {
    fun create(name: String, act: UiActivity): Screen? = when (name) {
        "setup" -> SetupScreen(act)
        "permissions" -> PermissionsScreen(act)
        "ai" -> AiScreen(act)
        "outfits" -> OutfitsScreen(act)
        "music" -> MusicScreen(act)
        "fun" -> FunScreen(act)
        "game-guess" -> GameScreen(act, "guess")
        "game-rps" -> GameScreen(act, "rps")
        "game-memory" -> GameScreen(act, "memory")
        "game-twenty" -> GameScreen(act, "twenty")
        "agents" -> AgentsScreen(act)
        "runs" -> RunsScreen(act)
        else -> null
    }
}
