create or replace function jsimplelist.rename_online_list(
    target_list_id uuid,
    target_name text,
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

    if length(trim(target_name)) > 100 then
        raise exception 'List name too long';
    end if;

    if not (
        jsimplelist_private.is_list_owner(target_list_id)
        or jsimplelist_private.is_active_list_member(target_list_id)
    ) then
        raise exception 'List not found or access denied';
    end if;

    operation_time := now();

    update jsimplelist.lists
    set name = trim(target_name),
        updated_at = operation_time,
        origin_client_id = target_origin_client_id
    where id = target_list_id
      and deleted_at is null;

    if not found then
        raise exception 'List not found or access denied';
    end if;

    return operation_time;
end;
$$;

revoke all
on function jsimplelist.rename_online_list(uuid, text, uuid)
from public, anon, authenticated;

grant execute
on function jsimplelist.rename_online_list(uuid, text, uuid)
to authenticated;
