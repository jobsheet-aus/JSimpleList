/*
 * Invitation lifecycle hardening.
 *
 * Goals:
 * - stale invitations must not survive list deletion
 * - recipients must only see invitations for active lists
 * - pending invitations must carry enough metadata for a useful UI
 * - recipients may explicitly decline
 * - owners receive persistent accepted/declined notifications
 */


/*
 * First remove stale active invitations left behind by lists that were
 * deleted before invitation cleanup existed.
 */
update jsimplelist.list_invitations as invitation
set cancelled_at = now()
from jsimplelist.lists as list_record
where list_record.id = invitation.list_id
  and list_record.deleted_at is not null
  and invitation.accepted_at is null
  and invitation.cancelled_at is null;


/*
 * Future list deletion automatically cancels every still-pending
 * invitation for that list.
 *
 * This is attached to the state transition rather than one particular
 * delete RPC so future deletion paths cannot accidentally leave stale
 * invitations behind.
 */
create or replace function jsimplelist.cancel_invitations_for_deleted_list()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if old.deleted_at is null
       and new.deleted_at is not null then

        update jsimplelist.list_invitations
        set cancelled_at = new.deleted_at
        where list_id = new.id
          and accepted_at is null
          and cancelled_at is null;
    end if;

    return new;
end;
$$;


revoke all
on function jsimplelist.cancel_invitations_for_deleted_list()
from public, anon, authenticated;


drop trigger if exists lists_cancel_pending_invitations
on jsimplelist.lists;


create trigger lists_cancel_pending_invitations
after update of deleted_at
on jsimplelist.lists
for each row
execute function jsimplelist.cancel_invitations_for_deleted_list();


/*
 * Replace every previous invitation for one list/email pair with exactly
 * one fresh invitation.
 *
 * Old invitation-derived persistent notifications are deliberately deleted
 * first. push_outbox rows reference notifications with ON DELETE CASCADE,
 * so obsolete push-delivery history disappears with them.
 *
 * If the invited email currently belongs to an active member of the list,
 * no history is removed and the new invitation is rejected.
 */
create or replace function jsimplelist.replace_list_invitation(
    target_list_id uuid,
    target_email text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    cleaned_email text;
    invited_user_id uuid;
    new_invitation_id uuid;
begin
    current_user_id := auth.uid();
    cleaned_email := lower(trim(target_email));

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_list_id is null then
        raise exception 'List ID required';
    end if;

    if cleaned_email is null or cleaned_email = '' then
        raise exception 'Email address required';
    end if;

    /*
     * Lock the list row so simultaneous invitation attempts for the same
     * list cannot interleave their cleanup/create operations.
     */
    perform 1
    from jsimplelist.lists
    where id = target_list_id
      and owner_id = current_user_id
      and deleted_at is null
    for update;

    if not found then
        raise exception 'List not found or access denied';
    end if;

    /*
     * Resolve an existing Auth user by email only inside this
     * SECURITY DEFINER function.
     */
    select id
    into invited_user_id
    from auth.users
    where lower(trim(email)) = cleaned_email
    order by created_at asc
    limit 1;

    /*
     * Do not generate another invitation for somebody who is already an
     * active member of this list.
     */
    if invited_user_id is not null
       and exists (
            select 1
            from jsimplelist.list_members
            where list_id = target_list_id
              and user_id = invited_user_id
              and removed_at is null
       ) then
        raise exception 'This person is already a member of the list';
    end if;

    /*
     * Remove persistent events sourced from every older invitation for this
     * exact list/email pair. Their push_outbox rows cascade automatically.
     */
    delete from jsimplelist.notifications
    where source_invitation_id in (
        select invitation.id
        from jsimplelist.list_invitations as invitation
        where invitation.list_id = target_list_id
          and lower(trim(invitation.invited_email)) = cleaned_email
    );

    /*
     * Remove every previous invitation state: pending, accepted, declined
     * or otherwise cancelled. The new invitation becomes the sole current
     * invitation history for this list/email pair.
     */
    delete from jsimplelist.list_invitations
    where list_id = target_list_id
      and lower(trim(invited_email)) = cleaned_email;

    insert into jsimplelist.list_invitations (
        list_id,
        invited_email,
        invited_by,
        role
    )
    values (
        target_list_id,
        cleaned_email,
        current_user_id,
        'member'
    )
    returning id
    into new_invitation_id;

    return new_invitation_id;
end;
$$;


revoke all
on function jsimplelist.replace_list_invitation(uuid, text)
from public, anon, authenticated;


grant execute
on function jsimplelist.replace_list_invitation(uuid, text)
to authenticated;



/*
 * Return only usable pending invitations for the currently authenticated
 * email address.
 *
 * The Android app no longer needs to query raw list_invitations rows and
 * then guess whether the associated list is still valid.
 */
create or replace function jsimplelist.get_my_pending_invitations()
returns table (
    id uuid,
    list_id uuid,
    invited_by uuid,
    role text,
    list_name text,
    list_kind text,
    inviter_display_name text
)
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
    current_email text;
begin
    current_email :=
        lower(trim(auth.jwt() ->> 'email'));

    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    if current_email is null or current_email = '' then
        raise exception 'Authenticated email required';
    end if;

    return query
    select
        invitation.id,
        invitation.list_id,
        invitation.invited_by,
        invitation.role,
        list_record.name,
        list_record.kind,
        coalesce(
            nullif(trim(profile.display_name), ''),
            case
                when inviter_auth.email is not null
                     and trim(inviter_auth.email) <> ''
                then left(
                    split_part(
                        trim(inviter_auth.email),
                        '@',
                        1
                    ) || '@',
                    50
                )
                else 'JSimpleList user'
            end
        ) as inviter_display_name
    from jsimplelist.list_invitations as invitation
    join jsimplelist.lists as list_record
      on list_record.id = invitation.list_id
     and list_record.deleted_at is null
    left join jsimplelist.profiles as profile
      on profile.user_id = invitation.invited_by
    left join auth.users as inviter_auth
      on inviter_auth.id = invitation.invited_by
    where invitation.accepted_at is null
      and invitation.cancelled_at is null
      and lower(trim(invitation.invited_email)) = current_email
    order by invitation.created_at asc;
end;
$$;


revoke all
on function jsimplelist.get_my_pending_invitations()
from public, anon, authenticated;


grant execute
on function jsimplelist.get_my_pending_invitations()
to authenticated;


/*
 * Persistent notifications now cover both invitation outcomes.
 */
alter table jsimplelist.notifications
drop constraint notifications_event_type_check;


alter table jsimplelist.notifications
add constraint notifications_event_type_check
check (
    event_type in (
        'invitation_accepted',
        'invitation_declined'
    )
);


/*
 * The durable push outbox mirrors supported persistent notification types.
 */
alter table jsimplelist.push_outbox
drop constraint push_outbox_event_type_check;


alter table jsimplelist.push_outbox
add constraint push_outbox_event_type_check
check (
    event_type in (
        'invitation_accepted',
        'invitation_declined'
    )
);


/*
 * Decline a currently active invitation belonging to the authenticated
 * email address.
 *
 * The decline and persistent notification are committed atomically.
 * The existing notifications trigger then creates the push-outbox row in
 * that same transaction.
 */
create or replace function jsimplelist.decline_list_invitation(
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
    declined_list_name text;
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

    if lower(trim(invitation_record.invited_email))
       <> lower(trim(current_email)) then
        raise exception 'Invitation does not belong to authenticated user';
    end if;

    select name
    into declined_list_name
    from jsimplelist.lists
    where id = invitation_record.list_id
      and deleted_at is null;

    if not found then
        /*
         * The list disappeared after the invitation was loaded.
         * Cancel the obsolete invitation and return normally so the cleanup
         * commits instead of being rolled back by an exception.
         */
        update jsimplelist.list_invitations
        set cancelled_at = now()
        where id = invitation_record.id;

        return invitation_record.list_id;
    end if;

    select display_name
    into actor_display_name
    from jsimplelist.profiles
    where user_id = current_user_id;

    actor_display_name := coalesce(
        nullif(trim(actor_display_name), ''),
        left(
            split_part(current_email, '@', 1) || '@',
            50
        )
    );

    update jsimplelist.list_invitations
    set cancelled_at = now()
    where id = invitation_record.id;

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
        'invitation_declined',
        invitation_record.list_id,
        current_user_id,
        invitation_record.id,
        declined_list_name,
        actor_display_name
    )
    on conflict (source_invitation_id)
    do nothing;

    return invitation_record.list_id;
end;
$$;


revoke all
on function jsimplelist.decline_list_invitation(uuid)
from public, anon, authenticated;


grant execute
on function jsimplelist.decline_list_invitation(uuid)
to authenticated;


/*
 * Enqueue both accepted and declined persistent notifications.
 */
create or replace function jsimplelist.enqueue_notification_push()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.event_type in (
        'invitation_accepted',
        'invitation_declined'
    ) then
        insert into jsimplelist.push_outbox (
            notification_id,
            recipient_user_id,
            event_type,
            list_id,
            actor_user_id,
            source_invitation_id,
            list_name,
            actor_display_name
        )
        values (
            new.id,
            new.recipient_user_id,
            new.event_type,
            new.list_id,
            new.actor_user_id,
            new.source_invitation_id,
            new.list_name,
            new.actor_display_name
        )
        on conflict (notification_id)
        do nothing;
    end if;

    return new;
end;
$$;


revoke all
on function jsimplelist.enqueue_notification_push()
from public, anon, authenticated;
