package app.yetgil.simulator

import app.yetgil.engine.geo.Route
import app.yetgil.engine.model.Place
import app.yetgil.engine.model.Quiz
import app.yetgil.engine.plan.Announcer
import app.yetgil.engine.plan.VehicleSignals
import app.yetgil.engine.session.Action
import app.yetgil.engine.session.DriveSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 실차 대신 엔진에 이벤트를 넣는다. 1초마다 위치를 넣고,
 * 말하기는 예상 시간이 지나면 끝난 것으로, 퀴즈 답은 몇 초 뒤 [answer] 로 말한 것으로 친다.
 */
class VirtualCar(
    private val session: DriveSession,
    private val places: List<Place>,
    private val start: Instant,
    private val answer: (Quiz) -> String?,
) {
    private var t = 0L
    private var speaking: Pair<String, Long>? = null
    private var pendingUtterance: Pair<String, Long>? = null
    private var lastUpcoming: String? = null

    fun now(): Instant = start.plusSeconds(t)

    /** 경로를 따라 끝까지 달린다. 장소 옆을 지나는 순간도 찍는다. */
    fun drive(route: Route, speedMps: Double) {
        session.onRoute(route)
        val passes = passes(route, places).toMutableList()
        var along = 0.0
        while (along < route.lengthM) {
            t++
            along = minOf(route.lengthM, along + speedMps)
            finishSpeechAndAnswers()
            handle(session.onLocation(route.pointAt(along), route.bearingAt(along), speedMps, now()))
            handle(session.onTick(now()))
            showUpcoming()
            while (passes.isNotEmpty() && passes.first().first <= along) {
                log("통과", passes.removeAt(0).second)
            }
        }
        log("도착", "${Announcer.formatDistance(route.lengthM)} 주행")
        // 도착 뒤 하던 말과 퀴즈를 마저 끝낸다
        val end = route.points.last()
        repeat(MAX_WRAP_UP_SEC) {
            if (speaking == null && pendingUtterance == null && session.state == DriveSession.State.Idle) return
            t++
            finishSpeechAndAnswers()
            handle(session.onLocation(end, 0.0, 0.0, now()))
            handle(session.onTick(now()))
        }
    }

    fun vehicle(signals: VehicleSignals, label: String) {
        log("차량", label)
        handle(session.onVehicleSignals(signals, now()))
        if (session.state == DriveSession.State.ParkedIdle) {
            log("화면", "[정차] 10km 안에 정차용 긴 이야기(4분 분량)가 있는 곳이 없음")
        }
    }

    /** 서 있는 채로 seconds 초 보낸다. 말이 끝나면 일찍 멈출 수 있다. */
    fun wait(seconds: Int, untilQuiet: Boolean = false) {
        repeat(seconds) {
            if (untilQuiet && speaking == null && pendingUtterance == null) return
            t++
            finishSpeechAndAnswers()
            handle(session.onTick(now()))
        }
    }

    fun say(text: String) {
        log("사람", "\"$text\"")
        handle(session.onUtterance(text, now()))
    }

    private fun finishSpeechAndAnswers() {
        speaking?.let { (id, end) ->
            if (t >= end) {
                speaking = null
                handle(session.onSpeechDone(id, now()))
            }
        }
        pendingUtterance?.let { (text, at) ->
            if (t >= at) {
                pendingUtterance = null
                say(text)
            }
        }
    }

    private fun showUpcoming() {
        val plan = session.upcoming
        val key = plan?.let { "${it.place.id}/${it.pick.episode.id}" }
        if (plan != null && key != lastUpcoming) {
            val wait = plan.timing.startInSec.toLong()
            log("예정", "${plan.place.name} · ${plan.pick.episode.title} · ${wait / 60}분 ${wait % 60}초 뒤 시작")
        }
        lastUpcoming = key
    }

    private fun handle(actions: List<Action>) {
        for (a in actions) {
            when (a) {
                is Action.Speak -> {
                    speaking = a.id to t + a.estimatedSec
                    log("말하기", "${a.text.ellipsis(110)}  (${a.estimatedSec}초)")
                }
                Action.StopSpeaking -> {
                    speaking = null
                    log("멈춤", "말하기 중단")
                }
                Action.ListenForAnswer -> {
                    log("듣기", "퀴즈 답을 기다림")
                    val quiz = (session.state as? DriveSession.State.AwaitingAnswer)?.episode?.quiz
                    quiz?.let(answer)?.let { pendingUtterance = it to t + ANSWER_DELAY_SEC }
                }
                is Action.ShowDrivingCard -> log("화면", "${a.placeName} · ${a.caption} · ${a.category}")
                Action.ClearCard -> {}
                is Action.ShowParkedStory -> log(
                    "화면",
                    "[정차] ${a.placeName} (${Announcer.formatDistance(a.distanceM)}) · ${a.episodeTitle} · " +
                        "사진 ${if (a.imageUrl != null) "있음 (${a.imageCredit})" else "없음"} · " +
                        "출처 ${a.sources.joinToString { it.publisher }}",
                )
                is Action.AddWaypoint -> log("경유지", "${a.name} 추가 (NaviHelper addWaypoint)")
            }
        }
    }

    private fun log(tag: String, text: String) {
        println("${TIME.format(now().atZone(ZONE))}  ${tag.padEnd(4, '　')}  $text")
    }

    companion object {
        const val ANSWER_DELAY_SEC = 3L
        const val MAX_WRAP_UP_SEC = 300
        private val ZONE = ZoneId.of("Asia/Seoul")
        private val TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

        /** 경로 위 어디쯤에서 어느 장소 옆을 지나는지 (가까운 순) */
        fun passes(route: Route, places: List<Place>): List<Pair<Double, String>> =
            places.mapNotNull { p ->
                val proj = route.project(p.location)
                if (proj.lateralM > p.triggerRadiusM) {
                    null
                } else {
                    proj.alongM to "${p.name} · ${proj.side.spoken} ${Announcer.formatDistance(proj.lateralM)}"
                }
            }.sortedBy { it.first }

        private fun String.ellipsis(max: Int) = if (length <= max) this else take(max) + "…"
    }
}
