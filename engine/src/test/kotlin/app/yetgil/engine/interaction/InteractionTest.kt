package app.yetgil.engine.interaction

import app.yetgil.engine.model.Quiz
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractionTest {
    @Test
    fun `음성 명령`() {
        assertEquals(VoiceCommand.STOP, VoiceCommandParser.parse("그만"))
        assertEquals(VoiceCommand.STOP, VoiceCommandParser.parse("이제 그만해"))
        assertEquals(VoiceCommand.REPLAY, VoiceCommandParser.parse("다시 들려줘"))
        assertEquals(VoiceCommand.STOPOVER, VoiceCommandParser.parse("들렀다 가자"))
        assertEquals(VoiceCommand.SKIP_QUIZ, VoiceCommandParser.parse("퀴즈는 됐어"))
        assertEquals(VoiceCommand.SKIP_QUIZ, VoiceCommandParser.parse("퀴즈 그만"))
        assertNull(VoiceCommandParser.parse("흰쌀"))
        assertNull(VoiceCommandParser.parse("  "))
    }

    private val quiz = Quiz("권율 장군은 물 대신 무엇을 끼얹었을까요?", listOf("흰쌀", "쌀"), "")

    @Test
    fun `말로 한 퀴즈 답 채점`() {
        assertTrue(QuizJudge.isCorrect(quiz, "흰 쌀이요!"))
        assertTrue(QuizJudge.isCorrect(quiz, "쌀"))
        assertFalse(QuizJudge.isCorrect(quiz, "물"))
        assertFalse(QuizJudge.isCorrect(quiz, ""))
    }
}
