package app.yetgil.story.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvChargePortOpenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import app.yetgil.engine.geo.Geo
import app.yetgil.engine.model.GeoPoint
import app.yetgil.engine.plan.VehicleSignals

private const val TAG = "Yetgil.Car"
private val main = Handler(Looper.getMainLooper())

/**
 * Vehicle SDK: 기어(P)와 충전구 열림으로 정차를 판단한다.
 * 차종이 지원하지 않거나 SDK 가 없으면 값이 null 로 남고 정차로 보지 않는다.
 */
class CarSignals(context: Context, private val onChange: (VehicleSignals) -> Unit) {
    var connected = false
        private set
    var signals = VehicleSignals(parkingBrakeOn = null, charging = null, gearInPark = null)
        private set

    private var vehicle: Vehicle? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = update { it.copy(gearInPark = gear?.let { g -> g == VehicleGear.GEAR_PARK }) }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 상태 실패", e)
        }
    }
    private val chargePortListener = object : EvChargePortOpenListener {
        override fun onEvChargePortOpenUpdated(open: Boolean?) = update { it.copy(charging = open) }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "충전구 상태 실패", e)
        }
    }

    init {
        try {
            val v = Vehicle(context.applicationContext)
            v.initialize()
            vehicle = v
            v.getGear().getCurrentGearSelectionState(
                { gear -> update { it.copy(gearInPark = gear?.let { g -> g == VehicleGear.GEAR_PARK }) } },
                { e -> Log.w(TAG, "기어 조회 실패", e) },
            )
            v.getGear().registerGearSelection(gearListener)
            v.getEvBattery().registerEvChargePortOpen(chargePortListener)
            connected = true
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle SDK 를 쓸 수 없음", t)
        }
    }

    private fun update(change: (VehicleSignals) -> VehicleSignals) {
        main.post {
            signals = change(signals)
            onChange(signals)
        }
    }

    fun release() {
        vehicle?.let {
            runCatching { it.getGear().unregisterGearSelection(gearListener) }
            runCatching { it.getEvBattery().unregisterEvChargePortOpen(chargePortListener) }
            it.release()
        }
    }
}

/** FusedLocation SDK: 1초마다 위치·방향. 속도는 직전 위치와의 거리로 어림한다. */
class CarLocation(context: Context, private val onFix: (GeoPoint, Double, Double) -> Unit) {
    var connected = false
        private set

    private var fused: FusedLocation? = null
    private var last: Pair<GeoPoint, Long>? = null

    private val listener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
            if (data == null) return
            val p = GeoPoint(data.latitude, data.longitude)
            val prev = last
            val speed = if (prev == null || data.timestampMillis <= prev.second) {
                0.0
            } else {
                Geo.distanceM(prev.first, p) / ((data.timestampMillis - prev.second) / 1000.0)
            }
            last = p to data.timestampMillis
            main.post { onFix(p, data.heading.toDouble(), speed) }
        }

        override fun onError(e: Exception) {
            Log.w(TAG, "위치 오류", e)
        }
    }

    init {
        try {
            val f = FusedLocation(context.applicationContext)
            f.initialize()
            if (f.isSupported()) {
                f.registerFusedLocationCallback(listener)
                connected = true
            }
            fused = f
        } catch (t: Throwable) {
            Log.w(TAG, "FusedLocation SDK 를 쓸 수 없음", t)
        }
    }

    fun release() {
        fused?.let {
            runCatching { it.unregisterFusedLocationCallback(listener) }
            it.release()
        }
    }
}

/**
 * NaviHelper SDK: 목적지를 알고, '들렀다 가자'를 경유지로 넣는다.
 * NaviHelper 는 경로 좌표열을 주지 않으므로, 주행 중 이야기 고르기는 진행 방향 앞쪽을 내다보는 방식으로 한다.
 */
class CarNavi(context: Context, private val onDestination: (Destination?) -> Unit) {
    data class Destination(val name: String, val location: GeoPoint, val remainDistanceM: Int, val remainTimeSec: Int)

    var connected = false
        private set

    private var navi: NaviHelper? = null

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(info: DestinationInfo) {
            main.post {
                onDestination(
                    Destination(info.name, GeoPoint(info.latitude, info.longitude), info.remainDistance, info.remainTime),
                )
            }
        }

        override fun onDestinationChanged() {
            navi?.getDestinationInfo()
        }

        override fun onRouteCancelled() = main.post { onDestination(null) }.let { }

        override fun onRouteEnded() = main.post { onDestination(null) }.let { }
    }

    private val appContext = context.applicationContext
    private var pending: (() -> Unit)? = null

    /**
     * 처음 필요할 때만 연결한다. NaviHelper 를 초기화하면 Pleos 내비 앱이 맨 앞으로 떠서
     * 앱을 켜자마자 지도가 앱을 덮는 것을 에뮬레이터에서 확인했다 (앱 시작 35초 뒤 내비가 top-activity 로 시작).
     */
    fun connect(then: (() -> Unit)? = null) {
        if (connected) {
            then?.invoke()
            return
        }
        if (then != null) pending = then
        if (navi != null) return
        try {
            val n = NaviHelper(appContext)
            navi = n
            n.initialize(
                {
                    main.post {
                        connected = true
                        n.addListener(listener)
                        n.getDestinationInfo()
                        pending?.invoke()
                        pending = null
                    }
                },
                { e -> Log.w(TAG, "NaviHelper 초기화 실패", e) },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "NaviHelper SDK 를 쓸 수 없음", t)
        }
    }

    /** 연결 전이면 연결한 뒤에 넣는다. 성공 여부는 onWaypointChanged 로 오지만, 화면에는 요청을 보낸 것까지 보여 준다. */
    fun addWaypoint(name: String, location: GeoPoint, placeId: String): Boolean {
        val send = {
            runCatching {
                navi?.addWaypoint(RequestWaypointInfo(location.lat, location.lon, name, WaypointIndex.FIRST, placeId, "", "", ""))
            }.onFailure { Log.w(TAG, "경유지 추가 실패", it) }
            Unit
        }
        connect(send)
        return navi != null
    }

    fun release() {
        navi?.let {
            runCatching { it.removeListener(listener) }
            it.release()
        }
    }
}
