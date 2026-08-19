package wang.harlon.eventbase

fun interface Clock {
    fun now(): Long
}

internal expect fun systemClock(): Clock
