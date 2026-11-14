package app.yetgil.engine.content

import app.yetgil.engine.Fixtures
import app.yetgil.engine.Fixtures.NOW
import app.yetgil.engine.model.GeoPoint
import app.yetgil.engine.model.License
import app.yetgil.engine.model.Series
import app.yetgil.engine.model.Source
import app.yetgil.engine.model.StoryPack
import app.yetgil.engine.plan.ListenRecord
import app.yetgil.engine.plan.ListeningHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ContentTest {
    private val original = "1592년 임진왜란 때 권율 장군이 독산성에서 왜군을 막아 냈다."
    private val open = listOf(Fixtures.source)
    private val locked = listOf(Source("관광 스토리텔링", "한국관광공사", null, License.KOGL_TYPE3, modifiable = false))

    @Test
    fun `원문 숫자만 쓴 각색은 통과`() {
        val r = AdaptationGuard.check(original, "1592년, 권율 장군이 독산성에서 왜군을 막았어요.", open)
        assertIs<AdaptationGuard.Result.Accepted>(r)
    }

    @Test
    fun `원문에 없는 숫자가 생기면 원문으로`() {
        val r = AdaptationGuard.check(original, "1592년, 권율 장군이 군사 2000명으로 왜군을 막았어요.", open)
        assertIs<AdaptationGuard.Result.Rejected>(r)
        assertEquals(AdaptationGuard.Reason.NEW_NUMBER, r.reason)
        assertEquals(original, r.text)
    }

    @Test
    fun `변경금지 출처는 각색본을 쓰지 않는다`() {
        val r = AdaptationGuard.check(original, "권율 장군이 왜군을 막았어요.", locked)
        assertEquals(AdaptationGuard.Reason.UNMODIFIABLE_SOURCE, (r as AdaptationGuard.Result.Rejected).reason)
        assertEquals(original, r.text)
    }

    @Test
    fun `너무 길어지면 원문으로`() {
        val r = AdaptationGuard.check(original, original + " 그리고 아주 길게 덧붙인 설명이 계속 이어집니다.", open)
        assertEquals(AdaptationGuard.Reason.TOO_LONG, (r as AdaptationGuard.Result.Rejected).reason)
    }

    @Test
    fun `도감 시리즈 수집과 교과 주제`() {
        val a = Fixtures.place("a", GeoPoint(36.7, 126.6)).copy(curriculumTopics = listOf("임진왜란"))
        val b = Fixtures.place("b", GeoPoint(36.8, 126.6)).copy(curriculumTopics = listOf("임진왜란", "문화유산"))
        val c = Fixtures.place("c", GeoPoint(36.9, 126.6))
        val pack = StoryPack(1, "t", "t", "2026-10-22", listOf(a, b, c), listOf(Series("s", "임진왜란", listOf("a", "c"))))
        val history = ListeningHistory(listOf(ListenRecord("a-1", "a", NOW), ListenRecord("b-1", "b", NOW)))

        assertEquals(1, Collection.seriesProgress(pack, history).single().collected)
        assertEquals(mapOf("임진왜란" to 2, "문화유산" to 1), Collection.curriculumTopics(pack, history))
    }
}
