package app.yetgil.collector

import java.io.File
import java.util.Properties

/**
 * 공공데이터포털 서비스 키. 저장소에는 절대 쓰지 않는다.
 *
 * 찾는 순서: 환경 변수 DATA_GO_KR_SERVICE_KEY → 저장소 루트 local.properties 의 dataGoKr.serviceKey
 * (local.properties 는 .gitignore 에 있다). 키는 수집 도구(서버·개발 PC)에서만 쓰고,
 * 차량 앱과 APK 에는 들어가지 않는다. 앱은 수집한 결과(data/packs)만 담는다.
 * 요청 URL 을 로그에 남길 때는 [JdkHttpGet.redact] 로 키를 가린다.
 */
object Secrets {
    const val ENV = "DATA_GO_KR_SERVICE_KEY"
    const val PROPERTY = "dataGoKr.serviceKey"

    val MISSING_KEY_HELP = """
        공공데이터포털 서비스 키가 없습니다. 둘 중 하나로 넣어 주세요 (어느 쪽도 커밋되지 않습니다):
          1) 저장소 루트의 local.properties 에 한 줄:  $PROPERTY=발급받은_일반_인증키
          2) 환경 변수:  $ENV=발급받은_일반_인증키
    """.trimIndent()

    fun dataGoKrServiceKey(root: File = File(".")): String? {
        System.getenv(ENV)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val file = File(root, "local.properties").takeIf { it.isFile } ?: return null
        val props = Properties().apply { file.reader(Charsets.UTF_8).use(::load) }
        return props.getProperty(PROPERTY)?.trim()?.takeIf { it.isNotEmpty() && it != "발급받은_일반_인증키" }
    }
}
