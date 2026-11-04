package com.yg.tokenvault.util

import java.security.SecureRandom

/**
 * 随机登录密码生成。
 *
 * 去掉容易混淆的字符（l / I / 1、O / 0），保证大小写字母、数字、符号各至少一个，
 * 再用 Fisher-Yates 洗牌打散位置 —— 这样生成的密码不会是「aaaaBBBB1234!!!!」那种形状。
 */
object PasswordGen {

    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGIT = "23456789"
    private const val SYMBOL = "!@#\$%^&*-_=+?"

    private val rnd = SecureRandom()

    fun random(length: Int = 20): String {
        val len = length.coerceIn(8, 64)
        val all = LOWER + UPPER + DIGIT + SYMBOL
        val chars = ArrayList<Char>(len)
        chars.add(LOWER[rnd.nextInt(LOWER.length)])
        chars.add(UPPER[rnd.nextInt(UPPER.length)])
        chars.add(DIGIT[rnd.nextInt(DIGIT.length)])
        chars.add(SYMBOL[rnd.nextInt(SYMBOL.length)])
        while (chars.size < len) chars.add(all[rnd.nextInt(all.length)])
        for (i in chars.indices.reversed()) {
            val j = rnd.nextInt(i + 1)
            val t = chars[i]
            chars[i] = chars[j]
            chars[j] = t
        }
        return String(chars.toCharArray())
    }
}