create or replace function jsimplelist.delete_online_account_data()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid;
    current_email text;
begin
    current_user_id := auth.uid();
    current_email := auth.jwt() ->> 'email';

    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    /*
     * Permanently remove lists owned by this account.
     *
     * Items belong to lists, not to the individual member who created them.
     * Therefore items in lists owned by somebody else are deliberately
     * untouched when this user deletes their account.
     */

    delete from jsimplelist.items
    where list_id in (
        select id
        from jsimplelist.lists
        where owner_id = current_user_id
    );

    delete from jsimplelist.list_invitations
    where list_id in (
        select id
        from jsimplelist.lists
        where owner_id = current_user_id
    );

    delete from jsimplelist.list_members
    where list_id in (
        select id
        from jsimplelist.lists
        where owner_id = current_user_id
    );

    delete from jsimplelist.lists
    where owner_id = current_user_id;

    /*
     * Remove this account from lists owned by other users.
     * The remaining lists and all of their items remain intact.
     */

    delete from jsimplelist.list_members
    where user_id = current_user_id;

    /*
     * Remove invitations created by this account.
     */

    delete from jsimplelist.list_invitations
    where invited_by = current_user_id;

    /*
     * Remove outstanding invitations addressed to this account's email.
     * This prevents a later account using the same email address from
     * inheriting invitations belonging to the deleted account.
     */

    if current_email is not null and trim(current_email) <> '' then
        delete from jsimplelist.list_invitations
        where lower(invited_email) = lower(current_email)
          and accepted_at is null
          and cancelled_at is null;
    end if;

    /*
     * Remove the application profile explicitly.
     * It also has ON DELETE CASCADE from auth.users, but removing it here
     * leaves the JSimpleList application data clean before Auth deletion.
     */

    delete from jsimplelist.profiles
    where user_id = current_user_id;
end;
$$;


revoke all
on function jsimplelist.delete_online_account_data()
from public, anon, authenticated;


grant execute
on function jsimplelist.delete_online_account_data()
to authenticated;
