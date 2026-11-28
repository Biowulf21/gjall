# Product

## Platform

Android phones, Android 6.0 (API 23) or later. Distributed as signed APKs from
this repository's GitHub releases. No store listing yet.

## Users

- **The owner (Biowulf21)**, as the primary daily user. Gjall is built to replace
  the stock Messages app on his own phone first.
- **Android users who sideload open-source apps** and want a private, customisable
  SMS/MMS client that matches the rest of the collection.

## Product Purpose

Gjall is the default SMS and MMS app for the device. It reads and writes the
system Telephony provider, so messages stay in Android's own store and keep
working if the user switches apps. Success means sending and receiving reliably
on the user's carrier, finding any conversation quickly, and never leaking
message content or phone numbers.

## Positioning

Gjall is a personal fork of [QUIK](https://github.com/quik-sms/quik), which
continues Moez Bhatti's [QKSMS](https://github.com/moezbhatti/qksms). It keeps
QUIK's messaging engine and adds the shared Expressive design, Telegram-style
message actions, inline scheduling and a launcher label of **Messages**. It is
not affiliated with or endorsed by the QUIK or QKSMS projects.

## Operating Context

- Gjall must be the default SMS app to send, receive, delete or mark messages.
- Carrier MMS configuration comes from the bundled `android-smsmms` module and
  QUIK's MMS config updates. Delivery depends on the carrier and device.
- Gjall's own Realm database is a cache and index of the Telephony provider, plus
  Gjall-only data (scheduled messages, filters, reactions, preferences).
- The app has internet permission for MMS transport and opening links; it has no
  account, analytics or ads.

## Capabilities and Constraints

- Conversations: inbox, archive, pin, block, swipe actions, search, custom titles.
- Composing: SMS, group MMS, attachments, voice messages, scheduled sends,
  dual-SIM selection, drafts.
- Message actions: reply, copy, forward, delete, react, schedule (Telegram-style
  selection bar).
- Blocking: built-in block list, message content filters (plain or regex), and
  external blocking clients that QUIK supports.
- Backup and restore of messages to a user-chosen file.
- Themes: Expressive (default) or the classic QKSMS look, per-conversation colour,
  light/dark/black, font size and font family.

## Brand Commitments

- Name **Gjall** (the horn Gjallarhorn); launcher label **Messages** by default,
  with a setting to show "Gjall".
- App ID `io.github.biowulf21.gjall`. Never reuse QUIK's or QKSMS's app ID, name
  or icon.
- Always credit QUIK, QKSMS and android-smsmms in the README and About screen.

## Monetisation

None. Gjall is free and GPL-3.0-or-later. The only money flow is an optional
donation link (Buy Me a Coffee). Donations unlock nothing and buy no support,
warranty or priority. Never add ads, paid features, subscriptions or in-app
purchases.

## Evidence on Hand

- Owner-tested on his own device. No beta group, usage data or store reviews.
- Upstream history: QKSMS (2014–2021) and QUIK (2023–present).

## Product Principles

- Messages belong to Android's store, not to Gjall.
- Never write phone numbers or message bodies to logs, crash reports or issues.
- Match the shared design language in [DESIGN.md](DESIGN.md).
- Keep upstream-mergeable where practical; fork-specific code stays small.

## Accessibility & Inclusion

Ships QUIK's translations (Weblate), supports RTL layouts and system font scaling.
No formal accessibility conformance target.
