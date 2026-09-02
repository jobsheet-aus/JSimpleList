/*
 * Notify every other active member when somebody joins a shared list.
 *
 * Previously notifications.source_invitation_id was globally unique, which
 * allowed only one persistent notification for an invitation. The new model
 * permits one notification per invitation per recipient.
 *
 * accept_list_invitation() remains the authoritative membership boundary.
 * Notification fan-out occurs only after membership has been successfully
 * created/reactivated. The existing notifications trigger creates one durable
 * push_outbox job for each notification row.
 */

alter table jsimplelist.notifications
drop constraint notifications_source_invitation_id_key;


alter table jsimplelist.notifications
add constraint notifications_invitation_recipient_key
unique (
    source_invitation_id,
    recipient_user_id
);


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

    /*
     * Membership acceptance is authoritative and must not depend on
     * notification delivery succeeding. Keep notification fan-out in a
     * subtransaction so a notification/outbox failure rolls back only this
     * block while the accepted membership can still commit.
     */
    begin
        insert into jsimplelist.notifications (
            recipient_user_id,
            event_type,
            list_id,
            actor_user_id,
            source_invitation_id,
            list_name,
            actor_display_name
        )
        select
            membership.user_id,
            'invitation_accepted',
            invitation_record.list_id,
            current_user_id,
            invitation_record.id,
            accepted_list_name,
            actor_display_name
        from jsimplelist.list_members as membership
        where membership.list_id = invitation_record.list_id
          and membership.removed_at is null
          and membership.user_id <> current_user_id
        on conflict (
            source_invitation_id,
            recipient_user_id
        )
        do nothing;
    exception
        when others then
            raise warning
                'Failed to create join notifications for invitation %: %',
                invitation_record.id,
                sqlerrm;
    end;

    return invitation_record.list_id;
end;
$$;


revoke all
on function jsimplelist.accept_list_invitation(uuid)
from public, anon, authenticated;


grant execute
on function jsimplelist.accept_list_invitation(uuid)
to authenticated;
