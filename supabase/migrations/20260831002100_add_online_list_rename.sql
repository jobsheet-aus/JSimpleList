alter table jsimplelist.lists
add column if not exists origin_client_id uuid;


create or replace function jsimplelist.rename_online_list(
    target_list_id uuid,
    target_name text,
    target_origin_client_id uuid
)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    operation_time timestamptz;
begin
    current_user_id := auth.uid();

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_list_id is null then
        raise exception 'List ID required';
    end if;

    if trim(target_name) = '' then
        raise exception 'List name required';
    end if;

    if length(trim(target_name)) > 100 then
        raise exception 'List name too long';
    end if;

    if not jsimplelist_private.is_list_owner(target_list_id) then
        raise exception 'List not found or access denied';
    end if;

    operation_time := now();

    update jsimplelist.lists
    set name = trim(target_name),
        updated_at = operation_time,
        origin_client_id = target_origin_client_id
    where id = target_list_id
      and deleted_at is null;

    if not found then
        raise exception 'List not found or access denied';
    end if;

    return operation_time;
end;
$$;


revoke all
on function jsimplelist.rename_online_list(uuid, text, uuid)
from public, anon, authenticated;

grant execute
on function jsimplelist.rename_online_list(uuid, text, uuid)
to authenticated;


create or replace function jsimplelist_private.broadcast_list_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    change_origin_client_id uuid;
begin
    if (
        tg_op = 'UPDATE'
        and old.origin_client_id is not null
        and new.origin_client_id is null
        and (
            to_jsonb(new) - 'origin_client_id'
        ) = (
            to_jsonb(old) - 'origin_client_id'
        )
    ) then
        return new;
    end if;

    change_origin_client_id := new.origin_client_id;

    perform realtime.send(
        jsonb_build_object(
            'list_id',
            new.id::text,
            'origin_client_id',
            case
                when change_origin_client_id is null then null
                else change_origin_client_id::text
            end
        ),
        'list_changed',
        'jsimplelist:list:' || new.id::text,
        true
    );

    if new.origin_client_id is not null then
        update jsimplelist.lists
        set origin_client_id = null
        where id = new.id;
    end if;

    return new;
end;
$$;


revoke all
on function jsimplelist_private.broadcast_list_change()
from public, anon, authenticated;


drop trigger if exists broadcast_jsimplelist_list_change
on jsimplelist.lists;

create trigger broadcast_jsimplelist_list_change
after update
on jsimplelist.lists
for each row
execute function jsimplelist_private.broadcast_list_change();
