-- Farming record CRUD adds weather fields and soft delete to farming_record.
-- Flyway is not installed in this backend yet; apply manually to dev/prod schemas.

-- No farming_record rows exist yet in any environment (create is not exposed
-- over HTTP before this feature), so these columns can be added not-null
-- directly without a backfill step.
alter table farming_record
    add column if not exists weather_condition varchar(64) not null;

alter table farming_record
    add column if not exists weather_temperature integer not null;

alter table farming_record
    add column if not exists is_deleted boolean not null default false;

create index if not exists idx_farming_record_member_worked_at
    on farming_record (member_id, is_deleted, worked_at desc, id desc);

alter table farming_record
    alter column memo set not null;
