create or replace function jsimplelist.create_online_list(
    target_list_id uuid,
    target_name text,
    target_kind text,
    target_position integer,
    target_created_at timestamptz,
    target_updated_at timestamptz
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    existing_owner_id uuid;
    existing_deleted_at timestamptz;
begin
    current_user_id := auth.uid();

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_list_id is null then
        raise exception 'List ID required';
    end if;

    if trim(target_name) = '' then
        raise exception 'List name required';
    end if;

    if target_kind not in ('TODO', 'SHOPPING', 'DISCUSSION') then
        raise exception 'Invalid list kind';
    end if;

    select owner_id, deleted_at
    into existing_owner_id, existing_deleted_at
    from jsimplelist.lists
    where id = target_list_id;

    if found then
        if existing_owner_id <> current_user_id then
            raise exception 'List ID already belongs to another owner';
        end if;

        if existing_deleted_at is not null then
            raise exception 'List has been deleted';
        end if;

        update jsimplelist.lists
        set name = trim(target_name),
            kind = target_kind,
            updated_at = target_updated_at
        where id = target_list_id;

        insert into jsimplelist.list_members (
            list_id,
            user_id,
            role,
            position,
            joined_at,
            removed_at,
            last_seen_at
        )
        values (
            target_list_id,
            current_user_id,
            'owner',
            target_position,
            now(),
            null,
            null
        )
        on conflict (list_id, user_id)
        do update
        set role = 'owner',
            position = excluded.position,
            removed_at = null;

        return target_list_id;
    end if;

    insert into jsimplelist.lists (
        id,
        owner_id,
        name,
        kind,
        created_at,
        updated_at,
        deleted_at
    )
    values (
        target_list_id,
        current_user_id,
        trim(target_name),
        target_kind,
        target_created_at,
        target_updated_at,
        null
    );

    insert into jsimplelist.list_members (
        list_id,
        user_id,
        role,
        position,
        joined_at,
        removed_at,
        last_seen_at
    )
    values (
        target_list_id,
        current_user_id,
        'owner',
        target_position,
        now(),
        null,
        null
    );

    return target_list_id;
end;
$$;


revoke all
on function jsimplelist.create_online_list(
    uuid,
    text,
    text,
    integer,
    timestamptz,
    timestamptz
)
from public, anon, authenticated;

grant execute
on function jsimplelist.create_online_list(
    uuid,
    text,
    text,
    integer,
    timestamptz,
    timestamptz
)
to authenticated;
