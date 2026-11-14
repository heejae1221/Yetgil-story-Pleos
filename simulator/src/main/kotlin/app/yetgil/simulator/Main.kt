package app.yetgil.simulator

import app.yetgil.engine.calendar.SpecialDayCalendar
import app.yetgil.engine.content.Collection
import app.yetgil.engine.geo.Route
import app.yetgil.engine.geo.RouteFile
import app.yetgil.engine.model.StoryPack
import app.yetgil.engine.model.StoryPackLoader
import app.yetgil.engine.plan.Announcer
import app.yetgil.engine.plan.EpisodePick
import app.yetgil.engine.plan.Frequency
import app.yetgil.engine.plan.ListenerMode
import app.yetgil.engine.plan.ListeningHistory
import app.yetgil.engine.plan.RoutePreview
import app.yetgil.engine.plan.StorySettings
import app.yetgil.engine.plan.VehicleSignals
import app.yetgil.engine.session.DriveSession
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.time.OffsetDateTime

private const val USAGE = """가상 주행: 추석 귀성길 → 도착 후 충전 → 이틀 뒤 귀경길
  ./gradlew :simulator:run [--args="옵션"]

  --route        data/routes/yesan-demo.json
  --packs        data/packs 의 모든 꾸러미 (쉼표로 여러 파일 지정 가능)
  --special-days data/special-days/2026.json
  --speed-kmh    60
  --mode         teen (child | teen | adult)  child 는 검수된 편만 나온다
  --frequency    often (often 3분 | normal 8분 | rare 15분)
  --depart       2026-09-24T10:00+09:00
"""

fun main(args: Array<String>) {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8))
    val opts = args.toList().chunked(2).associate { (k, v) -> k.removePrefix("--") to v }
    if ("help" in args || "--help" in args) {
        println(USAGE)
        return
    }

    val routeFile = RouteFile.decode(File(opts["route"] ?: "data/routes/yesan-demo.json").readText(Charsets.UTF_8))
    val packFiles = opts["packs"]?.split(',')?.map(::File)
        ?: File("data/packs").listFiles { f -> f.extension == "json" }.orEmpty().sorted()
    val packs = packFiles.map { StoryPackLoader.decode(it.readText(Charsets.UTF_8)) }
    val all = StoryPack(1, "all", "all", "", packs.flatMap { it.places }, packs.flatMap { it.series })
    val days = File(opts["special-days"] ?: "data/special-days/2026.json")
        .takeIf { it.exists() }
        ?.let { SpecialDayCalendar.decode(it.readText(Charsets.UTF_8)).days }
        .orEmpty()
    val settings = StorySettings(
        listenerMode = when (opts["mode"] ?: "teen") {
            "child" -> ListenerMode.WITH_CHILD
            "adult" -> ListenerMode.ADULTS
            else -> ListenerMode.WITH_TEEN
        },
        frequency = Frequency.valueOf((opts["frequency"] ?: "often").uppercase()),
    )
    val speedMps = (opts["speed-kmh"]?.toDouble() ?: 60.0) / 3.6
    val depart = OffsetDateTime.parse(opts["depart"] ?: "2026-09-24T10:00+09:00").toInstant()
    val route = routeFile.toRoute()
    val history = ListeningHistory()

    println("== 옛길 이야기 가상 주행 ==")
    println("경로: ${routeFile.name} (${Announcer.formatDistance(route.lengthM)})")
    routeFile.note?.let { println("  $it") }
    println("꾸러미: ${packFiles.joinToString { it.name }} · ${all.places.size}곳")
    println("설정: ${settings.listenerMode} · 빈도 ${settings.frequency} · ${opts["speed-kmh"] ?: "60"}km/h")
    if (settings.listenerMode.reviewedOnly && all.places.none { p -> p.episodes.any { it.reviewed } }) {
        println("  ※ 검수된 편이 없어 '아이와 함께'에서는 이야기가 나오지 않습니다. --mode teen 으로 돌려 보세요.")
    }

    // 1. 추석 귀성길: 퀴즈를 맞힌다
    leg("1. 추석 귀성길", route, depart, all, settings, history, days, speedMps) { car ->
        // 도착해서 충전: 근처 긴 이야기, 마음에 들면 "들렀다 가자"
        car.vehicle(VehicleSignals(parkingBrakeOn = true, charging = true), "주차 브레이크 · 충전 시작")
        car.wait(20)
        car.say("여기 들렀다 가자")
        car.wait(300, untilQuiet = true)
        car.vehicle(VehicleSignals(parkingBrakeOn = false, charging = false), "충전 끝 · 출발")
    }

    // 2. 이틀 뒤 같은 길로 돌아온다: 그날 첫 이야기에 명절 인사, 들은 곳은 다음 편이나 복습 퀴즈
    val back = depart.plusSeconds(2 * 24 * 3600 + 5 * 3600)
    leg("2. 이틀 뒤 귀경길 (같은 길 반대 방향)", route.reversed(), back, all, settings, history, days, speedMps, wrongAnswers = true)

    // 3. 도감
    println()
    println("== ⑥ 이야기 도감 ==")
    println("지나간 곳 ${Collection.visitedPlaceIds(history).size}곳 · 들은 편 ${history.all.map { it.episodeId }.toSet().size}편")
    for (s in Collection.seriesProgress(all, history)) println("  시리즈 ${s.name}: ${s.collected}/${s.total}")
    val topics = Collection.curriculumTopics(all, history)
    if (topics.isNotEmpty()) println("  교과 주제: ${topics.entries.joinToString { "${it.key} ${it.value}곳" }}")
}

private fun leg(
    title: String,
    route: Route,
    depart: java.time.Instant,
    pack: StoryPack,
    settings: StorySettings,
    history: ListeningHistory,
    days: List<app.yetgil.engine.calendar.SpecialDay>,
    speedMps: Double,
    wrongAnswers: Boolean = false,
    afterArrival: (VirtualCar) -> Unit = {},
) {
    println()
    println("== $title ==")
    println("③ 이번 길에서 만날 곳:")
    val preview = RoutePreview.build(route, pack.places, settings, history, depart)
    if (preview.isEmpty()) println("  (없음)")
    for (item in preview) {
        val what = when (item.pick) {
            is EpisodePick.NewEpisode -> "${item.pick.episode.number}편 ${item.pick.episode.title}"
            is EpisodePick.ReviewQuiz -> "복습 퀴즈"
        }
        println("  ${Announcer.formatDistance(item.distanceFromStartM)} 지점 ${item.side.spoken} · ${item.place.name} · $what")
    }
    println()

    val session = DriveSession(pack.places, settings, history, days)
    val car = VirtualCar(session, pack.places, depart) { quiz ->
        if (wrongAnswers) "음… 모르겠어요" else "${quiz.answers.first()}!"
    }
    car.drive(route, speedMps)
    afterArrival(car)

    val results = session.quizResults
    if (results.isNotEmpty()) {
        println("퀴즈: ${results.size}문제 중 ${results.count { it.correct == true }}개 맞힘")
    }
}
