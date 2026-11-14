package app.yetgil.collector

import app.yetgil.collector.khs.DraftBuilder
import app.yetgil.collector.khs.HeritageCollector
import app.yetgil.collector.khs.KhsApi
import app.yetgil.engine.geo.Route
import app.yetgil.engine.model.GeoPoint
import app.yetgil.engine.model.PlaceKind
import app.yetgil.engine.model.StoryPack
import app.yetgil.engine.model.StoryPackValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KhsTest {
    private fun resource(path: String) =
        javaClass.getResource("/$path")!!.readText(Charsets.UTF_8)

    // 국가유산청 API 에서 받은 실제 응답 (2026-10-22)
    private val list = KhsApi.parseList(resource("khs/list-sudeoksa.xml"))
    private val daeungjeon = list.items.first()
    private val detail = KhsApi.parseDetail(resource("khs/detail-sudeoksa-daeungjeon.xml"), daeungjeon, "https://example/dt")

    @Test
    fun `목록 응답을 읽는다`() {
        assertEquals(9, list.total)
        assertEquals(3, list.items.size)
        with(daeungjeon) {
            assertEquals("예산 수덕사 대웅전", name)
            assertEquals("11-34-0000490000000", key)
            assertEquals("국보", kindName)
            assertEquals(36.66328, location!!.lat, 1e-5)
            assertEquals(false, cancelled)
        }
    }

    @Test
    fun `상세 응답을 읽는다`() {
        assertEquals("유적건조물", detail.category)
        assertEquals("불교", detail.kind)
        assertEquals("충청남도 예산군 수덕사안길 79 (덕산면, 수덕사)", detail.address)
        assertTrue(detail.content.startsWith("수덕사는 덕숭산에 자리잡고 있는 절로"))
        assertNotNull(detail.imageUrl)
    }

    @Test
    fun `설명문 앞부분을 그대로 잘라 검수 전 초안을 만든다`() {
        val place = assertIs<DraftBuilder.Outcome.Built>(DraftBuilder().build(detail)).place
        assertEquals("khs-11-34-0000490000000", place.id)
        assertEquals("수덕사 대웅전", place.name)
        assertEquals(PlaceKind.TEMPLE, place.kind)

        val ep = place.episodes.single()
        assertEquals(false, ep.reviewed)
        assertTrue(ep.scripts.short.durationSec in StoryPackValidator.SHORT_RANGE_SEC)
        assertTrue(ep.scripts.long!!.durationSec in StoryPackValidator.LONG_RANGE_SEC)
        // 고쳐 쓰지 않고 원문 문장을 그대로 쓴다
        val flat = detail.content.replace(Regex("\\s+"), " ")
        assertTrue(flat.startsWith(ep.scripts.short.text))
        assertTrue(flat.startsWith(ep.scripts.long!!.text))
        assertEquals(false, ep.sources.single().modifiable)

        val pack = StoryPack(1, "t", "t", "2026-10-22", listOf(place))
        val errors = StoryPackValidator.validate(pack).filter { it.severity == StoryPackValidator.Severity.ERROR }
        assertEquals(emptyList(), errors)
    }

    @Test
    fun `분량에 맞게 문장 단위로 자른다`() {
        val s = DraftBuilder.sentences("가나다라마. 바사아자차카! 타파하 가나다라?  마바사아자차카타파하.")
        assertEquals(4, s.size)
        // 첫 문장부터 범위를 넘으면 자를 수 없다
        assertNull(DraftBuilder.excerpt(listOf("가".repeat(300) + "."), 15..40, 25))
        val picked = DraftBuilder.excerpt(List(10) { "가".repeat(30) + "." }, 15..40, 25)!!
        assertEquals(24, picked.durationSec)
    }

    @Test
    fun `지역 이름을 떼고 종류를 정한다`() {
        assertEquals("수덕사 대웅전", DraftBuilder.shortName("예산 수덕사 대웅전", "예산군"))
        assertEquals("독산성과 세마대지", DraftBuilder.shortName("오산 독산성과 세마대지", "오산시"))
        assertEquals("경복궁", DraftBuilder.shortName("경복궁", "종로구"))

        fun kindOf(name: String, category: String = "유적건조물") = DraftBuilder.kind(
            detail.copy(item = daeungjeon.copy(name = name), kind = null, detailKind = null, category = category),
        )
        assertEquals(PlaceKind.FORTRESS, kindOf("오산 독산성과 세마대지"))
        assertEquals(PlaceKind.ROYAL_TOMB, kindOf("공주 무령왕릉과 왕릉원"))
        assertEquals(PlaceKind.MOUNTAIN_PASS, kindOf("문경 새재"))
        assertEquals(PlaceKind.MONUMENT, kindOf("서울 숭례문"))
        assertEquals(PlaceKind.MONUMENT, kindOf("예산 윤봉길 의사 유적"))
        assertEquals(PlaceKind.TEMPLE, kindOf("서산 개심사"))
    }

    @Test
    fun `경로 근처의 장소만, 가까이 모인 것은 하나만 남긴다`() {
        // 북쪽으로 20km 뻗은 도로
        val route = Route(listOf(GeoPoint(36.60, 126.60), GeoPoint(36.78, 126.60)))
        fun item(no: String, kind: String, name: String, lat: Double, lon: Double) =
            """<item><ccbaKdcd>$kind</ccbaKdcd><ccbaCtcd>34</ccbaCtcd><ccbaAsno>$no</ccbaAsno>
               <ccmaName>x</ccmaName><ccbaMnm1>$name</ccbaMnm1><ccsiName>예산군</ccsiName>
               <ccbaCncl>N</ccbaCncl><latitude>$lat</latitude><longitude>$lon</longitude></item>"""
        val pages = mapOf(
            "11" to listOf(item("1", "11", "예산 가 절 대웅전", 36.70, 126.61), item("2", "11", "예산 먼 탑", 36.70, 126.80)),
            "12" to listOf(item("3", "12", "가 절 불상", 36.70, 126.611), item("4", "12", "가 절 삼층석탑", 36.7005, 126.6105)),
        )
        val content = "옛 절이다. " + "이 절은 아주 오래전에 지어졌고 여러 번 고쳐 지었다고 전한다. ".repeat(3)
        val http = HttpGet { url ->
            when {
                "SearchKindOpenapiList" in url -> {
                    val kind = Regex("ccbaKdcd=(\\d+)").find(url)!!.groupValues[1]
                    val items = pages[kind].orEmpty()
                    "<result><totalCnt>${items.size}</totalCnt>${items.joinToString("")}</result>"
                }
                else -> {
                    val category = if ("ccbaAsno=3" in url) "유물" else "유적건조물"
                    "<result><item><gcodeName>$category</gcodeName><content>$content</content></item></result>"
                }
            }
        }
        val result = HeritageCollector(KhsApi(http), DraftBuilder()).collect(route, listOf("34"), listOf("11", "12"))
        assertEquals(listOf("가 절 대웅전"), result.places.map { it.name })
        val reasons = result.skipped.associate { it.name to it.reason }
        assertTrue("유물" in reasons.getValue("가 절 불상"))
        assertTrue("같은 곳" in reasons.getValue("가 절 삼층석탑"))
        assertTrue("예산 먼 탑" !in reasons, "경로에서 먼 곳은 상세 정보도 받지 않는다")
    }

    @Test
    fun `서비스 키는 로그에 남기지 않는다`() {
        assertEquals("https://x/y?a=1&ServiceKey=***&b=2", JdkHttpGet.redact("https://x/y?a=1&ServiceKey=abc%2B123&b=2"))
    }
}
