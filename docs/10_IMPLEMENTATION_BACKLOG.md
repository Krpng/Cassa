# 10 — Implementation Backlog — CASSA

## Regole

Priorità:
- P0: core indispensabile.
- P1: indispensabile prima uso reale.
- P2: rifinitura.

Ogni task si implementa separatamente.

## M0 — Bootstrap

### APP-001 [P0] Progetto Android
Obiettivo:
- creare progetto Kotlin/Compose, minSdk 26.

Done:
- build debug;
- install;
- Home placeholder.

### APP-002 [P0] Version catalog e dipendenze
- Compose;
- Navigation;
- Room;
- Hilt;
- Coroutines;
- DataStore;
- test.

### APP-003 [P0] Package architecture
Creare struttura di `04_ANDROID_ARCHITECTURE.md`.

### APP-004 [P0] Hilt
Moduli:
- Database;
- Repository;
- Clock;
- Printer.

### APP-005 [P0] Navigation shell
Rotte principali.

Demo M0:
- navigare tra placeholder senza crash.

## M1 — Core/domain

### CORE-001 [P0] Money
AC:
- cents Long;
- format EUR;
- no Double nel dominio.

Test: PRICE base formatting.

### CORE-002 [P0] Text normalization
AC:
- case/space/accent normalization;
- display immutato.

Test: SEARCH-005/006.

### CORE-003 [P0] ClockProvider
System + Fake.

### CORE-004 [P0] BusinessDateCalculator
Test: DATE-001..005.

### CORE-005 [P0] Domain models/enums
ProductCategory, OrderStatus, NumberingMode.

### CORE-006 [P0] PricingCalculator
Test: PRICE-001..008.

### CORE-007 [P0] Merge policy
Test: ORDER-001..006.

### CORE-008 [P0] Number formatting
Sequential padded min 3 digits.

## M2 — Room

### DB-001 [P0] Product/Ingredient/Additions schema
Include printedName.

### DB-002 [P0] ProductIngredient relation
Indices/unique.

### DB-003 [P0] Order schema
Single draft slot.

### DB-004 [P0] Order item/modifier schema
Snapshots.

### DB-005 [P0] App settings + numbering state
Defaults.

### DB-006 [P0] DAO read models
FullOrder/ProductWithIngredients.

### DB-007 [P0] Repositories base
Mapping Entity<->Domain.

### DB-008 [P0] Single draft invariant
Test DRAFT-004.

### DB-009 [P1] Schema export/migration foundation
No destructive release.

Demo M2:
- creare/leggere draft persistito.

## M3 — Menu/search/manual admin foundation

### MENU-001 [P0] Product repository CRUD logical
Create/update/activate/deactivate.

### MENU-002 [P0] Addition CRUD logical

### MENU-003 [P0] Ingredient editing

### MENU-004 [P0] Catalog Flow active

### MENU-005 [P0] ProductSearchEngine
Ranking + matched ingredient.

### MENU-006 [P1] Menu list UI
Pizze/Frittura/Bibite/Aggiunte.

### MENU-007 [P1] Product edit UI
name, printedName, price, category, ingredients, autoExtras, active.

### MENU-008 [P1] Addition edit UI.

Demo M3:
- catalogo manuale ricercabile.

## M4 — ODS

### ODS-001 [P0] SAF file picker
ODS only.

### ODS-002 [P0] OdsMenuParser ZIP/XML
Repeated cells/rows.

### ODS-003 [P0] Sheet detection by headers

### ODS-004 [P0] Product row parser
Ignore Prezzo Sala.

### ODS-005 [P0] Addition parser

### ODS-006 [P0] Price parser

### ODS-007 [P0] MenuImportValidator
Errors/duplicates/categories.

### ODS-008 [P0] Compare DB -> ImportPlan
new/update/unchanged.

### ODS-009 [P1] Preview UI
Detailed errors.

### ODS-010 [P0] Atomic commit
Rollback.

### ODS-011 [P1] Preserve active/autoExtras semantics.

Test: ODS-001..021.

Demo M4:
- importare file corretto e vedere catalogo.

## M5 — Draft order core

### ORD-001 [P0] Create/Get/Delete draft
One slot.

### ORD-002 [P0] Draft recovery startup UI

### ORD-003 [P0] Home draft banner

### ORD-004 [P0] New order conflict flow

### ORD-005 [P0] Order screen skeleton

### ORD-006 [P0] Search bar + filters

### ORD-007 [P0] Result row tap/+ behavior

### ORD-008 [P0] Quick add standard
Merge.

### ORD-009 [P0] Order list Flow
DB source of truth.

### ORD-010 [P0] Generic detail quantity/note/price

### ORD-011 [P0] Pizza additions UI/domain

### ORD-012 [P0] Pizza removals UI/domain

### ORD-013 [P0] automaticExtrasPricing

### ORD-014 [P0] Manual price

### ORD-015 [P0] Reset price

### ORD-016 [P0] Customized pizza no auto merge

### ORD-017 [P0] Custom quantity >1 same line

### ORD-018 [P0] Modify one/all prompt

### ORD-019 [P0] Atomic split

### ORD-020 [P0] Remove line/change quantity

### ORD-021 [P0] General note

### ORD-022 [P0] Total live from persisted state

Test: ORDER/DRAFT/PRICE.

Demo M5:
- creare ordine complesso, kill app, riprendere identico.

## M6 — Preview, acceptance, numbering

### NUM-001 [P0] Sequential service/state
Test NUM-T001..006.

### NUM-002 [P0] Random code formatter

### NUM-003 [P0] Stable random permutation generator

### NUM-004 [P0] Random state/cycles
2600 distinct.

### NUM-005 [P0] Mode switch preservation

### ACCEPT-001 [P0] Preview screen category ordering

### ACCEPT-002 [P0] Draft actions
Azioni: Modifica, Stampa bozza, Accetta, Accetta e stampa, Home, Nuovo ordine con gestione conflitto DRAFT.


### ACCEPT-003 [P0] AcceptOrder transaction

### ACCEPT-004 [P0] Double-accept protection

### ACCEPT-005 [P0] Accepted screen immutability

### ACCEPT-006 [P0] Remain on Accepted after accept

### ACCEPT-007 [P0] Hide accept CTAs

Test: ACCEPT + NUM.

Demo M6:
- ordine Accepted numerato e immutabile.

## M7 — Today/archive/duplicate

### ARCH-001 [P1] Today query/UI
businessDate.

### ARCH-002 [P1] Archive date filters

### ARCH-003 [P1] Number search

### ARCH-004 [P1] Accepted detail

### ARCH-005 [P1] Historical snapshot display

### ARCH-006 [P1] Duplicate transaction

### ARCH-007 [P1] Draft conflict on duplicate

Test ARCH/SNAP/DUP.

Demo M7:
- trovare vecchio ordine, duplicarlo.

## M8 — Printing foundation

### PRINT-001 [P0] Printer contracts/models

### PRINT-002 [P0] PrintableDocument/ReceiptComposer

### PRINT-003 [P0] PricePrintMode

### PRINT-004 [P0] Formatter draft/final
Sections, total, notes.

### PRINT-005 [P0] ESC/POS encoder

### PRINT-006 [P0] FakePrinterDriver

### PRINT-007 [P0] PrinterService + Mutex

Test PRINT-T001..014, 024..027.

Demo M8:
- fake print completo senza hardware.

## M9 — Bluetooth NETUM

### BT-001 [P1] Runtime permission manager

### BT-002 [P1] List bonded devices

### BT-003 [P1] Persist selected printer

### BT-004 [P1] RFCOMM/SPP driver

### BT-005 [P1] Timeout/disconnect/error mapping

### BT-006 [P1] Printer settings UI

### BT-007 [P1] Test print UI

### PRINT-020 [P1] PrintDraft integration

### PRINT-021 [P1] PrintAccepted integration

### PRINT-022 [P1] Accept and print after commit

### PRINT-023 [P1] Retry same order

### PRINT-024 [P1] uncertain outcome microcopy

### HW-001 [P1] NETUM calibration spike
chars/codepage/feed.

### HW-002 [P1] 10 consecutive prints

Demo M9:
- ordine reale stampato su NETUM.

## M10 — Hardening

### QA-001 [P0] Full unit suite

### QA-002 [P0] Room integration tests

### QA-003 [P1] Critical Compose UI tests

### QA-004 [P1] ODS sample regression fixture

### QA-005 [P1] Process death/recovery manual + automated where possible

### QA-006 [P1] Release permissions audit

### QA-007 [P1] Release logging audit

### QA-008 [P1] Disable unintended Android cloud backup

### QA-009 [P1] No destructive migrations

### QA-010 [P1] Smoke test release APK

### QA-011 [P2] Accessibility pass

### QA-012 [P2] UI polish

Demo M10:
- checklist release completa.

## Sequenza Codex

Ordine:
`M0 -> M1 -> M2 -> M3 -> M4 -> M5 -> M6 -> M7 -> M8 -> M9 -> M10`

Eccezione utile:
- M8 fake printing può essere iniziato in parallelo dopo M2, ma non è necessario.

## Definition of Ready

Task pronto se:
- ID;
- obiettivo;
- requisito;
- dati;
- dipendenze;
- acceptance/test;
- edge case noti.

## Definition of Done

Task DONE se:
- build compila;
- acceptance criteria soddisfatti;
- test collegati passano;
- nessun TODO necessario;
- niente business logic duplicata;
- errori gestiti;
- documentazione/schema aggiornati se richiesti;
- no regressioni evidenti.

## Strategia commit

Consigliato:
- un task o gruppo strettamente coeso per commit;
- messaggio con ID, es. `ORD-019 atomic split customized pizza`.

Non accumulare milestone intere in un unico commit.
