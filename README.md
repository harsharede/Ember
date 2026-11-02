# Ember

A calm Android launcher. One Home screen, a status rail instead of a status bar, and a notification light instead of
banners.

- **Home** is a Today page: the day and date, Suggestions (the apps you open most, recent use counting most) and a clock.
- **The rail** on the right shows the time, date and an icon for each thing that's on: Wi-Fi, mobile signal, VPN,
  Bluetooth, airplane mode, Do Not Disturb, silent/vibrate, location, power saving and battery. Swipe any icon sideways
  to hide it; Settings brings it back. The orange notification count opens to show which apps have notifications.
  Below it, a four-app dock (hold a place to change its app).
- **Swipe up** for the app drawer: every app A to Z (the keyboard opens so you can type straight away) or, one swipe
  across, the same apps in groups. Pull it down to close.
- **Widgets**: hold Home › Add widget. Edit Home to move, resize or remove them.
- **Material You**: colours come from your wallpaper (or Ember's orange, in Settings).
- **Swipe down** (or tap the count) for the notification page: a timeline under Now, Earlier today, Yesterday and
  Older. Reply right there when the app allows it, or tap **Remind me** for 3, 5, 15 or 30 minutes or an hour. Swipe a
  card away to dismiss it. Push up past the end to close. Silent notifications and ones that can't be dismissed
  are folded into their own sections, and the latter have a Turn off button that opens that channel's settings.
- **The pill.** When a notification arrives while you're using the phone, a small pill glows at the top left, like an
  old phone's notification light, instead of a banner over what you're doing. Tap it or pull it down to read, reply or
  snooze in a pop-up; flick it sideways to put it out.

Made for the Galaxy S23 Ultra, works on any phone with Android 12 or later.

## Setup

1. Install the APK and open **Ember settings**.
2. Turn on, in order: **Use Ember as your Home app**, **Notification access**, **Display over other apps**.
3. If Notification access is greyed out (Android 13+ does this for apps installed from a file): App info › ⋮ ›
   Allow restricted settings, then try again.
4. So Android's own banners don't cover the screen as well as the pill: Samsung Settings › Notifications ›
   Notification pop-up style, and turn pop-ups off (or per app).

## Privacy

Ember has no internet permission. Notifications, app usage and settings never leave the phone. It asks for:

| Permission | Why |
|---|---|
| Notification access | The count, the notification page, reply, reminders and the pill |
| Display over other apps | Showing the pill while another app is open |
| Query all packages | Listing your apps (every launcher needs this) |
| Network state | The Wi-Fi, mobile and VPN icons |
| Request delete packages | Uninstall from the drawer; Android always asks first |

## Building

Every push builds debug and release APKs and runs the unit tests (GitHub Actions › Build › artifacts). Locally:
`./gradlew :app:assembleDebug` with the Android SDK installed.

The release build is signed with the throwaway key in `app/debug.keystore` so anyone can install it. **Before publishing
to the Play Store, create your own upload key** and keep it out of the repository.

## License

MIT, see [LICENSE](LICENSE).
