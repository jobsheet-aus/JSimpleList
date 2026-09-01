/*
 * One-time cleanup of invitation history created before the current
 * invitation lifecycle rules.
 *
 * Current invariant:
 * - deleted lists have no surviving invitation lifecycle
 * - for each active list + normalised invited email, only the newest
 *   invitation lifecycle survives
 *
 * Invitation-derived notifications are removed before invitation rows.
 * push_outbox.notification_id uses ON DELETE CASCADE, so obsolete push
 * delivery rows disappear automatically when their notification is deleted.
 */


/*
 * Materialise the invitation rows that should be removed.
 *
 * All invitations belonging to deleted lists are obsolete.
 *
 * For active lists, rank invitations newest-first within each
 * list + normalised-email pair and remove every row except rank 1.
 */
create temporary table invitation_history_to_purge (
    invitation_id uuid primary key
)
on commit drop;


insert into invitation_history_to_purge (
    invitation_id
)
select ranked.id
from (
    select
        invitation.id,
        list_record.deleted_at,
        row_number() over (
            partition by
                invitation.list_id,
                lower(trim(invitation.invited_email))
            order by
                invitation.created_at desc,
                invitation.id desc
        ) as lifecycle_rank
    from jsimplelist.list_invitations as invitation
    join jsimplelist.lists as list_record
      on list_record.id = invitation.list_id
) as ranked
where ranked.deleted_at is not null
   or ranked.lifecycle_rank > 1;


/*
 * Remove notifications sourced from invitation lifecycle rows that are
 * about to disappear.
 *
 * Their corresponding push_outbox records cascade automatically.
 */
delete from jsimplelist.notifications
where source_invitation_id in (
    select invitation_id
    from invitation_history_to_purge
);


/*
 * Also remove any surviving notification belonging to a deleted list.
 *
 * This catches historical notification rows even if their
 * source_invitation_id had already become NULL under the older
 * ON DELETE SET NULL foreign-key behaviour.
 */
delete from jsimplelist.notifications as notification
using jsimplelist.lists as list_record
where notification.list_id = list_record.id
  and list_record.deleted_at is not null;


/*
 * Finally remove the obsolete invitation rows themselves.
 */
delete from jsimplelist.list_invitations
where id in (
    select invitation_id
    from invitation_history_to_purge
);
