package app.yetgil.engine.session

import app.yetgil.engine.calendar.Greeter
import app.yetgil.engine.calendar.SpecialDay
import app.yetgil.engine.content.ScriptTiming
import app.yetgil.engine.geo.Route
import app.yetgil.engine.interaction.QuizJudge
import app.yetgil.engine.interaction.VoiceCommand
import app.yetgil.engine.interaction.VoiceCommandParser
import app.yetgil.engine.model.Episode
import app.yetgil.engine.model.GeoPoint
import app.yetgil.engine.model.Place
import app.yetgil.engine.model.Quiz
import app.yetgil.engine.model.Source
import app.yetgil.engine.plan.Announcer
import app.yetgil.engine.plan.DriveMode
import app.yetgil.engine.plan.DriveModes
import app.yetgil.engine.plan.EpisodePick
import app.yetgil.engine.plan.LengthPlanner
import app.yetgil.engine.plan.ListenRecord
import app.yetgil.engine.plan.ListeningHistory
import app.yetgil.engine.plan.NarrationPlan
import app.yetgil.engine.plan.ParkedStoryFinder
import app.yetgil.engine.plan.PassPredictor
import app.yetgil.engine.plan.StoryScheduler
import app.yetgil.engine.plan.StorySettings
import app.yetgil.engine.plan.VehicleSignals
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 앱 모듈이 Pleos SDK 로 실행할 일 */
sealed interface Action {
    /** Gleo AI TextToSpeech speak. 다 읽으면 [DriveSession.onSpeechDone] 에 id 를 넘긴다. */
    data class Speak(val id: String, val text: String, val estimatedSec: Int) : Action

    data object StopSpeaking : Action

    /** Gleo AI SpeechToText 로 퀴즈 답을 듣는다. 결과는 [DriveSession.onUtterance] 로. */
    data object ListenForAnswer : Action

    /** ① 주행 중 화면 · ② 내비 위 미니 카드: 장소 이름·방향·큰 버튼만 */
    data class ShowDrivingCard(
        val placeName: String,
        val caption: String,
        val episodeTitle: String,
        val category: String,
    ) : Action

    data object ClearCard : Action

    /** ⑤ 정차·충전 중: 긴 글과 사진, 출처 */
    data class ShowParkedStory(
        val placeName: String,
        val episodeTitle: String,
        val text: String,
        val imageUrl: String?,
        val imageCredit: String?,
        val sources: List<Source>,
        val distanceM: Double,
    ) : Action

    /** NaviHelper addWaypoint ('들렀다 가자') */
    data class AddWaypoint(val placeId: String, val name: String, val location: GeoPoint) : Action
}

data class QuizResult(val episodeId: String, val placeId: String, val correct: Boolean?, val at: Instant)

/**
 * 차량 앱의 상태 기계. Pleos SDK 콜백을 이벤트로 받아 할 일([Action])을 돌려준다.
 * 엔진은 SDK 를 모르고, SDK 호출은 앱 모듈이 Action 을 실행할 때만 한다.
 *
 * | Pleos SDK                                   | 이벤트                 |
 * |---------------------------------------------|------------------------|
 * | NaviHelper getRouteStateInfo · addListener  | [onRoute]              |
 * | Fused Location (없으면 Android 위치 API)       | [onLocation]           |
 * | Vehicle Brake · EvBattery                   | [onVehicleSignals]     |
 * | TextToSpeech 재생 완료                         | [onSpeechDone]         |
 * | SpeechToText 결과                             | [onUtterance]          |
 * | 1초 타이머                                    | [onTick]               |
 */
class DriveSession(
    private val places: List<Place>,
    settings: StorySettings = StorySettings(),
    val history: ListeningHistory = ListeningHistory(),
    private val specialDays: List<SpecialDay> = emptyList(),
    private val zone: ZoneId = Greeter.KOREA,
) {
    sealed interface State {
        data object Idle : State

        data class Narrating(val plan: NarrationPlan, val utteranceId: String) : State

        data class AskingQuiz(val place: Place, val episode: Episode, val utteranceId: String) : State

        data class AwaitingAnswer(val place: Place, val episode: Episode, val since: Instant) : State

        data class GivingFeedback(val utteranceId: String) : State

        data class Replaying(val utteranceId: String) : State

        data class TellingParkedStory(val place: Place, val episode: Episode, val utteranceId: String) : State

        /** 정차 중인데 들려줄 긴 이야기가 없거나 다 들었다 */
        data object ParkedIdle : State
    }

    private val scheduler = StoryScheduler(PassPredictor(places), settings)

    var settings: StorySettings
        get() = scheduler.settings
        set(value) {
            scheduler.settings = value
        }

    var state: State = State.Idle
        private set

    var mode: DriveMode = DriveMode.DRIVING
        private set

    /** 다음에 들려줄 예정인 이야기 (② 미니 카드의 '다음 이야기') */
    var upcoming: NarrationPlan? = null
        private set

    val quizResults: List<QuizResult> get() = results

    private val results = mutableListOf<QuizResult>()
    private var navRoute: Route? = null
    private var position: GeoPoint? = null
    private var lastEndedAt: Instant? = null
    private var lastStory: Pair<Place, String>? = null
    private var utteranceSeq = 0

    /** 시연 주행의 '다음 장소 앞으로': 사용자가 바로 다음 장소를 듣겠다고 했으므로 이야기 사이 최소 간격을 따지지 않는다 */
    fun clearNarrationGap() {
        lastEndedAt = null
    }

    /** 목적지를 넣거나 바꾸면 경로, 목적지를 지우면 null */
    fun onRoute(route: Route?) {
        navRoute = route
        upcoming = null
    }

    fun onLocation(position: GeoPoint, bearingDeg: Double, speedMps: Double, now: Instant): List<Action> {
        this.position = position
        if (mode == DriveMode.PARKED || state != State.Idle) return emptyList()

        val route = navRoute ?: Route.straightAhead(position, bearingDeg, STRAIGHT_AHEAD_M)
        val along = navRoute?.project(position)?.alongM ?: 0.0
        val greeting = Greeter.firstStoryGreeting(now, history, specialDays, zone)
        val leadIn = greeting?.let { ScriptTiming.estimateSec(it).toDouble() } ?: 0.0

        val plan = scheduler.next(route, along, speedMps, history, now, lastEndedAt, leadIn)
        upcoming = plan
        if (plan == null || plan.timing.startInSec > START_WINDOW_SEC) return emptyList()
        return startNarration(plan, greeting)
    }

    fun onVehicleSignals(signals: VehicleSignals, now: Instant): List<Action> {
        val newMode = DriveModes.from(signals)
        if (newMode == mode) return emptyList()
        mode = newMode
        return when (newMode) {
            DriveMode.PARKED -> if (state == State.Idle) startParkedStory(now) else emptyList()
            DriveMode.DRIVING -> when (state) {
                // 출발하면 긴 이야기는 멈춘다 (끝까지 듣지 않았으니 기록하지 않는다)
                is State.TellingParkedStory -> {
                    state = State.Idle
                    listOf(Action.StopSpeaking, Action.ClearCard)
                }
                State.ParkedIdle -> {
                    state = State.Idle
                    listOf(Action.ClearCard)
                }
                else -> emptyList()
            }
        }
    }

    fun onSpeechDone(utteranceId: String, now: Instant): List<Action> =
        when (val s = state) {
            is State.Narrating -> if (s.utteranceId != utteranceId) emptyList() else afterNarration(s.plan, now)
            is State.AskingQuiz -> if (s.utteranceId != utteranceId) {
                emptyList()
            } else {
                state = State.AwaitingAnswer(s.place, s.episode, now)
                listOf(Action.ListenForAnswer)
            }
            is State.GivingFeedback -> if (s.utteranceId != utteranceId) emptyList() else finish(now)
            is State.Replaying -> if (s.utteranceId != utteranceId) emptyList() else finish(now)
            is State.TellingParkedStory -> if (s.utteranceId != utteranceId) {
                emptyList()
            } else {
                history.add(ListenRecord(s.episode.id, s.place.id, now))
                state = State.ParkedIdle
                emptyList()
            }
            else -> emptyList()
        }

    fun onUtterance(text: String, now: Instant): List<Action> {
        val command = VoiceCommandParser.parse(text)
        val s = state
        if (s is State.AwaitingAnswer) {
            return when (command) {
                VoiceCommand.SKIP_QUIZ, VoiceCommand.STOP -> {
                    results += QuizResult(s.episode.id, s.place.id, correct = null, at = now)
                    finish(now)
                }
                VoiceCommand.STOPOVER -> listOf(waypoint(s.place))
                else -> answer(s, text, now)
            }
        }
        return when (command) {
            VoiceCommand.STOP -> stop(now)
            VoiceCommand.SKIP_QUIZ -> if (s is State.AskingQuiz) listOf(Action.StopSpeaking) + finish(now) else emptyList()
            VoiceCommand.REPLAY -> replay()
            VoiceCommand.STOPOVER -> currentPlace()?.let { listOf(waypoint(it)) } ?: emptyList()
            null -> emptyList()
        }
    }

    /** 답을 기다리다 시간이 지나면 정답을 알려 준다. */
    fun onTick(now: Instant): List<Action> {
        val s = state
        if (s is State.AwaitingAnswer && Duration.between(s.since, now) >= ANSWER_TIMEOUT) {
            results += QuizResult(s.episode.id, s.place.id, correct = null, at = now)
            val quiz = s.episode.quiz!!
            return feedback("시간이 다 됐어요. ${reveal(quiz)}")
        }
        return emptyList()
    }

    private fun startNarration(plan: NarrationPlan, greeting: String?): List<Action> {
        val episode = plan.pick.episode
        val text = listOfNotNull(greeting, "${plan.announcement}.", plan.timing.script.text).joinToString(" ")
        val estimated = (greeting?.let { ScriptTiming.estimateSec(it) } ?: 0) +
            LengthPlanner.ANNOUNCE_SEC.toInt() + plan.timing.script.durationSec
        val id = nextId()
        state = State.Narrating(plan, id)
        upcoming = null
        return listOf(
            Action.ShowDrivingCard(
                placeName = plan.place.name,
                caption = Announcer.onScreen(plan.side, plan.lateralM),
                episodeTitle = episode.title,
                category = episode.category.spokenLabel,
            ),
            Action.Speak(id, text, estimated),
        )
    }

    private fun afterNarration(plan: NarrationPlan, now: Instant): List<Action> {
        val episode = plan.pick.episode
        history.add(ListenRecord(episode.id, plan.place.id, now))
        lastStory = plan.place to plan.timing.script.text
        return when {
            // 복습 퀴즈는 원고가 곧 질문이다
            plan.pick is EpisodePick.ReviewQuiz -> {
                state = State.AwaitingAnswer(plan.place, episode, now)
                listOf(Action.ListenForAnswer)
            }
            plan.quizAfter(settings.listenerMode) -> {
                val id = nextId()
                state = State.AskingQuiz(plan.place, episode, id)
                val question = "퀴즈! ${episode.quiz!!.question}"
                listOf(Action.Speak(id, question, ScriptTiming.estimateSec(question)))
            }
            else -> finish(now)
        }
    }

    private fun answer(s: State.AwaitingAnswer, text: String, now: Instant): List<Action> {
        val quiz = s.episode.quiz!!
        val correct = QuizJudge.isCorrect(quiz, text)
        results += QuizResult(s.episode.id, s.place.id, correct, now)
        return feedback(if (correct) "정답이에요! ${quiz.explanation}" else "아쉬워요. ${reveal(quiz)}")
    }

    private fun reveal(quiz: Quiz) = "정답은 ${Announcer.withCopula(quiz.answers.first())}. ${quiz.explanation}"

    private fun feedback(text: String): List<Action> {
        val id = nextId()
        state = State.GivingFeedback(id)
        return listOf(Action.Speak(id, text, ScriptTiming.estimateSec(text)))
    }

    /** "그만": 하던 말을 멈춘다. 멈춘 편도 들은 것으로 쳐서 다시 나오지 않게 한다. */
    private fun stop(now: Instant): List<Action> {
        when (val s = state) {
            is State.Narrating -> history.add(ListenRecord(s.plan.pick.episode.id, s.plan.place.id, now))
            is State.TellingParkedStory -> history.add(ListenRecord(s.episode.id, s.place.id, now))
            State.Idle, State.ParkedIdle -> return emptyList()
            else -> {}
        }
        return listOf(Action.StopSpeaking) + finish(now, continueParked = false)
    }

    private fun replay(): List<Action> {
        if (state != State.Idle) return emptyList()
        val (_, text) = lastStory ?: return emptyList()
        val id = nextId()
        state = State.Replaying(id)
        return listOf(Action.Speak(id, text, ScriptTiming.estimateSec(text)))
    }

    private fun currentPlace(): Place? = when (val s = state) {
        is State.Narrating -> s.plan.place
        is State.AskingQuiz -> s.place
        is State.TellingParkedStory -> s.place
        else -> lastStory?.first
    }

    private fun waypoint(place: Place) = Action.AddWaypoint(place.id, place.name, place.location)

    /** 한 이야기가 끝났다. 정차 중이면 ("그만"으로 멈춘 게 아니면) 긴 이야기로 이어 간다. */
    private fun finish(now: Instant, continueParked: Boolean = true): List<Action> {
        lastEndedAt = now
        state = State.Idle
        val actions = listOf(Action.ClearCard)
        if (mode != DriveMode.PARKED) return actions
        if (!continueParked) {
            state = State.ParkedIdle
            return actions
        }
        return actions + startParkedStory(now)
    }

    private fun startParkedStory(now: Instant): List<Action> {
        val story = position?.let {
            ParkedStoryFinder.find(it, places, settings.listenerMode, history, now)
        }
        if (story == null) {
            state = State.ParkedIdle
            return emptyList()
        }
        val script = story.episode.scripts.parked!!
        val id = nextId()
        state = State.TellingParkedStory(story.place, story.episode, id)
        return listOf(
            Action.ShowParkedStory(
                placeName = story.place.name,
                episodeTitle = story.episode.title,
                text = script.text,
                imageUrl = story.place.imageUrl,
                imageCredit = story.place.imageCredit,
                sources = story.episode.sources,
                distanceM = story.distanceM,
            ),
            Action.Speak(id, script.text, script.durationSec),
        )
    }

    private fun nextId() = "u${++utteranceSeq}"

    companion object {
        /** 목적지가 없을 때 진행 방향으로 내다볼 거리 */
        const val STRAIGHT_AHEAD_M = 30_000.0

        /** 위치는 1초마다 들어온다. 시작 시점이 이만큼 안으로 들어오면 바로 시작한다. */
        const val START_WINDOW_SEC = 2.0

        val ANSWER_TIMEOUT: Duration = Duration.ofSeconds(12)
    }
}
