create function jsimplelist.delete_all_online_items(
    target_list_id uuid,
    target_origin_client_id uuid
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    affected_count integer;
    operation_time timestamptz;
begin
    current_user_id := auth.uid();

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_list_id is null then
        raise exception 'List ID required';
    end if;

    if not (
        jsimplelist_private.is_list_owner(target_list_id)
        or jsimplelist_private.is_active_list_member(target_list_id)
    ) then
        raise exception 'List not found or access denied';
    end if;

    operation_time := now();

    update jsimplelist.items
    set deleted_at = operation_time,
        updated_at = operation_time,
        origin_client_id = target_origin_client_id
    where list_id = target_list_id
      and deleted_at is null;

    get diagnostics affected_count = row_count;

    return affected_count;
end;
$$;


revoke all
on function jsimplelist.delete_all_online_items(uuid, uuid)
from public, anon, authenticated;

grant execute
on function jsimplelist.delete_all_online_items(uuid, uuid)
to authenticated;
