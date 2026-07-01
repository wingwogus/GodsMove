create extension if not exists pgcrypto;
create extension if not exists vector;

create table if not exists rag_index_chunk (
  id uuid primary key default gen_random_uuid(),
  source_type varchar(32) not null check (source_type in ('TECH_DOCUMENT', 'FARMING_RECORD')),
  source_id varchar(128) not null,
  member_id uuid,
  farm_id uuid,
  crop_id uuid,
  work_type_id uuid,
  record_id uuid,
  worked_at timestamptz,
  chunk_index integer not null,
  content text not null,
  content_hash varchar(64) not null,
  embedding vector(1024) not null,
  embedding_model varchar(128) not null,
  metadata jsonb not null default '{}'::jsonb,
  indexed_at timestamptz not null default now(),
  unique (source_type, source_id, chunk_index, embedding_model)
);

create index if not exists rag_index_chunk_member_idx
  on rag_index_chunk (member_id);

create index if not exists rag_index_chunk_filter_idx
  on rag_index_chunk (source_type, member_id, farm_id, crop_id, work_type_id, record_id, worked_at);

create index if not exists rag_index_chunk_content_hash_idx
  on rag_index_chunk (content_hash);

create index if not exists rag_index_chunk_embedding_hnsw_idx
  on rag_index_chunk using hnsw (embedding vector_cosine_ops);
