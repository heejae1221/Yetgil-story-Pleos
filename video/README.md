# 시연 영상 초안 (hyperframes)

`docs/design` 의 화면 시안 7종을 이어 붙인 약 2분 12초(1920×1080, 30fps) 영상. 소리 없음.

- `src/template.html`: 영상 틀 (배경, 자막, 움직임). 고칠 곳은 여기.
- `tools/build.mjs`: 장면 순서·길이·자막, 시안 → 정적 HTML 변환. 고칠 곳은 여기. 시안의 `<helmet>` 은 버리고 `docs/design/tokens.css` 를 영상 틀에 한 번 넣는다.
- `index.html`: 위 둘로 만든 결과물. **직접 고치지 않는다** (`npm run build` 가 덮어씀).
- `renders/`: 렌더한 MP4 (git 제외).

## 준비

hyperframes CLI 0.9.3 은 `npm install` 로 이 폴더에 설치된다. 렌더용 Chrome 은 `npx hyperframes browser ensure` 로 `~/.cache/hyperframes/chrome` 에 받는다.

FFmpeg 는 시스템에 설치하지 않고 압축만 풀어 쓴다 ([gyan.dev essentials 빌드](https://github.com/GyanD/codexffmpeg/releases)). `C:\Users\<사용자>\tools\ffmpeg` 에 풀어 `bin\ffmpeg.exe` 가 그 아래 오게 한다.

`npm run check` / `snapshot` / `render` 는 [tools/hf.mjs](tools/hf.mjs) 를 거쳐 hyperframes 를 실행한다. 이 래퍼가 FFmpeg 경로를 넘기고 사용 통계와 스킬 자동 설치를 끈다. 따로 환경변수를 설정할 필요는 없다. FFmpeg 를 다른 곳에 두었다면 `bin` 폴더 경로를 `HYPERFRAMES_FFMPEG_DIR` 에 넣는다. 그 밖의 hyperframes 명령은 `npm run hf -- <명령>` 으로 실행한다 (예: `npm run hf -- doctor`).

## 만들기

```bash
npm run build     # 시안 → index.html
npm run check     # 검사 (오류 0개여야 함)
npm run render    # renders/yetgil-demo-draft.mp4
```

## 남은 일

- 실제 Pleos Connect 에뮬레이터 가상 주행 화면으로 장면 바꾸기 (기획서 Q7)
- 내레이션·배경음 (지금은 무음)
- 화면 시안을 고치면 `npm run build` 후 다시 렌더
