"""
한국관광공사 '관광지 오디오 가이드정보'(오디, Odii)에서 경로 근처 해설 대본을 모아 이야기 꾸러미로 만든다.

실행 (저장소 루트에서):
    python -X utf8 collector/scripts/odii_collect.py --route data/routes/gyeongbu-demo.json --out data/packs/gyeongbu-odii.json

- 서비스 키: local.properties 의 dataGoKr.serviceKey (또는 환경 변수 DATA_GO_KR_SERVICE_KEY). 출력·파일에 키를 남기지 않는다.
- 이용 조건: 공공데이터포털 '이용허락범위 제한 없음'. 원고는 원문 그대로 쓰고(말머리 [남자] 같은 표시만 뺀다) 출처를 단다.
- 같은 관광지(tid)의 해설은 한 장소의 여러 편으로 묶는다. 이미 다른 꾸러미에 있는 장소와 600m 안이면 그 장소에 편을 더한다.
- 대본 전체(150~300초)는 정차용, 앞 문장들로 주행 중 짧은 편(15~40초)·긴 편(60~120초)을 만든다.
- 모두 검수 전(reviewed=false), 청소년부터.
"""
import argparse, json, math, os, re, sys, time, urllib.parse, urllib.request

BASE = "https://apis.data.go.kr/B551011/Odii"
SOURCE_PAGE = "https://www.data.go.kr/data/15101971/openapi.do"
SHORT, LONG, PARKED = (15, 40), (60, 120), (150, 300)


def service_key(root):
    k = os.environ.get("DATA_GO_KR_SERVICE_KEY", "").strip()
    if k:
        return k
    path = os.path.join(root, "local.properties")
    if os.path.isfile(path):
        for line in open(path, encoding="utf-8"):
            if line.strip().startswith("dataGoKr.serviceKey="):
                v = line.split("=", 1)[1].strip()
                if v and v != "발급받은_일반_인증키":
                    return v
    sys.exit("공공데이터포털 서비스 키가 없습니다. local.properties 에 dataGoKr.serviceKey=... 를 넣어 주세요.")


def redact(s, key):
    return re.sub(r"(?i)(serviceKey=)[^&\s]*", r"\1***", s.replace(key, "***").replace(urllib.parse.quote(key, safe=""), "***"))


def get(key, op, **params):
    q = {"serviceKey": key, "MobileOS": "ETC", "MobileApp": "yetgil", "_type": "json", "langCode": "ko", **params}
    url = f"{BASE}/{op}?" + urllib.parse.urlencode(q)
    for attempt in range(3):
        time.sleep(0.3 * (attempt + 1))
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                data = json.loads(r.read().decode("utf-8"))
            body = data["response"]["body"]
            items = (body.get("items") or {}).get("item", []) if isinstance(body.get("items"), dict) else []
            return [items] if isinstance(items, dict) else items, int(body.get("totalCount") or 0)
        except Exception as e:  # noqa: BLE001
            err = redact(str(e), key)
    sys.exit(f"요청 실패 ({op}): {err}")


# ---- 기하 ----
def xy(lat, lon, lat0):
    return lon * 111320 * math.cos(math.radians(lat0)), lat * 110574


def dist_m(a, b):
    lat0 = (a[0] + b[0]) / 2
    ax, ay = xy(*a, lat0)
    bx, by = xy(*b, lat0)
    return math.hypot(ax - bx, ay - by)


def lateral_m(route, p):
    best = 1e18
    for a, b in zip(route, route[1:]):
        lat0 = (a[0] + b[0]) / 2
        A, B, P = xy(*a, lat0), xy(*b, lat0), xy(*p, lat0)
        dx, dy = B[0] - A[0], B[1] - A[1]
        t = max(0.0, min(1.0, ((P[0] - A[0]) * dx + (P[1] - A[1]) * dy) / (dx * dx + dy * dy)))
        best = min(best, math.hypot(P[0] - A[0] - t * dx, P[1] - A[1] - t * dy))
    return best


def sample_points(route, step_m):
    pts = [route[0]]
    carry = 0.0
    for a, b in zip(route, route[1:]):
        seg = dist_m(a, b)
        d = step_m - carry
        while d < seg:
            f = d / seg
            pts.append((a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f))
            d += step_m
        carry = seg - (d - step_m)
    pts.append(route[-1])
    return pts


# ---- 원고 ----
def syllables(t):
    return sum(1 for c in t if c.isalnum())


def sec(t):
    return math.ceil(syllables(t) / 5.0)


SPEAKER = re.compile(r"\[[^\]]{1,10}\]\s*")


def clean(script):
    t = SPEAKER.sub("", script)
    t = t.replace("“", "").replace("”", "").replace('"', "")
    return re.sub(r"\s+", " ", t).strip()


# 이 앱은 '역사 이야기'다. 관광 안내·여가 해설은 뺀다.
HISTORY = re.compile(r"조선|고려|신라|백제|가야|고구려|삼국|왕릉|왕비|임금|왕조|태조|세종|정조|영조|사찰|절터|스님|대사|율사|불상|석탑|산성|성곽|읍성|봉수|전투|임진왜란|병자호란|의병|장군|독립|항일|향교|서원|선비|유학|과거|생가|국보|보물|사적|문화유산|유적|고분|전설|설화|유래|옛날|역사")
EXCLUDE = re.compile(r"K-?POP|BTS|공원|카페|야경|밤밤|유원지|랜드|벽화|게스트하우스|맨발|체험|관광특구|축제|골목|힙한|매표소|스토리|자전거|오백리길|언덕|호수|에코파크|갤러리|창작촌|쇼핑|맛집|열린관광지|코스|산책|캠핑|휴양림")


# 사람이 확인해 고친 것 (tid 기준)
RENAME = {"998": "독립기념관"}
DROP = {"609"}  # 한강변 미술 작품 해설 (역사 이야기 아님)

MUSEUM = re.compile(r"전시실|전시관|전시되어|관람객 여러분|오디오 가이드를|번 유물")
MAX_PER_PLACE = 4


def history_score(script):
    return len(HISTORY.findall(script or ""))


def is_history(title, script):
    if EXCLUDE.search(title or "") or MUSEUM.search(script or ""):
        return False
    return len(HISTORY.findall(script or "")) >= 2


def is_dialogue(script):
    tags = set(re.findall(r"\[([^\]]{1,10})\]", script))
    return len(tags) >= 2 or any(re.search(r"친구|아이|엄마|아빠|딸|아들|학생", t) for t in tags)


def sentences(t):
    return [s.strip() for s in re.split(r"(?<=[.!?다요])\s+", t) if s.strip()]


def excerpt(sents, rng, target):
    out, best = [], None
    for s in sents:
        out.append(s)
        d = sec(" ".join(out))
        if rng[0] <= d <= rng[1]:
            best = " ".join(out)
            if d >= target:
                break
        if d > rng[1]:
            break
    return best


def script_obj(text):
    return {"text": text, "durationSec": sec(text)}


def common_name(titles):
    first = titles[0]
    if len(titles) == 1:
        return first
    p = os.path.commonprefix(titles).strip()
    p = re.sub(r"[\s(·,-]+$", "", p)
    return p if len(p) >= 4 else first


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--route", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--merge-into", nargs="*", default=["data/packs/gyeongbu-stories.json", "data/packs/gyeongbu-khs-draft.json"])
    ap.add_argument("--step-km", type=float, default=8)
    ap.add_argument("--radius-m", type=int, default=6000)
    ap.add_argument("--corridor-m", type=float, default=4000)
    ap.add_argument("--merge-m", type=float, default=600)
    a = ap.parse_args()
    root = os.getcwd()
    key = service_key(root)

    route = [(p["lat"], p["lon"]) for p in json.load(open(a.route, encoding="utf-8"))["points"]]
    stories = {}
    pts = sample_points(route, a.step_km * 1000)
    for i, (lat, lon) in enumerate(pts):
        page = 1
        while True:
            items, total = get(key, "storyLocationBasedList", mapX=lon, mapY=lat, radius=a.radius_m, numOfRows=100, pageNo=page)
            for it in items:
                stories.setdefault(it["stid"], it)
            if page * 100 >= total or not items:
                break
            page += 1
        print(f"  지점 {i + 1}/{len(pts)}: 누적 {len(stories)}편", flush=True)

    kept, skipped = [], {"경로에서 멂": 0, "역사 이야기 아님": 0, "대화극 형식": 0, "짧은 편을 만들 수 없음": 0}
    for it in stories.values():
        p = (float(it["mapY"]), float(it["mapX"]))
        if lateral_m(route, p) > a.corridor_m:
            skipped["경로에서 멂"] += 1
            continue
        raw = it.get("script") or ""
        if not is_history(it.get("audioTitle") or it.get("title"), raw) or EXCLUDE.search(it.get("title") or ""):
            skipped["역사 이야기 아님"] += 1
            continue
        if is_dialogue(raw):
            skipped["대화극 형식"] += 1
            continue
        text = clean(raw)
        sents = sentences(text)
        short = excerpt(sents, SHORT, 25)
        if not short:
            skipped["짧은 편을 만들 수 없음"] += 1
            continue
        long_ = excerpt(sents, LONG, 90)
        full = sec(text)
        parked = text if PARKED[0] <= full <= PARKED[1] else (excerpt(sents, PARKED, 240) if full > PARKED[1] else None)
        scripts = {"short": script_obj(short)}
        if long_ and long_ != short:
            scripts["long"] = script_obj(long_)
        if parked:
            scripts["parked"] = script_obj(parked)
        kept.append((it, p, scripts))

    # 다른 꾸러미의 기존 장소 (600m 안이면 거기에 더한다)
    others = {}
    for path in a.merge_into:
        others[path] = json.load(open(path, encoding="utf-8"))
    existing = [(path, pl) for path, pk in others.items() for pl in pk["places"]]

    by_tid = {}
    for it, p, scripts in kept:
        by_tid.setdefault(it["tid"], []).append((it, p, scripts))

    new_places, merged_count, merged_into = [], 0, set()
    for tid, group in by_tid.items():
        if tid in DROP:
            continue
        # 한 곳에 몰리지 않게 역사 관련도가 높은 편부터 MAX_PER_PLACE 개만
        group.sort(key=lambda g: -history_score(g[0].get("script")))
        group = sorted(group[:MAX_PER_PLACE], key=lambda g: g[0]["stid"])
        loc = group[0][1]
        target = None
        for path, pl in existing:
            if dist_m(loc, (pl["location"]["lat"], pl["location"]["lon"])) <= a.merge_m:
                target = (path, pl)
                break
        if target:
            path, pl = target
            pid = pl["id"]
            have = {e["id"] for e in pl["episodes"]}
        else:
            pid = f"odii-{tid}"
            pl = {"id": pid, "name": RENAME.get(tid) or common_name([g[0]["title"] for g in group]),
                  "location": {"lat": round(loc[0], 7), "lon": round(loc[1], 7)}, "kind": "other",
                  "triggerRadiusM": 3000, "episodes": []}
            have = set()
        for it, p, scripts in group:
            eid = f"{pid}-odii-{it['stid']}"
            if eid in have:
                continue
            ep = {
                "id": eid, "number": len(pl["episodes"]) + 1,
                "title": it.get("audioTitle") or it["title"], "category": "record", "minAudience": "teen",
                "reviewed": False, "scripts": scripts,
                "sources": [{"title": f"한국관광공사 관광지 오디오 가이드(오디): {it.get('audioTitle') or it['title']}",
                             "publisher": "한국관광공사", "url": SOURCE_PAGE, "license": "other", "modifiable": False}],
            }
            pl["episodes"].append(ep)
            if not pl.get("imageUrl") and it.get("imageUrl"):
                pl["imageUrl"], pl["imageCredit"] = it["imageUrl"], "한국관광공사"
            if target:
                merged_count += 1
        if target:
            merged_into.add(target[0])
        elif pl["episodes"]:
            new_places.append(pl)

    pack = {"$schema": "../../docs/schema/story-pack.schema.json", "schemaVersion": 1,
            "packId": os.path.splitext(os.path.basename(a.out))[0],
            "region": "경부고속도로 (한국관광공사 오디오 가이드)", "updatedAt": time.strftime("%Y-%m-%d"),
            "places": new_places, "series": []}
    json.dump(pack, open(a.out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    for path in merged_into:
        json.dump(others[path], open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    eps = sum(len(p["episodes"]) for p in new_places)
    parked = sum(1 for p in new_places for e in p["episodes"] if "parked" in e["scripts"])
    print(f"\n받은 이야기 {len(stories)}편 → 쓴 것 {len(kept)}편")
    print(f"  새 장소 {len(new_places)}곳 {eps}편 (정차용 {parked}편) → {a.out}")
    print(f"  기존 장소에 더한 편 {merged_count}편 → {', '.join(sorted(merged_into)) or '-'}")
    print(f"  뺀 것: {skipped}")


if __name__ == "__main__":
    main()
