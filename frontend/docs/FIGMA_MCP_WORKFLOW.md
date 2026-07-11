# Figma MCP 개발 플로우

> Codex에서 Figma 선택 노드를 읽고 SwiftUI 화면/컴포넌트에 반영하기 위한 작업 메모.
> 작성 기준일: 2026-07-08.

---

## 1. 목적

Figma Dev Mode의 공식 MCP 호출 한도나 Dev Mode seat 제한이 있을 때, 로컬 `Talk To Figma MCP Plugin`을 통해 **현재 선택한 Figma 노드만** Codex로 전달해 구현한다.

이 플로우는 특히 다음 작업에 쓴다.

- Figma 화면 프레임을 SwiftUI presentation 레이어에 반영
- 디자인 시스템 컴포넌트 variant/size 변경 확인
- 구현된 API/VM 흐름은 유지하고 레이아웃만 Figma 기준으로 보정
- iPhone 13 기준 디자인을 iPhone SE 2/3까지 깨지지 않게 재해석

---

## 2. 현재 로컬 구성

### Codex MCP 설정

Codex 설정 파일:

```text
~/.codex/config.toml
```

현재 사용하는 MCP 서버 설정:

```toml
[mcp_servers.TalkToFigma]
command = "/Users/user/.bun/bin/bun"
args = ["/Users/user/.codex/tools/cursor-talk-to-figma-mcp/dist/server.js"]
startup_timeout_sec = 120
```

플러그인 UI에 보이는 아래 JSON은 **Cursor용 안내**다. Codex에는 그대로 넣지 않는다.

```json
{
  "mcpServers": {
    "TalkToFigma": {
      "command": "bunx",
      "args": ["cursor-talk-to-figma-mcp@latest"]
    }
  }
}
```

### 로컬 도구 위치

```text
~/.codex/tools/cursor-talk-to-figma-mcp
```

중요한 파일:

```text
src/socket.ts
src/cursor_mcp_plugin/manifest.json
src/cursor_mcp_plugin/ui.html
```

현재 로컬 플러그인은 채널명을 `chamchamcham`으로 고정하도록 보정되어 있다. 새 장비에서 재구성할 때는 `ui.html`의 `generateChannelName()`이 같은 채널명을 반환하는지 확인한다.

---

## 3. 매번 작업할 때의 실행 순서

### 1. WebSocket 서버 실행

터미널에서:

```bash
cd /Users/user/.codex/tools/cursor-talk-to-figma-mcp
PATH=/Users/user/.bun/bin:$PATH /Users/user/.bun/bin/bun socket
```

정상 시작 로그:

```text
WebSocket server running on port 3055
```

### 2. Figma Desktop에서 플러그인 실행

Figma Desktop에서 작업할 파일을 열고:

```text
Plugins > Development > Cursor MCP Plugin
```

플러그인 창에서 다음 상태를 확인한다.

```text
Connected to server in channel:
chamchamcham
```

터미널에는 Figma 플러그인 쪽 join이 보인다.

```text
✓ Client joined channel "chamchamcham" (1 total clients)
```

이 숫자 1은 아직 Figma 플러그인만 들어온 상태일 수 있다. 이후 Codex가 같은 방에 조인하면 요청을 주고받을 수 있다.

### 3. Figma에서 대상 노드 선택

캔버스 또는 Layers 패널에서 구현할 프레임/컴포넌트를 직접 선택한다.

좋은 선택 단위:

- 화면 하나: iPhone 프레임 1개
- 컴포넌트 하나: variant set 또는 component node 1개
- 너무 큰 page/document 전체 선택은 피한다

### 4. Codex에 알리기

사용자가 Codex에 이렇게 말한다.

```text
선택했어
```

Codex는 다음 순서로 읽는다.

1. `TalkToFigma.join_channel(channel: "chamchamcham")`
2. `TalkToFigma.get_selection()`
3. 선택이 잡히면 `TalkToFigma.read_my_design()`

문서 전체 조회인 `get_document_info()`는 큰 Figma 파일에서 타임아웃이 잦으므로 기본적으로 쓰지 않는다.

---

## 4. 구현 절차

### 화면 구현

Figma 프레임을 읽은 뒤 아래 순서로 SwiftUI에 반영한다.

1. 기존 presentation 화면이 있는지 먼저 찾는다.
2. API/Repository/ViewModel이 이미 준비되어 있으면 그대로 유지한다.
3. Figma와 다른 부분은 `View` 레이어에서 우선 보정한다.
4. 반복될 UI만 `Core/DesignSystem/Components/`로 승격한다.
5. 한 화면에만 쓰이는 배치값은 화면 내부에 둔다.
6. iPhone SE 2/3에서 텍스트 잘림, 버튼 가림, 키보드 가림이 없는지 같이 본다.

예시: 커뮤니티 상세 화면은 Figma `iPhone 13 & 14 - 9` 프레임을 읽어 `CommunityDetailView`의 헤더, 작성자 행, 배지, 하단 댓글 입력바만 보정하고, 기존 `CommunityDetailViewModel`과 댓글 API 흐름은 유지했다.

### 디자인 시스템 변경

디자인 시스템 컴포넌트를 바꿀 때는 `docs/DESIGN_SYSTEM_HANDOFF.md`도 같이 갱신한다.

기본 원칙:

- 색상은 `Color+App.swift`의 semantic token 사용
- 폰트는 `.appTypography(_:)` 사용
- Figma 390pt 고정 폭은 SwiftUI에 그대로 박지 않는다
- 컴포넌트 크기, radius, icon tap target처럼 의미 있는 고정값만 유지한다
- 모든 상태/variant는 `#Preview`에 남긴다

---

## 5. 트러블슈팅

| 증상 | 의미 | 대응 |
|---|---|---|
| `No other clients in channel "chamchamcham"` | Codex 또는 Figma 플러그인 중 한쪽만 채널에 들어온 상태 | Figma 플러그인 연결 상태 확인 후 Codex에서 `join_channel` 재시도 |
| `selectionCount: 0` | 연결은 됐지만 Figma에서 선택된 노드가 없음 | 캔버스/Layers에서 프레임을 다시 클릭 |
| `Request to Figma timed out` | 요청은 보냈지만 플러그인 응답이 돌아오지 않음 | 플러그인 `Disconnect` 후 `Connect`, Codex `join_channel` 재시도 |
| 플러그인 창이 안 뜸 | Figma가 플러그인을 실행하지 못했거나 Dev/Design 모드 문제 | Figma Desktop에서 Design mode로 이동 후 `Plugins > Development > Cursor MCP Plugin` 실행 |
| `.codex` 폴더가 Finder에 안 보임 | macOS 숨김 폴더 | 파일 선택창에서 `Cmd + Shift + G`로 `~/.codex/...` 직접 이동 |
| `manifest editorType does not include "dev"` | manifest가 Dev Mode 실행을 허용하지 않음 | `manifest.json`의 `editorType`에 `"dev"` 포함 |
| 터미널에 `New client connected`만 많음 | WebSocket 연결은 됐지만 channel join 전일 수 있음 | 반드시 `✓ Client joined channel "chamchamcham"` 로그 확인 |
| `http://127.0.0.1:3845/mcp`가 보임 | Figma Desktop 공식 로컬 MCP endpoint | Talk-to-Figma의 `3055`와 별개. 공식 MCP 한도/seat 이슈가 있으면 이 플로우에서는 사용하지 않음 |

---

## 6. 다음 작업자 체크리스트

Figma 기반 구현을 시작하기 전:

- [ ] WebSocket 서버가 `3055`에서 실행 중인지 확인
- [ ] Figma 플러그인이 `chamchamcham` 채널에 connected 상태인지 확인
- [ ] Figma에서 프레임/컴포넌트 1개를 선택했는지 확인
- [ ] Codex에서 `get_selection()` 결과에 `selectionCount: 1`이 나오는지 확인
- [ ] 화면 구현 전 기존 ViewModel/Repository/API 준비 상태를 먼저 확인
- [ ] 구현 후 `xcodebuild` 빌드 확인

SwiftUI 화면에 반영할 때:

- [ ] API ready면 실제 VM/Repository를 연결
- [ ] API partially ready면 loading/empty/error/disabled 상태 포함
- [ ] API unavailable이면 네트워크를 새로 invent하지 않고 placeholder 처리
- [ ] SE 2/3 최소 사용성 규칙을 확인
- [ ] 반복 UI만 디자인 시스템으로 승격

