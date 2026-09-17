# 03 — UX/UI, schermate e flussi — CASSA

## 1. Principi UX

- Velocità prima dell'estetica decorativa.
- Azioni principali sempre evidenti.
- Testi in italiano.
- Pulsanti con etichetta testuale, non sole icone per azioni critiche.
- Touch target ampi.
- Contrasto elevato.
- Supportare font scale Android senza tagli di contenuto critico.
- Non usare il colore come unico segnale.
- Stati error/loading/empty espliciti.
- Evitare dialog non necessari durante il flusso rapido.
- Conferma obbligatoria solo per azioni distruttive: eliminazione draft, sostituzione draft, rimozione riga ordine.

## 2. Information architecture

```text
HOME
├── NUOVO ORDINE
│   ├── Ricerca/filtri
│   ├── Dettaglio prodotto
│   ├── Personalizzazione pizza
│   └── Anteprima
├── ORDINI DI OGGI
│   └── Dettaglio Accepted (giornata corrente)
├── MENU
│   ├── Pizze
│   ├── Frittura
│   ├── Bibite
│   ├── Aggiunte
│   ├── Modifica prodotto
│   └── Importa ODS
└── IMPOSTAZIONI
    ├── Numerazione
    └── Stampante
```

> **M7 product scope:** nessun `ARCHIVIO` storico. Solo `ORDINI DI OGGI` (`ACCEPTED` della `currentBusinessDate`).

## 3. Home

### Scopo
Far partire un ordine nel minor tempo possibile.

### Gerarchia
1. `NUOVO ORDINE` — CTA dominante.
2. Banner DRAFT se presente.
3. `ORDINI DI OGGI`.
4. Menu e Impostazioni secondari.

> `ARCHIVIO` rimosso dallo scope prodotto (M7 freeze). La UI Home può ancora mostrare un placeholder legacy fino al task di rimozione UI; non è requisito funzionale.

### Stato con draft
```text
ORDINE IN CORSO
3 articoli · € 27,50
[RIPRENDI ORDINE]
```

### Nuovo ordine con draft esistente
Dialog:
```text
Esiste già un ordine in corso.

[RIPRENDI]
[ELIMINA E CREA NUOVO]
[ANNULLA]
```

Elimina richiede conferma secondaria se il draft non è vuoto.

## 4. Recupero DRAFT all'avvio

Se non vuoto:
```text
Hai un ordine non completato.

[RIPRENDI]
[ELIMINA]
```

`ELIMINA`:
- conferma;
- cancella draft in transazione.

Se vuoto:
- non mostrare recupero.

## 5. Nuovo ordine

### Header (M5 UX — compact order workspace)

Nella parte alta della schermata principale del DRAFT, tre azioni testuali coerenti visivamente con l'attuale `INDIETRO` / `CassaBackButton`:

```text
[ NOTE ]        [ CERCA ]        [ ← INDIETRO ]
```

Posizionamento:
- `NOTE` → alto sinistra;
- `CERCA` → alto centro;
- `INDIETRO` → alto destra.

Regole:
- touch target `>= 48dp`;
- accessibilità corretta (content description distinti);
- stile coerente con `CassaBackButton` senza obbligatoriamente duplicarne l'implementazione se non appropriato;
- `INDIETRO` resta non distruttivo (stesso significato di back/home attuale).

Sotto l'header: titolo `Nuovo ordine` (o indicazione equivalente `Ordine in corso` se già prevista).

### Schermata principale — contenuto (compact)

La schermata principale **non** contiene più:
- editor inline `NOTA ORDINE`;
- pulsante inline `SALVA NOTA`;
- campo ricerca inline.

La schermata principale **mantiene**:
- `Nuovo ordine`;
- `ORDINE CORRENTE` + righe ordine;
- selettore categorie `TUTTI | PIZZE | FRITTURA | BIBITE`;
- elenco catalogo/prodotti della categoria selezionata;
- sticky `TOTALE` (ORD-022).

Controlli ORD-020 invariati: `+/-`, `RIMUOVI`, tap riga → dettaglio.
Custom pizza highlight invariato.

Obiettivo: liberare spazio verticale per il catalogo su smartphone portrait senza rimuovere funzioni.

### Filtri (schermata principale)

`TUTTI | PIZZE | FRITTURA | BIBITE`

Sulla schermata principale i filtri categoria guidano il catalogo inline.
La vista dedicata `CERCA` ha un **proprio** selettore categorie (stesse etichette), indipendente dal filtro MAIN: vedi sezione *Vista dedicata CERCA*.

### Catalogo principale (schermata ordine)

Riga:
```text
Margherita
Pomodoro, Fior di latte... (opzionale/compatto)
€ 7,00                         [+]
```

Se match ingrediente (solo nella vista `CERCA` dedicata):
```text
Margherita
Contiene: Parmigiano
€ 7,00                         [+]
```

Tap riga:
- apre dettaglio.

Tap `+`:
- quick add standard (stesso flow ORD esistente).

### Ordine corrente
Ogni riga del DRAFT mostra:
- controlli quantità `[-] quantity [+]`;
- nome;
- modifiche;
- nota sintetica;
- prezzo finale/line total;
- azione esplicita `RIMUOVI`;
- tap sulla riga (fuori da `+/-` e `RIMUOVI`) apre il dettaglio.

Regole quantità dalla lista:
- `[+]` aumenta di 1 e persiste subito;
- `[-]` con `quantity > 1` diminuisce di 1 e persiste subito;
- `[-]` con `quantity = 1` è disabilitato/non azionabile e non muta;
- `quantity = 0` non è ammesso e non elimina la riga.

`RIMUOVI`:
```text
Rimuovere questa riga?

[ANNULLA]
[RIMUOVI]
```

- `ANNULLA`: nessuna scrittura;
- `RIMUOVI` confermato: elimina atomicamente la riga e i suoi child modifier.

Touch target almeno `48dp`. `RIMUOVI` resta distinto dai controlli quantità.

### Nota ordine — overlay (ORD-021 + M5 UX compact)

`NOTE` apre una sovraimpressione/modal chiaramente separata dalla schermata ordine.

```text
NOTA ORDINE
[campo multilinea]
[ANNULLA]  [SALVA]
```

All'apertura:
- editor inizializzato dal `generalNote` **persistito**;
- dirty = false rispetto al valore Room.

`ANNULLA`:
- chiude overlay;
- nessuna mutation;
- modifiche locali non salvate scartate;
- `generalNote` persistito invariato.

`SALVA`:
- usa `UpdateGeneralNote` esistente;
- normalizzazione / guard / `updatedAt` ORD-021 invariati;
- successo → chiude overlay e torna all'ordine;
- fallimento → overlay resta aperto, testo locale preservato, errore visibile, retry possibile.

Flow re-emission mentre l'overlay è dirty:
- **non** sovrascrive il testo digitato (stesso contratto ORD-021).

Blank/whitespace → persisted `null`. Nessun autosave.

`NOTE` resta disponibile anche su DRAFT vuoto.

Semantica normativa completa: `docs/02_BUSINESS_RULES.md` (Nota generale ordine ORD-021).
`orders.generalNote` non attiva l'highlight viola delle pizze personalizzate.

### Vista dedicata CERCA (M5 UX compact)

Tap `CERCA` apre una vista dedicata alla ricerca (preferire stato/feature locale; nuova Navigation destination solo se strettamente necessaria).

```text
                        [ ← INDIETRO ]

Cerca prodotto
[campo ricerca]
TUTTI | PIZZE | FRITTURA | BIBITE
[risultati]
```

Layout filtri:
- subito sotto il campo ricerca;
- subito sopra i risultati;
- stile e comportamento coerenti con i chip della schermata MAIN;
- preferire riuso del componente/filtro esistente invece di duplicare UI;
- la lista risultati continua a occupare tutto lo spazio verticale restante.

In questa vista **non** mostrare:
- editor general note;
- lista ordine;
- sticky totale;

salvo necessità tecnica/accessibilità non invasiva.

#### Filtro categoria in CERCA (indipendente da MAIN)

All'apertura di `CERCA`:
- search category filter = `TUTTI` (default).

Il filtro della vista `CERCA` è **indipendente** dal filtro categoria della schermata MAIN.

Esempio:
- MAIN = `FRITTURA`;
- apro `CERCA` → search filter = `TUTTI`;
- in `CERCA` seleziono `PIZZE`;
- torno a MAIN → MAIN resta `FRITTURA`.

Riaprendo successivamente `CERCA`:
- search category filter riparte da `TUTTI`.

Nessuna persistenza DB/DataStore per questo stato UI (solo stato feature/in-memory della sessione di ricerca).

Semantica filtri (come in MAIN):
- `TUTTI` → nessun filtro categoria;
- `PIZZE` → `category == PIZZA`;
- `FRITTURA` → `category == FRITTURA`;
- `BIBITE` → `category == BIBITA`.

#### Query + categoria (AND)

Query e category filter lavorano in **AND**:
- risultato = prodotto che soddisfa la ricerca esistente **e** la categoria selezionata (se non `TUTTI`).

Esempio: query `pomodoro` + filtro `PIZZE` → solo pizze che matchano `pomodoro`.

Semantica ricerca: **invariata** rispetto al motore esistente:
- nome + ingredienti;
- case / accent tolerant;
- ranking corrente;
- solo prodotti attivi;
- **non** creare un secondo search engine.

Il category filter si applica ai candidati/risultati **senza** alterare ranking e normalizzazione del motore esistente.

`INDIETRO` (nella vista CERCA):
- torna alla schermata ordine corrente;
- stesso DRAFT;
- filtro MAIN invariato.

Quick-add (`+`) da risultato:
- persiste tramite lo stesso flow ORD esistente;
- **resta** nella schermata CERCA (non chiude la ricerca);
- query e category filter della ricerca restano invariati;
- nessuna modifica a merge policy, quantity rules, pricing, snapshots, total calculation.

Placeholder campo: `Cerca prodotto o ingrediente...` (o equivalente coerente).

### Totale live (ORD-022)

Nella schermata ordine DRAFT, il footer/area finale mostra chiaramente:

```text
TOTALE
€xx,xx
```

Regole UX:
- valore reattivo allo stato **persistito** Room (`order_items`), non a editor/form dirty o catalogo live;
- totale molto evidente e leggibile;
- accessibilità coerente con i pattern esistenti;
- rispettare `safeDrawing` / navigation insets;
- sticky nella schermata **principale**;
- **non** mostrare un secondo totale nella vista CERCA.

Empty DRAFT (nessuna riga):
```text
TOTALE
€0,00
```

Il totale resta visibile anche nello stato vuoto (insieme al messaggio empty state esistente). Non creare righe fittizie.

`COMPLETA` (FREEZE-B / M6):
- disponibile **solo** se il DRAFT persistito contiene almeno un `order_item`;
- DRAFT vuoto: `COMPLETA` **disabled**, non apre preview, **zero** consumo numerazione, **zero** write;
- se abilitato, apre l'acceptance preview read-only (sezione 9).

Footer/sticky (solo schermata principale):
- `TOTALE` (live, ORD-022);
- `COMPLETA` (FREEZE-B; enabled solo con ≥1 item persistito).

Semantica di calcolo, source of truth, `orders.totalCents` non-authoritative sul DRAFT, overflow e reattività: vedi `docs/02_BUSINESS_RULES.md` (Totale live DRAFT ORD-022).

### Empty state
Se nessuna riga:
`Aggiungi un prodotto per iniziare l'ordine.`

Un DRAFT senza items resta esistente (vuoto); Home/recovery seguono le regole già definite per draft vuoto.
L'azione `NOTE` resta disponibile anche su DRAFT vuoto.
Il `TOTALE` resta visibile a `€0,00` anche su DRAFT vuoto.

### Nessun risultato
`Nessun prodotto trovato.`

## 6. Dettaglio prodotto generico

Per tutte le categorie:
- nome;
- prezzo;
- quantità `[-] n [+]`;
- nota;
- modifica prezzo;
- salva;
- annulla.

Il dettaglio resta il flusso di editing avanzato. Il `+/-` e `RIMUOVI` della lista ordine (ORD-020) non sostituiscono e non alterano questo editor.

Per pizza aggiungere:
- `Aggiunte`;
- `Rimuovi ingrediente`.

Frittura/Bibita:
- nessuna sezione aggiunte/rimozioni.

## 7. Personalizzazione pizza

Layout:
```text
< Nome pizza >

Quantità
[-] 1 [+]

AGGIUNTE
[Aggiungi]

RIMOZIONI
[Rimuovi ingrediente]

NOTA
[________________]

PREZZO
€ 11,00
[Modifica prezzo]

[SALVA] [ANNULLA]
```

Se manual:
```text
€ 13,00  ✎
[Ripristina prezzo automatico]
```

### Lista rimozioni
Solo ingredienti della pizza.

### Lista aggiunte
Solo additions attive.

### `automaticExtrasPricing=false`
UI può mostrare:
`Le aggiunte non modificano automaticamente il prezzo di questo prodotto.`

Non menzionare nomi hardcoded.

## 8. Modifica riga aggregata pizza

Se pizza standard quantità > 1 e si apre una personalizzazione:

```text
Vuoi modificare una pizza o tutte?

[MODIFICA UNA]
[MODIFICA TUTTE]
[ANNULLA]
```

`Modifica una`:
- split atomico solo quando l'utente salva una personalizzazione valida.

Il cambio quantità tramite `+/-` della lista ordine non usa questo prompt.
Il caso editor `MODIFICA UNA` + cambio quantità nello stesso form resta fuori da ORD-020 e conserva il rifiuto conservativo corrente.

## 9. Acceptance preview (FREEZE-B / M6)

Entry: `COMPLETA` dalla schermata DRAFT (solo se ≥1 `order_item` persistito).

### Source of truth

**Solo** DRAFT persistito da Room. Non usare editor dirty / local unsaved state.

### Mutazioni

- **zero** write Room;
- **zero** allocazione/consumo numero;
- **zero** update di `orders.totalCents`.

### Ordinamento

Categorie nell'ordine fisso:

1. `PIZZE`
2. `FRITTURA`
3. `BIBITE`

Categorie vuote **non** mostrate. Dentro ogni categoria: `createdSequence ASC`. Nessun sorting alfabetico e nessuna rilettura catalogo per riordinare.

### Contenuto riga (snapshot persistiti)

- quantity;
- name / printed name secondo UX esistente;
- additions;
- removals;
- item note;
- finalUnitPrice;
- line total (`finalUnitPriceCents * quantity`).

Mostrare anche, se presenti/applicabili:

- `generalNote`;
- totale preview derivato: `SUM(finalUnitPriceCents * quantity)` con Money semantics ORD-022;
- **ignorare** un eventuale `orders.totalCents` stale durante DRAFT.

Overflow del totale checked: acceptance non consentita; nessuna mutation; nessun numero; errore esplicito.

### Azioni preview — M6

Esporre **solo**:

- `[ INDIETRO / ANNULLA ]`
- `[ ACCETTA ]`

**Non** mostrare ancora in M6:

- `ACCETTA E STAMPA`
- `STAMPA BOZZA`

(appartengono alle milestone PRINT successive).

System Back: chiude preview → torna al DRAFT; zero mutation; zero consumo.

`ACCETTA`: esegue `AcceptOrder` atomico (business §18 / schema §16). Durante accept: CTA disabled/in-progress.

**PRINT-022 / D-069:** dopo `AcceptOrderResult.Accepted` (commit riuscito), **una sola** chiamata automatica `PrinterService.printAccepted(result.orderId)` sul path del comando ACCETTA — mai da observer/`LaunchedEffect` su stato Accepted; mai dentro la transazione Room; nessun precheck stampante prima di ACCETTA. Fallimento stampa → ordine resta ACCEPTED; feedback `Ordine accettato. ` + errore stampante mappato. Successo stampa → feedback transient `Ordine inviato alla stampante` (come PRINT-021). Manuale post-accept `STAMPA` (PRINT-021) resta disponibile a Idle e resta bloccata durante auto-print.

## 10. Stato durante Accept

Proteggere doppio tap:

- disabilitare CTA;
- mostrare progress minimale.

Il dominio resta comunque responsabile dell'idempotenza (`AlreadyAccepted` / `OrderNotDraft`).

## 11. Schermata Accepted (FREEZE-B / M6)

Dopo `ACCETTA` riuscita:

- **restare** sulla schermata ordine `ACCEPTED` (read-only);
- **non** tornare automaticamente Home.

Mostra:

- `displayNumber` (grande);
- ordine (snapshot);
- `totalCents` snapshot;
- data/ora su schermo se già prevista dall'UX esistente.

CTA M6 / post-PRINT:

- `[ STAMPA ]` — **PRINT-021 COMPLETE** su Accepted: `printAccepted(orderId)`; durante PRINT-022 auto-print in-flight resta disabled (stesso `AcceptedPrintUiState`);
- `[ HOME ]` — torna Home;
- `[ NUOVO ORDINE ]` — crea/apre un nuovo DRAFT tramite il flusso normale; il vecchio `ACCEPTED` **non** viene riutilizzato.

Nascondi / rimuovi dopo successo:

- `ACCETTA`;
- `ACCETTA E STAMPA` (non presente in M6);
- qualsiasi modifica ordine/item.

## 12. Stampa fallita

Se ordine già accettato:
```text
Ordine A37 salvato.

Impossibile stampare.

[RIPROVA]
[CHIUDI]
```

Se outcome fisico incerto:
```text
La connessione si è interrotta durante la stampa.
Verifica se la copia è uscita prima di riprovare.

[RIPROVA]
[CHIUDI]
```

Se bozza:
- stesso pattern;
- nessun numero;
- draft invariato.

## 13. Ordini di oggi

Solo `ACCEPTED` con `businessDate = currentBusinessDate`.

Lista:
- displayNumber;
- acceptedAt;
- totale;
- eventualmente count righe.

Ordine:
- acceptedAt DESC.

Empty:
`Nessun ordine accettato nella giornata operativa corrente.`

Tap:
- dettaglio Accepted della giornata corrente (ARCH-004);
- navigation e CTA: vedi §15 (FREEZE).

Niente filtri IERI / selezione data / ricerca cross-day (ARCH-002/003 OBSOLETE).

## 14. Archivio — OBSOLETE

> **CANCELLED BY PRODUCT SCOPE CHANGE (M7).** Nessun archivio storico, nessun chip `IERI` / `SCEGLI DATA`, nessuna ricerca numero cross-day.

Contratto storico (non attivo):
- ricerca numero;
- chip `OGGI` / `IERI` / `SCEGLI DATA`;
- risultati multi-giorno.

## 15. Dettaglio Accepted (giornata corrente) — ARCH-004 FREEZE

Solo ordini `ACCEPTED` della `currentBusinessDate` (ARCH-004).

### Entry / navigation

```text
ORDINI DI OGGI → tap row → Accepted detail(orderId)
```

Uscita:

- `[ INDIETRO ]` → `ORDINI DI OGGI` (lista Today);
- System Back → `ORDINI DI OGGI`;
- `[ HOME ]` → Home, senza riaprire preview/edit/accept flow.

### Guard

Mostrabile solo se:

```text
status = ACCEPTED
AND businessDate = currentBusinessDate
```

Altrimenti stato **Unavailable / Not found** (no crash), con uscita consentita via `INDIETRO` e/o `HOME`:

- orderId assente / eliminato (anche mid-open dopo RET-001 purge);
- ordine `DRAFT`;
- `ACCEPTED` di `businessDate` precedente.

Il dettaglio deve **osservare** l'ordine: se RET-001 lo elimina mentre la schermata è aperta → Unavailable, non dati stale.

### Contenuto (snapshot-only)

Header:

- `displayNumber`;
- data/ora (`acceptedAt`);
- totale persistito (`totalCents` Accepted).

Sezioni ordine — **stessa regola del preview Acceptance** (riuso obbligatorio di `AcceptancePreviewOrdering` o primitive equivalente condivisa):

1. `PIZZE`
2. `FRITTURA`
3. `BIBITE`

Categorie vuote omesse. Dentro ogni categoria: `createdSequence ASC`.

Per ogni riga (solo snapshot persistiti; **nessun** reprice / rilettura catalogo):

- quantity;
- product name / printed name secondo UX esistente;
- additions / removals snapshot;
- item note;
- finalUnitPrice;
- line total (`finalUnitPriceCents * quantity`);
- manual price snapshot se presente.

Mostrare anche `generalNote` se presente.

### Azioni ARCH-004

- `[ STAMPA ]` — label invariata (**mai** `RISTAMPA`). **PRINT-021 / D-068 COMPLETE:** enabled su detail current-day ACCEPTED (non busy / non printing); invoca `PrinterService.printAccepted(orderId)`.
- `[ INDIETRO ]` — vedi navigation.
- `[ HOME ]` — vedi navigation.

**Non** mostrare in **ARCH-004 alone**:

- label `RISTAMPA` (vietata; label normativa unica = `STAMPA`);
- `NUOVO ORDINE DA QUESTO` (**attivata da ARCH-006 / D-046**; conflitto DRAFT = ARCH-007).

### Read-only

Nessun controllo di modifica:

- `+/-`, `RIMUOVI`;
- edit note / additions / removals / manual price;
- `ACCETTA`, `COMPLETA`.

## 16. Duplicazione — COMPLETE (D-046 / ARCH-006 + D-047 / ARCH-007)

> **COMPLETE.** `NUOVO ORDINE DA QUESTO` = REQUIRED da Accepted Order Detail (solo current-day ACCEPTED). Contratto: `docs/02_BUSINESS_RULES.md` §23 + D-046 + D-047. Implementazione: ARCH-006 `67be68b`; ARCH-007 `fcd614b` (contract freeze `86baf12`).

### CTA (ARCH-006 — COMPLETE)

Sul detail valido (`ACCEPTED` + currentBusinessDate):

- `[ NUOVO ORDINE DA QUESTO ]` — **VISIBLE + ENABLED**;
- `[ STAMPA ]` — **PRINT-021 / D-068 COMPLETE:** enable su detail valido (vedi §15); invoca `PrinterService.printAccepted(orderId)`;
- `[ INDIETRO ]` / `[ HOME ]` — invariati.

### Success navigation — no active DRAFT (ARCH-006, invariato)

Duplicate success → apri **direttamente** il nuovo DRAFT nella **NewOrder** UI standard.
Non restare sul detail. Non andare Home. Non creare un secondo editor.

### Conflitto DRAFT — dialog (ARCH-007 / D-047 — COMPLETE)

Se esiste **active DRAFT** (incluso DRAFT vuoto persistito `draftSlot=1`):

1. ARCH-006 repository → typed `DraftConflict` + zero writes (invariato);
2. ARCH-007 UI → **conflict dialog** (non solo errore terminale).

#### Dialog copy (frozen)

- Titolo: `C'È GIÀ UN ORDINE IN CORSO`
- Testo: `Puoi riprendere l'ordine in corso oppure eliminarlo e creare un nuovo ordine da questo.`
- Azioni:
  - `[ RIPRENDI ORDINE IN CORSO ]`
  - `[ ELIMINA DRAFT E DUPLICA ]`
  - `[ ANNULLA ]`
- Nessuna seconda confirmation su `ELIMINA DRAFT E DUPLICA`.

#### RIPRENDI ORDINE IN CORSO

- Zero writes (DRAFT e source ACCEPTED invariati; nessuna duplicazione).
- Navigazione → NewOrder standard con `existingDraftId`.
- Valido con items **o** DRAFT vuoto persistito.

#### ANNULLA

- Chiude dialog; resta sul dettaglio ACCEPTED; zero writes.

#### ELIMINA DRAFT E DUPLICA

- Operazione atomica ONE Room transaction (vedi BUSINESS_RULES §23 / SCHEMA §18b).
- Success → NewOrder standard con **esattamente** `newDraftId`; old DRAFT assente.
- Failure → rollback; old DRAFT preservato; messaggio errore tipizzato (no silent).

## 17. Gestione menu

Landing:
- `IMPORTA ODS`;
- `PIZZE`;
- `FRITTURA`;
- `BIBITE`;
- `AGGIUNTE`.

Lista:
- ricerca;
- attivo/inattivo;
- prezzo;
- tap modifica.

### Modifica prodotto
Campi:
- nome;
- nome stampato;
- categoria;
- prezzo;
- ingredienti;
- `Prezzo automatico aggiunte` (`automaticExtrasPricing`);
- attivo.

Aggiunte:
- nome;
- nome stampato;
- prezzo;
- attivo.

Preferire disattivazione alla cancellazione.

## 18. Import ODS

Flusso:
1. `Seleziona file ODS`.
2. parsing.
3. validazione.
4. preview.
5. conferma.
6. import atomico.
7. risultato.

Preview:
```text
Nuovi: 12
Aggiornati: 3
Invariati: 35
Avvisi: 1
Errori: 0

[CONFERMA IMPORTAZIONE]
[ANNULLA]
```

Se errori bloccanti:
- disabilitare conferma;
- elencare foglio/riga/campo/messaggio.

## 19. Impostazioni numerazione (D-040 RESOLVED — NUM-005)

**Owner UI:** `SettingsScreen` esistente. Entry: Home → `IMPOSTAZIONI` → `SettingsScreen`. Nessun nuovo screen / destination.

### Controllo

Titolo: **Modalità numerazione**.

Scelte mutuamente esclusive (segmented / radio):

- `SEQUENZIALE` (`SEQUENTIAL`);
- `CASUALE` (`RANDOM`).

Default persistito: `SEQUENTIAL`. Source of truth: `app_settings.numberingMode` (singleton). La UI **deriva** sempre dalla selezione persistita; non mantenere una seconda source of truth locale.

### Salvataggio

Salvataggio **immediato** al tap (no pulsante SALVA generale).

- Successo → UI mostra la nuova selezione.
- Fallimento → resta la selezione persistita precedente; errore/retry coerente con pattern app; non inventare stato locale “salvato”.
- Durante `saving`: evitare doppio tap concorrente.

### Stati UI congelati

- `loading settings`
- `loaded SEQUENTIAL`
- `loaded RANDOM`
- `saving`
- `save error` (+ retry)

System Back: torna alla schermata precedente **senza** mutation extra.

### Accessibilità

- label testuale completa per entrambe le opzioni;
- stato selected leggibile (non solo colore);
- touch target ≥ 48dp.

### Semantica prodotto

Cambio immediato per le **prossime** accettazioni. Non riscrive DRAFT / ordini esistenti. Non resetta `nextSequentialNumber` né `randomSeed` / `randomSeedInitialized` / `randomCycle` / `randomPosition`. Nessun consumo numerazione al cambio settings.

## 20. Impostazioni stampante

MVP consigliato:
- pairing effettuato nelle impostazioni Android;
- app elenca dispositivi Bluetooth già associati;
- selezione stampante;
- stato;
- test stampa.

Mostrare:
- nome dispositivo;
- indirizzo mascherato/tecnico solo se utile;
- `STAMPA DI PROVA`.

Errori:
- Bluetooth spento;
- permesso negato;
- nessun dispositivo selezionato;
- connessione fallita.

## 21. Stati di loading

Usare solo quando operazione percepibile:
- import ODS;
- accesso database iniziale se necessario;
- connessione/stampa.

Non mostrare spinner per ogni tap locale istantaneo.

## 22. Accessibilità

- Material 3.
- touch target almeno coerenti con linee guida Android;
- testo non inferiore a dimensioni leggibili;
- supporto font scaling;
- contrasto adeguato;
- `contentDescription` per icone non decorative;
- focus order sensato;
- non affidarsi a rosso/verde da soli;
- nomi lunghi con wrapping, non shrink aggressivo.
