# Location Blocker

A minimal Android location requirement provider for Curbox API 3. Curbox blocks your chosen apps; this app verifies that you are inside a saved place before Curbox grants access.

## Set up

1. Install this app and Curbox with API version 3 or newer.
2. Under **Finish setting up**, tap **Connect** and approve the app in Curbox, then allow precise location and make sure location is on. Approval grants the full Curbox public API; this app uses provider registration, removal, and listing only.
3. Tap **Add a place** and choose whether blocked apps can be unlocked **Only here** or **Anywhere but here** (to keep apps locked at work, for example). Stand in the middle of it, and tap **Use where I am now** (or type coordinates). Choose how big the area is and, optionally, the days and times it counts.
4. Saved places are added to Curbox automatically once it's connected. Places saved earlier are added as soon as you connect.
5. In Curbox, configure an app blocker group and select its apps. Open **Warning Screen Configuration → Require effort to unlock → Require another app**, then choose **At [place]** or **Away from [place]**. Save and enable the group. Enable Curbox's accessibility service.
6. Keep Location Blocker outside your blocked apps. Use **Test here** to try it, then test the actual Curbox warning screen.

Each group can select one requirement. Multiple saved places can be assigned to different groups. Creating or editing Curbox groups is not exposed by the API.

## Behavior and limits

- Every check listens to GPS, network, and (Android 12+) fused location at the same time, using live fixes only, never cached ones. It approves as soon as one fix passes; otherwise it keeps listening for up to 20 seconds and judges the most precise fix it got. Fixes older than 30 seconds, permission denial, disabled location, missing configuration, and no fix all deny access.
- A fix passes when its distance from the center **plus half** its reported uncertainty fits inside the area (for **Anywhere but here** places, when its distance **minus half** the uncertainty is outside it), and its uncertainty is within the place's required precision (half the area size, 20–100 m, unless changed under **More options**). Android location accuracy is an estimate, not a guarantee of physical presence.
- A place's center is the precision-weighted average of several live fixes (up to 30 seconds, or until five fixes reach ±12 m), so one noisy reading doesn't shift it.
- Mock fixes are rejected by default. This does not make the app resistant to rooted-device tampering or all location spoofing.
- Access hours use the device's local date/time. Overnight hours belong to the day on which they start; equal start/end means the full selected day. Device time changes affect this schedule.
- Only the selected Curbox package may launch a successful provider check, using a verified activity caller and a matching saved requirement ID. Approvals echo only the current request ID. Curbox validates its own authorization, attempt expiry, and warning configuration.
- Places are immutable. Add and publish a replacement to change the location, radius, or policy; select it again in Curbox. Removing a place fails closed even if remote deregistration is unavailable.
- **This is an unlock-time check, not continuous geofencing.** Departing does not revoke an already granted Curbox access interval. Use Curbox's “On each open” behavior or a short interval for more frequent checks. Curbox owns unlock duration and proceed limits.
- Publishing does not attach a place to a group or enable enforcement. Existing group schedules and overlapping restrictions still apply.
- No account, network permission, background location, foreground service, or location history. Coordinates remain in private device storage; Android backup is disabled.

## Build and verify

Requires JDK 21, Android SDK 37, and the checked-in Gradle wrapper.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Unit tests cover the area boundary, default precision, stale/invalid/mock fixes, regular and overnight schedules, week rollover, and coordinate parsing. Instrumentation checks cover setup UI, local persistence, and rejection of unauthenticated exported-activity launches.

```sh
./gradlew :app:connectedDebugAndroidTest
```

Real integration testing still requires an installed Curbox API 3 build: approve/revoke access, publish a place, select it in a group, and trigger actual unlock attempts inside/outside the boundary. Also verify denial with location off, approximate-only permission, removed places, and unavailable provider; test both release/debug Curbox installations when present.
