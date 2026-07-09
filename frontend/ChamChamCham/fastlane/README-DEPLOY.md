# TestFlight 배포 파이프라인

`dev` 브랜치에 push → GitHub Actions(`.github/workflows/ios-testflight.yml`) → fastlane `beta`
레인이 빌드하고 TestFlight **외부 테스터 그룹**에 업로드한다. 프로세싱 완료까지 대기하며,
수출 규정 "약관 동의"는 `Info-Additions.plist`의 `ITSAppUsesNonExemptEncryption=false`로
자동 처리된다.

외부 테스터(외부 링크 포함) 그룹은 버전별 첫 빌드에 한해 Apple **Beta App Review** 승인이
필요하다 — `beta` 레인이 매 배포마다 심사 연락처와 함께 자동으로 제출(`submit_beta_review`)하므로
App Store Connect에서 수동으로 "Submit for Review"를 누를 필요가 없다. 심사는 보통 24~48시간
걸리며, 승인 후 그룹의 Public Link를 켜면 외부 링크 배포가 가능하다.

인증: **App Store Connect API Key(.p8)** / 서명: **fastlane match**.

---

## 1. 최초 1회 준비 (로컬, 딱 한 번)

### (a0) App Store Connect 앱 레코드 생성 + 외부 테스트 그룹

App Store Connect에 `me.GodsMove.ChamChamCham` 번들 ID로 **앱이 먼저 등록되어 있어야**
업로드와 빌드 번호 조회가 동작한다. (My Apps → `+` → New App) 아직 없다면 한 번 만들어 둔다.

**TestFlight → External Testing**에서 배포 대상 그룹(예: `test`)을 미리 만들어 둔다.
그룹 이름은 GitHub Secrets의 `TESTFLIGHT_GROUPS`와 동일해야 한다. 승인 후 이 그룹의
**Public Link**를 켜면 외부 링크로 배포할 수 있다.

### (a) App Store Connect API Key 발급

App Store Connect → **Users and Access → Integrations → App Store Connect API**에서
`Admin` 또는 `App Manager` 권한의 키를 만든다. 받은 값:

- **Key ID** → `ASC_KEY_ID`
- **Issuer ID** → `ASC_ISSUER_ID`
- 내려받은 **`AuthKey_XXXX.p8`** → base64로 인코딩해 `ASC_KEY_CONTENT`
  ```bash
  base64 -i AuthKey_XXXX.p8 | pbcopy
  ```

### (b) match 인증서 저장소 초기화

인증서 전용 **private git repo**를 하나 만든다(예: `<org>/certificates`). 그리고
로컬에서 한 번 실행해 배포 인증서/프로파일을 발급·암호화 저장한다:

```bash
cd frontend/ChamChamCham
export MATCH_GIT_URL="https://github.com/<org>/certificates.git"
export MATCH_PASSWORD="<저장소 암호>"          # 직접 정한 강한 비밀번호
bundle install
bundle exec fastlane match appstore
```

이때 정한 `MATCH_GIT_URL`, `MATCH_PASSWORD`를 그대로 GitHub Secrets에 등록한다.

---

## 2. GitHub Secrets 등록

리포지토리 **Settings → Secrets and variables → Actions**에 아래를 추가한다.

| Secret | 설명 |
| --- | --- |
| `ASC_KEY_ID` | ASC API Key ID |
| `ASC_ISSUER_ID` | ASC API Issuer ID |
| `ASC_KEY_CONTENT` | `.p8` 파일을 base64 인코딩한 값 |
| `MATCH_GIT_URL` | 인증서 private repo URL |
| `MATCH_PASSWORD` | match 저장소 암·복호화 비밀번호 |
| `MATCH_GIT_BASIC_AUTHORIZATION` | `base64("<github-user>:<PAT>")` — CI가 private repo를 읽기 위한 인증 |
| `KAKAO_NATIVE_APP_KEY` | 카카오 네이티브 앱 키 |
| `NAVER_CLIENT_ID` | 네이버 로그인 Client ID |
| `NAVER_CLIENT_SECRET` | 네이버 로그인 Client Secret |
| `JUSO_API_KEY` | 행안부 JUSO API 키 |
| `VWORLD_API_KEY` | V-World API 키 |
| `TESTFLIGHT_GROUPS` | 배포 대상 External Testing 그룹명. 콤마로 여러 개 가능 (예: `test` 또는 `test,QA`) |
| `TESTFLIGHT_FEEDBACK_EMAIL` | 테스터 피드백을 받을 이메일 (Beta App 정보) |
| `BETA_REVIEW_CONTACT_EMAIL` | Beta App Review 심사관 연락용 이메일 |
| `BETA_REVIEW_CONTACT_FIRST_NAME` | 심사 연락처 이름 |
| `BETA_REVIEW_CONTACT_LAST_NAME` | 심사 연락처 성 |
| `BETA_REVIEW_CONTACT_PHONE` | 심사 연락처 전화번호 (국가번호 포함, 예: `+821012345678`) |

> 로그인은 카카오/네이버/Apple만 지원하고 Apple 로그인으로 심사관이 바로 테스트할 수 있어
> `demo_account_required: false`로 고정했다. 만약 Apple 로그인 없이 카카오/네이버만 남기게
> 되면 데모 계정이 필요할 수 있으니 그때 `Fastfile`의 `beta_app_review_info`를 다시 검토한다.

`MATCH_GIT_BASIC_AUTHORIZATION` 만드는 법 (repo `read` 권한 PAT 필요):

```bash
echo -n "<github-user>:<PAT>" | base64 | pbcopy
```

> 앱 키(KAKAO/NAVER/JUSO/VWORLD)는 `Secrets.swift`가 `.gitignore` 대상이라 CI 체크아웃에
> 없기 때문에, `beta` 레인이 이 값들로 `Core/Config/Secrets.swift`를 다시 생성한다.

---

## 3. 동작

```
git push origin dev
```

- 빌드 번호는 TestFlight의 마지막 빌드 + 1로 자동 지정된다(중복 방지).
- 마케팅 버전(1.0)을 올리려면 Xcode의 `MARKETING_VERSION`을 수정한다.
- 수동 실행: Actions 탭 → **iOS TestFlight** → **Run workflow**.

## 로컬에서 직접 배포하고 싶을 때

위 환경변수를 셸에 export한 뒤:

```bash
cd frontend/ChamChamCham
bundle exec fastlane beta
```
