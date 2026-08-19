package wang.harlon.eventbase

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun systemClock(): Clock = Clock { (NSDate().timeIntervalSince1970 * 1000).toLong() }
