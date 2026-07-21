# 재배지 지도 — 상시 작도 진입점 + 대한민국 권역 제한

- 작성일: 2026-07-20
- 기준 브랜치: `dev`
- 관련 이전 리젝: `3617f65f` `fix(onboarding): 도로명 없는 재배지 주소 좌표변환 실패 수정 (Guideline 2.1)`
- 범위: `FarmLocationMapSection`/`FarmLocationViewModel`(온보딩·마이페이지 공유 컴포넌트)

## 이번에 반영한 것

1. 지도 우측상단에 **상시 작도 진입 버튼**을 추가했다. 필지 조회 성공/실패와 무관하게
   항상 노출되며, 탭하면 기존 필지를 대체하고 폴리곤 직접 그리기 모드로 들어간다.
   `beginDrawing()`이 이전 필지를 스냅샷해두고, `cancelDrawing()`에서 그대로 복원한다
   (확인 다이얼로그 없음 — 취소 시 원복되므로 데이터 손실 리스크가 낮다고 판단).
2. 작도 완료(`finishDrawing()`) 후 역지오코딩된 주소를 `drawnSummaryCard`에 노출해
   사용자가 육안으로 확인할 수 있게 했다. 역지오코딩이 실패해 주소가 비어 있으면
   안내 배너 + "주소 다시 확인"(`retryDrawnAddress()`) 버튼을 보여준다 — 이전에는
   실패해도 아무 표시 없이 `canProceed`만 조용히 `false`로 남아, 이전 App Store 리젝과
   같은 유형(막다른 상태)의 리스크가 있었다.
3. 지도 팬·탭을 대한민국을 넉넉히 덮는 사각 바운딩박스(위도 33.0~38.65, 경도
   124.5~131.95)로 제한했다. `MKMapView.cameraBoundary`가 SwiftUI `Map(position:)`에
   노출되지 않아, 기존 `.onMapCameraChange` 훅에서 범위를 벗어나면 경계 안으로 클램프
   (튕겨 들어오기)하는 방식을 썼다. 정확한 국경/EEZ 폴리곤이 아니라 근사 사각 경계다.
4. 사용자 작도(`onboardingUserDrawnPolygon`) 케이스의 `data_source_address`가 실제로는
   V-World reverse-geocode인데 `"JUSO"`로 하드코딩돼 있던 라벨링 버그를
   `"V_WORLD_REVERSE_GEOCODE"`로 수정했다(`FarmDTOs.swift`). 백엔드 `data_source_address`
   컬럼은 자유 문자열(nullable, length 64)이라 스키마 변경은 없다.

## A. 완전히 해결하지 못한 부분 (반드시 인지하고 넘어갈 것)

- **역지오코딩 정확도 근본 한계**: V-World의 `getAddress` 응답에는 거리·신뢰도 필드가
  없고, 코드도 이를 검증하지 않는다. VWorld가 내부적으로 "이 정도면 근처"로 판단해
  `status=OK`를 반환하면, 실제로는 수백 미터 떨어진 엉뚱한 주소로 라벨링될 수 있다.
  이번엔 주소를 화면에 노출해 사용자가 스스로 확인하게 하는 선에서 그쳤다 — 거리 기반
  자동 거부나, 사용자가 주소 텍스트를 직접 고치는 UI는 만들지 않았다. 필요하면 제품
  결정이 필요하다.
- **작도 후 주소 수정 경로 없음**: 작도가 끝난 뒤 주소가 잘못 채워졌다면 고칠 방법이
  없다. 기존 주소 검색(`selectAddress(_:)`, `FarmLocationViewModel.swift`)을 다시 쓰면
  `drawnCoordinates`/`isDrawingMode`가 초기화되어 애써 그린 폴리곤이 사라진다. "주소만
  고치고 작도는 유지"하는 흐름은 이번 범위에 없다 — 별도 아키텍처 변경이 필요하다.
- **대한민국 경계는 근사치**: 사각 바운딩박스이지 실제 국경/EEZ가 아니다. 접경·해상
  경계 부근 좌표가 의도와 다르게 포함/제외될 수 있다. 정밀한 국경 폴리곤(GeoJSON) 기반
  마스킹은 이번엔 만들지 않았다(과한 구현 비용 대비 효용 낮다고 판단).
- **`data_source_address` 백필 없음**: 이번 수정은 커밋 이후 신규 제출 건에만 적용된다.
  이미 저장된 기존 사용자 작도 농지 레코드는 여전히 `"JUSO"`로 잘못 라벨링된 채 남아
  있다. 백엔드에서 이 값에 의존하는 리포트/쿼리가 있다면 백필 여부를 별도 검토해야
  한다.

## B. 앱스토어 배포 전 심사위원 안내 / 세팅 (리젝 방지 체크리스트)

이 서비스는 대한민국 농지 등록·관리 전용이며, 지도가 대한민국 권역으로만 동작한다.
심사자가 해외에 있거나 위치를 한국 밖으로 시뮬레이션하면 지도 상호작용이 제한적으로
보일 수 있으므로, 다음을 App Store Connect 제출 전 반드시 확인한다.

- [ ] **App Review Information → Notes**에 아래 문구(또는 동등한 영문 안내)를 남긴다:
      "이 앱은 대한민국 농지 등록·관리 전용 서비스입니다. 온보딩의 지도 기능(주소 검색,
      필지 조회, 좌표변환)은 대한민국 영역으로 제한되어 있으며, 정상 동작 확인을 위해
      위치를 대한민국(예: 서울)으로 설정해 테스트해 주세요."
- [ ] 데모/테스트 계정에 실제 대한민국 주소로 등록된 농지를 최소 2건(도로명 있는 케이스,
      도로명 없는 산간/하천 인접 케이스) 준비해 심사 중 막히는 지점이 없게 한다.
- [ ] 온보딩 재배지 등록 흐름에서 네트워크·좌표변환 실패를 강제로 유발해도 "지도에
      직접 그리기"로 항상 끝까지 완료 가능한지 릴리스 직전 재확인한다(이전 Guideline 2.1
      리젝의 근본 원인이 바로 이 지점의 막다른 상태였다).
- [ ] 지도를 대한민국 밖으로 팬 했을 때 심사자에게 "고장난 것처럼" 보이지 않도록,
      클램프(튕겨 들어오기) 동작이 매끄러운지 실기기에서 확인한다.

## 관련 파일

- `frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/Views/FarmLocationMapSection.swift`
- `frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/ViewModels/FarmLocationViewModel.swift`
- `frontend/ChamChamCham/ChamChamCham/Core/Networking/DTOs/FarmDTOs.swift`
- `frontend/ChamChamCham/ChamChamChamTests/FarmLocationDrawingTests.swift`
