/*
 * Prevent a repeated invitation from replacing an active pending invitation.
 * Existing active membership remains protected. Declined/cancelled historical
 * invitations may still be superseded by a fresh invitation.
 */
create or replace function jsimplelist.replace_list_invitation(
    target_list_id uuid,
    target_email text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    cleaned_email text;
    invited_user_id uuid;
    new_invitation_id uuid;
begin
    current_user_id := auth.uid();
    cleaned_email := lower(trim(target_email));

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if target_list_id is null then
        raise exception 'List ID required';
    end if;

    if cleaned_email is null or cleaned_email = '' then
        raise exception 'Email address required';
    end if;

    /* Serialize invitation attempts for this list. */
    perform 1
    from jsimplelist.lists
    where id = target_list_id
      and owner_id = current_user_id
      and deleted_at is null
    for update;

    if not found then
        raise exception 'List not found or access denied';
    end if;

    select id
    into invited_user_id
    from auth.users
    where lower(trim(email)) = cleaned_email
    order by created_at asc
    limit 1;

    if invited_user_id is not null
       and exists (
            select 1
            from jsimplelist.list_members
            where list_id = target_list_id
              and user_id = invited_user_id
              and removed_at is null
       ) then
        raise exception 'This person is already a member of the list';
    end if;

    /* Do not delete or resend an already pending invitation. */
    if exists (
        select 1
        from jsimplelist.list_invitations
        where list_id = target_list_id
          and lower(trim(invited_email)) = cleaned_email
          and accepted_at is null
          and cancelled_at is null
    ) then
        raise exception 'An invitation is already pending for this email address';
    end if;

    /* Retain the existing replacement policy for non-pending history. */
    delete from jsimplelist.notifications
    where source_invitation_id in (
        select invitation.id
        from jsimplelist.list_invitations as invitation
        where invitation.list_id = target_list_id
          and lower(trim(invitation.invited_email)) = cleaned_email
    );

    delete from jsimplelist.list_invitations
    where list_id = target_list_id
      and lower(trim(invited_email)) = cleaned_email;

    insert into jsimplelist.list_invitations (
        list_id,
        invited_email,
        invited_by,
        role
    )
    values (
        target_list_id,
        cleaned_email,
        current_user_id,
        'member'
    )
    returning id
    into new_invitation_id;

    return new_invitation_id;
end;
$$;
