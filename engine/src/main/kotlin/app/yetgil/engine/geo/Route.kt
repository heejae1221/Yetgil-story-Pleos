package app.yetgil.engine.geo

import app.yetgil.engine.model.GeoPoint
import kotlin.math.hypot
import kotlin.math.sqrt

enum class Side(val spoken: String) { LEFT("왼쪽"), RIGHT("오른쪽") }

/** 한 지점을 경로에 투영한 결과 */
data class RouteProjection(
    /** 경로 시작부터 투영점까지 거리 (m) */
    val alongM: Double,
    /** 경로에서 떨어진 거리 (m) */
    val lateralM: Double,
    /** 진행 방향 기준 어느 쪽인지 */
    val side: Side,
)

/**
 * 앞으로 달릴 경로 (폴리라인). 목적지가 있으면 NaviHelper 경로에서,
 * 없으면 [straightAhead] 로 진행 방향 직선을 만든다.
 */
class Route(val points: List<GeoPoint>) {
    private val cumulative: DoubleArray

    init {
        require(points.size >= 2) { "경로에는 점이 2개 이상 필요합니다" }
        cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + Geo.distanceM(points[i - 1], points[i])
        }
    }

    val lengthM: Double get() = cumulative.last()

    /** p 에서 가장 가까운 경로 위 지점을 찾는다. */
    fun project(p: GeoPoint): RouteProjection {
        var best: RouteProjection? = null
        for (i in 0 until points.size - 1) {
            val origin = points[i]
            val (bx, by) = Geo.toLocal(origin, points[i + 1])
            val (px, py) = Geo.toLocal(origin, p)
            val segLen2 = bx * bx + by * by
            val t = if (segLen2 == 0.0) 0.0 else ((px * bx + py * by) / segLen2).coerceIn(0.0, 1.0)
            val lateral = hypot(px - t * bx, py - t * by)
            if (best == null || lateral < best.lateralM) {
                // 진행 방향 벡터와의 외적이 양수면 왼쪽
                val cross = bx * py - by * px
                best = RouteProjection(
                    alongM = cumulative[i] + t * sqrt(segLen2),
                    lateralM = lateral,
                    side = if (cross > 0) Side.LEFT else Side.RIGHT,
                )
            }
        }
        return best!!
    }

    /** 경로 시작에서 alongM 만큼 간 지점 (가상 주행용). 범위를 벗어나면 양 끝점. */
    fun pointAt(alongM: Double): GeoPoint {
        val i = segmentIndex(alongM)
        val a = points[i]
        val b = points[i + 1]
        val segLen = cumulative[i + 1] - cumulative[i]
        val t = if (segLen == 0.0) 0.0 else ((alongM - cumulative[i]) / segLen).coerceIn(0.0, 1.0)
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /** alongM 지점에서의 진행 방향 (북=0, 동=90) */
    fun bearingAt(alongM: Double): Double {
        val i = segmentIndex(alongM)
        return Geo.bearingDeg(points[i], points[i + 1])
    }

    fun reversed(): Route = Route(points.reversed())

    private fun segmentIndex(alongM: Double): Int {
        for (i in 0 until points.size - 1) {
            if (alongM < cumulative[i + 1]) return i
        }
        return points.size - 2
    }

    companion object {
        /** 목적지가 없을 때: 지금 위치에서 진행 방향으로 뻗은 직선 경로 */
        fun straightAhead(from: GeoPoint, bearingDeg: Double, lengthM: Double, stepM: Double = 1_000.0): Route {
            val steps = maxOf(1, (lengthM / stepM).toInt())
            val pts = (0..steps).map { Geo.destination(from, bearingDeg, lengthM * it / steps) }
            return Route(pts)
        }
    }
}
