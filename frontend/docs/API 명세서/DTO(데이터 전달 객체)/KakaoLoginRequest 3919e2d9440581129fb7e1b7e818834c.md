# KakaoLoginRequest

API 분류: API Request
태그: [인증] 계정

## Body

```json
{
  "idToken": "kakao-oidc-id-token",
  "nonce": "client-generated-raw-nonce",
  "kakaoAccessToken": "kakao-access-token"
}
```

## Field

- `idToken`: string, required. Kakao OIDC ID token.
- `nonce`: string, required. 앱에서 생성한 raw nonce.
- `kakaoAccessToken`: string, optional. Kakao OIDC userinfo 조회에 사용한다. 값이 없으면 로그인 검증은 가능하지만 이름, 전화번호, 생년월일 prefill은 제한된다.

## Parameter

None

## Header

None

## Query

None