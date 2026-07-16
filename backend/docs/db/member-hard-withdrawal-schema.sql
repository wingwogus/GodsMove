do $$
declare
    relation record;
    existing_constraint text;
begin
    for relation in
        select *
        from (values
            ('external_identity', 'member_id', 'member', 'CASCADE'),
            ('member_consent', 'member_id', 'member', 'CASCADE'),
            ('notification_preference', 'member_id', 'member', 'CASCADE'),
            ('farm', 'owner_member_id', 'member', 'CASCADE'),
            ('member_crop', 'member_id', 'member', 'CASCADE'),
            ('farming_record', 'member_id', 'member', 'CASCADE'),
            ('farming_record', 'farm_id', 'farm', 'CASCADE'),
            ('farming_cycle_report', 'member_id', 'member', 'CASCADE'),
            ('farming_cycle_report', 'farm_id', 'farm', 'CASCADE'),
            ('record_feedback', 'member_id', 'member', 'CASCADE'),
            ('report_feedback', 'member_id', 'member', 'CASCADE'),
            ('policy_recommendation', 'member_id', 'member', 'CASCADE'),
            ('voice_record_session', 'member_id', 'member', 'CASCADE'),
            ('community_post', 'author_member_id', 'member', 'CASCADE'),
            ('community_comment', 'author_member_id', 'member', 'CASCADE'),
            ('community_post_like', 'member_id', 'member', 'CASCADE'),
            ('uploaded_media', 'owner_member_id', 'member', 'CASCADE'),
            ('farm_boundary_coordinate', 'farm_id', 'farm', 'CASCADE'),
            ('member_crop', 'farm_id', 'farm', 'CASCADE'),
            ('planting_record', 'record_id', 'farming_record', 'CASCADE'),
            ('watering_record', 'record_id', 'farming_record', 'CASCADE'),
            ('weeding_record', 'record_id', 'farming_record', 'CASCADE'),
            ('fertilizing_record', 'record_id', 'farming_record', 'CASCADE'),
            ('pest_control_record', 'record_id', 'farming_record', 'CASCADE'),
            ('harvest_record', 'record_id', 'farming_record', 'CASCADE'),
            ('farming_record_media', 'record_id', 'farming_record', 'CASCADE'),
            ('record_feedback', 'record_id', 'farming_record', 'CASCADE'),
            ('record_feedback_next_action', 'record_feedback_id', 'record_feedback', 'CASCADE'),
            ('report_feedback', 'report_id', 'farming_cycle_report', 'CASCADE'),
            ('report_feedback_item', 'report_feedback_id', 'report_feedback', 'CASCADE'),
            ('voice_record_turn', 'session_id', 'voice_record_session', 'CASCADE'),
            ('community_post_media', 'post_id', 'community_post', 'CASCADE'),
            ('community_comment', 'post_id', 'community_post', 'CASCADE'),
            ('community_post_like', 'post_id', 'community_post', 'CASCADE'),
            ('community_comment', 'parent_comment_id', 'community_comment', 'CASCADE'),
            ('community_post_media', 'uploaded_media_id', 'uploaded_media', 'CASCADE'),
            ('farming_record_media', 'uploaded_media_id', 'uploaded_media', 'CASCADE'),
            ('member', 'profile_media_id', 'uploaded_media', 'SET NULL'),
            ('community_comment', 'media_id', 'uploaded_media', 'SET NULL'),
            ('community_post', 'farming_record_id', 'farming_record', 'SET NULL'),
            ('voice_record_session', 'draft_record_id', 'farming_record', 'SET NULL'),
            ('farming_cycle_report', 'final_harvest_record_id', 'farming_record', 'SET NULL')
        ) as relationships(child_table, child_column, parent_table, delete_action)
    loop
        if to_regclass('public.' || relation.child_table) is null then
            raise exception 'Missing expected table: %', relation.child_table;
        end if;
        if to_regclass('public.' || relation.parent_table) is null then
            raise exception 'Missing expected table: %', relation.parent_table;
        end if;
        if not exists (
            select 1
            from information_schema.columns
            where table_schema = 'public'
              and table_name = relation.child_table
              and column_name = relation.child_column
        ) then
            raise exception 'Missing expected column: %.%', relation.child_table, relation.child_column;
        end if;

        for existing_constraint in
            select constraint_row.conname
            from pg_constraint constraint_row
            join pg_class child_table on child_table.oid = constraint_row.conrelid
            join pg_namespace child_schema on child_schema.oid = child_table.relnamespace
            join pg_class parent_table on parent_table.oid = constraint_row.confrelid
            join pg_namespace parent_schema on parent_schema.oid = parent_table.relnamespace
            join pg_attribute child_column
              on child_column.attrelid = child_table.oid
             and child_column.attnum = any(constraint_row.conkey)
            where constraint_row.contype = 'f'
              and child_schema.nspname = 'public'
              and parent_schema.nspname = 'public'
              and child_table.relname = relation.child_table
              and parent_table.relname = relation.parent_table
              and child_column.attname = relation.child_column
        loop
            execute format(
                'alter table public.%I drop constraint %I',
                relation.child_table,
                existing_constraint
            );
        end loop;

        execute format(
            'alter table public.%I add constraint %I foreign key (%I) references public.%I(id) on delete %s',
            relation.child_table,
            'fk_' || relation.child_table || '_' || relation.child_column,
            relation.child_column,
            relation.parent_table,
            relation.delete_action
        );
    end loop;
end $$;
