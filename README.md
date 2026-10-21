<p align="center">
  <img src="branding/relic-icon-round-1024.png" alt="Relic" width="128" height="128">
</p>

<h1 align="center">Relic</h1>

<p align="center">
  <strong>Deine Einträge gehören dir.</strong> · <em>Your entries belong to you.</em><br>
  <a href="https://relicjournalapp.com">relicjournalapp.com</a>
</p>

---

## Deutsch

Relic ist ein privates, tägliches Tagebuch für Android. Jeder Tag wird als ganz normale
Datei gespeichert, die du auch in 30 Jahren noch lesen kannst – selbst wenn es diese App
nicht mehr gibt.

- **Deine Dateien, dein Ordner:** Einträge liegen als Markdown-Dateien (`2026/10/2026-10-09.md`)
  mit Fotos daneben in einem Ordner deiner Wahl. Keine versteckte Datenbank.
- **Diese Woche und Archiv:** Auf einen Blick sehen, ob gestern ein Eintrag fehlt; ruhig
  durch Monate und Jahre zurückblättern und alles durchsuchen.
- **Bis zu vier Fotos pro Tag**, verkleinert und ohne Standortdaten.
- **Abendliche Erinnerung** – nur an Tagen, an denen du noch nichts geschrieben hast.
- **App-Sperre** mit Fingerabdruck oder Displaysperre.
- **Automatische Sicherung** jede Woche in einen Ordner deiner Wahl (auch einen Cloud-Ordner), mit
  mehreren Versionen; dazu „Alles exportieren“ als ZIP-Datei.
- **Hell, dunkel oder wie das Handy**, mit wählbarer Akzentfarbe.
- **Kein Internetzugriff, keine Android-Gerätesicherung.** Relic *kann* nichts verschicken – nicht einmal
  die Einstellungen gehen über Android an Google.
- **Kein Konto, kein Server, kein Tracking, keine Werbung.** Dauerhaft kostenlos.

## English

Relic is a private daily journal for Android. Every day is saved as a plain file that you
can still read in 30 years – even if this app no longer exists.

- **Your files, your folder:** entries are Markdown files (`2026/10/2026-10-09.md`) with
  photos next to them, in a folder you choose. No hidden database.
- **This week and archive:** see at a glance whether yesterday is missing; leaf back calmly
  through months and years and search everything.
- **Up to four photos per day**, resized and without location data.
- **Evening reminder** – only on days when you haven't written anything yet.
- **App lock** with fingerprint or screen lock.
- **Automatic backup** every week into a folder of your choice (a cloud folder works too), keeping
  several versions; plus “Export everything” as a ZIP file.
- **Light, dark or follow the phone**, with a choice of accent colour.
- **No internet access, no Android device backup.** Relic *cannot* send anything – not even its
  settings go to Google through Android.
- **No account, no server, no tracking, no ads.** Free, for good.

## Das Dateiformat · File format

```markdown
---
date: 2026-10-09
created: 2026-10-09T21:14:03+02:00
updated: 2026-10-09T21:20:47+02:00
---

Heute war ein **guter** Tag.

<!-- relic:photos -->
![](2026-10-09_1.jpg)
```

## Technik · Tech

Kotlin, Jetpack Compose, Material 3, Storage Access Framework, WorkManager. minSdk 26.
Keine Internet-Berechtigung · No internet permission.
Schriften / Fonts: [Fraunces](https://fonts.google.com/specimen/Fraunces) and
[DM Sans](https://fonts.google.com/specimen/DM+Sans) (SIL Open Font License).

## Lizenz · License

Relic ist freie Software unter der [GNU General Public License v3.0](LICENSE): Du darfst den Code
nutzen, verändern und weitergeben; veränderte Versionen müssen ebenfalls unter der GPL-3.0 stehen.
Die Schriften stehen unter der SIL Open Font License (`app/src/main/assets/licenses/`).

Relic is free software under the [GNU General Public License v3.0](LICENSE): you may use, change
and share the code; modified versions must also be released under the GPL-3.0.
The fonts are under the SIL Open Font License (`app/src/main/assets/licenses/`).
