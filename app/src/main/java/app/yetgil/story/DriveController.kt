package app.yetgil.story

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.yetgil.engine.calendar.SpecialDay
import app.yetgil.engine.calendar.SpecialDayCalendar
import app.yetgil.engine.content.Collection
import app.yetgil.engine.content.SeriesProgress
import app.yetgil.engine.geo.Route
import app.yetgil.engine.geo.RouteFile
import app.yetgil.engine.geo.Side
import app.yetgil.engine.model.GeoPoint
import app.yetgil.engine.model.Place
import app.yetgil.engine.model.StoryPack
import app.yetgil.engine.model.StoryPackLoader
import app.yetgil.engine.plan.DriveMode
import app.yetgil.engine.plan.EpisodePick
import app.yetgil.engine.plan.Frequency
import app.yetgil.engine.plan.ListenRecord
import app.yetgil.engine.plan.ListenerMode
import app.yetgil.engine.plan.ListeningHistory
import app.yetgil.engine.plan.RoutePreview
import app.yetgil.engine.plan.StorySettings
import app.yetgil.engine.plan.VehicleSignals
import app.yetgil.engine.session.Action
import app.yetgil.engine.session.DriveSession
import app.yetgil.engine.session.QuizResult
import app.yetgil.story.platform.CarLocation
import app.yetgil.story.platform.CarNavi
import app.yetgil.story.platform.CarSignals
import app.yetgil.story.platform.Ears
import app.yetgil.story.platform.Narrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

/** ① 주행 중 카드 */
data class DrivingCard(
    val placeName: String,
    val caption: String,
    val episodeTitle: String,
    val category: String,
    val side: Side?,
    val startedAt: Instant,
    val estimatedSec: Int,
    val secondsToPass: Int?,
)

data class ParkedView(
    val placeName: String,
    val episodeTitle: String,
    val text: String,
    val sources: String,
    val distance: String,
)

data class QuizView(val placeName: String, val question: String, val listening: Boolean, val secondsLeft: Int?, val feedback: String?)

data class UpcomingView(val placeName: String, val episodeTitle: String, val side: Side, val startInSec: Int)

data class PreviewRow(val placeName: String, val distanceKm: Int, val side: Side, val what: String, val review: Boolean)

data class UiState(
    val mode: DriveMode = DriveMode.DRIVING,
    val speedKmh: Int = 0,
    val card: DrivingCard? = null,
    val quiz: QuizView? = null,
    val parked: ParkedView? = null,
    val parkedIdle: Boolean = false,
    val upcoming: UpcomingView? = null,
    val nextAfter: String? = null,
    val preview: List<PreviewRow> = emptyList(),
    val previewTitle: String = "",
    val settings: StorySettings = StorySettings(listenerMode = ListenerMode.WITH_TEEN),
    val visited: Set<String> = emptySet(),
    val placeNames: List<Pair<String, String>> = emptyList(),
    val series: List<SeriesProgress> = emptyList(),
    val quizResults: List<QuizResult> = emptyList(),
    val heardEpisodes: Int = 0,
    val toast: String? = null,
    val platform: List<Pair<String, String>> = emptyList(),
    val demo: DemoState = DemoState(),
    val packSummary: String = "",
)

data class DemoState(
    val running: Boolean = false,
    val routeName: String = "",
    val progressKm: Int = 0,
    val totalKm: Int = 0,
    val parked: Boolean = false,
)

/**
 * 차량 앱의 중심. Pleos SDK 콜백을 [DriveSession] 이벤트로 넘기고, 돌려받은 [Action] 을 SDK 로 실행한다.
 * 실차·에뮬레이터에서 위치가 오지 않을 때를 위해 저장소의 경로 파일로 달리는 시연 주행([startDemo])을 둔다.
 */
class DriveController(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("yetgil", Context.MODE_PRIVATE)

    private val pack: StoryPack = loadPacks(app)
    private val specialDays: List<SpecialDay> = loadSpecialDays(app)
    private val demoRouteFile: RouteFile = RouteFile.decode(app.assets.open(DEMO_ROUTE).reader(Charsets.UTF_8).readText())
    private val demoRoute: Route = demoRouteFile.toRoute()

    private val history = ListeningHistory(loadHistory())
    private val session = DriveSession(pack.places, loadSettings(), history, specialDays)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val narrator = Narrator(app) { id -> handle(session.onSpeechDone(id, Instant.now())) }
    private val ears = Ears(app) { text -> utter(text) }
    // 시연 주행 중에는 시연 버튼이 차량 신호를 대신한다 (에뮬레이터 기본 기어가 P 라서)
    private val car = CarSignals(app) { signals -> if (!demoRunning && !demoParkedOverride) vehicle(signals) }
    private val navi = CarNavi(app) { dest -> destination = dest; refresh() }
    private val location = CarLocation(app) { p, heading, speed -> if (!demoRunning) fix(p, heading, speed) }

    private var card: DrivingCard? = null
    private var parked: ParkedView? = null
    private var feedback: String? = null
    private var destination: CarNavi.Destination? = null
    private var lastFix: Triple<GeoPoint, Double, Double>? = null
    private var toast: String? = null
    private var toastUntil: Instant = Instant.EPOCH

    // 시연 주행
    private var demoRunning = false
    private var demoAlong = 0.0
    private var demoSpeedMps = DEMO_SPEED_KMH / 3.6
    private var demoParkedOverride = false
    private var lastSkipAlong = -1.0

    init {
        viewModelScope.launch {
            while (true) {
                tick()
                delay(1_000)
            }
        }
        refresh()
    }

    // ---- 사용자 조작 (화면 버튼 = 같은 뜻의 음성 명령) ----

    fun stop() = utter("그만")

    fun replay() = utter("다시 들려줘")

    fun stopover() = utter("들렀다 가자")

    fun skipQuiz() = utter("퀴즈는 됐어")

    /** 운전대 음성 버튼에 해당. 인식이 없으면 아무 일도 하지 않는다. */
    fun listen() = ears.listen()

    /** 시연용: 아이가 말로 답한 것으로 친다 */
    fun answerQuiz(correct: Boolean) {
        val quiz = (session.state as? DriveSession.State.AwaitingAnswer)?.episode?.quiz ?: return
        utter(if (correct) quiz.answers.first() else "음… 모르겠어요")
    }

    /** 이번 길 탭: 시연 주행이 아니면 내비 목적지로 미리보기를 만들기 위해 NaviHelper 에 연결한다. */
    fun onRouteTabOpened() {
        if (!demoRunning && demoAlong == 0.0) navi.connect()
    }

    fun setListenerMode(mode: ListenerMode) = updateSettings(session.settings.copy(listenerMode = mode))

    fun setFrequency(f: Frequency) = updateSettings(session.settings.copy(frequency = f))

    fun setReviewQuiz(on: Boolean) = updateSettings(session.settings.copy(reviewQuizEnabled = on))

    fun startDemo() {
        demoRunning = true
        demoParkedOverride = false
        session.onRoute(demoRoute)
        if (session.mode == DriveMode.PARKED) vehicle(VehicleSignals(parkingBrakeOn = false, charging = false, gearInPark = false))
        refresh()
    }

    fun pauseDemo() {
        demoRunning = false
        vehicle(car.signals)
        refresh()
    }

    /** 다음 이야기 장소를 1분 30초쯤 앞둔 곳으로 건너뛴다. */
    fun skipToNextPlace() {
        // 건너뛴 지점이 장소보다 앞이므로, 마지막으로 건너뛴 장소 다음부터 찾는다
        val from = maxOf(demoAlong + 500, lastSkipAlong + 1)
        val next = pack.places
            .map { it to demoRoute.project(it.location) }
            .filter { (p, proj) -> proj.lateralM <= p.triggerRadiusM && proj.alongM > from }
            .filter { (p, _) -> p.episodes.any { e -> session.settings.listenerMode.allows(e) } }
            .minByOrNull { it.second.alongM }
        if (next == null) {
            showToast("이 경로에서 더 지나갈 곳이 없어요")
            return
        }
        lastSkipAlong = next.second.alongM
        session.clearNarrationGap()
        demoAlong = (next.second.alongM - demoSpeedMps * DEMO_LEAD_SEC).coerceAtLeast(0.0)
        if (!demoRunning) startDemo()
        showToast("${next.first.name} 앞으로 이동")
    }

    fun toggleDemoParked() {
        demoParkedOverride = !demoParkedOverride
        val signals = if (demoParkedOverride) {
            VehicleSignals(parkingBrakeOn = null, charging = true, gearInPark = true)
        } else {
            car.signals.copy(gearInPark = false, charging = false)
        }
        vehicle(signals)
    }

    fun resetHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        showToast("들은 기록을 지웠어요. 앱을 다시 열면 처음부터 들려줘요")
    }

    // ---- 이벤트 → DriveSession ----

    private fun tick() {
        val now = Instant.now()
        if (demoRunning && session.mode == DriveMode.DRIVING) {
            demoAlong = (demoAlong + demoSpeedMps).coerceAtMost(demoRoute.lengthM)
            if (demoAlong >= demoRoute.lengthM) demoRunning = false
            fix(demoRoute.pointAt(demoAlong), demoRoute.bearingAt(demoAlong), if (demoRunning) demoSpeedMps else 0.0)
        }
        handle(session.onTick(now))
        refresh()
    }

    private fun fix(p: GeoPoint, heading: Double, speedMps: Double) {
        lastFix = Triple(p, heading, speedMps)
        handle(session.onLocation(p, heading, speedMps, Instant.now()))
    }

    private fun vehicle(signals: VehicleSignals) {
        handle(session.onVehicleSignals(signals, Instant.now()))
        refresh()
    }

    private fun utter(text: String) {
        handle(session.onUtterance(text, Instant.now()))
        refresh()
    }

    private fun handle(actions: List<Action>) {
        for (a in actions) {
            when (a) {
                is Action.Speak -> {
                    narrator.speak(a.id, a.text, a.estimatedSec)
                    card = card?.copy(startedAt = Instant.now(), estimatedSec = a.estimatedSec)
                    if (session.state is DriveSession.State.GivingFeedback) feedback = a.text
                }
                Action.StopSpeaking -> narrator.stop()
                Action.ListenForAnswer -> {
                    feedback = null
                    ears.listen()
                    if (demoRunning) {
                        val waiting = session.state
                        viewModelScope.launch {
                            delay(DEMO_AUTO_ANSWER_MS)
                            if (session.state == waiting) answerQuiz(true)
                        }
                    }
                }
                is Action.ShowDrivingCard -> {
                    val plan = (session.state as? DriveSession.State.Narrating)?.plan
                    card = DrivingCard(
                        placeName = a.placeName,
                        caption = a.caption,
                        episodeTitle = a.episodeTitle,
                        category = a.category,
                        side = plan?.side,
                        startedAt = Instant.now(),
                        estimatedSec = 0,
                        secondsToPass = null,
                    )
                    parked = null
                }
                Action.ClearCard -> {
                    card = null
                    parked = null
                    feedback = null
                }
                is Action.ShowParkedStory -> parked = ParkedView(
                    placeName = a.placeName,
                    episodeTitle = a.episodeTitle,
                    text = a.text,
                    sources = a.sources.joinToString(" · ") { it.title },
                    distance = app.yetgil.engine.plan.Announcer.formatDistance(a.distanceM),
                )
                is Action.AddWaypoint -> {
                    val ok = navi.addWaypoint(a.name, a.location, a.placeId)
                    showToast(if (ok) "${a.name}을(를) 경유지로 넣었어요" else "${a.name} 경유지 추가 (내비 연결 없음, 시연)")
                }
            }
        }
        saveHistory()
    }

    // ---- 화면 상태 ----

    private fun refresh() {
        val now = Instant.now()
        val s = session.state
        val fix = lastFix
        val cardNow = card?.let { c ->
            val plan = (s as? DriveSession.State.Narrating)?.plan
            val toPass = if (plan != null && fix != null && fix.third > 1.0) {
                val ahead = demoRouteOrStraight(fix)
                ((ahead.project(plan.place.location).alongM - ahead.project(fix.first).alongM) / fix.third).toInt().coerceAtLeast(0)
            } else {
                null
            }
            c.copy(secondsToPass = toPass)
        }
        val quiz = when (s) {
            is DriveSession.State.AskingQuiz -> QuizView(s.place.name, s.episode.quiz?.question.orEmpty(), false, null, null)
            is DriveSession.State.AwaitingAnswer -> QuizView(
                s.place.name,
                s.episode.quiz?.question.orEmpty(),
                listening = true,
                secondsLeft = (DriveSession.ANSWER_TIMEOUT.seconds - java.time.Duration.between(s.since, now).seconds).toInt().coerceAtLeast(0),
                feedback = null,
            )
            is DriveSession.State.GivingFeedback -> QuizView("", "", false, null, feedback)
            else -> null
        }
        val up = session.upcoming
        val settings = session.settings
        val previewRoute = if (demoRunning || demoAlong > 0) demoRoute else destinationRoute()
        val preview = previewRoute?.let { r ->
            RoutePreview.build(r, pack.places, settings, history, now).map { item ->
                PreviewRow(
                    placeName = item.place.name,
                    distanceKm = (item.distanceFromStartM / 1000).toInt(),
                    side = item.side,
                    what = when (val pick = item.pick) {
                        is EpisodePick.NewEpisode -> "${pick.episode.number}화 · ${pick.episode.category.spokenLabel}"
                        is EpisodePick.ReviewQuiz -> "다 들었어요 · 복습 퀴즈"
                    },
                    review = item.pick is EpisodePick.ReviewQuiz,
                )
            }
        }.orEmpty()
        if (now.isAfter(toastUntil)) toast = null

        _state.value = UiState(
            mode = session.mode,
            speedKmh = ((fix?.third ?: 0.0) * 3.6).toInt(),
            card = cardNow,
            quiz = quiz,
            parked = parked,
            parkedIdle = s == DriveSession.State.ParkedIdle,
            upcoming = up?.let { UpcomingView(it.place.name, it.pick.episode.title, it.side, it.timing.startInSec.toInt()) },
            nextAfter = (s as? DriveSession.State.Narrating)?.plan?.let { plan ->
                if (plan.quizAfter(settings.listenerMode)) "퀴즈 1문제가 이어져요" else null
            },
            preview = preview,
            previewTitle = when {
                demoRunning || demoAlong > 0 -> demoRouteFile.name
                destination != null -> "${destination!!.name}까지 (목적지 방향 기준)"
                else -> ""
            },
            settings = settings,
            visited = Collection.visitedPlaceIds(history),
            placeNames = pack.places.map { it.id to it.name },
            series = Collection.seriesProgress(pack, history),
            quizResults = session.quizResults,
            heardEpisodes = history.all.map { it.episodeId }.toSet().size,
            toast = toast,
            platform = listOf(
                "음성 안내" to narrator.engineName,
                "음성 인식" to ears.engineName,
                "차량 신호" to if (car.connected) "Vehicle SDK (기어·충전구)" else "연결 없음",
                "위치" to if (location.connected) "FusedLocation SDK" else "연결 없음",
                "내비" to if (navi.connected) "NaviHelper SDK" else "필요할 때 연결 (이번 길 · 들렀다 가기)",
            ),
            demo = DemoState(
                running = demoRunning,
                routeName = demoRouteFile.name,
                progressKm = (demoAlong / 1000).toInt(),
                totalKm = (demoRoute.lengthM / 1000).toInt(),
                parked = demoParkedOverride,
            ),
            packSummary = "${pack.places.size}곳 ${pack.places.sumOf { it.episodes.size }}편 · 검수 ${pack.places.sumOf { p -> p.episodes.count { it.reviewed } }}편",
        )
    }

    private fun demoRouteOrStraight(fix: Triple<GeoPoint, Double, Double>): Route =
        if (demoRunning) demoRoute else Route.straightAhead(fix.first, fix.second, DriveSession.STRAIGHT_AHEAD_M)

    /** NaviHelper 는 경로 좌표를 주지 않으므로 미리보기는 현재 위치→목적지 직선 주변으로 어림한다. */
    private fun destinationRoute(): Route? {
        val d = destination ?: return null
        val from = lastFix?.first ?: return null
        return Route(listOf(from, d.location))
    }

    private fun showToast(text: String) {
        toast = text
        toastUntil = Instant.now().plusSeconds(4)
        refresh()
    }

    private fun updateSettings(s: StorySettings) {
        session.settings = s
        prefs.edit()
            .putString(KEY_MODE, s.listenerMode.name)
            .putString(KEY_FREQ, s.frequency.name)
            .putBoolean(KEY_REVIEW, s.reviewQuizEnabled)
            .apply()
        refresh()
    }

    private fun loadSettings() = StorySettings(
        listenerMode = prefs.getString(KEY_MODE, null)?.let(ListenerMode::valueOf) ?: ListenerMode.WITH_TEEN,
        frequency = prefs.getString(KEY_FREQ, null)?.let(Frequency::valueOf) ?: Frequency.OFTEN,
        reviewQuizEnabled = prefs.getBoolean(KEY_REVIEW, true),
    )

    // 들은 기록: 한 줄에 "episodeId|placeId|epochSec" (Sync SDK 가 없어 기기 안에만 둔다)
    private fun loadHistory(): List<ListenRecord> =
        prefs.getString(KEY_HISTORY, "").orEmpty().lines().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 3) null else ListenRecord(parts[0], parts[1], Instant.ofEpochSecond(parts[2].toLong()))
        }

    private fun saveHistory() {
        prefs.edit().putString(
            KEY_HISTORY,
            history.all.joinToString("\n") { "${it.episodeId}|${it.placeId}|${it.heardAt.epochSecond}" },
        ).apply()
    }

    override fun onCleared() {
        narrator.release()
        ears.release()
        car.release()
        navi.release()
        location.release()
    }

    companion object {
        const val DEMO_ROUTE = "routes/gyeongbu-demo.json"
        const val DEMO_SPEED_KMH = 100.0

        /** '다음 장소 앞으로'는 장소를 이만큼 앞둔 곳으로 건너뛴다 (짧은 편이 지나기 전에 끝나는 거리) */
        const val DEMO_LEAD_SEC = 45.0

        /** 시연 주행 중 퀴즈는 이 시간 뒤 아이가 정답을 말한 것으로 친다 (마이크로 먼저 답하면 그쪽이 쓰인다) */
        const val DEMO_AUTO_ANSWER_MS = 4_000L
        private const val KEY_HISTORY = "history"
        private const val KEY_MODE = "listenerMode"
        private const val KEY_FREQ = "frequency"
        private const val KEY_REVIEW = "reviewQuiz"

        fun loadPacks(context: Context): StoryPack {
            val packs = context.assets.list("packs").orEmpty().filter { it.endsWith(".json") }.sorted().map { name ->
                StoryPackLoader.decode(context.assets.open("packs/$name").reader(Charsets.UTF_8).readText())
            }
            return StoryPack(1, "all", "all", "", packs.flatMap { it.places }, packs.flatMap { it.series })
        }

        fun loadSpecialDays(context: Context): List<SpecialDay> =
            context.assets.list("special-days").orEmpty().filter { it.endsWith(".json") }.flatMap { name ->
                SpecialDayCalendar.decode(context.assets.open("special-days/$name").reader(Charsets.UTF_8).readText()).days
            }
    }
}
