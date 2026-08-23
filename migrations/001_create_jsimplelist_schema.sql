create schema if not exists jsimplelist;

create table jsimplelist.profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null check (length(trim(display_name)) between 1 and 50),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table jsimplelist.lists (
    id uuid primary key,
    owner_id uuid not null references auth.users(id),
    name text not null check (length(trim(name)) between 1 and 100),
    kind text not null check (kind in ('TODO', 'SHOPPING', 'DISCUSSION')),
    position integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz
);

create table jsimplelist.list_members (
    list_id uuid not null references jsimplelist.lists(id),
    user_id uuid not null references auth.users(id),
    role text not null check (role in ('owner', 'member')),
    joined_at timestamptz not null default now(),
    removed_at timestamptz,
    last_seen_at timestamptz,
    primary key (list_id, user_id)
);

create table jsimplelist.list_invitations (
    id uuid primary key default gen_random_uuid(),
    list_id uuid not null references jsimplelist.lists(id),
    invited_email text not null,
    invited_by uuid not null references auth.users(id),
    role text not null default 'member' check (role in ('member')),
    created_at timestamptz not null default now(),
    accepted_at timestamptz,
    cancelled_at timestamptz
);

create table jsimplelist.items (
    id uuid primary key,
    list_id uuid not null references jsimplelist.lists(id),
    description text not null,
    quantity integer,
    completed boolean not null,
    position integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz,
    check (quantity is null or quantity > 0)
);

create index lists_owner_id_idx
    on jsimplelist.lists(owner_id);

create index list_members_user_id_idx
    on jsimplelist.list_members(user_id);

create index list_invitations_email_idx
    on jsimplelist.list_invitations(lower(invited_email));

create index items_list_id_idx
    on jsimplelist.items(list_id);

alter table jsimplelist.profiles enable row level security;
alter table jsimplelist.lists enable row level security;
alter table jsimplelist.list_members enable row level security;
alter table jsimplelist.list_invitations enable row level security;
alter table jsimplelist.items enable row level security;