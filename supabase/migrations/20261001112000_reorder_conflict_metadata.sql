-- JSimpleList: record moved-item intent for atomic reorder conflict handling.
--
-- Keep the original 5-argument apply_online_reorder() in place during the
-- staged rollout. New Android code will use the 6-argument overload below.

alter table jsimplelist.reorder_operations
add column if not exists moved_item_id uuid;

create index if not exists reorder_operations_moved_item_revision_idx
on jsimplelist.reorder_operations (
    list_id,
    moved_item_id,
    applied_revision
);

create function jsimplelist.apply_online_reorder(
    target_list_id uuid,
    target_operation_id uuid,
    target_expected_revision bigint,
    target_order uuid[],
    target_moved_item_id uuid,
    target_origin_client_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_id uuid := auth.uid();
    current_revision bigint;
    saved_operation jsimplelist.reorder_operations%rowtype;
    current_order uuid[];
    canonical_order jsonb;
    item_completed boolean;
    seen_completed boolean := false;
    final_revision bigint;
    same_item_moved boolean := false;
begin
    if caller_id is null then
        raise exception 'Authentication required';
    end if;

    if target_list_id is null
       or target_operation_id is null
       or target_expected_revision is null
       or target_expected_revision < 0
       or target_order is null
       or target_moved_item_id is null
       or target_origin_client_id is null then
        raise exception 'Invalid reorder request';
    end if;

    -- Keep the same lock order as the first protocol version.
    perform 1
    from jsimplelist.lists l
    where l.id = target_list_id
      and l.deleted_at is null
    for update;

    if not found then
        raise exception 'List not found or access denied';
    end if;

    if not (
        jsimplelist_private.is_list_owner(target_list_id)
        or jsimplelist_private.is_active_list_member(target_list_id)
    ) then
        raise exception 'List not found or access denied';
    end if;

    perform 1
    from jsimplelist.items i
    where i.list_id = target_list_id
      and i.deleted_at is null
    order by i.id
    for update;

    select s.revision
    into current_revision
    from jsimplelist.list_order_state s
    where s.list_id = target_list_id
    for update;

    -- Idempotency must be checked before revision comparison because an
    -- earlier request may have committed even if its HTTP response was lost.
    select *
    into saved_operation
    from jsimplelist.reorder_operations r
    where r.operation_id = target_operation_id;

    if found then
        if saved_operation.account_id <> caller_id
           or saved_operation.list_id <> target_list_id
           or saved_operation.expected_revision <> target_expected_revision
           or saved_operation.target_order <> target_order
           or saved_operation.moved_item_id is distinct from target_moved_item_id then
            raise exception 'Operation ID reused with different parameters';
        end if;

        return jsonb_build_object(
            'status', 'already_applied',
            'applied_revision', saved_operation.applied_revision,
            'revision', current_revision
        );
    end if;

    if current_revision <> target_expected_revision then
        select exists (
            select 1
            from jsimplelist.reorder_operations r
            where r.list_id = target_list_id
              and r.moved_item_id = target_moved_item_id
              and r.applied_revision > target_expected_revision
        )
        into same_item_moved;

        select coalesce(
            jsonb_agg(
                jsonb_build_object(
                    'id', i.id,
                    'completed', i.completed
                )
                order by
                    i.completed,
                    i.position,
                    i.created_at,
                    i.id
            ),
            '[]'::jsonb
        )
        into canonical_order
        from jsimplelist.items i
        where i.list_id = target_list_id
          and i.deleted_at is null;

        return jsonb_build_object(
            'status', 'conflict',
            'revision', current_revision,
            'same_item_moved', same_item_moved,
            'items', canonical_order
        );
    end if;

    select coalesce(
        array_agg(
            i.id
            order by
                i.completed,
                i.position,
                i.created_at,
                i.id
        ),
        '{}'::uuid[]
    )
    into current_order
    from jsimplelist.items i
    where i.list_id = target_list_id
      and i.deleted_at is null;

    if cardinality(target_order) <> cardinality(current_order)
       or (
            select count(distinct id)
            from unnest(target_order) as ids(id)
       ) <> cardinality(target_order)
       or not (
            target_order @> current_order
            and current_order @> target_order
       ) then
        return jsonb_build_object(
            'status', 'conflict',
            'revision', current_revision,
            'reason', 'item_set_changed',
            'same_item_moved', false
        );
    end if;

    if not (target_moved_item_id = any(current_order)) then
        return jsonb_build_object(
            'status', 'conflict',
            'revision', current_revision,
            'reason', 'moved_item_missing',
            'same_item_moved', false
        );
    end if;

    for item_completed in
        select i.completed
        from unnest(target_order)
            with ordinality as requested(id, ord)
        join jsimplelist.items i
          on i.id = requested.id
        order by requested.ord
    loop
        if item_completed then
            seen_completed := true;
        elsif seen_completed then
            raise exception
                'Unchecked items must precede checked items';
        end if;
    end loop;

    perform set_config(
        'jsimplelist.atomic_reorder',
        'on',
        true
    );

    with desired as (
        select
            i.id,
            (
                row_number() over (
                    partition by i.completed
                    order by requested.ord
                ) * 10
            )::integer as position
        from unnest(target_order)
            with ordinality as requested(id, ord)
        join jsimplelist.items i
          on i.id = requested.id
    )
    update jsimplelist.items i
    set position = desired.position,
        updated_at = now(),
        origin_client_id = target_origin_client_id
    from desired
    where i.id = desired.id
      and i.list_id = target_list_id
      and i.deleted_at is null
      and i.position is distinct from desired.position;

    select revision
    into final_revision
    from jsimplelist.list_order_state
    where list_id = target_list_id;

    insert into jsimplelist.reorder_operations (
        operation_id,
        list_id,
        account_id,
        expected_revision,
        target_order,
        applied_revision,
        moved_item_id
    )
    values (
        target_operation_id,
        target_list_id,
        caller_id,
        target_expected_revision,
        target_order,
        final_revision,
        target_moved_item_id
    );

    return jsonb_build_object(
        'status', 'applied',
        'revision', final_revision
    );
end;
$$;

revoke all
on function jsimplelist.apply_online_reorder(
    uuid,
    uuid,
    bigint,
    uuid[],
    uuid,
    uuid
)
from public, anon, authenticated;

grant execute
on function jsimplelist.apply_online_reorder(
    uuid,
    uuid,
    bigint,
    uuid[],
    uuid,
    uuid
)
to authenticated;
