-- Voice farming-record session tables (음성 영농일지, BR-VOICE-*, PR #13).
-- Flyway is not installed. Apply this reviewed schema manually to dev/prod.
--
-- 배포 확인: 이 스키마 없이는 POST /api/v1/voice-sessions의 세션 저장(발급 성공 후)과
-- PATCH /api/v1/voice-sessions/{id}/turns가 500으로 실패한다. 적용 후 세션 생성 →
-- turns 제출까지 한 번 돌려 두 테이블에 행이 남는지 확인한다.

create table if not exists voice_record_session (
    id uuid primary key,
    member_id uuid not null references member (id) on delete cascade,
    draft_record_id uuid null references farming_record (id) on delete set null,
    status varchar(32) not null,
    transcript text null,
    confirmed_at timestamp null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint ck_voice_record_session_status
        check (
            status in (
                'CREATED',
                'PROCESSING',
                'WAITING_CONFIRMATION',
                'COMPLETED',
                'CANCELLED',
                'FAILED'
            )
        )
);

-- findByIdAndMemberId는 PK 조회지만, 회원별 세션 정리/조회를 위해 member 인덱스를 둔다.
create index if not exists idx_voice_record_session_member
    on voice_record_session (member_id);

create table if not exists voice_record_turn (
    id uuid primary key,
    session_id uuid not null references voice_record_session (id) on delete cascade,
    role varchar(32) not null,
    content text not null,
    extracted_fields text null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint ck_voice_record_turn_role
        check (role in ('USER', 'ASSISTANT'))
);

create index if not exists idx_voice_record_turn_session
    on voice_record_turn (session_id);
