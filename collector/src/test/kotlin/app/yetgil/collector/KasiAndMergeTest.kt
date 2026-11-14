package app.yetgil.collector

import app.yetgil.collector.kasi.KasiApi
import app.yetgil.engine.calendar.SpecialDayKind
import app.yetgil.engine.model.Audience
import app.yetgil.engine.model.Episode
import app.yetgil.engine.model.EpisodeCategory
import app.yetgil.engine.model.GeoPoint
import app.yetgil.engine.model.License
import app.yetgil.engine.model.Place
import app.yetgil.engine.model.PlaceKind
import app.yetgil.engine.model.Script
import app.yetgil.engine.model.Scripts
import app.yetgil.engine.model.Source
import app.yetgil.engine.model.StoryPack
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KasiAndMergeTest {
    @Test
    fun `특일 정보 응답을 읽는다`() {
        val xml = javaClass.getResource("/kasi/rest-days-sample.xml")!!.readText(Charsets.UTF_8)
        val days = KasiApi.parse(xml)
        assertEquals(listOf("2026-09-24", "2026-09-25", "2026-09-23"), days.map { it.date })
        assertEquals(SpecialDayKind.HOLIDAY, days[0].kind)
        assertEquals(true, days[0].isHoliday)
        assertEquals(SpecialDayKind.SOLAR_TERM, days[2].kind)
    }

    @Test
    fun `키 오류는 예외로`() {
        val err = """<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
            <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg><returnReasonCode>30</returnReasonCode>
            </cmmMsgHeader></OpenAPI_ServiceResponse>"""
        val e = assertFailsWith<IOException> { KasiApi.parse(err) }
        assertTrue("SERVICE_KEY_IS_NOT_REGISTERED_ERROR" in e.message!!)
    }

    @Test
    fun `한 해 열두 달 공휴일과 절기를 모아 날짜순으로`() {
        val urls = mutableListOf<String>()
        val api = KasiApi({ url -> urls += url; "<response><body><items/></body></response>" }, "a+b/c")
        api.year(2026)
        assertEquals(24, urls.size)
        assertTrue(urls.all { "ServiceKey=a%2Bb%2Fc" in it })
        assertTrue(urls.any { "get24DivisionsInfo?solYear=2026&solMonth=09" in it })
    }

    private fun place(id: String, lat: Double, lon: Double, reviewed: Boolean = false) = Place(
        id, id, GeoPoint(lat, lon), PlaceKind.OTHER,
        episodes = listOf(
            Episode(
                "$id-1", 1, "t", EpisodeCategory.RECORD, Audience.TEEN, reviewed,
                Scripts(Script("가".repeat(125), 25)),
                listOf(Source("s", "p", "u", License.OTHER, false)),
            ),
        ),
    )

    @Test
    fun `이미 있는 장소는 그대로 두고 새 장소만 더한다`() {
        val edited = place("khs-1", 36.70, 126.60, reviewed = true)
        val existing = StoryPack(1, "yesan", "예산", "2026-01-01", listOf(edited))
        val drafts = listOf(
            place("khs-1", 36.70, 126.60),
            place("khs-2", 36.7001, 126.6001), // 기존 장소 바로 옆
            place("khs-3", 36.80, 126.70),
        )
        val r = PackMerger.merge(existing, drafts, "yesan", "예산", "2026-10-22")
        assertEquals(listOf("khs-1", "khs-3"), r.pack.places.map { it.id })
        assertEquals(true, r.pack.places[0].episodes[0].reviewed)
        assertEquals(listOf("khs-3"), r.added.map { it.id })
        assertEquals(2, r.alreadyThere.size)
        assertEquals("2026-10-22", r.pack.updatedAt)
    }
}
