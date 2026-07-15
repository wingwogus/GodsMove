-- Flyway is not installed. Apply this reviewed schema manually to dev/prod.
create table if not exists farming_cycle_report (
    id uuid primary key,
    member_id uuid not null references member (id),
    farm_id uuid not null references farm (id),
    crop_id uuid not null references crop (id),
    status varchar(16) not null,
    starts_at timestamp not null,
    ends_at timestamp null,
    start_basis varchar(48) not null,
    final_harvest_record_id uuid null references farming_record (id),
    statistics_schema_version integer not null,
    statistics jsonb not null,
    source_revision bigint not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint ck_farming_cycle_report_status
        check (status in ('ACTIVE', 'COMPLETED', 'SUPERSEDED')),
    constraint ck_farming_cycle_report_start_basis
        check (
            start_basis in (
                'FIRST_RECORD',
                'AFTER_PREVIOUS_FINAL_HARVEST'
            )
        ),
    constraint ck_farming_cycle_report_boundary
        check (
            status = 'SUPERSEDED'
            or (
                status = 'ACTIVE'
                and ends_at is null
                and final_harvest_record_id is null
            )
            or (
                status = 'COMPLETED'
                and ends_at is not null
                and final_harvest_record_id is not null
            )
        ),
    constraint ck_farming_cycle_report_dates
        check (ends_at is null or starts_at <= ends_at),
    constraint ck_farming_cycle_report_schema_version
        check (statistics_schema_version > 0),
    constraint ck_farming_cycle_report_source_revision
        check (source_revision > 0)
);

create unique index if not exists uq_farming_cycle_report_active_scope
    on farming_cycle_report (member_id, farm_id, crop_id)
    where status = 'ACTIVE';

create unique index if not exists uq_farming_cycle_report_completed_final
    on farming_cycle_report (final_harvest_record_id)
    where status = 'COMPLETED';

create index if not exists idx_farming_cycle_report_completed_list
    on farming_cycle_report (
        member_id,
        farm_id,
        crop_id,
        ends_at desc,
        final_harvest_record_id desc
    )
    where status = 'COMPLETED';

create index if not exists idx_farming_cycle_report_scope_start
    on farming_cycle_report (
        member_id,
        farm_id,
        crop_id,
        starts_at,
        id
    );
