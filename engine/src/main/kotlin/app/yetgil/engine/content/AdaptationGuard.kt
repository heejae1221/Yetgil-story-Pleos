package app.yetgil.engine.content

import app.yetgil.engine.model.Source

/**
 * Gleo AI LLM 이 듣는 사람 눈높이로 풀어 쓴 글을 확인한다.
 * - 변경금지 출처면 각색본을 쓰지 않고 원문을 그대로 읽는다.
 * - 원문에 없는 숫자(연도·수량)가 생기면 버린다.
 * - 원문보다 지나치게 길면 시간 안에 못 끝나므로 버린다.
 * 버리면 원문을 쓴다.
 */
object AdaptationGuard {
    const val MAX_LENGTH_RATIO = 1.3

    enum class Reason { UNMODIFIABLE_SOURCE, NEW_NUMBER, TOO_LONG, EMPTY }

    sealed interface Result {
        val text: String

        data class Accepted(override val text: String) : Result

        data class Rejected(val reason: Reason, override val text: String, val detail: String = "") : Result
    }

    private val NUMBER = Regex("""\d+(?:[.,]\d+)*""")

    fun check(original: String, adapted: String, sources: List<Source>): Result {
        if (sources.any { !it.modifiable }) {
            return if (squash(adapted) == squash(original)) {
                Result.Accepted(original)
            } else {
                Result.Rejected(Reason.UNMODIFIABLE_SOURCE, original)
            }
        }
        if (adapted.isBlank()) return Result.Rejected(Reason.EMPTY, original)

        val known = numbers(original)
        val added = numbers(adapted) - known
        if (added.isNotEmpty()) {
            return Result.Rejected(Reason.NEW_NUMBER, original, added.joinToString())
        }
        if (adapted.length > original.length * MAX_LENGTH_RATIO) {
            return Result.Rejected(Reason.TOO_LONG, original, "${adapted.length} > ${original.length}")
        }
        return Result.Accepted(adapted)
    }

    internal fun numbers(s: String): Set<String> =
        NUMBER.findAll(s).map { it.value.replace(",", "") }.toSet()

    private fun squash(s: String) = s.filterNot { it.isWhitespace() }
}
