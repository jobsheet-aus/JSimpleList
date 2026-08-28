create or replace function jsimplelist.delete_online_list(
    target_list_id uuid,
    target_origin_client_id uuid
)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    operation_time timestamptz;
    existing_deleted_at timestamptz;
begin
    current_user_id := auth.uid();

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_list_id is null then
        raise exception 'List ID required';
    end if;

    if not jsimplelist_private.is_list_owner(target_list_id) then
        select deleted_at
        into existing_deleted_at
        from jsimplelist.lists
        where id = target_list_id
          and owner_id = current_user_id;

        if existing_deleted_at is not null then
            return existing_deleted_at;
        end if;

        raise exception 'List not found or access denied';
    end if;

    operation_time := now();

    update jsimplelist.lists
    set deleted_at = operation_time,
        updated_at = operation_time,
        origin_client_id = target_origin_client_id
    where id = target_list_id
      and deleted_at is null;

    if not found then
        select deleted_at
        into existing_deleted_at
        from jsimplelist.lists
        where id = target_list_id
          and owner_id = current_user_id;

        if existing_deleted_at is not null then
            return existing_deleted_at;
        end if;

        raise exception 'List not found or access denied';
    end if;

    return operation_time;
end;
$$;


revoke all
on function jsimplelist.delete_online_list(uuid, uuid)
from public, anon, authenticated;

grant execute
on function jsimplelist.delete_online_list(uuid, uuid)
to authenticated;


create or replace function jsimplelist.leave_online_list(
    target_list_id uuid
)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    operation_time timestamptz;
    existing_removed_at timestamptz;
begin
    current_user_id := auth.uid();

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_list_id is null then
        raise exception 'List ID required';
    end if;

    if jsimplelist_private.is_list_owner(target_list_id) then
        raise exception 'List owner cannot leave own list';
    end if;

    select removed_at
    into existing_removed_at
    from jsimplelist.list_members
    where list_id = target_list_id
      and user_id = current_user_id;

    if not found then
        raise exception 'List not found or access denied';
    end if;

    if existing_removed_at is not null then
        return existing_removed_at;
    end if;

    operation_time := now();

    update jsimplelist.list_members
    set removed_at = operation_time
    where list_id = target_list_id
      and user_id = current_user_id
      and removed_at is null;

    return operation_time;
end;
$$;


revoke all
on function jsimplelist.leave_online_list(uuid)
from public, anon, authenticated;

grant execute
on function jsimplelist.leave_online_list(uuid)
to authenticated;
