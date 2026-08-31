alter table jsimplelist.items
add column if not exists origin_client_id uuid;


drop function if exists jsimplelist.delete_online_item(uuid);


create function jsimplelist.delete_online_item(
    target_item_id uuid,
    target_origin_client_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    target_list_id uuid;
begin
    current_user_id := auth.uid();

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_item_id is null then
        raise exception 'Item ID required';
    end if;

    select i.list_id
    into target_list_id
    from jsimplelist.items i
    where i.id = target_item_id;

    if not found then
        return;
    end if;

    if not (
        jsimplelist_private.is_list_owner(target_list_id)
        or jsimplelist_private.is_active_list_member(target_list_id)
    ) then
        raise exception 'Item not found or access denied';
    end if;

    update jsimplelist.items
    set deleted_at = now(),
        updated_at = now(),
        origin_client_id = target_origin_client_id
    where id = target_item_id
      and deleted_at is null;
end;
$$;


revoke all
on function jsimplelist.delete_online_item(uuid, uuid)
from public, anon, authenticated;

grant execute
on function jsimplelist.delete_online_item(uuid, uuid)
to authenticated;


create or replace function jsimplelist_private.can_receive_list_broadcast(
    target_topic text
)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    target_list_id uuid;
begin
    if target_topic is null then
        return false;
    end if;

    if target_topic !~ '^jsimplelist:list:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
        return false;
    end if;

    target_list_id :=
        substring(target_topic from 18)::uuid;

    return
        jsimplelist_private.is_list_owner(target_list_id)
        or
        jsimplelist_private.is_active_list_member(target_list_id);
end;
$$;


revoke all
on function jsimplelist_private.can_receive_list_broadcast(text)
from public, anon, authenticated;

grant execute
on function jsimplelist_private.can_receive_list_broadcast(text)
to authenticated;


drop policy if exists "jsimplelist_receive_list_broadcasts"
on realtime.messages;

create policy "jsimplelist_receive_list_broadcasts"
on realtime.messages
for select
to authenticated
using (
    extension = 'broadcast'
    and (
        select jsimplelist_private.can_receive_list_broadcast(
            realtime.topic()
        )
    )
);


create or replace function jsimplelist_private.broadcast_item_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform realtime.send(
        jsonb_build_object(
            'list_id',
            new.list_id::text,
            'origin_client_id',
            case
                when new.origin_client_id is null then null
                else new.origin_client_id::text
            end
        ),
        'list_changed',
        'jsimplelist:list:' || new.list_id::text,
        true
    );

    return null;
end;
$$;


revoke all
on function jsimplelist_private.broadcast_item_change()
from public, anon, authenticated;


drop trigger if exists broadcast_jsimplelist_item_change
on jsimplelist.items;

create trigger broadcast_jsimplelist_item_change
after insert or update
on jsimplelist.items
for each row
execute function jsimplelist_private.broadcast_item_change();