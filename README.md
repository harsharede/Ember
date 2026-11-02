# Ember

A calm Android launcher. One Home screen, a status rail instead of a status bar, and a notification light instead of
banners.

- **Home** is a Today page: the day and date, a **Smart Stack** and your widgets. The Smart Stack holds widgets from your
  apps (news, Reddit, weather, calendar, mail and others; whatever widgets your apps offer) and flips to the next every 5,
  7 or 10 seconds while Home is on screen. "Pick for me" fills it with the best widget from each app, skipping search bars and
  shortcuts; swipe sideways to flip by hand. Besides widgets it has Ember's own cards: Weather, People (recent chats, starred contacts),
  Now playing, Up next (calendar), App suggestions, and app feeds built from apps' notifications (new YouTube videos,
  Reddit replies, Instagram, news), which work even for apps without widgets. The pencil under the stack chooses and
  orders them; a card asks for its permission only when you switch it on.
- **Wallpapers from your photos** (Settings › Wallpaper): every 30 minutes or three times a day, Ember picks one of your
  camera photos, preferring sharp ones and portrait-mode shots with a blurred background, and crops it around the
  subject. 👎 swaps it at once and it never returns, ↻ puts a new one on, ♥ makes it come back more often. Timed changes
  wait for the screen to go off or for Home, since a new wallpaper changes the colours and restarts open apps' screens.
- **The rail** on the right shows the time and an icon for each thing that's on: Wi-Fi, mobile signal, VPN,
  Bluetooth, airplane mode, Do Not Disturb, silent/vibrate, location, power saving and battery. Swipe any icon sideways
  to hide it; Settings brings it back. The orange notification count opens to show which apps have notifications.
  Below it, the dock: as many apps as you like, filling the room left (it scrolls, with an arrow, when the capsule above
  grows). Hold an app to change, move or remove it. - **Wallpaper button**: tiny, in the bottom-left corner. Tap it for where and when the photo was taken (scrolling past;
  tap to open the photo), ♥, new photo and 👎.
- **Customize look** (hold Home): rail width, dock height, rail and card transparency, live.
- **Pages**: hold Home › Add page for blank pages to fill with widgets; swipe sideways between them.
- **Swipe up** for the app drawer: every app A to Z (the keyboard opens so you can type straight away) or, one swipe
  across, the same apps in groups. Pull it down to close.
- **Groups** in the drawer: automatic (well-known apps, names, then the app's declared category), and yours to fix:
  hold an app › Move to group, hold a group › Rename or Ungroup. **Sort with AI** sends your app list (names and
  package names only) to Claude, ChatGPT or Gemini through the share sheet or clipboard; share or paste the reply back
  and Ember shows the groups before applying them. Ember itself sends nothing.
- **Widgets**: hold Home › Add widget. Edit Home to move, resize or remove them.
- **Material You**: colours come from your wallpaper (or Ember's orange, in Settings).
- **Swipe down** (or tap the count) for the notification page: a timeline under Now, Earlier today, Yesterday and
  Older. Reply right there when the app allows it, or tap **Remind me** for 3, 5, 15 or 30 minutes or an hour. Swipe a
  card away to dismiss it. Push up past the end to close. Silent notifications and ones that can't be dismissed
  are folded into their own sections, and the latter have a Turn off button that opens that channel's settings.
- **The lock-screen light.** When something arrives with the screen off, the screen wakes black at the lowest
  brightness and only a small pill glows at the top left, blinking slowly like an old notification LED. Tap to see the
  lock screen, swipe up to unlock into the notification page. It goes dark when the notifications are dealt with, after
  the time you choose, or when you unlock; in a pocket the proximity sensor keeps the screen off. Do Not Disturb is respected.
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

Notifications, photos, app usage and settings never leave the phone. The only network use is the Weather card, if you add
it: it sends a rounded location (or the city you chose) to Open-Meteo for the forecast. It asks for:

| Permission | Why |
|---|---|
| Notification access | The count, the notification page, reply, reminders and the pill |
| Display over other apps | Showing the pill while another app is open |
| Query all packages | Listing your apps (every launcher needs this) |
| Network state | The Wi-Fi, mobile and VPN icons |
| Request delete packages | Uninstall from the drawer; Android always asks first |
| Photos (read only) | Choosing wallpapers from your camera photos; they're analysed on the phone and never leave it |
| Internet | Only the Weather card's forecast (Open-Meteo) |
| Approximate location (optional) | The forecast for where you are; choose a city instead to skip it |
| Contacts (optional) | Starred contacts on the People card |
| Calendar (optional) | The Up next card |
| Photo locations (optional) | Naming where the wallpaper photo was taken, from the photo's own GPS data |
| Set wallpaper | Putting the chosen photo on Home and the lock screen |
| Wake lock, turn screen on | The lock-screen light: waking the screen, keeping it on while it glows, the pocket sensor |

## Building

Every push builds debug and release APKs and runs the unit tests (GitHub Actions › Build › artifacts). Locally:
`./gradlew :app:assembleDebug` with the Android SDK installed.

The release build is signed with the throwaway key in `app/debug.keystore` so anyone can install it. **Before publishing
to the Play Store, create your own upload key** and keep it out of the repository.

## License

MIT, see [LICENSE](LICENSE).
