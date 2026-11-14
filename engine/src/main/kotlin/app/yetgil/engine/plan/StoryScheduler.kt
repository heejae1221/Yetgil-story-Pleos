package app.yetgil.engine.plan

import app.yetgil.engine.geo.Route
import app.yetgil.engine.geo.Side
import app.yetgil.engine.model.Place
import app.yetgil.engine.model.Script
import java.time.Duration
import java.time.Instant

/** 설정 화면의 '얼마나 자주 들려줄까요?': 이야기 사이 최소 간격 */
enum class Frequency(val minGap: Duration) {
    OFTEN(Duration.ofMinutes(3)),
    NORMAL(Duration.ofMinutes(8)),
    RARE(Duration.ofMinutes(15)),
}

data class StorySettings(
    val listenerMode: ListenerMode = ListenerMode.WITH_CHILD,
    val frequency: Frequency = Frequency.NORMAL,
    val reviewQuizEnabled: Boolean = true,
)

/** 다음에 들려줄 것 하나 */
data class NarrationPlan(
    val place: Place,
    val pick: EpisodePick,
    val timing: TimingPlan,
    val side: Side,
    val lateralM: Double,
    val etaSec: Double,
) {
    val announcement: String get() = Announcer.spoken(place.name, side)

    /** 이야기가 끝나고 퀴즈를 이어 낼지 */
    fun quizAfter(mode: ListenerMode): Boolean =
        pick is EpisodePick.NewEpisode && mode.quizEnabled && pick.episode.quiz != null
}

/**
 * 주행 중 다음 이야기를 정한다. 앱은 위치가 바뀔 때마다, 이야기가 끝날 때마다 [next] 를 부른다.
 * 가까운 장소부터 보며, 들려줄 편이 있고 지나가기 전에 끝낼 수 있고 최소 간격을 지키는 첫 장소를 고른다.
 */
class StoryScheduler(
    private val predictor: PassPredictor,
    var settings: StorySettings = StorySettings(),
) {
    fun next(
        route: Route,
        vehicleAlongM: Double,
        speedMps: Double,
        history: ListeningHistory,
        now: Instant,
        lastNarrationEndedAt: Instant? = null,
        /** 안내 멘트 앞에 붙일 말의 길이 (그날 첫 이야기의 명절 인사 등) */
        leadInSec: Double = 0.0,
    ): NarrationPlan? {
        val gapLeftSec = lastNarrationEndedAt
            ?.let { (settings.frequency.minGap.seconds - Duration.between(it, now).seconds).toDouble() }
            ?.coerceAtLeast(0.0) ?: 0.0

        for (pass in predictor.predict(route, vehicleAlongM, speedMps)) {
            val pick = EpisodeSelector.pick(
                pass.place, settings.listenerMode, history, now, settings.reviewQuizEnabled,
            ) ?: continue
            val timing = when (pick) {
                is EpisodePick.NewEpisode -> LengthPlanner.plan(pass.etaSec, pick.episode.scripts, leadInSec)
                is EpisodePick.ReviewQuiz -> LengthPlanner.plan(
                    pass.etaSec, listOf(StoryLength.SHORT to reviewScript(pick)), leadInSec,
                )
            } ?: continue
            // 간격 때문에 늦게 시작하면 통과 전에 못 끝내므로 다음 장소를 본다
            if (timing.startInSec < gapLeftSec) continue
            return NarrationPlan(pass.place, pick, timing, pass.side, pass.lateralM, pass.etaSec)
        }
        return null
    }

    private fun reviewScript(pick: EpisodePick.ReviewQuiz): Script =
        Script("기억나요? ${pick.episode.quiz!!.question}", REVIEW_QUIZ_SEC)

    companion object {
        const val REVIEW_QUIZ_SEC = 20
    }
}
