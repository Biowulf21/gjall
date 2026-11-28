# Gjall Product Requirements Document

**Status:** living product baseline
**Date:** 2026-11-28
**Product:** Gjall, Android SMS/MMS app (fork of QUIK / QKSMS)
**Current version:** 4.7.2 (versionCode 2246)

## 1. Product Summary

Gjall is a personal Android messaging app. It is a GPL-3.0 fork of QUIK, which
continues QKSMS. It reads and writes Android's Telephony provider, keeps a local
Realm index for fast lists and search, and adds Gjall-only features on top. The
repository does not establish store publication or testing beyond the owner's
devices. Requirements below describe product goals; check implementation claims
against the code.

## 2. Problem and Users

### Problem

Stock and OEM messaging apps differ across devices, push RCS/chat features, and
do not match the rest of the owner's app collection. Open-source alternatives
work but look dated and lack quick message actions and inline scheduling.

### Primary users

| User | Need | Surface |
|---|---|---|
| Owner (daily driver) | Reliable SMS/MMS, fast search, scheduling, consistent design | The whole app |
| Sideloading Android users | A private, free, open-source SMS app | Signed APK from GitHub releases |
| Contributor | Build, understand and patch the fork | This repository |

## 3. Goals

1. Send and receive SMS and MMS reliably as the default SMS app.
2. Keep Android's Telephony provider the source of truth; Gjall's database must be
   rebuildable from it with a full sync.
3. Make common actions one or two taps: reply, react, forward, schedule, archive.
4. Match the shared design language in [DESIGN.md](DESIGN.md).
5. Stay licence-compliant: GPL-3.0-or-later, upstream notices kept, source
   published for every distributed APK.

## 4. Non-goals

- RCS, chat features, end-to-end encryption or any server-side component.
- Accounts, cloud sync, analytics, ads or crash-reporting SDKs.
- Paid features or in-app purchases (donations only; see PRODUCT.md).
- Diverging from QUIK's messaging engine without a concrete bug to fix.

## 5. Functional Requirements

### 5.1 Default SMS role and sync

- Request the default SMS role on first launch and explain why if refused.
- Full sync imports conversations, messages, MMS parts and recipients from the
  Telephony provider into Realm; partial syncs keep it current.
- Contacts are cached from Android Contacts and refreshed when the recipient
  picker opens.

### 5.2 Conversations

- Inbox and archive lists with pinned threads first; unread state, drafts and
  snippets.
- Swipe actions are configurable; destructive swipes use the danger colour.
- Search across conversation titles and message bodies.
- Custom conversation titles and per-conversation theme colour.

### 5.3 Composing and actions

- SMS, MMS, group messages ("send as group" toggle), attachments and voice notes.
- Dual-SIM: choose the subscription per message.
- Telegram-style selection: tap to select messages, then reply, copy, forward,
  delete, react or schedule from one action bar.
- Inline scheduling: pick a time from the composer; the scheduled message shows in
  the thread until it sends. Scheduling uses exact alarms.

### 5.4 Blocking and filters

- Block numbers and conversations; optional external blocking clients.
- Message content filters: plain text or regex, case sensitivity, optionally
  applied to contacts.

### 5.5 Backup

- Back up messages to a user-chosen file and restore from it. Backups contain
  message content and must never be uploaded by Gjall.

### 5.6 Appearance

- Expressive theme by default; classic QKSMS themes remain available.
- Light, dark, black and automatic night mode; font size and family.
- Launcher label **Messages**, optional **Gjall**.

## 6. Technical Requirements

- Kotlin, Android View system, RxJava, Dagger, Conductor, Realm.
- Modules: `presentation` (UI), `domain` (models, interactors, preferences),
  `data` (repositories, Realm migrations, receivers), `common`, `android-smsmms`.
- JDK 17, Android SDK 34 (see README).
- Realm schema changes go through `QkRealmMigration` with a bumped
  `SCHEMA_VERSION`; see [SCHEMA.md](SCHEMA.md).

## 7. Success Metrics

Personal-scale targets; no telemetry exists, so evidence is manual.

| Area | Target | Evidence |
|---|---|---|
| Delivery | No lost or duplicated messages in daily use | Owner use; compare with Telephony provider |
| Sync integrity | Full sync rebuilds the same conversation list | Manual check after reinstall |
| Privacy | No numbers or bodies in logcat | `adb logcat` review per release |
| Licence | Every release has source and notices on GitHub | Release checklist |

## 8. Reliability and Privacy Invariants

- Never log message bodies or phone numbers (upstream fix "Don't leak personal
  data into logs" must stay).
- Never send message data off the device except as SMS/MMS the user asked to send.
- A Realm migration must not lose Gjall-only data (scheduled messages, filters,
  reactions).
- Sign releases with the same key so updates install over existing copies.

## 9. Risks and Dependencies

| Risk | Mitigation |
|---|---|
| Carrier MMS quirks | Keep QUIK's MMS config updates; merge upstream fixes |
| Upstream drift | Fetch `upstream` regularly; keep fork changes small |
| Realm deprecation | Track QUIK's plans; Telephony provider remains the source of truth |
| Trademark confusion | Distinct name, icon and app ID; "not affiliated" note in README |
| Exact-alarm policy | Scheduling needs `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`; justify before any store listing |

## 10. Release Acceptance

A release is ready when it builds signed, installs over the previous version,
sends and receives SMS and MMS on the owner's carrier, migrates Realm cleanly,
logs no personal data, and its tag on GitHub has the matching source, LICENSE and
THIRD_PARTY notices.
