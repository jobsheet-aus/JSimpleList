/*
 * The invite-list Edge Function uses the Supabase service_role only for
 * server-side push delivery lookups.
 *
 * RLS bypass alone does not grant USAGE on a custom schema, so grant the
 * minimum required schema/table privileges explicitly.
 */

grant usage
on schema jsimplelist
to service_role;


grant select
on table jsimplelist.push_devices
to service_role;


grant select
on table jsimplelist.profiles
to service_role;
