package app.yetgil.engine.session

import app.yetgil.engine.Fixtures
import app.yetgil.engine.Fixtures.NOW
import app.yetgil.engine.Fixtures.episode
import app.yetgil.engine.Fixtures.place
import app.yetgil.engine.Fixtures.pointBeside
import app.yetgil.engine.calendar.SpecialDay
import app.yetgil.engine.calendar.SpecialDayKind
import app.yetgil.engine.geo.Route
import app.yetgil.engine.model.Place
import app.yetgil.engine.plan.Frequency
import app.yetgil.engine.plan.StorySettings
import app.yetgil.engine.plan.VehicleSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriveSessionTest {
    // 25 m/s 로 북쪽으로 달린다. 수덕사는 4km 앞 왼쪽 → 160초 뒤 통과
    private val sudeoksa = place("수덕사", pointBeside(4_000.0, -700.0), listOf(episode("sudeoksa-1", parkedSec = 240)))
    private val chuseok = listOf(SpecialDay("2026-09-25", "추석", SpecialDayKind.HOLIDAY, isHoliday = true))

    /** 가상 차량: 1초마다 위치를 넣고, 말하기는 estimatedSec 뒤에 끝난 것으로 친다. */
    private class Car(val session: DriveSession, val route: Route = Fixtures.northRoute, val speed: Double = 25.0) {
        var t = 0
        var answer: String? = null
        val log = mutableListOf<Pair<Int, Action>>()
        private var speaking: Pair<String, Int>? = null

        fun now() = NOW.plusSeconds(t.toLong())

        fun run(seconds: Int) = repeat(seconds) { step() }

        fun step() {
            t++
            speaking?.let { (id, end) ->
                if (t >= end) {
                    speaking = null
                    handle(session.onSpeechDone(id, now()))
                }
            }
            val along = speed * t
            handle(session.onLocation(route.pointAt(along), route.bearingAt(along), speed, now()))
            handle(session.onTick(now()))
        }

        fun say(text: String) = handle(session.onUtterance(text, now()))

        fun handle(actions: List<Action>) {
            for (a in actions) {
                log += t to a
                when (a) {
                    is Action.Speak -> speaking = a.id to t + a.estimatedSec
                    Action.StopSpeaking -> speaking = null
                    Action.ListenForAnswer -> answer?.let { say(it) }
                    else -> {}
                }
            }
        }

        fun spoken() = log.mapNotNull { (t, a) -> (a as? Action.Speak)?.let { t to it } }
    }

    private fun session(vararg places: Place, settings: StorySettings = StorySettings(), days: List<SpecialDay> = emptyList()) =
        DriveSession(places.toList(), settings, specialDays = days)

    @Test
    fun `지나가기 전에 이야기가 끝나고 아이 모드 퀴즈가 이어진다`() {
        val car = Car(session(sudeoksa)).apply { answer = "정답이요!" }
        car.run(10)
        assertEquals("수덕사", car.session.upcoming?.place?.name)

        car.run(190)
        val (start, story) = car.spoken().first()
        assertTrue(story.text.startsWith("잠시 후 왼쪽으로 수덕사를 지나요."), story.text)
        assertTrue(start + story.estimatedSec < 160, "통과(160초) 전에 끝나야 한다: $start + ${story.estimatedSec}")
        assertTrue(car.spoken()[1].second.text.startsWith("퀴즈!"))
        assertTrue(car.spoken()[2].second.text.startsWith("정답이에요!"))
        assertEquals(true, car.session.quizResults.single().correct)
        assertEquals(listOf("sudeoksa-1"), car.session.history.all.map { it.episodeId })
        assertIs<DriveSession.State.Idle>(car.session.state)
    }

    @Test
    fun `추석에는 그날 첫 이야기에만 인사를 붙인다`() {
        val later = place("독산성", pointBeside(16_000.0, 500.0))
        val car = Car(session(sudeoksa, later, settings = StorySettings(frequency = Frequency.OFTEN), days = chuseok))
        car.run(640)
        val stories = car.spoken().map { it.second.text }.filter { "잠시 후" in it }
        assertEquals(2, stories.size)
        assertTrue(stories[0].startsWith("즐거운 한가위 보내세요. 잠시 후"), stories[0])
        assertTrue(stories[1].startsWith("잠시 후 오른쪽으로 독산성을"), stories[1])
    }

    @Test
    fun `그만이라고 하면 멈추고 그 편은 다시 나오지 않는다`() {
        val car = Car(session(sudeoksa))
        car.run(70)
        assertIs<DriveSession.State.Narrating>(car.session.state)
        car.say("그만해")
        assertEquals(listOf(Action.StopSpeaking, Action.ClearCard), car.log.takeLast(2).map { it.second })
        car.run(100)
        assertEquals(1, car.spoken().size)
        assertEquals(listOf("sudeoksa-1"), car.session.history.all.map { it.episodeId })
    }

    @Test
    fun `답이 없으면 잠시 뒤 정답을 알려 준다`() {
        val car = Car(session(sudeoksa))
        car.run(200)
        val last = car.spoken().last().second.text
        assertTrue(last.startsWith("시간이 다 됐어요. 정답은 정답이에요."), last)
        assertNull(car.session.quizResults.single().correct)
    }

    @Test
    fun `들렀다 가자고 하면 듣던 장소를 경유지로`() {
        val car = Car(session(sudeoksa))
        car.run(70)
        car.say("여기 들렀다 가자")
        val wp = car.log.last().second
        assertIs<Action.AddWaypoint>(wp)
        assertEquals("수덕사", wp.name)
    }

    @Test
    fun `다시 들려줘는 방금 들은 이야기를 다시 읽는다`() {
        val car = Car(session(sudeoksa, settings = StorySettings(listenerMode = app.yetgil.engine.plan.ListenerMode.ADULTS)))
        car.run(170)
        car.say("다시 들려줘")
        val (_, first) = car.spoken().first()
        val (_, replay) = car.spoken().last()
        assertTrue(first.text.endsWith(replay.text))
        assertIs<DriveSession.State.Replaying>(car.session.state)
    }

    @Test
    fun `충전을 시작하면 근처 긴 이야기, 출발하면 멈춘다`() {
        val s = session(sudeoksa)
        s.onLocation(pointBeside(4_000.0, 0.0), 0.0, 0.0, NOW)
        val parked = s.onVehicleSignals(VehicleSignals(parkingBrakeOn = null, charging = true), NOW)
        val card = parked.filterIsInstance<Action.ShowParkedStory>().single()
        assertEquals("수덕사", card.placeName)
        assertNotNull(parked.filterIsInstance<Action.Speak>().singleOrNull())

        val leaving = s.onVehicleSignals(VehicleSignals(parkingBrakeOn = false, charging = false), NOW.plusSeconds(30))
        assertEquals(listOf(Action.StopSpeaking, Action.ClearCard), leaving)
        assertTrue(s.history.all.isEmpty(), "끝까지 듣지 않았으면 기록하지 않는다")
    }
}
