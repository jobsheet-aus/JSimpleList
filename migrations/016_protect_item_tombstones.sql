drop policy if exists "items_insert_active_members"
on jsimplelist.items;

create policy "items_insert_active_members"
on jsimplelist.items
for insert
to authenticated
with check (
    deleted_at is null
    and (
        (select jsimplelist_private.is_list_owner(list_id))
        or
        (select jsimplelist_private.is_active_list_member(list_id))
    )
);


drop policy if exists "items_update_active_members"
on jsimplelist.items;

create policy "items_update_active_members"
on jsimplelist.items
for update
to authenticated
using (
    deleted_at is null
    and (
        (select jsimplelist_private.is_list_owner(list_id))
        or
        (select jsimplelist_private.is_active_list_member(list_id))
    )
)
with check (
    deleted_at is null
    and (
        (select jsimplelist_private.is_list_owner(list_id))
        or
        (select jsimplelist_private.is_active_list_member(list_id))
    )
);


create or replace function jsimplelist.delete_online_item(
    target_item_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    target_list_id uuid;
begin
    current_user_id := auth.uid();

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_item_id is null then
        raise exception 'Item ID required';
    end if;

    select i.list_id
    into target_list_id
    from jsimplelist.items i
    where i.id = target_item_id;

    if not found then
        return;
    end if;

    if not (
        jsimplelist_private.is_list_owner(target_list_id)
        or jsimplelist_private.is_active_list_member(target_list_id)
    ) then
        raise exception 'Item not found or access denied';
    end if;

    update jsimplelist.items
    set deleted_at = now(),
        updated_at = now()
    where id = target_item_id
      and deleted_at is null;
end;
$$;


revoke all
on function jsimplelist.delete_online_item(uuid)
from public, anon, authenticated;

grant execute
on function jsimplelist.delete_online_item(uuid)
to authenticated;