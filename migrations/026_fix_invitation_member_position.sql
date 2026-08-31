create or replace function jsimplelist.accept_list_invitation(
    target_invitation_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    invitation_record jsimplelist.list_invitations%rowtype;
    current_user_id uuid;
    current_email text;
    next_position integer;
begin
    current_user_id := auth.uid();
    current_email := auth.jwt() ->> 'email';

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if current_email is null or trim(current_email) = '' then
        raise exception 'Authenticated email required';
    end if;

    select *
    into invitation_record
    from jsimplelist.list_invitations
    where id = target_invitation_id
      and accepted_at is null
      and cancelled_at is null
    for update;

    if not found then
        raise exception 'Invitation not found or no longer active';
    end if;

    if lower(invitation_record.invited_email) <> lower(current_email) then
        raise exception 'Invitation does not belong to authenticated user';
    end if;

    if not exists (
        select 1
        from jsimplelist.lists
        where id = invitation_record.list_id
          and deleted_at is null
    ) then
        raise exception 'List is no longer active';
    end if;

    select coalesce(max(position), -1) + 1
    into next_position
    from jsimplelist.list_members
    where user_id = current_user_id
      and removed_at is null;

    insert into jsimplelist.list_members (
        list_id,
        user_id,
        role,
        position,
        joined_at,
        removed_at,
        last_seen_at
    )
    values (
        invitation_record.list_id,
        current_user_id,
        invitation_record.role,
        next_position,
        now(),
        null,
        null
    )
    on conflict (list_id, user_id)
    do update set
        role = excluded.role,
        position = excluded.position,
        joined_at = excluded.joined_at,
        removed_at = null;

    update jsimplelist.list_invitations
    set accepted_at = now()
    where id = target_invitation_id;

    return invitation_record.list_id;
end;
$$;


revoke all
on function jsimplelist.accept_list_invitation(uuid)
from public, anon, authenticated;


grant usage on schema jsimplelist to authenticated;


grant execute
on function jsimplelist.accept_list_invitation(uuid)
to authenticated;
