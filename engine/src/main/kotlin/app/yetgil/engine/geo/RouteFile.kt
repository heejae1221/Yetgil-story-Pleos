package app.yetgil.engine.geo

import app.yetgil.engine.model.GeoPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** data/routes 의 경로 JSON. 가상 주행과 수집 도구가 쓴다. */
@Serializable
data class RouteFile(
    val name: String,
    val note: String? = null,
    val points: List<GeoPoint>,
) {
    fun toRoute(): Route = Route(points)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): RouteFile = json.decodeFromString(serializer(), text)
    }
}
