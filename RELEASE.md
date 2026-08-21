# Photo Sweep — release checklist

Everything here is a submission-blocking requirement or a claim that must stay true.
Re-read it before every Play Console upload.

## 1. Photo and Video Permissions declaration — REQUIRED at submission

Photo Sweep requests `READ_MEDIA_IMAGES`, which is broad photo access. Google Play's
**Photo and Video Permissions policy** requires broad access to be justified in the Play
Console declaration form, and rejects apps that could work with the system photo picker
instead.

- **Declared use case:** gallery management / cleanup.
  This is a listed acceptable use case under the policy.
- **Why the photo picker is not sufficient:** the app's entire function is to survey the
  *whole* gallery — group it into piles by folder and month, total the bytes each pile
  occupies, and find near-duplicate frames across the library. The photo picker returns a
  user-chosen subset, which cannot answer "which folder is eating your storage".
- **Where to fill it in:** Play Console → App content → Photo and Video Permissions.
- The form must be completed on the **first** submission. It is not optional and the
  listing cannot go live without it.

## 2. Permissions the app declares, and why

| Permission | Reason |
|---|---|
| `READ_MEDIA_IMAGES` | Read the gallery; address photos for trashing. The product. |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`) | API 30–32 only. `READ_MEDIA_IMAGES` does not exist before API 33, so without this the app is blind on Android 11 and 12 — which are inside its own `minSdk`. |
| `INTERNET` | Google Mobile Ads SDK. No app code opens a socket. |
| `ACCESS_NETWORK_STATE` | Google Mobile Ads SDK. |
| `com.google.android.gms.permission.AD_ID` | Google Mobile Ads SDK. Must be disclosed in Data safety as an advertising identifier. |

### Deliberately not declared

- **`READ_MEDIA_VIDEO`** — v1 is images only. Requesting an unused permission creates a
  Data-safety mismatch, and reviewers check.
- **`MANAGE_MEDIA`** — without it each batch trash shows one system confirmation dialog.
  The app already shows its own confirm screen, so that is acceptable friction, and it keeps
  a special-access declaration off the first submission. Revisit in 1.1 if reviews complain.
- **`READ_MEDIA_VISUAL_USER_SELECTED`** — not declared, but the platform adds it implicitly
  on API 34+ to any app requesting `READ_MEDIA_IMAGES`. Verified by `dumpsys package` on a
  device running API 36. That implicit grant is how partial access is detected.

### Permissions merged in by the Ads SDK

The manifest declares five. The built APK contains more, injected by
`play-services-ads` and not removable without breaking it:

```
android.permission.ACCESS_ADSERVICES_AD_ID
android.permission.ACCESS_ADSERVICES_ATTRIBUTION
android.permission.ACCESS_ADSERVICES_TOPICS
android.permission.WAKE_LOCK
android.permission.FOREGROUND_SERVICE
com.simobr.photosweep.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

Verify the full list before each release with:

```
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
```

If that list grows, the Data safety form may need updating.

## 3. AdMob App ID — build-blocking

`MobileAdsInitProvider` reads `com.google.android.gms.ads.APPLICATION_ID` from the manifest
while the process is binding, and **throws if it is missing, killing the app before any of
its own code runs**. There is no code path that can catch it. This was found by launching on
a real device; nothing in the unit suite could have caught it.

The value is a build-type manifest placeholder:

- **debug** — Google's published sample App ID, `ca-app-pub-3940256099942544~3347511713`.
  Serves test ads only.
- **release** — read from the `photosweep.admobAppId` Gradle property. If it is absent the
  release build **fails** at `preReleaseBuild`, because shipping the sample ID would serve
  test ads to real users and breach the AdMob policy.

Set it before the first release build, in `local.properties` or `~/.gradle/gradle.properties`:

```
photosweep.admobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
```

Get the value from AdMob → Apps → your app → App settings → App ID. It is not the ad unit ID.

## 4. Claims made in-product that must stay true

The app makes verifiable statements about itself on the permission screen and in Settings.
A user can check any of them in about fifteen seconds, and a false one is a Play Store
misrepresentation risk as well as simply being a lie.

- ✅ "Your photos never leave your phone. … The only thing that uses the network is
  Google's ad service."
- ✅ "Photo Sweep has no upload code. The only network traffic is Google's ad service."
- ❌ **Never restore** the earlier draft copy: *"The app ships without the internet
  permission, so it cannot send anything anywhere"* and *"Photo Sweep has no internet
  permission — check it yourself."* Both are false in any build containing AdMob — the
  Mobile Ads SDK requires `INTERNET` — and both are contradicted by the system settings
  screen the copy invites the user to open.

If ads are ever removed, these strings may be strengthened again — not before.

## 5. Android 14+ partial access

On API 34+ the system may grant access to selected photos only. The app must never render a
degraded pile list in that state: a gallery cleaner showing 12 of 4,000 photos looks broken
and gives the user no way to understand why.

Handled in `MediaPermission.resolve` → `MediaAccess.PartialSelectionOnly` →
`PartialAccessScreen`, which states the visible count and links to the app's settings page.

## 6. Trash, retention, and the storage claim

**Photo Sweep does not implement its own trash and must not.** Everything goes through
`MediaStore.createTrashRequest` / `createDeleteRequest` as an IntentSender the system
executes after showing its own dialog.

Copying condemned photos into app-private storage would temporarily **double** the space
they occupy — inside a storage cleaner — and would pull them out of Google Photos' own bin,
removing a recovery route the user already had. Android's trash costs nothing, already runs
30 days, and is visible from Files and Google Photos.

**There is no retention setting.** The 30 days belong to the system and are not
configurable. Screen 12 must never offer "keep deleted photos for 7 / 30 / 60 days"; the row
is a static line instead:

> Trash is Android's own. Swept photos also appear in Google Photos → Bin.

**The storage claim.** A trashed photo still occupies storage until the trash empties. The
success screen and the home stat are therefore labelled **SWEPT**, never "SPACE FREED", with
one line underneath — "Space frees up when Trash empties." — and an "Empty Trash now" action
for users who want the space immediately. Reverting that label to "SPACE FREED" earns
one-star reviews from users who check Settings → Storage and see no change, and they would be
right.

**Byte totals are never assumed.** After the system dialog closes, the app re-queries
MediaStore with `QUERY_ARG_MATCH_TRASHED = MATCH_ONLY` and reports only what the content
resolver confirms. The dialog's result code is not consulted at all. A partial grant leaves
the unmoved photos marked.

## 7. Before upload

- [ ] Real AdMob App ID set (section 4) — the release build enforces this.
- [ ] Photo and Video Permissions declaration submitted (section 1).
- [ ] Data safety form matches the permission table (section 2), including the advertising ID.
- [ ] `aapt2 dump permissions` on the release APK reviewed for new entries.
- [ ] In-product privacy claims re-read against the shipped dependency list (section 4).
- [ ] Play Store icon: `design/play-store-icon-512.png` (512×512).
