package app.yetgil.engine.content

import app.yetgil.engine.Fixtures
import app.yetgil.engine.model.Script
import app.yetgil.engine.model.StoryPack
import app.yetgil.engine.model.StoryPackValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptTimingTest {
    @Test
    fun `공백과 문장부호는 세지 않는다`() {
        assertEquals(10, ScriptTiming.syllables("즐거운 한가위, 보내세요!"))
        assertEquals(2, ScriptTiming.estimateSec("즐거운 한가위 보내세요."))
        assertEquals(125, ScriptTiming.syllablesFor(25))
    }

    @Test
    fun `글 길이와 durationSec 이 크게 다르면 경고`() {
        val ep = Fixtures.episode("a-1").let { it.copy(scripts = it.scripts.copy(short = Script("너무 짧은 글", 25))) }
        val pack = StoryPack(1, "t", "t", "2026-10-22", listOf(Fixtures.place("a", Fixtures.START, listOf(ep))))
        val warnings = StoryPackValidator.validate(pack).filter { it.severity == StoryPackValidator.Severity.WARNING }
        assertTrue(warnings.any { it.path.endsWith("scripts.short") && "낭독 시간" in it.message }, warnings.toString())
    }
}
