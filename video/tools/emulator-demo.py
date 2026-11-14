# Pleos Connect 에뮬레이터 실제 화면 캡처(docs/design/preview/emulator-*.png)로 자막 넣은 시연 영상을 만든다.
# 실행: python video/tools/emulator-demo.py   → video/renders/yetgil-emulator-demo.mp4 (1920x1080, 무음)
# 필요: ffmpeg (PATH), 맑은 고딕 글꼴 (Windows)
import os, subprocess, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PREVIEW = os.path.join(ROOT, "docs", "design", "preview")
OUT_DIR = os.path.join(ROOT, "video", "renders")
FONT = "C\\:/Windows/Fonts/malgunbd.ttf"
FONT_REG = "C\\:/Windows/Fonts/malgun.ttf"

SLIDES = [
    ("emulator-1-upcoming.png", 6, "출발하면 지나갈 곳을 미리 알려 줘요", "잠시 후 왼쪽 · 서울 선릉과 정릉, 31초 뒤에 이야기를 시작해요"),
    ("emulator-2-driving.png", 8, "그곳을 지나기 전에 끝나도록 들려줘요", "천안삼거리 이야기 0:23, 통과까지 0:15 · Gleo AI TextToSpeech 로 낭독"),
    ("emulator-3-quiz.png", 7, "이야기가 끝나면 가족 퀴즈", "Gleo AI SpeechToText 로 아이의 답을 들어요 · 운전자는 대답하지 않아도 돼요"),
    ("emulator-4-route.png", 6, "이번 길에서 만날 곳을 미리 보여 줘요", "서울 → 부산 경부고속도로 · 63곳"),
    ("emulator-5-collection.png", 6, "들은 곳은 도감에 쌓여요", "지나간 곳 도장과 시리즈(임진왜란) 모으기"),
]


def card(path, seconds, title, sub, tmp):
    """검은 바탕 글자 카드"""
    tf, sf = os.path.join(tmp, "t.txt"), os.path.join(tmp, "s.txt")
    open(tf, "w", encoding="utf-8").write(title)
    open(sf, "w", encoding="utf-8").write(sub)
    vf = (f"drawtext=fontfile='{FONT}':textfile='{tf.replace(os.sep, '/').replace(':', chr(92) + ':')}':fontsize=96:fontcolor=white:x=(w-tw)/2:y=h/2-110,"
          f"drawtext=fontfile='{FONT_REG}':textfile='{sf.replace(os.sep, '/').replace(':', chr(92) + ':')}':fontsize=40:fontcolor=0xA9A9B0:x=(w-tw)/2:y=h/2+30")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", f"color=c=black:s=1920x1080:d={seconds}:r=30",
                    "-vf", vf, "-c:v", "libx264", "-pix_fmt", "yuv420p", path], check=True)


def slide(path, image, seconds, title, sub, tmp):
    """에뮬레이터 화면 + 아래 자막 띠"""
    tf, sf = os.path.join(tmp, "t.txt"), os.path.join(tmp, "s.txt")
    open(tf, "w", encoding="utf-8").write(title)
    open(sf, "w", encoding="utf-8").write(sub)
    esc = lambda p: p.replace(os.sep, "/").replace(":", "\\:")
    vf = ("scale=1920:1080,"
          "drawbox=x=0:y=ih-180:w=iw:h=180:color=black@0.95:t=fill,"
          f"drawtext=fontfile='{FONT}':textfile='{esc(tf)}':fontsize=48:fontcolor=white:x=60:y=h-150,"
          f"drawtext=fontfile='{FONT_REG}':textfile='{esc(sf)}':fontsize=32:fontcolor=0xC8C8CE:x=60:y=h-80,"
          f"drawtext=fontfile='{FONT_REG}':text='Pleos Connect Emulator v2.1.0 실제 화면':fontsize=24:fontcolor=0x9A9AA2:x=w-tw-40:y=h-60")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-loop", "1", "-t", str(seconds), "-i", image, "-r", "30",
                    "-vf", vf, "-c:v", "libx264", "-pix_fmt", "yuv420p", path], check=True)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        parts = []
        p = os.path.join(tmp, "00.mp4"); card(p, 4, "옛길 이야기", "Pleos Connect 앱 · 에뮬레이터 시연 (무음)", tmp); parts.append(p)
        for i, (img, sec, title, sub) in enumerate(SLIDES, 1):
            p = os.path.join(tmp, f"{i:02d}.mp4"); slide(p, os.path.join(PREVIEW, img), sec, title, sub, tmp); parts.append(p)
        p = os.path.join(tmp, "99.mp4"); card(p, 4, "지나는 길마다 역사가 말을 겁니다", "옛길 이야기", tmp); parts.append(p)
        lst = os.path.join(tmp, "list.txt")
        open(lst, "w", encoding="utf-8").write("".join(f"file '{x.replace(os.sep, '/')}'\n" for x in parts))
        out = os.path.join(OUT_DIR, "yetgil-emulator-demo.mp4")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", out], check=True)
        print(out)


if __name__ == "__main__":
    main()
