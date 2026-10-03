-- JSimpleList: staged, versioned reorder protocol.
-- IMPORTANT: Leave protected=false until Android and browser use this RPC.
-- Existing released clients still write item positions directly.

create table jsimplelist.list_order_state (
    list_id uuid primary key references jsimplelist.lists(id) on delete cascade,
    revision bigint not null default 0 check (revision >= 0),
    protected boolean not null default false
);

insert into jsimplelist.list_order_state (list_id)
select id from jsimplelist.lists;

create table jsimplelist.reorder_operations (
    operation_id uuid primary key,
    list_id uuid not null references jsimplelist.lists(id) on delete cascade,
    account_id uuid not null references auth.users(id) on delete cascade,
    expected_revision bigint not null,
    target_order uuid[] not null,
    applied_revision bigint not null,
    applied_at timestamptz not null default now()
);

create index reorder_operations_list_revision_idx
on jsimplelist.reorder_operations (list_id, applied_revision);

revoke all on jsimplelist.list_order_state from public, anon, authenticated;
revoke all on jsimplelist.reorder_operations from public, anon, authenticated;
alter table jsimplelist.list_order_state enable row level security;
alter table jsimplelist.reorder_operations enable row level security;

create function jsimplelist_private.create_list_order_state()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into jsimplelist.list_order_state (list_id) values (new.id);
    return new;
end;
$$;
revoke all on function jsimplelist_private.create_list_order_state()
from public, anon, authenticated;

create trigger jsimplelist_create_list_order_state
after insert on jsimplelist.lists for each row
execute function jsimplelist_private.create_list_order_state();

-- During the later cutover, protected=true makes legacy position updates no-ops
-- while still allowing their unrelated item edits. Only the controlled RPC can
-- change positions after cutover. Do not enable this without a client rollout.
create function jsimplelist_private.guard_item_position()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    is_protected boolean;
begin
    if new.position is not distinct from old.position then
        return new;
    end if;

    select s.protected into is_protected
    from jsimplelist.list_order_state s
    where s.list_id = old.list_id;

    if coalesce(is_protected, false)
       and current_setting('jsimplelist.atomic_reorder', true) is distinct from 'on' then
        new.position := old.position;
    end if;
    return new;
end;
$$;
revoke all on function jsimplelist_private.guard_item_position()
from public, anon, authenticated;

create trigger jsimplelist_guard_item_position
before update on jsimplelist.items for each row
execute function jsimplelist_private.guard_item_position();

-- Direct additions, completion changes, tombstones and legacy position writes
-- advance the revision. One legacy statement may advance it multiple times:
-- the revision is a monotonic conflict token, not a count of user gestures.
create function jsimplelist_private.bump_item_order_revision()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if tg_op = 'INSERT' then
        update jsimplelist.list_order_state
        set revision = revision + 1 where list_id = new.list_id;
    elsif tg_op = 'DELETE' then
        update jsimplelist.list_order_state
        set revision = revision + 1 where list_id = old.list_id;
    elsif old.list_id is distinct from new.list_id
       or old.position is distinct from new.position
       or old.completed is distinct from new.completed
       or old.deleted_at is distinct from new.deleted_at then
        update jsimplelist.list_order_state
        set revision = revision + 1
        where list_id = old.list_id or list_id = new.list_id;
    end if;
    return null;
end;
$$;
revoke all on function jsimplelist_private.bump_item_order_revision()
from public, anon, authenticated;

create trigger jsimplelist_bump_item_order_revision
after insert or update or delete on jsimplelist.items for each row
execute function jsimplelist_private.bump_item_order_revision();

-- Fetch the revision and canonical order together for initialising/rebasing
-- the device outbox. The existing list snapshot remains unchanged in stage 1.
create function jsimplelist.get_list_order_state(target_list_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    current_revision bigint;
    canonical_order jsonb;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;
    if not (jsimplelist_private.is_list_owner(target_list_id)
         or jsimplelist_private.is_active_list_member(target_list_id)) then
        raise exception 'List not found or access denied';
    end if;

    -- Lock the list first to exclude concurrent insertions.
    perform 1 from jsimplelist.lists l
    where l.id = target_list_id and l.deleted_at is null
    for update;
    if not found then
        raise exception 'List not found or access denied';
    end if;

    -- Acquire item locks deterministically before the revision.
    perform 1 from jsimplelist.items i
    where i.list_id = target_list_id and i.deleted_at is null
    order by i.id
    for share;

    select s.revision into current_revision
    from jsimplelist.list_order_state s
    where s.list_id = target_list_id
    for share;

    select coalesce(jsonb_agg(
        jsonb_build_object('id', i.id, 'completed', i.completed)
        order by i.completed, i.position, i.created_at, i.id
    ), '[]'::jsonb) into canonical_order
    from jsimplelist.items i
    where i.list_id = target_list_id and i.deleted_at is null;

    return jsonb_build_object(
        'revision', current_revision,
        'items', canonical_order
    );
end;
$$;

revoke all on function jsimplelist.get_list_order_state(uuid)
from public, anon, authenticated;
grant execute on function jsimplelist.get_list_order_state(uuid)
to authenticated;

-- Single transaction, per-list revision check, complete active-item set,
-- checked/unchecked partition, and durable operation-ID deduplication.
create function jsimplelist.apply_online_reorder(
    target_list_id uuid,
    target_operation_id uuid,
    target_expected_revision bigint,
    target_order uuid[],
    target_origin_client_id uuid
)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    caller_id uuid := auth.uid();
    current_revision bigint;
    saved_operation jsimplelist.reorder_operations%rowtype;
    current_order uuid[];
    canonical_order jsonb;
    item_completed boolean;
    seen_completed boolean := false;
    final_revision bigint;
begin
    if caller_id is null then
        raise exception 'Authentication required';
    end if;
    if target_list_id is null or target_operation_id is null
       or target_expected_revision is null or target_expected_revision < 0
       or target_order is null or target_origin_client_id is null then
        raise exception 'Invalid reorder request';
    end if;

    -- Lock the list before item rows. A concurrent delete must not win midway.
    perform 1 from jsimplelist.lists l
    where l.id = target_list_id and l.deleted_at is null
    for update;
    if not found then
        raise exception 'List not found or access denied';
    end if;
    if not (jsimplelist_private.is_list_owner(target_list_id)
         or jsimplelist_private.is_active_list_member(target_list_id)) then
        raise exception 'List not found or access denied';
    end if;

    -- Lock existing active items in UUID order before the revision row.
    -- Legacy writers acquire item locks before their revision trigger.
    perform 1 from jsimplelist.items i
    where i.list_id = target_list_id and i.deleted_at is null
    order by i.id
    for update;

    select s.revision into current_revision
    from jsimplelist.list_order_state s
    where s.list_id = target_list_id for update;

    -- The operation ledger must be consulted before comparing revisions:
    -- a previous attempt may have committed despite a lost HTTP response.
    select * into saved_operation
    from jsimplelist.reorder_operations r
    where r.operation_id = target_operation_id;
    if found then
        if saved_operation.account_id <> caller_id
           or saved_operation.list_id <> target_list_id
           or saved_operation.expected_revision <> target_expected_revision
           or saved_operation.target_order <> target_order then
            raise exception 'Operation ID reused with different parameters';
        end if;
        return jsonb_build_object(
            'status', 'already_applied',
            'applied_revision', saved_operation.applied_revision,
            'revision', current_revision
        );
    end if;

    if current_revision <> target_expected_revision then
        select coalesce(jsonb_agg(
            jsonb_build_object('id', i.id, 'completed', i.completed)
            order by i.completed, i.position, i.created_at, i.id
        ), '[]'::jsonb) into canonical_order
        from jsimplelist.items i
        where i.list_id = target_list_id and i.deleted_at is null;
        return jsonb_build_object(
            'status', 'conflict',
            'revision', current_revision,
            'items', canonical_order
        );
    end if;

    -- The list and existing active item rows remain locked through commit.

    select coalesce(array_agg(i.id order by
        i.completed, i.position, i.created_at, i.id), '{}'::uuid[])
    into current_order
    from jsimplelist.items i
    where i.list_id = target_list_id and i.deleted_at is null;

    if cardinality(target_order) <> cardinality(current_order)
       or (select count(distinct id) from unnest(target_order) as ids(id))
          <> cardinality(target_order)
       or not (target_order @> current_order and current_order @> target_order) then
        return jsonb_build_object(
            'status', 'conflict',
            'revision', current_revision,
            'reason', 'item_set_changed'
        );
    end if;

    for item_completed in
        select i.completed
        from unnest(target_order) with ordinality as requested(id, ord)
        join jsimplelist.items i on i.id = requested.id
        order by requested.ord
    loop
        if item_completed then
            seen_completed := true;
        elsif seen_completed then
            raise exception 'Unchecked items must precede checked items';
        end if;
    end loop;

    perform set_config('jsimplelist.atomic_reorder', 'on', true);

    with desired as (
        select i.id,
               (row_number() over (
                   partition by i.completed order by requested.ord
               ) * 10)::integer as position
        from unnest(target_order) with ordinality as requested(id, ord)
        join jsimplelist.items i on i.id = requested.id
    )
    update jsimplelist.items i
    set position = desired.position,
        updated_at = now(),
        origin_client_id = target_origin_client_id
    from desired
    where i.id = desired.id and i.list_id = target_list_id
      and i.deleted_at is null
      and i.position is distinct from desired.position;

    select revision into final_revision
    from jsimplelist.list_order_state where list_id = target_list_id;

    insert into jsimplelist.reorder_operations (
        operation_id, list_id, account_id, expected_revision,
        target_order, applied_revision
    ) values (
        target_operation_id, target_list_id, caller_id,
        target_expected_revision, target_order, final_revision
    );

    return jsonb_build_object(
        'status', 'applied',
        'revision', final_revision
    );
end;
$$;

revoke all on function jsimplelist.apply_online_reorder(uuid, uuid, bigint, uuid[], uuid)
from public, anon, authenticated;
grant execute on function jsimplelist.apply_online_reorder(uuid, uuid, bigint, uuid[], uuid)
to authenticated;
