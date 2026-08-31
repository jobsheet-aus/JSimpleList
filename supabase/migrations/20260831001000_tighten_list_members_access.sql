drop policy if exists "list_members_insert_owner"
on jsimplelist.list_members;

drop policy if exists "list_members_update_owner"
on jsimplelist.list_members;


revoke insert, update
on table jsimplelist.list_members
from authenticated;


grant select
on table jsimplelist.list_members
to authenticated;