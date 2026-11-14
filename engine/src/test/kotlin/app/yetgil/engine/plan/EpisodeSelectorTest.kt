package app.yetgil.engine.plan

import app.yetgil.engine.Fixtures
import app.yetgil.engine.Fixtures.NOW
import app.yetgil.engine.Fixtures.episode
import app.yetgil.engine.model.Audience
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EpisodeSelectorTest {
    private val ep1 = episode("p-1", number = 1)
    private val ep2 = episode("p-2", number = 2, quiz = null)
    private val teenOnly = episode("p-3", number = 3, minAudience = Audience.TEEN)
    private val unreviewed = episode("p-4", number = 4, reviewed = false)
    private val place = Fixtures.place("p", Fixtures.START, listOf(ep2, teenOnly, ep1, unreviewed))

    private fun heard(vararg ids: String, ago: Duration = Duration.ofDays(30)) =
        ListeningHistory(ids.map { ListenRecord(it, "p", NOW - ago) })

    @Test
    fun `안 들은 편 중 번호가 가장 작은 편`() {
        val pick = EpisodeSelector.pick(place, ListenerMode.ADULTS, ListeningHistory(), NOW)
        assertEquals(EpisodePick.NewEpisode(ep1), pick)
    }

    @Test
    fun `1년 안에 들은 편은 건너뛰어 다음 편으로`() {
        val pick = EpisodeSelector.pick(place, ListenerMode.ADULTS, heard("p-1"), NOW)
        assertEquals("p-2", pick?.episode?.id)
    }

    @Test
    fun `1년이 지나면 다시 나온다`() {
        val pick = EpisodeSelector.pick(place, ListenerMode.ADULTS, heard("p-1", ago = Duration.ofDays(366)), NOW)
        assertEquals("p-1", pick?.episode?.id)
    }

    @Test
    fun `아이와 함께는 청소년용과 검수 전 편을 빼고, 다 들으면 복습 퀴즈`() {
        val pick = EpisodeSelector.pick(place, ListenerMode.WITH_CHILD, heard("p-1", "p-2"), NOW)
        assertIs<EpisodePick.ReviewQuiz>(pick)
        assertEquals("p-1", pick.episode.id) // 퀴즈가 있는 편
    }

    @Test
    fun `청소년과 함께는 청소년용 편까지`() {
        val pick = EpisodeSelector.pick(place, ListenerMode.WITH_TEEN, heard("p-1", "p-2"), NOW)
        assertEquals(EpisodePick.NewEpisode(teenOnly), pick)
    }

    @Test
    fun `복습을 끄거나 어른끼리면 다 들은 곳은 조용히 지나간다`() {
        val all = heard("p-1", "p-2", "p-3", "p-4")
        assertNull(EpisodeSelector.pick(place, ListenerMode.WITH_CHILD, all, NOW, reviewQuizEnabled = false))
        assertNull(EpisodeSelector.pick(place, ListenerMode.ADULTS, all, NOW))
    }
}
