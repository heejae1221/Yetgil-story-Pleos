package app.yetgil.engine.interaction

enum class VoiceCommand {
    /** "그만" */
    STOP,

    /** "다시 들려줘" */
    REPLAY,

    /** "들렀다 가자" → 경유지 추가 */
    STOPOVER,

    /** "퀴즈는 됐어" */
    SKIP_QUIZ,
}

/** SpeechToText 결과를 명령으로 바꾼다. 명령이 아니면 null (퀴즈 답일 수 있다). */
object VoiceCommandParser {
    // 위에서부터 먼저 맞는 것을 쓴다. "퀴즈 그만"이 STOP 이 되지 않도록 SKIP_QUIZ 가 먼저.
    private val rules: List<Pair<VoiceCommand, List<String>>> = listOf(
        VoiceCommand.SKIP_QUIZ to listOf("퀴즈는됐", "퀴즈됐", "퀴즈그만", "퀴즈건너", "퀴즈안"),
        VoiceCommand.STOPOVER to listOf("들렀다", "들렸다", "들러", "들르자", "가보자", "경유지"),
        VoiceCommand.REPLAY to listOf("다시들려", "다시말해", "다시해", "한번더", "다시듣"),
        VoiceCommand.STOP to listOf("그만", "멈춰", "꺼줘", "조용", "됐어"),
    )

    fun parse(utterance: String): VoiceCommand? {
        val n = Text.normalize(utterance)
        if (n.isEmpty()) return null
        return rules.firstOrNull { (_, keys) -> keys.any { it in n } }?.first
    }
}

internal object Text {
    /** 공백·문장부호를 없애고 소문자로 */
    fun normalize(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }
}
