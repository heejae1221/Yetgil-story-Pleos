package app.yetgil.engine.plan

import app.yetgil.engine.Fixtures
import app.yetgil.engine.Fixtures.NOW
import app.yetgil.engine.Fixtures.episode
import app.yetgil.engine.Fixtures.place
import app.yetgil.engine.Fixtures.pointBeside
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DriveModeTest {
    @Test
    fun `기어 P 도 정차로 본다 (Pleos Vehicle SDK 는 주차 브레이크 대신 기어를 준다)`() {
        assertEquals(DriveMode.PARKED, DriveModes.from(VehicleSignals(parkingBrakeOn = null, charging = null, gearInPark = true)))
        assertEquals(DriveMode.DRIVING, DriveModes.from(VehicleSignals(parkingBrakeOn = null, charging = false, gearInPark = false)))
        assertEquals(DriveMode.PARKED, DriveModes.from(VehicleSignals(parkingBrakeOn = null, charging = true, gearInPark = false)))
    }

    @Test
    fun `주차 브레이크나 충전 중일 때만 정차`() {
        assertEquals(DriveMode.PARKED, DriveModes.from(VehicleSignals(parkingBrakeOn = true, charging = null)))
        assertEquals(DriveMode.PARKED, DriveModes.from(VehicleSignals(parkingBrakeOn = false, charging = true)))
        assertEquals(DriveMode.DRIVING, DriveModes.from(VehicleSignals(parkingBrakeOn = false, charging = false)))
        // 차종이 둘 다 지원하지 않으면 정차로 바꾸지 않는다
        assertEquals(DriveMode.DRIVING, DriveModes.from(VehicleSignals(null, null)))
    }

    private val nearby = place("nearby", pointBeside(3_000.0, 0.0), listOf(
        episode("nearby-1", number = 1, parkedSec = null),
        episode("nearby-2", number = 2, parkedSec = 240),
    ))
    private val closer = place("closer", pointBeside(1_000.0, 0.0), listOf(episode("closer-1", parkedSec = null)))
    private val tooFar = place("too-far", pointBeside(25_000.0, 0.0), listOf(episode("too-far-1", parkedSec = 240)))

    @Test
    fun `반경 안에서 정차용 원고가 있는 가장 가까운 장소`() {
        val story = ParkedStoryFinder.find(Fixtures.START, listOf(tooFar, closer, nearby), ListenerMode.WITH_CHILD, ListeningHistory(), NOW)!!
        assertEquals("nearby-2", story.episode.id)
        assertEquals(3_000.0, story.distanceM, 10.0)
    }

    @Test
    fun `이미 들었으면 고르지 않는다`() {
        val history = ListeningHistory(listOf(ListenRecord("nearby-2", "nearby", NOW - Duration.ofDays(1))))
        assertNull(ParkedStoryFinder.find(Fixtures.START, listOf(nearby, tooFar), ListenerMode.WITH_CHILD, history, NOW))
    }
}
