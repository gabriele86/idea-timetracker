# Branch Time Tracker

*[English version](README.en.md)*

Plugin IntelliJ per tenere traccia del tempo lavorato sui **branch Git** di progetti diversi.

## Funzionalità

- **Voce automatica al checkout**: la prima volta che lavori su un branch (checkout) viene creata
  una voce di tracking. Puoi anche aggiungerne a mano con il pulsante `+`.
- **Avvia / Pausa / Ferma / Elimina** il tracking di ogni branch dalla tool window *Time Tracker*.
- **Un solo timer attivo per volta**: avviare una voce mette in pausa quella corrente.
- **Pausa automatica**:
  - su inattività dell'IDE (timeout configurabile, default 5 min; ripresa automatica opzionale);
  - al cambio di branch (la voce del branch che lasci va in pausa);
  - alla chiusura dell'IDE (così le ore notturne non vengono conteggiate).
- **Riepilogo giornaliero** (tab *Riepilogo*): totale della giornata, dettaglio per progetto e per
  branch, navigazione tra i giorni e totali degli ultimi 7 giorni.
- I dati sono **globali a livello IDE** (tutti i progetti in un'unica vista) e persistiti in
  `<config>/options/branchTimeTracker.xml`.

## Lingue

L'interfaccia è tradotta in **inglese** (default) e **italiano**. Le stringhe vivono in
`src/main/resources/messages/TimeTrackerBundle[_it].properties` e passano da
`com.github.gbassi.timetracker.TimeTrackerBundle`.

La lingua segue il locale dell'IDE (language pack), non quello del SO. Non esiste un language
pack italiano ufficiale JetBrains: per vedere l'italiano avvia l'IDE con `-Didea.locale=it`
(riga in *Help > Edit Custom VM Options*) oppure imposta la registry key `i18n.locale`.

## Requisiti

- IntelliJ IDEA 2024.3+ (build 243+)
- JDK 21 (già usato dalla toolchain Gradle)

## Sviluppo

```bash
./gradlew test          # test unitari (logica di accumulo tempo)
./gradlew buildPlugin    # produce build/distributions/intellij-timetracker-<versione>.zip
./gradlew runIde         # avvia un IDE sandbox con il plugin installato
./gradlew verifyPlugin    # plugin verifier contro IC 2024.3
```

Se `gradle/wrapper/gradle-wrapper.jar` manca, rigeneralo con *Gradle > Add Gradle Wrapper* dal
Gradle bundle di IntelliJ, oppure `gradle wrapper --gradle-version 8.10.2`.

Per installare il plugin in un IDE reale: *Settings > Plugins > ⚙ > Install Plugin from Disk…* e
seleziona lo zip in `build/distributions/`.

## Impostazioni

*Settings > Tools > Branch Time Tracker*:

| Opzione | Default | Descrizione |
|---|---|---|
| Metti in pausa dopo (minuti) | 5 | Timeout di inattività |
| Riprendi automaticamente | on | Riavvia il timer alla ripresa dell'attività |
| Avvia il tracking al checkout | off | Se off, al checkout la voce è solo creata |
| Metti in pausa al cambio branch | on | Pausa la voce attiva quando cambi branch |


## Struttura

```
model/        BranchEntry, DayBucket, TrackerState, EntryState
service/      TimeTrackerService (stato persistente + ticker), TimeAccrual (split mezzanotte),
              TimeTrackerListener (message bus)
git/          BranchWatcher (GitRepositoryChangeListener -> rileva il checkout)
listeners/    TrackerStartupActivity, AppCloseListener
ui/           TimeTrackerToolWindowFactory, BranchTablePanel, DailySummaryPanel, AddEntryDialog
settings/     TimeTrackerConfigurable
```
