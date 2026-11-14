package app.yetgil.engine.plan

import app.yetgil.engine.model.Script
import app.yetgil.engine.model.Scripts

enum class StoryLength { SHORT, LONG }

data class TimingPlan(
    val length: StoryLength,
    val script: Script,
    /** 지금부터 몇 초 뒤에 안내 멘트를 시작할지 */
    val startInSec: Double,
)

/**
 * 남은 시간에 맞춰 25초/90초 분량을 고르고, 장소를 지나기 전에 끝나도록 시작 시점을 정한다.
 * 안내 멘트 + 이야기 + 여유 시간이 통과 시점 전에 들어가야 한다.
 */
object LengthPlanner {
    /** "잠시 후 왼쪽으로 수덕사를 지나요" */
    const val ANNOUNCE_SEC = 4.0

    /** 이야기가 끝나고 통과할 때까지 남겨 둘 여유 */
    const val FINISH_MARGIN_SEC = 5.0

    /** leadInSec: 안내 멘트 앞에 붙는 말 (명절 인사 등) */
    fun requiredSec(script: Script, leadInSec: Double = 0.0): Double =
        leadInSec + ANNOUNCE_SEC + script.durationSec + FINISH_MARGIN_SEC

    fun plan(etaSec: Double, scripts: Scripts, leadInSec: Double = 0.0): TimingPlan? =
        plan(
            etaSec,
            listOfNotNull(scripts.long?.let { StoryLength.LONG to it }, StoryLength.SHORT to scripts.short),
            leadInSec,
        )

    /** candidates 는 선호 순서. 처음으로 들어맞는 것을 고른다. */
    fun plan(etaSec: Double, candidates: List<Pair<StoryLength, Script>>, leadInSec: Double = 0.0): TimingPlan? {
        if (!etaSec.isFinite()) return null
        for ((length, script) in candidates) {
            val need = requiredSec(script, leadInSec)
            if (etaSec >= need) {
                // 장소가 창밖에 보일 때 끝나도록 최대한 늦게 시작한다
                return TimingPlan(length, script, startInSec = etaSec - need)
            }
        }
        return null
    }
}
