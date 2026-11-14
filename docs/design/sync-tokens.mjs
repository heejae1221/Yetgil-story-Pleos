// tokens.css 를 시안 7종(.dc.html)의 <helmet> 안 표시 구간에 복사한다.
// 시안은 캔버스에서 한 파일씩 열리므로 스타일을 파일 안에 품고 있어야 한다. 원본은 tokens.css 하나.
// 실행: node docs/design/sync-tokens.mjs
import { readFileSync, writeFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const dir = dirname(fileURLToPath(import.meta.url));
const tokens = readFileSync(join(dir, "tokens.css"), "utf8").trim();
const START = "/* ui-tokens:start · docs/design/tokens.css 에서 복사됨. 여기 말고 tokens.css 를 고친 뒤 sync-tokens.mjs 실행 */";
const END = "/* ui-tokens:end */";
const block = new RegExp(String.raw`/\* ui-tokens:start[^*]*\*/[\s\S]*?/\* ui-tokens:end \*/`);

let changed = 0;
for (const file of readdirSync(dir).filter((f) => f.endsWith(".dc.html"))) {
  const path = join(dir, file);
  const src = readFileSync(path, "utf8");
  if (!block.test(src)) throw new Error(`${file}: ui-tokens 표시 구간이 없습니다`);
  const out = src.replace(block, () => `${START}\n${tokens}\n${END}`);
  if (out !== src) {
    writeFileSync(path, out);
    changed++;
  }
}
console.log(`시안 ${changed}개에 토큰을 복사했습니다`);
