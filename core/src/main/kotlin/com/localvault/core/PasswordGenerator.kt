package com.localvault.core

import java.security.SecureRandom

object PasswordGenerator {
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{}?"

    private val random = SecureRandom()

    /**
     * Generates a random password containing at least one character from every enabled group.
     * Look-alike characters (l, 1, I, O, 0) are left out so passwords are easy to type by hand.
     */
    fun generate(
        length: Int = 20,
        uppercase: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
    ): String {
        val groups = buildList {
            add(LOWER)
            if (uppercase) add(UPPER)
            if (digits) add(DIGITS)
            if (symbols) add(SYMBOLS)
        }
        require(length >= groups.size) { "Password too short" }
        val all = groups.joinToString("")
        val chars = groups.map { it[random.nextInt(it.length)] }.toMutableList()
        repeat(length - chars.size) { chars += all[random.nextInt(all.length)] }
        // Fisher-Yates shuffle so the guaranteed characters aren't always at the start.
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return chars.joinToString("")
    }
}
