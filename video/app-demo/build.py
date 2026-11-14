# 에뮬레이터에서 녹화한 장면(clips/*.webm, clips/log.json)으로 자막 넣은 시연 영상 구성(index.html)을 만든다.
#   python video/app-demo/build.py     → video/app-demo/index.html, clips/*.mp4
#   cd video/app-demo && node ../tools/hf.mjs render -o ../renders/yetgil-app-demo.mp4
# 녹화 원본(clips/)은 용량이 커서 저장소에 넣지 않는다.
import html, json, os, subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
CLIPS = os.path.join(HERE, "clips")
LOG = json.load(open(os.path.join(CLIPS, "log.json"), encoding="utf-8"))
SCALE = 1920 / 2560  # 에뮬레이터 2560×1440 → 영상 1920×1080

# 앱 버튼 위치 (에뮬레이터 화면 좌표). 누른 곳에 동그라미를 띄운다.
TAPS = {
    "출발": (1932, 1260), "다음 장소 앞으로": (2082, 1260), "정차·충전": (2276, 1260),
    "이번 길": (1194, 124), "지금 이야기": (1081, 124), "설정": (1374, 124), "도감": (1286, 124),
    "아이와 함께": (1524, 273), "청소년과": (1244, 273), "다시 듣기": (1492, 1127), "그만": (1105, 1127),
}


def ev(scene, name):
    """장면 로그에서 이름이 name 으로 시작하는 첫 사건의 시각"""
    for t, e in LOG[scene]["events"]:
        if e.startswith(name):
            return t
    raise KeyError(f"{scene}: {name}")


def mp4(scene):
    src, dst = os.path.join(CLIPS, scene + ".webm"), os.path.join(CLIPS, scene + ".mp4")
    if not os.path.exists(dst) or os.path.getmtime(dst) < os.path.getmtime(src):
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-vf", "scale=1920:1080,fps=30", "-c:v", "libx264",
                        "-crf", "20", "-g", "30", "-pix_fmt", "yuv420p", "-an", dst], check=True)
    return f"clips/{scene}.mp4"


# (장면, 시작, 끝, 배속, 자막 번호). 자막 번호가 None 이면 앞 자막을 그대로 둔다.
def segments():
    a, r, k, s4, s5, s6, s7 = "a_depart", "s3_route", "c_kids", "s4_story", "s5_next", "s6_park", "s7_collection"
    return [
        (a, ev(a, "출발") - 4, ev(a, "출발") + 4, 1, 1),
        (a, ev(a, "출발") + 4, ev(a, "이야기 시작") - 2, 5, None),
        (a, ev(a, "이야기 시작") - 2, ev(a, "이야기 끝") + 1, 2.5, 2),
        (r, ev(r, "이번 길") - 1, ev(r, "지금 이야기"), 1.2, 3),
        (k, ev(k, "설정") - 1, ev(k, "지금 이야기") + 1, 1.3, 4),
        (s4, 1.0, ev(s4, "이야기 시작") + 3, 2, 5),
        (s4, ev(s4, "이야기 시작") + 3, ev(s4, "퀴즈 시작") - 1, 3, None),
        (s4, ev(s4, "퀴즈 시작") - 1, ev(s4, "정답") + 4, 1.3, 6),
        (s5, 1.0, ev(s5, "그만") + 4, 2.5, 7),
        (s6, ev(s6, "정차·충전") - 3, LOG[s6]["dur"] - 2, 1.5, 8),
        (s7, ev(s7, "도감") - 1, LOG[s7]["dur"] - 2, 1.2, 9),
    ]


CAPTIONS = {
    1: ("출발하면 지나갈 곳을 미리 계산합니다",
        "위치·속도·진행 방향으로 유적에 가장 가까워지는 시점과 좌우를 구합니다. 화면에는 ‘잠시 후 왼쪽 · 서울 선릉과 정릉’과 이야기 시작까지 남은 시간이 뜹니다.",
        "FusedLocation · 1초마다 위치"),
    2: ("지나기 전에 끝나도록 들려줍니다",
        "남은 시간에 맞춰 25초 또는 90초 분량을 고릅니다. 진행 막대가 이야기 길이와 통과까지 남은 시간을 함께 보여 줍니다. 운전자는 화면을 보지 않고 목소리로만 듣습니다.",
        "Gleo AI TextToSpeech"),
    3: ("이번 길에서 만날 곳",
        "서울에서 부산까지 지나갈 장소 75곳을 거리와 방향과 함께 미리 보여 줍니다. 목적지는 내비에서 받아 옵니다.",
        "NaviHelper"),
    4: ("듣는 사람을 고릅니다",
        "‘아이와 함께’를 고르면 아이 눈높이로 따로 쓴 원고만 재생하고, 이야기 끝에 퀴즈를 냅니다. 오른쪽에는 연결된 Pleos SDK가 보입니다.",
        "설정"),
    5: ("아이용 이야기 · 독산성 세마대",
        "‘흰쌀로 말을 씻긴 권율 장군’. 독산성 1분쯤 앞에서 아이용 원고를 시작해 성을 지나기 전에 마칩니다.",
        "아이와 함께"),
    6: ("이야기가 끝나면 퀴즈",
        "“권율 장군은 물 대신 무엇을 끼얹어서 말을 씻기는 척했을까요?” 아이가 말로 답하면 앱이 알아듣고 정답을 알려 줍니다. 시연에서는 4초 뒤 정답을 말한 것으로 처리합니다.",
        "Gleo AI SpeechToText"),
    7: ("다음 장소 · 천안삼거리",
        "운전 중 조작은 ‘그만’, ‘다시 듣기’, ‘들렀다 가기’ 버튼 세 개와 음성 명령이 전부입니다. ‘들렀다 가기’는 그 장소를 내비 경유지로 넣습니다.",
        "버튼 · 음성 명령"),
    8: ("차를 세우면 긴 이야기",
        "기어를 P에 두거나 충전구를 열면 근처 유적의 긴 해설을 글과 함께 들려줍니다. 서울 선릉 근처에서 세운 장면입니다. 긴 해설은 ‘청소년과’, ‘어른끼리’를 골랐을 때 나옵니다.",
        "Vehicle SDK · 기어 · 충전구"),
    9: ("우리 가족 이야기 도감",
        "지나며 들은 곳과 퀴즈 결과가 도감에 쌓입니다. 한 번 들은 편은 1년 동안 다시 나오지 않습니다.",
        "도감"),
}

INTRO, OUTRO = 7.0, 5.0


def build():
    segs = segments()
    t = INTRO
    videos, caps, taps = [], [], []
    cap_open = None  # (번호, 시작)
    for i, (scene, t0, t1, rate, cap) in enumerate(segs):
        dur = round((t1 - t0) / rate, 2)
        videos.append(f'<video id="v{i}" class="clip shot" src="{mp4(scene)}" muted playsinline '
                      f'data-start="{t:.2f}" data-duration="{dur}" data-media-start="{t0:.2f}" '
                      f'data-playback-rate="{rate}" data-track-index="1"></video>')
        for et, name in LOG[scene]["events"]:
            key = next((k for k in TAPS if name.startswith(k)), None)
            if key and t0 <= et - 0.2 < t1:
                x, y = TAPS[key]
                taps.append((round(t + (et - 0.2 - t0) / rate, 2), x * SCALE, y * SCALE))
        if cap is not None:
            if cap_open:
                caps.append((cap_open[0], cap_open[1], t))
            cap_open = (cap, t)
        t = round(t + dur, 2)
    caps.append((cap_open[0], cap_open[1], t))
    body_end = t
    total = round(body_end + OUTRO, 2)

    cap_html = []
    for n, (c, s, e) in enumerate(caps):
        title, desc, tag = CAPTIONS[c]
        cap_html.append(f'''<section id="c{n}" class="clip cap" data-start="{s:.2f}" data-duration="{e - s:.2f}" data-track-index="3">
        <p class="step">{c:02d} / {len(CAPTIONS):02d}</p>
        <h2>{html.escape(title)}</h2>
        <p class="desc">{html.escape(desc)}</p>
        <p class="tag">{html.escape(tag)}</p>
      </section>''')
    tap_html = [f'<div id="t{n}" class="clip tap" data-start="{s:.2f}" data-duration="0.9" data-track-index="4" '
                f'style="left:{x - 36:.0f}px;top:{y - 36:.0f}px"><i></i></div>' for n, (s, x, y) in enumerate(taps)]

    page = TEMPLATE.replace("__TOTAL__", str(total)).replace("__BODY__", f"{body_end - INTRO:.2f}") \
        .replace("__BODYSTART__", str(INTRO)).replace("__OUTRO__", f"{body_end:.2f}") \
        .replace("<!--VIDEOS-->", "\n      ".join(videos)).replace("<!--CAPS-->", "\n      ".join(cap_html)) \
        .replace("<!--TAPS-->", "\n      ".join(tap_html)) \
        .replace("/*CAPS*/[]", json.dumps([[round(s, 2), round(e - s, 2)] for _, s, e in caps])) \
        .replace("/*TAPS*/[]", json.dumps([s for s, _, _ in taps]))
    open(os.path.join(HERE, "index.html"), "w", encoding="utf-8").write(page)
    print(f"index.html: {total:.1f}초, 장면 {len(segs)}개, 자막 {len(caps)}개, 터치 표시 {len(taps)}개")


TEMPLATE = r'''<!doctype html>
<!-- build.py 가 만든다. 직접 고치지 않는다. -->
<html lang="ko">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1920, height=1080" />
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.15.0/dist/gsap.min.js"></script>
    <style>
      @font-face { font-family: "Pretendard Variable"; src: url("fonts/PretendardVariable.woff2") format("woff2"); font-weight: 45 920; }
      * { margin: 0; padding: 0; box-sizing: border-box; }
      html, body { width: 1920px; height: 1080px; overflow: hidden; background: #000; color: #f4f4f6;
        font-family: "Pretendard Variable", sans-serif; letter-spacing: -0.01em; word-break: keep-all; }
      #root { position: relative; width: 100%; height: 100%; }
      .card { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center;
        justify-content: center; gap: 26px; text-align: center; background: #000; }
      .card .eyebrow { font-size: 30px; color: #a9a9b0; font-weight: 500; }
      .card h1 { font-size: 140px; font-weight: 700; letter-spacing: -0.04em; line-height: 1.1; }
      .card .line { font-size: 40px; color: #d4d4d8; font-weight: 500; max-width: 1500px; line-height: 1.45; }
      .card .note { font-size: 28px; color: #9a9aa2; margin-top: 30px; }
      .shot { position: absolute; left: 0; top: 0; width: 1920px; height: 1080px; object-fit: contain; }
      /* 왼쪽 계기판 영역(차 그림) 위를 자막 판으로 덮는다. 앱 화면은 오른쪽에 그대로 보인다 */
      #panel { position: absolute; left: 0; top: 0; width: 636px; height: 1080px; background: #0c0c0e;
        border-right: 1px solid #26262a; }
      #panel .brand { position: absolute; left: 48px; top: 44px; font-size: 30px; font-weight: 700; color: #f4f4f6; }
      #panel .brand span { color: #9a9aa2; font-weight: 500; margin-left: 10px; }
      #panel .foot { position: absolute; left: 48px; right: 40px; bottom: 44px; font-size: 24px; line-height: 1.5; color: #9a9aa2; }
      .cap { position: absolute; left: 48px; top: 250px; width: 548px; }
      .cap .step { font-size: 28px; font-weight: 600; color: #3d8bff; letter-spacing: 0.04em; }
      .cap h2 { margin-top: 18px; font-size: 50px; line-height: 1.22; font-weight: 700; letter-spacing: -0.03em; }
      .cap .desc { margin-top: 30px; font-size: 28px; line-height: 1.6; color: #c8c8ce; font-weight: 450; }
      .cap .tag { display: inline-block; margin-top: 34px; padding: 10px 18px; border-radius: 999px;
        border: 1px solid #3a3a40; font-size: 28px; color: #b4b4ba; }
      .tap { position: absolute; width: 72px; height: 72px; }
      .tap i { position: absolute; inset: 0; border-radius: 50%; border: 5px solid #ffcc33;
        background: rgba(255, 204, 51, 0.25); box-shadow: 0 0 24px rgba(255, 204, 51, 0.6); }
    </style>
  </head>
  <body>
    <div id="root" data-composition-id="main" data-start="0" data-duration="__TOTAL__" data-width="1920" data-height="1080">
      <section id="intro" class="clip card" data-start="0" data-duration="__BODYSTART__" data-track-index="2">
        <p class="eyebrow">Pleos Connect 차량 앱</p>
        <h1>옛길 이야기</h1>
        <p class="line">역사 유적 옆을 지날 때, 그곳을 지나기 전에 끝나도록 그 장소의 이야기를 들려줍니다</p>
        <p class="note">Pleos Connect Emulator v2.1.0 실제 화면 · 주행은 앱의 시연 버튼으로 재현 · 노란 원은 누른 곳</p>
      </section>
      <!--VIDEOS-->
      <div id="panel" class="clip" data-start="__BODYSTART__" data-duration="__BODY__" data-track-index="2">
        <p class="brand">옛길 이야기<span>앱 시연</span></p>
        <p class="foot" data-layout-ignore>Pleos Connect Emulator v2.1.0 실제 화면<br />시연 주행: 서울 → 부산 경부고속도로, 시속 100km<br />기다리는 구간은 빠르게 돌렸습니다</p>
      </div>
      <!--CAPS-->
      <!--TAPS-->
      <section id="outro" class="clip card" data-start="__OUTRO__" data-duration="5" data-track-index="2">
        <h1>옛길 이야기</h1>
        <p class="line">지나는 길마다 역사가 말을 겁니다</p>
        <p class="note">Pleos Connect 차량 앱 · 현대자동차그룹 차량용 앱 개발 공모전</p>
      </section>
    </div>
    <script>
      const tl = gsap.timeline({ paused: true });
      const caps = /*CAPS*/[];
      caps.forEach(([s, d], i) => {
        const el = document.getElementById("c" + i);
        tl.fromTo(el.children, { opacity: 0, y: 18 }, { opacity: 1, y: 0, duration: 0.5, stagger: 0.12, ease: "power2.out" }, s + 0.05);
        tl.to(el.children, { opacity: 0, duration: 0.25, ease: "power1.in" }, s + d - 0.25);
      });
      const taps = /*TAPS*/[];
      taps.forEach((s, i) => {
        const dot = document.querySelector("#t" + i + " i");
        tl.fromTo(dot, { scale: 0.4, opacity: 1 }, { scale: 1.3, opacity: 0, duration: 0.85, ease: "power2.out" }, s);
      });
      // 첫 프레임이 썸네일이 되므로 제목은 처음부터 보이게 두고 설명만 이어 띄운다
      const intro = document.getElementById("intro").children;
      const introRest = Array.from(intro).slice(2);
      tl.fromTo(introRest, { opacity: 0, y: 16 }, { opacity: 1, y: 0, duration: 0.6, stagger: 0.25, ease: "power2.out" }, 0.4);
      tl.to(intro, { opacity: 0, duration: 0.4 }, __BODYSTART__ - 0.4);
      const outro = document.getElementById("outro").children;
      tl.fromTo(outro, { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.7, stagger: 0.3, ease: "power2.out" }, __OUTRO__ + 0.2);
      window.__timelines = window.__timelines || {};
      window.__timelines["main"] = tl;
      tl.seek(0);
    </script>
  </body>
</html>
'''

if __name__ == "__main__":
    build()
