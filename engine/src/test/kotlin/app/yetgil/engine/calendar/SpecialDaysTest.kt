package app.yetgil.engine.calendar

import app.yetgil.engine.Fixtures.NOW
import app.yetgil.engine.plan.ListenRecord
import app.yetgil.engine.plan.ListeningHistory
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpecialDaysTest {
    private val days = listOf(
        SpecialDay("2026-09-25", "추석", SpecialDayKind.HOLIDAY, isHoliday = true),
        SpecialDay("2026-10-23", "상강", SpecialDayKind.SOLAR_TERM),
        SpecialDay("2026-08-07", "입추", SpecialDayKind.SOLAR_TERM),
        SpecialDay("2026-10-09", "한글날", SpecialDayKind.HOLIDAY, isHoliday = true),
        SpecialDay("2026-10-05", "대체공휴일", SpecialDayKind.HOLIDAY, isHoliday = true),
    )

    private fun greet(date: String) = Greeter.greetingFor(LocalDate.parse(date), days)

    @Test
    fun `명절 절기 공휴일 인사`() {
        assertEquals("즐거운 한가위 보내세요.", greet("2026-09-25"))
        assertEquals("오늘은 절기로 상강이에요.", greet("2026-10-23"))
        assertEquals("오늘은 절기로 입추예요.", greet("2026-08-07"))
        assertEquals("오늘은 한글날이에요.", greet("2026-10-09"))
        assertNull(greet("2026-10-05"))
        assertNull(greet("2026-10-22"))
    }

    @Test
    fun `그날 이미 이야기를 들었으면 인사하지 않는다`() {
        // NOW 는 2026-09-25 18:00 (한국 시각)
        assertEquals("즐거운 한가위 보내세요.", Greeter.firstStoryGreeting(NOW, ListeningHistory(), days))
        val heardThisMorning = ListeningHistory(listOf(ListenRecord("a-1", "a", NOW - Duration.ofHours(8))))
        assertNull(Greeter.firstStoryGreeting(NOW, heardThisMorning, days))
        val heardYesterday = ListeningHistory(listOf(ListenRecord("a-1", "a", NOW - Duration.ofHours(19))))
        assertEquals("즐거운 한가위 보내세요.", Greeter.firstStoryGreeting(NOW, heardYesterday, days))
    }

    @Test
    fun `특일 파일을 저장했다 읽어도 같다`() {
        val cal = SpecialDayCalendar(2026, "test", "2026-10-22", days)
        assertEquals(cal, SpecialDayCalendar.decode(SpecialDayCalendar.encode(cal)))
    }
}
