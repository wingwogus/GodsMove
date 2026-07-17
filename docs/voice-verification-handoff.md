# 음성 기록 검증·수정 작업 핸드오프

> 세션이 중단되어도 이 문서만으로 이어받을 수 있도록 각 커밋 직후 갱신한다.
> 전체 계획: `~/.claude/plans/curious-sniffing-harbor.md` (이 문서 하단에도 요약 있음)
> 브랜치: `feat/voice-session-guardrails` (dev에서 분기, PR은 dev 대상 Draft)

## 진행 상태

- [x] 1. `feat(voice): 대화 라운드·시간 제한 설정 추가 및 세션 응답 노출` (07583a8)
- [x] 2. `feat(voice): 수확 isLastHarvest·메모 자동 요약을 도구 스키마에 반영` (bf670db)
- [x] 3. `feat(voice): 음성 대화 지침 재설계(첫 안내·단위 명시·마무리 규칙)` (5770c0e)
- [x] 4. `feat(voice): 회원 작물 기반 농약 목록을 대화 지침에 주입` (2aafa14)
- [x] 5. `feat(voice): 턴 제출 개수 상한 검증 추가` (7a52968)
- [x] 6. `feat(voice): OpenAI 임시 세션 만료를 expires_after 360초로 단축` (d3ba4a3) — 실 curl로 expires_after·max_output_tokens 둘 다 지원 확인, 후자는 음성 잘림 위험으로 미적용
- [x] 7. `chore(seed): 회원 등록 작물과 농약-작물 적용 시드 추가` (bd1400e) — 시드에 member_crop이 아예 없던 문제도 함께 보완
- [x] 8. `chore(voice): 수동 E2E 검증용 웹 테스트 페이지 추가` (523b466)
- [x] 9. E2E 검증(자동화 가능 범위 전부 통과, 2026-07-17):
  - 세션 생성(실 OpenAI): expiresAt = 정확히 +360초, maxRounds=10/maxDurationSeconds=300 노출, 시드 농지·작물 목록 정상
  - submitTurns 부분 후보 → WAITING_CONFIRMATION + missingFields 정확 / 재호출 409 VOICE_002
  - confirm: memo 29자 400, 날씨 누락 400, harvest.isLastHarvest 누락 400, 정상 200(WATERING·HARVEST), DB entry_mode=VOICE 확인
  - 턴 상한: 41턴 400 / 40턴 200; 무토큰 401
  - 웹 페이지(5173, CORS 통과): 로그인 → 세션 생성 → (시뮬레이션 턴) 제출 → 리뷰 폼(날씨 자동 흐림/23, workedAt=now) → confirm 200
  - 농약 검색 API(keyword=코니도) → UUID 해석 정상
- [x] 10. **한도 확대 + recap 지시 + 종료 안내 강화** (사용자 피드백 반영, 2026-07-17):
  - `feat(voice): 대화 라운드·시간 한도를 20라운드/480초로 확대` (2c140b3) — 10R/5분은 필수 항목을 기억하며 답해야 하는 사용자에게 여유 부족. 라운드당 페이스(~24초) 유지하며 20R/480초(8분)로 확대.
  - `feat(voice): 진행 상황 중간 요약(recap) 안내와 종료 시 안내 문구 강화` (dba9773) — AI가 다음 질문 전 "지금까지 확인된 건/남은 건"을 매번 요약하도록 지시(VoiceSessionInstructions), 시간 임박 시 부족 항목은 이후 화면에서 채워야 한다고 명확히 안내.
  - `chore(voice): 한도 도달·세션 만료 시 눈에 띄는 종료 안내 배너 추가` (ba677ec) — 테스트 페이지에 하드컷/ICE 연결 끊김(토큰 만료) 각각 별도 배너로 원인 표시.
  - 재검증: `./gradlew test` 전체 그린. 프로세스가 오래(수 시간) 떠 있다 SIGKILL(137)로 죽어 confirm이 500(Whitelabel)을 반환하는 현상 발견 → 백엔드 재기동 후 세션 생성(maxRounds=20/maxDurationSeconds=480 확인)·submitTurns·confirm(200) 재검증 완료. 배너 로직(showBanner/hideBanner)도 브라우저에서 직접 호출해 클래스·텍스트 정상 확인.
  - **참고**: 로컬에서 bootRun을 장시간(수 시간) 방치하면 OS에 의해 SIGKILL될 수 있음 — 수동 검증 세션 직전에는 재기동 권장.

- [x] 11. **라운드 2 — 정합성 통합** (2026-07-17, 다른 세션의 iOS↔백엔드 분석을 코드로 재검증 후 통합. 사용자 최종 허가 후 커밋 완료(e0f3b11/da54130/abb5bbd). 상세 계획은 `~/.claude/plans/curious-sniffing-harbor.md` "라운드 2" 섹션):
  - **검증된 사실(iOS 소스 확인)**: iOS는 `maxRounds`/`maxDurationSeconds`/`expiresAt`를 아예 디코딩하지 않음(`VoiceSessionDTOs.swift`는 sessionId·clientSecret·model만) → 클라 타이머·소프트경고 주입 전무. 토큰 만료로 WebRTC가 `.closed`/`.failed`되면 `RecordVoiceComposeViewModel.failAndCleanUp`이 초안을 폐기하고 재시작만 가능(BR-VOICE-008), submitTurns로 살리는 경로 없음.
  - **발견한 신규 회귀 2건**(라운드 1 말미 480초 확대 때 생김): ① `maxDurationSeconds=480`(지침 "약 8분")이 토큰 만료 360초보다 길어 지침대로면 잘림 ② 턴 상한 40이 20라운드×2턴=40+인사턴=41로 빡빡해 정상 대화도 400 위험, 주석도 "max-rounds(10)"로 stale.
  - **결정**: `maxDurationSeconds=330`(5분30초), 토큰 만료는 **코드에서 `maxDurationSeconds+30초`로 파생**(=360, 기존과 동일값·비용 불변, 두 설정이 따로 놀며 어긋나는 것을 원천 차단). 턴 상한 40→50. 소프트경고 트리거 의존 제거 → AI 자가 페이싱으로 대체. **통합 종료 정책**(아래) 결정.
  - **B1**(시간 정합): `VoiceSessionProperties.maxDurationSeconds` 480→330. `RealtimeSessionRequest`에 `expiresAfterSeconds` 필드 추가, `VoiceSessionService.create`가 `maxDurationSeconds + EXPIRY_BUFFER_SECONDS(30)`로 계산해 전달. `OpenAiRealtimeProperties.expiresAfterSeconds`/`OpenAiRealtimeSessionProvider` 생성자 파라미터 제거(파생값 사용). 4개 yml의 `expires-after-seconds` 라인 제거 + `max-duration-seconds` 플레이스홀더 기본값도 330으로(⚠️ Kotlin data class 기본값만 바꾸고 yml 플레이스홀더 기본값을 안 바꿔서 480이 계속 나오는 실수를 한 번 했음 — yml이 우선 적용됨을 유의).
  - **B2**(턴 상한): `VoiceSessionRequests.SubmitTurnsRequest.turns` `@Size(max=40→50)`, 주석 정정.
  - **B3**(자가 페이싱): `VoiceSessionInstructions.kt`에서 "'시간이 얼마 남지 않았습니다' 시스템 안내를 받으면…" 문구(보내는 주체가 없는 죽은 의존) 제거 → "이 대화는 시스템이 도중에 끼어들 수 없으니 스스로 시간을 관리하세요" 자가 페이싱 문구로 교체.
  - **통합 종료 정책(iOS는 문서화만, .swift 미수정)**: 대화가 어떤 이유(자동 종료/완료 버튼/한도 도달/**토큰 만료·연결 끊김**)로 끝나든 **확보한 turns+candidate를 submitTurns로 살려 텍스트 기록 검토 화면으로 이어간다**. 유일 예외는 사용자 발화가 0건일 때만 실패·재시작. 백엔드는 이미 부분 제출·missingFields·검토화면 백필을 지원 → 백엔드 변경 불필요, iOS 쪽 구현 지침만 아래 "iOS 작업 지시"에 기록.
  - **재검증(실키, 2026-07-17)**: `./gradlew test` 전체 그린. 백엔드 재기동 후 실호출로 `maxDurationSeconds=330`, `expiresAt≈now+360`(330+30) 확인. submitTurns 51턴→400, 50턴→200 확인. 웹 페이지 카운터 20/330 표시 확인.
  - **독립 재검증(별도 세션, 2026-07-17)**: 이전 세션의 주장을 그대로 믿지 않고 diff·`./gradlew test`·실 OpenAI 호출로 다시 확인 — `maxDurationSeconds=330`, `expiresAt≈now+352~360초`, submitTurns 51턴→400("최대 50개"), 50턴→200(missingFields 정상) 재확인. iOS `VoiceSessionDTOs.swift`(미디코딩)·`RecordVoiceComposeViewModel.swift`(`.failed`/`.closed`→`failAndCleanUp`으로 초안 폐기, `endConversation` 미경유)도 직접 열어 위 iOS 갭 서술이 사실임을 재확인.

- [ ] 12. **사람 수동 검증(마이크 필요)**: 실제 음성 대화로 ① 첫 인사에 필수/선택/기본값 안내 ② 다음 질문 전 recap(확인된 것/남은 것) 요약 ③ 한 번에 하나씩 질문·재질문 없음 ④ memo를 묻지 않고 30~500자 생성 ⑤ PEST_CONTROL에서 부정확한 농약명 교정 제시 ⑥ HARVEST에서 isLastHarvest 예/아니오 1회 ⑦ AI가 스스로 페이싱해 20라운드/330초(5분30초) 근처에서 새 질문을 줄이고 마무리 ⑧ 20라운드/330초 하드컷 도달 시 화면 "⏱ 최대 라운드/시간 도달" 배너 → 부분 제출 → 리뷰 보완 → confirm ⑨ 6분(360초) 후 secret 만료로 ICE 연결 끊김 → "🔌 세션 만료" 배너 관찰

## iOS 작업 지시 (⚠️ 이 세션은 .swift 파일을 전혀 수정하지 않음 — 아래는 글로만 된 지시사항)

이 세션은 Xcode가 없어 iOS 빌드·테스트를 할 수 없다(사용자 확정 범위). iOS 반영은 별도로 진행하되, 아래 **통합 종료 정책**을 그대로 구현해달라:

- **단일 종료 규칙**: 대화가 ① AI 자동 종료 ② 사용자 '완료' 버튼 ③ (iOS에 타이머를 추가한다면) 시간/라운드 한도 도달 ④ **토큰 만료·WebRTC 연결 끊김(`.closed`/`.failed`)** 중 무엇으로 끝나든, 지금까지 확보한 대화(turns)와 후보(candidate)를 `PATCH /voice-sessions/{id}/turns`로 제출하고 텍스트 기록 검토 화면(RecordCompose 재사용)으로 이어간다. 사용자가 `missingFields`만 채우고 confirm.
  - **유일한 예외**: 사용자 발화가 **하나도 없는** 상태에서 끊긴 경우만 지금처럼 실패 처리·재시작.
- **현재 코드와의 차이**: `RecordVoiceComposeViewModel`의 `.failed`/`.closed` 핸들러(`failAndCleanUp` 호출부)가 지금은 무조건 초안을 폐기하는데, 대신 캡처된 turns가 있으면 `endConversation()`이 타는 것과 동일한 살리기 경로(submitTurns → reviewing)를 타도록 분기해야 한다.
- **참고(백엔드는 이미 지원)**: `SubmitTurnsRequest.candidate`는 전부 optional이라 부분 후보를 그대로 보낼 수 있고, `missingFields`가 계산돼 돌아온다. 단, `harvest`/`pestControl`처럼 `@NotNull` 하위 필드(growthPeriod/isLastHarvest/pesticideId 등)가 없는 detail 객체는 **통째로 생략**해야 400을 피한다(웹 테스트 페이지 `buildCandidate()`가 참고 구현).
- **한도 값 소비**: `POST /voice-sessions` 응답의 `maxRounds`(20)·`maxDurationSeconds`(330초)를 iOS DTO가 디코딩하도록 추가하면, 클라에서도 카운트다운/소프트 페이싱을 넣을 수 있다(현재는 미디코딩 — 필수는 아니고, 위 살리기 정책만으로도 초안 손실 문제는 해결됨).
- **부수 관찰(참고, 우선순위 낮음)**: raw OpenAI 에러 메시지가 사용자 말풍선에 그대로 노출됨, submitTurns 일시 네트워크 실패 시 재시도 없이 전량 실패 처리됨 — 개선 여지 있으나 이번 지시의 필수 사항은 아님.

**현재 진행 중**: 라운드 2(B1/B2/B3) 커밋 완료, PR 생성 단계. 사람 수동 검증(12번)은 여전히 대기.
**커밋 완료** (2026-07-17, 사용자 최종 허가 후 논리 단위별 분리 커밋):
  1. B1 시간 정합 (e0f3b11): `VoiceSessionProperties.kt`, `RealtimeSessionProvider.kt`, `VoiceSessionService.kt`, `OpenAiRealtimeProperties.kt`, `OpenAiRealtimeSessionProvider.kt`, yml 4개, `VoiceSessionServiceTest.kt`, `VoiceSessionControllerTest.kt`(maxDurationSeconds 리터럴), `OpenAiRealtimeSessionProviderTest.kt`
  2. B2 턴 상한 (da54130): `VoiceSessionRequests.kt`, `VoiceSessionControllerTest.kt`(51턴 테스트)
  3. B3 자가 페이싱 (abb5bbd): `VoiceSessionInstructions.kt`, `VoiceSessionInstructionsTest.kt`
  4. 이 문서(`docs/voice-verification-handoff.md`) 자체 갱신
  - (기존 untracked, 이 PR과 무관: backend/http/, backend/scripts/record-test-page/, backend/scripts/verify-voice-record.sql)

## 이어받기 절차

1. 이 문서 → 계획 파일 → `git log --oneline dev..feat/voice-session-guardrails` → `git status` 확인
2. 체크 안 된 첫 단계부터 재개. 각 단계는 `backend/`에서 `./gradlew test` 그린 확인 후 커밋(Conventional Commits, 한국어 제목, `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`).

## 핵심 배경 (탐색 완료 사실)

- 오디오는 클라 ↔ OpenAI Realtime WebRTC 직결. 백엔드는 `/api/v1/voice-sessions` create(client_secret 발급)/turns(1회 제출)/confirm(SaveRecordRequest 저장)/cancel만.
- confirm 필수인데 음성 미수집: 날씨·workedAt·pesticideId → **의도된 클라 보완**(리뷰 화면). 유지.
- 수정 대상: memo 30~500자 미안내, harvest `isLastHarvest` 스키마 누락, pesticideAmountUnit 프롬프트 누락, 농약명 교정 부재, 대화 한도 전무, 초기 안내 부재.
- 핵심 파일: `backend/application/.../voice/{VoiceSessionInstructions,FarmingRecordVoiceToolSchema,VoiceSessionService}.kt`, `backend/api/.../voice/dto/VoiceSessionRequests|Responses.kt`, `backend/api/.../voice/OpenAiRealtimeSessionProvider.kt`
- 농약 카탈로그: `PesticideApplication`(농약×병해충×cropName), 클라 검색 API `GET /api/v1/pesticides?keyword=`. 로컬 시드는 농약 2종·pesticide_application 0행.
- 부분후보 400 함정: CandidateRequest가 detail DTO를 재사용하므로 `@NotNull` 하위필드(growthPeriod/isLastHarvest/pesticideId) 없는 detail 객체는 통째로 생략해야 함.

## 실행 주의사항

- **bootRun은 backend/.env를 자동 로딩하지 않음** → `export OPENAI_API_KEY=...` (미설정 시 DDL create로 데이터 초기화 + VOICE_003 조용한 폴백 위험. `SPRING_JPA_HIBERNATE_DDL_AUTO`도 필요 시 export)
- 서버: `cd backend && ./gradlew :api:bootRun` / 테스트: `cd backend && ./gradlew test`
- 시드: `docker exec -i ccc-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f - < backend/scripts/local-dev-seed.sql` (앱 1회 기동 후 — crop 시드 필요)
- 테스트 페이지: `cd backend/scripts/voice-test-page && python3 -m http.server 5173` (CORS는 5173 기본 허용)
- 계정: `POST /api/v1/auth/login` (weather-qa@example.com / Test1234!). 신규 계정은 redis verified-email 트릭(`docker exec ccc-redis redis-cli SET "verified-email:<email>" "true" EX 3600`) → signup → onboarding.
- expires_after 검증 curl은 계획 파일 6단계 참조.
