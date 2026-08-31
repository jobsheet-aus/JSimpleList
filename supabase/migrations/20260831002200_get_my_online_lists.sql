create or replace function jsimplelist.get_my_online_lists()
returns jsonb
language plpgsql
stable
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

    return coalesce(
        (
            select jsonb_agg(
                jsonb_build_object(
                    'id', l.id,
                    'name', l.name,
                    'kind', l.kind,
                    'role', lm.role,
                    'position', lm.position,
                    'created_at', l.created_at,
                    'updated_at', l.updated_at
                )
                order by lm.position, l.created_at, l.id
            )
            from jsimplelist.list_members lm
            join jsimplelist.lists l
              on l.id = lm.list_id
            where lm.user_id = current_user_id
              and lm.removed_at is null
              and l.deleted_at is null
        ),
        '[]'::jsonb
    );
end;
$$;


revoke all
on function jsimplelist.get_my_online_lists()
from public, anon, authenticated;

grant execute
on function jsimplelist.get_my_online_lists()
to authenticated;
