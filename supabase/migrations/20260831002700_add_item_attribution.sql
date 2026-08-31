alter table jsimplelist.items
add column if not exists created_by_user_id uuid
references auth.users(id)
on delete set null;

alter table jsimplelist.items
add column if not exists updated_by_user_id uuid
references auth.users(id)
on delete set null;


create or replace function jsimplelist_private.set_item_attribution()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    current_user_id uuid;
begin
    current_user_id := auth.uid();

    if tg_op = 'INSERT' then
        if current_user_id is not null then
            new.created_by_user_id := current_user_id;
            new.updated_by_user_id := current_user_id;
        end if;

        return new;
    end if;

    new.created_by_user_id := old.created_by_user_id;

    if current_user_id is not null then
        new.updated_by_user_id := current_user_id;
    else
        new.updated_by_user_id := old.updated_by_user_id;
    end if;

    return new;
end;
$$;


drop trigger if exists items_set_attribution
on jsimplelist.items;

create trigger items_set_attribution
before insert or update
on jsimplelist.items
for each row
execute function jsimplelist_private.set_item_attribution();


create or replace function jsimplelist.get_list_sync_snapshot(
    target_list_id uuid
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    list_record jsimplelist.lists%rowtype;
    membership_record jsimplelist.list_members%rowtype;
    is_owner boolean;
begin
    current_user_id := auth.uid();

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    select *
    into list_record
    from jsimplelist.lists
    where id = target_list_id;

    if not found then
        return jsonb_build_object(
            'state', 'not_found',
            'list_id', target_list_id
        );
    end if;

    is_owner := list_record.owner_id = current_user_id;

    select *
    into membership_record
    from jsimplelist.list_members
    where list_id = target_list_id
      and user_id = current_user_id;

    if not found and not is_owner then
        raise exception 'List access denied';
    end if;

    if list_record.deleted_at is not null then
        return jsonb_build_object(
            'state', 'deleted',
            'list_id', list_record.id,
            'deleted_at', list_record.deleted_at
        );
    end if;

    if not is_owner and membership_record.removed_at is not null then
        return jsonb_build_object(
            'state', 'removed',
            'list_id', list_record.id,
            'removed_at', membership_record.removed_at
        );
    end if;

    if not is_owner and membership_record.removed_at is null then
        null;
    elsif not is_owner then
        raise exception 'List access denied';
    end if;

    return jsonb_build_object(
        'state', 'active',

        'list', jsonb_build_object(
            'id', list_record.id,
            'owner_id', list_record.owner_id,
            'name', list_record.name,
            'kind', list_record.kind,
            'created_at', list_record.created_at,
            'updated_at', list_record.updated_at,
            'deleted_at', list_record.deleted_at
        ),

        'membership', jsonb_build_object(
            'user_id', current_user_id,
            'role', case
                when is_owner then 'owner'
                else membership_record.role
            end,
            'position', membership_record.position,
            'joined_at', membership_record.joined_at,
            'removed_at', membership_record.removed_at,
            'last_seen_at', membership_record.last_seen_at
        ),

        'items', coalesce(
            (
                select jsonb_agg(
                    jsonb_build_object(
                        'id', i.id,
                        'list_id', i.list_id,
                        'description', i.description,
                        'quantity', i.quantity,
                        'completed', i.completed,
                        'position', i.position,
                        'created_at', i.created_at,
                        'updated_at', i.updated_at,
                        'deleted_at', i.deleted_at,
                        'created_by_user_id', i.created_by_user_id,
                        'updated_by_user_id', i.updated_by_user_id
                    )
                    order by i.position, i.created_at, i.id
                )
                from jsimplelist.items i
                where i.list_id = target_list_id
            ),
            '[]'::jsonb
        )
    );
end;
$$;
