package app.yetgil.engine

import app.yetgil.engine.geo.Geo
import app.yetgil.engine.geo.Route
import app.yetgil.engine.model.Audience
import app.yetgil.engine.model.Episode
import app.yetgil.engine.model.EpisodeCategory
import app.yetgil.engine.model.GeoPoint
import app.yetgil.engine.model.License
import app.yetgil.engine.model.Place
import app.yetgil.engine.model.PlaceKind
import app.yetgil.engine.model.Quiz
import app.yetgil.engine.model.Script
import app.yetgil.engine.model.Scripts
import app.yetgil.engine.model.Source
import java.time.Instant

/** 테스트용 가상 지형: 출발점에서 정북으로 30km 뻗은 직선 도로 */
object Fixtures {
    val START = GeoPoint(36.60, 126.60)
    val NOW: Instant = Instant.parse("2026-09-25T09:00:00Z")

    val northRoute: Route = Route.straightAhead(START, bearingDeg = 0.0, lengthM = 30_000.0)

    /** 도로를 따라 aheadM 북쪽, 동(+)/서(-)로 offsetM 떨어진 지점 */
    fun pointBeside(aheadM: Double, offsetM: Double): GeoPoint {
        val onRoad = Geo.destination(START, 0.0, aheadM)
        return if (offsetM >= 0) Geo.destination(onRoad, 90.0, offsetM) else Geo.destination(onRoad, 270.0, -offsetM)
    }

    val source = Source("국가유산청 국가유산 정보", "국가유산청", "https://www.khs.go.kr", License.KOGL_TYPE1, modifiable = true)

    fun episode(
        id: String,
        number: Int = 1,
        minAudience: Audience = Audience.CHILD,
        reviewed: Boolean = true,
        shortSec: Int = 25,
        longSec: Int? = 90,
        parkedSec: Int? = null,
        quiz: Quiz? = Quiz("질문?", listOf("정답"), "설명"),
    ) = Episode(
        id = id,
        number = number,
        title = "$id 제목",
        category = EpisodeCategory.FOLKLORE,
        minAudience = minAudience,
        reviewed = reviewed,
        scripts = Scripts(
            short = Script("$id 짧은 이야기", shortSec),
            long = longSec?.let { Script("$id 긴 이야기", it) },
            parked = parkedSec?.let { Script("$id 정차 이야기", it) },
        ),
        sources = listOf(source),
        quiz = quiz,
    )

    fun place(
        id: String,
        location: GeoPoint,
        episodes: List<Episode> = listOf(episode("$id-1")),
        triggerRadiusM: Int = 3_000,
    ) = Place(id, id, location, PlaceKind.OTHER, triggerRadiusM, episodes)
}
