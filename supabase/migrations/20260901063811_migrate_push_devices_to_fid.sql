/*
 * FCM is transitioning from legacy registration tokens to direct
 * registration by Firebase Installation ID (FID).
 *
 * Push-device registration has not yet been released, so discard any
 * development-only legacy token rows rather than retaining identifiers
 * that are not valid FID targets.
 */
delete from jsimplelist.push_devices;


alter table jsimplelist.push_devices
rename column fcm_token to firebase_installation_id;


alter table jsimplelist.push_devices
drop constraint push_devices_fcm_token_key;


alter table jsimplelist.push_devices
add constraint push_devices_firebase_installation_id_key
unique (firebase_installation_id);


/*
 * Parameter names are part of PostgREST's RPC interface, so replace the
 * function rather than retaining target_fcm_token.
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
    cleaned_installation_id text;
begin
    current_user_id := auth.uid();

    cleaned_installation_id :=
        trim(target_firebase_installation_id);

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_client_instance_id is null then
        raise exception 'Client instance ID is required';
    end if;

    if cleaned_installation_id = '' then
        raise exception 'Firebase installation ID is required';
    end if;

    /*
     * A Firebase installation may only belong to one JSimpleList account
     * at a time. This also repairs a stale association after an account
     * switch if an earlier unregister could not reach the server.
     */
    delete from jsimplelist.push_devices
    where firebase_installation_id = cleaned_installation_id
      and not (
          user_id = current_user_id
          and client_instance_id = target_client_instance_id
      );

    insert into jsimplelist.push_devices (
        user_id,
        client_instance_id,
        firebase_installation_id,
        platform,
        created_at,
        updated_at,
        last_seen_at
    )
    values (
        current_user_id,
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
