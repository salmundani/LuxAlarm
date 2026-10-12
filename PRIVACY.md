# Privacy Policy for Lux Alarm

_Last updated: October 12, 2026_

Lux Alarm (package `com.dsalmun.luxalarm`) is a free and open-source alarm clock developed by Daniel Salmun. This policy explains what the app does with your data.

In short: Lux Alarm does not collect, transmit, or share any personal data. Everything stays on your device.

## Data collection and sharing

Lux Alarm does not request the Internet permission, so it has no way to send data off your device. The app:

* does not collect any personal or usage data,
* does not contain ads, analytics, crash reporting, or any other third-party SDK,
* does not require an account or sign-in,
* does not share any data with the developer or with third parties.

## Data stored on your device

To work as an alarm clock, the app stores the following only in its private storage on your device:

* your alarms: time, repeat days, whether each alarm is enabled, the selected ringtone, volume and vibration preference,
* your settings: the light level required to turn off an alarm.

You can delete this data at any time by deleting alarms in the app, clearing the app's storage in Android settings, or uninstalling the app.

## Light sensor

The app reads your phone's ambient light sensor in two places: while an alarm is ringing, to decide whether the room is bright enough to turn the alarm off, and on the settings screen, to show the current light level while you choose a threshold. Readings are used in the moment only. They are never stored or transmitted.

## Device backups

If you have enabled Android's backup feature, Android may include the app's stored alarms and settings in your device backup, in your own Google account. That backup is operated by Google and controlled by you in Android settings. The developer has no access to it.

## Permissions

Lux Alarm uses the following Android permissions, all of them solely to make alarms ring reliably:

* `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`: ring at the exact time you set.
* `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`: show the ringing alarm, including over the lock screen, and upcoming alarm notifications.
* `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`: keep the alarm sounding until you turn it off.
* `RECEIVE_BOOT_COMPLETED`: reschedule your alarms after the device restarts.
* `WAKE_LOCK`, `VIBRATE`, `DISABLE_KEYGUARD`: wake the screen, vibrate, and show the alarm when the device is locked.

## Children's privacy

Lux Alarm does not collect data from anyone, including children.

## Changes to this policy

If this policy changes, the updated version will be published at this same address with a new "Last updated" date.

## Contact

For questions about this policy, open an issue at <https://github.com/salmundani/LuxAlarm/issues>.

The full source code is available at <https://github.com/salmundani/LuxAlarm>.
