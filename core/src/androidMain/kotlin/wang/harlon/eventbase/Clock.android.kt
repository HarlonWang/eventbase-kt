package wang.harlon.eventbase

internal actual fun systemClock(): Clock = Clock { System.currentTimeMillis() }
