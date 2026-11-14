package app.yetgil.collector

import app.yetgil.collector.kasi.KasiApi
import app.yetgil.collector.khs.DraftBuilder
import app.yetgil.collector.khs.HeritageCollector
import app.yetgil.collector.khs.KhsApi
import app.yetgil.engine.calendar.SpecialDayCalendar
import app.yetgil.engine.geo.RouteFile
import app.yetgil.engine.model.License
import app.yetgil.engine.model.StoryPackLoader
import app.yetgil.engine.model.StoryPackValidator
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.time.LocalDate
import kotlin.system.exitProcess

private const val USAGE = """사용법: ./gradlew :collector:run --args="<명령> [옵션]"

  heritage      경로 근처 국가유산으로 꾸러미 초안을 만든다 (국가유산청 API, 키 필요 없음)
                --route data/routes/yesan-demo.json  --sido 34[,31]  --out data/packs/<이름>.json
                [--kinds 11,13,12,16] [--corridor-m 3000] [--region 이름] [--license other] [--delay-ms 300]
                --out 파일이 있으면 새 장소만 더한다 (이미 있는 장소는 건드리지 않는다)

  special-days  공휴일·24절기를 받아 data/special-days/<연도>.json 을 만든다 (특일 정보 API)
                --year 2026  [--out data/special-days/2026.json]
                서비스 키는 local.properties 의 dataGoKr.serviceKey 또는 환경 변수 DATA_GO_KR_SERVICE_KEY (커밋되지 않음)

  validate      꾸러미를 검사한다. 오류가 있으면 종료 코드 1
                data/packs/a.json [data/packs/b.json ...]
"""

fun main(args: Array<String>) {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8))
    System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, Charsets.UTF_8))
    val cli = Cli(args.drop(1))
    val code = when (args.firstOrNull()) {
        "heritage" -> heritage(cli)
        "special-days" -> specialDays(cli)
        "validate" -> validate(cli.positional)
        else -> {
            println(USAGE)
            if (args.isEmpty()) 0 else 2
        }
    }
    exitProcess(code)
}

private fun heritage(cli: Cli): Int {
    val route = RouteFile.decode(File(cli.required("route")).readText(Charsets.UTF_8))
    val out = File(cli.required("out"))
    val license = cli["license"]?.let { name -> License.entries.first { it.name.equals(name, true) || it.serialName() == name } }
        ?: License.OTHER
    val http = JdkHttpGet(delayMs = cli["delay-ms"]?.toLong() ?: 300)
    val collector = HeritageCollector(KhsApi(http), DraftBuilder(license), log = ::println)

    val result = collector.collect(
        route = route.toRoute(),
        sidoCodes = cli.list("sido").ifEmpty { error("--sido 가 필요합니다 (예: 34)") },
        kindCodes = cli.list("kinds").ifEmpty { HeritageCollector.DEFAULT_KIND_CODES },
        corridorM = cli["corridor-m"]?.toDouble() ?: HeritageCollector.DEFAULT_CORRIDOR_M,
    )
    val existing = out.takeIf { it.exists() }?.let { StoryPackLoader.decode(it.readText(Charsets.UTF_8)) }
    val merged = PackMerger.merge(
        existing,
        result.places,
        packId = out.nameWithoutExtension,
        region = cli["region"] ?: route.name,
        today = LocalDate.now().toString(),
    )
    out.parentFile?.mkdirs()
    out.writeText(StoryPackLoader.encode(merged.pack) + "\n", Charsets.UTF_8)

    println()
    println("새로 더한 곳 ${merged.added.size}곳:")
    merged.added.forEach { println("  + ${it.name} (${it.id}, ${it.kind})") }
    if (merged.alreadyThere.isNotEmpty()) println("이미 있는 곳 ${merged.alreadyThere.size}곳은 그대로 둠")
    if (result.skipped.isNotEmpty()) {
        println("뺀 것 ${result.skipped.size}건:")
        result.skipped.forEach { println("  - ${it.name}: ${it.reason}") }
    }
    println("저장: ${out.path}  (모두 검수 전 초안입니다. 원고·출처·이용 조건을 확인한 뒤 reviewed=true 로 바꾸세요)")
    return 0
}

private fun specialDays(cli: Cli): Int {
    val key = Secrets.dataGoKrServiceKey() ?: run {
        System.err.println(Secrets.MISSING_KEY_HELP)
        return 2
    }
    val year = cli.required("year").toInt()
    val out = File(cli["out"] ?: "data/special-days/$year.json")
    val days = KasiApi(JdkHttpGet(), key).year(year)
    val calendar = SpecialDayCalendar(
        year = year,
        source = "한국천문연구원 특일 정보 (공공데이터포털)",
        updatedAt = LocalDate.now().toString(),
        days = days,
    )
    out.parentFile?.mkdirs()
    out.writeText(SpecialDayCalendar.encode(calendar) + "\n", Charsets.UTF_8)
    println("${days.size}일 저장: ${out.path}")
    return 0
}

private fun validate(paths: List<String>): Int {
    if (paths.isEmpty()) {
        println(USAGE)
        return 2
    }
    var errors = 0
    for (path in paths) {
        val pack = StoryPackLoader.decode(File(path).readText(Charsets.UTF_8))
        val issues = StoryPackValidator.validate(pack)
        val episodes = pack.places.sumOf { it.episodes.size }
        val reviewed = pack.places.sumOf { p -> p.episodes.count { it.reviewed } }
        println("$path: ${pack.places.size}곳 ${episodes}편 (검수 $reviewed), 퀴즈 ${pack.places.sumOf { p -> p.episodes.count { it.quiz != null } }}개, 시리즈 ${pack.series.size}종")
        for (i in issues) println("  ${i.severity} ${i.path}: ${i.message}")
        errors += issues.count { it.severity == StoryPackValidator.Severity.ERROR }
    }
    return if (errors > 0) 1 else 0
}

private fun License.serialName(): String =
    License.serializer().descriptor.getElementName(ordinal)

/** --key value 와 --key a,b 를 읽는다. */
private class Cli(args: List<String>) {
    private val options = mutableMapOf<String, String>()
    val positional = mutableListOf<String>()

    init {
        var i = 0
        while (i < args.size) {
            val a = args[i]
            if (a.startsWith("--")) {
                options[a.removePrefix("--")] = args.getOrNull(i + 1) ?: error("$a 다음에 값이 필요합니다")
                i += 2
            } else {
                positional += a
                i++
            }
        }
    }

    operator fun get(key: String): String? = options[key]

    fun required(key: String): String = options[key] ?: error("--$key 가 필요합니다\n\n$USAGE")

    fun list(key: String): List<String> = options[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
