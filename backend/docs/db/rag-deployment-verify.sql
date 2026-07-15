\set ON_ERROR_STOP on

do $$
declare
    missing_extensions text;
    missing_tables text;
    mismatched_columns text;
    missing_constraints text;
begin
    select string_agg(required.name, ', ' order by required.name)
      into missing_extensions
      from (values ('vector'), ('hstore'), ('uuid-ossp')) as required(name)
     where not exists (
         select 1
           from pg_extension extension
          where extension.extname = required.name
     );

    if missing_extensions is not null then
        raise exception 'missing required extensions: %', missing_extensions;
    end if;

    select string_agg(required.name, ', ' order by required.name)
      into missing_tables
      from (
          values
              ('public.vector_store'),
              ('public.farming_cycle_report'),
              ('public.record_feedback'),
              ('public.record_feedback_next_action'),
              ('public.report_feedback'),
              ('public.report_feedback_item')
      ) as required(name)
     where to_regclass(required.name) is null;

    if missing_tables is not null then
        raise exception 'missing required tables: %', missing_tables;
    end if;

    select string_agg(
               format('%s.%s expected %s', required.table_name, required.column_name, required.data_type),
               ', '
               order by required.table_name, required.column_name
           )
      into mismatched_columns
      from (
          values
              ('vector_store', 'id', 'uuid', true),
              ('vector_store', 'content', 'text', false),
              ('vector_store', 'metadata', 'json', false),
              ('vector_store', 'embedding', 'vector(1024)', false),
              ('record_feedback', 'source_revision', 'bigint', true),
              ('record_feedback', 'good_point_text', 'character varying(255)', false),
              ('record_feedback_next_action', 'text', 'character varying(255)', true),
              ('report_feedback', 'source_fingerprint', 'character varying(64)', false),
              ('report_feedback', 'summary', 'text', false),
              ('report_feedback_item', 'text', 'text', true)
      ) as required(table_name, column_name, data_type, not_null)
     where not exists (
         select 1
           from pg_attribute attribute
          where attribute.attrelid = format('public.%I', required.table_name)::regclass
            and attribute.attname = required.column_name
            and not attribute.attisdropped
            and format_type(attribute.atttypid, attribute.atttypmod) = required.data_type
            and attribute.attnotnull = required.not_null
     );

    if mismatched_columns is not null then
        raise exception 'column contract mismatch: %', mismatched_columns;
    end if;

    if not exists (
        select 1
          from pg_indexes
         where schemaname = 'public'
           and tablename = 'vector_store'
           and indexname = 'spring_ai_vector_index'
           and indexdef ilike '%using hnsw%'
           and indexdef ilike '%vector_cosine_ops%'
    ) then
        raise exception 'spring_ai_vector_index must be HNSW with vector_cosine_ops';
    end if;

    select string_agg(required.name, ', ' order by required.name)
      into missing_constraints
      from (
          values
              ('record_feedback', 'record_feedback_status_check', 'STALE'),
              ('record_feedback', 'uk_record_feedback_record_revision', null),
              ('record_feedback_next_action', 'uk_record_feedback_next_action_order', null),
              ('report_feedback', 'report_feedback_status_check', 'STALE'),
              ('report_feedback', 'uk_report_feedback_report_work_type', null),
              ('report_feedback_item', 'uk_report_feedback_item_order', null)
      ) as required(table_name, name, required_fragment)
     where not exists (
         select 1
           from pg_constraint constraint_info
          where constraint_info.conrelid = format('public.%I', required.table_name)::regclass
            and constraint_info.conname = required.name
            and (
                required.required_fragment is null
                or pg_get_constraintdef(constraint_info.oid) like '%' || required.required_fragment || '%'
            )
     );

    if missing_constraints is not null then
        raise exception 'missing or incompatible constraints: %', missing_constraints;
    end if;
end
$$;

select
    format_type(attribute.atttypid, attribute.atttypmod) as embedding_type,
    (select count(*) from public.vector_store) as vector_rows
from pg_attribute attribute
where attribute.attrelid = 'public.vector_store'::regclass
  and attribute.attname = 'embedding'
  and not attribute.attisdropped;
