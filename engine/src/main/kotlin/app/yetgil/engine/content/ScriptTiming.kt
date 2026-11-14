package app.yetgil.engine.content

import kotlin.math.ceil

/**
 * 원고를 읽는 데 걸리는 시간 어림.
 * Gleo AI TextToSpeech 로 실측하기 전까지는 한국어 낭독 속도를 초당 5음절로 본다.
 */
object ScriptTiming {
    const val SYLLABLES_PER_SEC = 5.0

    /** 소리 내어 읽는 글자 수 (공백·문장부호 제외) */
    fun syllables(text: String): Int = text.count { it.isLetterOrDigit() }

    fun estimateSec(text: String): Int = ceil(syllables(text) / SYLLABLES_PER_SEC).toInt()

    /** 이 시간 안에 읽을 수 있는 글자 수 */
    fun syllablesFor(sec: Int): Int = (sec * SYLLABLES_PER_SEC).toInt()
}
