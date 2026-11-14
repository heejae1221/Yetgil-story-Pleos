package app.yetgil.engine.model

import app.yetgil.engine.Fixtures
import app.yetgil.engine.model.StoryPackValidator.Severity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoryPackTest {
    private fun errors(pack: StoryPack) = StoryPackValidator.validate(pack).filter { it.severity == Severity.ERROR }

    private val good = StoryPack(
        schemaVersion = 1,
        packId = "test",
        region = "테스트",
        updatedAt = "2026-10-22",
        places = listOf(Fixtures.place("a", Fixtures.START)),
    )

    @Test
    fun `JSON 으로 저장했다 읽어도 같다`() {
        assertEquals(good, StoryPackLoader.decode(StoryPackLoader.encode(good)))
    }

    @Test
    fun `정상 꾸러미는 오류 없음`() {
        assertEquals(emptyList(), errors(good))
    }

    @Test
    fun `규칙 위반을 잡아낸다`() {
        val ep = Fixtures.episode("a-1", shortSec = 60).copy(
            sources = listOf(Source("관광 스토리텔링", "한국관광공사", null, License.KOGL_TYPE4, modifiable = true)),
            seriesIds = listOf("nope"),
        )
        val bad = good.copy(
            places = listOf(
                Fixtures.place("a", GeoPoint(10.0, 10.0), listOf(ep, ep.copy(number = 3))),
            ),
            series = listOf(Series("s", "시리즈", listOf("ghost"))),
        )
        val paths = errors(bad).map { it.message }
        assertTrue(paths.any { "좌표" in it })
        assertTrue(paths.any { "편 번호" in it })
        assertTrue(paths.any { "id 중복" in it })
        assertTrue(paths.any { "15~40초" in it })
        assertTrue(paths.any { "변경금지" in it })
        assertTrue(paths.any { "없는 시리즈" in it })
        assertTrue(paths.any { "없는 장소" in it })
    }

    @Test
    fun `저장소의 이야기 꾸러미는 모두 읽히고 오류가 없다`() {
        val dir = File(System.getProperty("yetgil.packDir") ?: "../data/packs")
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue(files.isNotEmpty(), "꾸러미 없음: $dir")
        for (f in files) {
            val pack = StoryPackLoader.decode(f.readText(Charsets.UTF_8))
            assertEquals(emptyList(), errors(pack), f.name)
        }
    }
}
