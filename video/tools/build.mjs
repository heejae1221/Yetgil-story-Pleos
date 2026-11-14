// docs/design 의 화면 시안(.dc.html)을 정적 HTML 장면으로 바꿔 src/template.html 에 끼워 index.html 을 만든다.
// 시안을 고친 뒤 `npm run build` 로 다시 만든다. 원본 시안은 건드리지 않는다.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");
const designDir = join(root, "..", "docs", "design");
const ACCENT = "#3D8BFF";

// 영상 순서: 기획서 Q7 시연 흐름 (경로 미리보기 → 주행 → 퀴즈 → 정차 → 도감 → 설정)
const scenes = [
  { kind: "title", duration: 6 },
  {
    kind: "text",
    duration: 8,
    lines: ["교과서에서 본 이름이 표지판에 스쳐 갑니다.", "운전 중엔 찾아볼 수 없고,", "검색할 즈음이면 이미 지나쳐 있죠."],
  },
  {
    file: "RoutePreview.dc.html",
    duration: 16,
    caption: "출발 전 — 목적지를 넣으면 이번 길에서 만날 이야기를 미리 보여 줘요.",
  },
  {
    file: "Main.dc.html",
    duration: 20,
    caption: "주행 중 — 장소를 지나기 전에 끝나도록 길이를 골라 들려줘요. 조작은 목소리로만.",
  },
  {
    file: "NavWidget.dc.html",
    duration: 12,
    caption: "내비를 보는 중엔 지도 위 작은 카드로. 이야기가 없으면 카드가 사라져요.",
  },
  {
    file: "Quiz.dc.html",
    duration: 10,
    vars: { revealed: false, hidden: true },
    caption: "이야기가 끝나면 가족 퀴즈 — 아이들이 말로 답해요.",
  },
  {
    file: "Quiz.dc.html",
    duration: 8,
    vars: { revealed: true, hidden: false },
    caption: "정답 공개. 운전하는 사람은 대답하지 않아도 돼요.",
  },
  {
    file: "Parked.dc.html",
    duration: 16,
    caption: "충전·주차 중엔 긴 이야기와 사진. 마음에 들면 말로 경유지에 넣어요.",
  },
  {
    file: "Collection.dc.html",
    duration: 16,
    caption: "들은 이야기는 도감에 쌓여요. 다음 명절엔 같은 곳에서 다음 편을 들어요.",
  },
  {
    file: "Settings.dc.html",
    duration: 12,
    caption: "누구와 타는지, 얼마나 자주 들을지 고르면 그에 맞는 이야기만 나와요.",
  },
  { kind: "outro", duration: 8 },
];

const canvas = JSON.parse(readFileSync(join(designDir, "canvas.json"), "utf8"));

// Collection.dc.html 의 renderVals() 를 옮긴 것 (sc-for 목록 데이터)
function collectionLists() {
  const heard = ["덕산온천", "수덕사", "독산성 세마대", "천안삼거리", "대관령"];
  const names = ["덕산온천", "수덕사", "독산성 세마대", "안성", "천안삼거리", "문경새재", "추풍령", "통도사", "낙화암", "치악산", "도담삼봉",
    "정이품송", "진천 농다리", "익산 미륵사지", "경포대", "화석정", "탄금대", "직지사", "무령왕릉", "금정산 금샘", "대관령", "서산 마애삼존불"];
  const stamps = names.map((n) => ({ name: n, cls: heard.includes(n) ? "ui-stamp is-heard" : "ui-stamp" }));
  const rows = [
    { name: "율곡 이이를 따라서", got: 2, total: 3, places: "덕산온천, 화석정, 대관령" },
    { name: "옛길과 과거 시험", got: 2, total: 4, places: "천안삼거리, 문경새재, 추풍령, 대관령" },
    { name: "임진왜란", got: 1, total: 2, places: "독산성 세마대, 탄금대" },
    { name: "백제 이야기", got: 0, total: 4, places: "낙화암, 미륵사지, 무령왕릉, 서산 마애삼존불" },
  ];
  const series = rows.map((r) => ({
    name: r.name,
    count: r.got + " / " + r.total,
    places: r.places,
    bar: "width: " + Math.round((r.got / r.total) * 100) + "%",
  }));
  return { stamps, series };
}

function renderDesign(file, vars = {}) {
  const src = readFileSync(join(designDir, file), "utf8");
  const m = src.match(/<x-dc>([\s\S]*?)<\/x-dc>/);
  if (!m) throw new Error(`${file}: <x-dc> 없음`);
  let html = m[1].replace(/<helmet>[\s\S]*?<\/helmet>/, "");
  const values = { accent: ACCENT, ...vars, ...(file === "Collection.dc.html" ? collectionLists() : {}) };

  html = html.replace(/<sc-if value="\{\{\s*(\w+)\s*\}\}"[^>]*>([\s\S]*?)<\/sc-if>/g, (_, name, body) => {
    if (!(name in values)) throw new Error(`${file}: sc-if 값 없음: ${name}`);
    return values[name] ? body : "";
  });
  html = html.replace(/<sc-for list="\{\{\s*(\w+)\s*\}\}" as="(\w+)"[^>]*>([\s\S]*?)<\/sc-for>/g, (_, list, as, body) => {
    const items = values[list];
    if (!Array.isArray(items)) throw new Error(`${file}: sc-for 목록 없음: ${list}`);
    return items
      .map((item) => body.replace(new RegExp(`\\{\\{\\s*${as}\\.(\\w+)\\s*\\}\\}`, "g"), (_, key) => item[key]))
      .join("");
  });
  html = html.replace(/\{\{\s*(\w+)\s*\}\}/g, (_, name) => {
    if (!(name in values)) throw new Error(`${file}: 값 없음: ${name}`);
    return values[name];
  });
  if (/\{\{|<sc-|<dc-/.test(html)) throw new Error(`${file}: 처리하지 못한 템플릿 문법이 남았습니다`);
  return html.trim();
}

let t = 0;
const parts = scenes.map((s, i) => {
  const id = `s${i}`;
  const timing = `data-start="${t}" data-duration="${s.duration}"`;
  t += s.duration;
  if (s.kind === "title") {
    return `<section id="${id}" class="clip scene scene-card" ${timing}>
      <p class="eyebrow">Pleos Connect 앱 시연 영상 초안</p>
      <h1 class="display">옛길 이야기</h1>
      <p class="tagline">같은 길, 다른 여정</p>
    </section>`;
  }
  if (s.kind === "text") {
    return `<section id="${id}" class="clip scene scene-card" ${timing}>
      ${s.lines.map((l) => `<p class="statement">${l}</p>`).join("\n      ")}
    </section>`;
  }
  if (s.kind === "outro") {
    return `<section id="${id}" class="clip scene scene-card" ${timing}>
      <h1 class="display">옛길 이야기</h1>
      <p class="tagline">지나는 길마다 역사가 말을 겁니다</p>
      <p class="note">[초안 · 화면 시안으로 만든 영상입니다. 실제 에뮬레이터 화면으로 바꿀 예정]</p>
    </section>`;
  }
  const label = canvas.boards[s.file]?.title ?? s.file;
  return `<section id="${id}" class="clip scene" ${timing}>
      <p class="label">${label}${s.vars?.revealed ? " · 정답 공개" : ""}</p>
      <div class="frame"><div class="screen" data-layout-ignore>${renderDesign(s.file, s.vars)}</div></div>
      <p class="caption">${s.caption}</p>
    </section>`;
});

const sceneTimes = [];
let acc = 0;
for (const s of scenes) {
  sceneTimes.push([acc, s.duration]);
  acc += s.duration;
}

// 시안의 <helmet> 은 버리므로 공통 토큰(docs/design/tokens.css)은 영상 틀에 한 번 넣는다
const tokens = readFileSync(join(designDir, "tokens.css"), "utf8");
const template = readFileSync(join(root, "src", "template.html"), "utf8");
const out = template
  .replaceAll("__TOTAL__", String(t))
  .replace("/*UI_TOKENS*/", () => tokens)
  .replace("<!--SCENES-->", parts.join("\n\n    "))
  .replace("/*SCENE_TIMES*/[]", JSON.stringify(sceneTimes));
writeFileSync(join(root, "index.html"), out);
console.log(`index.html: 장면 ${scenes.length}개, ${t}초`);
