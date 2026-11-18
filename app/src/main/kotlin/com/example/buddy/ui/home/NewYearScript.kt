package com.example.buddy.ui.home

import kotlin.random.Random

class NewYearScript(private val knock: NewYearKnock, rng: Random) {
    class Spark(val atMs: Long, val x: Float, val y: Float, val dx: Float, val dy: Float)

    val sprites = ArrayList<Sprite>()
    val sparks = ArrayList<Spark>()

    private val g = NewYearLayout.GROUND
    private val gx = NewYearLayout.giftX
    private val drops = Array(GIFTS) { ArrayList<Phase>() }
    private val gifts = Array(GIFTS) { NewYearActors.gift(it) }
    private val animals = ArrayList<Sprite>()
    private val eagle = ArrayList<Sprite>()

    init {
        val sleigh = Phase(0, SLEIGH_MS, Curves.linear, listOf(Key(0f, SX0, SY), Key(0.5f, (SX0 + SX1) / 2f, SY + 40f), Key(1f, SX1, SY - 20f)))
        sprites.add(Sprite(NewYearActors.sleigh(), listOf(sleigh), SLEIGH_MS))
        for (i in 0 until GIFTS) drops[i].add(dropPhase(650L + 300L * i, gx[i], g - 30f))
        animal(NewYearActors.rabbit(), 46f, 50f, -40f, 0, 2300L, 1)
        animal(NewYearActors.fox(), 64f, 40f, 430f, 3, 2600L, -1)
        animal(NewYearActors.penguin(), 40f, 46f, 430f, 2, 3000L, -1)
        thief()
        for (i in 0 until GIFTS) sprites.add(Sprite(gifts[i], drops[i], drops[i].last().endMs))
        sprites.add(knockGift())
        sprites.addAll(animals)
        sprites.addAll(eagle)
        sprites.add(bear())
        var t = SPARK_FROM
        while (t < SLEIGH_MS) {
            sparks.add(Spark(t, sleighX(t) + 105f, sleighY(t) + 12f, rng.nextFloat() * 12f - 6f, 10f + rng.nextFloat() * 20f))
            t += SPARK_STEP
        }
    }

    private fun sleighX(t: Long) = SX0 + (SX1 - SX0) * t / SLEIGH_MS

    private fun sleighY(t: Long) = if (t < SLEIGH_MS / 2) SY + 40f * (t / (SLEIGH_MS / 2f)) else SY + 40f - 60f * ((t - SLEIGH_MS / 2f) / (SLEIGH_MS / 2f))

    private fun dropPhase(t: Long, x: Float, y: Float): Phase {
        val fx = sleighX(t) + 70f
        val fy = sleighY(t) + 10f
        return Phase(
            t, DROP_MS, Curves.easeIn,
            listOf(Key(0f, fx, fy, 0f), Key(0.05f, fx, fy), Key(0.75f, x, y), Key(0.87f, x, y - 12f), Key(1f, x, y))
        )
    }

    private fun knockGift(): Sprite {
        val fx = sleighX(KNOCK_AT) + 70f
        val fy = sleighY(KNOCK_AT) + 10f
        val tx = knock.bx
        val ty = knock.by - knock.bh / 2f - 14f
        val flight = Phase(KNOCK_AT, NewYearKnock.FLIGHT_MS, Curves.easeIn, listOf(Key(0f, fx, fy, 0f), Key(0.05f, fx, fy), Key(1f, tx, ty)))
        val bounce = Phase(
            NewYearKnock.FALL_AT, 1400L, Curves.easeOut,
            listOf(
                Key(0f, tx, ty, 1f), Key(0.35f, tx + 50f, knock.by - 60f, 0.65f),
                Key(0.8f, tx + 80f, g - 30f, 0.2f), Key(1f, tx + 80f, g - 30f, 0f)
            )
        )
        return Sprite(NewYearActors.gift(KNOCK_GIFT), listOf(flight, bounce), bounce.endMs)
    }

    private fun animal(actor: Actor, w: Float, h: Float, from: Float, gi: Int, start: Long, dir: Int) {
        val x = gx[gi]
        val y = g - h / 2f + 4f
        val stop = x - dir * 22f
        val back = dir > 0
        val phases = listOf(
            Phase(start, 1000L, Curves.easeOut, listOf(Key(0f, from, y), Key(1f, stop, y)), flip = dir < 0),
            Phase(start + 1000L, 280L, Curves.linear, listOf(Key(0f, stop, y), Key(1f, stop, y)), flip = back, walking = false),
            Phase(start + 1280L, 1000L, Curves.easeIn, listOf(Key(0f, stop, y), Key(1f, from, y)), flip = back)
        )
        animals.add(Sprite(actor, phases, start + 2280L))
        val carry = Phase(
            start + 1280L, 1000L, Curves.easeIn,
            listOf(Key(0f, x, g - 30f), Key(0.25f, stop, g - h - 6f), Key(1f, from, g - h - 6f))
        )
        drops[gi].add(carry)
    }

    private fun thief() {
        val nx = NewYearLayout.NEST_X
        val ny = NewYearLayout.NEST_Y
        val x = gx[1]
        val dive = Phase(
            3300L, 1100L, Curves.easeIn,
            listOf(Key(0f, nx, ny - 20f), Key(0.6f, x + 40f, g - 140f), Key(1f, x, g - 52f))
        )
        val path = listOf(Key(0f, x, g - 52f), Key(0.4f, x + 60f, g - 260f), Key(0.75f, nx, ny - 30f), Key(1f, nx + 160f, ny - 280f))
        val away = Phase(4400L, 1600L, Curves.easeInOut, path)
        eagle.add(Sprite(NewYearActors.eagle(), listOf(dive, away), away.endMs, showBefore = true))
        drops[1].add(Phase(4400L, 1600L, Curves.easeInOut, path.map { Key(it.at, it.x, it.y + 26f) }))
    }

    private fun bear(): Sprite {
        val y = knock.bearY
        val phases = listOf(
            Phase(4500L, 1300L, Curves.easeOut, listOf(Key(0f, -80f, y), Key(1f, knock.fallX, y))),
            Phase(NewYearKnock.RESET_AT, 1400L, Curves.linear, listOf(Key(0f, knock.fallX, y), Key(1f, knock.fallX, y)), walking = false),
            Phase(NewYearKnock.RESET_AT + 1400L, EXIT_MS, Curves.easeIn, listOf(Key(0f, knock.fallX, y), Key(1f, -90f, y)), flip = true)
        )
        return Sprite(NewYearActors.bear(NewYearKnock.RESET_AT), phases, phases.last().endMs)
    }

    companion object {
        const val GIFTS = 4
        const val KNOCK_GIFT = 4
        const val SLEIGH_MS = 2800L
        const val DROP_MS = 800L
        const val EXIT_MS = 750L
        const val KNOCK_AT = NewYearKnock.KNOCK_AT
        const val EAGLE_BACK = 6000L
        const val TOTAL_MS = 8000L
        private const val SX0 = 470f
        private const val SX1 = -360f
        private const val SY = 160f
        private const val SPARK_FROM = 150L
        private const val SPARK_STEP = 70L
    }
}
