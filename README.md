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
- **Zeitstrahl und Archiv:** Auf einen Blick sehen, ob gestern ein Eintrag fehlt; ruhig
  durch Monate und Jahre zurückblättern.
- **Bis zu vier Fotos pro Tag**, verkleinert und ohne Standortdaten.
- **Abendliche Erinnerung** – nur an Tagen, an denen du noch nichts geschrieben hast.
- **App-Sperre** mit Fingerabdruck oder Displaysperre.
- **Alles exportieren** als ZIP-Datei.
- **Kein Konto, kein Server, kein Tracking.** Alles bleibt auf deinem Gerät.

## English

Relic is a private daily journal for Android. Every day is saved as a plain file that you
can still read in 30 years – even if this app no longer exists.

- **Your files, your folder:** entries are Markdown files (`2026/10/2026-10-09.md`) with
  photos next to them, in a folder you choose. No hidden database.
- **Timeline and archive:** see at a glance whether yesterday is missing; leaf back calmly
  through months and years.
- **Up to four photos per day**, resized and without location data.
- **Evening reminder** – only on days when you haven't written anything yet.
- **App lock** with fingerprint or screen lock.
- **Export everything** as a ZIP file.
- **No account, no server, no tracking.** Everything stays on your device.

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

Kotlin, Jetpack Compose, Material 3, Storage Access Framework. minSdk 26.
Schriften / Fonts: [Fraunces](https://fonts.google.com/specimen/Fraunces) and
[DM Sans](https://fonts.google.com/specimen/DM+Sans) (SIL Open Font License).
