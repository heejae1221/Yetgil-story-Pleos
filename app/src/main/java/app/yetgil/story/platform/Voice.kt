package app.yetgil.story.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import app.yetgil.story.BuildConfig
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode

private const val TAG = "Yetgil.Voice"

private val main = Handler(Looper.getMainLooper())

private fun hasPleosCredentials() = BuildConfig.PLEOS_CLIENT_ID.isNotBlank() && BuildConfig.PLEOS_CLIENT_SECRET.isNotBlank()

/**
 * 내레이션. Gleo AI TextToSpeech 를 먼저 쓰고, 초기화에 실패하면(일반 안드로이드 에뮬레이터 등) 안드로이드 TTS 로 읽는다.
 * 콜백은 메인 스레드로 넘긴다.
 */
class Narrator(context: Context, private val onDone: (String) -> Unit) {
    var engineName: String = "준비 중"
        private set

    private val appContext = context.applicationContext
    private var pleos: TextToSpeech? = null
    private var fallback: android.speech.tts.TextToSpeech? = null
    private var currentId: String? = null

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = finished()
        override fun onStop() = Unit
        override fun onError(errorType: TtsError) {
            Log.w(TAG, "Pleos TTS 오류: $errorType")
            finished()
        }
    }

    init {
        try {
            val mode = if (hasPleosCredentials()) TtsMode.HYBRID else TtsMode.ON_DEVICE
            val tts = TextToSpeech(appContext, mode)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        tts.setLocale(Locale.KOREA)
                        tts.addEventListener(events)
                        if (hasPleosCredentials()) {
                            tts.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET, object :
                                ai.pleos.playground.tts.listener.OnServerConnectionListener {
                                override fun onConnected() = Unit
                                override fun onFailed(errorType: TtsError) {
                                    Log.w(TAG, "Pleos TTS 앱 등록 실패: $errorType")
                                }
                            })
                        }
                        engineName = "Gleo AI TextToSpeech"
                    }
                }

                override fun onFailure(errorType: TtsError) {
                    Log.w(TAG, "Pleos TTS 초기화 실패: $errorType → 안드로이드 TTS")
                    main.post { useFallback() }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Pleos TTS 를 쓸 수 없음 → 안드로이드 TTS", t)
            useFallback()
        }
    }

    private fun useFallback() {
        pleos = null
        engineName = "안드로이드 TTS (대체)"
        fallback = android.speech.tts.TextToSpeech(appContext) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) fallback?.language = Locale.KOREA
        }.also {
            it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finished()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished()
            })
        }
    }

    private fun finished() {
        main.post {
            val id = currentId ?: return@post
            currentId = null
            onDone(id)
        }
    }

    /**
     * 재생 완료 이벤트가 늦거나 오지 않아도 이야기 흐름이 멈추지 않도록, 예상 시간의 두 배가 지나면 끝난 것으로 친다.
     * (느린 에뮬레이터에서 onDone 이 120초 뒤에야 오는 것을 확인했다)
     */
    fun speak(id: String, text: String, estimatedSec: Int) {
        currentId = id
        main.postDelayed({ if (currentId == id) finished() }, (estimatedSec * 2L + 10L) * 1000L)
        val spoken = forSpeech(text)
        val p = pleos
        if (p != null) {
            p.speak(spoken, true)
        } else {
            fallback?.speak(spoken, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    companion object {
        private val HANJA_OR_NUMBER_PAREN = Regex("""\s*\([^)]*[一-鿿0-9][^)]*\)""")
        private val BOOK_BRACKETS = Regex("""[『』「」《》〈〉]""")

        /**
         * 읽기 좋게만 다듬는다 (뜻은 바꾸지 않는다): 한자·연도 괄호 "선릉(宣陵)", "(1457~1494)" 와 책 이름 괄호를 소리 내지 않는다.
         * 화면과 저장된 원고는 그대로 둔다.
         */
        fun forSpeech(text: String): String =
            text.replace(HANJA_OR_NUMBER_PAREN, "").replace(BOOK_BRACKETS, "")
    }

    /** 멈춘 말은 끝난 것으로 치지 않는다 (DriveSession 이 이미 다음 상태로 넘어갔다). */
    fun stop() {
        currentId = null
        pleos?.stop()
        fallback?.stop()
    }

    fun release() {
        pleos?.let {
            it.removeEventListener(events)
            it.release()
        }
        fallback?.shutdown()
    }
}

/**
 * 음성 인식. Gleo AI SpeechToText 로 "그만", "다시 들려줘", 퀴즈 답을 듣는다.
 * 인식 서비스가 없으면 [available] 이 false 이고, 화면의 버튼으로 같은 명령을 낸다.
 */
class Ears(context: Context, private val onHeard: (String) -> Unit) {
    var available = false
        private set
    var engineName: String = "준비 중"
        private set
    var listening = false
        private set

    private var stt: SpeechToText? = null

    private val results = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (isFinal && text.isNotBlank()) {
                main.post {
                    listening = false
                    onHeard(text)
                }
            }
        }
        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() {
            main.post { listening = false }
        }
        override fun onError(errorType: SttError) {
            Log.w(TAG, "Pleos STT 오류: $errorType")
            main.post { listening = false }
        }
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        try {
            val mode = if (hasPleosCredentials()) SttMode.HYBRID else SttMode.ON_DEVICE
            val s = SpeechToText(context.applicationContext, mode)
            stt = s
            s.initialize(object : SttInitListener {
                override fun onSuccess() {
                    main.post {
                        s.setLocale(Locale.KOREA)
                        s.addListener(results)
                        if (hasPleosCredentials()) {
                            s.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET, object :
                                ai.pleos.playground.stt.listener.OnServerConnectionListener {
                                override fun onConnected() = Unit
                                override fun onFailed(errorType: SttError) {
                                    Log.w(TAG, "Pleos STT 앱 등록 실패: $errorType")
                                }
                            })
                        }
                        available = true
                        engineName = "Gleo AI SpeechToText"
                    }
                }

                override fun onFailure(errorType: SttError) {
                    Log.w(TAG, "Pleos STT 초기화 실패: $errorType")
                    main.post { engineName = "음성 인식 없음 (화면 버튼 사용)" }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Pleos STT 를 쓸 수 없음", t)
            engineName = "음성 인식 없음 (화면 버튼 사용)"
        }
    }

    fun listen() {
        val s = stt ?: return
        if (!available) return
        listening = true
        s.request()
    }

    fun stop() {
        listening = false
        if (available) stt?.stop()
    }

    fun release() {
        stt?.let {
            it.removeListener(results)
            it.release()
        }
    }
}
