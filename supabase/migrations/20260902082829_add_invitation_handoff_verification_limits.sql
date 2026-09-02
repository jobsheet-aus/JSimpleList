alter table jsimplelist.invitation_delivery
add column verification_code_sent_at timestamptz,
add column verification_attempt_count integer not null default 0;

alter table jsimplelist.invitation_delivery
add constraint invitation_delivery_verification_attempt_count_nonnegative
check (verification_attempt_count >= 0);

create index invitation_delivery_verification_code_sent_at_idx
on jsimplelist.invitation_delivery(verification_code_sent_at)
where verification_code_sent_at is not null;
