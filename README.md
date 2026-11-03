# Dr. Eh Android

**Technical README**

Dr. Eh is a portrait Android student productivity prototype built around a study companion. It combines task and calendar screens with lightweight pet-care progression, XP rewards, reviewer notes, an optional AI chat, and a calendar-based Google Classroom due-date import.

This project is a local Android app. It does not have a Dr. Eh backend, a hosted account system, or a direct Google Classroom API integration. The details below describe the current source behavior, including limitations that matter when building or publishing it.

## Features at a glance

- Home screen with the animated companion, health, energy, mood, hunger, level, and XP.
- Tasks screen with manually added tasks, imported class-calendar tasks, completion, and a completed list.
- Monthly calendar with local events and completion tracking.
- Rewards screen with lifetime-XP unlocks and a coin shop.
- Profile screen with local profile details, progression summary, achievements, and daily-decay settings.
- Reviewer folders, note files, and a generated reviewer flow.
- “Ask Dr. Eh” chat, with a local rule-based fallback and an optional OpenAI-compatible chat-completions endpoint.
- Google Classroom calendar import through Android's calendar provider or a user-provided Google Calendar secret iCal URL.
- Compose navigation transitions, press feedback, and animated GIF companion assets.

## Technology

| Area | Current implementation |
| --- | --- |
| Language | Kotlin 2.0.20 |
| UI | Jetpack Compose, Material 3, Compose BOM 2024.09.03 |
| Navigation | Navigation Compose 2.8.2 |
| Images and GIFs | Coil Compose 2.7.0 and Coil GIF 2.7.0 |
| Android Gradle Plugin | 8.5.2 |
| Gradle wrapper | 8.9 |
| Java bytecode target | 17 |
| Android SDK | Compile/target SDK 34; minimum SDK 24 |
| Namespace / application ID | `com.dreh.app` |

## Build and install

Install Android Studio or the Android command-line tools with Android SDK Platform 34, and use a JDK 17 runtime. Open this project directory in Android Studio, allow Gradle sync, then run the `app` configuration on a device or emulator.

PowerShell commands from the project root:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

The debug APK is normally written to `app/build/outputs/apk/debug/app-debug.apk`. `local.properties` contains a machine-specific Android SDK path; Android Studio can create it, and it should not be committed.

## Source layout

```text
app/src/main/java/com/dreh/app/
  MainActivity.kt             Activity entry point and state save on stop
  AppState.kt                 In-memory profile, task, calendar, and progression model
  StateStore.kt               Local SharedPreferences persistence
  ai/
    AiConfig.kt               API settings and OpenAI-compatible HTTP client
    Persona.kt                Dr. Eh prompt, reviewer format, and offline replies
  gclass/
    GClass.kt                 iCal parser, calendar-provider reader, sync store and merge logic
  ui/
    Nav.kt                    Navigation graph and route transitions
    Figma.kt                  DesignFrame, fixed-coordinate helpers, tabs and shared controls
    MainScreens.kt            Home, tasks, rewards, profile, sync dialog
    CalendarScreen.kt         Month grid and local calendar events
    ReviewerScreens.kt        Folders, files, and note editor
    AiScreens.kt              AI reviewer and API settings dialog
    ChatScreen.kt             Companion chat, message bubbles and keyboard layout
    Companion.kt              Home companion, bars, speech and animation
    GifSupport.kt             Animated image loading helpers
    Theme.kt                  Colors, type and Material theme
  res/drawable/                Icons, illustrations, avatars and companion GIFs
  AndroidManifest.xml          INTERNET and READ_CALENDAR permissions
```

The UI follows a 412 × 917 design coordinate space. `DesignFrame` scales this frame to the safe area on the device, and `FBox`, `FText`, `FImage`, and `Modifier.at` place UI elements within that coordinate space. `Modifier.tap` applies a small press-scale animation. Navigation between destinations uses short fade and horizontal-slide transitions.

## App structure and behavior

### Navigation

`DrEhApp` creates one process-wide `AppState`, loads it through `AppStateHolder`, and constructs shared AI and GClass stores. The bottom tabs are Home, Tasks, Rewards, and Profile. Additional routes open Calendar, the companion chat, the AI reviewer, reviewer folders, and notes. In the current UI, the folder/file Reviewer route is exposed through the companion's dead-state recovery action rather than a permanent tab.

The splash route chooses the onboarding or Home route based on the local session flag. Sign-up and log-in are prototype flows; they do not create or validate a server account.

### Progression model

`AppState` owns the companion bars, tasks, calendar events, coins, streak, levels, and rewards. Completing an unfinished task grants 10 XP and improves the companion's health, energy, and mood. Completing a calendar event grants 5 XP. Creating a reviewer file grants 15 XP. Level thresholds start at 30 XP and increase by 15 XP per level; levelling up awards 25 coins and restores some bars.

The rewards screen contains lifetime-XP gates at 10, 30, 60, 90, 120, 150, 180, and 210 XP. Claiming an unlocked card grants 10 coins and applies that card's perk. The coin shop spends the current coin balance on companion-bar and streak-shield perks. Daily bar decay is applied when `AppState.onAppOpen()` detects a new day and can be disabled in Profile → Settings.

These are app progression effects only. Completing a Dr. Eh task does not submit work to Google Classroom.

### Local persistence

`StateStore` stores profile fields, the local session flag, progression, claimed rewards, tasks, and calendar events in the `dr_eh_state` SharedPreferences file. `MainActivity.onStop()` asks `AppStateHolder` to save that state. The sign-up/log-in flow is local and is not backed by a user service.

AI settings are stored in `dr_eh_ai`. GClass links, selected device calendar IDs, sync markers, imported UIDs, and imported-task ID mappings are stored in `dr_eh_gclass`.

Current persistence gaps:

- Reviewer folders and note-file contents are in memory and are not restored by `StateStore` after a process restart.
- Chat messages are held by the chat screen and are not persisted as a conversation history.
- API keys and prototype credentials are kept in ordinary app preferences, not encrypted storage. Avoid using a valuable production API key in this prototype.

## Google Classroom calendar sync

### What it reads

GClass currently reads Google Calendar data associated with Classroom; it does not call the Classroom REST API. Google's student help describes class calendars as showing classwork due dates and class events. The app imports calendar event titles and due dates into the Dr. Eh task list. It does not read grades, submission state, attachments, or whether work has been turned in, and it does not write task completion back to Google.

Official references:

- [Google Classroom Help: View due dates and events in a calendar](https://support.google.com/edu/classroom/answer/6272985?hl=en)
- [Google Classroom API: courses.courseWork.list](https://developers.google.com/workspace/classroom/reference/rest/v1/courses.courseWork/list)
- [Google Classroom API authorization scopes](https://developers.google.com/workspace/classroom/guides/auth)

### Supported sources

1. **Phone calendars.** The app requests Android's `READ_CALENDAR` permission and lists Google calendars on the device that look like class calendars. The student chooses which calendars to import. The selected event query includes the previous three days and the next 120 days.
2. **Secret iCal links.** A student can paste a Google Calendar “Secret address in iCal format”. The app accepts HTTPS links on `calendar.google.com` whose path is an iCal `.ics` path, and normalizes `webcal://` to HTTPS. Each saved URL is a bearer secret and is kept locally in app preferences.

The sync dialog is opened from the Tasks screen's top-left control. A refresh control appears on the Tasks screen when at least one source is linked. Sync also runs when the Tasks destination opens, subject to a 15-minute successful-sync interval and a two-minute retry cooldown after an attempt. This is not a continuous background job.

### Sync flow

1. The app reads selected calendar IDs and saved iCal URLs from `GClassStore`.
2. iCal downloads run on `Dispatchers.IO` with connect and read timeouts. Each feed is checked for a VCALENDAR document before parsing.
3. The parser unfolds RFC 5545 continuation lines and reads the event UID, summary, start time, timezone, and status. It ignores cancelled events. Device all-day event timestamps are normalized to the device's local day.
4. First-time imports are limited to events due within the previous three days or next 120 days. Known iCal UIDs can still refresh if the feed continues to include them.
5. Events with the same normalized title and due instant are merged across configured sources. A stable event-derived task ID and a UID-to-task mapping let later syncs update the task instead of adding another row. Old 32-bit imported IDs are recognized and migrated when possible.
6. Updates change title, due label, or time details while preserving the user's Dr. Eh completion flag.
7. The result reports added/updated task counts and source failures. A failed or partial sync does not update the successful-sync timestamp, so the UI can offer a retry.

This integration is one-way and calendar-based. Direct API access would be a separate implementation requiring a Google Cloud project, OAuth consent, student authorization scopes, and the Classroom API. The current app has no such OAuth client configuration.

## AI chat and reviewer

`AiClient` sends OpenAI-compatible chat-completions requests to the configured base URL plus `/chat/completions`. The default endpoint is Groq's OpenAI-compatible endpoint and the default model is `openai/gpt-oss-20b`; there is no API key in the source. A user enters their own key from the gear in Ask Dr. Eh or the API card in AI Reviewer. The key, endpoint, and model are saved in `dr_eh_ai` on the device.

If the key is blank, Ask Dr. Eh uses `Persona.offlineReply`, a small local rule-based response path. AI Reviewer generation requires an API key. When configured, the AI reviewer sends the chosen prompt and note content to the configured endpoint. The current UI uses the first file in the first folder as its source; the visible “pick” prompt does not provide a file picker yet. The generated text can be appended to that note.

Do not commit credentials. The API key is stored in ordinary SharedPreferences and is not encrypted by this app. Review the provider's terms before sending private notes or chat content to an external endpoint.

## Permissions and network use

| Permission | Use |
| --- | --- |
| `android.permission.INTERNET` | Fetch iCal feeds and call the configured AI endpoint |
| `android.permission.READ_CALENDAR` | Read the selected calendars from Android CalendarProvider |

The app does not request calendar write access. It stores local events in its own state; it does not add events to the phone's Google Calendar.

## Troubleshooting

- **No phone calendars appear:** sync the school Google account in the Google Calendar app, reopen GClass settings, and check that calendar access is allowed.
- **The iCal link is rejected:** use the Google Calendar “Secret address in iCal format”, including the `.ics` path. Treat that link like a password.
- **A calendar request fails:** check the internet connection, permission, and that the saved link is still valid. Open sync settings and retry; a failed source is not treated as a successful sync.
- **No AI response from the provider:** enter a valid API key, base URL, and model in the gear/API settings. Chat can still answer using its local fallback without a key; AI Reviewer generation cannot.
- **Build cannot resolve Android Gradle Plugin:** Gradle needs access to the configured plugin repositories unless the exact plugin and transitive dependencies are already cached.

## Repository hygiene

Build outputs, Android Studio metadata, local SDK paths, and local secret files should stay untracked. `.gitignore` covers common generated and machine-local files. Do not add API keys, secret iCal addresses, or personal calendar data to commits.
