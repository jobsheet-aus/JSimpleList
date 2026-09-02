create or replace function jsimplelist.claim_invitation_handoff_verification_attempt(
    target_invitation_id uuid,
    max_attempts integer default 8
)
returns integer
language plpgsql
security definer
set search_path = jsimplelist, public
as $$
declare
    new_attempt_count integer;
begin
    if max_attempts < 1 then
        raise exception 'max_attempts must be positive';
    end if;

    update jsimplelist.invitation_delivery
    set
        verification_attempt_count =
            verification_attempt_count + 1,
        updated_at =
            now()
    where invitation_id =
            target_invitation_id
      and verification_attempt_count <
            max_attempts
    returning verification_attempt_count
    into new_attempt_count;

    return new_attempt_count;
end;
$$;

revoke all
on function jsimplelist.claim_invitation_handoff_verification_attempt(uuid, integer)
from public;

grant execute
on function jsimplelist.claim_invitation_handoff_verification_attempt(uuid, integer)
to service_role;
