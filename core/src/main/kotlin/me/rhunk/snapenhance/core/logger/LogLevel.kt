package me.rhunk.snapenhance.core.logger

import android.util.Log

enum class LogLevel(
    val letter: String,
    val shortName: String,
    val priority: Int = Log.INFO
) {
    VERBOSE("V", "verbose", Log.VERBOSE),
    DEBUG("D", "debug", Log.DEBUG),
    INFO("I", "info", Log.INFO),
    WARN("W", "warn", Log.WARN),
    ERROR("E", "error", Log.ERROR),
    ASSERT("A", "assert", Log.ASSERT);

    companion object {
        fun fromLetter(letter: String): LogLevel? {
            return values().find { it.letter == letter }
        }

        fun fromShortName(shortName: String): LogLevel? {
            return values().find { it.shortName == shortName }
        }

        fun fromPriority(priority: Int): LogLevel? {
            return values().find { it.priority == priority }
        }
    }
}