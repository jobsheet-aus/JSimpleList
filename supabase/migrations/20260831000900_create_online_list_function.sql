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

    insert into jsimplelist.lists (
        id,
        owner_id,
        name,
        kind,
        position,
        created_at,
        updated_at,
        deleted_at
    )
    values (
        target_list_id,
        current_user_id,
        trim(target_name),
        target_kind,
        target_position,
        target_created_at,
        target_updated_at,
        null
    );

    insert into jsimplelist.list_members (
        list_id,
        user_id,
        role,
        joined_at,
        removed_at,
        last_seen_at
    )
    values (
        target_list_id,
        current_user_id,
        'owner',
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