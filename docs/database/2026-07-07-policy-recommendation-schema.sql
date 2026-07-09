create table if not exists policy_sync_job (
    id uuid primary key,
    source varchar(32) not null,
    target_year varchar(4) not null,
    trigger_type varchar(32) not null,
    status varchar(32) not null,
    started_at timestamp not null,
    finished_at timestamp,
    total_count integer not null default 0,
    synced_count integer not null default 0,
    detail_success_count integer not null default 0,
    detail_failure_count integer not null default 0,
    error_message varchar(1000),
    created_by_member_id uuid,
    created_at timestamp not null,
    updated_at timestamp not null
);

-- Stage source identity columns as nullable first. Existing policy rows cannot
-- share the same (source, external_id, source_year) value before the unique
-- index is created.
alter table policy_program add column if not exists source varchar(32);
alter table policy_program add column if not exists external_id varchar(64);
alter table policy_program add column if not exists source_year varchar(4);
alter table policy_program alter column source drop default;
alter table policy_program alter column external_id drop default;
alter table policy_program alter column source_year drop default;
alter table policy_program alter column source drop not null;
alter table policy_program alter column external_id drop not null;
alter table policy_program alter column source_year drop not null;

update policy_program
set
    source = coalesce(nullif(source, ''), 'NONGUP_EZ'),
    external_id = coalesce(nullif(external_id, ''), id::text),
    source_year = coalesce(nullif(source_year, ''), '0000')
where source is null
   or source = ''
   or external_id is null
   or external_id = ''
   or source_year is null
   or source_year = '';

alter table policy_program alter column source set not null;
alter table policy_program alter column external_id set not null;
alter table policy_program alter column source_year set not null;
alter table policy_program add column if not exists summary text;
alter table policy_program alter column target_management_type drop not null;
alter table policy_program add column if not exists agency_name varchar(255) not null default '';
alter table policy_program add column if not exists department_name varchar(255);
alter table policy_program add column if not exists online_apply_available boolean not null default false;
alter table policy_program add column if not exists application_url varchar(2048);
alter table policy_program add column if not exists application_period_label varchar(19) not null default '접수기관문의';
alter table policy_program add column if not exists application_period_notice varchar(255);
alter table policy_program add column if not exists eligibility_original text;
alter table policy_program add column if not exists eligibility_summary varchar(19) not null default '상세 자격 확인';
alter table policy_program add column if not exists benefit_original text;
alter table policy_program add column if not exists benefit_summary varchar(19) not null default '상세 지원 확인';
alter table policy_program add column if not exists purpose text;
alter table policy_program add column if not exists application_method text;
alter table policy_program add column if not exists required_documents text;
alter table policy_program add column if not exists selection_criteria text;
alter table policy_program add column if not exists detail_synced boolean not null default false;
alter table policy_program add column if not exists recommendable boolean not null default false;
alter table policy_program add column if not exists target_tags_json text not null default '[]';
alter table policy_program add column if not exists crop_tags_json text not null default '[]';
alter table policy_program add column if not exists region_tags_json text not null default '[]';
alter table policy_program drop column if exists last_synced_job_id;
alter table policy_program add column if not exists raw_payload text not null default '{}';

comment on column policy_program.raw_payload is
    'Original NongupEZ list/detail JSON payload. MVP preserves contacts, attachments, and source tag values here instead of separate normalized tables.';

create unique index if not exists uk_policy_program_source_external_year
    on policy_program(source, external_id, source_year);

drop index if exists ix_policy_recommendation_member_sync_score;
alter table policy_recommendation drop column if exists source_sync_job_id;

create index if not exists ix_policy_recommendation_member_policy_score
    on policy_recommendation(member_id, policy_program_id, score desc, id asc);
