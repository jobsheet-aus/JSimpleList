# JSimpleList

JSimpleList is a small, straightforward Android app for To-do, Shopping, and Discussion lists.

It is designed to remain useful as a local-only app while also supporting optional private online sync and sharing.

JSimpleList is produced by JobSheet ([www.jobsheet.com.au](http://www.jobsheet.com.au)) and is copyright © 2026 21 TWELVE CONSULTING PTY LTD.

## Features

* User-named To-do, Shopping, and Discussion lists
* Shopping lists with quantity
* Add, edit, complete, uncheck and delete items
* Completed items remain available below incomplete items
* Bulk uncheck with Undo on Android
* Pinch to resize list text
* Remembers the last active list
* Local persistence across app restarts
* Local-only use without an account
* Optional online sync and private list sharing
* Optional browser access for online lists
* Email invitation workflow for shared lists
* Offline use of cached shared lists
* Compact Android interface
* Manrope font bundled with the app
* No advertising or analytics

## Local-first design

Local lists remain on the Android device unless the user explicitly chooses an operation that makes a particular list available online.

Signing in does not automatically upload existing local lists.

Online and shared lists remain cached locally so they can continue to be used when the device is temporarily offline.

## Online sharing

An account is required only for online features.

Online lists can be used across devices signed in to the same JSimpleList account or shared privately with other people.

Browser access is available at:

https://jslist.jobsheet.com.au

The public JSimpleList product page is:

https://jobsheet.com.au/jslist/

## Requirements

* Android 8.0 (API 26) or later

## Build

The project uses the included Gradle wrapper.

From Windows:

```text
D:\simplelist\gradlew.bat -p D:\simplelist assembleDebug
```

The debug APK is produced at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Privacy

Local-only lists remain stored on the Android device.

If the user chooses online sync or sharing, the relevant account, membership, invitation and list data is processed by the JSimpleList online service so those features can operate.

JSimpleList does not include advertising or analytics.

Privacy policy:

https://jobsheet.com.au/jsimplelist-privacy.html

## Changelog

Release history is maintained in:

`CHANGELOG.md`

## Licence

Copyright © 2026 21 TWELVE CONSULTING PTY LTD.

JSimpleList is currently released under the MIT Licence.

The copyright owner may choose different licensing terms for future versions. Existing versions already released under the MIT Licence remain available under those terms.
