drop trigger if exists broadcast_jsimplelist_item_change
on jsimplelist.items;


create or replace function jsimplelist_private.broadcast_item_change()
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
            new.list_id::text,
            'origin_client_id',
            case
                when change_origin_client_id is null then null
                else change_origin_client_id::text
            end
        ),
        'list_changed',
        'jsimplelist:list:' || new.list_id::text,
        true
    );

    if new.origin_client_id is not null then
        update jsimplelist.items
        set origin_client_id = null
        where id = new.id;
    end if;

    return new;
end;
$$;


create trigger broadcast_jsimplelist_item_change
after insert or update
on jsimplelist.items
for each row
execute function jsimplelist_private.broadcast_item_change();