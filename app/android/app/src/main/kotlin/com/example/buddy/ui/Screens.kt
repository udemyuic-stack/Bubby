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
        "agents" -> AgentsScreen(act)
        else -> null
    }
}
