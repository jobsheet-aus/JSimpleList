/*
 * Include inviter avatar identity in pending invitations.
 *
 * The existing RPC already joins jsimplelist.profiles for the inviter's
 * display name, so avatar icon/colour can be returned from the same
 * authoritative profile row without adding another client query.
 */

drop function if exists jsimplelist.get_my_pending_invitations();


create function jsimplelist.get_my_pending_invitations()
returns table (
    id uuid,
    list_id uuid,
    invited_by uuid,
    role text,
    list_name text,
    list_kind text,
    inviter_display_name text,
    inviter_avatar_icon text,
    inviter_avatar_colour text
)
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
    current_email text;
begin
    current_email :=
        lower(trim(auth.jwt() ->> 'email'));

    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    if current_email is null or current_email = '' then
        raise exception 'Authenticated email required';
    end if;

    return query
    select
        invitation.id,
        invitation.list_id,
        invitation.invited_by,
        invitation.role,
        list_record.name,
        list_record.kind,
        coalesce(
            nullif(trim(profile.display_name), ''),
            case
                when inviter_auth.email is not null
                     and trim(inviter_auth.email) <> ''
                then left(
                    split_part(
                        trim(inviter_auth.email),
                        '@',
                        1
                    ) || '@',
                    50
                )
                else 'JSimpleList user'
            end
        ) as inviter_display_name,
        coalesce(
            nullif(trim(profile.avatar_icon), ''),
            'person'
        ) as inviter_avatar_icon,
        coalesce(
            nullif(trim(profile.avatar_colour), ''),
            'blue'
        ) as inviter_avatar_colour
    from jsimplelist.list_invitations as invitation
    join jsimplelist.lists as list_record
      on list_record.id = invitation.list_id
     and list_record.deleted_at is null
    left join jsimplelist.profiles as profile
      on profile.user_id = invitation.invited_by
    left join auth.users as inviter_auth
      on inviter_auth.id = invitation.invited_by
    where invitation.accepted_at is null
      and invitation.cancelled_at is null
      and lower(trim(invitation.invited_email)) = current_email
    order by invitation.created_at asc;
end;
$$;


revoke all
on function jsimplelist.get_my_pending_invitations()
from public, anon, authenticated;


grant execute
on function jsimplelist.get_my_pending_invitations()
to authenticated;
