package app.yetgil.collector.kasi

import app.yetgil.collector.HttpGet
import app.yetgil.collector.Xml
import app.yetgil.collector.Xml.children
import app.yetgil.collector.Xml.text
import app.yetgil.engine.calendar.SpecialDay
import app.yetgil.engine.calendar.SpecialDayKind
import java.io.IOException
import java.net.URLEncoder

/**
 * 한국천문연구원 특일 정보 (공공데이터포털, 서비스 키 필요).
 * 공휴일과 24절기를 받아 data/special-days/<연도>.json 으로 만든다.
 */
class KasiApi(private val http: HttpGet, private val serviceKey: String) {

    enum class Operation(val path: String) {
        REST_DAYS("getRestDeInfo"),
        SOLAR_TERMS("get24DivisionsInfo"),
    }

    fun year(year: Int): List<SpecialDay> =
        Operation.entries
            .flatMap { op -> (1..12).flatMap { month -> parse(http.get(url(op, year, month))) } }
            .distinctBy { it.date to it.name }
            .sortedBy { it.date }

    fun url(op: Operation, year: Int, month: Int): String =
        "$BASE/${op.path}?solYear=$year&solMonth=${"%02d".format(month)}&numOfRows=50" +
            "&ServiceKey=${encodedKey()}"

    // 포털은 인코딩된 키와 디코딩된 키를 함께 준다. 어느 쪽을 넣어도 되게 한다.
    private fun encodedKey() = if ('%' in serviceKey) serviceKey else URLEncoder.encode(serviceKey, Charsets.UTF_8)

    companion object {
        const val BASE = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService"

        fun parse(xml: String): List<SpecialDay> {
            val root = Xml.parse(xml).documentElement
            // 키 오류 등은 다른 형식(OpenAPI_ServiceResponse)으로 온다
            root.text("returnAuthMsg")?.let { throw IOException("특일 정보 API 오류: $it") }
            val code = root.text("resultCode")
            if (code != null && code != "00") throw IOException("특일 정보 API 오류 $code: ${root.text("resultMsg")}")

            return root.children("item").mapNotNull { e ->
                val locdate = e.text("locdate") ?: return@mapNotNull null
                SpecialDay(
                    date = "${locdate.substring(0, 4)}-${locdate.substring(4, 6)}-${locdate.substring(6, 8)}",
                    name = e.text("dateName") ?: return@mapNotNull null,
                    kind = when (e.text("dateKind")) {
                        "01" -> SpecialDayKind.HOLIDAY
                        "02" -> SpecialDayKind.ANNIVERSARY
                        "03" -> SpecialDayKind.SOLAR_TERM
                        else -> SpecialDayKind.SEASONAL_DAY
                    },
                    isHoliday = e.text("isHoliday") == "Y",
                )
            }
        }
    }
}
