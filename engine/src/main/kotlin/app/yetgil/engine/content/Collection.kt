package app.yetgil.engine.content

import app.yetgil.engine.model.StoryPack
import app.yetgil.engine.plan.ListeningHistory

data class SeriesProgress(val seriesId: String, val name: String, val collected: Int, val total: Int)

/** 이야기 도감: 지나간 곳 도장과 시리즈 수집 */
object Collection {
    fun visitedPlaceIds(history: ListeningHistory): Set<String> = history.all.map { it.placeId }.toSet()

    fun seriesProgress(pack: StoryPack, history: ListeningHistory): List<SeriesProgress> {
        val visited = visitedPlaceIds(history)
        return pack.series.map { s ->
            SeriesProgress(s.id, s.name, collected = s.placeIds.count { it in visited }, total = s.placeIds.size)
        }
    }

    /** 부모 리포트용: 들은 장소들의 교과 연계 주제 → 장소 수 */
    fun curriculumTopics(pack: StoryPack, history: ListeningHistory): Map<String, Int> {
        val visited = visitedPlaceIds(history)
        return pack.places
            .filter { it.id in visited }
            .flatMap { it.curriculumTopics }
            .groupingBy { it }
            .eachCount()
    }
}
