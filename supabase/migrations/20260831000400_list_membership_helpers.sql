create or replace function jsimplelist_private.is_list_owner(
    target_list_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from jsimplelist.lists l
        where l.id = target_list_id
          and l.owner_id = (select auth.uid())
          and l.deleted_at is null
    );
$$;


create or replace function jsimplelist_private.is_active_list_member(
    target_list_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from jsimplelist.list_members lm
        join jsimplelist.lists l
          on l.id = lm.list_id
        where lm.list_id = target_list_id
          and lm.user_id = (select auth.uid())
          and lm.removed_at is null
          and l.deleted_at is null
    );
$$;


revoke all
on function jsimplelist_private.is_list_owner(uuid)
from public, anon, authenticated;

revoke all
on function jsimplelist_private.is_active_list_member(uuid)
from public, anon, authenticated;

grant execute
on function jsimplelist_private.is_list_owner(uuid)
to authenticated;

grant execute
on function jsimplelist_private.is_active_list_member(uuid)
to authenticated;