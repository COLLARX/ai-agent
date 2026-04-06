create extension if not exists vector;

create table if not exists love_app_conversation (
    id bigserial primary key,
    conversation_id varchar(128) not null unique,
    title varchar(255),
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

alter table love_app_conversation
    add column if not exists user_id varchar(64);

create table if not exists app_user (
    id varchar(64) primary key,
    username varchar(128) not null unique,
    password_hash varchar(255) not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

alter table love_app_conversation
    drop constraint if exists fk_love_app_conversation_user;

alter table love_app_conversation
    add constraint fk_love_app_conversation_user
    foreign key (user_id)
    references app_user (id);

create table if not exists love_app_message (
    id bigserial primary key,
    conversation_id varchar(128) not null,
    role varchar(32) not null,
    content text not null,
    sequence_no integer not null,
    created_at timestamp with time zone not null default now(),
    constraint fk_love_app_message_conversation
        foreign key (conversation_id)
        references love_app_conversation (conversation_id)
        on delete cascade
);

create index if not exists idx_love_app_message_conversation_id
    on love_app_message (conversation_id);

create table if not exists manus_conversation (
    id bigserial primary key,
    conversation_id varchar(128) not null unique,
    user_id varchar(128) not null,
    title varchar(255),
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create table if not exists manus_message (
    id bigserial primary key,
    conversation_id varchar(128) not null,
    role varchar(32) not null,
    content text not null,
    sequence_no integer not null,
    created_at timestamp with time zone not null default now(),
    constraint fk_manus_message_conversation
        foreign key (conversation_id)
        references manus_conversation (conversation_id)
        on delete cascade
);

create index if not exists idx_manus_message_conversation_id
    on manus_message (conversation_id);

create table if not exists manus_private_vector_store (
    id uuid primary key,
    content text,
    metadata json,
    embedding vector(1536)
);

create index if not exists idx_manus_private_vector_store_embedding
    on manus_private_vector_store
    using hnsw (embedding vector_cosine_ops);
