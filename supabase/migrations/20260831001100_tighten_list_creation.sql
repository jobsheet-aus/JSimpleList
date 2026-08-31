drop policy if exists "lists_insert_self_as_owner"
on jsimplelist.lists;

revoke insert
on table jsimplelist.lists
from authenticated;