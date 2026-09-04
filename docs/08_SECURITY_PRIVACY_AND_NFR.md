# 08 — Sicurezza, privacy e requisiti non funzionali — CASSA

## 1. Threat model MVP

Dati:
- menu;
- ingredienti;
- prezzi;
- ordini;
- note;
- archivio;
- configurazione stampante.

Non sono richiesti:
- password;
- carte di pagamento;
- dati bancari;
- account;
- dati sanitari;
- cloud.

Rischio principale:
- perdita del dispositivo;
- cancellazione dati;
- accesso fisico non autorizzato;
- log eccessivi;
- corruzione DB;
- permessi Bluetooth.

## 2. Data minimization

Non aggiungere campi cliente se non necessari.
Nessun nome/telefono/indirizzo richiesto nel v1.

Le note sono testo libero:
- UI non deve incoraggiare inserimento di dati personali non necessari.

## 3. Local storage

Room nel sandbox Android.
Nessun upload esterno.

Non introdurre encryption library senza un threat model che la richieda.
Il dispositivo dovrebbe essere protetto da lock screen.

## 4. Backup

Nel v1:
- disabilitare backup cloud automatico del DB;
- documentare rischio di perdita.

Non promettere disaster recovery.

Post-MVP:
- export/import controllato con versione schema e validazione.

## 5. Permessi

Richiedere solo ciò che serve.

Bluetooth:
- Android 12+ `BLUETOOTH_CONNECT`;
- `BLUETOOTH_SCAN` solo se si implementa scanning;
- preferire device già paired.

Storage:
- SAF, nessun `MANAGE_EXTERNAL_STORAGE`.

Internet:
- non necessario al core; evitare permission se non usata.

## 6. Logging

Release:
- non loggare note;
- non loggare contenuto completo ordine;
- non loggare menu completo;
- non loggare MAC address in chiaro senza necessità;
- non loggare bytes receipt.

Debug:
- logging tecnico limitato.

## 7. Integrità dati

- transazioni Room;
- vincolo single draft;
- Accepted immutable;
- numbering state atomico;
- import ODS atomico;
- migration tests.

## 8. Performance targets ingegneristici

Non SLA contrattuali; obiettivi:
- search percepita immediata su menu pizzeria;
- quick add senza spinner;
- DB writes brevi;
- import con progress se supera tempo percepibile;
- Bluetooth sempre su dispatcher I/O.

Evitare premature optimizations.

## 9. Affidabilità

- draft sopravvive process death;
- Accepted sopravvive failure stampa;
- retry non cambia numero;
- no destructive migration release;
- no menu partial import.

## 10. Accessibilità

- font scale;
- touch targets;
- contrasto;
- content descriptions;
- wrapping;
- errori testuali;
- non dipendere solo dal colore.

## 11. Localizzazione

UI v1 italiana.
Formati:
- valuta EUR;
- date/orari italiani in UI;
- `businessDate` internamente ISO.

Non tradurre dati menu.

## 12. Timezone

Business logic usa timezone salvata:
`Europe/Rome`.

Non dipendere implicitamente dal timezone del device senza settings, per evitare cambi accidentali.

## 13. Concorrenza

Single device non elimina race:
- doppio tap;
- coroutine concorrenti;
- stampa concorrente.

Proteggere:
- Accept via transazione e status check;
- Printer via Mutex;
- import con singolo job UI;
- pulsanti disabilitati durante operazioni.

## 14. Error UX

Errori devono:
- spiegare azione possibile;
- preservare dati;
- non mostrare stacktrace.

Esempi:
- `Bluetooth disattivato. Attivalo e riprova.`
- `Stampante non configurata. Seleziona una stampante nelle Impostazioni.`
- `Il file contiene 4 errori. Correggili prima di importare.`

## 15. App lifecycle

- non dipendere da service background per draft;
- DB commit immediato;
- stampa richiesta esplicita foreground;
- se Activity ricreata durante stampa, ViewModel/service deve evitare duplicate automatiche.

## 16. Release hardening

- minify opzionale, non priorità;
- no debug menus release;
- no sample secrets;
- no demo order auto-seed unless esplicito;
- ProGuard/R8 rules solo se librerie lo richiedono;
- crash-free smoke test reale.

## 17. Privacy/legal note

La stampa NETUM è operativa non fiscale.
Il progetto non deve dichiarare conformità fiscale.
Un futuro RT richiede specifica separata e verifica professionale/normativa aggiornata.
