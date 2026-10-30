# Watch Media Remote

Control whatever is playing on your Android phone (Spotify, YouTube Music, podcasts, anything with a media session) from a Pixel Watch.

- **Crown** changes the phone's media volume (clockwise = louder), with a volume arc on the right edge.
- **Prev / Play-Pause / Next** buttons, with artwork (dimmed backdrop) and the source app's name.
- **Tile** (Wear OS's home-screen widget) with the same controls plus volume `-` / `+`, since tiles can't read the crown.

## How it works

```
phone: MediaSyncService (notification listener) ──▶ MediaSessionManager.getActiveSessions
        └─ MediaBridge.publish ── DataClient /now_playing (title, artist, app, state, volume, artwork) ──▶ watch
watch:  crown / buttons ── MessageClient /command ("toggle", "next", "prev", "vol_set:N") ──▶ phone CommandReceiver
```

Both apps share one `applicationId` (`com.ajudi46.tempo`), which the Wearable Data Layer requires. They must also be signed with the same key (debug builds from the same machine are).

## Build and install

1. Open this folder in Android Studio (it creates the Gradle wrapper and syncs).
2. Run the `mobile` configuration on your phone.
3. Run the `wear` configuration on the watch. Enable Developer options > ADB debugging and Debug over Wi-Fi on the watch, then `adb pair` and `adb connect` it.
4. On the phone, open **Watch Media Remote** and tap the button to grant **notification access**. This is what allows reading and controlling other apps' media sessions.

## Add the widget

On the watch face, swipe sideways to the end of the tile carousel, tap **+**, and pick **Media Remote**. Or long-press the watch face, swipe to Tiles, and add it.

## Notes

- This sources from the phone's media session, so the watch must be connected to the phone via Bluetooth.
- Volume is the phone's *media* stream volume.
- `QUERY_ALL_PACKAGES` is used to show the playing app's name; remove it (and the label lookup) if you plan to publish on Play.
- If volume or state stops syncing, battery optimisation may be killing the notification listener on the phone; set the app to "Unrestricted" battery use.
