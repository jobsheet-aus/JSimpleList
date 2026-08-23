revoke all on table jsimplelist.items from anon;
revoke all on table jsimplelist.items from authenticated;

grant select, insert, update
on table jsimplelist.items
to authenticated;


create policy "items_select_active_members"
on jsimplelist.items
for select
to authenticated
using (
    deleted_at is null
    and (
        (select jsimplelist_private.is_list_owner(list_id))
        or
        (select jsimplelist_private.is_active_list_member(list_id))
    )
);


create policy "items_insert_active_members"
on jsimplelist.items
for insert
to authenticated
with check (
    (select jsimplelist_private.is_list_owner(list_id))
    or
    (select jsimplelist_private.is_active_list_member(list_id))
);


create policy "items_update_active_members"
on jsimplelist.items
for update
to authenticated
using (
    (select jsimplelist_private.is_list_owner(list_id))
    or
    (select jsimplelist_private.is_active_list_member(list_id))
)
with check (
    (select jsimplelist_private.is_list_owner(list_id))
    or
    (select jsimplelist_private.is_active_list_member(list_id))
);