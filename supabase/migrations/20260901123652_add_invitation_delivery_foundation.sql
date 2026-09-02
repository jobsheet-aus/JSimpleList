/*
 * First-class invitation delivery foundation.
 *
 * Product invitation authority remains jsimplelist.list_invitations.
 * This migration adds:
 *
 * - server-only recipient maturity classification based on JSimpleList-owned
 *   evidence rather than raw Auth existence
 * - one delivery-state row for each active invitation
 * - automatic removal of pending delivery state whenever an invitation is
 *   accepted, declined/cancelled, superseded or deleted
 *
 * A raw auth.users row is deliberately not enough to classify somebody as
 * an established JSimpleList user. A green recipient may already have an
 * Auth row because of an earlier authentication/bootstrap attempt.
 */


/*
 * Server-side recipient classification.
 *
 * ACTIVE_ANDROID
 *     At least one registered Android installation exists for this
 *     normalised email address.
 *
 * KNOWN_USER
 *     No registered Android installation exists, but the Auth identity has
 *     a JSimpleList profile. The profile is durable evidence that this
 *     identity has actually entered JSimpleList.
 *
 * GREEN
 *     No registered Android installation and no JSimpleList profile.
 *
 * auth_user_id is also returned when a raw Auth identity already exists.
 * That fact does not itself affect maturity but is useful to the later
 * onboarding/bootstrap implementation.
 */
create or replace function jsimplelist.classify_invitation_recipient(
    target_email text
)
returns table (
    recipient_maturity text,
    auth_user_id uuid,
    push_device_count integer
)
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
    cleaned_email text;
    resolved_auth_user_id uuid;
    registered_device_count integer;
begin
    cleaned_email :=
        lower(trim(target_email));

    if cleaned_email is null
       or cleaned_email = '' then
        raise exception 'Email address required';
    end if;

    /*
     * Resolve the oldest matching Auth identity only as identity context.
     * Auth existence alone is deliberately not treated as product usage.
     */
    select auth_user.id
    into resolved_auth_user_id
    from auth.users as auth_user
    where lower(trim(auth_user.email)) = cleaned_email
    order by auth_user.created_at asc
    limit 1;

    /*
     * A registered Android installation is our strongest evidence that the
     * recipient is currently equipped for the push fast path.
     */
    select count(*)::integer
    into registered_device_count
    from jsimplelist.push_devices
    where user_email = cleaned_email;

    if registered_device_count > 0 then
        return query
        select
            'ACTIVE_ANDROID'::text,
            resolved_auth_user_id,
            registered_device_count;

        return;
    end if;

    /*
     * A profile is durable JSimpleList application evidence even when no
     * currently registered Android installation is available.
     */
    if resolved_auth_user_id is not null
       and exists (
            select 1
            from jsimplelist.profiles
            where user_id = resolved_auth_user_id
       ) then

        return query
        select
            'KNOWN_USER'::text,
            resolved_auth_user_id,
            0;

        return;
    end if;

    return query
    select
        'GREEN'::text,
        resolved_auth_user_id,
        0;
end;
$$;


revoke all
on function jsimplelist.classify_invitation_recipient(text)
from public, anon, authenticated;


grant execute
on function jsimplelist.classify_invitation_recipient(text)
to service_role;


/*
 * Delivery state belongs to one authoritative invitation lifecycle.
 *
 * It is intentionally separate from list membership and from Supabase Auth.
 * Removing this row does not grant/revoke membership; it merely stops work
 * whose purpose was to deliver an invitation that is no longer pending.
 *
 * Future onboarding handoff credentials should reference this invitation
 * lifecycle rather than becoming a second product invitation authority.
 */
create table jsimplelist.invitation_delivery (
    invitation_id uuid primary key
        references jsimplelist.list_invitations(id)
        on delete cascade,

    recipient_email text not null,

    recipient_maturity text not null
        check (
            recipient_maturity in (
                'ACTIVE_ANDROID',
                'KNOWN_USER',
                'GREEN'
            )
        ),

    immediate_channel text not null
        check (
            immediate_channel in (
                'PUSH',
                'NONE',
                'ONBOARDING_EMAIL'
            )
        ),

    /*
     * Delivery timestamps describe notification work only.
     * They do not determine whether the invitation itself is active.
     */
    push_attempted_at timestamptz,
    push_sent_at timestamptz,
    onboarding_email_sent_at timestamptz,

    /*
     * ACTIVE_ANDROID and KNOWN_USER invitations may later receive a delayed
     * reminder if the authoritative invitation remains pending.
     */
    reminder_due_at timestamptz,
    reminder_sent_at timestamptz,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    check (
        recipient_email = lower(trim(recipient_email))
        and recipient_email <> ''
    )
);


create index invitation_delivery_reminder_due_idx
on jsimplelist.invitation_delivery (reminder_due_at)
where reminder_due_at is not null
  and reminder_sent_at is null;


alter table jsimplelist.invitation_delivery
enable row level security;


revoke all
on table jsimplelist.invitation_delivery
from public, anon, authenticated;


/*
 * invite-list and future server delivery workers are the only normal writers.
 */
grant select, insert, update, delete
on table jsimplelist.invitation_delivery
to service_role;


/*
 * Any transition from pending invitation -> resolved invitation immediately
 * destroys its outstanding delivery state.
 *
 * Acceptance therefore cancels a delayed reminder/onboarding lifecycle in
 * the same database transaction that grants membership.
 *
 * Decline and list-deletion cancellation receive the same treatment because
 * both set cancelled_at.
 *
 * Superseding reinvites hard-delete the old list_invitations row; the
 * invitation_delivery foreign key ON DELETE CASCADE handles that case.
 */
create or replace function jsimplelist.cleanup_resolved_invitation_delivery()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if (
        old.accepted_at is null
        and new.accepted_at is not null
    )
    or (
        old.cancelled_at is null
        and new.cancelled_at is not null
    ) then
        delete from jsimplelist.invitation_delivery
        where invitation_id = new.id;
    end if;

    return new;
end;
$$;


revoke all
on function jsimplelist.cleanup_resolved_invitation_delivery()
from public, anon, authenticated;


drop trigger if exists list_invitations_cleanup_delivery
on jsimplelist.list_invitations;


create trigger list_invitations_cleanup_delivery
after update of accepted_at, cancelled_at
on jsimplelist.list_invitations
for each row
execute function jsimplelist.cleanup_resolved_invitation_delivery();
