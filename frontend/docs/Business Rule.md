# Business Rule

<aside>
💡

농업의 인터넷 환경을 고려하여, 클라이언트에서는 대부분에 **오프라인 저장(임시저장)이 기본 구현**

</aside>

<aside>
💡

# Business Rules

> Version: 1.0 (MVP)
> 
> 
> 본 문서는 요구사항 명세서와 ERD 사이의 비즈니스 규칙을 정의한다.
> 
> - 요구사항: 무엇을 할 수 있는가
> - ERD: 데이터를 어떻게 저장하는가
> - **Business Rule: 언제, 어떤 조건에서 동작하는가**
</aside>

- User Rules
    
    # 1. User Rules
    
    ## BR-USER-001. 회원가입 완료 조건
    
    ### Trigger
    
    사용자가 회원가입을 진행한다.
    
    ### Preconditions
    
    - 인증(소셜 로그인 또는 이메일 인증)이 완료되어야 한다.
    
    ### Rules
    
    1. 회원가입만 완료된 상태는 서비스를 사용할 수 없다.
    2. 온보딩이 완료되어야 정상 회원으로 간주한다.
    3. 온보딩 완료 전에는 Home, 영농기록, 커뮤니티 접근을 제한한다.
    
    ### Result
    
    회원가입 완료 후 온보딩 화면으로 이동한다.
    
    ---
    
    ## BR-USER-002. 온보딩 완료
    
    ### Trigger
    
    사용자가 온보딩 정보를 모두 입력한다.
    
    ### Preconditions
    
    필수 입력값
    
    - 이름
    - 연락처
    - 생년월일
    - 닉네임
    - 영농 경력(년차)
    - 농업경영체 일반 / 농업경영체 법인 / 농업경영체 미가입자 이렇게 셋 중 하나
    - 농장 이름 및 주소
    - 재배 작물(1개 이상)
    
    ### Rules
    
    온보딩 완료 시 다음 데이터를 생성한다.
    
    - member
    - farms
    - member_crop
    
    기본 농장은 최초 1개 생성한다.
    
    ### Result
    
    Home 화면으로 이동한다.
    
    ---
    
    ## BR-USER-003. 사용자 정보 수정
    
    ### Trigger
    
    사용자가 마이페이지에서 정보를 수정한다.
    
    ### Rules
    
    수정 가능한 항목
    
    - 이름
    - 닉네임
    - 연락처
    - 영농 경력(년차)
    - 농업경영체 유형
    - 주요 작물
    - 농장 정보
    
    수정 후
    
    - 정책 추천 기준 갱신
    - Home 추천 정보 갱신
    
    ### Exception
    
    기존 영농일지는 수정하지 않는다.
    
    ---
    
    ## BR-USER-004. 회원탈퇴
    
    ### Trigger
    
    사용자가 회원탈퇴를 요청한다.
    
    ### Rules
    
    회원 데이터는 즉시 물리 삭제하지 않는다.
    
    member.status
    
    ```
    WITHDRAWN
    ```
    
    로 변경한다.
    
    withdrawn_at을 기록한다.
    
    ### Result
    
    - 로그인 불가
    - 모든 개인 기능 접근 불가
    
    ### Related Data
    
    영농기록
    
    - 삭제하지 않는다.
    
    커뮤니티 게시글
    
    - 작성자는 익명 처리한다.
    
    댓글
    
    - 익명 처리한다.
    
    ---
    
    ## BR-USER-005. 권한
    
    사용자는
    
    - 자신의 영농기록만 수정 가능
    - 자신의 농장만 수정 가능
    - 자신의 프로필만 수정 가능
    
- Farming Record Rules
    
    # 2. Farming Record Rules
    
    ---
    
    ## BR-RECORD-001. 영농일지 생성
    
    ### Trigger
    
    사용자가 영농일지 작성을 시작한다.
    
    ### Rules
    
    영농일지는
    
    - TEXT 입력
    - VOICE 입력
    
    두 방식만 지원한다.
    
    생성 방식은
    
    entry_mode
    
    에 저장한다.
    
    기록 과정에서 Voice ↔ Text 로 입력방식 모드 변환은 불가하다. 입력 방식 변경을 원한다면 반영된 데이터는 초기화하고 진행한다.
    
    ---
    
    ## BR-RECORD-002. 자동 입력
    
    ### Trigger
    
    새 영농일지를 생성한다.
    
    ### Rules
    
    시스템은 다음 데이터를 자동 입력한다.
    
    - 날짜
    - 시간
    
    가능하면 자동 추천한다.
    
    - 농지
    - 날씨
    
    자동 추천 실패 시
    
    사용자가 직접 선택한다.
    
    ---
    
    ## BR-RECORD-003. 저장 조건
    
    ### Trigger
    
    사용자가 저장 버튼을 누른다.
    
    ### Required Fields
    
    반드시 존재해야 하는 데이터
    
    - worked_at
    - farm
    - crop
    - work_type
    
    ### Rules
    
    필수값이 누락되면 저장하지 않는다.
    
    누락 항목을 사용자에게 표시한다.
    
    ---
    
    ## BR-RECORD-004. 작업별 입력
    
    ### Trigger
    
    사용자가 작업 종류를 선택한다.
    
    ### Rules
    
    선택한 work_type에 따라
    
    입력 필드를 동적으로 생성한다.
    
    예)
    
    수확
    
    ↓
    
    수확량
    
    ↓
    
    건조방법
    
    ↓
    
    약용부위
    
    물주기
    
    ↓
    
    관수량
    
    ↓
    
    관수방법
    
    ---
    
    ## BR-RECORD-005. AI 구조화
    
    ### Trigger
    
    사용자가 텍스트 또는 음성을 입력한다.
    
    ### Rules
    
    AI는
    
    - 작물
    - 작업 종류
    - 비료명
    - 농약명
    - 사용량
    
    후보를 추출한다.
    
    확정값으로 저장하지 않는다.
    
    사용자 확인 후 저장한다.
    
    ---
    
    ## BR-RECORD-006. 영농일지 수정
    
    ### Trigger
    
    사용자가 기존 영농일지를 수정한다.
    
    ### Rules
    
    영농일지는 언제든 수정 가능하다.
    
    수정 완료 후
    
    1. 기존 AI 분석 결과 무효화
    2. AI Parsing 재수행
    3. Coaching Feedback 재생성
    
    ### Exception
    
    다음만 수정한 경우
    
    - 사진 추가
    - 메모 오탈자
    
    AI 재분석은 수행하지 않는다.
    
    ---
    
    ## BR-RECORD-007. 영농일지 삭제
    
    ### Trigger
    
    사용자가 영농일지를 삭제한다.
    
    ### Rules
    
    MVP에서는 Soft Delete를 사용한다.
    
    삭제된 영농일지는
    
    기본 조회에서 제외한다.
    
    ### Related Data
    
    - AI Parsing 삭제
    - Coaching Feedback 삭제
    
    커뮤니티 공유 여부는
    
    공유 해제 처리한다.
    
    ---
    
    ## BR-RECORD-008. 다년생 작물
    
    ### Rules
    
    다년생 작물은
    
    재배연차가 필수이다.
    
    재배연차는 정식연도를 기준으로 계산한다.
    
    일반 작물은 선택 입력이다.
    
    ---
    
    ## BR-RECORD-009. 사진
    
    ### Rules
    
    사진은 선택 입력이다.
    
    사진만 존재하는 영농일지는 저장할 수 없다.
    
    사진은 기록의 보조 정보로만 사용한다.
    
    ---
    
    ## BR-RECORD-010. AI 실패
    
    ### Trigger
    
    AI Parsing 실패
    
    ### Rules
    
    영농일지는 정상 저장한다.
    
    AI 기능만 실패 처리한다.
    
    사용자는 직접 내용을 수정할 수 있다.
    
- Voice Session Rules
    
    # 3. Voice Session Rules
    
    ---
    
    ## BR-VOICE-001. 세션 생성
    
    ### Trigger
    
    사용자가 음성 기록을 시작한다.
    
    ### Rules
    
    voice_record_session을 생성한다.
    
    status
    
    ```
    CREATED
    ```
    
    로 시작한다.
    
    ---
    
    ## BR-VOICE-002. 음성 기록
    
    ### Rules
    
    사용자의 음성은
    
    STT를 통해 transcript를 생성한다.
    
    transcript는
    
    사용자가 종료할 때까지 계속 갱신된다.
    
    ---
    
    ## BR-VOICE-003. AI 구조화
    
    ### Trigger
    
    STT 완료
    
    ### Rules
    
    AI는 transcript를 분석하여
    
    초기 영농기록 초안을 생성한다.
    
    초안은 사용자 확인 전까지
    
    확정되지 않는다.
    
    ---
    
    ## BR-VOICE-004. 누락 정보 확인
    
    ### Trigger
    
    필수값이 부족하다.
    
    ### Rules
    
    AI는
    
    누락된 필드를
    
    한 번에 하나씩 질문한다.
    
    예)
    
    - 작물이 무엇인가요?
    - 사용량은 얼마인가요?
    
    ---
    
    ## BR-VOICE-005. 사용자 수정
    
    ### Rules
    
    사용자는
    
    기존 답변을 언제든 수정할 수 있다.
    
    최신 답변만 유효하다.
    
    ---
    
    ## BR-VOICE-006. 저장 승인
    
    ### Trigger
    
    사용자가 저장을 승인한다.
    
    ### Rules
    
    최종 영농일지를 생성한다.
    
    Session은
    
    ```
    COMPLETED
    ```
    
    상태가 된다.
    
    ---
    
    ## BR-VOICE-007. 취소
    
    ### Trigger
    
    사용자가 취소한다.
    
    ### Rules
    
    Session을 종료한다.
    
    영농일지는 생성하지 않는다.
    
    Session 상태
    
    ```
    CANCELLED
    ```
    
    ---
    
    ## BR-VOICE-008. 비정상 종료
    
    ### Trigger
    
    앱 종료
    
    네트워크 오류
    
    강제 종료
    
    ### Rules
    
    Session 상태를
    
    ```
    FAILED
    ```
    
    로 변경한다.
    
    생성되지 않은 영농일지는 저장하지 않는다.
    
    사용자는 새로운 Session으로 다시 시작한다.
    
- AI Rules
    
    # 4. AI Rules
    
    ---
    
    ## BR-AI-001. AI의 역할
    
    ### Rules
    
    사용자가 영농기록 작성 후, AI는 해당 기록/리포트에 대한 (보조,제안,코칭)하는 역할만 수행한다.
    
    AI는
    
    - 입력 데이터 구조화
    - 입력값 후보 추출
    - 기록 기반 피드백 생성
    - 리포트 요약 생성
    
    만 수행한다.
    
    ---
    
    ## BR-AI-002. AI가 추출 가능한 데이터
    
    ### Rules
    
    AI는 다음 데이터를 추출할 수 있다.
    
    - 작물명
    - 작업 종류
    - 비료명
    - 농약명
    - 사용량
    - 메모 요약
    
    추출 결과는 후보값으로만 사용한다.
    
    ---
    
    ## BR-AI-003. AI가 생성하면 안 되는 데이터
    
    ### Rules
    
    AI는 존재하지 않는 영농 사실을 생성하면 안 된다.
    
    다음 값은 사용자 입력 없이 확정할 수 없다.
    
    - 날짜
    - 시간
    - 수량
    - 재배연차
    - 농약명
    - 비료명
    - 수확량
    
    ---
    
    ## BR-AI-004. 불확실한 데이터
    
    ### Rules
    
    AI가 확신하지 못하는 값은
    
    NULL
    
    또는
    
    미확정 상태로 유지한다.
    
    추측값을 저장하지 않는다.
    
    ---
    
    ## BR-AI-005. 사용자 확인
    
    ### Rules
    
    AI가 추출한 모든 후보값은
    
    사용자가 확인 후 저장한다.
    
    사용자가 수정한 값이 항상 우선한다.
    
    ---
    
    ## BR-AI-006. 데이터 출처
    
    ### Rules
    
    AI 피드백에는 어떤 데이터를 근거로 생성했는지 표시한다.
    
    예)
    
    - 영농기록
    - 날씨
    - 작업별 입력값
- Coaching Rules
    
    # 5. Coaching Rules
    
    ---
    
    ## BR-COACH-001. 피드백 생성
    
    ### Trigger
    
    영농일지가 저장된다.
    
    ### Rules
    
    [BE] 저장 완료 후 AI는 해당 영농일지에 대한 Coaching Feedback 1개를 생성한다.
    
    ---
    
    ## BR-COACH-002. 피드백 범위
    
    ### Rules
    
    피드백은 단일 영농일지 기준으로 생성한다.
    
    다른 영농일지는 참조하지 않는다.
    
    ---
    
    ## BR-COACH-003. 피드백 구성
    
    ### Rules
    
    피드백은
    
    다음 항목으로 구성한다.
    
    - 요약
    - 위험 신호
    - 다음 권장 행동
    
    ---
    
    ## BR-COACH-004. 영농일지 수정
    
    ### Trigger
    
    영농일지가 수정된다.
    
    ### Rules
    
    기존 Coaching Feedback을 삭제한다.
    
    새로운 데이터를 기준으로
    
    다시 생성한다.
    
    ---
    
    ## BR-COACH-005. 영농일지 삭제
    
    ### Trigger
    
    영농일지가 삭제된다.
    
    ### Rules
    
    연결된 Coaching Feedback도 삭제한다.
    
    ---
    
    ## BR-COACH-006. AI 실패
    
    ### Trigger
    
    AI 생성 실패
    
    ### Rules
    
    영농일지는 정상 저장한다.
    
    피드백만 생성하지 않는다.
    
    사용자는 나중에 다시 생성할 수 있다.
    
- Report Rules
    
    # 6. Report Rules
    
    ---
    
    ## BR-REPORT-001. 리포트 생성
    
    ### Trigger
    
    사용자가 리포트 화면을 조회한다.
    
    ### Rules
    
    리포트는 영농기록을 기반으로 실시간 생성한다.
    
    DB에 저장하지 않는다.
    
    ---
    
    ## BR-REPORT-002. 조회 기간
    
    ### Rules
    
    사용자는
    
    다음 기간을 선택할 수 있다.
    
    - 최근 7일
    - 최근 30일
    - 전체 기간
    - 사용자 지정 기간
    
    ---
    
    ## BR-REPORT-003. 리포트 구성
    
    ### Rules
    
    리포트는 다음 정보를 포함한다.
    
    - 작업 횟수
    - 작업 유형 분포
    - 작물별 통계
    - 위험 신호
    - AI 종합 요약
    
    ---
    
    ## **BR-REPORT-004. 영농일지 수정**
    
    ### Trigger
    
    영농일지가 수정된다.
    
    ### Rules
    
    별도의 리포트 갱신은 수행하지 않는다.
    
    다음 조회 시 최신 데이터를 기준으로 다시 생성한다.
    
    ---
    
    ## BR-REPORT-005. AI 실패
    
    ### Rules
    
    AI 요약 생성에 실패하면
    
    통계 정보만 표시한다.
    
- Community Rules
    
    # 7. Community Rules
    
    ---
    
    ## BR-COMMUNITY-001. 게시글 생성
    
    ### Trigger
    
    사용자가 게시글을 작성한다.
    
    ### Rules
    
    게시글 유형은
    
    - GENERAL
    - QUESTION
    
    중 하나를 선택한다.
    
    ---
    
    ## BR-COMMUNITY-002. 영농일지 공유
    
    ### Trigger
    
    사용자가 영농일지를 공유한다.
    
    ### Rules
    
    **게시글은 해당 영농일지와 연결된다. (선택)**
    
    작물 정보는 영농일지에서 자동 가져온다.
    
    ---
    
    ## BR-COMMUNITY-003. 게시글 수정
    
    ### Rules
    
    작성자만 수정 가능하다.
    
    ---
    
    ## BR-COMMUNITY-004. 게시글 삭제
    
    ### Rules
    
    작성자만 게시글을 삭제할 수 있다.
    
    이때, 해당 게시글에 연결된 영농일지는 삭제되지 않는다.
    
    ---
    
    ## BR-COMMUNITY-005. 영농일지 삭제
    
    ### Trigger
    
    영농일지가 삭제된다.
    
    ### Rules
    
    연결된 게시글은 공유 해제 상태로 변경한다.
    
    영농일지 링크는 제거한다.
    
    ---
    
    ## BR-COMMUNITY-006. 댓글 작성
    
    ### Rules
    
    댓글은
    
    최상위 댓글
    
    또는
    
    답글
    
    형태로 작성한다.
    
    parent_comment_id가
    
    NULL이면
    
    최상위 댓글이다.
    
    ---
    
    ## BR-COMMUNITY-007. 답글
    
    ### Rules
    
    답글은
    
    기존 댓글에만 작성 가능하다.
    
    답글의 답글도 허용한다.
    
    (DB 기준)
    
    ---
    
    ## BR-COMMUNITY-008. 채택 답변
    
    ### Rules
    
    QUESTION 게시글만
    
    답변 채택이 가능하다.
    
    작성자만 채택할 수 있다.
    
    채택 가능한 댓글은
    
    1개만 허용한다.
    
    ---
    
    ## BR-COMMUNITY-009. 권한
    
    ### Rules
    
    게시글 작성자만
    
    - 수정
    - 삭제
    
    할 수 있다.
    
    댓글 작성자만
    
    댓글 수정 및 삭제가 가능하다.
    
    ---
    
    ## BR-COMMUNITY-010. 검색 노출
    
    ### Rules
    
    공개 게시글만
    
    통합 검색 대상이 된다.
    
    삭제된 게시글은
    
    검색 결과에서 제외한다.
    

- Search Rules
    
    # 8. Search Rules
    
    ---
    
    ## BR-SEARCH-001. 검색 대상
    
    ### Rules
    
    통합 검색은 다음 데이터를 대상으로 한다.
    
    - 영농일지
    - 커뮤니티 게시글
    - 정책 정보
    - 약관 및 공지사항
    
    ---
    
    ## BR-SEARCH-002. 검색 범위
    
    ### Rules
    
    검색은
    
    - 제목
    - 내용
    - 작물명
    - 작업 종류
    
    를 대상으로 수행한다.
    
    ---
    
    ## BR-SEARCH-003. 검색 제외
    
    ### Rules
    
    다음 데이터는 검색 결과에 포함하지 않는다.
    
    - 삭제된 데이터
    - 비공개 데이터
    - 탈퇴한 사용자의 비공개 정보
    
    ---
    
    ## BR-SEARCH-004. 기본 정렬
    
    ### Rules
    
    검색 결과는
    
    관련도순
    
    을 기본으로 한다.
    
    관련도가 동일한 경우
    
    최신순으로 정렬한다.
    
    ---
    
    ## BR-SEARCH-005. 필터
    
    ### Rules
    
    검색 결과는 다음 조건으로 필터링할 수 있다.
    
    - 작물
    - 작업 종류
    - 기간
    - 게시글 유형
- Policy Recommendation Rules
    
    # 9. Policy Recommendation Rules
    
    ---
    
    ## BR-POLICY-001. 추천 생성
    
    ### Trigger
    
    다음 상황에서 정책 추천을 생성한다.
    
    - 회원가입 완료
    - 프로필 수정
    - 주요 작물 변경
    - 지역 변경
    
    ---
    
    ## BR-POLICY-002. 추천 기준
    
    ### Rules
    
    다음 정보를 기반으로 추천한다.
    
    - 지역
    - 재배 작물
    - 영농 경력
    - 농장 유형
    
    ---
    
    ## BR-POLICY-003. 추천 결과
    
    ### Rules
    
    추천 결과에는
    
    - 정책 정보
    - 추천 이유
    - 추천 점수
    
    를 포함한다.
    
    ---
    
    ## BR-POLICY-004. 추천 갱신
    
    ### Trigger
    
    사용자 정보가 변경된다.
    
    ### Rules
    
    기존 추천을 폐기하고
    
    새로운 추천을 생성한다.
    
    ---
    
    ## BR-POLICY-005. 정책 종료
    
    ### Rules
    
    종료된 정책은
    
    추천 대상에서 제외한다.
    
- Notification Rules
    
    # 10. Notification Rules
    
    ---
    
    ## BR-NOTIFICATION-001. 알림 종류
    
    ### Rules
    
    알림은 다음 유형을 제공한다.
    
    - 정책 추천
    - 공지사항
    - 커뮤니티 활동(베스트 게시물)
    - 서비스 알림
    
    ---
    
    ## BR-NOTIFICATION-002. 정책 알림
    
    ### Trigger
    
    새로운 추천 정책이 생성된다.
    
    ### Rules
    
    사용자의 알림 설정이 활성화되어 있으면
    
    푸시 알림을 발송한다.
    
    ---
    
    ## BR-NOTIFICATION-003. 커뮤니티 알림
    
    ### Trigger
    
    다음 이벤트가 발생한다.
    
    - 내 게시글에 댓글 작성
    - 내 댓글에 답글 작성
    - 질문 채택
    
    ### Rules
    
    알림을 생성한다.
    
    ---
    
    ## BR-NOTIFICATION-004. 알림 설정
    
    ### Rules
    
    사용자는
    
    알림 종류별
    
    ON/OFF를 설정할 수 있다.
    

- Data Lifecycle Rules
    
    # 11. Data Lifecycle Rules
    
    ---
    
    ## BR-DATA-001. Soft Delete
    
    ### Rules
    
    다음 데이터는 Soft Delete를 사용한다.
    
    - User
    - Farming Record
    - Community Post
    - Community Comment
    
    ---
    
    ## BR-DATA-002. 삭제 제한
    
    ### Rules
    
    영농기록이 존재하는 농장은
    
    삭제할 수 없다.
    
    ---
    
    ## BR-DATA-003. 연관 데이터 삭제
    
    ### Rules
    
    영농일지가 삭제되면
    
    다음 데이터도 함께 삭제한다.
    
    - AI Parsing
    - Coaching Feedback
    - 첨부 이미지
    
    공유 게시글은
    
    공유 해제 처리한다.
    
    ---
    
    ## BR-DATA-004. 생성 시간
    
    ### Rules
    
    모든 데이터는
    
    created_at
    
    을 반드시 저장한다.
    
    ---
    
    ## BR-DATA-005. 수정 시간
    
    ### Rules
    
    수정 가능한 데이터는
    
    수정 시
    
    updated_at
    
    을 갱신한다.
    
- State Transition Rules
    
    # 12. State Transition Rules
    
    ---
    
    ## BR-STATE-001. Voice Session
    
    ```
    CREATED
        ↓
    RECORDING
        ↓
    PROCESSING
        ↓
    WAITING_CONFIRMATION
        ↓
    COMPLETED
    ```
    
    예외
    
    ```
    RECORDING
            ↓
    CANCELLED
    ```
    
    ```
    PROCESSING
            ↓
    FAILED
    ```
    
    ---
    
    ## BR-STATE-002. User
    
    ```
    ACTIVE
        ↓
    WITHDRAWN
    ```
    
    탈퇴 후 복구는 지원하지 않는다.
    
    ---
    
    ## BR-STATE-003. Community Post
    
    ```
    CREATED
        ↓
    UPDATED
        ↓
    DELETED
    ```
    
- Permission Rules
    
    # 13. Permission Rules
    
    ---
    
    ## BR-PERMISSION-001. 영농일지
    
    사용자는
    
    자신이 작성한 영농일지만
    
    조회, 수정, 삭제할 수 있다.
    
    ---
    
    ## BR-PERMISSION-002. 농장
    
    사용자는
    
    자신의 농장만
    
    수정할 수 있다.
    
    ---
    
    ## BR-PERMISSION-003. 게시글
    
    게시글 작성자만
    
    게시글 수정 및 삭제가 가능하다.
    
    ---
    
    ## BR-PERMISSION-004. 댓글
    
    댓글 작성자만
    
    댓글 수정 및 삭제가 가능하다.
    
    ---
    
    ## BR-PERMISSION-005. 답변 채택
    
    질문 게시글 작성자만
    
    답변을 채택할 수 있다.
    
    ---
    
    ## BR-PERMISSION-006. 관리자
    
    관리자는
    
    다음 권한을 가진다.
    
    - 정책 등록 및 수정
    - 공지사항 등록
    - 부적절한 게시글 삭제
    - 신고 처리
- Exception Rules
    
    # 14. Exception Rules
    
    ---
    
    ## BR-EXCEPTION-001. AI 실패
    
    ### Rules
    
    AI 기능이 실패하더라도
    
    영농일지 저장은 실패하지 않는다.
    
    AI 결과만 생성하지 않는다.
    
    ---
    
    ## BR-EXCEPTION-002. 날씨 조회 실패
    
    ### Rules
    
    날씨 조회 실패 시
    
    영농일지 저장은 정상 진행한다.
    
    날씨 정보는 NULL로 저장한다.
    
    ---
    
    ## BR-EXCEPTION-003. 이미지 업로드 실패
    
    ### Rules
    
    이미지 업로드 실패는
    
    영농일지 저장을 막지 않는다.
    
    이미지만 다시 업로드할 수 있다.
    
    ---
    
    ## BR-EXCEPTION-004. 네트워크 오류
    
    ### Rules
    
    데이터 저장 실패 시
    
    사용자에게 실패를 안내한다.
    
    저장되지 않은 데이터는
    
    재시도할 수 있다.
    
    ---
    
    ## BR-EXCEPTION-005. 필수값 누락
    
    ### Rules
    
    필수 입력값이 누락된 경우
    
    저장을 수행하지 않는다.
    
    누락된 항목을 사용자에게 표시한다.
    
- General Principles
    
    # 15. General Principles
    
    ---
    
    ## BR-GENERAL-001. 사용자 입력 우선
    
    AI보다
    
    사용자의 직접 입력을 항상 우선한다.
    
    ---
    
    ## BR-GENERAL-002. 데이터 추측 금지
    
    AI는
    
    확실하지 않은 값을
    
    생성하거나 저장하지 않는다.
    
    ---
    
    ## BR-GENERAL-003. 단일 책임
    
    - 영농일지는 기록을 관리한다.
    - Coaching은 단일 영농일지 피드백을 관리한다.
    - Report는 여러 영농일지를 종합 분석한다.
    - Community는 사용자 간 정보 공유를 담당한다.
    
    ---
    
    ## BR-GENERAL-004. MVP 원칙
    
    MVP에서는
    
    - 기능 단순성
    - 데이터 일관성
    - 사용자 입력 우선
    - AI 보조 역할
    
    을 최우선 원칙으로 한다.

### 영농 리포트 주기

- 주기 범위는 회원·밭·작물 조합이다.
- 앞선 마지막 수확이 없으면 최초 기록부터 시작한다.
- 앞선 마지막 수확이 있으면 그 다음 기록부터 시작한다.
- 일부 수확은 주기를 닫지 않고 마지막 수확만 해당 기록을 포함해 주기를 닫는다.
- 심기는 강제 시작점이 아니며 심기 전 준비 작업도 주기에 포함한다.
- 현재 리포트의 비교 대상은 지난해가 아니라 직전 완료 주기다.
- 관수 횟수와 간격만으로 수분 부족·과다·적정을 판단하지 않는다.
