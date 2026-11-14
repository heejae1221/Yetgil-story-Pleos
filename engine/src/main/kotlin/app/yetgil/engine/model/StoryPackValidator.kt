package app.yetgil.engine.model

import app.yetgil.engine.content.ScriptTiming
import kotlin.math.abs

/** 꾸러미를 앱에 싣기 전에 확인한다. ERROR 가 하나라도 있으면 싣지 않는다. */
object StoryPackValidator {

    enum class Severity { ERROR, WARNING }

    data class Issue(val severity: Severity, val path: String, val message: String)

    val SHORT_RANGE_SEC = 15..40
    val LONG_RANGE_SEC = 60..120
    val PARKED_RANGE_SEC = 150..300

    /** durationSec 과 글 길이로 어림한 시간이 이 비율 넘게 다르면 경고 */
    const val TIMING_TOLERANCE = 0.3

    // 대한민국 대략 범위 (독도·마라도 포함)
    private val LAT_RANGE = 33.0..39.0
    private val LON_RANGE = 124.0..132.0

    fun validate(pack: StoryPack): List<Issue> {
        val issues = mutableListOf<Issue>()
        fun error(path: String, msg: String) = issues.add(Issue(Severity.ERROR, path, msg))
        fun warn(path: String, msg: String) = issues.add(Issue(Severity.WARNING, path, msg))

        if (pack.schemaVersion != StoryPack.SCHEMA_VERSION) {
            error("schemaVersion", "지원하는 버전은 ${StoryPack.SCHEMA_VERSION} 입니다 (${pack.schemaVersion})")
        }

        val seriesIds = pack.series.map { it.id }.toSet()
        val placeIds = mutableSetOf<String>()
        val episodeIds = mutableSetOf<String>()

        for (place in pack.places) {
            val pp = "places[${place.id}]"
            if (!placeIds.add(place.id)) error(pp, "장소 id 중복")
            if (place.location.lat !in LAT_RANGE || place.location.lon !in LON_RANGE) {
                error("$pp.location", "국내 좌표 범위를 벗어났습니다")
            }
            if (place.triggerRadiusM <= 0) error("$pp.triggerRadiusM", "0보다 커야 합니다")
            if (place.episodes.isEmpty()) error(pp, "편이 하나도 없습니다")
            if (place.imageUrl != null && place.imageCredit.isNullOrBlank()) {
                error("$pp.imageCredit", "사진에는 출처 표기가 필요합니다")
            }

            val numbers = place.episodes.map { it.number }.sorted()
            if (numbers != (1..place.episodes.size).toList()) {
                error("$pp.episodes", "편 번호는 1부터 빠짐없이 이어져야 합니다: $numbers")
            }

            for (ep in place.episodes) {
                val epPath = "$pp.episodes[${ep.id}]"
                if (!episodeIds.add(ep.id)) error(epPath, "편 id 중복")
                checkScript("$epPath.scripts.short", ep.scripts.short, SHORT_RANGE_SEC, ::error)
                ep.scripts.long?.let { checkScript("$epPath.scripts.long", it, LONG_RANGE_SEC, ::error) }
                ep.scripts.parked?.let { checkScript("$epPath.scripts.parked", it, PARKED_RANGE_SEC, ::error) }
                for ((name, script) in listOfNotNull(
                    "short" to ep.scripts.short,
                    ep.scripts.long?.let { "long" to it },
                    ep.scripts.parked?.let { "parked" to it },
                )) {
                    val estimated = ScriptTiming.estimateSec(script.text)
                    if (abs(estimated - script.durationSec) > script.durationSec * TIMING_TOLERANCE) {
                        warn("$epPath.scripts.$name", "글 길이로 어림한 낭독 시간 ${estimated}초가 ${script.durationSec}초와 다릅니다")
                    }
                }

                if (ep.sources.isEmpty()) error("$epPath.sources", "출처가 없습니다")
                ep.sources.forEachIndexed { i, s ->
                    if (s.license.forbidsModification && s.modifiable) {
                        error("$epPath.sources[$i]", "${s.license} 은 변경금지이므로 modifiable=false 여야 합니다")
                    }
                    if (s.title.contains("TODO") || s.url.isNullOrBlank()) {
                        warn("$epPath.sources[$i]", "출처 확인 필요")
                    }
                }
                if (!ep.reviewed) warn(epPath, "검수 전 (아이와 함께 모드에서 나오지 않음)")

                ep.quiz?.let { q ->
                    if (q.question.isBlank()) error("$epPath.quiz.question", "질문이 비었습니다")
                    if (q.answers.none { it.isNotBlank() }) error("$epPath.quiz.answers", "정답이 없습니다")
                }
                for (sid in ep.seriesIds) {
                    if (sid !in seriesIds) error("$epPath.seriesIds", "없는 시리즈: $sid")
                }
            }
        }

        for (s in pack.series) {
            for (pid in s.placeIds) {
                if (pid !in placeIds) error("series[${s.id}]", "없는 장소: $pid")
            }
        }
        return issues
    }

    private fun checkScript(
        path: String,
        script: Script,
        range: IntRange,
        error: (String, String) -> Boolean,
    ) {
        if (script.text.isBlank()) error(path, "내용이 비었습니다")
        if (script.durationSec !in range) error(path, "길이 ${script.durationSec}초는 ${range.first}~${range.last}초 범위 밖입니다")
    }
}
