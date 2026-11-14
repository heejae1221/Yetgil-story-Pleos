package app.yetgil.engine.geo

import app.yetgil.engine.Fixtures
import app.yetgil.engine.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoTest {
    @Test
    fun `서울시청에서 부산시청까지 직선거리는 약 325km`() {
        val d = Geo.distanceM(GeoPoint(37.5663, 126.9779), GeoPoint(35.1798, 129.0750))
        assertTrue(d in 320_000.0..330_000.0, "d=$d")
    }

    @Test
    fun `destination 으로 간 거리만큼 distance 가 나온다`() {
        val p = Geo.destination(Fixtures.START, 45.0, 12_345.0)
        assertEquals(12_345.0, Geo.distanceM(Fixtures.START, p), 1.0)
    }

    @Test
    fun `북쪽 도로에서 동쪽은 오른쪽, 서쪽은 왼쪽`() {
        val route = Fixtures.northRoute
        val east = route.project(Fixtures.pointBeside(10_000.0, 800.0))
        val west = route.project(Fixtures.pointBeside(10_000.0, -800.0))
        assertEquals(Side.RIGHT, east.side)
        assertEquals(Side.LEFT, west.side)
        assertEquals(800.0, east.lateralM, 5.0)
        assertEquals(10_000.0, east.alongM, 20.0)
    }

    @Test
    fun `경로 위 지점과 진행 방향`() {
        val corner = Geo.destination(Fixtures.START, 0.0, 5_000.0)
        val route = Route(listOf(Fixtures.START, corner, Geo.destination(corner, 90.0, 5_000.0)))
        assertEquals(0.0, Geo.distanceM(route.pointAt(5_000.0), corner), 5.0)
        assertEquals(0.0, angleDiff(route.bearingAt(1_000.0), 0.0), 0.5)
        assertEquals(0.0, angleDiff(route.bearingAt(6_000.0), 90.0), 0.5)
        assertEquals(7_000.0, route.project(route.pointAt(7_000.0)).alongM, 10.0)
        assertEquals(0.0, Geo.distanceM(route.points.last(), route.pointAt(99_999.0)), 0.01)
    }

    private fun angleDiff(a: Double, b: Double) = (a - b + 540.0) % 360.0 - 180.0

    @Test
    fun `꺾인 경로에서도 가장 가까운 구간에 투영한다`() {
        // 북쪽으로 5km 간 뒤 동쪽으로 5km
        val corner = Geo.destination(Fixtures.START, 0.0, 5_000.0)
        val end = Geo.destination(corner, 90.0, 5_000.0)
        val route = Route(listOf(Fixtures.START, corner, end))
        // 두 번째 구간 2km 지점의 북쪽 500m → 진행 방향(동) 기준 왼쪽
        val p = Geo.destination(Geo.destination(corner, 90.0, 2_000.0), 0.0, 500.0)
        val proj = route.project(p)
        assertEquals(7_000.0, proj.alongM, 30.0)
        assertEquals(500.0, proj.lateralM, 10.0)
        assertEquals(Side.LEFT, proj.side)
    }
}
