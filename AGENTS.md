# ChamChamCham Agent Guide

This root guide contains repository-wide instructions only. Keep stack-specific
details in each workspace folder.

## Repository Map

```text
ChamChamCham
├── backend   # Spring Boot Kotlin backend
├── frontend  # SwiftUI iOS app (ChamChamCham)
└── docs      # planning and project notes
```

Folder-specific instructions:

- Backend: [backend/AGENTS.md](backend/AGENTS.md)
- Frontend: [frontend/AGENTS.md](frontend/AGENTS.md)

## Common Rules

- Preserve the domain term `member`; do not reintroduce project-owned `user`
  naming such as `userId` or `users`.
- Keep secrets, tokens, private keys, and raw credentials out of committed
  files and logs.
- Do not revert user changes unless the user explicitly asks.
- Follow YAGNI: do not add code, dependencies, or abstractions before a concrete need exists.
- Prefer small, reviewable commits with focused scope.
- Run the relevant test/build command before claiming implementation work is
  complete.

## Commit Messages

Use Conventional Commits:

```text
type(scope): title
```

Examples:

```text
feat(auth): 카카오 로그인 API 추가
fix(auth): refreshToken 재발급 쿠키 처리 수정
docs(agents): 에이전트 가이드 분리
refactor(domain): 공통 시간 엔티티 적용
test(auth): 로그인 실패 케이스 테스트 추가
chore(gradle): 테스트 설정 정리
```

Common types:

- `feat`: new feature
- `fix`: bug fix
- `docs`: documentation
- `refactor`: behavior-preserving code change
- `test`: test addition or update
- `chore`: build, configuration, or miscellaneous work

## Branch Strategy

Use a simple branch strategy:

```text
main
dev
feat/feature-name
```

Development flow:

1. Create feature branches from `dev`.
2. Open a PR from the feature branch to `dev`.
3. Merge to `dev` after review.
4. Merge `dev` to `main` for release.
5. Add `(front)` for frontend branches when useful, for example
   `feat/map(front)`.

## Claude Code

Claude Code reads `CLAUDE.md`, not `AGENTS.md`. This repository keeps
`CLAUDE.md` files as small import shims that point Claude to the matching
`AGENTS.md` file.
