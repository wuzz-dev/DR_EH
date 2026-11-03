# Dr. Eh: User Guide

This guide explains how to use the current Dr. Eh Android app. Dr. Eh is a student planner with a study companion: tasks, a calendar, reviewer notes, rewards, and optional AI features.

## First launch

1. Open the app. A short splash screen leads to the start page.
2. Create a local profile or log in with the profile created on this device.
3. Finish the profile steps to reach Home.

The sign-up and log-in screens are part of the prototype. They do not create an online account or sync your profile to another device.

## The four main tabs

The bottom bar stays available on the main screens:

- **Home:** Dr. Eh, his bars, level and XP, plus the calendar and chat entry points.
- **Tasks:** open tasks, completed tasks, manual task entry, and Google Classroom calendar sync settings.
- **Rewards:** XP-unlocked reward cards and a shop that spends coins.
- **Profile:** profile details, level, streak, achievements, and settings.

Other screens open from these tabs: Calendar, Ask Dr. Eh, AI Reviewer, reviewer folders, and notes.

## Home and the companion

Dr. Eh has health, energy, mood, and hunger bars. Tap the companion for a small mood lift. His state changes as the bars rise or fall. Daily bar decay can be turned off in **Profile → Settings**.

The companion responds to study actions:

- Finishing a task gives **10 XP** and improves some bars.
- Completing a calendar event gives **5 XP**.
- Creating a reviewer file gives **15 XP**.
- Reaching a new level gives **25 coins** and restores some bars.

These rewards only affect Dr. Eh. They do not submit an assignment to Google Classroom.

## Tasks

### Add and complete your own task

1. Open **Tasks**.
2. Tap **Add a task** and enter its title.
3. Tap a task to see its details. Use its checkbox to mark it done.
4. Completed tasks are shown under **Completed**.

### Connect a Classroom calendar

The app can read calendar events that Classroom puts in Google Calendar. It does not sign in to Classroom or read the Classroom assignment page directly.

#### Option A: calendars already synced to your phone

1. Open **Tasks** and tap the top-left calendar control.
2. Allow calendar access when Android asks.
3. Select the class calendars you want Dr. Eh to read.
4. Tap **Sync from phone now**.

If no class calendars are listed, open Google Calendar and make sure your school account and class calendars have synced. Return to Dr. Eh and open the sync settings again. You can change the selected calendars later.

#### Option B: a secret iCal link

1. In Google Classroom, open a class and go to **Classwork**, then open its Google Calendar.
2. In Google Calendar, open that class calendar's settings and find **Integrate calendar**.
3. Copy **Secret address in iCal format**.
4. In Dr. Eh, open **Tasks → top-left calendar control**.
5. Paste the address under **Secret iCal link** and tap **Add + Sync**.

Treat the secret link like a password: anyone who has it may be able to read that calendar. Dr. Eh stores it on this device and does not display the full link in its saved-calendar list. To remove a saved feed, tap its calendar row in the sync settings.

#### Refresh and what appears

When a source is linked, use the refresh icon on Tasks to sync it again. The app also checks when Tasks opens, with a cooldown so it does not repeatedly fetch the same calendar. Newly imported events appear as tasks; later syncs can update their titles and due dates while keeping the Dr. Eh completion mark.

The app imports calendar event titles and dates. It does **not** read grades, attachments, submission status, or whether an assignment is turned in. Marking a task done in Dr. Eh does not mark it done in Classroom. iCal first-time imports focus on the last three days and the next 120 days. The phone-calendar option uses the same date range.

## Calendar

From Home, open the calendar icon to see a month. Use the arrows to change months and tap a date to view its events. Choose **New event**, enter a title and time, and save. Tapping an event marks it complete and gives 5 XP. These are Dr. Eh's local events; the app does not write them into Google Calendar.

## Reviewer notes

The folder/file Reviewer route is currently exposed as the companion's recovery action when Dr. Eh is dead: tap **Make a reviewer** on the Home recovery screen. There is not a permanent Reviewer tab yet. In the Reviewer screen, create a folder, open it, then create a file. Open the file to edit its note text.

From **Calendar**, tap **Ask Dr. Eh** to open AI Reviewer. It can generate a study reviewer and append the result to the first note file. From **Home**, Ask Dr. Eh opens the chat screen instead.

Current limitations: folders and note text are held in memory and are not restored after the app process is restarted. The AI Reviewer uses the first file in the first folder; the visible pick prompt is not a file picker yet. Keep important notes copied somewhere safe until note persistence is added.

## Ask Dr. Eh and AI Reviewer

### Ask Dr. Eh chat

Open **Ask Dr. Eh** from Home. Type a message and tap the arrow. Without an API key, the app uses a small local reply mode that can respond to common questions about tasks, streaks, XP, rewards, and companion bars.

For full AI chat, tap the gear and enter an API key. The default provider settings point to Groq; the app also accepts another OpenAI-compatible base URL and model. Your key is stored in app preferences on this device. Chat messages are sent to the configured provider when full AI is enabled.

### Generate a reviewer

Open Calendar and use its Ask Dr. Eh/AI Reviewer entry. Add a provider key in the API settings, provide an optional request or tone, and generate. The generated reviewer is intended to include key ideas, terms, self-check questions, and common traps.

At the moment, AI Reviewer takes the first file in the first reviewer folder as its note source. If there is no file, it may have no note content to work from. The current API settings use the first-party endpoint/model defaults but require your own key; AI Reviewer generation needs the key, unlike chat's local fallback.

Only send notes to an AI provider if you are comfortable with that provider receiving their contents. The app does not operate its own AI server.

## Rewards and profile

Lifetime XP unlocks reward cards at 10, 30, 60, 90, 120, 150, 180, and 210 XP. Claiming an unlocked reward grants 10 coins and applies the card's companion perk. The shop uses coins for bar boosts and streak shields. The Profile page shows level, XP, coins, streak, completed tasks, reviewer count, and achievement chips.

The Settings dialog can turn daily bar decay on or off or reset progression. Resetting progression returns XP, coins, streak, and sample task completion to their starting values.

## Saving and privacy

Most profile, progression, task, event, and claimed-reward data is saved locally on this device when the app stops. There is no Dr. Eh cloud account or cross-device sync. Reviewer folders, note contents, and chat history are not currently restored after restart.

The AI API key and secret iCal URLs are stored in ordinary app preferences, not encrypted storage. Keep those credentials private. The app needs internet for iCal downloads and configured AI requests, and calendar permission only for the phone-calendar option.

## Quick troubleshooting

| Problem | What to try |
| --- | --- |
| No class calendars listed | Sync the school account in Google Calendar, check Android calendar permission, then reopen Dr. Eh's sync settings. |
| iCal link rejected | Copy the Google Calendar **Secret address in iCal format** and make sure it ends in `.ics`. |
| Sync says some calendars failed | Check internet access, recheck phone calendar permission, and verify the saved iCal address. Open sync settings and retry. |
| Ask Dr. Eh uses short local replies | Add a valid key in the gear settings to use the configured AI endpoint. |
| AI Reviewer cannot generate | Add a valid API key and confirm the endpoint and model in API settings. |
| Notes disappear after restart | This version does not persist reviewer folders/files yet; keep a backup of important note text. |
