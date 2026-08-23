revoke all on table jsimplelist.profiles from anon;
revoke all on table jsimplelist.profiles from authenticated;

grant select, insert, update
on table jsimplelist.profiles
to authenticated;


create policy "profiles_select_self_or_shared_members"
on jsimplelist.profiles
for select
to authenticated
using (
    user_id = (select auth.uid())
    or
    (select jsimplelist_private.users_share_active_list(user_id))
);


create policy "profiles_insert_self"
on jsimplelist.profiles
for insert
to authenticated
with check (
    user_id = (select auth.uid())
);


create policy "profiles_update_self"
on jsimplelist.profiles
for update
to authenticated
using (
    user_id = (select auth.uid())
)
with check (
    user_id = (select auth.uid())
);