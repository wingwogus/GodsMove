-- Flyway is not installed. Apply this reviewed PostgreSQL schema manually to dev/prod.
-- Do not run this file against a database until legacy REPORT_MANUAL feedback rows
-- have been explicitly mapped to farming_cycle_report or archived by an operator.

alter table farming_record
    add column if not exists source_revision bigint not null default 1;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'ck_farming_record_source_revision'
    ) then
        alter table farming_record
            add constraint ck_farming_record_source_revision
            check (source_revision > 0);
    end if;
end $$;

alter table coaching_feedback
    add column if not exists feedback_type varchar(32);

alter table coaching_feedback
    add column if not exists status varchar(32);

alter table coaching_feedback
    add column if not exists cycle_report_id uuid;

alter table coaching_feedback
    add column if not exists source_revision bigint;

alter table coaching_feedback
    add column if not exists input_snapshot jsonb;

alter table coaching_feedback
    add column if not exists failure_code varchar(128);

-- New phase-2 fields must permit records before LLM generation.
alter table coaching_feedback
    alter column structured_result drop not null;

alter table coaching_feedback
    alter column model_name drop not null;

alter table coaching_feedback
    alter column embedding_model drop not null;

alter table coaching_feedback
    alter column citations set default '[]'::jsonb;

alter table coaching_feedback
    alter column audit_warnings set default '[]'::jsonb;

update coaching_feedback
   set citations = '[]'::jsonb
 where citations is null;

update coaching_feedback
   set audit_warnings = '[]'::jsonb
 where audit_warnings is null;

alter table coaching_feedback
    alter column citations set not null;

alter table coaching_feedback
    alter column audit_warnings set not null;

-- The new uniqueness contract has one row per record revision. Do not choose a
-- legacy winner implicitly: an operator must archive or map duplicate rows first.
do $$
begin
    if exists (
        select 1
          from coaching_feedback feedback
          join farming_record record on record.id = feedback.record_id
         where feedback.coaching_mode = 'RECORD_AUTO'
           and feedback.feedback_type is null
         group by feedback.record_id
        having count(*) > 1
    ) then
        raise exception using
            message = 'Duplicate legacy RECORD_AUTO coaching_feedback rows remain',
            hint = 'Archive or explicitly select one legacy feedback row for each record before applying the revision uniqueness index. No row was deleted or guessed.';
    end if;
end $$;

-- Legacy single-record feedback has no immutable input snapshot. Preserve it as
-- retryable failure rather than presenting stale text as phase-2 coaching.
update coaching_feedback feedback
   set feedback_type = 'RECORD',
       status = 'FAILED',
       source_revision = record.source_revision,
       failure_code = 'LEGACY_INPUT_SNAPSHOT_MISSING'
  from farming_record record
 where feedback.coaching_mode = 'RECORD_AUTO'
   and feedback.record_id = record.id
   and feedback.feedback_type is null;

-- Existing REPORT_MANUAL rows cannot be associated to a cycle safely: the old
-- table has farm/crop/period fields, but no canonical cycle_report_id.
-- Map each row to an exact farming_cycle_report or archive it before rerunning.
do $$
begin
    if exists (
        select 1
          from coaching_feedback
         where feedback_type is null
    ) then
        raise exception using
            message = 'Unmappable legacy coaching_feedback rows remain',
            hint = 'Map each REPORT_MANUAL row to cycle_report_id and feedback_type=CYCLE_REPORT, or archive it outside this migration. No rows were deleted or guessed.';
    end if;
end $$;

-- Legacy columns are no longer written. Keep them nullable until operators have
-- verified their archive/mapping policy; do not drop historical data here.
do $$
declare
    legacy_column text;
begin
    foreach legacy_column in array array[
        'coaching_mode', 'farm_id', 'crop_id', 'question', 'period_starts_on',
        'period_ends_on', 'summary', 'risk_level', 'confidence_score',
        'audit_status'
    ] loop
        if exists (
            select 1
              from information_schema.columns
             where table_schema = current_schema()
               and table_name = 'coaching_feedback'
               and column_name = legacy_column
        ) then
            execute format(
                'alter table coaching_feedback alter column %I drop not null',
                legacy_column
            );
        end if;
    end loop;
end $$;

alter table coaching_feedback
    alter column feedback_type set not null;

alter table coaching_feedback
    alter column status set not null;

alter table coaching_feedback
    alter column source_revision set not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_coaching_feedback_cycle_report'
    ) then
        alter table coaching_feedback
            add constraint fk_coaching_feedback_cycle_report
            foreign key (cycle_report_id) references farming_cycle_report (id);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'ck_coaching_feedback_target'
    ) then
        alter table coaching_feedback
            add constraint ck_coaching_feedback_target
            check (
                (feedback_type = 'RECORD' and record_id is not null and cycle_report_id is null)
                or
                (feedback_type = 'CYCLE_REPORT' and record_id is null and cycle_report_id is not null)
            );
    end if;

    if not exists (
        select 1 from pg_constraint where conname = 'ck_coaching_feedback_status'
    ) then
        alter table coaching_feedback
            add constraint ck_coaching_feedback_status
            check (status in ('PENDING', 'READY', 'FAILED', 'STALE'));
    end if;

    if not exists (
        select 1 from pg_constraint where conname = 'ck_coaching_feedback_source_revision'
    ) then
        alter table coaching_feedback
            add constraint ck_coaching_feedback_source_revision
            check (source_revision > 0);
    end if;
end $$;

create unique index if not exists uq_coaching_feedback_record_revision
    on coaching_feedback (record_id, source_revision)
    where feedback_type = 'RECORD';

create unique index if not exists uq_coaching_feedback_cycle_report_revision
    on coaching_feedback (cycle_report_id, source_revision)
    where feedback_type = 'CYCLE_REPORT';

create index if not exists idx_coaching_feedback_record_status_updated_at
    on coaching_feedback (record_id, status, updated_at desc)
    where feedback_type = 'RECORD';
