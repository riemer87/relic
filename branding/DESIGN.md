# Relic – Design

**Tinte auf Papier.** Ruhig, warm, schlicht. Flach wie ein Kalenderblatt, eine Linie mit Punkten
wie im Schulheft, Übersichten als lose Blätter.

Diese Datei ist die **einzige verbindliche Quelle** für das Aussehen von Relic – in der App und auf
der Website (https://relicjournalapp.com). Sie wird bei jeder Design-Änderung mit angepasst.
In der App stehen die Werte in `app/src/main/java/de/riemer87/relic/ui/theme/`
(`Color.kt`, `Type.kt`, `Theme.kt`, `Appearance.kt`); beide müssen immer übereinstimmen.

Maße in der App sind in `dp` (Abstände) und `sp` (Schrift). Auf der Website entspricht
1 dp/sp ungefähr 1 CSS-Pixel.

---

## 1. Farben

Farben werden **nur über ihre Namen** verwendet, nie als fester Wert im Code. Akzent und
Dunkelmodus sind in der App wählbar (Einstellungen › Darstellung).

### Grundfarben

| Name | Wofür | Hell | Dunkel | CSS-Variable |
|---|---|---|---|---|
| Papier | Hintergrund der Seiten | `#EFE9DF` | `#1D1C1A` | `--paper` |
| Blatt | Karten/Blätter, etwas heller als Papier | `#F8F4EC` | `#272522` | `--sheet` |
| Tinte | Haupttext, Symbole | `#24221E` | `#ECE6DB` | `--ink` |
| Tinte weich | Nebentext, Erklärungen | `#5F5A51` | `#B5AEA2` | `--ink-soft` |
| Tinte blass | Platzhalter, Kleingedrucktes, Wochentag-Kürzel | `#9B9488` | `#7F796F` | `--ink-faint` |
| Linie | Zeitstrahl-Linie, Ränder, gestrichelte Blätter | `#C9C0B2` | `#47433D` | `--rule` |
| Akzent | Punkt für heute, Zeile „Heute“, Speichern-Knopf, Schalter | = Tinte | = Tinte | `--accent` |
| Schrift auf Akzent | Text auf dem Speichern-Knopf | `#F8F4EC` | `#1D1C1A` | `--on-accent` |
| Erfolg | nur Erfolgsmeldungen („Export gespeichert“, Haken) | `#3A6B45` | `#8FC29A` | `--success` |

### Akzentfarben

Standard ist **Tinte** (kein Farbakzent). Der Akzent färbt **nur** den Punkt für heute, die
Zeile „Heute“ (und die Tageszahl von heute), den Speichern-Knopf und eingeschaltete Schalter.

| Name | Hell | Dunkel |
|---|---|---|
| Tinte (Standard) | `#24221E` | `#ECE6DB` |
| Salbei | `#4E6A4B` | `#9FBB98` |
| Pflaume | `#7D4F6D` | `#CF9DBD` |
| Ocker | `#8A6118` | `#DCAE5A` |

Salbei und Ocker sind in Hell etwas dunkler als in den ersten Entwürfen, damit kleine Schrift
(„HEUTE“) und helle Schrift auf dem Knopf gut lesbar bleiben.

### Lesbarkeit

Ein automatischer Test (`ThemeColorsTest`) prüft für alle 8 Kombinationen (Hell/Dunkel × 4 Akzente)
einen Kontrast von **mindestens 4,5 : 1** für Tinte, Tinte weich, Akzent und Erfolg auf Papier und
Blatt sowie für die Schrift auf dem Akzent. „Tinte blass“ ist absichtlich zart (mindestens 2,3 : 1)
und nur für Nebensächliches gedacht.

### Ausnahme

Die Vollbild-Fotoansicht ist immer schwarz mit weißen Bedienelementen (Fotos wirken auf Schwarz
am besten), unabhängig von Hell/Dunkel.

---

## 2. Schriften

Beide Schriften kommen von **Google Fonts** und stehen unter der **SIL Open Font License 1.1**.
In der App liegen sie als Dateien bei (`app/src/main/res/font/`, kein Download zur Laufzeit),
die Lizenztexte in `app/src/main/assets/licenses/`. Die Website nutzt dieselben Schriften.

| Schrift | Quelle | Wofür |
|---|---|---|
| **Fraunces** (variabel) | https://fonts.google.com/specimen/Fraunces | Schriftzug „Relic“, Überschriften, Tageszahlen, Uhrzeit |
| **DM Sans** (variabel) | https://fonts.google.com/specimen/DM+Sans | Text, Bedienelemente, kleine Zeilen |

Fraunces wird in zwei Einstellungen benutzt:
- **Fraunces Display** – sehr groß (Tageszahlen, Seitentitel): optische Größe `opsz 144`,
  Weichheit `SOFT 100`, Stärken 400 und 600.
- **Fraunces Text** – Überschriften normaler Größe: `opsz 72`, `SOFT 50`, Stärken 400 und 600.

DM Sans: Stärken 400 und 500, kursiv 400.

### Schriftgrößen

| Stil | Schrift | Größe / Zeilenhöhe | Stärke | Verwendung |
|---|---|---|---|---|
| Titel groß | Fraunces Display | 31 / 34 | 400 | Überschrift der Eintragsseite, Einführung |
| Schriftzug/Seitentitel | Fraunces Text | 29 | 600 | „Relic“, „Archiv“, „Einstellungen“ |
| Tageszahl | Fraunces Display | 34 / 36 | 400 | große Zahl auf Tagesblättern |
| Überschrift klein | Fraunces Display | 26 / 32 | 400 | Monatskopf im Archiv |
| Titel mittel | Fraunces Text | 19 / 24 | 400 | Archiv-Blatt im Zeitstrahl, Monatsauswahl |
| Text groß | DM Sans | 16 / 25 | 400 | Eintragstext, Haupttexte |
| Text | DM Sans | 14,5 / 22 | 400 | Erklärungen, Vorschauen |
| Text klein | DM Sans | 12,5 / 18 | 400 | Fußnoten, Kleingedrucktes |
| Knopf | DM Sans | 14 (Einführung 15) | 500 | Knöpfe |
| Beschriftung | DM Sans | 12 | 400 | Namen unter Farbtupfern |
| Kleine Zeile („Kicker“) | DM Sans, GROSSBUCHSTABEN | 11 (10–10,5 unter Tageszahlen) | 500 | „HEUTE“, „SPEICHERORT“, Datumszeile; Zeichenabstand 0,12 em |

---

## 3. Abstände, Ecken, Linien, Schatten

**Schatten: keine.** Alles ist flach. Keine waagerechten Trennstriche zwischen Tagen; Abstand
und Blätter gliedern die Seite.

### Abstände

| Wo | Wert |
|---|---|
| Seitenrand links/rechts | 16 (Zeitstrahl-Kopf 20 links; Einführung und Ordnerwahl 24) |
| Innenabstand Tagesblatt | 14 seitlich, 12 oben, 14 unten |
| Innenabstand Eintragsseite (Blatt) | 18 |
| Innenabstand Einstellungs-Abschnitt | 16 |
| Abstand zwischen Blättern/Abschnitten | 10–14 |
| Abstand Tageszahl ↔ Inhalt | 12 |
| Abstand zwischen Fotos | 6–8 |

### Eckenradien

| Element | Radius |
|---|---|
| Blätter, Karten, Einstellungs-Abschnitte, Hinweisflächen | **10** |
| Knöpfe (Speichern, Umrandet), Auswahlfelder, Fotos | **8** (kleine Vorschaufotos 6) |
| Große Knöpfe der Einführung | 10 |
| Tagesauswahl-Dialog | 20 |
| Punkte, Farbtupfer, Plus-Kreis | Kreis |

### Linien

- Strichstärke **1,5** für Ränder, Zeitstrahl-Linie und Punkte (Auswahlfelder 1).
- **Gestrichelt** (6 Strich / 4 Lücke) für leere Tage und „nachtragen“-Blätter;
  die Zeitstrahl-Linie endet fein gestrichelt (3 / 3) zum Archiv-Blatt.
- Textfeld: nur eine feine Linie darunter (Linie-Farbe), kein Kasten.

---

## 4. Bausteine

### Kopfzeile
Kein farbiger Balken. Links das **runde Logo** (34 groß, feiner Rand 1 in Linie-Farbe, damit der
Kreis auch auf hellem Papier sichtbar ist; bleibt immer Tinte auf Papier), mit Abstand 10 daneben
der Schriftzug „Relic“ (Fraunces), rechts das Regler-Symbol für die
Einstellungen, davor die Lupe für die Suche (in beiden Reitern).
Darunter zwei **Reiter** „Diese Woche“ · „Archiv“, je über die halbe Breite (Seitenrand 16, mind.
48 hoch), **ohne Trennstrich**: DM Sans 15, Stärke 500; der aktive in Tinte mit einem **kurzen
Strich** darunter (28 × 2, Tinte, mittig, 4 über der Unterkante), der andere in Tinte blass.
Beim Wechsel bleiben Kopfzeile und Reiter stehen; nur der Inhalt blendet in **150 ms** über, der
Strich gleitet in derselben Zeit zum anderen Reiter, die Schriftfarben wechseln mit. Keine graue
Drück-Fläche auf den Reitern. Bewegt wird nur Durchsichtigkeit und Position (schont ältere Handys).
Unterseiten (Eintrag, Einstellungen, Suche): Pfeil zurück oben links, darunter der Seitentitel.
**Keine untere Navigationsleiste.**

### Symbole
Feine Linien in **1,5er-Stärke** mit runden Enden (wie die Zeitstrahl-Linie), 24er Raster, in Tinte.
Einstellungen = **Regler** (drei Linien mit Kreisen), Suche = Lupe. Keine gefüllten, schweren Symbole.

### Zeitstrahl
Links eine senkrechte Linie (Linie-Farbe) mit einem **Punkt pro Tag** (12 groß):
heute gefüllt im Akzent, Tage mit Eintrag als Ring in Tinte weich, leere Tage als Ring in
Linie-Farbe. Rechts daneben pro Tag ein **Tagesblatt**. Unten endet die Linie gestrichelt in
einem Blatt „Archiv · Alle älteren Einträge →“.

### Tagesblatt (Karte)
Fläche in Blatt-Farbe, Radius 10, kein Rand, kein Schatten. Links die große **Tageszahl**
(Fraunces Display 34, heute im Akzent) mit Wochentag-Kürzel darunter (Kicker, Tinte blass),
rechts eine Kicker-Zeile („HEUTE“, „GESTERN“ oder Wochentag) und die Textvorschau
(Zeitstrahl 2 Zeilen, Archiv 4 Zeilen), darunter kleine Fotos.
**Leerer Tag:** keine Fläche, gestrichelter Rand, Text kursiv „Nichts aufgeschrieben – nachtragen →“.

### Eintragsseite
Ein großes Blatt auf Papier: kleine Datumszeile (Kicker), große Fraunces-Überschrift
(„Etwas zu heute“ / „Dieser Tag“), Textfeld mit feiner Linie darunter, Zeichenzähler, Fotoreihe
(64er Kacheln, Radius 8, „+“-Feld gestrichelt) und rechts unten der Speichern-Knopf.

### Knöpfe
- **Hauptknopf** (Speichern, „Los geht’s“): Fläche Akzent, Schrift „Schrift auf Akzent“, Radius 8
  (Einführung: volle Breite, Radius 10, mind. 50 hoch).
- **Umrandeter Knopf** („Ordner ändern“, „Alles exportieren“): Rand Linie, Schrift Tinte, Radius 8.
- **Textknopf** („Vielleicht später“, „Abbrechen“): nur Schrift in Tinte.
- **Schalter**: an = Akzent, aus = Papier mit Rand in Linie-Farbe.
- **Auswahlfelder** (Hell · Dunkel · Wie das Handy): gewählt = Fläche Tinte mit Papier-Schrift,
  sonst Rand in Linie-Farbe; Radius 8.
- **Farbtupfer**: Kreise 40, gewählter mit Ring in Tinte.

### Auswahl-Blätter (Ordnerwahl in der Einführung)
Hauptwahl: Blatt mit Rand in Tinte. Zweite Wahl: Blatt ohne Rand. Dritte Wahl: gestrichelt.

### Archiv
Kopfzeile „September 2026 ▾“ mit Pfeilen ‹ › auf einem Blatt; aufgeklappt Jahre und 12 Monate als
Raster (Monate mit Einträgen hervorgehoben, Zukunft ausgegraut). Darunter „x Einträge“ und die
Tagesblätter. Am Ende ein gestricheltes Blatt „+ Einen Tag nachtragen“.

### Suche
Lupe oben rechts in der Kopfzeile (beide Reiter). Eigene Seite: Pfeil zurück, Seitentitel „Suche“,
darunter das Suchfeld als Blatt (Radius 10): Lupe links (Tinte weich), Platzhalter „Einträge durchsuchen“
in Tinte blass, ✕ rechts (Tinte weich, nur sichtbar, wenn etwas im Feld steht; leert das Feld).
Treffer als Archiv-Blätter mit Kicker „MONAT JAHR“; die Fundstellen im Vorschautext
**halbfett (600)**, sonst gleiche Farbe wie der Text. Kürzt die Vorschau vorn, beginnt sie mit „…“.

### Tagesauswahl (Nachtragen)
Eigener Dialog im Relic-Stil, Radius 20: Monatsraster ab Montag, **Punkt unter jedem Tag mit
Eintrag**, heute umrandet, Zukunft blass und nicht wählbar.

### Einführung
Fortschrittspunkte oben (aktueller Schritt als kurzer Strich in Tinte, übrige als Punkte in
Linie-Farbe), große Fraunces-Überschrift, Text in Tinte weich, Knöpfe unten.

### Meldungen
Kurze Meldungen („Eintrag gespeichert“) unten als dunkle Leiste (Fläche Tinte, Schrift Blatt).
Erfolg in den Einstellungen: grüner Haken + Text in Erfolg-Farbe.

---

## 5. Logo und App-Symbol

Das Symbol: ein großes, angeschnittenes **„R“ aus Fraunces** (144pt SemiBold) mit dem gefüllten
**Punkt aus dem Zeitstrahl** links oben – Tinte `#24221E` auf Papier `#EFE9DF`.
Das Logo bleibt immer hell (Tinte auf Papier), auch im Dunkelmodus.

| Datei (in `branding/`) | Wann verwenden |
|---|---|
| `relic-icon.svg` | Standard für Website und Druck: quadratisch, Vektor, beliebig skalierbar |
| `relic-icon-round.svg` | Wo eine runde Form gebraucht wird (Favicon-Varianten, Profilbilder), als Vektor |
| `relic-icon-512.png` | **Play Store** (verlangt genau 512 × 512) |
| `relic-icon-1024.png` | Presse, Präsentationen, soziale Medien (quadratisch) |
| `relic-icon-round-1024.png` | Profilbilder (z. B. Social Media) als Bilddatei |

Hinweise:
- Das „R“ ist absichtlich angeschnitten; nicht neu zentrieren oder verkleinern.
- Nicht umfärben, keine Schatten, keine Effekte.
- Für Text neben dem Logo den Schriftzug „Relic“ in Fraunces setzen.
- In der App ist das Symbol ein adaptives Vektor-Symbol
  (`app/src/main/res/drawable/ic_launcher_foreground.xml` und `ic_launcher_background.xml`,
  gleicher Pfad wie die SVGs) mit einfarbiger Variante für Android-„Designsymbole“.
  Der Startbildschirm nutzt die Papierfarbe (im Dunkelmodus des Handys: dunkles Papier).
- Die Buchstabenform stammt aus Fraunces (SIL Open Font License); die Lizenz erlaubt die
  Verwendung in einem Logo.

---

## 6. Für die Website

Die Website übernimmt Farben (als CSS-Variablen wie in Abschnitt 1), Schriften, Radien und den
flachen Papier-Stil. Vorschlag für die Grundlage:

```css
:root {
  --paper: #EFE9DF; --sheet: #F8F4EC; --ink: #24221E; --ink-soft: #5F5A51;
  --ink-faint: #9B9488; --rule: #C9C0B2; --accent: var(--ink); --on-accent: #F8F4EC;
  --success: #3A6B45;
  --serif: "Fraunces", Georgia, serif;   /* opsz 144, SOFT 100 für Große Titel */
  --sans: "DM Sans", system-ui, sans-serif;
  --radius-sheet: 10px; --radius-button: 8px;
}
@media (prefers-color-scheme: dark) {
  :root {
    --paper: #1D1C1A; --sheet: #272522; --ink: #ECE6DB; --ink-soft: #B5AEA2;
    --ink-faint: #7F796F; --rule: #47433D; --on-accent: #1D1C1A; --success: #8FC29A;
  }
}
```

---

## Ordner

- `branding/` – **verbindlich**: diese Datei, Logo und App-Symbol (für App und Website).
- Frühere Ideen und Entwürfe (Ordner `design/`) wurden entfernt und sind nur noch in der
  Git-Geschichte zu finden. Es gibt keine anderen Design-Vorlagen.
