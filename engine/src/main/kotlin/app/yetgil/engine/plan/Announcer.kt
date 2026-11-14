package app.yetgil.engine.plan

import app.yetgil.engine.geo.Side
import kotlin.math.roundToInt

/** 안내 멘트와 화면 문구 */
object Announcer {

    /** TTS 안내: "잠시 후 왼쪽으로 수덕사를 지나요" */
    fun spoken(placeName: String, side: Side): String =
        "잠시 후 ${side.spoken}으로 ${withObjectParticle(placeName)} 지나요"

    /** 화면 문구: "잠시 후 왼쪽 · 약 750m 옆을 지나요" */
    fun onScreen(side: Side, lateralM: Double): String =
        "잠시 후 ${side.spoken} · ${formatDistance(lateralM)} 옆을 지나요"

    /** 50m 단위로 반올림, 1km 부터는 0.1km 단위 */
    fun formatDistance(m: Double): String =
        if (m < 1_000) {
            "약 ${maxOf(50, (m / 50).roundToInt() * 50)}m"
        } else {
            val km = (m / 100).roundToInt() / 10.0
            if (km % 1.0 == 0.0) "약 ${km.toInt()}km" else "약 ${km}km"
        }

    /** 받침이 있으면 '을', 없으면 '를' */
    fun withObjectParticle(word: String): String = word + pick(word, "을", "를", "을(를)")

    /** 받침이 있으면 '이에요', 없으면 '예요': "추석이에요", "입추예요" */
    fun withCopula(word: String): String = word + pick(word, "이에요", "예요", "이에요")

    private fun pick(word: String, withFinal: String, withoutFinal: String, unknown: String): String {
        val last = word.lastOrNull() ?: return ""
        if (last !in '가'..'힣') return unknown
        return if ((last - '가') % 28 != 0) withFinal else withoutFinal
    }
}
