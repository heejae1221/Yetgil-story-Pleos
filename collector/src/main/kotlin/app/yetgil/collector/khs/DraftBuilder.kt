package app.yetgil.collector.khs

import app.yetgil.engine.content.ScriptTiming
import app.yetgil.engine.model.Audience
import app.yetgil.engine.model.Episode
import app.yetgil.engine.model.EpisodeCategory
import app.yetgil.engine.model.License
import app.yetgil.engine.model.Place
import app.yetgil.engine.model.PlaceKind
import app.yetgil.engine.model.Script
import app.yetgil.engine.model.Scripts
import app.yetgil.engine.model.Source
import app.yetgil.engine.model.StoryPackValidator
import kotlin.math.abs

/**
 * 국가유산청 설명문으로 검수 전 초안을 만든다.
 * 원고는 설명문 앞부분을 문장 단위로 그대로 잘라 쓴다 (고쳐 쓰지 않는다).
 * 이용 조건을 확인하기 전에는 변경 금지로 두어 AI 각색이 붙지 않게 한다.
 */
class DraftBuilder(
    private val license: License = License.OTHER,
    private val modifiable: Boolean = false,
) {
    sealed interface Outcome {
        data class Built(val place: Place) : Outcome

        data class Skipped(val key: String, val name: String, val reason: String) : Outcome
    }

    fun build(detail: KhsDetail): Outcome {
        val item = detail.item
        val location = item.location ?: return Outcome.Skipped(item.key, item.name, "좌표 없음")
        val sentences = sentences(detail.content)
        val short = excerpt(sentences, StoryPackValidator.SHORT_RANGE_SEC, SHORT_TARGET_SEC)
            ?: return Outcome.Skipped(
                item.key,
                item.name,
                "문장 단위로 ${StoryPackValidator.SHORT_RANGE_SEC.first}~${StoryPackValidator.SHORT_RANGE_SEC.last}초 분량을 만들 수 없음 " +
                    "(첫 문장 약 ${sentences.firstOrNull()?.let(ScriptTiming::estimateSec) ?: 0}초). 원고를 직접 써야 함",
            )
        val long = excerpt(sentences, StoryPackValidator.LONG_RANGE_SEC, LONG_TARGET_SEC)
        val parked = excerpt(sentences, StoryPackValidator.PARKED_RANGE_SEC, PARKED_TARGET_SEC)

        val id = placeId(item)
        val name = shortName(item.name, item.sigungu)
        val episode = Episode(
            id = "$id-1",
            number = 1,
            title = "${item.kindName} ${item.name}",
            category = EpisodeCategory.RECORD,
            minAudience = Audience.TEEN,
            reviewed = false,
            scripts = Scripts(short = short, long = long, parked = parked),
            sources = listOf(
                Source(
                    title = "국가유산청 국가유산 정보: ${item.name}",
                    publisher = "국가유산청",
                    url = detail.sourceUrl,
                    license = license,
                    modifiable = modifiable && !license.forbidsModification,
                ),
            ),
        )
        return Outcome.Built(
            Place(
                id = id,
                name = name,
                location = location,
                kind = kind(detail),
                episodes = listOf(episode),
                imageUrl = detail.imageUrl,
                imageCredit = detail.imageUrl?.let { "국가유산청" },
            ),
        )
    }

    companion object {
        const val SHORT_TARGET_SEC = 25
        const val LONG_TARGET_SEC = 90
        const val PARKED_TARGET_SEC = 240

        fun placeId(item: KhsListItem) = "khs-${item.kindCode}-${item.sidoCode}-${item.assetNo}".lowercase()

        /** "예산 수덕사 대웅전"(예산군) → "수덕사 대웅전". 안내 멘트에서 지역 이름을 되풀이하지 않는다. */
        fun shortName(name: String, sigungu: String): String {
            val region = sigungu.trim().removeSuffix("시").removeSuffix("군").removeSuffix("구")
            if (region.isEmpty()) return name
            val rest = name.removePrefix("$region ").trim()
            return rest.ifEmpty { name }
        }

        fun kind(detail: KhsDetail): PlaceKind {
            val name = detail.item.name
            val codes = listOfNotNull(detail.kind, detail.detailKind).joinToString(" ")
            return when {
                Regex("산성|읍성|성곽|토성|성지").containsMatchIn(name) || "성곽" in codes -> PlaceKind.FORTRESS
                Regex("왕릉|릉$|릉과|릉 ").containsMatchIn(name) || "능묘" in codes -> PlaceKind.ROYAL_TOMB
                // "윤봉길 의사 유적" 같은 이름이 절로 잡히지 않게 분류 코드를 먼저 본다
                "불교" in codes || Regex("(?<!의)사$|암$").containsMatchIn(name) -> PlaceKind.TEMPLE
                Regex("고개|새재").containsMatchIn(name) -> PlaceKind.MOUNTAIN_PASS
                "온천" in name -> PlaceKind.HOT_SPRING
                detail.category == "유적건조물" -> PlaceKind.MONUMENT
                else -> PlaceKind.OTHER
            }
        }

        fun sentences(text: String): List<String> =
            text.replace(Regex("\\s+"), " ").trim()
                .split(Regex("(?<=[.!?])\\s+"))
                .filter { it.isNotBlank() }

        /** 앞에서부터 문장을 이어 붙여 range 안에서 target 에 가장 가까운 분량을 고른다. */
        fun excerpt(sentences: List<String>, range: IntRange, targetSec: Int): Script? {
            var best: Script? = null
            val text = StringBuilder()
            for (s in sentences) {
                if (text.isNotEmpty()) text.append(' ')
                text.append(s)
                val sec = ScriptTiming.estimateSec(text.toString())
                if (sec > range.last) break
                if (sec in range && (best == null || abs(sec - targetSec) < abs(best.durationSec - targetSec))) {
                    best = Script(text.toString(), sec)
                }
            }
            return best
        }
    }
}
