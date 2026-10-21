# Relic – Design

**Tinte auf Papier.** Ruhig, klar, schlicht. Ein fast weißer Grund, darauf weiße, flache Blätter;
Grautöne mit einem Hauch Wärme statt Beige. Bewusst eigenständig (kein Zeitstrahl, keine cremige Fläche).

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
| Papier | Hintergrund der Seiten (fast neutrales Weiß) | `#F3F3F1` | `#1A1A19` | `--paper` |
| Blatt | Karten/Blätter, heller als Papier | `#FFFFFF` | `#252524` | `--sheet` |
| Tinte | Haupttext, Symbole | `#22211F` | `#ECEAE6` | `--ink` |
| Tinte weich | Nebentext, Erklärungen | `#5C5955` | `#B3B0AA` | `--ink-soft` |
| Tinte blass | Platzhalter, Kleingedrucktes, Wochentag-Kürzel | `#8C8882` | `#7C7975` | `--ink-faint` |
| Linie | feine Linien, Ränder, gestrichelte Blätter | `#DAD8D3` | `#42413E` | `--rule` |
| Akzent | Zeile „Heute“, Speichern-Knopf, Schalter | = Tinte | = Tinte | `--accent` |
| Schrift auf Akzent | Text auf dem Speichern-Knopf | `#FFFFFF` | `#1A1A19` | `--on-accent` |
| Erfolg | nur Erfolgsmeldungen („Export gespeichert“, „Letzte Sicherung“, Haken) | `#3A6B45` | `#8FC29A` | `--success` |
| Läuft | nur laufende Vorgänge („Sichert gerade …“, Punkt davor); gedecktes Bernstein-Gelb | `#8A5A00` | `#E2B54A` | `--progress` |

### Akzentfarben

Standard ist **Tinte** (kein Farbakzent). Der Akzent färbt **nur** die Zeile „HEUTE“ auf dem
Blatt für heute, den Speichern-Knopf und eingeschaltete Schalter.

| Name | Hell | Dunkel |
|---|---|---|
| Tinte (Standard) | `#22211F` | `#ECEAE6` |
| Salbei | `#4E6A4B` | `#9FBB98` |
| Pflaume | `#7D4F6D` | `#CF9DBD` |
| Ocker | `#8A6118` | `#DCAE5A` |

Salbei und Ocker sind in Hell etwas dunkler als in den ersten Entwürfen, damit kleine Schrift
(„HEUTE“) und helle Schrift auf dem Knopf gut lesbar bleiben.

### Lesbarkeit

Ein automatischer Test (`ThemeColorsTest`) prüft für alle 8 Kombinationen (Hell/Dunkel × 4 Akzente)
einen Kontrast von **mindestens 4,5 : 1** für Tinte, Tinte weich, Akzent, Erfolg und Läuft auf Papier und
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
| Titel groß | Fraunces Display | 31 / 34 | 400 | Überschrift der Eintragsseite, Datum auf dem Blatt für heute, Einführung |
| Schriftzug/Seitentitel | Fraunces Text | 29 | 600 | „Relic“, „Archiv“, „Einstellungen“ |
| Tageszahl | Fraunces Display | 34 / 36 | 400 | große Zahl auf Tagesblättern |
| Überschrift klein | Fraunces Display | 26 / 32 | 400 | Monatskopf im Archiv |
| Titel mittel | Fraunces Text | 19 / 24 | 400 | Archiv-Blatt auf „Diese Woche“, Monatsauswahl |
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
| Seitenrand links/rechts | 16 (Kopfzeile 18 links; Einführung und Ordnerwahl 24) |
| Innenabstand Tagesblatt | 14 seitlich, 12 oben, 14 unten |
| Innenabstand Eintragsseite (Blatt) | 18 |
| Innenabstand Einstellungs-Abschnitt | 16 |
| Abstand zwischen Blättern/Abschnitten | 10–14 |
| Abstand Tageszahl ↔ Inhalt | 12 |
| Abstand zwischen Fotos | 6–8 |

### Eckenradien

| Element | Radius |
|---|---|
| Blätter, Karten, Einstellungs-Abschnitte, Hinweisflächen | **14** (`SheetShape` in `ui/theme/Shape.kt`) |
| Knöpfe (Speichern, Umrandet), Auswahlfelder, Fotos | **8** (kleine Vorschaufotos 6) |
| Große Knöpfe der Einführung | 10 |
| Tagesauswahl-Dialog | 20 |
| Punkte, Farbtupfer, Plus-Kreis | Kreis |

### Linien

- Strichstärke **1,5** für Ränder und Symbole (Auswahlfelder und Linie unter Textfeldern 1).
- **Gestrichelt** (6 Strich / 4 Lücke) für „nachtragen“-Blätter im Archiv und die dritte Ordner-Option.
- Textfeld: nur eine feine Linie darunter (Linie-Farbe), kein Kasten.

---

## 4. Bausteine

### Kopfzeile
Kein farbiger Balken, viel Luft (22 über, 10 unter der Logo-Zeile). Links das **runde Logo**
(50 groß, weißer Kreis **ohne Rand**; bleibt immer Tinte auf Weiß, auch im Dunkelmodus), mit Abstand 12
daneben der Schriftzug „Relic“ (Fraunces), rechts das Regler-Symbol für die
Einstellungen, davor die Lupe für die Suche (in beiden Reitern).
Darunter zwei **Reiter** „Diese Woche“ · „Archiv“, je über die halbe Breite (Seitenrand 16, **56 hoch**
für große Finger), **ohne Trennstrich**: DM Sans 15, Stärke 500; der aktive in Tinte mit einem **kurzen
Strich** darunter (28 × 2, Tinte, mittig, 4 über der Unterkante), der andere in Tinte blass.
Beim Wechsel bleiben Kopfzeile und Reiter stehen; nur der Inhalt blendet in **150 ms** über, der
Strich gleitet in derselben Zeit zum anderen Reiter, die Schriftfarben wechseln mit. Keine graue
Drück-Fläche auf den Reitern. Bewegt wird nur Durchsichtigkeit und Position (schont ältere Handys).
Unterseiten (Eintrag, Einstellungen, Suche): Pfeil zurück oben links, darunter der Seitentitel.
**Keine untere Navigationsleiste.**

### Symbole
Feine Linien in **1,5er-Stärke** mit runden Enden, 24er Raster, in Tinte.
Einstellungen = **Regler** (drei Linien mit Kreisen), Suche = Lupe. Keine gefüllten, schweren Symbole.

### Diese Woche (Startseite)
Kein Zeitstrahl. Abstand 20 unter den Reitern, 10 zwischen den Einträgen.
- **Blatt für heute** (oben, Blatt-Farbe, Innenabstand 18): Kicker „HEUTE“ im Akzent, darunter das Datum
  groß (Titel groß, „Mittwoch, 21. Oktober“), darunter „Was war heute?“ (Text groß, Tinte blass) auf einer
  feinen Linie (1, Linie-Farbe). Kein Knopf: das ganze Blatt ist antippbar. Hat heute schon einen Eintrag,
  stehen dort bis zu 3 Zeilen Text und die Fotos (56).
- **Tage mit Eintrag:** Tagesblatt (siehe unten), Kicker „GESTERN“ bzw. Wochentag in Tinte weich.
- **Leere Tage:** keine Fläche, nur eine schmale Zeile (mind. 48 hoch): Tageszahl klein (Fraunces 20,
  Tinte blass), „Sonntag · nichts aufgeschrieben“ (Tinte weich), rechts mit mind. 12 Abstand „Nachtragen“ (500, Tinte); wird es eng, bricht der Text um.
- **Sicherungs-Erinnerung** (nur wenn nötig: Sicherung aus und lange nicht gesichert, oder sie klappt nicht): direkt unter dem Blatt für heute
  eine gestrichelte Zeile (Radius 14, mind. 48 hoch, Innenabstand 16/12): Text in Tinte weich
  („Seit 34 Tagen keine Sicherung“), rechts „Sichern →“ bzw. „Ansehen →“ (500, Tinte).
- Am Ende ein Blatt „Archiv · Alle älteren Einträge →“.

### Tagesblatt (Karte)
Fläche in Blatt-Farbe, Radius 10, kein Rand, kein Schatten. Links die große **Tageszahl**
(Fraunces Display 34, heute im Akzent) mit Wochentag-Kürzel darunter (Kicker, Tinte blass),
rechts eine Kicker-Zeile („HEUTE“, „GESTERN“ oder Wochentag) und die Textvorschau
(„Diese Woche“ 2 Zeilen, Archiv 4 Zeilen), darunter kleine Fotos.
(Leere Tage gibt es nur auf „Diese Woche“, dort als schmale Zeile, siehe oben.)

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
Willkommensseite: Logo (72, weißer Kreis ohne Rand) vor dem Schriftzug „Relic“ (Fraunces 44, 600), Abstand 16 –
wie die Kopfzeile, nur größer.
Letzter Schritt: automatische Sicherung anbieten (Aufbau wie der Sperr-Schritt: Titel, Text, Kleingedrucktes,
Hauptknopf „Ja, Ordner wählen“, darunter „Vielleicht später“).
Fortschrittspunkte oben (aktueller Schritt als kurzer Strich in Tinte, übrige als Punkte in
Linie-Farbe), große Fraunces-Überschrift, Text in Tinte weich, Knöpfe unten.

### Meldungen
Kurze Meldungen („Eintrag gespeichert“) unten als dunkle Leiste (Fläche Tinte, Schrift Blatt).
Erfolg in den Einstellungen: grüner Haken + Text in Erfolg-Farbe (Export, letzte Sicherung).
Laufender Vorgang (Sicherung startet/läuft): Punkt 8 + Text (500) in Läuft-Farbe.

---

## 5. Logo und App-Symbol

Das Symbol: ein großes, angeschnittenes **„R“ aus Fraunces** (144pt SemiBold) mit einem gefüllten
**Punkt** links oben – Tinte `#24221E` auf Weiß `#FFFFFF`.
Das Logo bleibt immer hell (Tinte auf Weiß), auch im Dunkelmodus.

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
  --paper: #F3F3F1; --sheet: #FFFFFF; --ink: #22211F; --ink-soft: #5C5955;
  --ink-faint: #8C8882; --rule: #DAD8D3; --accent: var(--ink); --on-accent: #FFFFFF;
  --success: #3A6B45; --progress: #8A5A00;
  --serif: "Fraunces", Georgia, serif;   /* opsz 144, SOFT 100 für Große Titel */
  --sans: "DM Sans", system-ui, sans-serif;
  --radius-sheet: 14px; --radius-button: 8px;
}
@media (prefers-color-scheme: dark) {
  :root {
    --paper: #1A1A19; --sheet: #252524; --ink: #ECEAE6; --ink-soft: #B3B0AA;
    --ink-faint: #7C7975; --rule: #42413E; --on-accent: #1A1A19; --success: #8FC29A; --progress: #E2B54A;
  }
}
```

---

## Ordner

- `branding/` – **verbindlich**: diese Datei, Logo und App-Symbol (für App und Website).
- Frühere Ideen und Entwürfe (Ordner `design/`) wurden entfernt und sind nur noch in der
  Git-Geschichte zu finden. Es gibt keine anderen Design-Vorlagen.
