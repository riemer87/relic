# Backup-Ratgeber (Entwurf für die Website)

Rohfassung für https://relicjournalapp.com. Inhaltlich abgestimmt, sprachlich noch zu glätten.
Punkte mit *(geplant)* gibt es in der App noch nicht (Meilenstein 11).

---

## Kurz gesagt

Relic hat **keinen Internetzugriff**. Deine Einträge verlassen dein Handy nur, wenn du sie selbst
irgendwohin kopierst. Das ist Absicht – und heißt auch: **Um eine Sicherung kümmerst du dich selbst.**
Relic macht es dir dabei so einfach wie möglich.

Ein Tagebuch wird mit jedem Jahr wertvoller. Ein verlorenes, gestohlenes oder kaputtes Handy sollte
nicht bedeuten, dass Jahre an Erinnerungen weg sind. Ein paar Minuten im Monat genügen.

## Warum Relic kein eingebautes Cloud-Backup hat

- **Nachprüfbar privat:** Relic fragt keine Internet-Berechtigung an. Das kannst du in den
  Android-Einstellungen und im Play Store selbst sehen. Die App *kann* deine Einträge gar nicht
  verschicken – nicht nur „verspricht, es nicht zu tun“.
- **Unabhängig:** Deine Einträge sind ganz normale Dateien. Du entscheidest, wo die Sicherung liegt:
  Google Drive, Nextcloud, Dropbox, OneDrive, ein USB-Stick, dein PC. Relic bindet dich an keinen Anbieter.
- **Weniger, das schiefgehen kann:** Keine Anmeldung, keine abgelaufenen Zugänge, keine halb
  hochgeladenen Dateien.

## So liegt dein Tagebuch auf dem Handy

Alles steht in dem Ordner, den du beim ersten Start gewählt hast (meist *Dokumente › Relic*):
pro Tag eine Textdatei (`2026-10-16.md`) und die Fotos daneben (`2026-10-16_1.jpg`), sortiert nach
Jahr und Monat. Eine Sicherung ist nichts anderes als eine Kopie dieses Ordners.

## Drei Wege zur Sicherung

### 1. „Alles exportieren“ – der einfachste Weg

*Einstellungen › Export › Alles exportieren* packt dein ganzes Tagebuch in **eine ZIP-Datei**.
Im „Speichern unter“-Fenster von Android wählst du, wohin:

- **In die Cloud:** Ist z. B. die Google-Drive-, OneDrive- oder Dropbox-App auf deinem Handy, erscheint
  sie dort als Ziel. Die ZIP-Datei landet direkt in deiner Cloud – hochgeladen von der Cloud-App,
  nicht von Relic.
- **Auf dem Handy:** z. B. in *Downloads*, und von dort per Kabel auf deinen PC.

Relic prüft die fertige Datei, bevor „Export gespeichert“ erscheint, und merkt sich, wann du zuletzt
exportiert hast.

*(geplant)* Ist dein letzter Export länger als etwa einen Monat her, erinnert Relic dich mit einem
ruhigen Hinweis daran.

### 2. Den Ordner automatisch spiegeln – für alle, die es ganz bequem wollen

Weil dein Tagebuch ein ganz normaler Ordner ist, kann jede Sync-App ihn laufend sichern, zum Beispiel:

- **Syncthing** – spiegelt den Ordner direkt auf deinen PC, ohne Cloud dazwischen.
- **FolderSync** oder ähnliche Apps – spiegeln den Ordner in Google Drive, Nextcloud, Dropbox und Co.

Du kannst auch gleich beim Einrichten von Relic einen Ordner wählen, den eine solche App schon
synchronisiert („Eigenen Ordner wählen“).

### 3. Per Kabel auf den PC

Handy per USB anschließen, *Dateiübertragung* wählen und den Ordner *Dokumente/Relic* auf den PC
kopieren. Ganz ohne App und ohne Internet.

## Wie oft?

- Schreibst du täglich: **einmal im Monat** exportieren ist ein guter Rhythmus.
- Nach besonderen Tagen mit vielen Fotos lohnt sich ein Export zwischendurch.
- Faustregel: **Mindestens eine Kopie, die nicht auf dem Handy liegt.** Noch besser zwei an
  verschiedenen Orten (z. B. Cloud und PC).

## Wiederherstellen auf einem neuen Handy

1. Die Sicherung aufs neue Handy holen (ZIP-Datei aus der Cloud laden oder den Ordner per Kabel kopieren).
2. Eine ZIP-Datei mit einer Dateien-App (z. B. *Dateien von Google*) entpacken, am besten nach
   *Dokumente*, sodass dort wieder ein Ordner mit den Jahresordnern liegt.
3. Relic installieren und beim Einrichten **„Ich habe schon ein Relic-Tagebuch“** wählen, dann diesen
   Ordner auswählen.

Fertig – alle Einträge und Fotos sind wieder da. Und selbst ganz ohne Relic kannst du deine Einträge
mit jedem Texteditor oder z. B. Obsidian öffnen.

## Was die App-Sperre nicht schützt

Die App-Sperre schützt Relic selbst. Die Dateien im Ordner und in deinen Sicherungen sind normale
Dateien. Wer Zugriff auf deine Cloud oder deinen PC hat, kann sie lesen – schütze diese Orte also
gut (sicheres Passwort, Zwei-Faktor-Anmeldung).
