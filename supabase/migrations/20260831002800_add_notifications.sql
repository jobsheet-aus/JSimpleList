create table jsimplelist.notifications (
    id uuid primary key default gen_random_uuid(),
    recipient_user_id uuid not null
        references auth.users(id) on delete cascade,
    event_type text not null
        check (event_type in ('invitation_accepted')),
    list_id uuid
        references jsimplelist.lists(id) on delete set null,
    actor_user_id uuid
        references auth.users(id) on delete set null,
    source_invitation_id uuid unique
        references jsimplelist.list_invitations(id) on delete set null,
    list_name text not null,
    actor_display_name text not null,
    created_at timestamptz not null default now(),
    seen_at timestamptz
);


create index notifications_recipient_created_idx
on jsimplelist.notifications (
    recipient_user_id,
    created_at desc
);


alter table jsimplelist.notifications enable row level security;


revoke all
on table jsimplelist.notifications
from anon, authenticated;


grant select
on table jsimplelist.notifications
to authenticated;


create policy "notifications_select_recipient"
on jsimplelist.notifications
for select
to authenticated
using (
    recipient_user_id = (select auth.uid())
);


create or replace function jsimplelist.mark_notification_seen(
    target_notification_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    update jsimplelist.notifications
    set seen_at = coalesce(seen_at, now())
    where id = target_notification_id
      and recipient_user_id = auth.uid();

    if not found then
        raise exception 'Notification not found';
    end if;
end;
$$;


revoke all
on function jsimplelist.mark_notification_seen(uuid)
from public, anon, authenticated;


grant execute
on function jsimplelist.mark_notification_seen(uuid)
to authenticated;


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
    accepted_list_name text;
    actor_display_name text;
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

    select name
    into accepted_list_name
    from jsimplelist.lists
    where id = invitation_record.list_id
      and deleted_at is null;

    if not found then
        raise exception 'List is no longer active';
    end if;

    select display_name
    into actor_display_name
    from jsimplelist.profiles
    where user_id = current_user_id;

    actor_display_name := coalesce(
        nullif(trim(actor_display_name), ''),
        left(split_part(current_email, '@', 1) || '@', 50)
    );

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

    insert into jsimplelist.notifications (
        recipient_user_id,
        event_type,
        list_id,
        actor_user_id,
        source_invitation_id,
        list_name,
        actor_display_name
    )
    values (
        invitation_record.invited_by,
        'invitation_accepted',
        invitation_record.list_id,
        current_user_id,
        invitation_record.id,
        accepted_list_name,
        actor_display_name
    )
    on conflict (source_invitation_id)
    do nothing;

    return invitation_record.list_id;
end;
$$;


revoke all
on function jsimplelist.accept_list_invitation(uuid)
from public, anon, authenticated;


grant usage
on schema jsimplelist
to authenticated;


grant execute
on function jsimplelist.accept_list_invitation(uuid)
to authenticated;
