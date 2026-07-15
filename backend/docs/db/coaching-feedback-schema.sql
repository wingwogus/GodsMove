-- Flyway is not installed. Apply this reviewed schema manually to dev/prod.
\set ON_ERROR_STOP on

begin;

create table if not exists public.record_feedback (
    created_at timestamp(6) not null,
    source_revision bigint not null,
    updated_at timestamp(6) not null,
    id uuid primary key,
    member_id uuid not null references public.member (id),
    record_id uuid not null references public.farming_record (id),
    audit_status varchar(32),
    status varchar(32) not null,
    embedding_model varchar(128),
    failure_code varchar(128),
    model_name varchar(128),
    good_point_basis varchar(255),
    good_point_text varchar(255),
    audit_warnings jsonb not null,
    citations jsonb not null,
    input_snapshot jsonb,
    constraint record_feedback_status_check
        check (status in ('PENDING', 'READY', 'FAILED', 'STALE')),
    constraint uk_record_feedback_record_revision
        unique (record_id, source_revision)
);

alter table public.record_feedback
    drop constraint if exists ck_record_feedback_status;
alter table public.record_feedback
    drop constraint if exists record_feedback_status_check;
alter table public.record_feedback
    add constraint record_feedback_status_check
        check (status in ('PENDING', 'READY', 'FAILED', 'STALE'));

create table if not exists public.record_feedback_next_action (
    display_order integer not null,
    id uuid primary key,
    record_feedback_id uuid not null references public.record_feedback (id),
    category varchar(32) not null,
    due varchar(32) not null,
    basis varchar(255) not null,
    text varchar(255) not null,
    constraint record_feedback_next_action_category_check
        check (
            category in (
                'WEATHER', 'PEST_DISEASE', 'IRRIGATION', 'FERTILIZING',
                'PEST_CONTROL', 'HARVEST', 'CULTIVATION', 'GENERAL'
            )
        ),
    constraint record_feedback_next_action_due_check
        check (due in ('TODAY', 'THIS_WEEK', 'NEXT_WEEK', 'NEXT_CHECK')),
    constraint uk_record_feedback_next_action_order
        unique (record_feedback_id, display_order)
);

create table if not exists public.report_feedback (
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid primary key,
    member_id uuid not null references public.member (id),
    report_id uuid not null references public.farming_cycle_report (id),
    audit_status varchar(32),
    status varchar(32) not null,
    work_type varchar(32) not null,
    source_fingerprint varchar(64),
    embedding_model varchar(128),
    failure_code varchar(128),
    model_name varchar(128),
    summary text,
    audit_warnings jsonb not null,
    citations jsonb not null,
    input_snapshot jsonb,
    constraint report_feedback_status_check
        check (status in ('PENDING', 'READY', 'FAILED', 'STALE')),
    constraint report_feedback_work_type_check
        check (
            work_type in (
                'PLANTING', 'WATERING', 'FERTILIZING', 'PEST_CONTROL',
                'WEEDING', 'PRUNING', 'HARVEST', 'ETC'
            )
        ),
    constraint uk_report_feedback_report_work_type
        unique (report_id, work_type)
);

alter table public.report_feedback
    add column if not exists source_fingerprint varchar(64);
alter table public.report_feedback
    drop constraint if exists ck_report_feedback_status;
alter table public.report_feedback
    drop constraint if exists report_feedback_status_check;
alter table public.report_feedback
    add constraint report_feedback_status_check
        check (status in ('PENDING', 'READY', 'FAILED', 'STALE'));

create table if not exists public.report_feedback_item (
    display_order integer not null,
    id uuid primary key,
    report_feedback_id uuid not null references public.report_feedback (id),
    section varchar(32) not null,
    basis text not null,
    text text not null,
    constraint report_feedback_item_section_check
        check (section in ('COMPARISON', 'STRENGTH', 'IMPROVEMENT', 'NEXT_ACTION')),
    constraint uk_report_feedback_item_order
        unique (report_feedback_id, display_order)
);

commit;
