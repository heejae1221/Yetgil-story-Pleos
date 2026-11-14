package app.yetgil.engine.plan

import app.yetgil.engine.model.Script
import app.yetgil.engine.model.Scripts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LengthPlannerTest {
    private val both = Scripts(short = Script("짧은", 25), long = Script("긴", 90))

    @Test
    fun `시간이 넉넉하면 90초 분량을 지나기 전에 끝나게 시작한다`() {
        val plan = LengthPlanner.plan(etaSec = 300.0, scripts = both)!!
        assertEquals(StoryLength.LONG, plan.length)
        // 4초 안내 + 90초 + 5초 여유 = 99초 → 201초 뒤 시작
        assertEquals(201.0, plan.startInSec, 0.001)
        assertEquals(300.0, plan.startInSec + LengthPlanner.requiredSec(plan.script), 0.001)
    }

    @Test
    fun `90초가 안 들어가면 25초 분량`() {
        val plan = LengthPlanner.plan(etaSec = 60.0, scripts = both)!!
        assertEquals(StoryLength.SHORT, plan.length)
        assertEquals(26.0, plan.startInSec, 0.001)
    }

    @Test
    fun `25초도 안 들어가면 들려주지 않는다`() {
        assertNull(LengthPlanner.plan(etaSec = 30.0, scripts = both))
        assertNull(LengthPlanner.plan(etaSec = Double.POSITIVE_INFINITY, scripts = both))
    }

    @Test
    fun `긴 분량이 없으면 짧은 것만 쓴다`() {
        val plan = LengthPlanner.plan(300.0, Scripts(short = Script("짧은", 25)))!!
        assertEquals(StoryLength.SHORT, plan.length)
    }
}
