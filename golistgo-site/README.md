# GoListGo public website

Static marketing and support pages for `golistgo.net`. This is independent of the authenticated browser application in `web/` served from `jslist.jobsheet.com.au`.

## Cloudflare Pages settings

- Repository: `jobsheet-aus/JSimpleList`
- Production branch: `main`
- Framework: None
- Root directory: `golistgo-site` (or leave repository root unchanged and set output directory to `golistgo-site`)
- Build command: blank (or `exit 0`)
- Build output directory: `.` if root directory is `golistgo-site`; otherwise `golistgo-site`
- Custom domains: `golistgo.net` and `www.golistgo.net`

## Release safety

- Don't move authentication, invitation handoff or verified Android App Link URLs in version 2.1.
- This public site links to legacy hosted privacy/terms/account-deletion documents. Review those documents and replace links with verified first-party policy pages in a separate change.
- Google Play URL intentionally uses the existing Android application ID.
- The current logo is CSS approximation; replace it with approved branded artwork when a suitable source asset is integrated.
- Confirm an inbound support email address before advertising one.
