package app.yetgil.engine.interaction

import app.yetgil.engine.model.Quiz

/** 말로 한 퀴즈 답을 채점한다. "흰 쌀이요!" → 정답 "흰쌀" */
object QuizJudge {
    fun isCorrect(quiz: Quiz, utterance: String): Boolean {
        val said = Text.normalize(utterance)
        if (said.isEmpty()) return false
        return quiz.answers.map(Text::normalize).any { it.isNotEmpty() && it in said }
    }
}
