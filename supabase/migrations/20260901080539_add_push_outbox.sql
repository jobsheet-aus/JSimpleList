/*
 * Durable push-delivery outbox.
 *
 * Persistent notifications remain the authoritative application event.
 * An AFTER INSERT trigger creates a matching push job in the same database
 * transaction. Firebase delivery happens separately and can therefore fail
 * or retry without affecting invitation acceptance.
 */

create table jsimplelist.push_outbox (
    id uuid primary key default gen_random_uuid(),

    notification_id uuid not null unique
        references jsimplelist.notifications(id) on delete cascade,

    recipient_user_id uuid not null
        references auth.users(id) on delete cascade,

    event_type text not null
        check (event_type in ('invitation_accepted')),

    list_id uuid
        references jsimplelist.lists(id) on delete set null,

    actor_user_id uuid
        references auth.users(id) on delete set null,

    source_invitation_id uuid
        references jsimplelist.list_invitations(id) on delete set null,

    list_name text not null,
    actor_display_name text not null,

    created_at timestamptz not null default now(),

    attempt_count integer not null default 0
        check (attempt_count >= 0),

    last_attempt_at timestamptz,
    next_attempt_at timestamptz not null default now(),

    sent_at timestamptz,
    last_error text
);


create index push_outbox_pending_idx
on jsimplelist.push_outbox (
    next_attempt_at,
    created_at
)
where sent_at is null;


alter table jsimplelist.push_outbox
enable row level security;


revoke all
on table jsimplelist.push_outbox
from public, anon, authenticated;


/*
 * Only the server-side push worker needs to inspect and update delivery
 * state. Normal Android clients receive notifications through FCM and the
 * existing recipient-only notifications table.
 */
grant select, update
on table jsimplelist.push_outbox
to service_role;


/*
 * Create a push job whenever a supported persistent notification is
 * actually inserted.
 *
 * This trigger runs inside the same transaction as the notification insert,
 * so either both records commit or neither does.
 */
create or replace function jsimplelist.enqueue_notification_push()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.event_type = 'invitation_accepted' then
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


drop trigger if exists notifications_enqueue_push
on jsimplelist.notifications;


create trigger notifications_enqueue_push
after insert
on jsimplelist.notifications
for each row
execute function jsimplelist.enqueue_notification_push();
