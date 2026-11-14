package app.yetgil.engine.calendar

import app.yetgil.engine.plan.Announcer
import app.yetgil.engine.plan.ListeningHistory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 한국천문연구원 특일 정보의 dateKind (01 국경일·공휴일, 02 기념일, 03 24절기, 04 잡절) */
@Serializable
enum class SpecialDayKind {
    @SerialName("holiday") HOLIDAY,
    @SerialName("anniversary") ANNIVERSARY,
    @SerialName("solar_term") SOLAR_TERM,
    @SerialName("seasonal_day") SEASONAL_DAY,
}

@Serializable
data class SpecialDay(
    /** ISO-8601 날짜 (예: 2026-09-25) */
    val date: String,
    /** 특일 정보의 dateName (예: "추석", "상강", "대체공휴일") */
    val name: String,
    val kind: SpecialDayKind,
    val isHoliday: Boolean = false,
)

/** data/special-days/<연도>.json. 수집 도구가 특일 정보 API 로 만든다. */
@Serializable
data class SpecialDayCalendar(
    val year: Int,
    val source: String,
    val updatedAt: String,
    val days: List<SpecialDay>,
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        fun decode(text: String): SpecialDayCalendar = json.decodeFromString(serializer(), text)

        fun encode(calendar: SpecialDayCalendar): String = json.encodeToString(serializer(), calendar)
    }
}

/** 명절과 24절기에 그날 첫 이야기 앞에 붙이는 인사 */
object Greeter {
    val KOREA: ZoneId = ZoneId.of("Asia/Seoul")

    private val fixedGreetings = mapOf(
        "설날" to "새해 복 많이 받으세요.",
        "추석" to "즐거운 한가위 보내세요.",
        "1월1일" to "새해 첫날이에요. 새해 복 많이 받으세요.",
    )

    // 이름만으로는 인사가 어색한 날
    private val skipped = setOf("대체공휴일", "임시공휴일")

    fun greetingFor(date: LocalDate, days: List<SpecialDay>): String? {
        val today = days.filter { it.date == date.toString() && it.name !in skipped }
        today.firstNotNullOfOrNull { fixedGreetings[it.name] }?.let { return it }
        today.firstOrNull { it.kind == SpecialDayKind.SOLAR_TERM }
            ?.let { return "오늘은 절기로 ${Announcer.withCopula(it.name)}." }
        today.firstOrNull { it.kind == SpecialDayKind.HOLIDAY }
            ?.let { return "오늘은 ${Announcer.withCopula(it.name)}." }
        return null
    }

    /** 그날 아직 아무 이야기도 듣지 않았을 때만 인사한다. */
    fun firstStoryGreeting(
        now: Instant,
        history: ListeningHistory,
        days: List<SpecialDay>,
        zone: ZoneId = KOREA,
    ): String? {
        val today = now.atZone(zone).toLocalDate()
        if (history.all.any { it.heardAt.atZone(zone).toLocalDate() == today }) return null
        return greetingFor(today, days)
    }
}
