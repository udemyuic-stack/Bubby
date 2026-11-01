package com.example.buddy.ui.home

object SceneOrigin {
    private var sceneX = 0f
    private var sceneY = 0f
    private var homeX = 0f
    private var homeY = 0f

    val dx get() = sceneX - homeX
    val dy get() = sceneY - homeY

    fun setScene(x: Int, y: Int) {
        sceneX = x.toFloat()
        sceneY = y.toFloat()
    }

    fun setHome(x: Int, y: Int) {
        homeX = x.toFloat()
        homeY = y.toFloat()
    }
}
