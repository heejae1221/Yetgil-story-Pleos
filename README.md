# 옛길 이야기 (Pleos Connect 앱)

역사가 남은 곳을 차로 지날 때, 그 장소를 지나기 전에 끝나도록 그 자리의 이야기를 들려주는 Pleos Connect 앱. 현대차·기아 Pleos 앱 공모전 출품작.

## 심사용 안내

| 항목 | 내용 |
| --- | --- |
| 앱 이름 | 옛길 이야기 |
| 한 줄 소개 | 역사가 남은 곳을 차로 지날 때, 그곳을 지나기 전에 끝나도록 그 자리의 이야기를 목소리로 들려주는 Pleos Connect 앱 |
| APK | [`apk/옛길이야기_옛길이야기_release.apk`](apk/) (release 빌드, 패키지 `app.yetgil.story`) |
| 테스트 환경 | Pleos Connect Emulator v2.1.0 (Android 14, API 34, x86_64, Automotive), RAM 4GB, cold boot |
| 필요한 설정 | 없음. Pleos Playground 앱 등록 키 없이 Gleo AI TTS·STT 를 ON_DEVICE 모드로 쓴다 |

**실행 방법**

1. Pleos Connect Emulator 를 cold boot 로 실행한다 (설치는 아래 'Pleos Connect 에뮬레이터').
2. `adb install -r -g apk/옛길이야기_옛길이야기_release.apk` 후 앱 목록에서 '옛길 이야기'를 연다. Pleos 앱 화면 영역에 뜬다.
3. 실차 대신 화면 아래 **시연 주행** 막대를 쓴다.
   - **출발**: 저장소의 경부고속도로 근사 경로(서울→부산)를 시속 100km 로 달린다.
   - **다음 장소 앞으로**: 다음 이야기 장소의 약 1분 40초 전으로 건너뛴다. 예고 → 이야기 낭독 → (퀴즈가 있는 곳은) 퀴즈 순으로 이어진다. 다섯 번 누르면 독산성 세마대(퀴즈 있음)다.
   - **정차·충전 (P)**: 정차로 바꿔 근처 긴 이야기를 보여 준다.
   - 퀴즈를 듣는 동안 **정답 말하기 / 틀린 답** 버튼이 나온다 (마이크로 말해도 된다).
4. 탭: 지금 이야기 · 이번 길(경로 미리보기) · 도감 · 설정(듣는 사람, 빈도, SDK 연결 상태).

에뮬레이터 화면: [docs/design/preview/](docs/design/preview/) 의 `emulator-*.png`.

- 접수양식 02 답변: [docs/application.md](docs/application.md)
- 차량 화면 시안 7종: [docs/design/](docs/design/)

## 화면

| 파일 | 화면 |
| --- | --- |
| `Main.dc.html` | ① 주행 중 · 이야기 들려주는 중 |
| `NavWidget.dc.html` | ② 내비 위 미니 카드 |
| `RoutePreview.dc.html` | ③ 목적지 설정 시 · 이번 길 미리보기 |
| `Quiz.dc.html` | ④ 가족 퀴즈 |
| `Parked.dc.html` | ⑤ 정차·충전 중 · 긴 이야기 |
| `Collection.dc.html` | ⑥ 이야기 도감 · 부모 리포트 |
| `Settings.dc.html` | ⑦ 설정 |

디자인 방향은 Pleos Connect 공개 자료(현대차그룹 소개 글·공개 화면)를 참고했다. 현대차 내부 디자인 시스템을 옮긴 것은 아니다. 뉴트럴 블랙·그레이 면에 강조색은 하나만 쓰고, 산세리프(Pretendard) 한 벌을 쓴다. 반경은 작게 두고 1px 구분선을 쓰며, 버튼은 72px 이상으로 크게 둔다. 앱 화면 영역 위쪽에는 공통 앱 바(앱 이름 · 탭 · 상태)를 둔다.

- 색·서체·간격·반경과 공통 컴포넌트(`ui-` 클래스)는 [docs/design/tokens.css](docs/design/tokens.css) 한 곳에 있다.
- 시안은 캔버스에서 한 파일씩 열리므로 각 파일의 `<helmet>` 에 토큰 사본을 품는다. `tokens.css` 를 고친 뒤 `node docs/design/sync-tokens.mjs` 로 7개 파일에 복사한다. 시안 안의 사본은 직접 고치지 않는다.
- 강조색은 시안마다 `accent` 속성(`--ui-accent`)으로 바꿀 수 있다. 기본값은 `#3D8BFF` 이다.
- 아이콘은 lucide 원본 경로를 쓴다. 면(패널)은 화면마다 중심 정보에만 쓰고, 보조 정보는 배경 없이 구분선으로 나눈다.
- 렌더한 화면 이미지: [docs/design/preview/](docs/design/preview/) (`overview.png` 가 전체 모음). 영상 빌드 결과를 캡처한 것이라 시안을 고치면 다시 찍어야 한다.

## 기술 스택

- Kotlin, Pleos Connect SDK (Android Automotive OS 14 기반 Pleos Connect v2.1.0 에뮬레이터에서 확인)
- 앱에서 쓰는 Pleos SDK: Vehicle 2.3.2(기어·충전구), NaviHelper 3.0.5(목적지·경유지), TextToSpeech 3.3.1·SpeechToText 3.3.1(Gleo AI), FusedLocation 1.1.3
  - 공개 Maven 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/` (인증 없음)
  - SDK 문서 목록에 Sync SDK 와 인앱 결제는 없다. Gleo AI LLM SDK(2.1.3.2)는 있지만 아직 쓰지 않는다.
- 이야기 데이터: 국가유산청 API, 위키백과로 확인한 사실로 직접 쓴 원고, (예정) 관광 스토리텔링 DB, TourAPI 4.0, 특일 정보

## 구조

```
engine/                 순수 Kotlin 엔진 (Pleos SDK 의존 없음, JVM에서 테스트)
  model/                이야기 꾸러미 데이터 모델 · JSON 로더 · 검증기
  geo/                  거리·방위 계산, 경로 투영(진행 거리·옆 거리·좌우), 경로 파일
  plan/                 지나갈 장소 예측, 25초/90초 길이 결정, 편 순환, 다음 이야기 스케줄러,
                        안내 멘트, 정차 판단·정차용 이야기, 경로 미리보기
  interaction/          음성 명령 ("그만", "다시 들려줘", "들렀다 가자", "퀴즈는 됐어"), 퀴즈 채점
  content/              AI 각색 검사(변경금지·새 숫자·길이), 도감·교과 주제 집계, 낭독 시간 어림
  calendar/             특일(명절·24절기) 파일, 그날 첫 이야기 인사
  session/              DriveSession: SDK 이벤트 → 할 일(Action) 상태 기계. 차량 앱은 이것만 연결한다
app/                    차량 앱 (Android Automotive · Pleos Connect SDK · Jetpack Compose)
  platform/             Pleos SDK 어댑터 (Voice: TTS·STT, Car: Vehicle·FusedLocation·NaviHelper)
  DriveController       SDK 콜백 → DriveSession → Action → SDK. 시연 주행(경로 파일로 달리기) 포함
  ui/                   지금 이야기 · 이번 길 · 도감 · 설정 (docs/design/tokens.css 와 같은 값)
collector/              수집 도구: 국가유산청 API → 꾸러미 초안, 특일 정보 API → 특일 파일, 꾸러미 검사
simulator/              가상 주행: 실차 없이 DriveSession 을 돌려 시간순으로 출력
data/packs/             이야기 꾸러미 JSON (모두 검수 전). 앱은 data/ 를 그대로 assets 로 쓴다
data/routes/            가상 주행·수집용 경로
data/special-days/      특일 파일
docs/schema/            꾸러미 JSON Schema (VS Code 에서 data/packs/*.json 자동 검사)
```

## 실행

JDK 17 과 Android SDK 가 필요하다. 이 PC 에서는 압축본으로 `C:/Users/<사용자>/tools` 아래에 두었다
(`jdk-17.0.21+9`, `android-sdk`). `JAVA_HOME` 을 지정하고, Android SDK 경로는 `local.properties` 의 `sdk.dir` 에 둔다
(`sdk.dir=C:/Users/<사용자>/tools/android-sdk`, 슬래시로 쓴다. 이 파일은 커밋하지 않는다).

```bash
./gradlew test                      # 엔진 + 수집 도구 단위 테스트
./gradlew :app:assembleDebug        # 차량 앱 (app/build/outputs/apk/debug)
./gradlew :app:assembleRelease      # 시연용 release APK (아직 디버그 키로 서명)
./gradlew :simulator:run            # 가상 주행: 추석 귀성길 → 도착 후 충전 → 이틀 뒤 귀경길 → 도감
./gradlew :simulator:run --args="--mode child --frequency normal --speed-kmh 90"

# 경로 근처 국가유산으로 꾸러미 초안 만들기 (키 필요 없음). 파일이 있으면 새 장소만 더한다.
./gradlew :collector:run --args="heritage --route data/routes/gyeongbu-demo.json --sido 11,31,34,33,25,45,37,22,38,21,26 --corridor-m 4000 --out data/packs/gyeongbu-khs-draft.json"
# 특일 파일 만들기 (공공데이터포털 서비스 키는 환경 변수로만)
DATA_GO_KR_SERVICE_KEY=... ./gradlew :collector:run --args="special-days --year 2026"
# 꾸러미 검사 (오류가 있으면 종료 코드 1)
./gradlew :collector:run --args="validate data/packs/gyeongbu-stories.json data/packs/gyeongbu-khs-draft.json"
```

### Pleos Connect 에뮬레이터

공식 안내는 Pleos Playground 개발자 가이드 「Pleos Connect Emulator 설치 및 실행」이다. 시스템 이미지는
`https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` 에 있다 (42dot SDK 라이선스).
이 PC 에서는 이미지를 `android-sdk/system-images/android-34/pleos_car_emulator/x86_64` 에 풀고 AVD `Pleos_Connect_v2.1.0`(RAM 4GB, cold boot)을 만들었다.

```bash
emulator -avd Pleos_Connect_v2.1.0 -no-snapshot -gpu swiftshader_indirect   # 외장 GPU 가 없으면 소프트웨어 렌더링
adb shell settings put global verifier_verify_adb_installs 0                 # 느린 에뮬레이터에서 설치 검증 시간 초과 방지
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.yetgil.story/.MainActivity
```

- 공식 권장 사양은 외장 GPU(RTX 3060 이상)다. 이 PC(Intel Iris Xe)에서는 `-gpu auto` 로는 멈췄고, `swiftshader_indirect` 로 약 3분 만에 부팅했다. 느리지만 앱·SDK 동작 확인은 된다.
- Pleos Connect Virtual Console(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/)은 열리지만, 이 PC 에서는 차량 신호 브리지가 `emulator_inactive` 로 남아 기어를 바꿀 수 없었다. 그래서 앱 아래의 '시연 주행' 버튼(출발 · 다음 장소 앞으로 · 정차·충전)으로 주행과 정차를 대신한다.

## 차량 앱 연결 (`:app`)

SDK 호출은 `:app` 에만 둔다. `:app` 은 SDK 콜백을 `DriveSession` 이벤트로 넘기고, 돌려받은 `Action` 을 SDK 로 실행한다.
SDK 를 쓸 수 없는 환경(일반 안드로이드 에뮬레이터 등)에서는 안드로이드 TTS 로 읽고, 음성 인식 대신 화면 버튼을 쓴다.

| Pleos SDK | DriveSession |
| --- | --- |
| NaviHelper `getDestinationInfo`, `addListener` (목적지 변경) | 미리보기 (현재 위치→목적지 방향 기준). NaviHelper 는 경로 좌표를 주지 않으므로 주행 중에는 `onRoute(null)` 로 진행 방향 앞쪽을 내다본다. 시연 주행은 경로 파일로 `onRoute(route)` |
| FusedLocation `registerFusedLocationCallback`, 1초마다 | `onLocation(위치, 방향, 속도, 시각)` (속도는 직전 위치와의 거리로 어림) |
| Vehicle `Gear`(P), `EvBattery` 충전구 열림. SDK 2.3 에는 주차 브레이크·충전 상태 API 가 없다 | `onVehicleSignals(VehicleSignals(gearInPark, charging))` |
| Gleo AI TextToSpeech `onDone` (늦으면 예상 시간의 2배 뒤 끝난 것으로 침) | `onSpeechDone(id)` |
| Gleo AI SpeechToText 최종 결과 | `onUtterance(text)` |
| 1초 타이머 | `onTick()` (퀴즈 답 12초 기다림) |
| ← `Action.Speak` / `StopSpeaking` | TextToSpeech `speak` / `stop` (한자·연도 괄호는 소리 내지 않음) |
| ← `Action.ListenForAnswer` | SpeechToText `request` |
| ← `Action.AddWaypoint` | NaviHelper `addWaypoint` |
| ← `Action.ShowDrivingCard` / `ShowParkedStory` / `ClearCard` | 지금 이야기 화면 |

들은 기록은 기기 안(SharedPreferences)에만 둔다. Sync SDK 는 문서 목록에 없다.

Pleos Playground 'My Project' 의 Client ID/Secret 이 있으면 `local.properties` 에 `pleos.clientId`, `pleos.clientSecret` 로 넣는다.
그러면 TTS·STT 를 HYBRID(서버) 모드로 쓰고, 없으면 ON_DEVICE 모드로만 쓴다.

## 현재 상태 (2026-11-14)

- 단위 테스트 66개 통과: 엔진 55개, 수집 도구 11개.
- 차량 앱이 Pleos Connect v2.1.0 에뮬레이터에서 돈다. Pleos 앱 영역(왼쪽 주행 정보 옆)에 뜨고, 독에 아이콘이 붙는다.
  확인한 것: Vehicle SDK 기어(P → 정차), FusedLocation·NaviHelper 연결, Gleo AI TextToSpeech 로 이야기 낭독, 퀴즈 질문 → SpeechToText 듣기 → 12초 뒤 정답 공개, 이번 길 미리보기, 도감.
  에뮬레이터 화면은 [docs/design/preview/](docs/design/preview/) 의 `emulator-*.png`.
- 이야기 데이터 101곳 196편 (퀴즈 8개, 정차용 긴 이야기 23편, 시리즈 2종). 그중 시연 경로 6곳 13편은 출처와 대조해 검수했다(`reviewed: true`, 기록은 [docs/review-checklist.md](docs/review-checklist.md)). '아이와 함께' 모드에서는 이 6곳의 이야기와 퀴즈가 나온다.
  - `gyeongbu-odii.json` 과 국가유산청 초안에 더한 편(`-odii-`): 한국관광공사 관광지 오디오 가이드(오디) 대본. `python -X utf8 collector/scripts/odii_collect.py --route data/routes/gyeongbu-demo.json --out data/packs/gyeongbu-odii.json` 로 다시 만든다 (서비스 키 필요). 역사 관련 편만 고르고, 박물관 전시 해설·대화극 형식은 빼며, 한 곳에 4편까지. 이용허락범위 '제한 없음', 원문 그대로(말머리 표시만 뺌), 검수 전.
  - `gyeongbu-khs-draft.json` (80곳 83편): 경부고속도로 근사 경로 4km 안의 국가유산을 수집 도구로 모은 초안. 원고는 국가유산청 설명문을 그대로 자른 것이고 `license: other`, `modifiable: false`. 통도사(2편 추가)·범어사(1편 추가)에는 직접 쓴 이야기를 더했다. 경주 구간에 몰려 있다.
  - `gyeongbu-stories.json` (4곳 8편, 퀴즈 4개): 독산성 세마대, 천안삼거리, 추풍령, 직지사. 위키백과 문서로 확인한 사실만으로 직접 쓴 원고(`license: original`). 전설과 기록을 나눠 썼다.
  - `yesan-khs-draft.json`: 예산 시연 경로용. (덕산온천 자리 표시용 `sample-demo.json` 은 원고가 없어 뺐다.)
- `data/routes/gyeongbu-demo.json` 은 주요 나들목 좌표를 이은 근사 경로다. 독산성(5.8km)·천안삼거리(3.5km)처럼 고속도로에서 떨어진 곳은 장소별 `triggerRadiusM` 을 넓혀 두었다.
- 국가유산청 원문을 그대로 쓴 편은 길고 딱딱하다(한자·연도 괄호, '~이다' 문체). 검수할 때 아이·청소년용으로 다시 쓰거나, 이용 조건(공공누리 유형)을 확인한 뒤 AI 각색을 허용해야 한다.
- 정차용 4분 이야기는 아직 없다.
- 기획서 고칠 곳 (제출 전): Q5·Q7 의 숫자(테스트 37개 → 66개, 22곳 40편 → 실제 개수와 검수 편수), Q5 의 "Sync SDK 로 동기화"·"getRouteStateInfo 로 남은 경로를 받아"·"Brake(주차 브레이크)" 문장, Q7 의 "에뮬레이터 가상 주행 영상"(지금 영상은 시안으로 만든 것).
- 제출용 APK 는 `apk/옛길이야기_옛길이야기_release.apk` . 접수양식 02 답변은 [docs/application.md](docs/application.md), 시연 장소 검수표는 [docs/review-checklist.md](docs/review-checklist.md).

## 비밀 정보

API 키는 커밋하지 않는다 (`local.properties`, `.env`는 `.gitignore`에 포함).

- 공공데이터포털 서비스 키: `local.properties` 의 `dataGoKr.serviceKey=` (또는 환경 변수 `DATA_GO_KR_SERVICE_KEY`). **수집 도구만** 읽는다. 앱과 APK 에는 들어가지 않고, 앱은 수집 결과(`data/packs`)만 담아 키 없이 동작한다.
- Pleos Client ID/Secret(선택): `local.properties` 의 `pleos.clientId`, `pleos.clientSecret`. debug 빌드에만 들어가고 release APK 에는 넣지 않는다 (제출 APK 는 ON_DEVICE 모드로 동작).
- 커밋 검사: `git config core.hooksPath tools/git-hooks` 를 한 번 실행하면, 키 값·`local.properties`·`serviceKey=값` 이 커밋에 섞일 때 막는다. 새 PC 에서 클론한 뒤에도 한 번 실행한다.
