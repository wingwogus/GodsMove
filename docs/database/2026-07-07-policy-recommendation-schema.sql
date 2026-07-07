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

alter table policy_program add column if not exists source varchar(32) not null default 'NONGUP_EZ';
alter table policy_program add column if not exists external_id varchar(64) not null default '';
alter table policy_program add column if not exists source_year varchar(4) not null default '';
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
alter table policy_program add column if not exists last_synced_job_id uuid references policy_sync_job(id);
alter table policy_program add column if not exists raw_payload text not null default '{}';

create unique index if not exists uk_policy_program_source_external_year
    on policy_program(source, external_id, source_year);

alter table policy_recommendation add column if not exists source_sync_job_id uuid references policy_sync_job(id);
create index if not exists ix_policy_recommendation_member_sync_score
    on policy_recommendation(member_id, source_sync_job_id, score desc, id asc);
