-- Community comments may attach at most one uploaded image.
-- Flyway is not installed in this backend yet; apply manually to dev/prod schemas.

alter table community_comment
    add column if not exists media_id uuid;

alter table community_comment
    add constraint fk_community_comment_media
        foreign key (media_id)
        references uploaded_media(id);

create index if not exists idx_community_comment_post_parent_created_id
    on community_comment (post_id, parent_comment_id, created_at desc, id desc);
