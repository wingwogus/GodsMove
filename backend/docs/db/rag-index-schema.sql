-- Flyway is not installed. Apply this reviewed schema manually to dev/prod.
\set ON_ERROR_STOP on

begin;

create extension if not exists vector;
create extension if not exists hstore;
create extension if not exists "uuid-ossp";
create schema if not exists public;

create table if not exists public.vector_store (
    id uuid default uuid_generate_v4() primary key,
    content text,
    metadata json,
    embedding vector(1024)
);

drop index if exists public.vector_store_embedding_hnsw_idx;

create index if not exists spring_ai_vector_index
    on public.vector_store using hnsw (embedding vector_cosine_ops);

commit;
