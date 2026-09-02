# JSimpleList Changelog

This document records significant user-facing changes to JSimpleList.

## 2.0 — Unreleased

### Added

* User-named lists
* To-do, Shopping, and Discussion list types
* Optional JSimpleList accounts for online features
* Private online list sync
* Private sharing between JSimpleList users
* Browser access for online lists
* Email invitation onboarding for new users
* Android push notifications for sharing events
* Account deletion
* Shared-list ownership and member access
* Offline caching of online and shared lists
* Remember last active list
* Bulk uncheck with Undo
* Safe online item deletion using tombstones
* Realtime refresh signalling between clients
* Shared-item creator and editor attribution support

### Changed

* JSimpleList is now local-first rather than strictly local-only
* Signing in does not automatically upload existing local lists
* Lists become online only through an explicit user action
* Fixed To-do and Shopping tabs have been replaced by user-managed lists
* The current list can be changed by horizontal swipe or Manage lists
* Shared lists are marked with a chain/link indicator
* Completed items remain below incomplete items
* Item descriptions now place the cursor at the end when editing
* Shopping quantity fields select the existing value for quick replacement
* Account and sharing controls have been reorganised around a single signed-in account per device
* List sharing and account deletion are separate operations
* Invitation delivery now distinguishes active Android users, existing users and new recipients

### Browser

* Online lists can be opened at jslist.jobsheet.com.au
* Browser users can sign in with the same JSimpleList account used on Android
* Shared and self-online lists appear in the browser
* Browser item add, edit, complete, uncheck, and delete operations sync with Android
* Browser list rename is supported
* Browser bulk uncheck is supported

### Privacy and networking

* Local-only use still requires no account
* Online features use Supabase-backed account, list, sharing and sync services
* Sharing invitations may use email
* Android sharing notifications may use Firebase Cloud Messaging
* JSimpleList continues to contain no advertising or analytics

### Upgrade notes

* Existing JSimpleList 1.1 To-do and Shopping data is migrated into the 2.0 Room database
* Existing descriptions, quantities, completed states and order are intended to be preserved
* Existing font scale is retained
* A Google Play-signed 1.1 → 2.0 upgrade test is required before public release

## 1.1 — 21 August 2026

### Added

* Updated compact list-entry controls
* About screen showing version and build date
* GitHub and privacy-policy links
* Bundled JSimpleList branding and Manrope font

### Changed

* Quantity entry controls refined for Shopping lists
* General UI and release polish

## 1.0

Initial public JSimpleList release.

### Included

* Fixed To-do list
* Fixed Shopping list
* Shopping quantities
* Add, edit, complete, uncheck, and delete items
* Completed items displayed below incomplete items
* Pinch-to-resize list text
* Local persistence
* No account
* No advertising
* No analytics
* No server connection
