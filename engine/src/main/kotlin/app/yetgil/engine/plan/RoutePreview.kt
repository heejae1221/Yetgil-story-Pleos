package app.yetgil.engine.plan

import app.yetgil.engine.geo.Route
import app.yetgil.engine.geo.Side
import app.yetgil.engine.model.Place
import java.time.Instant

data class RoutePreviewItem(
    val place: Place,
    /** 출발점에서 장소 옆까지 거리 (m) */
    val distanceFromStartM: Double,
    val side: Side,
    val lateralM: Double,
    val pick: EpisodePick,
)

/** ③ 목적지를 넣었을 때 '이번 길에서 만날 곳' */
object RoutePreview {
    fun build(
        route: Route,
        places: List<Place>,
        settings: StorySettings,
        history: ListeningHistory,
        now: Instant,
    ): List<RoutePreviewItem> =
        // 출발점 바로 옆 장소도 넣도록 경로 시작보다 조금 뒤에서 본다
        PassPredictor(places, lookaheadM = route.lengthM + 1.0)
            .predict(route, vehicleAlongM = -1.0, speedMps = 0.0)
            .mapNotNull { pass ->
                EpisodeSelector.pick(pass.place, settings.listenerMode, history, now, settings.reviewQuizEnabled)
                    ?.let { RoutePreviewItem(pass.place, pass.distanceAheadM - 1.0, pass.side, pass.lateralM, it) }
            }
}
