-- Farming record report contract adds enum category columns and final harvest flag.
-- Flyway is not installed in this backend yet; apply manually to dev/prod schemas.
-- Existing fertilizing_record and pest_control_record rows require manual category
-- mapping before NOT NULL/drop. Do not guess categories from old free-text names.

alter table fertilizing_record
    add column if not exists material_category varchar(48);

alter table pest_control_record
    add column if not exists pesticide_category varchar(32);

alter table harvest_record
    add column if not exists is_final_harvest boolean not null default false;

-- Manually map every existing row, for example:
-- update fertilizing_record set material_category = '<FertilizerMaterialCategory>' where id = '<row-id>';
-- update pest_control_record set pesticide_category = '<PesticideCategory>' where id = '<row-id>';
--
-- IMPORTANT: Existing harvest rows are added as false only to keep the DDL
-- explicit and non-heuristic. Before any farming cycle report projection is
-- trusted, built, rebuilt, or backfilled, operators must mark each known
-- historical final harvest by exact row ID. Do not run report rebuild/backfill
-- until this ID mapping is complete.
--
-- update harvest_record
--    set is_final_harvest = true
--  where id in ('<known-final-harvest-row-id>', '<known-final-harvest-row-id>');

do $$
begin
    if not exists (
        select 1
          from pg_constraint
         where conname = 'ck_fertilizing_record_material_category'
    ) then
        alter table fertilizing_record
            add constraint ck_fertilizing_record_material_category
            check (
                material_category in (
                    'COMPOUND_FERTILIZER',
                    'NITROGEN_FERTILIZER',
                    'PHOSPHATE_FERTILIZER',
                    'POTASSIUM_FERTILIZER',
                    'ORGANIC_FERTILIZER',
                    'LIME_FERTILIZER',
                    'OTHER'
                )
            );
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
          from pg_constraint
         where conname = 'ck_pest_control_record_pesticide_category'
    ) then
        alter table pest_control_record
            add constraint ck_pest_control_record_pesticide_category
            check (
                pesticide_category in (
                    'FUNGICIDE',
                    'INSECTICIDE',
                    'HERBICIDE',
                    'ACARICIDE',
                    'BIOPESTICIDE',
                    'OTHER'
                )
            );
    end if;
end $$;

alter table fertilizing_record
    alter column material_category set not null;

alter table pest_control_record
    alter column pesticide_category set not null;

alter table fertilizing_record
    drop column if exists material_name;

alter table pest_control_record
    drop column if exists pesticide_name;
