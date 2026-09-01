create table jsimplelist.push_devices (
    user_id uuid not null
        references auth.users(id) on delete cascade,
    client_instance_id uuid not null,
    fcm_token text not null,
    platform text not null default 'android'
        check (platform = 'android'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),

    primary key (user_id, client_instance_id),
    unique (fcm_token)
);


alter table jsimplelist.push_devices
enable row level security;


revoke all
on table jsimplelist.push_devices
from public, anon, authenticated;


/*
 * Register the current Android installation for push notifications.
 *
 * The authenticated user is always taken from auth.uid(); callers cannot
 * register a device for another account.
 *
 * An FCM token may only belong to one account/installation at a time.
 * Removing an existing row for the token prevents a stale registration
 * from a previous signed-in account continuing to receive notifications.
 */
create or replace function jsimplelist.register_push_device(
    target_client_instance_id uuid,
    target_fcm_token text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    cleaned_fcm_token text;
begin
    current_user_id := auth.uid();
    cleaned_fcm_token := trim(target_fcm_token);

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_client_instance_id is null then
        raise exception 'Client instance ID is required';
    end if;

    if cleaned_fcm_token = '' then
        raise exception 'FCM token is required';
    end if;

    delete from jsimplelist.push_devices
    where fcm_token = cleaned_fcm_token
      and not (
          user_id = current_user_id
          and client_instance_id = target_client_instance_id
      );

    insert into jsimplelist.push_devices (
        user_id,
        client_instance_id,
        fcm_token,
        platform,
        created_at,
        updated_at,
        last_seen_at
    )
    values (
        current_user_id,
        target_client_instance_id,
        cleaned_fcm_token,
        'android',
        now(),
        now(),
        now()
    )
    on conflict (user_id, client_instance_id)
    do update
    set
        fcm_token = excluded.fcm_token,
        platform = excluded.platform,
        updated_at = now(),
        last_seen_at = now();
end;
$$;


revoke all
on function jsimplelist.register_push_device(uuid, text)
from public, anon, authenticated;


grant execute
on function jsimplelist.register_push_device(uuid, text)
to authenticated;


/*
 * Remove only the current authenticated account's registration for this
 * installation. This is called before the Supabase session is destroyed.
 */
create or replace function jsimplelist.unregister_push_device(
    target_client_instance_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
begin
    current_user_id := auth.uid();

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_client_instance_id is null then
        raise exception 'Client instance ID is required';
    end if;

    delete from jsimplelist.push_devices
    where user_id = current_user_id
      and client_instance_id = target_client_instance_id;
end;
$$;


revoke all
on function jsimplelist.unregister_push_device(uuid)
from public, anon, authenticated;


grant execute
on function jsimplelist.unregister_push_device(uuid)
to authenticated;


/*
 * Extend online-account cleanup so application-owned push registrations
 * disappear before the Auth user itself is deleted.
 */
create or replace function jsimplelist.delete_online_account_data()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    current_email text;
begin
    current_user_id := auth.uid();
    current_email := auth.jwt() ->> 'email';

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    /*
     * Permanently remove lists owned by this account.
     *
     * Items belong to lists, not to the individual member who created them.
     * Therefore items in lists owned by somebody else are deliberately
     * untouched when this user deletes their account.
     */
    delete from jsimplelist.items
    where list_id in (
        select id
        from jsimplelist.lists
        where owner_id = current_user_id
    );

    delete from jsimplelist.list_invitations
    where list_id in (
        select id
        from jsimplelist.lists
        where owner_id = current_user_id
    );

    delete from jsimplelist.list_members
    where list_id in (
        select id
        from jsimplelist.lists
        where owner_id = current_user_id
    );

    delete from jsimplelist.lists
    where owner_id = current_user_id;

    /*
     * Remove this account from lists owned by other users.
     * The remaining lists and all of their items remain intact.
     */
    delete from jsimplelist.list_members
    where user_id = current_user_id;

    /*
     * Remove invitations created by this account.
     */
    delete from jsimplelist.list_invitations
    where invited_by = current_user_id;

    /*
     * Remove outstanding invitations addressed to this account's email.
     * This prevents a later account using the same email address from
     * inheriting invitations belonging to the deleted account.
     */
    if current_email is not null and trim(current_email) <> '' then
        delete from jsimplelist.list_invitations
        where lower(invited_email) = lower(current_email)
          and accepted_at is null
          and cancelled_at is null;
    end if;

    /*
     * Explicitly remove push registrations before Auth deletion.
     * The foreign key also has ON DELETE CASCADE as a second safeguard.
     */
    delete from jsimplelist.push_devices
    where user_id = current_user_id;

    /*
     * Remove the application profile explicitly.
     * It also has ON DELETE CASCADE from auth.users, but removing it here
     * leaves the JSimpleList application data clean before Auth deletion.
     */
    delete from jsimplelist.profiles
    where user_id = current_user_id;
end;
$$;


revoke all
on function jsimplelist.delete_online_account_data()
from public, anon, authenticated;


grant execute
on function jsimplelist.delete_online_account_data()
to authenticated;
