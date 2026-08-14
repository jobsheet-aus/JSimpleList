# JSimpleList

JSimpleList is a small, straightforward Android app for Todo and Shopping lists.

JSimpleList was originally produced by JobSheet (www.jobsheet.com.au).

It is deliberately local-only: no account, no advertising, no analytics and no server connection.

## Features

- Todo list
- Shopping list with quantity
- Add, edit, complete and delete items
- Completed items remain available to untick
- Pinch to resize list text
- Remembers the selected text size
- Local persistence across app restarts
- Compact Android interface
- Manrope font bundled with the app
- No internet permission

## Requirements

- Android 8.0 (API 26) or later

## Build

The project uses the included Gradle wrapper.

From Windows:
D:\simplelist\gradlew.bat -p D:\simplelist assembleDebug

The debug APK is produced at:
app\build\outputs\apk\debug\app-debug.apk

## Privacy

JSimpleList stores list data locally on the device.
It does not require an account and does not send list contents to a server.

## Licence

JSimpleList is released under the MIT Licence.