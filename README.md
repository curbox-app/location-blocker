# Location Blocker

A minimal Android location requirement provider for Curbox API 3. Curbox blocks your chosen apps; this app verifies that you are inside a saved place before Curbox grants access.

## Set up

1. Install this app and Curbox with API version 3 or newer.
2. Open **Setup & connection**, choose the intended Curbox installation, and tap **Approve in Curbox**. Approval grants the full Curbox public API; this app uses provider registration and removal only.
3. Allow precise location and turn on Android location services.
4. Add a place using your current location or latitude/longitude. Choose its radius. Optional settings include maximum uncertainty, rejection of Android mock locations, weekdays, and access hours (including overnight windows).
5. Tap **Publish to Curbox** for the saved place.
6. In Curbox, configure an app blocker group and select its apps. Open **Warning Screen Configuration → Require effort to unlock → Require another app**, then choose **At [place]**. Save and enable the group. Enable Curbox's accessibility service.
7. Keep Location Blocker outside your blocked apps. Test with **Check here**, then test the actual Curbox warning screen.

Each group can select one requirement. Multiple saved places can be assigned to different groups. Creating or editing Curbox groups is not exposed by the API.

## Behavior and limits

- Every unlock attempt requests a new location, with a 25-second acquisition timeout. Fixes older than 30 seconds are rejected. Permission denial, disabled providers, missing configuration, and unavailable location deny access.
- The measured distance **plus** reported horizontal accuracy must fit inside the radius. This makes the boundary conservative. Android location accuracy is an estimate, not a guarantee of physical presence.
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

Unit tests cover conservative boundaries, stale/invalid/mock fixes, regular and overnight schedules, week rollover, and time parsing. Instrumentation checks cover setup UI, local persistence, and rejection of unauthenticated exported-activity launches.

```sh
./gradlew :app:connectedDebugAndroidTest
```

Real integration testing still requires an installed Curbox API 3 build: approve/revoke access, publish a place, select it in a group, and trigger actual unlock attempts inside/outside the boundary. Also verify denial with location off, approximate-only permission, removed places, and unavailable provider; test both release/debug Curbox installations when present.
