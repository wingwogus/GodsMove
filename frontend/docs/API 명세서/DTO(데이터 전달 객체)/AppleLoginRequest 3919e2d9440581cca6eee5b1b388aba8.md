# AppleLoginRequest

API 분류: API Request
태그: [인증] 계정

## Body

```json
{
  "identityToken": "apple-identity-token",
  "nonce": "client-generated-raw-nonce",
  "authorizationCode": "apple-authorization-code",
  "userIdentifier": "apple-user-identifier"
}
```

## Field

- `identityToken`: string, required. Apple identity token.
- `nonce`: string, required. 앱에서 생성한 raw nonce.
- `authorizationCode`: string, optional.
- `userIdentifier`: string, optional. 전달되면 identity token subject와 같은지 검증한다.

## Parameter

None

## Header

None

## Query

None