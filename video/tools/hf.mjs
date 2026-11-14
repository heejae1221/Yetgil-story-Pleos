// hyperframes CLI 를 이 프로젝트 설정으로 실행한다: FFmpeg 경로, 사용 통계 끄기, 스킬 자동 설치 끄기.
// FFmpeg 는 기본으로 ~/tools/ffmpeg/bin 에서 찾는다. 다른 곳에 두었으면 HYPERFRAMES_FFMPEG_DIR 로 알려 준다.
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { createRequire } from "node:module";
import { homedir } from "node:os";
import { dirname, join } from "node:path";

const require = createRequire(import.meta.url);
const ext = process.platform === "win32" ? ".exe" : "";
const ffmpegDir = process.env.HYPERFRAMES_FFMPEG_DIR ?? join(homedir(), "tools", "ffmpeg", "bin");
const ffmpeg = join(ffmpegDir, `ffmpeg${ext}`);
const ffprobe = join(ffmpegDir, `ffprobe${ext}`);

if (!existsSync(ffmpeg) || !existsSync(ffprobe)) {
  console.error(`FFmpeg 를 찾지 못했습니다: ${ffmpegDir}`);
  console.error("FFmpeg 를 그 폴더에 풀거나 HYPERFRAMES_FFMPEG_DIR 에 bin 폴더 경로를 넣어 주세요.");
  process.exit(1);
}

const pkgDir = dirname(require.resolve("hyperframes/package.json"));
const cli = join(pkgDir, require("hyperframes/package.json").bin.hyperframes);

const result = spawnSync(process.execPath, [cli, ...process.argv.slice(2)], {
  stdio: "inherit",
  env: {
    ...process.env,
    HYPERFRAMES_FFMPEG_PATH: ffmpeg,
    HYPERFRAMES_FFPROBE_PATH: ffprobe,
    HYPERFRAMES_NO_TELEMETRY: "1",
    HYPERFRAMES_SKIP_SKILLS: "1",
  },
});
process.exit(result.status ?? 1);
