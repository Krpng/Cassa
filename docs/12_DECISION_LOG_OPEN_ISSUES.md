# 12 — Decision Log, assunzioni e open issues — CASSA

## Decisioni congelate

### D-001 Android nativo
Kotlin + Compose.

### D-002 Offline-first
Nessun backend/login.

### D-003 Single device
Nessuna sync concorrente multi-device.

### D-004 DB source of truth
Room.

### D-005 Un solo Draft
unique draft slot.

### D-006 Business day
05:00 Europe/Rome.

### D-007 Numbering
`NumberingMode`: `SEQUENTIAL | RANDOM`. Default `SEQUENTIAL`. Persisted in `app_settings.numberingMode` (singleton). Daily numbering state per `businessDate`. Mode switch preserves both independent states; affects only future accepts. UI owner: `SettingsScreen` (D-040 RESOLVED).

### D-008 Random code
`[A-Z][0-9][0-9]`, 2600/ciclo, no repeat within cycle. FREEZE-A: XorShift32 + Fisher–Yates bit-stable; seed once via `randomSeedInitialized`; cycle advance keeps same seed.

### D-009 Categories
Pizze/Frittura/Bibite.

### D-010 Search
Nome + ingredienti, ranking definito.

### D-011 Product interaction
row tap details, `+` quick add.

### D-012 Personalized pizzas
No automatic dedup.

### D-013 Modify one/all
Standard pizza aggregated qty>1.

### D-014 Extras
Solo pizza.

### D-015 Removals
Solo ingredienti pizza, no price decrease.

### D-016 automaticExtrasPricing
Flag configurabile, no hardcoded names.

### D-017 Manual price
Absolute precedence.

### D-018 Accepted immutable
Read/reprint only while the order is retained (current business day).
> Duplicate: **PENDING PRODUCT DECISION** (M7).

### D-019 Duplicate — PENDING PRODUCT DECISION
> **SUPERSEDED as active M7 requirement.** Exact snapshots/prices to new Draft was the historical contract; do not implement ARCH-006/007 until explicitly re-authorized for current-day orders.

### D-020 ODS
Prezzo Asporto only; Sala ignored.

### D-021 ODS reimport
Same normalized product name update, new name create, absent stays.

### D-022 Printed name
Optional for product/addition.

### D-023 Printer
NETUM 80mm test, generic ESC/POS architecture.

### D-024 Cutter
Not required.

### D-025 Print header
Number only, no date/time.

### D-026 Price print
Detailed initially; TotalOnly available.

### D-027 Reprint
No RISTAMPA label.

### D-028 Copies
One per explicit print action.

### D-029 Accept and print
Commit first, print after.

### D-030 Non-fiscal
No RT.

### D-040 Numbering mode Settings UI ownership — RESOLVED (2026-09-14)
Owner UI: **`SettingsScreen` esistente** (Home → `IMPOSTAZIONI`). Nessun nuovo screen, nessuna nuova navigation destination, nessun task Settings separato. Controllo in Settings: “Modalità numerazione” (`SEQUENZIALE` / `CASUALE`), segmented/radio, save immediato su `app_settings.numberingMode` (singleton `id=1`, default `SEQUENTIAL`). Semantica mode switch già congelata in FREEZE-A / §16 business rules (future accept only; stati SEQUENTIAL/RANDOM indipendenti e preservati; DRAFT/ACCEPTED invariati). NUM-005 implementa domain (`AppSettingsDao` / `SettingsRepository` o equivalenti) + UI su `SettingsScreen`; **NOT IMPLEMENTED** finché non autorizzato.

### D-041 M6 FREEZE-A / FREEZE-B (2026-09-12)
Congelati: algoritmo random XorShift32+Fisher–Yates; acceptance preview zero-write/zero-consume; AcceptOrder singola Room transaction; post-accept `STAMPA` visible disabled in M6; no `ACCETTA E STAMPA` / `STAMPA BOZZA` in preview M6. M6 production remains NOT STARTED.

### D-042 RANDOM seed initialization marker (2026-09-14)
Congelato: `numbering_state.randomSeedInitialized` (`INTEGER NOT NULL DEFAULT 0`). Marker autorevole di seed non inizializzato vs inizializzato. Filler `randomSeed=0L` consentito solo quando `initialized=false` e **non** è sentinel. `0L` è seed valido quando `initialized=true`. Migration additiva obbligatoria (**DB-010**); no destructive migration. Existing rows → `false` (RANDOM not yet consumed).

### D-043 ACCEPT-002 formal closure — COMPLETE / satisfied by ACCEPT-001 (2026-09-14)
ACCEPT-002 (Draft / preview actions M6) è **COMPLETE** senza lavoro production/test aggiuntivo: la superficie azioni (`COMPLETA` gated, preview read-only, `INDIETRO/ANNULLA`, `ACCETTA` only, no CTA stampa, zero write/zero number; ACCEPT-T008/T009/T020) è già materializzata e verificata da ACCEPT-001 (`d6e7ff9`). Ownership: ACCEPT-001 = preview content/order/total; ACCEPT-002 = action surface (satisfied); ACCEPT-003 = AcceptOrder + ACCETTA enable/wiring + number allocation. **`ACCETTA` resta visible+disabled** finché ACCEPT-003; nessuna fase intermedia e nessuna preallocazione numero.

### D-044 M7 CHANGE-OF-SCOPE — no historical archive / daily purge (2026-09-15)
Congelato:
- nessun archivio storico Accepted;
- consultabili solo `ACCEPTED` con `businessDate = currentBusinessDate` (soglia 05:00 / `businessDayStartMinutes = 300`);
- hard delete `ACCEPTED` dove `businessDate < currentBusinessDate` (RET-001);
- DRAFT preservato al cambio giornata; accept dopo 05:00 → nuova businessDate;
- `numbering_state` storico non cancellato dal purge; numerazione nuova giornata = stato della nuova data;
- ARCH-001 PRESERVED; ARCH-002/003/005 OBSOLETE; ARCH-004 = dettaglio giornata corrente; ARCH-006/007 PENDING PRODUCT DECISION;
- Home: `ORDINI DI OGGI` preserved; concetto `ARCHIVIO` removed from product scope;
- schema DB v2 unchanged / migration NONE for RET-001 MVP (explicit removals delete, then orders);
- correttezza purge su app enter-in-use; esecuzione esatta 05:00 Android non richiesta.

### D-045 ARCH-004 CTA / navigation contract freeze (2026-09-15)
Congelato per dettaglio Accepted current-day (ARCH-004):
- read-only; guard `ACCEPTED` + `businessDate = currentBusinessDate`; observe → Unavailable se missing/DRAFT/old/purged (no crash, no stale);
- ordering = stesso preview Acceptance (`AcceptancePreviewOrdering`: PIZZE → FRITTURA → BIBITE; `createdSequence ASC`);
- label stampa UI = **`STAMPA`** (mai `RISTAMPA`); in ARCH-004 `STAMPA` = VISIBLE + DISABLED fino a PRINT (M8/M9);
- navigation: Today tap → detail; `INDIETRO`/System Back → Today; `HOME` → Home (no reopen preview/edit);
- `NUOVO ORDINE DA QUESTO` = NOT VISIBLE (owner ARCH-006; conflitto DRAFT = ARCH-007);
- test dedicati ARCH-T010..ARCH-T026 (non riusare ARCH-T001..003).

## Reconciliation decisions made in final pack

### R-001 Product uniqueness
Earlier schema considered `(normalizedName, category)`.
Latest reimport rule says same product name updates even if category changes.
Final:
- unique normalizedName globally in `products`.

### R-002 Numbering settings location
A later architecture note proposed DataStore.
Because numberingMode participates in acceptance semantics, final:
- business settings in Room.

### R-003 Printer settings location
Noncritical, final:
- DataStore.

### R-004 hasManualPrice
Earlier schema had boolean + nullable manual price.
Final:
- boolean derived, not persisted, to avoid inconsistency.

### R-005 Legacy payment notes
Older intermediate notes described contanti/carta.
Not reconfirmed in final order workflow.
Final:
- excluded v1.

### R-006 Legacy Epson
Superseded by NETUM test + vendor-independent driver.

### R-007 PrintJob table
Intermediate proposal only.
Final:
- no persisted print job in v1.

## Assunzioni operative

- pairing Bluetooth può essere fatto in Android Settings;
- menu size modesta;
- quantity positive integer;
- prices non-negative;
- no need customer fields;
- accepted retention = **current business day only** (hard delete older ACCEPTED; RET-001);
- menu admin access non protetto da login nel v1.

## Open issues BLOCCANTI prima uso reale, non prima coding

### O-001 Correzione file ODS
Current sample additions has invalid rows.
Vedere audit.

### O-002 automaticExtrasPricing setup
Dopo primo import impostare manualmente a false i prodotti che devono ignorare prezzo automatico aggiunte.
Non hardcodare.

### O-003 Printer calibration
Need physical test:
- chars;
- codepage;
- euro;
- feed.

## Open issues non bloccanti coding

### O-010 UI tablet layout
Nessun device size specificato. Implementare responsive semplice.

### O-011 PricePrintMode
Default Detailed. Passare a TotalOnly solo dopo prova leggibilità.

### O-012 Backup
Post-MVP; rischio perdita dati documentato.

### O-013 Fiscal integration
Separate future project/feature.

## Idee storiche NON normative

- payment method;
- change calculator;
- print font profile Standard/Grande/Extra;
- Epson mandatory;
- QR test;
- customer profile;
- cloud backup.

Non implementarle per "non tralasciare": sono conservate qui proprio per indicare che sono state considerate ma **non approvate nello scope corrente**.
