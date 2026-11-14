package app.yetgil.engine.plan

import app.yetgil.engine.Fixtures
import app.yetgil.engine.Fixtures.place
import app.yetgil.engine.Fixtures.pointBeside
import app.yetgil.engine.geo.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PassPredictorTest {
    private val near = place("near", pointBeside(5_000.0, -750.0))
    private val far = place("far", pointBeside(12_000.0, 1_000.0))
    private val behind = place("behind", pointBeside(1_000.0, 500.0))
    private val offRoute = place("off-route", pointBeside(8_000.0, 6_000.0))

    private val predictor = PassPredictor(listOf(far, offRoute, behind, near))

    @Test
    fun `앞에 있고 반경 안인 장소만 가까운 순서로`() {
        val passes = predictor.predict(Fixtures.northRoute, vehicleAlongM = 2_000.0, speedMps = 25.0)
        assertEquals(listOf("near", "far"), passes.map { it.place.id })
    }

    @Test
    fun `남은 거리와 속도로 도착 시간과 방향을 계산한다`() {
        val pass = predictor.predict(Fixtures.northRoute, 2_000.0, 25.0).first()
        assertEquals(3_000.0, pass.distanceAheadM, 20.0)
        assertEquals(120.0, pass.etaSec, 1.0)
        assertEquals(Side.LEFT, pass.side)
    }

    @Test
    fun `내다보는 거리 밖은 뺀다`() {
        val short = PassPredictor(listOf(near, far), lookaheadM = 5_000.0)
        assertEquals(listOf("near"), short.predict(Fixtures.northRoute, 2_000.0, 25.0).map { it.place.id })
    }

    @Test
    fun `차가 거의 서 있으면 도착 시간은 무한대`() {
        val pass = predictor.predict(Fixtures.northRoute, 2_000.0, 0.2).first()
        assertTrue(pass.etaSec.isInfinite())
    }
}
