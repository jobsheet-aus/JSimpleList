/*
 * Store a normalised email snapshot with each registered installation.
 *
 * Android never supplies this value. register_push_device() derives it
 * from the authenticated JWT, preventing a client from claiming another
 * person's email address.
 *
 * The server-side invitation function can later use this mapping to find
 * registered installations for an invited email address without exposing
 * push registrations to normal clients.
 */

alter table jsimplelist.push_devices
add column user_email text;


/*
 * Preserve any development registrations that already exist.
 */
update jsimplelist.push_devices as push_device
set user_email = lower(trim(auth_user.email))
from auth.users as auth_user
where auth_user.id = push_device.user_id
  and auth_user.email is not null
  and trim(auth_user.email) <> '';


/*
 * A usable push registration must correspond to an Auth account with an
 * email address.
 */
delete from jsimplelist.push_devices
where user_email is null
   or trim(user_email) = '';


alter table jsimplelist.push_devices
alter column user_email set not null;


create index push_devices_user_email_idx
on jsimplelist.push_devices (user_email);


/*
 * Replace the registration RPC so user_email is always refreshed from the
 * currently authenticated JWT rather than accepted from Android.
 */
drop function jsimplelist.register_push_device(uuid, text);


create function jsimplelist.register_push_device(
    target_client_instance_id uuid,
    target_firebase_installation_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    current_email text;
    cleaned_installation_id text;
begin
    current_user_id := auth.uid();

    current_email :=
        lower(trim(auth.jwt() ->> 'email'));

    cleaned_installation_id :=
        trim(target_firebase_installation_id);

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if current_email is null or current_email = '' then
        raise exception 'Authenticated email address is required';
    end if;

    if target_client_instance_id is null then
        raise exception 'Client instance ID is required';
    end if;

    if cleaned_installation_id = '' then
        raise exception 'Firebase installation ID is required';
    end if;

    /*
     * A Firebase installation may only belong to one JSimpleList account
     * at a time.
     */
    delete from jsimplelist.push_devices
    where firebase_installation_id = cleaned_installation_id
      and not (
          user_id = current_user_id
          and client_instance_id = target_client_instance_id
      );

    insert into jsimplelist.push_devices (
        user_id,
        user_email,
        client_instance_id,
        firebase_installation_id,
        platform,
        created_at,
        updated_at,
        last_seen_at
    )
    values (
        current_user_id,
        current_email,
        target_client_instance_id,
        cleaned_installation_id,
        'android',
        now(),
        now(),
        now()
    )
    on conflict (user_id, client_instance_id)
    do update
    set
        user_email = excluded.user_email,
        firebase_installation_id =
            excluded.firebase_installation_id,
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
