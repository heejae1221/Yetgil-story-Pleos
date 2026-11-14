package app.yetgil.engine.plan

import app.yetgil.engine.model.Audience
import app.yetgil.engine.model.Episode
import app.yetgil.engine.model.Place
import java.time.Duration
import java.time.Instant

/** 설정 화면의 '누구와 타나요?' */
enum class ListenerMode(val audience: Audience, val quizEnabled: Boolean, val reviewedOnly: Boolean) {
    /** 모든 이야기 · 퀴즈 없음 */
    ADULTS(Audience.ADULT, quizEnabled = false, reviewedOnly = false),

    /** 전설과 사료 비교까지 · 퀴즈 */
    WITH_TEEN(Audience.TEEN, quizEnabled = true, reviewedOnly = false),

    /** 쉬운 말 · 검수된 이야기만 · 퀴즈 */
    WITH_CHILD(Audience.CHILD, quizEnabled = true, reviewedOnly = true),
    ;

    fun allows(episode: Episode): Boolean =
        episode.minAudience.ordinal <= audience.ordinal && (!reviewedOnly || episode.reviewed)
}

data class ListenRecord(val episodeId: String, val placeId: String, val heardAt: Instant)

/** 들은 기록. 앱에서는 Sync SDK 로 동기화한다. */
class ListeningHistory(records: List<ListenRecord> = emptyList()) {
    private val records = records.toMutableList()

    val all: List<ListenRecord> get() = records

    fun add(record: ListenRecord) {
        records += record
    }

    fun lastHeard(episodeId: String): Instant? =
        records.filter { it.episodeId == episodeId }.maxOfOrNull { it.heardAt }

    fun heardWithin(episodeId: String, now: Instant, window: Duration): Boolean =
        lastHeard(episodeId)?.let { Duration.between(it, now) < window } ?: false
}

sealed interface EpisodePick {
    val episode: Episode

    /** 아직 안 들은 (또는 1년이 지난) 편 */
    data class NewEpisode(override val episode: Episode) : EpisodePick

    /** 다 들은 곳: 복습 퀴즈 */
    data class ReviewQuiz(override val episode: Episode) : EpisodePick
}

/** 장소별 여러 편을 순환한다. 들은 편은 1년 동안 다시 나오지 않는다. */
object EpisodeSelector {
    val REPEAT_WINDOW: Duration = Duration.ofDays(365)

    /** 복습 퀴즈는 들은 지 이만큼 지난 편에서만 낸다 */
    val REVIEW_MIN_AGE: Duration = Duration.ofDays(1)

    fun pick(
        place: Place,
        mode: ListenerMode,
        history: ListeningHistory,
        now: Instant,
        reviewQuizEnabled: Boolean = true,
    ): EpisodePick? {
        val eligible = place.episodes.filter(mode::allows).sortedBy { it.number }
        eligible.firstOrNull { !history.heardWithin(it.id, now, REPEAT_WINDOW) }
            ?.let { return EpisodePick.NewEpisode(it) }

        if (!reviewQuizEnabled || !mode.quizEnabled) return null
        // 들은 지 가장 오래된 편의 퀴즈로 복습. 방금 들은 (또는 "그만"한) 편은 복습하지 않는다.
        return eligible
            .filter { it.quiz != null }
            .filter { !history.heardWithin(it.id, now, REVIEW_MIN_AGE) }
            .minByOrNull { history.lastHeard(it.id) ?: Instant.MIN }
            ?.let { EpisodePick.ReviewQuiz(it) }
    }
}
