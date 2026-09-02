create or replace function jsimplelist.claim_invitation_handoff_verification_send(
    target_invitation_id uuid,
    cooldown_seconds integer default 60
)
returns timestamptz
language plpgsql
security definer
set search_path = jsimplelist, public
as $$
declare
    claimed_at timestamptz;
begin
    if cooldown_seconds < 1 then
        raise exception 'cooldown_seconds must be positive';
    end if;

    update jsimplelist.invitation_delivery
    set
        verification_code_sent_at =
            now(),
        updated_at =
            now()
    where invitation_id =
            target_invitation_id
      and (
            verification_code_sent_at is null
            or verification_code_sent_at <=
                now() - make_interval(secs => cooldown_seconds)
      )
    returning verification_code_sent_at
    into claimed_at;

    return claimed_at;
end;
$$;

revoke all
on function jsimplelist.claim_invitation_handoff_verification_send(uuid, integer)
from public;

grant execute
on function jsimplelist.claim_invitation_handoff_verification_send(uuid, integer)
to service_role;
