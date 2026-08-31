create unique index list_invitations_one_active_per_email
    on jsimplelist.list_invitations (
        list_id,
        lower(invited_email)
    )
    where accepted_at is null
      and cancelled_at is null;
