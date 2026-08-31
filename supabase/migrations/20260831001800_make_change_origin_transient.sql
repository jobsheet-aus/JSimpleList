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

    new.origin_client_id := null;

    return new;
end;
$$;


update jsimplelist.items
set origin_client_id = null
where origin_client_id is not null;


create trigger broadcast_jsimplelist_item_change
before insert or update
on jsimplelist.items
for each row
execute function jsimplelist_private.broadcast_item_change();