/*
 * GREEN recipient onboarding handoff.
 *
 * The authoritative invitation remains jsimplelist.list_invitations.
 * These fields belong only to invitation delivery/bootstrap state.
 *
 * The recipient receives the opaque handoff token. Only its SHA-256 hash is
 * stored server-side. Possession of the token does not grant membership.
 * Membership continues to require authenticated acceptance through
 * jsimplelist.accept_list_invitation().
 *
 * invitation_delivery already disappears when its invitation is accepted,
 * declined/cancelled, superseded by reinvite or removed with list deletion.
 * Handoff state therefore inherits the same lifecycle cleanup.
 */

alter table jsimplelist.invitation_delivery
add column handoff_token_hash text,
add column handoff_expires_at timestamptz;


/*
 * SHA-256 is stored as lowercase hexadecimal text.
 */
alter table jsimplelist.invitation_delivery
add constraint invitation_delivery_handoff_token_hash_format
check (
    handoff_token_hash is null
    or handoff_token_hash ~ '^[0-9a-f]{64}$'
);


/*
 * Token and expiry must either both exist or both be absent.
 */
alter table jsimplelist.invitation_delivery
add constraint invitation_delivery_handoff_pair
check (
    (
        handoff_token_hash is null
        and handoff_expires_at is null
    )
    or
    (
        handoff_token_hash is not null
        and handoff_expires_at is not null
    )
);


/*
 * An onboarding-email delivery must have an opaque handoff.
 *
 * Existing ACTIVE_ANDROID / KNOWN_USER delivery rows are unaffected because
 * their immediate channels are PUSH or NONE.
 */
alter table jsimplelist.invitation_delivery
add constraint invitation_delivery_onboarding_requires_handoff
check (
    immediate_channel <> 'ONBOARDING_EMAIL'
    or (
        handoff_token_hash is not null
        and handoff_expires_at is not null
    )
);


/*
 * A handoff token identifies at most one current invitation lifecycle.
 */
create unique index invitation_delivery_handoff_token_hash_idx
on jsimplelist.invitation_delivery (handoff_token_hash)
where handoff_token_hash is not null;


/*
 * Expired rows normally disappear through invitation lifecycle cleanup.
 * This index also supports later explicit expiry cleanup/resolution.
 */
create index invitation_delivery_handoff_expiry_idx
on jsimplelist.invitation_delivery (handoff_expires_at)
where handoff_token_hash is not null;
