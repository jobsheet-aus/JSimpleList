/*
 * Let the owner of an active list identify current members by their
 * verified Supabase Auth email addresses.
 *
 * Ordinary members cannot query this function successfully, and no
 * email addresses are added to the generally readable profiles table.
 */
create or replace function jsimplelist.get_owner_list_member_emails(
    target_list_id uuid
)
returns table (
    user_id uuid,
    email text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    if not exists (
        select 1
        from jsimplelist.lists as list_record
        where list_record.id = target_list_id
          and list_record.owner_id = auth.uid()
          and list_record.deleted_at is null
    ) then
        raise exception 'List not found or access denied';
    end if;

    return query
    select
        member.user_id,
        auth_user.email::text
    from jsimplelist.list_members as member
    join auth.users as auth_user
      on auth_user.id = member.user_id
    where member.list_id = target_list_id
      and member.removed_at is null
    order by member.user_id;
end;
$$;

revoke all
on function jsimplelist.get_owner_list_member_emails(uuid)
from public, anon, authenticated;

grant execute
on function jsimplelist.get_owner_list_member_emails(uuid)
to authenticated;
