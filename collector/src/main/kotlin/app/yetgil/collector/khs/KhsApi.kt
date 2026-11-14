package app.yetgil.collector.khs

import app.yetgil.collector.HttpGet
import app.yetgil.collector.Xml
import app.yetgil.collector.Xml.children
import app.yetgil.collector.Xml.text
import app.yetgil.engine.model.GeoPoint
import java.net.URLEncoder

/** 국가유산 목록의 한 줄 */
data class KhsListItem(
    /** 종목 코드 (11 국보, 12 보물, 13 사적, 15 천연기념물, 16 명승, …) */
    val kindCode: String,
    /** 시도 코드 (11 서울, 31 경기, 34 충남, …) */
    val sidoCode: String,
    val assetNo: String,
    val kindName: String,
    val name: String,
    val sigungu: String,
    val location: GeoPoint?,
    /** 지정 해제 여부 */
    val cancelled: Boolean,
) {
    val key: String get() = "$kindCode-$sidoCode-$assetNo"
}

data class KhsDetail(
    val item: KhsListItem,
    /** 분류: 유적건조물, 유물, 기록유산, 자연유산 … */
    val category: String?,
    /** 중분류 (예: 종교신앙) */
    val subCategory: String?,
    /** 소분류 (예: 불교) */
    val kind: String?,
    val detailKind: String?,
    val era: String?,
    val address: String?,
    val imageUrl: String?,
    val content: String,
    val sourceUrl: String,
)

data class KhsPage(val total: Int, val items: List<KhsListItem>)

/**
 * 국가유산청 국가유산 검색 OpenAPI (키 필요 없음, XML).
 * 앱에서 직접 부르지 않고 이 수집 도구가 서버에서 모아 꾸러미로 만든다.
 */
class KhsApi(private val http: HttpGet) {

    fun list(sidoCode: String, kindCode: String, page: Int, pageSize: Int = 100): KhsPage =
        parseList(http.get(listUrl(sidoCode, kindCode, page, pageSize)))

    fun detail(item: KhsListItem): KhsDetail {
        val url = detailUrl(item)
        return parseDetail(http.get(url), item, url)
    }

    companion object {
        const val BASE = "https://www.khs.go.kr/cha"

        fun listUrl(sidoCode: String, kindCode: String, page: Int, pageSize: Int): String =
            "$BASE/SearchKindOpenapiList.do?pageUnit=$pageSize&pageIndex=$page" +
                "&ccbaCtcd=${enc(sidoCode)}&ccbaKdcd=${enc(kindCode)}"

        fun detailUrl(item: KhsListItem): String =
            "$BASE/SearchKindOpenapiDt.do?ccbaKdcd=${enc(item.kindCode)}" +
                "&ccbaAsno=${enc(item.assetNo)}&ccbaCtcd=${enc(item.sidoCode)}"

        fun parseList(xml: String): KhsPage {
            val root = Xml.parse(xml).documentElement
            val items = root.children("item").map { e ->
                KhsListItem(
                    kindCode = e.text("ccbaKdcd").orEmpty(),
                    sidoCode = e.text("ccbaCtcd").orEmpty(),
                    assetNo = e.text("ccbaAsno").orEmpty(),
                    kindName = e.text("ccmaName").orEmpty(),
                    name = e.text("ccbaMnm1").orEmpty(),
                    sigungu = e.text("ccsiName").orEmpty(),
                    location = point(e.text("latitude"), e.text("longitude")),
                    cancelled = e.text("ccbaCncl") == "Y",
                )
            }
            return KhsPage(root.text("totalCnt")?.toIntOrNull() ?: items.size, items)
        }

        fun parseDetail(xml: String, item: KhsListItem, sourceUrl: String): KhsDetail {
            val root = Xml.parse(xml).documentElement
            val e = root.children("item").firstOrNull() ?: error("상세 정보가 없습니다: ${item.key}")
            return KhsDetail(
                // 상세의 좌표가 더 최신일 수 있다
                item = item.copy(location = point(root.text("latitude"), root.text("longitude")) ?: item.location),
                category = e.text("gcodeName"),
                subCategory = e.text("bcodeName"),
                kind = e.text("mcodeName"),
                detailKind = e.text("scodeName"),
                era = e.text("ccceName"),
                address = e.text("ccbaLcad"),
                imageUrl = e.text("imageUrl"),
                content = e.text("content").orEmpty(),
                sourceUrl = sourceUrl,
            )
        }

        private fun point(lat: String?, lon: String?): GeoPoint? {
            val la = lat?.toDoubleOrNull() ?: return null
            val lo = lon?.toDoubleOrNull() ?: return null
            // 좌표가 없는 항목은 0 으로 오기도 한다
            return if (la == 0.0 || lo == 0.0) null else GeoPoint(la, lo)
        }

        private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
    }
}
