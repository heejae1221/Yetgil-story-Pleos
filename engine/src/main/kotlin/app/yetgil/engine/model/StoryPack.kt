package app.yetgil.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 지역별 이야기 꾸러미. 서버의 수집 도구가 만들고 앱이 미리 내려받는다.
 * JSON 형식은 docs/schema/story-pack.schema.json 과 같다.
 */
@Serializable
data class StoryPack(
    val schemaVersion: Int,
    val packId: String,
    val region: String,
    /** ISO-8601 날짜 (예: 2026-10-22) */
    val updatedAt: String,
    val places: List<Place>,
    val series: List<Series> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

@Serializable
data class Place(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val kind: PlaceKind,
    /** 경로가 이 거리(m) 안으로 지나가면 '지나간다'고 본다. */
    val triggerRadiusM: Int = 3_000,
    val episodes: List<Episode>,
    /** 교과 연계 주제 (예: "임진왜란", "우리 지역의 문화유산") */
    val curriculumTopics: List<String> = emptyList(),
    /** 정차 중 보여 줄 사진 (TourAPI 이미지 등). 출처는 imageCredit 에 둔다. */
    val imageUrl: String? = null,
    val imageCredit: String? = null,
)

@Serializable
enum class PlaceKind {
    @SerialName("fortress") FORTRESS,
    @SerialName("temple") TEMPLE,
    @SerialName("royal_tomb") ROYAL_TOMB,
    @SerialName("mountain_pass") MOUNTAIN_PASS,
    @SerialName("hot_spring") HOT_SPRING,
    @SerialName("river") RIVER,
    @SerialName("village") VILLAGE,
    @SerialName("monument") MONUMENT,
    @SerialName("other") OTHER,
}

/** 한 장소의 한 편. 장소마다 여러 편이 있고 number 순서로 들려준다. */
@Serializable
data class Episode(
    val id: String,
    val number: Int,
    val title: String,
    val category: EpisodeCategory,
    /** 이 편을 들려줘도 되는 가장 어린 청취자 */
    val minAudience: Audience,
    /** 사람이 검수했는지. '아이와 함께'에서는 검수된 편만 나온다. */
    val reviewed: Boolean = false,
    val scripts: Scripts,
    val sources: List<Source>,
    val quiz: Quiz? = null,
    val seriesIds: List<String> = emptyList(),
)

@Serializable
enum class EpisodeCategory(val spokenLabel: String) {
    /** 기록으로 남은 이야기 */
    @SerialName("record") RECORD("기록으로 남은 이야기"),
    /** 지명 유래 */
    @SerialName("name_origin") NAME_ORIGIN("이름 유래"),
    /** 전해 내려오는 이야기 (전설·설화) */
    @SerialName("folklore") FOLKLORE("전해 내려오는 이야기"),
    /** 전설과 기록의 차이 */
    @SerialName("legend_vs_record") LEGEND_VS_RECORD("전설과 기록"),
}

/** 어린 순서. ordinal 비교에 쓰므로 순서를 바꾸지 않는다. */
@Serializable
enum class Audience {
    @SerialName("child") CHILD,
    @SerialName("teen") TEEN,
    @SerialName("adult") ADULT,
}

@Serializable
data class Scripts(
    /** 주행 중 짧은 분량 (약 25초) */
    val short: Script,
    /** 주행 중 긴 분량 (약 90초) */
    val long: Script? = null,
    /** 정차·충전 중 긴 이야기 (약 4분) */
    val parked: Script? = null,
)

@Serializable
data class Script(val text: String, val durationSec: Int)

@Serializable
data class Source(
    val title: String,
    val publisher: String,
    val url: String? = null,
    val license: License,
    /** false 면 원문 그대로 읽는다 (AI 각색 금지). */
    val modifiable: Boolean,
)

@Serializable
enum class License(val forbidsModification: Boolean) {
    /** 공공누리 제1유형: 출처표시 */
    @SerialName("kogl_1") KOGL_TYPE1(false),
    /** 공공누리 제2유형: 출처표시 + 상업적 이용금지 */
    @SerialName("kogl_2") KOGL_TYPE2(false),
    /** 공공누리 제3유형: 출처표시 + 변경금지 */
    @SerialName("kogl_3") KOGL_TYPE3(true),
    /** 공공누리 제4유형: 출처표시 + 상업적 이용금지 + 변경금지 */
    @SerialName("kogl_4") KOGL_TYPE4(true),
    @SerialName("cc_by") CC_BY(false),
    /** 직접 쓴 글 */
    @SerialName("original") ORIGINAL(false),
    @SerialName("other") OTHER(false),
}

@Serializable
data class Quiz(
    val question: String,
    /** 정답으로 인정할 표현들 (예: "흰쌀", "쌀") */
    val answers: List<String>,
    val explanation: String,
)

/** 여러 지역에 흩어진 장소를 묶은 시리즈 (예: "율곡 이이를 따라서") */
@Serializable
data class Series(
    val id: String,
    val name: String,
    val placeIds: List<String>,
)
