alter table jsimplelist.list_members
add column position integer;


update jsimplelist.list_members lm
set position = l.position
from jsimplelist.lists l
where l.id = lm.list_id;


alter table jsimplelist.list_members
alter column position set not null;


create index list_members_user_position_idx
on jsimplelist.list_members(user_id, position);


create or replace function jsimplelist.create_online_list(
    target_list_id uuid,
    target_name text,
    target_kind text,
    target_position integer,
    target_created_at timestamptz,
    target_updated_at timestamptz
)
returns uuid
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

    if target_list_id is null then
        raise exception 'List ID required';
    end if;

    if trim(target_name) = '' then
        raise exception 'List name required';
    end if;

    if target_kind not in ('TODO', 'SHOPPING', 'DISCUSSION') then
        raise exception 'Invalid list kind';
    end if;

    insert into jsimplelist.lists (
        id,
        owner_id,
        name,
        kind,
        created_at,
        updated_at,
        deleted_at
    )
    values (
        target_list_id,
        current_user_id,
        trim(target_name),
        target_kind,
        target_created_at,
        target_updated_at,
        null
    );

    insert into jsimplelist.list_members (
        list_id,
        user_id,
        role,
        position,
        joined_at,
        removed_at,
        last_seen_at
    )
    values (
        target_list_id,
        current_user_id,
        'owner',
        target_position,
        now(),
        null,
        null
    );

    return target_list_id;
end;
$$;


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
                        'deleted_at', i.deleted_at
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


alter table jsimplelist.lists
drop column position;