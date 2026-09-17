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

### DB-010 [P0] Additive migration: `numbering_state.randomSeedInitialized`
Contract patch (M6 RANDOM-seed initialization state).

- ADD COLUMN `randomSeedInitialized INTEGER NOT NULL DEFAULT 0`;
- bump Room DB version;
- **no** destructive migration / no DB recreate;
- existing rows → `false` (RANDOM not yet consumed in v1).

Status: **NOT IMPLEMENTED**. Prerequisite before closing NUM-001 against the patched contract and before NUM-003 seed init.

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

Azioni direttamente sulle righe del DRAFT nella schermata ordine.

Quantità lista:
- ogni riga offre `[-] quantity [+]`;
- `+`/`-` persistono subito in Room (no dettaglio, no `SALVA`);
- `quantity >= 1`;
- `[-]` a `quantity = 1` non muta e non elimina;
- `quantity = 0` non valido e non è meccanismo di delete;
- stessa `order_item`: preserva id, `createdSequence`, snapshot, customization; no split/merge; no ricalcolo unit price dal catalogo;
- `lineTotal = finalUnitPrice * quantity`.

Rimozione:
- azione esplicita `RIMUOVI` con conferma `Rimuovere questa riga?` / `ANNULLA` / `RIMUOVI`;
- delete atomico item + child additions/removals secondo schema reale; nessun orphan;
- `orders.updatedAt` via `ClockProvider`;
- ultima riga rimossa → DRAFT vuoto esistente; non elimina `orders`.

Guard: order/item esistenti, ownership, solo `DRAFT`; `ACCEPTED` immutabile a repository/domain.

Fuori scope: editor `MODIFICA UNA` + cambio quantity (rifiuto conservativo corrente preservato); ORD-021 general note.

Test: ORDER-013..025.

### ORD-021 [P0] General note

Campo: `orders.generalNote` (schema esistente; nessuna migration se già presente). Distinto da `order_items.note`.

UI (New Order / DRAFT):
- sezione `NOTA ORDINE` dopo le righe ordine, prima di totale/azioni;
- campo multilinea;
- salvataggio esplicito `SALVA NOTA` (no autosave per carattere, no debounce);
- `SALVA NOTA` disabilitabile se editor == persistito;
- IME/`navigationBarsPadding`, touch `>= 48dp`; non sticky se non richiesto;
- preservare controlli ORD-020.

Semantica:
- blank/whitespace → `null`; testo non vuoto → trim esterno solo;
- nessun max length applicativo MVP;
- solo `DRAFT`; `ACCEPTED` immutabile a repository/domain;
- `updatedAt` via `ClockProvider`;
- editor dirty non sovrascritto da Flow non correlati;
- save failure preserva testo locale;
- indipendenza da item note;
- non influenza custom pizza highlight.

Fuori scope: ORD-022 total live; flussi ACCEPTED dedicati oltre l’immutabilità; stampa dedicata.

Test: ORDER-026..039.

### ORD-022 [P0] Total live from persisted state

Per `status == DRAFT`, totale live derivato **solo** dagli `order_items` persistiti in Room.

Formule:
```text
lineTotalCents = finalUnitPriceCents * quantity
orderTotalCents = sum(lineTotalCents)
```

`Money` / `Long` cents only; vietati `Double`/`Float`.

Source of truth:
- persisted `order_items` = unica SoT del live total DRAFT;
- **non** da editor dirty, form non salvati, catalogo live, menu corrente, valori temporanei Compose/ViewModel.

`orders.totalCents` sul DRAFT:
- **non** è la source of truth del totale live;
- ORD-022 **non** sincronizza `orders.totalCents` dopo ogni mutation (quick add, qty, remove, item edit, split);
- Acceptance futura salverà lo snapshot definitivo in `orders.totalCents` (fuori scope).

Live/reactive:
Room mutation → Flow/read model → ricalcolo totale derivato → UI.
Nessun optimistic total non persistito.

Reagisce a mutation persistite di righe/prezzi/qty (quick add, +/-, `RIMUOVI`, Addition/Removal/manual/reset/`MODIFICA TUTTE`/atomic split). Item note e `generalNote` non cambiano il totale. Removal ingrediente può riemettere senza abbassare il prezzo.

Pricing: nessuna nuova regola; usare solo `finalUnitPriceCents` + `quantity` già persistiti (`manual €0` valido → line total 0).

Empty DRAFT: mostra `TOTALE` `€0,00` (totale visibile; no riga fittizia; DRAFT non cancellato).

UI: footer/area finale `TOTALE` `€xx,xx` evidente, accessibile, insets; layout minimo necessario.

Overflow: checked/`Money`; `AmountOverflow` / equivalente; no wraparound/totale corrotto.

Fuori scope:
- `COMPLETA` / Acceptance / Preview / numerazione / write DRAFT di `orders.totalCents`;
- nuovi flussi ACCEPTED (useranno snapshot futuro).

ORD-022 chiude il blocco ORD di M5. Successivo: Demo/validation M5 secondo questo backlog (non inventare `ORD-023`).

Test: ORDER-040..058.

Demo M5:
- creare ordine complesso, kill app, riprendere identico.

## M6 — Preview, acceptance, numbering

Status milestone: **IN PROGRESS** — NUM-001..005 COMPLETE; ACCEPT-001 COMPLETE; ACCEPT-002 COMPLETE (satisfied by ACCEPT-001); **NEXT = ACCEPT-003**. Contract FREEZE-A + FREEZE-B COMPLETE.

### NUM-001 [P0] Sequential service/state — COMPLETE
Test NUM-T001..006 (+ NUM-T020/T021). DB-010 applied with NUM-001.

### NUM-002 [P0] Random code formatter — COMPLETE
Mapping indice ↔ `[A-Z][0-9][0-9]`; test NUM-T010, NUM-T017.

### NUM-003 [P0] Stable random permutation generator — COMPLETE
Contratto FREEZE-A: XorShift32 + Fisher–Yates bit-stable; first-need seed via `randomSeedInitialized`; test NUM-T016, NUM-T014, NUM-T018, NUM-T022..024.

### NUM-004 [P0] Random state/cycles — COMPLETE
2600 distinct, cycle 2, seed once; test NUM-T011..013, NUM-T018..019.

### NUM-005 [P0] Mode switch + Settings numbering control — COMPLETE
`SettingsScreen` owns NumberingMode UI; `app_settings.numberingMode`; tests NUM-T006, NUM-T015, NUM-T025..035 (T032/T033 deferred ACCEPT-003). Commit: `18b49b8`.

### ACCEPT-001 [P0] Preview screen category ordering — COMPLETE
`PIZZE`→`FRITTURA`→`BIBITE`; within `createdSequence ASC`; zero writes; derived total; ACCETTA visible+disabled. Test ACCEPT-T008..012, ACCEPT-T020. Commit: `d6e7ff9`.

### ACCEPT-002 [P0] Draft / preview actions (scope M6) — COMPLETE (satisfied by ACCEPT-001)
Ownership: M6 preview action surface (`COMPLETA` → preview with `[INDIETRO/ANNULLA]` + `[ACCETTA]` only; **no** `ACCETTA E STAMPA` / `STAMPA BOZZA`).
**No additional production or test work required** — delivered and verified under ACCEPT-001 (`d6e7ff9`). Coverage reused: ACCEPT-T008, ACCEPT-T009, ACCEPT-T020 (PASS).
`ACCETTA` remains **visible + disabled** until ACCEPT-003; number allocation = ZERO in ACCEPT-002.

### ACCEPT-003 [P0] AcceptOrder transaction — contract READY / NOT STARTED / NEXT
Owns: AcceptOrder use case; precondition revalidation; `numberingMode` read at accept-time; number allocation; `businessDate`/`acceptedAt`; checked total snapshot; single Room `@Transaction`; DRAFT→ACCEPTED; **ACCETTA enabled + click wiring**.
Dipende da NUM-001 (+ NUM-003 se mode RANDOM) — prerequisites SATISFIED. Test ACCEPT-T001, ACCEPT-T004..005, ACCEPT-T013..016, ACCEPT-T018.

### ACCEPT-004 [P0] Double-accept protection — contract READY
Test ACCEPT-T003, ACCEPT-T017; NUM-T005.

### ACCEPT-005 [P0] Accepted screen immutability — contract READY
Test ACCEPT-T007; SNAP-*.

### ACCEPT-006 [P0] Remain on Accepted after accept — contract READY
Test ACCEPT-T006, ACCEPT-T019.

### ACCEPT-007 [P0] Hide accept CTAs + M6 STAMPA disabled — contract READY
Test ACCEPT-T019.

Test: ACCEPT-T001..020 + NUM-T001..024.

Demo M6:
- ordine Accepted numerato e immutabile;
- preview non consuma numeri;
- STAMPA visibile disabled.

## M7 — Today / current-day Accepted detail / daily purge — COMPLETE

> **M7 COMPLETE** (HEAD `fcd614b`). Nessun archivio storico. Solo `ACCEPTED` della `currentBusinessDate`. Hard delete automatico dei giorni precedenti (RET-001). Duplication + conflict UX COMPLETE.

### ARCH-001 [P1] Today query/UI — COMPLETE / PRESERVED
businessDate corrente; `ACCEPTED` only; `acceptedAt DESC`.

### ARCH-002 [P1] Archive date filters — OBSOLETE
> **CANCELLED BY PRODUCT SCOPE CHANGE.** Niente IERI / selezione data.

### ARCH-003 [P1] Number search — OBSOLETE
> **CANCELLED BY PRODUCT SCOPE CHANGE.** Niente ricerca ordini cross-day.

### ARCH-004 [P1] Accepted detail — COMPLETE (D-045; edc3e9a)
Dettaglio read-only di un `ACCEPTED` della **sola** giornata corrente.
> Precedente significato “dettaglio archivio storico” superseded.

AC (congelati — D-045):
- entry: Today row tap → detail(`orderId`);
- guard: `ACCEPTED` + `businessDate = currentBusinessDate`; else Unavailable;
- observe order (purge mid-open → Unavailable, no stale);
- snapshot-only; ordering = `AcceptancePreviewOrdering` (PIZZE→FRITTURA→BIBITE, `createdSequence ASC`);
- CTA: `STAMPA` VISIBLE+DISABLED (PRINT = M8/M9); `INDIETRO`/System Back → Today; `HOME` → Home;
- in **ARCH-004 alone** non mostrare: `RISTAMPA`, `NUOVO ORDINE DA QUESTO` (CTA activated by ARCH-006 / D-046), edit controls, `ACCETTA`, `COMPLETA`.

Test: ARCH-T010..ARCH-T026.

### ARCH-005 [P1] Historical snapshot display — OBSOLETE
> **CANCELLED BY PRODUCT SCOPE CHANGE** come requisito di archivio storico.
> Gli snapshot restano obbligatori per immutabilità/ristampa degli Accepted **ancora presenti** (giornata corrente).

### ARCH-006 [P1] Duplicate transaction — COMPLETE (D-046; 67be68b)
Current-day ACCEPTED only from Accepted Order Detail → new independent DRAFT (faithful snapshot copy, no catalog reprice).
AC met: source guard; atomic txn; field map D-046; CTA VISIBLE+ENABLED; success → NewOrder; existing DRAFT → typed conflict + zero writes.
Schema: v2 unchanged / migration NONE.
Test: DUP-001..005, ARCH-T027..ARCH-T033 — PASS.

### ARCH-007 [P1] Draft conflict on duplicate — COMPLETE (D-047; contract 86baf12; impl fcd614b)
> Dipende da ARCH-006 COMPLETE. Docs freeze D-047 (`86baf12`). Implementation COMPLETE (`fcd614b`).

Delivered:
- conflict dialog (`C'È GIÀ UN ORDINE IN CORSO` + RIPRENDI / ELIMINA DRAFT E DUPLICA / ANNULLA);
- RIPRENDI → NewOrder(existingDraftId), zero writes (incl. empty persisted DRAFT);
- ANNULLA → stay on detail, zero writes;
- ELIMINA DRAFT E DUPLICA → ONE Room transaction replace (never dual-txn delete+duplicate);
- typed outcomes (`Success` / `SourceUnavailable` / `DraftMissing` / `DraftChanged` / `PersistenceFailure`);
- success nav → NewOrder(newDraftId);
- rollback preserves old DRAFT; source ACCEPTED immutable;
- ARCH-006 no-active-DRAFT path unchanged; D-046 duplication semantics preserved.

API: `ReplaceDraftWithAcceptedOrderDuplicate`.
Schema: v2 unchanged / migration NONE.
Test: ARCH7-T001..ARCH7-T015 — PASS (Manual ARCH-007 5/5; JVM 506; connected 160).

Out of scope (unchanged): printing; archive; merge DRAFT+duplicate; schema/migration; catalog reprice; multi-DRAFT.

### RET-001 [P0] Daily accepted-order purge — COMPLETE
Hard delete `ACCEPTED` where `businessDate < currentBusinessDate`.

AC:
- idempotente, transazionale, offline;
- indipendente da `numbering_state` (nessun reset/delete numbering);
- DRAFT preservato;
- delete esplicito `order_item_removals` poi `orders` (cascade items/additions);
- schema unchanged (DB v2), migration NONE;
- correttezza su app enter-in-use con `currentBusinessDate` aggiornata; nessun requirement di job esatto alle 05:00.

Test: RET-T001..RET-T008.

Demo M7 (COMPLETE):
- Ordini di oggi = solo giornata corrente;
- dopo cambio businessDate, Accepted precedenti non più presenti;
- dettaglio Accepted giornata corrente (ARCH-004);
- duplicazione + conflict UX (ARCH-006/007).

## M8 — Printing foundation — IN PROGRESS

> PRINT-001..007 **COMPLETE** (`cee8162` tip). M8 COMPLETE. BT-001 COMPLETE (`345dce6`). BT-002 COMPLETE (`d97cb49`). BT-003 COMPLETE (`e44c3e4` / D-057). BT-004 COMPLETE (`82cc98f` / D-058). BT-005 COMPLETE (`3aad082` / D-059). Next: **HW-001** (D-060 FROZEN — **READY FOR IMPLEMENTATION**).

### PRINT-001 [P0] Printer contracts/models — COMPLETE (D-048)
> Complete on `581f27d`. Pure contracts/models only.

### PRINT-002 [P0] PrintableDocument/ReceiptComposer — COMPLETE (D-049)
> Composition **contracts/models only** (parallel to PRINT-001). No full receipt text layout implementation, no ESC/POS bytes, no FakePrinter, no Bluetooth, no UI, no schema/migration.

**Depends on:** PRINT-001 COMPLETE (`581f27d`). Pipeline: `docs/04_ANDROID_ARCHITECTURE.md` §20; content rules normative in `docs/07_PRINTING_SPEC.md` §§4–15 (layout **implementation** owned by **PRINT-004**).

**Owns (AC):**
1. `PrintKind` (`DRAFT`, `FINAL`). Reprint uses the same FINAL document content; never emit label/content `RISTAMPA` (business rules / printing spec already frozen).
2. `PrintableDocument` pure-Kotlin model: intermediate **pre-EscPosEncoder** representation of one receipt/ticket (not `ByteArray`, not ESC/POS commands).
3. Minimal document shape frozen for encoder handoff later:
   - ordered `PrintableLine` values with `text: String` and optional emphasis hint for header sizing (`NORMAL` / `EMPHASIZED` only — semantic hint, not ESC/POS);
   - `kind: PrintKind` on the document.
4. `ReceiptComposer` interface only, pure Kotlin, e.g.:
   - `fun compose(order: Order, kind: PrintKind, pricePrintMode: PricePrintMode, charsPerLine: Int): PrintableDocument`
   - Inputs are domain `Order` snapshot + PRINT-001 `PricePrintMode` + `charsPerLine` (needed by printing spec §7 separator adaptation when formatter runs).
5. No Android / Room entity / Compose / Bluetooth / NETUM dependencies in these types.
6. Schema: **DB v2 unchanged**; migration **NONE**; no `print_jobs`.

**Explicitly deferred (NOT PRINT-002):**
- Full layout/formatting implementation (headers/sections/wrap/prices/notes/total text) → **PRINT-004** (owns PRINT-T001..T008, T010..T014 as implementation gates).
- `PricePrintMode` settings/UX → **PRINT-003**.
- EscPosEncoder / code page / cut commands → **PRINT-005**.
- FakePrinterDriver → **PRINT-006**.
- PrinterService Mutex / wiring → **PRINT-007**.
- Accept+print / STAMPA CTA enable / BT / NETUM → **M9** (PRINT-020+ / BT-*).
- Hardware calibration of `charsPerLine` / `codePage` / feed / cutter.
- Inventing definitive code pages, margins, density, MAC, pairing, vendor protocol.

**Tests:** small contract/unit coverage for `PrintKind` / `PrintableDocument` / `ReceiptComposer` compile stubs. Do **not** mark PRINT-T001..027 PASS in PRINT-002 alone.

**Demo:** none (contracts only).

### PRINT-003 [P0] PricePrintMode — COMPLETE (D-050)
> Preference **wiring/persistence** for `PricePrintMode` (enum already PRINT-001). No receipt rendering. No ESC/POS. No Bluetooth/NETUM. No Room schema change.

**Depends on:** PRINT-001 COMPLETE (`581f27d`); PRINT-002 COMPLETE (`c0ce4e7`). Normative: `docs/00_SOURCE_OF_TRUTH.md` (device/printer prefs → DataStore); `docs/04_ANDROID_ARCHITECTURE.md` §18; `docs/05_DATABASE_SCHEMA.md` §12; `docs/07_PRINTING_SPEC.md` §3/§11.

**Owns (AC):**
1. Single source of truth for `pricePrintMode`: **DataStore** printer/device preferences (same conceptual field as `PrinterProfile.pricePrintMode`).
2. Default persisted/read value: **`DETAILED`**.
3. Domain/data API to get / observe / update `PricePrintMode` (pure Kotlin domain contract + DataStore impl). Invalid stored value → typed failure / safe re-default policy must not silently invent modes beyond DETAILED/TOTAL_ONLY.
4. When a `PrinterProfile` is assembled for print, `pricePrintMode` comes from this DataStore SoT (no parallel Room `app_settings` copy).
5. Schema: **DB v2 unchanged**; migration **NONE**. Do **not** add `pricePrintMode` to Room `app_settings`.
6. No DETAILED/TOTAL_ONLY receipt layout implementation (owner **PRINT-004**).

**Ownership model (frozen):**
- **PROFILE preference** stored in DataStore with other printer prefs — not Room business settings, not session-only, not per-print dialog override in v1.

**UX (frozen from docs):**
- Public UI is **not mandatory** in v1 (`docs/07_PRINTING_SPEC.md` §11: may be printer/developer preference).
- PRINT-003 does **not** require a new Compose screen.
- Changing mode from draft/accepted detail/print dialog is **out of scope**.
- Full printer settings UI remains **M9 / BT-006**.
- If a Settings radio is added later, Italian user-facing labels are **OPEN** (do not invent in PRINT-003).

**Explicitly deferred:**
- Receipt price rendering DETAILED/TOTAL_ONLY → **PRINT-004** (PRINT-T012 etc.).
- EscPos / Fake / Mutex / STAMPA enable / Bluetooth → PRINT-005..007 / M9.

**Tests:** unit tests for DataStore default DETAILED, persist/load TOTAL_ONLY, reject/handle invalid values as specified. Do **not** mark PRINT-T001..027 PASS (T012 remains PRINT-004).

**Demo:** none required (preference wiring).

### PRINT-004 [P0] Formatter draft/final — COMPLETE (D-051)
> Complete on `ba2c8e8`. `DefaultReceiptComposer` + PRINT-T001..T008, T010..T014.

### PRINT-005 [P0] ESC/POS encoder — COMPLETE (D-052)
> Complete on `cab2c1a`. `DefaultEscPosEncoder` + EncodeResult/EncodeError + byte-for-byte unit tests.

### PRINT-006 [P0] FakePrinterDriver — COMPLETE (D-053)
> Complete on `0a98b0f`. Pure-Kotlin `FakePrinterDriver` + PRINT-T026.

### PRINT-007 [P0] PrinterService + Mutex — COMPLETE (D-054)
> Complete on `cee8162`. `DefaultPrinterService` + `PrinterProfileProvider` abstraction + Q6b.
## M9 — Bluetooth NETUM — IN PROGRESS

> M8 COMPLETE. BT-001..007 COMPLETE. HW-001 COMPLETE (`3d05c91` / D-060 + D-061). **BT-007 COMPLETE**. M9 **13/14**. **PRINT-020 COMPLETE** (**D-064**). **PRINT-021 COMPLETE** (**D-068**). **PRINT-022 COMPLETE** (**D-069**). **PRINT-023 COMPLETE** (**D-070**). **PRINT-024 COMPLETE** (**D-071** — ConnectionLost uncertain microcopy). **D-065** + **D-066** COMPLETE (`2ac8233`). **D-067 COMPLETE** (`f7dad97`). Remaining formal: **HW-002 ONLY**. Do not start HW-002 until authorized.

### BT-001 [P1] Runtime permission manager — COMPLETE (`345dce6` / D-055)
> Android-facing Bluetooth runtime permission evaluation for **bonded-only** MVP. No discovery, SCAN, location, RFCOMM, NETUM, PrinterService, or printer UI.

**Depends on:** M8 COMPLETE (`cee8162`). Contract: **D-055**.

**Owns (AC):** see **D-055**. Summary:
1. `BluetoothPermissionManager` (or equivalent) — granted / required / denied evaluation + permission list for UI request.
2. API 31+: runtime `BLUETOOTH_CONNECT`; API <31: no CONNECT runtime prompt; legacy manifest `BLUETOOTH` maxSdk 30.
3. No `BLUETOOTH_SCAN`; no location permissions; discovery out of scope.
4. No Activity/Compose in domain; no PrinterService changes; `PermissionDenied` mapping later via driver/service.
5. Adapter on/off / `BluetoothDisabled` / full PRINT-T021 → BT-004/005.
6. Schema v2 unchanged; migration NONE.

**Owned tests:** unit permission/version matrix in D-055 (permission path only).
**Not owned:** PRINT-T021 full (Bluetooth disabled); bonded list; hardware.

**Blocking open questions:** NONE.

**Demo:** none (permissions/unit). Hardware: **NO**; Samsung: **NO**; NETUM: **NO**.

### BT-002 [P1] List bonded devices — COMPLETE (`d97cb49` / D-056)
> Read Android bonded/paired devices into an app-safe model; `BondedDevicesResult` Success/Failure; bonded-only. No discovery, SCAN, location, persistence, RFCOMM, NETUM, PrinterService, or settings UI.

**Depends on:** BT-001 COMPLETE (`345dce6`). Contract: **D-056**.

**Owns (AC):** see **D-056**. Summary:
1. `BondedBluetoothDevicesProvider` (or equivalent) — bondedDevices only; maps to `BondedBluetoothDevice(id=address, name nullable)`.
2. Uses `BluetoothPermissionManager` directly; missing CONNECT (API 31+) → `Failure(PermissionDenied)`; `SecurityException` → `Failure(PermissionDenied)`.
3. `adapter == null` → `Failure(BluetoothUnavailable)` (BT-002 local error; **not** `PrinterError`).
4. Empty bonded set (adapter present, permission OK) → `Success(emptyList)`. Duplicate names retained (distinct ids).
5. Ordering: named first; name case-insensitive ASC (locale-independent); ties by id ASC; null/blank names after named, then id ASC. No UI placeholder strings (BT-006).
6. `BluetoothDisabled` / `adapter.isEnabled` → **not** owned (BT-004/005). No `PrinterService` / `PrintResult` / `PrinterError` for listing.
7. Schema v2 unchanged; migration NONE.

**Blocking open questions:** NONE.

**Owned tests:** permission granted → list; permission denied → PermissionDenied; SecurityException → PermissionDenied; adapter null → BluetoothUnavailable; zero bonded → Success(empty); multiples + duplicate names by id; Q1 ordering + locale-independence; no discovery; **no** `isEnabled` tests.

**Manual:** PHONE ONLY androidTest PASS (`BondedBluetoothDevicesAndroidTest`). NETUM: **NO**. Samsung: **NO**.

**Not owned:** persistence (BT-003); RFCOMM/BluetoothDisabled/`isEnabled` (BT-004/005); settings UI / name fallback (BT-006); STAMPA; NETUM.

### BT-003 [P1] Persist selected printer — COMPLETE (`e44c3e4` / D-057)
> Persist / read / observe / clear selected bonded printer **identity** in existing `printer_preferences` DataStore. No Bluetooth stack, RFCOMM, UI, NETUM profile, or concrete PrinterProfileProvider.

**Depends on:** BT-002 COMPLETE (`d97cb49`); PRINT-003 DataStore (`printer_preferences` / D-050). Contract: **D-057**.

**Owns (AC):** see **D-057**. Summary:
1. Reuse `printer_preferences`; key `selected_printer_id` = `BondedBluetoothDevice.id` (address). No name persistence in BT-003.
2. Extend `PrinterSettingsRepository` / DataStore impl: get / observe / set / clear → `String?` (`null` = unconfigured).
3. Blank/whitespace id rejected; same-id set idempotent; no bonded-stack validation on set; stale selection preserved after external unpair.
4. Coexist with `PricePrintMode` (default DETAILED unchanged). Identity only — not physical profile / PrinterProfileProvider.
5. Schema Room v2 unchanged; migration NONE.

**Blocking open questions:** NONE.

**Owned tests:** JVM DataStore (PRINT-003 patterns) — default null; set/get; replace; clear; blank reject; coexistence with PricePrintMode; survive repository recreation.

**Manual:** **NO**. Hardware: **NO**. Samsung: **NO**. NETUM: **NO**.

**Not owned:** bonded listing; RFCOMM/BluetoothDisabled (BT-004/005); settings UI (BT-006); NETUM/HW profile; PrinterService.

### BT-004 [P1] RFCOMM/SPP driver — COMPLETE (`82cc98f` / D-058)
> Real Bluetooth Classic RFCOMM/SPP `PrinterDriver` transport: resolve bonded device from `PrinterProfile.id`, **secure** connect via `createRfcommSocketToServiceRecord(SPP_UUID)`, write encoded bytes, disconnect/cleanup. Bonded-only; reuse BT-001 permissions; own `BluetoothDisabled` gate.

**Depends on:** BT-003 COMPLETE (`e44c3e4`). Contract: **D-058 FROZEN**.

**Owns (AC):** see **D-058**. Summary:
1. SPP UUID: `00001101-0000-1000-8000-00805F9B34FB`.
2. **Q1-A SECURE only** — `createRfcommSocketToServiceRecord`; **no** insecure API; **no** secure→insecure automatic fallback.
3. Address from `PrinterProfile.id` (driver does not read DataStore); not bonded → `ConnectionFailed`; blank id → `PrinterNotConfigured`.
4. PermissionDenied via BT-001; BluetoothDisabled for disabled + null adapter.
5. Fake-aligned connect/print/disconnect semantics; flush after write; IO dispatcher; no driver Mutex.
6. Manual **after implementation**: **PHONE + NETUM** minimal secure RFCOMM proof (not HW-001/HW-002). Secure choice = NEEDS HARDWARE VALIDATION; insecure requires explicit contract revision if incompatible.

**Blocking open questions:** NONE.

**Not owned:** timeout enforcement / uncertain ConnectionLost UX / retry/reconnect (BT-005); settings/testPrint UI; concrete NETUM `PrinterProfileProvider` defaults; discovery/SCAN/location; Room.

### BT-005 [P1] Timeout/disconnect/error mapping — COMPLETE (`3aad082` / D-059)
> Hardened connect timeout, connection-loss/uncertain outcome mapping, reconnect/retry **policy** on top of BT-004 transport.

**Status:** **COMPLETE** (`3aad082`). Contract: **D-059 FROZEN**.
**Depends on:** BT-004 COMPLETE (`82cc98f`).

**Owns (AC) — D-059 FROZEN:**
1. Connect timeout default **10_000 ms**, injected at driver/transport level (not PrinterProfile / DataStore / Room); map to `PrinterError.Timeout`.
2. Mechanism: timeout + **close attempt socket** to interrupt blocking `BluetoothSocket.connect()`; timeout-won flag preserves `Timeout` over close-induced `IOException`.
3. **Q2-A connect-only** — no explicit write/flush timeout in MVP.
4. Write/flush `IOException` after CONNECTED → `ConnectionLost` + DISCONNECTED (uncertain physical outcome §20); microcopy → PRINT-024 (not BT-005 UI).
5. Cleanup precedence: primary typed failure preserved; close errors secondary.
6. **NO** automatic print retry; **NO** automatic reconnect algorithm; next explicit job/RIPROVA = fresh connect via D-054 lifecycle.
7. `CancellationException`: controlled timeout → `Timeout`; other cancel → rethrow; service may only add minimal cancel rethrow (no Mutex/lifecycle redesign).
8. Unit tests deterministic (fake gateway + injectable/virtual time); no real 10 s sleeps; no hardware for unit timeout.
9. Manual after impl: PHONE + NETUM (normal print; power-off / BT-off observation). False connect timeouts on normal NETUM → revise D-059 explicitly (no runtime auto-adapt).
10. After BT-005 COMPLETE → **HW-001 NEXT** (before BT-006).

**Blocking open questions:** NONE.

**Not owned:** write/flush timeout; PRINT-024 microcopy; PRINT-023 operator RIPROVA UX; settings UI; discovery/SCAN; insecure RFCOMM; HW-001 calibration; Room.

### BT-006 [P1] Printer settings UI — COMPLETE (D-062)
> Cashier Impostazioni → Stampante: bonded selection, permission UX, PricePrintMode UI, concrete `PrinterProfileProvider` wiring (D-061). No discovery. No test print execution.

**Status:** **COMPLETE**. Contract: **D-062 FROZEN**.
**Depends on:** BT-001..005 COMPLETE; HW-001 COMPLETE (`3d05c91` / D-061); PRINT-003; PRINT-007 abstraction.

**Owns (AC) — D-062 FROZEN:**
1. Stampante section on existing `SettingsScreen` (Home → IMPOSTAZIONI); no new NavHost destination.
2. Permission request UX (BT-001 APIs + requestability / app-settings CTA); bonded-only list (BT-002); select/clear via `PrinterSettingsRepository` (BT-003).
3. Stale selection visible; no auto-clear; clear CTA **Nessuna stampante**.
4. Device label = name or **Dispositivo Bluetooth**; secondary address line always.
5. PricePrintMode radios: **Prezzi dettagliati** / **Solo totale**; immediate persist.
6. Concrete production `PrinterProfileProvider` + Hilt: selected id + D-061 physical + DataStore pricePrintMode; never hardcode real MAC.
7. CTA open Android Bluetooth settings for pairing / adapter off; open app settings when permission not requestable; ON_RESUME refresh.
8. JVM ViewModel (+ provider) tests; manual PHONE validation.

**Evidence:**
- Code review: Critical 0 / Major 0
- Focused JVM: **38 PASS**
- `assembleDebug` / `assembleDebugAndroidTest`: PASS
- Samsung SM-S931B manual tests 1–8 PASS (selection/clear/PricePrintMode persist; BT OFF/ON resume; permission revoke/non-requestable → app settings recovery; stale selection; reassociation via ON_RESUME)
- Schema v2 unchanged; migration NONE

**Blocking open questions:** NONE.

**Not owned:** `STAMPA DI PROVA` / `testPrint` invoke (**BT-007**); discovery/SCAN/pairing; RFCOMM/timeout; receipt styling; physical profile editor; Room/migrations.

### BT-007 [P1] Test print UI — COMPLETE (D-063)
> Adds **`STAMPA DI PROVA`** on Impostazioni → Stampante and invokes existing `PrinterService.testPrint()`. Depends on BT-006 provider wiring (**COMPLETE**).

**Status:** **COMPLETE**. Contract: **D-063 FROZEN**.
**Depends on:** BT-006 COMPLETE (`8b41233`); PRINT-007 `testPrint` + Mutex; concrete `PrinterProfileProvider` (BT-006); BT-004/005 driver.

**Owns (AC) — D-063 FROZEN:**
1. Button **STAMPA DI PROVA** on existing Stampante section (no new NavHost destination).
2. Enabled only when printer settings `Ready` + non-null selected id + **not** stale + not already printing.
3. One explicit tap → one `PrinterService.testPrint()` job; profile via `PrinterProfileProvider`; no direct `PrinterDriver` from UI.
4. Reuse `DefaultPrinterService.testPrintDocument()` unchanged (`TEST STAMPANTE` / `Cassa` / accents+€); no Order/Room/numbering.
5. Success feedback: transient **Test stampa inviato** (transport job completed — do not claim absolute paper certainty beyond service Success).
6. Map `PrinterError` to Italian recoverable UI; **no** auto-retry/reconnect; manual re-tap allowed when idle/enabled.
7. Minimal Hilt wiring for production `PrinterService` graph if still unbound (driver + encoder + composer + service).
8. JVM ViewModel tests A–N; Compose enabled/disabled/progress if existing pattern fits; Samsung+NETUM manual after review.

**Evidence:**
- D-063 FROZEN; code review PASS — Critical 0 / Major 0 (3 Minors accepted for M9)
- Focused JVM: **PrinterSettingsViewModelTest 43 PASS**
- `assembleDebug` / `assembleDebugAndroidTest`: PASS
- Manual Samsung SM-S931B + paired NETUM: one explicit tap → **one** physical print; `TEST STAMPANTE` / accents / € PASS; no crash; no auto-retry; no Order/Room mutation
- Receipt styling refinements: **DEFERRED POST-M9**
- Schema v2 unchanged; migration NONE

**Blocking open questions:** NONE.

**Not owned:** PRINT-020..024; HW-002; receipt typography / DOUBLE_BOTH application; discovery/pairing; transport/timeout changes; physical profile editor; Room/migrations; BT-006 permission-attempt persistence Minor.

### PRINT-020 [P1] PrintDraft integration — COMPLETE (D-064)
> Cashier **Acceptance Preview → `STAMPA BOZZA`** invokes existing `PrinterService.printDraft(orderId)`. Read-only vs order/business persistence.

**Status:** **COMPLETE**. Contract: **D-064 FROZEN**. Code review PASS (Critical 0 / Major 0 / Minor 0). Focused ViewModel tests 32 PASS. `assembleDebug` / `assembleDebugAndroidTest` PASS. Samsung+NETUM actual hardware draft print **PASS** (after D-065/D-066/D-067).
**Depends on:** BT-007 COMPLETE (`b026dbb`); PRINT-007 `printDraft` + Mutex (D-054); BT-006 `PrinterProfileProvider`. Base scale: **D-065**. DRAFT TOTALE: **D-066**. Layout width: **D-067**.

**Owns (AC) — D-064 FROZEN:**
1. Button **`STAMPA BOZZA`** on existing Acceptance Preview action area (with ANNULLA/ACCETTA); no new NavHost destination.
2. Enabled only for `AcceptancePreviewUiState.Ready` + not accepting + not already printing.
3. One explicit tap → one `printDraft(draftId)`; profile via `PrinterProfileProvider`; no direct `PrinterDriver` from UI.
4. Header **`BOZZA`** / no `displayNumber`; draft remains DRAFT; no numbering mutation.
5. Success: transient **Bozza inviata alla stampante**; map `PrinterError` per D-064; no auto-retry; no PRINT-024 accepted uncertainty copy.
6. Orthogonal print phase on `AcceptancePreviewViewModel`.
7. JVM ViewModel tests A–W (D-064); Samsung+NETUM manual **PASS**.

**Hardware closure evidence:** one tap → one physical print; BOZZA; DOUBLE_BOTH 2×; effective width 21; FRITTURA/separators single physical line; products/prices readable; printed DRAFT total **22,50**; status remains DRAFT; no automatic acceptance; no `displayNumber` allocation; no crash.

**Blocking open questions:** NONE.

**Not owned:** PRINT-021..024; HW-002; `ACCETTA E STAMPA`; NewOrder editor print; discovery/pairing; transport; schema/migrations.

### D-065 [P0] M9 business receipt base text scale — COMPLETE (`2ac8233`)
> Production business receipts (`DefaultReceiptComposer`) use `PrintableLine.textScale = DOUBLE_BOTH`. Not a `PrinterProfile` field. `charsPerLine=42` unchanged.

**Status:** **COMPLETE** on `2ac8233`.

### D-066 [P0] Draft printable total source — COMPLETE (`2ac8233`)
> DRAFT `TOTALE` = `CalculateOrderTotal.fromPersistedItems(order.items).orderTotal`. FINAL keeps `order.total`. No Room write.

**Status:** **COMPLETE** on `2ac8233`.

### D-067 [P0] Scale-aware receipt layout width — COMPLETE (`f7dad97`)
> Layout width = `max(1, floor(charsPerLine / horizontalScaleMultiplier(textScale)))`. Business DOUBLE_BOTH + profile 42 → **21**. Reuse existing `ReceiptTextLayout` wrap/price helpers. Do not change profile `charsPerLine`.

**Status:** **COMPLETE** on `f7dad97`. Contract: **D-067 FROZEN**. Hardware paper retest **PASS** (with PRINT-020).
**Depends on:** D-065; D-060; `ReceiptTextLayout` / `DefaultReceiptComposer`.

**Owns:** scale-aware layoutWidth helpers; composer uses DOUBLE_BOTH width before wrap; separators/headers/rows fit; tests A–Q; PRINT-020 paper retest.

**Not owned:** PrinterProfile edits; encoder wrap; typography hierarchy; PRINT-021+; feed/spacing polish.

### PRINT-021 [P1] PrintAccepted integration — COMPLETE (D-068)
> Enable existing **`STAMPA`** on current-day ACCEPTED surfaces to invoke `PrinterService.printAccepted(orderId)`. Business read-only reprint / first manual final print of an already-accepted order. Never label `RISTAMPA`.

**Status:** **COMPLETE**. Contract: **D-068 FROZEN**. Code review PASS (Critical 0 / Major 0 / Minor 1 accepted). Focused ViewModel tests PASS (AcceptedOrderDetail 33; AcceptancePreview 40). UI androidTest PASS. `assembleDebug` / `assembleDebugAndroidTest` PASS. Samsung+NETUM actual hardware FINAL print **PASS**.
**Depends on:** PRINT-007 `printAccepted` + Mutex (D-054 COMPLETE); BT-006/007; PRINT-020 COMPLETE; D-065/D-066/D-067 COMPLETE; ARCH-004 Accepted Order Detail + post-accept Accepted CTAs.

**Owns (AC) — D-068 FROZEN:**
1. Enable existing **`STAMPA`** on **Accepted Order Detail** (primary) and **post-accept Accepted** screen (Acceptance Preview Accepted state); no new NavHost destination; label stays `STAMPA`.
2. Enabled only when UI shows a valid current-day ACCEPTED order and not already printing / not busy with duplicate-replace.
3. One explicit tap → one `printAccepted(orderId)` with the exact accepted order id; no direct `PrinterDriver`.
4. `PrintKind.FINAL`: frozen `displayNumber` header (not BOZZA); frozen `order.total`; frozen snapshots; D-065/D-067 via existing composer path.
5. Success: transient **Ordine inviato alla stampante**; map `PrinterError` consistently with PRINT-020 (accepted wording); no auto-retry; no PRINT-024 uncertainty copy.
6. Orthogonal accepted-print phase on owning ViewModels (observation must not wipe PRINTING incorrectly).
7. Focused JVM ViewModel tests A–O (D-068); Samsung+NETUM manual **PASS**.

**Hardware closure evidence:** one explicit STAMPA → one physical FINAL receipt; BOZZA absent; displayNumber **001**; products/prices readable (4 formaggi 14,00 + 4 stagioni 14,00); TOTALE **28,00**; DOUBLE_BOTH 2×; scale-aware wrap; separators OK; no crash; no business/numbering mutation; no automatic accept/print/retry.

**Blocking open questions:** NONE.

**Not owned:** PRINT-022 (`ACCETTA E STAMPA` / auto-print after accept commit); PRINT-023 dedicated retry workflow; PRINT-024 uncertain microcopy; receipt typography/layout; PrinterProfile; Bluetooth/transport; Room/schema/migrations; historical order access.

### PRINT-022 [P1] Accept and print after commit — COMPLETE (D-069)
> After successful explicit **`ACCETTA`**, once the AcceptOrder Room transaction has committed, invoke exactly one `PrinterService.printAccepted(exactAcceptedOrderId)`. Business acceptance remains authoritative if print fails.

**Status:** **COMPLETE**. Contract: **D-069 FROZEN**. Code review PASS (Critical 0 / Major 0 / Minor 1 accepted). Focused ViewModel tests **53 PASS**. Connected Samsung UI `AcceptancePreviewScreenTest` **11 PASS**. `assembleDebug` / `assembleDebugAndroidTest` PASS. Samsung+NETUM success-path hardware **PASS**. Bluetooth-disabled failure-path hardware **PASS**.
**Depends on:** ACCEPT-003+ AcceptOrder COMPLETE; PRINT-007 `printAccepted` + Mutex (D-054); PRINT-021 COMPLETE (AcceptedPrintUiState / manual STAMPA); D-065/D-066/D-067 COMPLETE.

**Owns (AC) — D-069 FROZEN:**
1. Entry = existing Acceptance Preview **`ACCETTA`** (Ready state). No new NavHost destination. No separate `ACCETTA E STAMPA` CTA required in this task (PRD capability = ACCETTA → commit → one auto FINAL print).
2. Ordering: AcceptOrder succeeds and returns `AcceptOrderResult.Accepted` **before** any `printAccepted`. Never print inside Room transaction / against DRAFT / before `displayNumber` frozen.
3. Exact id: `printAccepted(result.orderId)` from `AcceptOrderResult.Accepted` (same DRAFT id promoted). Not from displayNumber / sourceOrderId / UI text / broad query.
4. One successful ACCETTA → at most one automatic `printAccepted`. Command-path one-shot; **no** observer/`LaunchedEffect` auto-print from `UiState.Accepted`.
5. No printer precheck before ACCETTA. Acceptance allowed without printer; print attempted only after commit.
6. Print failure: order stays ACCEPTED; no rollback / renumber / re-accept; feedback `"Ordine accettato. " +` PRINT-021 mapped printer error (no PRINT-023 RIPROVA CTA; no PRINT-024 uncertainty copy).
7. Print success: reuse PRINT-021 transient **`Ordine inviato alla stampante`**; screen remains normal Accepted.
8. Reuse `acceptedPrintJob` / `AcceptedPrintUiState` so manual PRINT-021 `STAMPA` cannot overlap while auto-print is in-flight; after Idle, manual STAMPA remains available.
9. Focused JVM tests A–T (D-069); Samsung+NETUM hardware **PASS**.

**Hardware closure evidence:**
- Success-path: one ACCETTA → ACCEPTED → exactly one automatic FINAL receipt; no BOZZA; products/prices/total readable; DOUBLE_BOTH / scale-aware; no duplicate auto-print; no crash; no manual STAMPA required.
- Failure-path: Bluetooth disabled before ACCETTA → acceptance still succeeds; print failure reported; order remains ACCEPTED; visible in Ordini di oggi; displayNumber assigned/preserved; no rollback / renumber / retry.

**Blocking open questions:** NONE.

**Not owned:** PRINT-023 dedicated retry workflow / RIPROVA CTA; PRINT-024 uncertain microcopy; receipt typography/layout; PrinterService/composer/profile changes; Bluetooth/transport; Room/schema/migrations; AcceptedOrderDetail auto-print.

### PRINT-023 [P1] Retry same order — COMPLETE (D-070)
> Explicit user retry of printing the **same** already-ACCEPTED order after a prior print attempt completed/failed. Uses ordinary **`STAMPA`** → `PrinterService.printAccepted(sameOrderId)`. No new CTA, no `RISTAMPA`, no persisted retry state, no automatic retry.

**Status:** **COMPLETE**. Contract: **D-070 FROZEN**. Classification: **ALREADY FUNCTIONALLY SATISFIED** (no production change). Focused ViewModel tests: AcceptedOrderDetail **38 PASS** (5 PRINT-023); AcceptancePreview **59 PASS** (6 PRINT-023). `assembleDebug` / `assembleDebugAndroidTest` PASS. Samsung+NETUM hardware retry **PASS**.
**Depends on:** PRINT-021 COMPLETE (`STAMPA` / `acceptedPrintJob` / `AcceptedPrintUiState`); PRINT-022 COMPLETE (post-accept auto failure leaves Accepted + manual `STAMPA` available).

**Owns (AC) — D-070 FROZEN:**
1. Retry = new explicit **`STAMPA`** after previous accepted-print job finished (Error/Success/Idle), on same Accepted Order Detail or AcceptancePreview Accepted surface.
2. Call `printAccepted(exactSameAcceptedOrderId)` — same frozen `displayNumber` / snapshots / `order.total`; never AcceptOrder / renumber / duplicate.
3. Blocked while `acceptedPrintJob` active / `Printing`; available again after completion.
4. Label remains **`STAMPA`** (never `RISTAMPA` / no dedicated `RIPROVA STAMPA` print CTA). Historical UX §12 `[RIPROVA]` dialog superseded for PRINT-023 by STAMPA re-tap + transient error feedback.
5. No persisted failed-print job; after navigation/restart, ordinary current-day Accepted `STAMPA` is sufficient.
6. ConnectionLost / uncertain physical-outcome microcopy = **PRINT-024** (out of scope).
7. Focused regression tests A–Q (D-070); definite-failure hardware (Bluetooth OFF → ON → STAMPA retry) **PASS**.

**Hardware closure evidence:**
- Existing ACCEPTED → Bluetooth OFF → STAMPA fails (definite) → order remains ACCEPTED → Bluetooth ON → explicit STAMPA on SAME order → exactly one FINAL receipt; same number/business data; no BOZZA; no new order/number; no automatic extra print; no crash.

**Blocking open questions:** NONE.

**Not owned:** new production UI/CTA; PRINT-024 uncertainty copy; automatic retry; Room writes; PrinterService/composer changes; HW-002.

### PRINT-024 [P1] uncertain outcome microcopy — COMPLETE (D-071)
> Operator microcopy when physical print outcome is uncertain after `PrinterError.ConnectionLost` (D-059 write/flush loss). No transport changes. No automatic retry.

**Status:** **COMPLETE**. Contract: **D-071 FROZEN**. Code review PASS (Critical 0 / Major 0 / Minor 1 accepted). Production = ViewModel ConnectionLost string mapping only. Focused ViewModel tests: AcceptancePreview **63 PASS**; AcceptedOrderDetail **40 PASS**. `assembleDebug` / `assembleDebugAndroidTest` PASS. Connected UI NOT RUN (no ADB). Real ConnectionLost hardware fault injection **NOT REQUIRED**.
**Depends on:** BT-005 / D-059 COMPLETE; PRINT-020..023 COMPLETE.

**Owns (AC) — D-071 FROZEN — delivered:**
1. Only `ConnectionLost` → uncertain microcopy; definite pre-print/encoder errors unchanged.
2. DRAFT / manual ACCEPTED: `Stampa non confermata. Controlla lo scontrino prima di stampare di nuovo.`
3. PRINT-022 auto: `Ordine accettato. Stampa non confermata. Controlla lo scontrino prima di stampare di nuovo.`
4. Reuse existing transient Error feedback + 3s consume; no dialog / special CTA; PRINT-023 explicit STAMPA preserved.
5. No new `PrinterError`; no PrinterService/Driver/Bluetooth/Room changes; BT-007 test-print wording out of scope.
6. Focused tests A–Q PASS; real ConnectionLost hardware fault injection **NOT REQUIRED**.

**Blocking open questions:** NONE.

**Not owned:** HW-002; auto-retry; transport policy; print queue/ACK; new error types.

### HW-001 [P1] NETUM calibration spike — COMPLETE (D-060 + D-061)
> Physical NETUM profile calibration + generic ESC/POS text formatting capability validation via synthetic calibration sheet. No business receipt redesign.

**Status:** **COMPLETE** — **READY TO COMMIT**. Capability contract: **D-060 FROZEN**. Physical profile: **D-061 FROZEN**.
**Depends on:** BT-005 COMPLETE (`3aad082`).

**Owns (AC) — delivered:**
1. `PrintableLine` alignment + textScale; `PrinterProfile.escPosCodeTable`; encoder `ESC a` / `GS !` ≤2× / optional `ESC t` + final reset.
2. Synthetic androidTest calibration harness (`EscPosCalibrationHardwareTest`) with one section per run.
3. Hardware evidence + M9 NETUM freeze (**D-061**): `charsPerLine=42`, `codePage=IBM00858`, `escPosCodeTable=19`, `feedLines=3`, `supportsCut=false`, `cutCommandVariant=null`.
4. M9 preferred base text scale **DOUBLE_BOTH** recorded separately (not a profile field); detailed receipt styling deferred post-M9.

**Blocking open questions:** NONE.

**Not owned:** production receipt redesign; BT-006/007 UI; discovery/retry/reconnect; Room/migrations; HW-002; feed fine-tuning / visual hierarchy post-M9.

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
