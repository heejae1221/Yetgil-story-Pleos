package app.yetgil.collector.khs

import app.yetgil.engine.geo.Geo
import app.yetgil.engine.geo.Route
import app.yetgil.engine.model.Place

/**
 * 경로를 따라 corridorM 안에 있는 국가유산을 모아 초안 장소로 만든다.
 * 한 절 안의 여러 문화유산처럼 가까이 모인 것은 종목 순서(kindCodes)가 앞선 하나만 남긴다.
 */
class HeritageCollector(
    private val api: KhsApi,
    private val builder: DraftBuilder,
    private val log: (String) -> Unit = {},
) {
    data class Result(val places: List<Place>, val skipped: List<DraftBuilder.Outcome.Skipped>)

    fun collect(
        route: Route,
        sidoCodes: List<String>,
        kindCodes: List<String> = DEFAULT_KIND_CODES,
        corridorM: Double = DEFAULT_CORRIDOR_M,
    ): Result {
        val near = mutableListOf<KhsListItem>()
        for (sido in sidoCodes) {
            for (kind in kindCodes) {
                val items = listAll(sido, kind)
                val hits = items.filter { item ->
                    !item.cancelled && item.location != null && route.project(item.location).lateralM <= corridorM
                }
                log("시도 $sido · 종목 $kind: ${items.size}건 중 경로 근처 ${hits.size}건")
                near += hits
            }
        }

        val skipped = mutableListOf<DraftBuilder.Outcome.Skipped>()
        val drafts = mutableListOf<Pair<KhsListItem, Place>>()
        for (item in near.distinctBy { it.key }) {
            val detail = api.detail(item)
            if (!isPlace(detail)) {
                skipped += DraftBuilder.Outcome.Skipped(item.key, item.name, "장소가 아닌 유물 (${detail.category})")
                continue
            }
            when (val outcome = builder.build(detail)) {
                is DraftBuilder.Outcome.Built -> drafts += item to outcome.place
                is DraftBuilder.Outcome.Skipped -> skipped += outcome
            }
        }

        val kept = mutableListOf<Place>()
        for ((item, place) in drafts.sortedBy { (item, _) -> kindCodes.indexOf(item.kindCode) }) {
            val dup = kept.firstOrNull { Geo.distanceM(it.location, place.location) < CLUSTER_RADIUS_M }
            if (dup != null) {
                skipped += DraftBuilder.Outcome.Skipped(item.key, item.name, "${dup.name} 와 같은 곳")
            } else {
                kept += place
            }
        }
        // 경로를 따라 만나는 순서로
        return Result(kept.sortedBy { route.project(it.location).alongM }, skipped)
    }

    private fun listAll(sido: String, kind: String): List<KhsListItem> {
        val all = mutableListOf<KhsListItem>()
        var page = 1
        while (true) {
            val res = api.list(sido, kind, page, PAGE_SIZE)
            all += res.items
            if (res.items.isEmpty() || all.size >= res.total) return all
            page++
        }
    }

    companion object {
        /** 국보, 사적, 보물, 명승 */
        val DEFAULT_KIND_CODES = listOf("11", "13", "12", "16")
        const val DEFAULT_CORRIDOR_M = 3_000.0
        const val CLUSTER_RADIUS_M = 300.0
        const val PAGE_SIZE = 100

        /** 차창 밖으로 지나는 '곳'만. 불상·그림 같은 유물은 뺀다. */
        fun isPlace(detail: KhsDetail): Boolean =
            detail.category == "유적건조물" || detail.category == "자연유산" ||
                detail.item.kindCode == "13" || detail.item.kindCode == "16"
    }
}
