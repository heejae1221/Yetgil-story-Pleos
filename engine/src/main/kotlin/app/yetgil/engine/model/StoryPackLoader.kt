package app.yetgil.engine.model

import kotlinx.serialization.json.Json

object StoryPackLoader {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun decode(text: String): StoryPack = json.decodeFromString(StoryPack.serializer(), text)

    fun encode(pack: StoryPack): String = json.encodeToString(StoryPack.serializer(), pack)
}
