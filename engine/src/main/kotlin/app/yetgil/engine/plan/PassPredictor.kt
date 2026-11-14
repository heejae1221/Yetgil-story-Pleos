package app.yetgil.engine.plan

import app.yetgil.engine.geo.Route
import app.yetgil.engine.geo.Side
import app.yetgil.engine.model.Place

/** 앞으로 지나갈 장소 하나 */
data class UpcomingPass(
    val place: Place,
    /** 장소에 가장 가까워지는 경로 위 지점까지 남은 거리 (m) */
    val distanceAheadM: Double,
    /** 그 지점까지 남은 시간 (초). 차가 거의 서 있으면 무한대 */
    val etaSec: Double,
    val side: Side,
    /** 그 지점에서 장소까지 거리 (m) */
    val lateralM: Double,
)

/** 경로와 속도로 장소에 가장 가까워지는 시점과 좌우 방향을 계산한다. */
class PassPredictor(
    private val places: List<Place>,
    private val lookaheadM: Double = 30_000.0,
) {
    fun predict(route: Route, vehicleAlongM: Double, speedMps: Double): List<UpcomingPass> =
        places.mapNotNull { place ->
            val proj = route.project(place.location)
            val ahead = proj.alongM - vehicleAlongM
            when {
                ahead <= 0.0 -> null
                ahead > lookaheadM -> null
                proj.lateralM > place.triggerRadiusM -> null
                else -> UpcomingPass(
                    place = place,
                    distanceAheadM = ahead,
                    etaSec = if (speedMps < MIN_MOVING_MPS) Double.POSITIVE_INFINITY else ahead / speedMps,
                    side = proj.side,
                    lateralM = proj.lateralM,
                )
            }
        }.sortedBy { it.distanceAheadM }

    companion object {
        const val MIN_MOVING_MPS = 1.0
    }
}
