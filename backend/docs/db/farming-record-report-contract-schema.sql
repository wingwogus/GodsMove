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

alter table fertilizing_record
    alter column material_category set not null;

alter table pest_control_record
    alter column pesticide_category set not null;

alter table fertilizing_record
    drop column if exists material_name;

alter table pest_control_record
    drop column if exists pesticide_name;
