create or replace function jsimplelist.set_my_list_position(
    target_list_id uuid,
    target_position integer
)
returns void
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

    update jsimplelist.list_members
    set position = target_position
    where list_id = target_list_id
      and user_id = current_user_id
      and removed_at is null;

    if not found then
        raise exception 'Active list membership not found';
    end if;
end;
$$;


revoke all
on function jsimplelist.set_my_list_position(uuid, integer)
from public, anon, authenticated;

grant execute
on function jsimplelist.set_my_list_position(uuid, integer)
to authenticated;