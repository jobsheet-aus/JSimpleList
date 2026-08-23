revoke all on table jsimplelist.list_invitations from anon;
revoke all on table jsimplelist.list_invitations from authenticated;

grant select, insert, update
on table jsimplelist.list_invitations
to authenticated;


create policy "list_invitations_select_owner"
on jsimplelist.list_invitations
for select
to authenticated
using (
    (select jsimplelist_private.is_list_owner(list_id))
);


create policy "list_invitations_select_invitee"
on jsimplelist.list_invitations
for select
to authenticated
using (
    accepted_at is null
    and cancelled_at is null
    and lower(invited_email) = lower(
        coalesce((select auth.jwt() ->> 'email'), '')
    )
);


create policy "list_invitations_insert_owner"
on jsimplelist.list_invitations
for insert
to authenticated
with check (
    invited_by = (select auth.uid())
    and
    (select jsimplelist_private.is_list_owner(list_id))
);


create policy "list_invitations_update_owner"
on jsimplelist.list_invitations
for update
to authenticated
using (
    (select jsimplelist_private.is_list_owner(list_id))
)
with check (
    (select jsimplelist_private.is_list_owner(list_id))
);