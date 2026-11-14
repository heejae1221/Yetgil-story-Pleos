package app.yetgil.collector

import app.yetgil.engine.geo.Geo
import app.yetgil.engine.model.Place
import app.yetgil.engine.model.StoryPack

/**
 * 새로 모은 초안을 기존 꾸러미에 더한다.
 * 이미 있는 장소(같은 id 또는 가까운 곳)는 사람이 고쳤을 수 있으므로 건드리지 않는다.
 */
object PackMerger {
    const val NEARBY_M = 300.0

    data class Result(val pack: StoryPack, val added: List<Place>, val alreadyThere: List<Place>)

    fun merge(existing: StoryPack?, drafts: List<Place>, packId: String, region: String, today: String): Result {
        val base = existing ?: StoryPack(StoryPack.SCHEMA_VERSION, packId, region, today, emptyList())
        val added = mutableListOf<Place>()
        val alreadyThere = mutableListOf<Place>()
        for (draft in drafts) {
            val all = base.places + added
            val same = all.any { it.id == draft.id || Geo.distanceM(it.location, draft.location) < NEARBY_M }
            if (same) alreadyThere += draft else added += draft
        }
        return Result(base.copy(updatedAt = today, places = base.places + added), added, alreadyThere)
    }
}
