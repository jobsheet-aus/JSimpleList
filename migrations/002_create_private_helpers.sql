create schema if not exists jsimplelist_private;

create or replace function jsimplelist_private.users_share_active_list(
    other_user_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from jsimplelist.list_members me
        join jsimplelist.list_members them
          on them.list_id = me.list_id
        join jsimplelist.lists l
          on l.id = me.list_id
        where me.user_id = (select auth.uid())
          and me.removed_at is null
          and them.user_id = other_user_id
          and them.removed_at is null
          and l.deleted_at is null
    );
$$;

revoke all on function jsimplelist_private.users_share_active_list(uuid) from public;
revoke all on function jsimplelist_private.users_share_active_list(uuid) from anon;
revoke all on function jsimplelist_private.users_share_active_list(uuid) from authenticated;

grant usage on schema jsimplelist_private to authenticated;
grant execute on function jsimplelist_private.users_share_active_list(uuid) to authenticated;