revoke all on table jsimplelist.lists from anon;
revoke all on table jsimplelist.lists from authenticated;

grant select, insert, update
on table jsimplelist.lists
to authenticated;


revoke all on table jsimplelist.list_members from anon;
revoke all on table jsimplelist.list_members from authenticated;

grant select, insert, update
on table jsimplelist.list_members
to authenticated;


create policy "lists_select_active_members"
on jsimplelist.lists
for select
to authenticated
using (
    deleted_at is null
    and (
        owner_id = (select auth.uid())
        or
        (select jsimplelist_private.is_active_list_member(id))
    )
);


create policy "lists_insert_self_as_owner"
on jsimplelist.lists
for insert
to authenticated
with check (
    owner_id = (select auth.uid())
    and deleted_at is null
);


create policy "lists_update_owner"
on jsimplelist.lists
for update
to authenticated
using (
    owner_id = (select auth.uid())
)
with check (
    owner_id = (select auth.uid())
);


create policy "list_members_select_active_list_members"
on jsimplelist.list_members
for select
to authenticated
using (
    (select jsimplelist_private.is_list_owner(list_id))
    or
    (select jsimplelist_private.is_active_list_member(list_id))
);


create policy "list_members_insert_owner"
on jsimplelist.list_members
for insert
to authenticated
with check (
    (select jsimplelist_private.is_list_owner(list_id))
);


create policy "list_members_update_owner"
on jsimplelist.list_members
for update
to authenticated
using (
    (select jsimplelist_private.is_list_owner(list_id))
)
with check (
    (select jsimplelist_private.is_list_owner(list_id))
);