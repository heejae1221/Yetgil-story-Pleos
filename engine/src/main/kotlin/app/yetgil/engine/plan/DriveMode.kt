package app.yetgil.engine.plan

import app.yetgil.engine.geo.Geo
import app.yetgil.engine.model.Episode
import app.yetgil.engine.model.GeoPoint
import app.yetgil.engine.model.Place
import java.time.Instant

/**
 * Vehicle SDK 에서 받은 값. 차종이 지원하지 않는 값(checkXXXCapability 가 false)은 null.
 *
 * Pleos Vehicle SDK 2.3 은 주차 브레이크 대신 브레이크 페달 상태만 주고, 충전 상태 대신 충전구 열림만 준다.
 * 그래서 차량 앱은 기어 P([gearInPark])와 충전구 열림([charging])으로 정차를 판단한다.
 */
data class VehicleSignals(
    val parkingBrakeOn: Boolean?,
    val charging: Boolean?,
    val gearInPark: Boolean? = null,
)

enum class DriveMode {
    /** 목소리로만, 장소 이름·방향·큰 버튼만 */
    DRIVING,

    /** 긴 글과 사진을 보여도 된다 */
    PARKED,
}

object DriveModes {
    /** 신호 대기처럼 잠깐 선 것은 주행으로 본다. 주차 브레이크·기어 P·충전이 확인될 때만 정차. */
    fun from(signals: VehicleSignals): DriveMode =
        if (signals.parkingBrakeOn == true || signals.gearInPark == true || signals.charging == true) {
            DriveMode.PARKED
        } else {
            DriveMode.DRIVING
        }
}

data class ParkedStory(val place: Place, val episode: Episode, val distanceM: Double)

/** 정차·충전 중: 근처 장소의 긴 이야기를 고른다. */
object ParkedStoryFinder {
    const val DEFAULT_RADIUS_M = 10_000.0

    fun find(
        position: GeoPoint,
        places: List<Place>,
        mode: ListenerMode,
        history: ListeningHistory,
        now: Instant,
        radiusM: Double = DEFAULT_RADIUS_M,
    ): ParkedStory? =
        places
            .map { it to Geo.distanceM(position, it.location) }
            .filter { (_, d) -> d <= radiusM }
            .sortedBy { (_, d) -> d }
            .firstNotNullOfOrNull { (place, d) ->
                place.episodes
                    .filter { mode.allows(it) && it.scripts.parked != null }
                    .sortedBy { it.number }
                    .firstOrNull { !history.heardWithin(it.id, now, EpisodeSelector.REPEAT_WINDOW) }
                    ?.let { ParkedStory(place, it, d) }
            }
}
