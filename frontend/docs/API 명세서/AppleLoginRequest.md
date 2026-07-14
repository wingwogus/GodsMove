<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# AppleLoginRequest

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "identityToken": "apple-identity-token",
  "nonce": "client-generated-raw-nonce",
  "authorizationCode": "apple-authorization-code",
  "userIdentifier": "apple-user-identifier"
}
```
### Field
- `identityToken`: string, required. Apple identity token.
- `nonce`: string, required. 앱에서 생성한 raw nonce.
- `authorizationCode`: string, optional.
- `userIdentifier`: string, optional. 전달되면 identity token subject와 같은지 검증한다.
### Parameter
None
### Header
None
### Query
None
