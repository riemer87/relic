# Backup-Ratgeber (Entwurf für die Website)

Rohfassung für https://relicjournalapp.com. Inhaltlich abgestimmt und auf dem Stand der App
(Meilenstein 11), sprachlich noch zu glätten.

---

## Kurz gesagt

Relic hat **keinen Internetzugriff**. Deine Einträge verlassen dein Handy nur, wenn du sie selbst
irgendwohin sicherst. Das ist Absicht – und heißt auch: **Wohin die Sicherung geht, entscheidest du.**
Relic macht es dir dabei so einfach wie möglich: Einmal einrichten, dann sichert Relic jede Woche von selbst.

Ein Tagebuch wird mit jedem Jahr wertvoller. Ein verlorenes, gestohlenes oder kaputtes Handy sollte
nicht bedeuten, dass Jahre an Erinnerungen weg sind.

## Warum Relic kein eingebautes Cloud-Backup hat

- **Nachprüfbar privat:** Relic fragt keine Internet-Berechtigung an. Das kannst du in den
  Android-Einstellungen und im Play Store selbst sehen. Die App *kann* deine Einträge gar nicht
  verschicken – nicht nur „verspricht, es nicht zu tun“.
- **Nicht einmal die Android-Gerätesicherung:** Viele Apps lassen Android ihre Daten still zu Google
  sichern oder beim Handywechsel mitnehmen. Relic hat das ausdrücklich abgeschaltet – auch Relics
  Einstellungen verlassen dein Handy nie ohne dich. Völlige Unabhängigkeit.
- **Unabhängig:** Deine Einträge sind ganz normale Dateien. Du entscheidest, wo die Sicherung liegt:
  Google Drive, Nextcloud, Dropbox, OneDrive, ein USB-Stick, dein PC. Relic bindet dich an keinen Anbieter.
- **Weniger, das schiefgehen kann:** Keine Anmeldung, keine abgelaufenen Zugänge, keine halb
  hochgeladenen Dateien.

## Datenschutz auf einen Blick (für die Website)

- Kein Internetzugriff – Relic *kann* nichts verschicken.
- Keine Android-Gerätesicherung – nicht einmal Einstellungen gehen an Google oder auf ein anderes Handy.
- Kein Konto, kein Server, kein Tracking, keine Werbung, keine Bezahl-Bibliotheken.
- Deine Einträge sind normale Dateien in einem Ordner, den du wählst – lesbar auch ohne Relic.
- Sicherungen gehen nur dorthin, wo du sie hinlegst.

## So liegt dein Tagebuch auf dem Handy

Alles steht in dem Ordner, den du beim ersten Start gewählt hast (meist *Dokumente › Relic*):
pro Tag eine Textdatei (`2026-10-16.md`) und die Fotos daneben (`2026-10-16_1.jpg`), sortiert nach
Jahr und Monat. Eine Sicherung ist nichts anderes als eine Kopie dieses Ordners, verpackt als ZIP-Datei.

## Weg 1: Automatische Sicherung – die Empfehlung

*Einstellungen › Automatische Sicherung* einschalten und einmal einen **Sicherungsordner** wählen.
Relic bietet das auch gleich beim ersten Start an.
Ab dann legt Relic **jede Woche** eine Sicherung als ZIP-Datei dort ab – aber nur, wenn sich seit der
letzten etwas geändert hat. Mit „Jetzt sichern“ geht es auch sofort.

**Welcher Ordner?** Am besten einer, der nicht nur auf dem Handy liegt:

- **Ein Cloud-Ordner:** In der Ordnerauswahl von Android erscheinen auch Cloud-Apps wie Google Drive
  (unter „Öffnen aus“). Wählst du dort einen Ordner, landen die Sicherungen direkt in deiner Cloud –
  hochgeladen von der Cloud-App, nicht von Relic. Mit Google Drive ausprobiert und bewährt.
- **Ein Ordner, den eine Sync-App spiegelt** (z. B. Syncthing oder FolderSync, siehe Weg 3).
- Ein Ordner nur auf dem Handy (z. B. *Dokumente › Relic-Backups*) schützt vor versehentlichem Löschen,
  aber nicht vor einem verlorenen Handy. Dann bitte zusätzlich ab und zu Weg 2.

Nicht erlaubt ist nur dein Tagebuch-Ordner selbst – dort wäre die Sicherung ja mit verloren.

**Mehrere Versionen, ohne dass der Speicher vollläuft.** Relic hebt höchstens fünf Sicherungen auf:

| Aufgehoben wird | Beispiel am 21. Oktober 2026 |
|---|---|
| die zwei neuesten | 21.10. und 14.10. |
| die letzte aus dem Vormonat | 30.09. |
| die letzte aus dem Vorquartal | 30.09. (Juli–September) |
| die letzte aus dem Vorjahr | 31.12.2025 |

So kannst du auch etwas zurückholen, das du vor Wochen oder Monaten versehentlich gelöscht hast.
Ältere Sicherungen räumt Relic selbst weg. **Andere Dateien in diesem Ordner fasst Relic nie an** –
nur Dateien, die genau so heißen wie seine Sicherungen: `Relic-Backup-2026-10-21_1430.zip`.

**Sicher gespeichert:** Jede Sicherung wird nach dem Schreiben vollständig wieder eingelesen und geprüft.
Erst dann bekommt sie ihren richtigen Namen. Eine abgebrochene Sicherung (z. B. Akku leer) wird nie für
eine gute gehalten.

In den Einstellungen siehst du jederzeit, wie es steht: gelb, während gesichert wird, danach grün mit
„Letzte Sicherung: …“, Anzahl und Größe. Klappt etwas nicht (Ordner weg, Speicher voll), steht dort,
was zu tun ist.

## Weg 2: „Alles exportieren“ – eine Kopie, wohin du willst

*Einstellungen › Export › Alles exportieren* packt dein ganzes Tagebuch in **eine ZIP-Datei**.
Im „Speichern unter“-Fenster von Android wählst du, wohin – in die Cloud, nach *Downloads* und von dort
per Kabel auf deinen PC, auf einen USB-Stick. Gut als zusätzliche Kopie an einem zweiten Ort.

## Weg 3: Den Tagebuch-Ordner laufend spiegeln – für Fortgeschrittene

Weil dein Tagebuch ein ganz normaler Ordner ist, kann jede Sync-App ihn laufend sichern, zum Beispiel:

- **Syncthing** – spiegelt den Ordner direkt auf deinen PC, ohne Cloud dazwischen.
- **FolderSync** oder ähnliche Apps – spiegeln den Ordner in Google Drive, Nextcloud, Dropbox und Co.

Achtung: Eine reine Spiegelung übernimmt auch versehentliche Löschungen. Am besten mit Weg 1 kombinieren.

Oder ganz ohne App: Handy per USB anschließen, *Dateiübertragung* wählen und den Ordner *Dokumente/Relic*
auf den PC kopieren.

## Relic erinnert dich

Ist die automatische Sicherung aus und deine letzte Sicherung mindestens 30 Tage her (oder gab es noch
keine und du schreibst schon seit mindestens einer Woche), zeigt Relic auf „Diese Woche“ eine ruhige
Zeile: *„Seit 34 Tagen keine Sicherung · Sichern →“*.
Ist die automatische Sicherung an, aber klappt nicht, steht dort *„Die automatische Sicherung klappt
gerade nicht · Ansehen →“*. Ist alles in Ordnung, siehst du nichts. Keine Benachrichtigungen, kein Drängeln.

## Faustregel

**Mindestens eine Kopie, die nicht auf dem Handy liegt.** Noch besser zwei an verschiedenen Orten –
zum Beispiel die automatische Sicherung in der Cloud und ab und zu ein Export auf den PC.

## Wiederherstellen auf einem neuen Handy

1. Die Sicherung aufs neue Handy holen: die neueste `Relic-Backup-….zip` (oder einen Export) aus der
   Cloud laden oder per Kabel kopieren.
2. Die ZIP-Datei mit einer Dateien-App (z. B. *Dateien von Google*) entpacken, am besten nach
   *Dokumente*, sodass dort wieder ein Ordner mit den Jahresordnern liegt.
3. Relic installieren und beim Einrichten **„Ich habe schon ein Relic-Tagebuch“** wählen, dann diesen
   Ordner auswählen.

Fertig – alle Einträge und Fotos sind wieder da. Und selbst ganz ohne Relic kannst du deine Einträge
mit jedem Texteditor oder z. B. Obsidian öffnen.

## Was die App-Sperre nicht schützt

Die App-Sperre schützt Relic selbst. Die Dateien im Ordner und in deinen Sicherungen sind normale
Dateien. Wer Zugriff auf deine Cloud oder deinen PC hat, kann sie lesen – schütze diese Orte also
gut (sicheres Passwort, Zwei-Faktor-Anmeldung).
