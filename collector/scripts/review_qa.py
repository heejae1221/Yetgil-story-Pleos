"""
검수 보조: 차 안에서 들려주기에 맞지 않는 원고를 찾는다 (사실 확인은 출처 대조로 따로 한다).

python -X utf8 collector/scripts/review_qa.py            # 보고만
python -X utf8 collector/scripts/review_qa.py --apply    # 고쳐서 저장

검사 항목 (주행 중 원고 short/long 기준):
- 현장 지시어: 직접 서서 보는 사람에게 하는 말 ("앞에 보이는", "걸어가시면", "여러분") → 주행 중에는 맞지 않음
- 상업·안내 정보: 입장료, 전화, 운영 시간, 주차장 → 이야기와 무관
- 문장이 끝나지 않고 잘린 원고
--apply: short 에 문제가 있으면 문제없는 문장들로 다시 자르고, 안 되면 그 편을 뺀다. long 에 문제가 있으면 long 만 뺀다.
          정차용(parked)은 근처에 세워 둔 상황이라 그대로 둔다.
"""
import argparse, glob, json, math, re

DEIXIS = re.compile(r"보이는|보이시|보시면|보세요|보실|보입니다|보이죠|보이네|앞에 있는|앞쪽|뒤쪽|이쪽|저쪽|오른쪽에|왼쪽에|오른편|왼편|"
                    r"서 계신|서 있는 곳|여기서|이곳에서 보|발걸음|걸어|걷다|들어가시|올라가시|내려가시|안내해 드|안녕하세요|여러분|"
                    r"오디오 가이드|관람|둘러보")
COMMERCE = re.compile(r"입장료|관람료|요금|전화|운영 ?시간|휴관|주차장|예약|매표|원입니다|원이며")
END = re.compile(r"[.!?다요죠]\"?$")
SHORT, LONG = (15, 40), (60, 120)


def sec(t):
    return math.ceil(sum(1 for c in t if c.isalnum()) / 5.0)


def sentences(t):
    return [s.strip() for s in re.split(r"(?<=[.!?다요])\s+", t) if s.strip()]


def problems(text):
    p = []
    if m := DEIXIS.search(text):
        p.append(f"현장 지시어 '{m.group(0)}'")
    if m := COMMERCE.search(text):
        p.append(f"안내 정보 '{m.group(0)}'")
    if not END.search(text.strip()):
        p.append("문장이 끝나지 않음")
    return p


def re_excerpt(full, rng, target):
    """문제없는 문장만 이어서 다시 자른다 (앞에서부터, 문제 문장은 건너뛴다)."""
    out, best = [], None
    for s in sentences(full):
        if problems(s + ("" if END.search(s) else ".")) and (DEIXIS.search(s) or COMMERCE.search(s)):
            continue
        out.append(s)
        d = sec(" ".join(out))
        if rng[0] <= d <= rng[1]:
            best = " ".join(out)
            if d >= target:
                break
        if d > rng[1]:
            break
    return best


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    a = ap.parse_args()
    stats = {"검사": 0, "문제 없음": 0, "short 다시 자름": 0, "long 뺌": 0, "편 뺌": 0}
    examples = []
    for path in sorted(glob.glob("data/packs/*.json")):
        pack = json.load(open(path, encoding="utf-8"))
        changed = False
        for pl in pack["places"]:
            keep = []
            for e in pl["episodes"]:
                if e.get("reviewed"):          # 이미 사람이 검수한 편(직접 쓴 원고)은 건너뛴다
                    keep.append(e)
                    continue
                stats["검사"] += 1
                sc = e["scripts"]
                ps, pl_ = problems(sc["short"]["text"]), problems(sc["long"]["text"]) if "long" in sc else []
                if not ps and not pl_:
                    stats["문제 없음"] += 1
                    keep.append(e)
                    continue
                if len(examples) < 12:
                    examples.append(f"{pl['name']} / {e['title']}: short {ps or '-'} long {pl_ or '-'}")
                if ps:
                    source = (sc.get("parked") or sc.get("long") or sc["short"])["text"]
                    new = re_excerpt(source, SHORT, 25)
                    if new and not problems(new):
                        sc["short"] = {"text": new, "durationSec": sec(new)}
                        stats["short 다시 자름"] += 1
                    else:
                        stats["편 뺌"] += 1
                        changed = True
                        continue
                if pl_ or ("long" in sc and problems(sc["long"]["text"])):
                    new = re_excerpt((sc.get("parked") or sc["long"])["text"], LONG, 90)
                    if new and not problems(new) and new != sc["short"]["text"]:
                        sc["long"] = {"text": new, "durationSec": sec(new)}
                    else:
                        del sc["long"]
                        stats["long 뺌"] += 1
                changed = True
                keep.append(e)
            for i, e in enumerate(keep, 1):
                e["number"] = i
            pl["episodes"] = keep
        if a.apply:
            pack["places"] = [p for p in pack["places"] if p["episodes"]]
            # 시리즈가 가리키는 장소가 사라지면 정리
            ids = {p["id"] for p in pack["places"]}
            for s in pack.get("series", []):
                s["placeIds"] = [x for x in s["placeIds"] if x in ids]
            if changed:
                json.dump(pack, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(stats)
    print("\n예시:")
    for x in examples:
        print(" -", x)


if __name__ == "__main__":
    main()
