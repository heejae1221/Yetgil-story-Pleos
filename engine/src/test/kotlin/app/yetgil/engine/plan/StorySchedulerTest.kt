package app.yetgil.engine.plan

import app.yetgil.engine.Fixtures
import app.yetgil.engine.Fixtures.NOW
import app.yetgil.engine.Fixtures.episode
import app.yetgil.engine.Fixtures.place
import app.yetgil.engine.Fixtures.pointBeside
import app.yetgil.engine.geo.Side
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorySchedulerTest {
    // 25 m/s(90 km/h) 로 북쪽으로 달린다. 출발점 기준.
    private val speed = 25.0

    // 500m 앞: 20초 → 너무 가까워 못 들려줌
    private val tooClose = place("too-close", pointBeside(500.0, 300.0))

    // 2km 앞 서쪽: 80초 → 25초 분량
    private val sudeoksa = place("수덕사", pointBeside(2_000.0, -700.0))

    // 6km 앞: 240초 → 90초 분량
    private val later = place("later", pointBeside(6_000.0, 900.0))

    private fun scheduler(vararg places: app.yetgil.engine.model.Place, settings: StorySettings = StorySettings()) =
        StoryScheduler(PassPredictor(places.toList()), settings)

    @Test
    fun `통과 전에 끝낼 수 있는 가장 가까운 장소를 고른다`() {
        val plan = scheduler(tooClose, sudeoksa, later)
            .next(Fixtures.northRoute, 0.0, speed, ListeningHistory(), NOW)!!
        assertEquals("수덕사", plan.place.id)
        assertEquals(StoryLength.SHORT, plan.timing.length)
        assertEquals(Side.LEFT, plan.side)
        assertEquals("잠시 후 왼쪽으로 수덕사를 지나요", plan.announcement)
        assertTrue(plan.timing.startInSec + LengthPlanner.requiredSec(plan.timing.script) <= plan.etaSec + 1e-6)
    }

    @Test
    fun `최소 간격이 안 지났으면 그 뒤에 시작해도 되는 장소로`() {
        // 2분 전에 이야기가 끝남, 보통(8분) → 6분(360초) 기다려야 한다
        val plan = scheduler(sudeoksa, later, place("far", pointBeside(15_000.0, 500.0)))
            .next(Fixtures.northRoute, 0.0, speed, ListeningHistory(), NOW, NOW - Duration.ofMinutes(2))!!
        assertEquals("far", plan.place.id)
        assertTrue(plan.timing.startInSec >= 360.0)
    }

    @Test
    fun `다 들은 곳은 아이 모드에서 복습 퀴즈로`() {
        val history = ListeningHistory(listOf(ListenRecord("수덕사-1", "수덕사", NOW - Duration.ofDays(200))))
        val plan = scheduler(sudeoksa).next(Fixtures.northRoute, 0.0, speed, history, NOW)!!
        assertIs<EpisodePick.ReviewQuiz>(plan.pick)
        assertTrue(plan.timing.script.text.startsWith("기억나요?"))
        assertTrue(!plan.quizAfter(ListenerMode.WITH_CHILD))
    }

    @Test
    fun `새 편에 퀴즈가 있으면 아이 모드에서 이어서 낸다`() {
        val plan = scheduler(sudeoksa).next(Fixtures.northRoute, 0.0, speed, ListeningHistory(), NOW)!!
        assertTrue(plan.quizAfter(ListenerMode.WITH_CHILD))
        assertTrue(!plan.quizAfter(ListenerMode.ADULTS))
    }

    @Test
    fun `들려줄 곳이 없으면 조용히 달린다`() {
        val adultOnly = place("x", pointBeside(3_000.0, 200.0), listOf(episode("x-1", minAudience = app.yetgil.engine.model.Audience.ADULT)))
        assertNull(scheduler(adultOnly).next(Fixtures.northRoute, 0.0, speed, ListeningHistory(), NOW))
    }
}
