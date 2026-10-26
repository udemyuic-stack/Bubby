package com.example.buddy.ui

/** Name -> screen, for pushing screens by name. */
object Screens {
    fun create(name: String, act: UiActivity): Screen? = when (name) {
        "music" -> MusicScreen(act)
        "fun" -> FunScreen(act)
        "agents" -> AgentsScreen(act)
        else -> null
    }
}
