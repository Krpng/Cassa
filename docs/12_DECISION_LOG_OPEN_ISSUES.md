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
> Duplicate from current-day Accepted: **ACTIVE** under D-046 (ARCH-006/007). Source ACCEPTED remains immutable.

### D-019 Duplicate — SUPERSEDED by D-046 (2026-09-15)
> **CLOSED / SUPERSEDED.** Historical M7 note marked duplication PENDING. Product has now required current-day `NUOVO ORDINE DA QUESTO`. Active contract = **D-046**. Do not use D-019 as blocking status.

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
- ARCH-001 PRESERVED; ARCH-002/003/005 OBSOLETE; ARCH-004 = dettaglio giornata corrente; ARCH-006/007 product scope later resolved by **D-046** (was PENDING at D-044 freeze);
- Home: `ORDINI DI OGGI` preserved; concetto `ARCHIVIO` removed from product scope;
- schema DB v2 unchanged / migration NONE for RET-001 MVP (explicit removals delete, then orders);
- correttezza purge su app enter-in-use; esecuzione esatta 05:00 Android non richiesta.

### D-045 ARCH-004 CTA / navigation contract freeze (2026-09-15)
Congelato per dettaglio Accepted current-day (ARCH-004):
- read-only; guard `ACCEPTED` + `businessDate = currentBusinessDate`; observe → Unavailable se missing/DRAFT/old/purged (no crash, no stale);
- ordering = stesso preview Acceptance (`AcceptancePreviewOrdering`: PIZZE → FRITTURA → BIBITE; `createdSequence ASC`);
- label stampa UI = **`STAMPA`** (mai `RISTAMPA`); in ARCH-004 `STAMPA` = VISIBLE + DISABLED fino a PRINT (M8/M9);
- navigation: Today tap → detail; `INDIETRO`/System Back → Today; `HOME` → Home (no reopen preview/edit);
- `NUOVO ORDINE DA QUESTO` = NOT VISIBLE **in ARCH-004 alone** (activated by ARCH-006 / D-046; conflitto DRAFT = ARCH-007 / D-047);
- test dedicati ARCH-T010..ARCH-T026 (non riusare ARCH-T001..003).

### D-046 ARCH-006/007 current-day duplication contract freeze (2026-09-15)
Product decision: `NUOVO ORDINE DA QUESTO` = **REQUIRED** for current-business-day ACCEPTED from Accepted Order Detail.

**Principle:** DUPLICAZIONE FEDELE MA INDIPENDENTE — nuovo DRAFT, nuovi UUID, contenuto/personalizzazioni/prezzi = snapshot origine; no catalog reread; no reprice; source ACCEPTED unchanged.

**ARCH-006 = COMPLETE** (67be68b; transaction + CTA + success nav; conflict = typed reject only until ARCH-007 UX).
**ARCH-007 = COMPLETE** (contract D-047 / `86baf12`; implementation `fcd614b`).

Frozen field map:
- `productId` / `additionId` / `ingredientId` = **COPY** exact source (null stays null; no name lookup / catalog resolve);
- product/item snapshots, qty, base/auto extras/manual/final unit prices, item note, additions/removals snapshots + `displayOrder` = **COPY**;
- `manualUnitPriceCents = 0` is valid manual and **must remain 0** (not null);
- `generalNote` = **COPY** (null stays null; no extra trim on duplicate);
- `createdSequence` = **COPY exact** (no renumber; gaps allowed; later DRAFT adds use next available);
- DRAFT `orders.totalCents` = **normal DRAFT / ORD-022** live derived from persisted items — **do not** copy ACCEPTED `totalCents` as authoritative;
- Accept later: AcceptOrder checked total as usual (no catalog reprice).

Identity:
- new `order.id` / item / child UUIDs; `status=DRAFT`; `draftSlot=1`; `displayNumber`/`acceptedAt`/`businessDate` = null;
- `sourceOrderId` = immediate source ACCEPTED id;
- timestamps via `ClockProvider`.

Source guard: `ACCEPTED` AND `businessDate == currentBusinessDate` (`ClockProvider` + `SettingsRepository` + `BusinessDateCalculator`). Else reject + zero writes.

Atomicity: one Room transaction (validate source/date → no active DRAFT → create DRAFT → insert items/additions/removals). Failure → full rollback.

Existing active DRAFT (ARCH-006 without ARCH-007 UX): typed conflict + zero writes; no delete/replace/merge in ARCH-006.
With ARCH-007 (D-047): same typed conflict from repository becomes conflict **dialog** (RIPRENDI / ELIMINA DRAFT E DUPLICA / ANNULLA); replace = dedicated ONE Room transaction.

CTA (valid current-day detail): `NUOVO ORDINE DA QUESTO` VISIBLE+ENABLED; `STAMPA` remains VISIBLE+DISABLED.

Success (no active DRAFT): open new DRAFT in standard NewOrder UI (not stay on detail; not Home).

Schema: DB v2 unchanged; `sourceOrderId` already present; migration NONE.

Tests: DUP-001..005 PASS (ARCH-006); ARCH-T027..ARCH-T033 PASS; ARCH7-T001..T015 PASS (D-047 / ARCH-007).

### D-047 ARCH-007 active-DRAFT conflict UX freeze (2026-09-15)
Product decision: when `NUOVO ORDINE DA QUESTO` hits an **active DRAFT** (including persisted empty DRAFT), show conflict dialog — not a terminal error-only UX.

**Depends on:** ARCH-006 COMPLETE (67be68b). ARCH-006 no-active-DRAFT path **unchanged**.

**ARCH-007 = COMPLETE** (docs freeze `86baf12`; implementation `fcd614b` — `feat: handle active draft duplication conflict`).

Baseline close: Manual ARCH-007 5/5 PASS; JVM 506 PASS; connected 160 PASS; schema v2 unchanged; migration NONE.

#### Dialog (frozen copy)
- Title: `C'È GIÀ UN ORDINE IN CORSO`
- Body: `Puoi riprendere l'ordine in corso oppure eliminarlo e creare un nuovo ordine da questo.`
- Actions:
  - `RIPRENDI ORDINE IN CORSO`
  - `ELIMINA DRAFT E DUPLICA`
  - `ANNULLA`
- No second confirmation for `ELIMINA DRAFT E DUPLICA` (destructive meaning is explicit in the button).

#### RIPRENDI ORDINE IN CORSO
- Zero writes: do not modify existing DRAFT, do not modify source ACCEPTED, do not create/duplicate.
- Navigate → standard NewOrder workspace with **existingDraftId**.
- Works for DRAFT with items **and** persisted empty DRAFT (`draftSlot=1`).

#### ANNULLA
- Close dialog; stay on Accepted detail; **zero writes**.

#### ELIMINA DRAFT E DUPLICA (critical — atomic)
ONE Room transaction only. Forbidden: `deleteDraft()` then `duplicateAcceptedOrder()` in two transactions (risk of lost DRAFT if duplicate fails).

Inside the same transaction:
1. reread source order;
2. validate source = current-day ACCEPTED;
3. reread current active DRAFT;
4. verify active DRAFT expected (present / identity match as designed);
5. delete existing DRAFT + children (empty DRAFT: delete the DRAFT **row**, not only items);
6. create new DRAFT;
7. `sourceOrderId` = source ACCEPTED id;
8. copy `generalNote`;
9. copy items;
10. copy additions;
11. copy removals;
12. commit.

Duplication semantics = **ARCH-006 / D-046 preserved** (FAITHFUL + INDEPENDENT; new UUIDs; snapshots/qty/`createdSequence`/manual incl. €0/refs COPY; no catalog reread; no reprice; DRAFT total ORD-022 live; source ACCEPTED immutable).

Failure any step → **full rollback**: old DRAFT intact; source intact; no partial new DRAFT.

#### Empty persisted DRAFT
`status=DRAFT AND draftSlot=1` with zero items = **active DRAFT** → same dialog. Replace must delete DRAFT row and insert new duplicated DRAFT.

#### Concurrency / typed outcomes (no silent success)
Dialog may originate from ARCH-006 `DraftConflict`, but destructive action **must revalidate inside Room** (not trust UI cache alone).

Minimum typed outcomes/errors:
- `Success(newDraftId)`
- `SourceUnavailable` (missing / not ACCEPTED / not current-day)
- `DraftMissing` (active DRAFT no longer present)
- `DraftChanged` (active DRAFT identity ≠ expected)
- `PersistenceFailure`

#### Success navigation (replace)
→ standard NewOrder with **exactly** the new duplicated `draftId`. Old DRAFT must not exist.

#### Architecture (implemented)
Dedicated repository API + use case `ReplaceDraftWithAcceptedOrderDuplicate`, owning the single Room transaction. Do **not** chain separate `deleteDraft` + `duplicateAcceptedOrder` transactions.

#### Schema
DB v2 unchanged; migration NONE; no new DAO required beyond existing draft/order APIs used inside one txn.

#### Tests
ARCH7-T001..ARCH7-T015 (see `docs/09_TEST_PLAN.md`).

#### Out of scope for ARCH-007
Printing; historical archive; schema/migration; catalog reprice; multiple simultaneous DRAFTs; merge current DRAFT with duplicated content.

### D-048 PRINT-001 printer contracts/models freeze (2026-09-15)
M8 first task: **PRINT-001 = READY FOR IMPLEMENTATION**.

**Owns only:** `PrinterProfile`, `PricePrintMode`, `PrinterDriver`, `PrinterService` interfaces, typed `PrinterResult`/`PrintResult`/printer errors — as sketched in `docs/04_ANDROID_ARCHITECTURE.md` §20–23 and field list in `docs/07_PRINTING_SPEC.md` §3 / §17.

**Hard exclusions (M9 / later PRINT tasks):**
- no `BluetoothAdapter` / `BluetoothDevice` / `BluetoothSocket`;
- no Android Bluetooth permissions;
- no NETUM-specific protocol / vendor SDK in domain;
- no MAC address / pairing UI / reconnect strategy / physical printer settings UI;
- no FakePrinterDriver implementation (PRINT-006);
- no ESC/POS encoder (PRINT-005);
- no ReceiptComposer / printable layout (PRINT-002/004);
- no PrinterService Mutex implementation (PRINT-007);
- no enabling `STAMPA` CTA on Accepted detail;
- no schema change / no `print_jobs` table / migration NONE.

**Preserved product rules (already frozen elsewhere — not reopened by PRINT-001):**
- 80 mm non-fiscal ESC/POS architecture;
- label UI `STAMPA` only (never `RISTAMPA`);
- accept commit before print; print failure does not roll back Accepted;
- snapshot-only content for accepted reprints (no catalog reprice);
- Fake printer for development exists as M8 goal, implemented in PRINT-006+.

**Open (do not invent in PRINT-001):**
- exact package path under `printer` (follow existing architecture package sketch);
- whether `UnsupportedEncoding` is a first-class printer error (listed in printing spec §17, not in architecture §23) — defer until encoder/task that needs it;
- PRINT-T001..027 remain later-task gates, not PRINT-001 AC.

**Amendment (D-060 / HW-001):** `PrinterProfile` gains optional `escPosCodeTable: Int? = null` (physical ESC/POS `ESC t` selector; distinct from JVM `codePage`). No other D-048 field ownership change.
M8 next after PRINT-001 COMPLETE (`581f27d`): **PRINT-002 = READY FOR IMPLEMENTATION**.

**Owns only (contracts/models):**
- `PrintKind` = `DRAFT` | `FINAL` (reprint = FINAL content; never `RISTAMPA`);
- `PrintableDocument` + `PrintableLine` (`text`, emphasis hint `NORMAL`/`EMPHASIZED` only);
- `ReceiptComposer` **interface** composing domain `Order` snapshot → `PrintableDocument`
  with `PrintKind`, `PricePrintMode`, `charsPerLine`.

**Normative content rules (already in docs — not reopened):**
- `docs/07_PRINTING_SPEC.md` §§4–15 (headers, section order P/F/B, empty omitted, `createdSequence`, printed names, additions/removals, notes, total, DETAILED/TOTAL_ONLY, wrap);
- snapshot-only totals/names (no catalog reprice);
- 80 mm non-fiscal; no date/time/fiscal/`RISTAMPA` on ticket.

**Split with later PRINT tasks:**
- **PRINT-002** = composition contracts/models only (this decision);
- **PRINT-004** = formatter **implementation** of those content rules (PRINT-T001..T008, T010..T014);
- **PRINT-003** = PricePrintMode preference/UX (enum already PRINT-001);
- **PRINT-005** = EscPosEncoder;
- **PRINT-006** = FakePrinterDriver;
- **PRINT-007** = PrinterService + Mutex;
- **M9** = Bluetooth/NETUM / STAMPA enable / accept+print wiring.

**Hard exclusions:**
- no ESC/POS bytes / code-page calibration / cut commands;
- no FakePrinter / Mutex service / UI / schema / `print_jobs`;
- no `Bluetooth*` Android types / NETUM SDK / MAC / pairing;
- no inventing hardware margins, density, baud, definitive code pages.

**Open (do not invent in PRINT-002):**
- exact package path (`domain.printer` vs `data.printer` sketch) — follow existing printer package conventions from PRINT-001;
- whether additional emphasis levels beyond NORMAL/EMPHASIZED are needed — defer until encoder/formatter proves need;
- PrinterState machine (printing spec §17) — not PRINT-002.

**Amendment (D-060 / HW-001):** `PrintableLine` gains `alignment` (`LEFT|CENTER|RIGHT`, default `LEFT`) and `textScale` (`NORMAL|DOUBLE_WIDTH|DOUBLE_HEIGHT|DOUBLE_BOTH`, default `NORMAL`). `PrintEmphasis` unchanged. Business `ReceiptComposer` styling remains deferred; defaults preserve existing receipts.


### D-050 PRINT-003 PricePrintMode preference wiring freeze (2026-09-15)
After PRINT-002 COMPLETE (`c0ce4e7`): **PRINT-003 = READY FOR IMPLEMENTATION**.

**Owns:**
- DataStore persistence of `pricePrintMode` (default **DETAILED**);
- domain/data get/observe/update API;
- wiring so assembled `PrinterProfile.pricePrintMode` reads this SoT.

**Ownership / SoT (derived, not invented):**
- Architecture §18 + schema §12 + SoT: device/printer prefs → **DataStore**;
- field already on `PrinterProfile` (PRINT-001);
- **not** Room `app_settings` (business-critical only: numbering/day/timezone);
- **not** session-only; **not** per-print override UI in v1;
- single SoT — do not duplicate into Room.

**UX:**
- Public UI **not mandatory** in PRINT-003 itself (printing spec §11);
- no draft/accepted/print-dialog entry point;
- printer settings UI = **BT-006 / D-062**;
- Italian user-facing labels: **FROZEN in D-062** — `DETAILED` → **Prezzi dettagliati**; `TOTAL_ONLY` → **Solo totale**.

**Defers:**
- DETAILED/TOTAL_ONLY layout rendering → PRINT-004;
- Bluetooth/NETUM/Fake/encoder → later PRINT/M9;
- Room migration / `print_jobs` → none.

**Open (do not invent in PRINT-003):**
- exact DataStore key naming / Preferences file name (follow project conventions when DataStore is introduced) — **RESOLVED by PRINT-003 implementation**;
- Settings radio ownership — **RESOLVED: BT-006 / D-062**.


### D-051 PRINT-004 receipt formatter / ReceiptComposer body freeze (2026-09-15)
After PRINT-003 COMPLETE (`bb71f72`): **PRINT-004 = READY FOR IMPLEMENTATION**.

**Owns:** `ReceiptComposer` implementation mapping `domain.model.Order` snapshot → `PrintableDocument` (pre-ESC/POS text lines). Normative layout: `docs/07_PRINTING_SPEC.md` §§4–15. Align section grouping with existing `AcceptancePreviewOrdering` (PIZZE/FRITTURA/BIBITE titles; empty omitted; `createdSequence` ASC, then `id`).

#### Input / SoT
- Input order = domain `Order` aggregate snapshots only (items/additions/removals/names/prices/flags/total/generalNote/displayNumber).
- **No** catalog reread, **no** reprice, **no** Room entities in composer.
- Reprint = `PrintKind.FINAL` with same FINAL content; never emit `RISTAMPA`.

#### DRAFT vs FINAL
- **DRAFT:** header text `BOZZA`; no `displayNumber`; no date/time (already forbidden §5).
- **FINAL:** header text = `displayNumber` only (must be non-null for meaningful FINAL; if null treat as empty header text without inventing a number).
- Neither DRAFT nor FINAL prints date/time/fiscal/logo/QR/`ORDINE`/`RISTAMPA`.

#### Header / separators
- Banner lines of `=` with length = `charsPerLine`.
- Center header token (`BOZZA` / displayNumber) on its line.
- Header token line: `PrintEmphasis.EMPHASIZED`; banner `=` lines: `NORMAL`.
- Section title line compact form `|---------- {TITLE} ----------` with dashes adapted to `charsPerLine`; section title: `EMPHASIZED`.
- Total separator line of `-` length = `charsPerLine`, then `TOTALE` + amount (see money).

#### Categories / items
- Order: PIZZE → FRITTURA → BIBITE; empty categories omitted.
- Within category: `createdSequence` ASC, then `id` ASC.
- Item main line: `{qty}x {productPrintedNameSnapshot}` (snapshot already resolved).
- Additions after main line, `displayOrder` ASC: `+ {printedNameSnapshot}`; indent with 3 spaces as in examples.
- Removals after additions, `displayOrder` ASC: `- {nameSnapshot}`; indent 3 spaces; **never** a price; never subtract from total.
- Item note if non-blank: `NOTA: {note}` after that item's modifiers; wrap long notes.
- Omit blank/null generalNote and blank item notes entirely.
- General note after all sections, before total:
  ```
  NOTE ORDINE:
  {content}
  ```
- Do not waste extra blank separators between DETAILED item blocks.
- **TOTAL_ONLY item spacing (normative from docs/07 §11 example):** insert exactly one blank line between consecutive items (after each item's modifier/note block, before the next item). Not optional.

#### Money format (PrintableDocument text)
- Source = integer cents from snapshots.
- Decimal separator = `,`
- Always exactly 2 fraction digits.
- **NO** thousands grouping.
- **NO** `€` / currency symbol in PrintableLine text.
- Normative examples: `0,00`, `7,00`, `24,50`, `1000,00`.
- Charset/`€` glyph encoding remains PRINT-005 / hardware calibration — not PRINT-004.

#### Prices
- Amounts from snapshot only (`finalUnitPrice`, addition `chargedPrice`, `order.total`).
- Shown amounts are **quantity-extended** when a line price is printed (`unit * quantity`).
- **DETAILED:**
  - Main item shows extended final unit price using **DETAILED price placement** below.
  - Addition prices shown only if: DETAILED + no manual override on item (`manualUnitPrice == null`) + `automaticExtrasPricingSnapshot == true` + extended charged > 0; else addition line without price.
  - Charged €0 addition: print `+ Name` without `0,00`.
  - Removals never priced.
- **TOTAL_ONLY:** no item/addition line prices; still print total from `order.total`.
- Total always printed for DRAFT and FINAL from `order.total` snapshot (§15), using the money format above (right-aligned with `TOTALE` on its line within `charsPerLine` when it fits; otherwise wrap label then dedicated right-aligned amount line — same fit rule as price placement).

#### DETAILED price placement (deterministic; character-based only)
Applies to priced main lines and priced addition lines. Removals have no price.
- Product/addition text is never truncated.
- Price text is never truncated.
- Let `left` = the left-hand text for that logical line (e.g. `2x Margherita` or `   + Provola`).
- Let `price` = money string as frozen above.
- If `left.length + 1 + price.length <= charsPerLine`: emit **one** physical line with `left` left-aligned and `price` right-aligned (pad spaces between).
- Else: wrap `left` normally (word-wrap / hard-split oversized tokens per wrapping rules); then emit `price` on a **dedicated** following physical line, right-aligned to `charsPerLine`.
- Wrapped continuations of `left` do not repeat the price.
- No dot/font/ESC-POS width concepts.

#### Wrapping (character-based, not bytes)
- `charsPerLine` drives wrap and separator widths; not code page / dots / ESC/POS.
- Prefer wrap on word boundaries; if a single token exceeds width, hard-split the token.
- Explicit `\n` in notes: split first, then wrap each segment.
- If `charsPerLine < 1`, clamp to `1` (deterministic guard).
- Long product names wrap without truncating essential text; do not invent font compression.

#### Emphasis
Only `NORMAL` / `EMPHASIZED`:
- EMPHASIZED: header token (BOZZA/displayNumber), section titles.
- NORMAL: all other lines (including total).

#### Determinism
Same `Order` + `PrintKind` + `PricePrintMode` + `charsPerLine` ⇒ same `PrintableDocument`.
No ClockProvider, catalog, Bluetooth/printer state, or randomness.

#### Out of scope
EscPosEncoder / code page / cut / feed bytes; FakePrinter; PrinterService/Mutex; BT/NETUM; UI; Room migration/`print_jobs`.

**Open questions blocking PRINT-004:**
NONE.

Hardware/encoder-only items remain deferred (PRINT-005+ / M9): code page, `€` glyph, cut/feed, physical calibration.

### D-052 PRINT-005 EscPosEncoder contract freeze (2026-09-15)
After PRINT-004 COMPLETE (`ba2c8e8`): **PRINT-005 = READY FOR IMPLEMENTATION**.

**Owns:** pure-Kotlin `EscPosEncoder` mapping `PrintableDocument` + `PrinterProfile` → `EncodeResult` (success bytes or typed encode failure) for later `PrinterDriver.print` (architecture §20). Generic M8 ESC/POS baseline — **not** a NETUM compatibility declaration. Physical verification / calibration = **M9 / HW-001**.

#### Boundary
- Pipeline: `Order snapshot → ReceiptComposer → PrintableDocument → EscPosEncoder → bytes → PrinterDriver → transport`.
- Input = `PrintableDocument` + `PrinterProfile`.
- Output = `EncodeResult` (not raw exceptions as normal business flow).
- Deterministic, stateless, transport-/Android-/Bluetooth-/NETUM-independent.
- Same document + same relevant profile fields ⇒ exact same success bytes.
- Forbidden: `BluetoothSocket`, Android Context, `PrinterDriver` impl, NETUM vendor tables, pairing/retries/reconnect, OS locale, clock, random, mutable globals.
- **No** layout/wrapping/reprice/re-centering (PRINT-004 / D-051 owns text).
- Schema: **DB v2 unchanged**; migration **NONE**; no `print_jobs`.

#### Consumed PrinterProfile fields
- **Consumes:** `codePage`, `feedLines`, `supportsCut`, `cutCommandVariant`.
- **Does not use for layout:** `charsPerLine`, `paperWidthMm`, `pricePrintMode`.

#### Code page / charset (M8)
- `PrinterProfile.codePage` = **JVM Charset name** (conceptual examples: `CP437`, `windows-1252`, `ISO-8859-1`).
- Used **only** for `text → encoded bytes`.
- PRINT-005 does **not** emit `ESC t n` or any hardware printer code-page select.
- Charset → physical printer page selector mapping = **DEFERRED TO M9**.
- Unknown / unavailable charset → `EncodeError.UnsupportedEncoding` (typed; no raw charset exception as business flow).
- Physical NETUM charset calibration = M9 — not frozen here.

#### Text normalization / unencodable (printing spec §24 + this freeze)
Before charset encode:
1. smart single quotes → ASCII `'`;
2. smart double quotes → ASCII `"`;
3. unicode dash variants → ASCII `-`.
Then:
- `€` if still not representable in selected charset → replace with literal ASCII `EUR`;
- any other still-unrepresentable character → typed failure `EncodeError.UnencodableCharacter` (or equivalent under `UnsupportedEncoding` naming if kept as single family — prefer distinct `UnencodableCharacter`).
- Encoder conceptually uses **REPORT** for malformed/unmappable input.
- **Forbidden:** silent `?` replacement; silent character deletion.

#### EncodeResult / EncodeError
```text
EncodeResult
  Success(bytes: ByteArray)
  Failure(error: EncodeError)

EncodeError (minimum)
  UnsupportedEncoding
  UnencodableCharacter
  InvalidProfile
```
- Do **not** reshape `PrinterResult` / `PrintResult` solely to carry `ByteArray`.
- Mapping `EncodeError` → `PrinterError` / service result = **DEFERRED TO PRINT-007**.

#### Initialization
- Every encoded document starts with `ESC @` = `0x1B 0x40`.
- Generic M8 baseline; physical acceptance verified in M9.

#### Emphasis (resolves §23 vs D-051 conflict)
- `NORMAL` → `ESC E 0` = `0x1B 0x45 0x00`
- `EMPHASIZED` → `ESC E 1` = `0x1B 0x45 0x01` (**bold only**)
- **M8 baseline:** no double-width, double-height, alignment, or underline commands (would break D-051 character-width assumptions; header centering already textual).
- After `ESC @`, emphasis state = NORMAL.
- Emit emphasis command **only on state change**.
- After all document lines, force final `ESC E 0` (deterministic final reset).
- **Amendment (D-060 / HW-001):** generic `ESC a` alignment + `GS !` scale (≤2×) + optional `ESC t` are **IN SCOPE** under **D-060**; see D-060 for command order, per-line explicit state, and final reset. D-052 remainder (charset JVM encode, € fallback, LF, feed, cut variants, no silent `?`) stays in force.

#### Line ending
- **LF only** = `0x0A` after **every** `PrintableLine`, including the last.
- Empty `PrintableLine` → one LF.
- Empty `PrintableDocument` → no document-line LF; init / final-reset / feed / cut still apply per rules below.
- **No CRLF.**

#### Feed
- `feedLines` = count of **additional** LF bytes appended after document lines (and after final emphasis reset).
- `feedLines = 0` → none; `feedLines = N` → `N × 0x0A`.
- **Do not** use `ESC d n` in M8.
- `feedLines < 0` → `EncodeError.InvalidProfile` (no silent clamp).

#### Cut
- `supportsCut = false` → **no** cut bytes; `cutCommandVariant` ignored.
- `supportsCut = true` → only frozen variants (exact string match on existing `cutCommandVariant: String?`):
  - `FULL` → `GS V 0` = `0x1D 0x56 0x00`
  - `PARTIAL` → `GS V 1` = `0x1D 0x56 0x01`
- `supportsCut = true` + missing/null/unknown variant → `EncodeError.InvalidProfile` (no automatic cutter choice).
- Physical cutter support (NETUM often tear-only) = M9 profile calibration (`supportsCut=false` remains valid runtime).

#### Command order (deterministic) — M8 baseline; superseded for HW-001 encode by D-060 order
1. `ESC @`
2. For each line: optional emphasis transition + encoded text + LF
3. Final `ESC E 0`
4. `feedLines` × LF
5. Optional cut command
No other commands in M8. **D-060** extends order with optional `ESC t` after init and per-line alignment + scale (see D-060).

#### Owned tests
No existing numbered PRINT-T IDs. Implementation owns **new** byte-for-byte unit tests at least for:
ESC @; NORMAL line; EMPHASIZED line; NORMAL→EMPHASIZED→NORMAL transitions; final NORMAL reset; LF after every line; empty line; empty document; deterministic repeated encode; known charset; unknown charset → UnsupportedEncoding; smart-quote normalize; unicode-dash normalize; €→EUR when required; remaining unencodable → typed failure; feedLines 0 / >0 / <0→InvalidProfile; supportsCut=false no cut; FULL cut; PARTIAL cut; supportsCut=true + missing/unknown variant → InvalidProfile; no truncation/silent `?`.
Do **not** mark any test PASS in this freeze document.

#### Out of scope (M8 PRINT-005)
`ESC t` / physical code-table calibration; NETUM-specific mappings; Bluetooth; concrete `PrinterDriver`; FakePrinter; PrinterService; Mutex; discovery/pairing/reconnect/permissions/UI; Room; migrations.
**Supersession note:** optional `ESC t`, `ESC a`, and `GS !` (≤2×) enter scope in **D-060 / HW-001** without invalidating other D-052 rules.

**Open questions blocking PRINT-005:**
NONE.

### D-053 PRINT-006 FakePrinterDriver contract freeze (2026-09-15)
After PRINT-005 COMPLETE (`cab2c1a`): **PRINT-006 = READY FOR IMPLEMENTATION**.

**Owns:** pure-Kotlin `FakePrinterDriver` implementing existing `PrinterDriver` for tests/dev without hardware (printing spec §25; architecture §20; D-048). Driver fake only — not a Fake of the whole print system. No Bluetooth, NETUM, PrinterService, Mutex, UI, schema.

**Test-plan ownership check (2026-09-15):** `docs/09_TEST_PLAN.md` §16 places PRINT-T020 (“Not configured → typed error”) and PRINT-T022 (“Connection lost, Accepted remains”) under **Printer service**; neither text assigns them to Fake/PRINT-006. PRINT-T026 (“Fake timeout”) is Fake-owned. **No contradiction → ownership freeze below is allowed.**

#### Boundary
- Implements frozen `PrinterDriver` without changing its signature:
  - `connect(profile: PrinterProfile): PrinterResult`
  - `print(data: ByteArray): PrinterResult`
  - `disconnect(): Unit`
- `print` receives **already-encoded** bytes (PRINT-005). No `ReceiptComposer` / `EscPosEncoder` inside Fake.
- Driver outcomes = `PrinterResult` / `PrinterError`. `PrintResult` = PrinterService (PRINT-007).
- Schema: **DB v2 unchanged**; migration **NONE**; no `print_jobs`.
- Mutex / request serialization = **PRINT-007**. Fake is **not** thread-safe by contract (no internal Mutex).

#### Connection lifecycle
- Internal binary state only: **DISCONNECTED** | **CONNECTED**. Do **not** introduce a public domain `PrinterState` type.
- Read-only test observation: `isConnected: Boolean`.
- Initial state = **DISCONNECTED**.
- `print` requires `connected == true`.
- `print` while disconnected → `PrinterResult.Failure(PrinterError.ConnectionLost)`; **payload not captured**; **queued print result not consumed**.
- `PrinterNotConfigured` is **not** the representation of plain disconnected.
- `connect` while DISCONNECTED + Success → CONNECTED; + Failure → remains DISCONNECTED.
- `connect` while already CONNECTED → `Success`, remains CONNECTED (**idempotent**); **does not consume** queued connect results.
- `disconnect`: CONNECTED→DISCONNECTED; DISCONNECTED→DISCONNECTED (**idempotent**); no exception; **does not** clear captured history or queued injected results.
- Clean slate = **new Fake instance**.

#### Failure injection (minimal)
```text
enqueueConnectResult(result: PrinterResult)
enqueuePrintResult(result: PrinterResult)
```
- FIFO, one-shot. Queue non-empty → consume first; empty → `PrinterResult.Success`.
- No random, sleep, artificial delay, callbacks, lambdas, general-purpose mock DSL.
- Connect injectables: `Timeout`, `ConnectionFailed`, `PrinterNotConfigured` (and Success via empty queue / explicit Success).
- Print injectables: `Timeout`, `ConnectionLost`, `PrintFailed`, `PrinterNotConfigured` (and Success via empty queue / explicit Success).
- `ConnectionFailed` = connect injectable only (not a required print outcome).

#### State effects after calls
- connect Success → connected=true
- connect Failure → connected=false
- print Success / Timeout / PrintFailed / PrinterNotConfigured → remains connected
- print ConnectionLost → connected=false

#### Recording
- Ordered history `capturedPayloads` of every `print(bytes)` that starts while **connected**.
- Capture happens **before** returning the configured print result (so Timeout/ConnectionLost/PrintFailed/PrinterNotConfigured attempts are captured if connected at call start).
- Multiple prints preserve invocation order.
- Defensive copies: copy on capture; copy on observation. Mutating caller buffer or returned observation must not alter Fake internal history.
- Convenience derived accessors allowed: `lastCapturedPayload`, `capturedCount` (not duplicate sources of truth).
- **Do not** record/retain `PrinterProfile` in Fake observation API (keep Fake small; do not change `PrinterDriver` to invent extra hooks).

#### Determinism
Same initial Fake + same queued results + same call sequence + same payloads ⇒ same results + same final `isConnected` + same captured history. No clock/random/sleep/hardware.

#### Owned PRINT-T IDs
| ID | Owner |
|----|-------|
| PRINT-T026 Fake timeout | **PRINT-006** |
| PRINT-T020 Not configured | **PRINT-007** |
| PRINT-T022 Connection lost, Accepted remains | **PRINT-007** |
| PRINT-T009 one print request | **PRINT-007** |
| PRINT-T024 Mutex blocks concurrent | **PRINT-007** |
| PRINT-T021/T023/T025/T027 | **PRINT-007 / M9** per documented scope |

Do **not** mark any test PASS in this freeze. Implementation owns Fake unit tests listed below.

#### Unit tests (contract; not PASS here)
initial disconnected; connect success/failure; repeated connect idempotent + does not consume queue; disconnect; repeated disconnect; print while disconnected → ConnectionLost + not captured + queue not consumed; successful capture; defensive copy input/observation; multiple captures order; default Success; FIFO connect/print injection; connect Timeout/ConnectionFailed/PrinterNotConfigured; print Timeout/ConnectionLost/PrintFailed/PrinterNotConfigured; ConnectionLost disconnects; other print failures remain connected; deterministic repeated scenario.

#### Out of scope
PrinterService; Mutex; composer/encoder orchestration; EncodeError mapping; Bluetooth/NETUM/permissions/pairing/reconnect; physical printing; UI; Room; migrations.

**Open questions blocking PRINT-006:**
NONE.

### D-054 PRINT-007 PrinterService / Mutex contract freeze (2026-09-15)
After PRINT-006 COMPLETE (`0a98b0f`): **PRINT-007 = READY FOR IMPLEMENTATION**.

**Owns:** production `PrinterService` orchestration + per-instance `Mutex` serialization (architecture §20–21; printing spec §§18–21, §25–26; backlog PRINT-007). Dependencies: `OrderRepository` (read snapshots), `ReceiptComposer`, `EscPosEncoder`, `PrinterDriver` (Fake in M8), **`PrinterProfileProvider`** (abstraction in PRINT-007; concrete hardware/config provider = M9). No Bluetooth, NETUM, permissions, pairing, reconnect, STAMPA CTA enable, Room/`print_jobs`, invented NETUM profile defaults.

#### Q1 — PrinterProfile runtime source (RESOLVED)
- Introduce minimal **`PrinterProfileProvider`** abstraction (exact Kotlin shape chosen at implementation; no useless hierarchies).
- Semantics: `getActiveProfile()` → one complete runtime `PrinterProfile` **or** not configured.
- Provider returns **one complete** profile including current `pricePrintMode`.
- `PrinterService` does **not** assemble the profile manually and does **not** read individual DataStore keys.
- Not configured → `PrintResult.Failure(PrinterError.PrinterNotConfigured)`.
- PRINT-007: interface/provider contract + **test/static provider allowed** in unit tests.
- Real hardware/configuration-backed provider (DataStore device fields + D-050 `pricePrintMode`) = **DEFERRED TO M9**.
- **Forbidden in PRINT-007:** inventing `charsPerLine` / `codePage` / `feedLines` / `supportsCut` / `cutCommandVariant` defaults; inventing a NETUM profile; Hilt binding to a fake hardware profile.

#### Q2 — EncodeError → PrinterError (RESOLVED)
Extend typed `PrinterError` with explicit equivalents (do **not** map encode failures to `Unknown`):
- `EncodeError.UnsupportedEncoding` → `PrinterError.UnsupportedEncoding`
- `EncodeError.UnencodableCharacter` → `PrinterError.UnencodableCharacter`
- `EncodeError.InvalidProfile` → `PrinterError.InvalidPrinterProfile`
Driver errors propagate **unchanged** as `PrintResult.Failure(same PrinterError)`:
`BluetoothDisabled`, `PermissionDenied`, `PrinterNotConfigured`, `ConnectionFailed`, `ConnectionLost`, `Timeout`, `PrintFailed`, `Unknown`.
No raw encoding exceptions leave the service. Service has no Bluetooth knowledge.

#### Q3 — testPrint document (RESOLVED)
- Must **not** create/read/update an Order; must **not** consume numbers; must **not** use `ReceiptComposer`.
- Build synthetic deterministic `PrintableDocument` then: profile → `EscPosEncoder` → `PrinterDriver`.
- Use `PrintKind.DRAFT` only as existing-model technical value — **do not** add `PrintKind.TEST`. `PrintKind` does not change encoding.
- Exact lines:
  - EMPHASIZED: `TEST STAMPANTE`
  - NORMAL: `Cassa`
  - NORMAL: `à è ì ò ù €`
- No date/time/random/order id/device id.
- Encode failures follow Q2 mapping.

#### Q4 — Order eligibility (RESOLVED)
- `printDraft`: order must exist and status **DRAFT**.
- `printAccepted`: order must exist and status **ACCEPTED**.
- Missing → `PrinterError.OrderNotFound`; wrong status → `PrinterError.InvalidOrderState`.
- Do **not** use `Unknown` / `PrintFailed` / `PrinterNotConfigured` for these cases.
- Printing never mutates order status; Accepted remains immutable.

#### Q5 — Mutex scope (RESOLVED)
- One `Mutex` per `PrinterService` implementation instance.
- All of `printDraft` / `printAccepted` / `testPrint` share that Mutex.
- Entire print job inside `mutex.withLock`: load order (if applicable), get profile, compose or build test doc, encode, connect, print, disconnect/cleanup, result mapping.
- Do **not** move compose/encode outside the lock in M8. Not a persisted print queue.

#### Q6 — Connect/disconnect lifecycle (RESOLVED)
Per job order:
1. validate/load input → 2. obtain profile → 3. compose/build document → 4. encode → 5. connect → 6. print → 7. disconnect.
- Failures **before** `connect()`: no connect, no disconnect (missing order, invalid state, profile missing, encode failure).
- After `connect()` is attempted: `disconnect()` **MUST** run in `finally` (connect Failure, print Failure, unexpected exception). Allowed because `disconnect()` is idempotent (D-053 / driver contract).
- No retry / reconnect / second print attempt in PRINT-007 (M9/UI).
- Unexpected non-typed exception → `PrintResult.Failure(PrinterError.Unknown)` with disconnect still guaranteed after connect attempt.

#### Q6b — disconnect() exception precedence (FROZEN 2026-09-15)
`PrinterDriver.disconnect(): Unit` may technically throw. `disconnect()` is **secondary cleanup** and must **not** erase a more specific primary job failure. No raw cleanup exception escapes `PrinterService`. No new M8 error type (`DisconnectFailed` / `CleanupFailed`); reconsider in M9 only if hardware evidence requires it.

| Case | Primary outcome | disconnect() throws | Final `PrintResult` |
|---|---|---|---|
| **A** | typed `Failure(primaryError)` from connect/print (e.g. ConnectionFailed, ConnectionLost, Timeout, PrintFailed, PrinterNotConfigured, BluetoothDisabled, PermissionDenied, …) | yes | **`Failure(primaryError)`** — cleanup does not replace |
| **B** | connect Success + print Success | yes | **`Failure(Unknown)`** — do not return Success; job lifecycle did not complete cleanly |
| **C** | unexpected main-path exception after connect attempt → `Failure(Unknown)` | yes | **`Failure(Unknown)`** — still no raw exception |
| **D** | connect returns typed `Failure` (still requires finally disconnect) | yes | **same as A** — preserve connect typed failure |
| **E** | failure before connect attempt | n/a | no disconnect; cleanup policy does not apply |

**Required PRINT-007 tests (not PASS until implemented):** typed print Failure + disconnect throws → primary preserved; typed connect Failure + disconnect throws → primary preserved; successful print + disconnect throws → `Failure(Unknown)`; unexpected main-path exception + disconnect throws → `Failure(Unknown)`; no raw exception escapes.

#### Pipelines (frozen)
**printDraft:** Mutex → load persisted DRAFT snapshot → profile → `ReceiptComposer(kind=DRAFT, pricePrintMode=profile.pricePrintMode, charsPerLine=profile.charsPerLine)` → encode → connect → print → disconnect finally.
**printAccepted / reprint:** same with `kind=FINAL`; never `RISTAMPA`; no reprice/renumber.
**testPrint:** Mutex → profile → synthetic document (Q3) → encode → driver lifecycle.

#### Acceptance / snapshot boundary
- `PrinterService` **never** calls `AcceptOrder`.
- Accept success independent of physical print success.
- Print failure on ACCEPTED: remains ACCEPTED; displayNumber/totals unchanged; no rollback/renumber. Retry = same snapshot + same number.
- Draft print never consumes a displayNumber.
- No catalog reread, reprice, order/numbering mutations, or Room writes as part of printing.

#### Test ownership
| ID | Scenario | Owner | M8 w/o hardware |
|---|---|---|---|
| PRINT-T009 | one logical print request → one driver print attempt | **PRINT-007** | YES |
| PRINT-T020 | not configured → typed failure | **PRINT-007** | YES |
| PRINT-T021 | Bluetooth disabled | **M9** | NO |
| PRINT-T022 | Fake ConnectionLost → Accepted remains | **PRINT-007** | YES |
| PRINT-T023 | retry same number | **PRINT-007** service invariant + **M9** UI | PARTIAL |
| PRINT-T024 | Mutex serializes concurrent | **PRINT-007** | YES (deterministic gated driver OK; no sleep/races) |
| PRINT-T025 | draft retry no number | **PRINT-007** service invariant + **M9** UI | PARTIAL |
| PRINT-T026 | Fake timeout | **PRINT-006** — PASS | n/a |
| PRINT-T027 | testPrint creates/modifies no Order | **PRINT-007** | YES |

#### Out of scope
Bluetooth*/NETUM/permissions/discovery/pairing/reconnect; ESC t / physical calibration; STAMPA enable; retry/settings UI; Accept+print wiring (PRINT-020+); inventing NETUM profile defaults; `print_jobs` / persisted queue; FakePrinterDriver changes unless real D-053 incompatibility.

**Open questions blocking PRINT-007:**
NONE.

### D-055 BT-001 Android Bluetooth runtime permission contract freeze (2026-09-15)
After M8 COMPLETE (`cee8162`): **BT-001 = READY FOR IMPLEMENTATION**.

**Owns:** Android-facing runtime permission **evaluation / required-permission list** for the Bluetooth MVP (architecture §22; printing spec §22; security §5). Abstraction name equivalent to `BluetoothPermissionManager` (exact Kotlin shape follows project conventions). No Activity/Compose in domain. No bonded list, discovery, RFCOMM, NETUM, PrinterService, printer UI.

#### MVP strategy (frozen)
- **Bonded/paired devices only** (pairing via Android Settings; app selects bonded device).
- **Discovery OUT OF SCOPE** for BT-001 and for MVP unless a later task explicitly adds it.
- Therefore BT-001 must **not** require `BLUETOOTH_SCAN` and must **not** introduce location permissions for scanning.

#### Platform (current project)
- `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`.
- AndroidManifest today: **no** Bluetooth permissions declared yet.

#### Version behavior (frozen)
- **API 31+ (Android 12+):** runtime `BLUETOOTH_CONNECT` is required for bonded-device operations; manager reports required permission(s) and granted/denied/missing state.
- **API < 31:** no runtime `BLUETOOTH_CONNECT` prompt; manager reports no CONNECT runtime requirement. Preserve legacy install-time Bluetooth permission declarations needed for the bonded strategy (see Manifest).
- Do **not** request `BLUETOOTH_SCAN` for bonded-only MVP.
- Do **not** add `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` for discovery.

#### Manifest (declare at implementation; not in this docs-only task)
Expected declarations for bonded-only MVP:
- `android.permission.BLUETOOTH_CONNECT` (runtime on API 31+).
- `android.permission.BLUETOOTH` with `android:maxSdkVersion="30"` (legacy install-time).
- **Not** `BLUETOOTH_SCAN`; **not** location permissions.
- `BLUETOOTH_ADMIN`: **not** required by BT-001; revisit only if BT-002 proves a platform need for bonded enumeration (do not invent here).

#### Abstraction semantics (frozen)
Capable at least of:
- determining whether required runtime permissions are already granted;
- determining whether a runtime request is required;
- exposing the permission string list the UI/platform layer may request;
- reflecting denied / permanently-denied / “don’t ask again” **when the UI layer can detect it** (manager does not auto-loop requests).
Manager does **not** present system permission UI itself — request launcher/dialogs belong to a later UI integration (first consumer likely BT-002/BT-006).

#### Domain boundary
- Android permission details stay **outside** domain.
- Existing `PrinterError.PermissionDenied` remains the typed surface for missing/denied required permission when a later Bluetooth driver/service path maps it.
- BT-001 does **not** modify `PrinterService`, `PrinterDriver`, or invent a parallel error hierarchy.
- `PrinterError.BluetoothDisabled` stays **separate** from permission denial.

#### Adapter enabled / BluetoothDisabled
- **OUT OF BT-001.** Adapter on/off observation and `BluetoothDisabled` mapping are deferred to **BT-004 / BT-005** (and PRINT-T021 full scenario).

#### Denial cases (frozen)
Support deterministic evaluation for: already granted; missing/required; denied; permanently denied / don’t-ask-again if detectable by UI/platform APIs. No automatic request loops. No inventing Settings-redirect UX unless/until a docs task owns it.

#### Tests (BT-001 owned; no NETUM / no Samsung required)
Unit (injected SDK/permission checker preferred):
- API 31+: CONNECT required; granted; denied;
- API < 31: no CONNECT runtime requirement;
- bonded-only → permission list never includes `BLUETOOTH_SCAN`;
- no location permission introduced;
- deterministic required-permission list;
- no domain/Compose leakage in the manager.
Instrumented (optional/partial): only if Android permission APIs cannot be faked sufficiently — still **no NETUM**, no Samsung mandate.
**PRINT-T021** (“Bluetooth disabled”) = **NOT fully owned** by BT-001 (adapter-disabled path → BT-004/005). BT-001 owns only the **permission denied/missing** deterministic path that later maps to `PermissionDenied`.

#### Out of scope
Bonded list (BT-002); persistence (BT-003); RFCOMM/socket/timeout/reconnect (BT-004/005); settings/testPrint UI (BT-006/007); STAMPA enable / PRINT-020+; PrinterProfileProvider; NETUM; discovery; schema/`print_jobs`.

**Open questions blocking BT-001:**
NONE.

### D-056 BT-002 list bonded devices contract freeze (2026-09-15)
After BT-001 COMPLETE (`345dce6`): **BT-002 = READY FOR IMPLEMENTATION**.

**Owns:** Android-facing read of **already bonded/paired** Bluetooth devices → app-safe model → deterministic `BondedDevicesResult`. No discovery, pairing, persistence, RFCOMM, NETUM, PrinterService, or printer settings UI.

#### Data source / permissions (frozen)
- **Bonded/paired only** (`bondedDevices` / equivalent). Architecture §22; printing §22; UX §20; security §5; D-055.
- **Discovery ABSENT:** no `startDiscovery` / `cancelDiscovery` / discovery BroadcastReceiver / pairing request.
- No `BLUETOOTH_SCAN`; no location permissions. API 31+ bonded read requires runtime `BLUETOOTH_CONNECT` granted (D-055). `BLUETOOTH_ADMIN` still **not** required unless platform evidence appears at implementation.
- **Permission boundary:** BT-002 **uses `BluetoothPermissionManager` (BT-001) directly**. Do **not** duplicate `BluetoothRuntimePermissionPolicy`. If required runtime permissions are missing → **do not** access bondedDevices → `Failure(PermissionDenied)`. UI request launcher = later (BT-006); BT-002 does not present permission UI.

#### Device model (frozen)
- Conceptual `BondedBluetoothDevice`: stable `id` + nullable `name`.
- **Stable id** = Android Bluetooth **device address** string (`BluetoothDevice.address` / MAC-form). Aligns with DataStore `selectedPrinterId/address` (schema §12; architecture §18).
- **Do not** expose `android.bluetooth.BluetoothDevice` outside the platform layer.
- **Null/blank name:** retain the device if `id` is valid; `name` stays nullable. **No** UI placeholder strings (e.g. “Dispositivo senza nome”) in BT-002 — visual fallback = **BT-006**.
- **Duplicate display names:** retained as separate devices (distinct `id`).

#### Q1 — Ordering (FROZEN)
Deterministic sort of the Success list:
1. devices with **non-blank** name first;
2. named devices by name **case-insensitive ASC**;
3. name ties → stable `id`/address ASC;
4. null/blank-name devices **after** named devices;
5. null/blank-name devices by stable `id`/address ASC.

Case-insensitive comparison must be **locale-independent** (must not depend on the device’s current locale; do not use default-locale `lowercase()` / default `Collator`).

#### Q2 — Null adapter (FROZEN)
- `BluetoothAdapter` unavailable/null **≠** empty bonded list **≠** Bluetooth disabled.
- `adapter == null` → `Failure(BluetoothUnavailable)`.
- **Do not** return `Success(emptyList)` for null adapter — empty list means: stack available + permission satisfied + zero bonded devices.
- **`BluetoothUnavailable`** is a **BT-002 local** result error — **not** a new `PrinterError` variant; **not** `PrinterError.BluetoothDisabled`.

#### Bluetooth disabled (unchanged — out of BT-002)
- Adapter exists but disabled → **not evaluated** by BT-002.
- BT-002 must **not** call/use `adapter.isEnabled`.
- `PrinterError.BluetoothDisabled` remains **BT-004 / BT-005** (PRINT-T021 full).

#### Semantically distinct outcomes (frozen)
| Condition | Result |
|---|---|
| adapter null | `Failure(BluetoothUnavailable)` |
| adapter disabled | not evaluated by BT-002 |
| permission missing | `Failure(PermissionDenied)` |
| zero bonded devices (adapter present, permission OK) | `Success(emptyList)` |
| SecurityException while reading bondedDevices | `Failure(PermissionDenied)` — no raw `SecurityException` escapes |

#### Result model (frozen)
Minimal platform result (Kotlin names may follow project style), semantically:
- `BondedDevicesResult.Success(devices: List<BondedBluetoothDevice>)`
- `BondedDevicesResult.Failure(error)` with at least:
  - `PermissionDenied`
  - `BluetoothUnavailable`

Do **not** use `PrinterService`, `PrintResult`, or `PrinterError` for this platform listing. Do **not** introduce a general Bluetooth error hierarchy beyond this listing result.

#### Abstraction (frozen)
Minimal Android-facing provider (name equivalent to `BondedBluetoothDevicesProvider`) under `platform.bluetooth`; testable with injected/fake bonded input + permission manager. No new domain repository layer for listing alone.

#### Tests (BT-002 owned)
Unit (injected/fake preferred):
- permission granted → bonded list;
- permission denied/missing → `PermissionDenied`;
- `SecurityException` → `PermissionDenied`;
- adapter null → `BluetoothUnavailable`;
- available adapter + zero bonded → `Success(emptyList)`;
- multiple devices retained; duplicate names retained by distinct id;
- named sorted case-insensitive ASC; same-name tie → id ASC;
- null/blank names after named; null/blank → id ASC;
- ordering independent of current locale;
- no discovery / SCAN / location.
**No** unit test of `adapter.isEnabled` (disabled ownership = BT-004/005).

#### Manual (after implementation/review — not this docs task)
**YES — PHONE ONLY:** pair ≥1 device in Android Settings → provider returns it; no discovery. **NETUM not required.** Samsung not mandated.

#### Schema
DB v2 unchanged; migration NONE. Persistence of selection = **BT-003**.

#### Out of scope
Selected-printer persistence (BT-003); RFCOMM/socket/connect/print (BT-004/005); `BluetoothDisabled` / `isEnabled` (BT-004/005); settings/testPrint UI (BT-006/007); STAMPA enable / PRINT-020+; PrinterProfileProvider; NETUM; discovery/SCAN/location; schema/`print_jobs`; PrinterService / `PrinterError` changes.

**Open questions blocking BT-002:**
NONE.

### D-057 BT-003 selected printer persistence contract freeze (2026-09-16)
After BT-002 COMPLETE (`d97cb49`): **BT-003 = READY FOR IMPLEMENTATION**.

**Owns:** persist / read / observe / clear the **selected bonded printer identity** in the existing printer DataStore. No Bluetooth stack access, no RFCOMM, no UI, no NETUM profile, no PrinterProfileProvider concrete assembly.

#### Persistence SoT (frozen)
- Mechanism: **DataStore** (architecture §18; schema §12; SoT device/printer prefs).
- **Reuse** existing file `printer_preferences` (PRINT-003 / `PrinterPreferences.DATA_STORE_FILE_NAME`).
- Do **not** create a second DataStore solely for selection.
- Canonical preference key (follow PRINT-003 snake_case): `selected_printer_id` (string).
- Room schema **v2 unchanged**; DataStore preference evolution is **not** a Room migration.

#### Canonical identity (frozen)
- Persist **`selectedPrinterId` = `BondedBluetoothDevice.id`** (Android Bluetooth device **address** / MAC-form string) — D-056 + schema §12 `selectedPrinterId/address`.
- **Do not** use display name as identity.
- **Do not** persist `BluetoothDevice`, sockets, adapter references, or physical profile calibration fields in BT-003.
- **Printer name / `deviceName`:** schema lists it as **optional**; BT-003 does **not** require or own name snapshot persistence. Display name remains BT-002 list / BT-006 UI concern.

#### Public model / API surface (frozen)
- Prefer extending existing domain `PrinterSettingsRepository` (+ `DataStorePrinterSettingsRepository`) so PricePrintMode and selection share one SoT — exact method names follow project style, semantically:
  - `getSelectedPrinterId(): String?`
  - `observeSelectedPrinterId(): Flow<String?>`
  - `setSelectedPrinterId(id: String)`
  - `clearSelectedPrinterId()`
- Unconfigured / cleared / missing key → **`null`**. This is a normal preference state — **not** a DataStore error and **not** `PrinterError.PrinterNotConfigured` at the persistence layer. Later `PrinterProfileProvider` / BT-004 may map null → `PrinterNotConfigured`.

#### Set / clear semantics (frozen)
- **Set:** stores the exact nonblank id string provided (no invented MAC normalization / reformatting unless Android already supplied that form via BT-002).
- **Blank / whitespace-only id:** **invalid** — must be **rejected** and must **not** be written as a configured selection. Rejection shape follows existing repository conventions (e.g. require / `IllegalArgumentException`); do **not** invent a new preference-error hierarchy.
- **Clear:** removes selection → subsequent get/observe = `null`. Clear when already empty remains empty (idempotent outcome).
- **Same id set repeatedly:** semantically **idempotent** (stable get/observe result). Contract does not depend on counting physical DataStore write ops.

#### Bonded-device validation (frozen)
- BT-003 **does not** query the Bluetooth stack / BT-002 provider on set.
- UI (BT-006) normally picks an id from the bonded list; connect-time availability = BT-004/005.

#### Stale selection after external unpair (frozen)
- BT-003 **preserves** the stored id; **no** automatic clear / background cleanup when the device disappears from bondedDevices.
- Later resolve/connect may surface not-configured / unavailable per BT-004/005 — not invented here.

#### PrinterProfileProvider / physical profile (frozen)
- BT-003 owns **identity persistence only**.
- Concrete hardware `PrinterProfileProvider` (charsPerLine/codePage/feed/NETUM defaults + wiring selected id into profile) remains **later M9** (D-054 deferral + HW-001 for physical calibration). BT-003 must not assemble NETUM profiles.

#### PricePrintMode coexistence (frozen)
- Same `printer_preferences` DataStore; new key alongside `price_print_mode`.
- Changing selected printer must **not** change `PricePrintMode`.
- Changing `PricePrintMode` must **not** change selected printer id.
- Default `PricePrintMode` remains **DETAILED** (D-050).

#### DataStore read / error policy (frozen)
- Missing / blank stored selection → `null` (unconfigured).
- Opaque nonblank stored string → return as-is (no invented MAC-format validation on read).
- IOException / DataStore failures: **reuse PRINT-003 behavior** (propagate via normal DataStore/coroutine failure paths); do not invent a parallel exception hierarchy for BT-003.
- No manual Mutex, no in-memory second source of truth, no SharedPreferences duplicate.

#### Security / privacy (frozen)
- Stored **locally only** on device DataStore.
- No backend/cloud/analytics.
- No new logging requirement for the address (security §6: do not log MAC in the clear without necessity).
- No custom encryption unless a later docs task requires it.

#### Tests (BT-003 owned; JVM DataStore patterns as PRINT-003)
At least:
- default / no preference → null;
- set valid id → get/observe exact same id;
- replace A with B → B;
- clear → null; clear when empty → null;
- set same id repeatedly → stable result;
- blank/whitespace id → rejected; no invalid configured value persisted;
- selected-printer key coexists with PricePrintMode;
- PricePrintMode unchanged when selection changes;
- selection unchanged when PricePrintMode changes;
- persistence survives new repository instance on same DataStore backing file.
Follow existing Windows-safe DataStore test discipline (avoid fragile multi-write rename races where already documented).

#### Manual / hardware
- **Manual test required: NO** (persistence-only; deterministic JVM coverage).
- Samsung: **NO**. NETUM: **NO**.

#### Out of scope
Bonded listing (BT-002); permission UI; discovery/SCAN/location; Bluetooth enabled state; RFCOMM/socket/connect/print; NETUM; physical profile calibration; settings/testPrint UI; STAMPA enable; Room/`print_jobs`; PrinterService/`PrinterError` changes; inventing concrete PrinterProfileProvider.

**Open questions blocking BT-003:**
NONE.

### D-058 BT-004 RFCOMM/SPP printer driver contract freeze (2026-09-16)
After BT-003 COMPLETE (`e44c3e4`): **BT-004 = READY FOR IMPLEMENTATION**.

**Owns:** Android Bluetooth Classic **RFCOMM/SPP** transport implementing existing domain `PrinterDriver` for real hardware (printing spec §2 / §22; architecture §20 / §22; backlog BT-004). Connect / write already-encoded bytes / disconnect+cleanup. Bonded-only. Reuse BT-001 permission gate. No discovery, pairing, SCAN, location, UI, NETUM profile calibration, retry/reconnect orchestration, or `PrinterService` redesign.

**Does not own:** receipt compose/encode; numbering; order mutations; settings/testPrint UI (BT-006/007); HW-001 calibration values; full PRINT-T021 product UX; Accept+print wiring (PRINT-020+).

#### Transport (FROZEN)
- Bluetooth **Classic** RFCOMM / **Serial Port Profile (SPP)**.
- Pipeline remains: `… → EscPosEncoder → PrinterDriver → Bluetooth SPP → printer`.
- No NETUM vendor SDK; no domain check for string `"NETUM"`.

#### SPP UUID (FROZEN — explicit)
- Docs/code did not previously pin a UUID.
- **D-058 freezes** the Bluetooth SIG well-known SPP UUID:
  `00001101-0000-1000-8000-00805F9B34FB`
- Used exclusively with `BluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)`.
- Do **not** invent vendor-specific UUIDs without hardware evidence.

#### Selected printer resolution (FROZEN)
- Persistence SoT remains BT-003 `selected_printer_id` in `printer_preferences`.
- Domain `PrinterDriver.connect(profile)` already receives `PrinterProfile` (D-048/D-053).
- **Canonical RFCOMM address** = `PrinterProfile.id`, which **must** equal `BondedBluetoothDevice.id` / `selected_printer_id` when a concrete `PrinterProfileProvider` is wired (later M9 / not invented NETUM defaults here).
- BT-004 driver **does not** read DataStore / `PrinterSettingsRepository` directly (keeps preferences SoT out of transport; matches D-054 service/provider split).
- Missing active profile (`PrinterProfileProvider.getActiveProfile() == null`) → already `PrintResult.Failure(PrinterNotConfigured)` at **PrinterService** (D-054).
- Driver `connect` with blank/missing `profile.id` → `PrinterResult.Failure(PrinterNotConfigured)`.
- Driver resolves `BluetoothDevice` from the adapter’s **bonded** set by address equality with `profile.id`. **No** discovery / `createBond()`.

#### Selected id no longer bonded (FROZEN)
- Stale selection remains in DataStore (D-057); BT-004 does **not** auto-clear.
- If address is nonblank but **not** present in bonded devices → `PrinterResult.Failure(PrinterError.ConnectionFailed)`.
- **Not** `PrinterNotConfigured` (preference/profile identity may still exist; UX “connessione fallita” vs “non configurata” — UX §20).

#### Permission (FROZEN)
- Reuse **BT-001** `BluetoothPermissionManager` (API 31+ `BLUETOOTH_CONNECT`; pre-31 no CONNECT runtime prompt).
- Driver/platform gateway is permission-gated (same pattern as BT-002 provider): if required runtime permissions missing → `PrinterResult.Failure(PermissionDenied)`.
- Catch `SecurityException` on bonded/socket APIs → `PermissionDenied`.
- **No** `BLUETOOTH_SCAN`; **no** location permission.
- Do **not** duplicate API-level policy outside `BluetoothRuntimePermissionPolicy` / BT-001.

#### BluetoothDisabled ownership (FROZEN → BT-004)
- D-055/D-056 deferred adapter enabled / `BluetoothDisabled` to BT-004/005.
- **BT-004 owns** pre-connect check: if adapter present and `isEnabled == false` → `PrinterResult.Failure(BluetoothDisabled)` (PRINT-T021 disabled path starts here).
- BT-005 does **not** own the enabled check.

#### Adapter unavailable / null (FROZEN)
- `BluetoothAdapter == null` (or equivalent “no adapter”) on the connect path → `PrinterResult.Failure(BluetoothDisabled)`.
- Do **not** surface BT-002 local `BondedDevicesError.BluetoothUnavailable` through `PrinterDriver`.
- Rationale: no usable adapter ≡ operator cannot print over BT; existing `PrinterError` set; no new error type in BT-004.

#### Q1 — RFCOMM socket mode (RESOLVED — Q1-A SECURE)
- **BT-004 uses secure RFCOMM only:**
  `BluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)`
  with SPP UUID `00001101-0000-1000-8000-00805F9B34FB`.
- **Do not** call `createInsecureRfcommSocketToServiceRecord(...)` in BT-004.
- **Automatic secure → insecure fallback = NOT ALLOWED** (no dual-try / silent retry with insecure).
- If secure socket fails on real hardware: report **physical incompatibility**; do **not** silently retry insecure.
- Any move to insecure requires an **explicit contract revision** based on **PHONE + NETUM** hardware evidence.
- Secure RFCOMM choice remains **NEEDS HARDWARE VALIDATION** — gate runs **after BT-004 implementation** with PHONE + NETUM (establish secure SPP connection, send minimal already-encoded ESC/POS-safe payload, verify printer receives raw bytes). Not HW-001/HW-002 calibration or 10 consecutive prints.

#### Driver state semantics (FROZEN — align Fake / D-053)
- Binary internal state only: **DISCONNECTED** | **CONNECTED**. No new public `PrinterState` domain type.
- Initial = DISCONNECTED.
- `connect` while CONNECTED → `Success`, remain CONNECTED (**idempotent**).
- `connect` Success → CONNECTED; Failure → DISCONNECTED.
- `disconnect` CONNECTED→DISCONNECTED; already DISCONNECTED → no-op (**idempotent**); prefer no throw.
- `print` while DISCONNECTED → `Failure(ConnectionLost)`; do not write.
- `print` receives **already-encoded** `ByteArray` only — no compose/encode/charset/feed mutation; no append of extra newlines/cut beyond payload bytes.

#### Byte write / flush (FROZEN)
- Write payload to socket `OutputStream` as-is.
- **`flush()` required** after write before returning Success (implementation necessity; not a formatter concern).
- Partial write / IOException during write → preliminary `PrintFailed` or `ConnectionLost` per observable disconnect; **refined uncertain-outcome / timeout classification = BT-005**.

#### Connect / disconnect cleanup (FROZEN)
- Socket created and owned by the driver (or its minimal Android gateway).
- After `connect` attempted: resources closed on `disconnect` / failure paths (stream + socket), deterministic best-effort close.
- Connect failure after socket construction → close and `ConnectionFailed` (or `BluetoothDisabled` / `PermissionDenied` when those gates apply).
- Close failures during cleanup: do not invent `DisconnectFailed`; follow D-054 service finally semantics when called via PrinterService (cleanup must not erase a more specific primary failure).

#### Timeout / ConnectionLost / retry boundary (FROZEN)
| Concern | Owner |
|---------|-------|
| PermissionDenied | **BT-004** (BT-001 gate) |
| PrinterNotConfigured | **BT-004** (blank profile.id) + **PrinterService/Provider** (null profile, D-054) |
| BluetoothDisabled (disabled + null adapter) | **BT-004** |
| Device not bonded / socket create/connect failure (no timeout) | **BT-004** → `ConnectionFailed` |
| Raw write IOException (no timeout wrapper) | **BT-004** preliminary → `PrintFailed` or `ConnectionLost` if socket already dead |
| Connect **timeout enforcement** (connect-only) | **BT-005** → `Timeout` (**D-059 FROZEN**; default **10_000 ms**) |
| Write/flush timeout | **NOT** BT-005 MVP (**D-059 Q2-A**) — future contract revision required |
| Connection-loss classification + uncertain paper UX signal | **BT-005** transport → `ConnectionLost`; microcopy **PRINT-024** (**D-059**) |
| Retry / reconnect algorithm / multi-attempt | **BT-005 policy** = **NO automatic** retry/reconnect (**D-059**); operator/next job only |

BT-004 = raw connect/write/close transport + gates above. BT-005 = hardened connect timing + loss/uncertain mapping + retry/reconnect **policy** (D-059 FROZEN).

#### Threading (FROZEN)
- Security §8: Bluetooth I/O on **Dispatchers.IO** (or injected IO `CoroutineDispatcher`).
- `PrinterDriver` remains `suspend`; blocking RFCOMM must not run on Main.
- Prefer single IO hop in driver/gateway; avoid redundant double-dispatch if caller already provides IO context — still **safe** to `withContext(io)` inside driver.

#### Concurrency (FROZEN)
- **No** extra Mutex inside the RFCOMM driver.
- Job serialization remains **PrinterService** per-instance Mutex (D-054).
- Driver need not be thread-safe beyond what a single serialized session requires.

#### PrinterProfileProvider / NETUM profile (FROZEN out of BT-004)
- BT-004 needs transport identity (`profile.id` address) + SPP UUID + **secure** socket factory (Q1-A).
- Does **not** freeze/invent `charsPerLine` / `codePage` / `feedLines` / cutter / `ESC t` — **HW-001**.
- Concrete hardware `PrinterProfileProvider` assembly remains later M9 (may be stubbed in tests/manual harness only).

#### Testability (FROZEN)
- Minimal Android gateway/factory for `BluetoothAdapter` / `BluetoothDevice` / `BluetoothSocket` / `OutputStream` (same spirit as BT-002 gateway) — **not** a general-purpose Bluetooth framework.
- JVM/unit coverage without hardware at least: missing/blank id → PrinterNotConfigured; permission denied; device not bonded → ConnectionFailed; adapter null/disabled → BluetoothDisabled; secure socket create/connect success; byte write + flush; disconnect cleanup; **no** discovery APIs; **no** insecure socket path.

#### Manual / physical (FROZEN)
- **Manual hardware test required: YES**
- Class: **PHONE + NETUM**
- Timing: **after BT-004 implementation**
- Scope BT-004: prove **secure** RFCOMM/SPP connect + raw byte transport to real NETUM.
- **Not** HW-001 (chars/codepage/feed calibration) and **not** HW-002 (10 consecutive prints).
- Physical payload: use M8 synthetic test document path — `DefaultPrinterService.testPrintDocument()` + existing `EscPosEncoder` + a **test** `PrinterProfile` (temporary values allowed only for spike/harness; not product defaults). Do **not** invent a second business formatter. Driver-under-test still only writes the encoded bytes.

#### Security (FROZEN)
- Address local-only; no backend/analytics/cloud.
- No automatic production logging of MAC/address or receipt bytes (security §6).

#### Schema
- Room **v2 unchanged**; migration **NONE**; no `print_jobs`.

#### Out of scope
Discovery; pairing/`createBond`; `BLUETOOTH_SCAN`; location; settings/testPrint UI; STAMPA enable; Accept+print; retry UX; reconnect; insecure RFCOMM; secure→insecure fallback; HW calibration; NETUM-specific code pages; concrete production `PrinterProfileProvider` defaults; PrinterService Mutex redesign; new `PrinterError` variants.

#### Open questions blocking BT-004
NONE.

### D-059 BT-005 Bluetooth transport timeout and failure contract (2026-09-16) — FROZEN

**Task:** BT-005 — Timeout / disconnect / error mapping
**Status:** **FROZEN** — **BT-005 READY FOR IMPLEMENTATION**.
**Base HEAD:** `82cc98f` (BT-004 COMPLETE).
**Production / tests / Gradle:** docs-only decision resolution; no code changes in this freeze.

#### Scope (FROZEN)

BT-005 hardens the **BT-004** secure RFCOMM transport already shipped. Owns exclusively:

1. **Connect timeout** enforcement (default **10_000 ms**) and `PrinterError.Timeout` mapping.
2. **Transport failure classification** (refine BT-004 preliminary write mapping).
3. **Connection-lost** handling after a usable session existed.
4. **Deterministic cleanup** after failures (owned resources closed; driver → DISCONNECTED when socket is dead/uncertain).
5. **Error mapping** onto the **existing** `PrinterError` set (no new variants).

BT-005 does **not** own:

- Write/flush timeout (**Q2-A**).
- Automatic print retry of the same payload.
- An invented automatic reconnect algorithm / multi-attempt connect loop.
- Uncertain-outcome **microcopy** / RIPROVA UX → **PRINT-024** / UX docs.
- Settings UI, discovery, pairing, SCAN/location, ESC/POS/format, NETUM calibration, Room.

Backlog wording “reconnect/retry policy” = **policy freeze** (explicit operator / next job), **not** authority to invent reconnect/retry algorithms.

#### Q1 — Connect timeout duration (RESOLVED)

- **Production default:** `10_000` ms (MVP explicit decision).
- **Configuration:** injected / configurable at **transport-driver** (or gateway) level.
- **Must not** live in: `PrinterProfile`, DataStore user setting, Room.
- **Tests:** duration injectable / virtual-time friendly; **no** real 10-second sleeps.
- If PHONE+NETUM hardware gate shows **false timeouts** on a normal NETUM connection: **revise D-059 explicitly** — do **not** auto-adapt the value at runtime.

#### Q2 — Write / flush timeout (RESOLVED — Q2-A CONNECT-ONLY)

BT-005 MVP does **not** implement explicit timeout on `OutputStream.write()` or `OutputStream.flush()`.

Contract rationale:

- blocking RFCOMM write/flush is not reliably interrupted by bare coroutine cancellation;
- forced-close timeout during write can create uncertain physical print outcome;
- existing `PrinterError.Timeout` does not encode operation phase;
- do not invent ambiguous write-timeout semantics in MVP.

Therefore:

- connect timeout → `PrinterError.Timeout`;
- write `IOException` after CONNECTED → `ConnectionLost` + DISCONNECTED + cleanup;
- flush `IOException` after CONNECTED → `ConnectionLost` + DISCONNECTED + cleanup.

A future write/flush timeout requires an **explicit separate contract revision** with coherent uncertain-outcome representation. **Not** part of BT-005 MVP.

#### Existing error model (FROZEN — inventory)

| `PrinterError` | BT-005 transport relevance |
|----------------|----------------------------|
| `BluetoothDisabled` | Connect-gate (BT-004); mid-session I/O → `ConnectionLost` |
| `PermissionDenied` | Connect/write `SecurityException` (BT-001/004) |
| `PrinterNotConfigured` | Blank `profile.id` (BT-004) / null profile (service) |
| `ConnectionFailed` | Failure **before** usable CONNECTED session |
| `ConnectionLost` | After CONNECTED: transport dead / write-path loss; also print while DISCONNECTED |
| `Timeout` | **Connect timeout only** (Q2-A) |
| `PrintFailed` | Remains in model (Fake); **Android RFCOMM write/flush `IOException` → `ConnectionLost`**, not `PrintFailed` |
| `Unknown` | Unexpected only; must not mask primary typed failures |
| `UnsupportedEncoding` / `UnencodableCharacter` / `InvalidPrinterProfile` | **Encode / D-054** — **NOT** BT-005 transport |
| `OrderNotFound` / `InvalidOrderState` | Service eligibility — **NOT** BT-005 |

**Do not** add new `PrinterError` variants in BT-005 (including no typed `PossiblyPrinted` / `UncertainPrint`).

#### ConnectionFailed vs ConnectionLost vs PrintFailed (FROZEN)

Aligned with D-053 Fake + D-058 + printing §20:

- **`ConnectionFailed`** = failure **before** a usable connection is established (`connect` does not reach CONNECTED Success). Includes: not bonded, secure socket factory failure, `connect()` `IOException` (non-timeout), output-stream acquisition failure before CONNECTED.
- **`ConnectionLost`** = a usable connection **existed** (or print attempted while already DISCONNECTED) and then transport failed: `print` while DISCONNECTED; write/flush `IOException` after CONNECTED; stream/socket gone mid-session. Per printing §20, **`ConnectionLost` during write = uncertain physical outcome**.
- **`PrintFailed`** = Fake may inject while remaining CONNECTED. For **real RFCOMM**, mid-print `IOException` → **`ConnectionLost` + DISCONNECTED**, not `PrintFailed`.

No ambiguous overlap: pre-CONNECTED connect failures never use `ConnectionLost`; post-CONNECTED write/flush transport failures never use `ConnectionFailed`.

#### Uncertain print outcome (FROZEN)

- Transport signal for uncertain paper: **`PrinterError.ConnectionLost`** on write/flush failure after CONNECTED.
- **No** new typed hierarchy in BT-005.
- Operator microcopy = **PRINT-024** / UX §12 — BT-005 introduces **no UI**.

#### Connect timeout mechanism (FROZEN)

`BluetoothSocket.connect()` is **blocking**. A bare `withTimeout { blockingConnect() }` without closing the socket is **not** sufficient.

Required semantics:

1. Create secure SPP socket.
2. Start connect on IO dispatcher; arm timeout (**10_000 ms** production default).
3. If timeout wins: close attempt socket → unblock connect → clear session → state **DISCONNECTED** → result **`PrinterError.Timeout`**.
4. Race: connect `IOException` caused by timeout-induced close must **not** overwrite **`Timeout`** — use a deterministic timeout-won flag / operation state (no `exception.message` matching).
5. If connect failed first with `IOException` and timeout did not win → **`ConnectionFailed`**.

Allowed shapes (all must close the socket on timeout): `withTimeout` + close-on-timeout sibling, gateway-level timeout that closes the socket, or executor/future **only if** cancel still closes the socket.

`TimeoutCancellationException` from the **controlled** connect timeout path → `PrinterError.Timeout`. Other `CancellationException` → **rethrow**.

#### Driver state after failures (FROZEN)

Binary state remains DISCONNECTED | CONNECTED (D-058).

| Failure | State after | Resources |
|---------|-------------|-----------|
| Connect timeout | DISCONNECTED | Close attempt socket; clear session |
| Connect failure (`IOException` / factory / stream) | DISCONNECTED | Close attempt; clear |
| Write failure | DISCONNECTED | Close owned socket/stream; clear |
| Flush failure | DISCONNECTED | Close; clear |
| Mid-session `PermissionDenied` | DISCONNECTED | Close; clear |
| Bluetooth disabled mid-session (via I/O failure) | DISCONNECTED | Close; clear (`ConnectionLost`) |

In all cases: owned session resources cleared; close attempted quietly; primary typed error preserved.

#### Cleanup precedence (FROZEN)

Same spirit as M8 D-054 Q6b / D-058 closeQuietly:

- **Primary** typed connect/write failure is preserved.
- Socket/stream close errors during cleanup are **secondary** — best-effort; never invent `DisconnectFailed`; never replace primary `Timeout` / `ConnectionFailed` / `ConnectionLost` / `PermissionDenied` with close noise or `Unknown`.
- Boundary: driver owns quiet close on its failure paths; `DefaultPrinterService` still runs `disconnect()` in `finally` and must not erase a typed `PrintResult.Failure` (D-054).

#### Bluetooth disabled during session (FROZEN mapping + HW note)

- Pre-connect `adapter.isEnabled == false` / null adapter → `BluetoothDisabled` (**BT-004**).
- After CONNECTED, Bluetooth off observed via I/O failure → **`ConnectionLost`**. No required mid-session `isEnabled` re-check in BT-005 MVP.
- Exact OS/stack exception on BT-off mid-RFCOMM: **NEEDS HARDWARE VALIDATION** after implementation — mapping preference remains `ConnectionLost`.

#### Permission revoked during session (FROZEN)

- `SecurityException` on connect or write/flush → **`PermissionDenied`**.
- Then DISCONNECTED + deterministic close/clear.
- No string-matching on messages.

#### Automatic print retry (FROZEN — NO)

- **NO automatic print retry** after `Timeout` / `ConnectionLost` / any typed failure (double-print risk).
- Operator RIPROVA = PRINT-023/024.

#### Automatic reconnect (FROZEN — NO algorithm)

- **NO automatic reconnect** loop in BT-005.
- Do **not** invent a reconnect algorithm.

#### Future explicit retry (FROZEN)

- New `PrinterService` job: Mutex → connect once → print once → disconnect finally (**D-054 unchanged**).

#### PrinterService boundary (FROZEN)

- **No** retry/lifecycle redesign.
- Only allowed service change if needed for implementation: preserve/rethrow non-timeout `CancellationException` (do not map to `Unknown`).
- Timeout → `PrinterError.Timeout` produced **inside the driver**.

#### Final error mapping matrix (FROZEN)

| Situation | Map to |
|-----------|--------|
| Permission missing / `SecurityException` | `PermissionDenied` |
| Adapter null before connect | `BluetoothDisabled` |
| Adapter disabled before connect | `BluetoothDisabled` |
| Selected printer missing/blank | `PrinterNotConfigured` |
| Selected device no longer bonded | `ConnectionFailed` |
| Socket factory failure | `ConnectionFailed` |
| Connect `IOException` before usable connection (timeout did not win) | `ConnectionFailed` |
| Connect timeout wins | `Timeout` |
| Output stream acquisition failure | `ConnectionFailed` |
| Print while DISCONNECTED | `ConnectionLost` |
| Write `IOException` after CONNECTED | `ConnectionLost` |
| Flush `IOException` after CONNECTED | `ConnectionLost` |
| Bluetooth switched off during active session (via I/O failure) | `ConnectionLost` |
| `SecurityException` during session | `PermissionDenied` |
| Close/cleanup exception with primary error | secondary; preserve primary |

No `exception.message` matching.

#### CancellationException (FROZEN)

- Controlled connect timeout `TimeoutCancellationException` → `PrinterError.Timeout`.
- Other `CancellationException` → **rethrow** (not `Unknown` / `ConnectionFailed` / `ConnectionLost`).

#### Testability / clock (FROZEN)

- Unit tests own: deterministic timeout behavior; race timeout-vs-connect failure; cleanup precedence; cancellation; write/flush failure mappings.
- Injectable duration + virtual time; **no** real 10 s sleeps; **no** hardware required for unit timeout tests.

#### Configuration (FROZEN)

- Connect timeout: **driver/transport injected**; production default **10_000 ms**.
- **Not** in `PrinterProfile`, DataStore user setting, or Room.
- Not app Settings UI in BT-005.

#### Hardware validation (FROZEN plan — after implementation)

Manual **PHONE + NETUM**, one case at a time, non-destructive:

1. Normal print still works (regression vs BT-004; watch for false connect timeouts → revise D-059 explicitly if needed).
2. Transport loss / printer power-off → typed failure (`ConnectionFailed` / `ConnectionLost` / `Timeout` as applicable).
3. Bluetooth-off mid-session if safely reproducible → prefer `ConnectionLost` on write path.

Not HW-001 calibration.

#### HW-001 order (FROZEN)

After **BT-005 COMPLETE** (`3aad082`) → **HW-001** under **D-060 FROZEN**, before BT-006, unless a new blocker appears.

HW-001 remains owner of physical calibration values: `charsPerLine`, JVM `codePage`, optional `escPosCodeTable`, `feedLines`, cutter/profile freeze **after** paper evidence (D-060). Capability encode (`ESC a` / `GS !` / optional `ESC t`) = D-060.

#### Scope guard (FROZEN)

Out of BT-005: write/flush timeout; automatic retry; automatic reconnect; insecure RFCOMM fallback; discovery; pairing; `BLUETOOTH_SCAN`; location; receipt formatting; ESC/POS encoder changes; NETUM calibration; UI/microcopy; Room; migrations.

Schema: **v2 unchanged**. Migration: **NONE**.

#### Open questions blocking READY

**NONE.**

Non-blocking after impl: mid-session BT-off OS behavior hardware observation; false-timeout revision path if normal NETUM connect exceeds 10 s.

#### BT-005 readiness

**COMPLETE** at `3aad082` (was READY under this freeze).

### D-060 HW-001 ESC/POS calibration capabilities (2026-09-16) — FROZEN

**Task:** HW-001 — NETUM physical profile + ESC/POS text formatting capability calibration
**Status:** **FROZEN** — **HW-001 READY FOR IMPLEMENTATION**.
**Base HEAD:** `3aad082` (BT-005 COMPLETE).
**Production / tests / Gradle:** docs-only freeze; no code changes in this decision.

#### Purpose (FROZEN)

HW-001 must:

1. Calibrate the **physical** NETUM 80 mm printer profile values.
2. Validate **generic** ESC/POS text formatting capabilities reusable later by receipts.
3. Produce a **synthetic calibration sheet** (not a real customer/order receipt).

**User requirement (explicit, in scope):** during HW-001, regulate printed **text size and format** on paper.

**Out of HW-001:** redesign of the business receipt layout / `ReceiptComposer` styling choices.

NETUM remains a **generic ESC/POS** test printer — no hardcode by NETUM name/model until paper proves what the device supports.

#### Relationship to prior contracts (FROZEN)

- **Extends D-052** exclusively with: `ESC a`, `GS !` (≤2× MVP), optional profile-driven `ESC t`.
- **Extends D-048** `PrinterProfile` only with optional physical ESC/POS code-table selector.
- **Extends D-049** `PrintableLine` with alignment + textScale (defaults preserve existing receipts).
- Does **not** invalidate remaining D-052 rules (JVM charset encode, €→EUR fallback, smart-quote/dash normalize, LF-only lines, feedLines, cut variants, no silent `?`).
- Does **not** change: BT-004, BT-005, `PrinterDriver`, transport, retry/reconnect policy.

#### PrintableLine semantics (FROZEN)

Extend `PrintableLine` with:

```text
alignment: PrintAlignment = LEFT
  LEFT | CENTER | RIGHT

textScale: PrintTextScale = NORMAL
  NORMAL | DOUBLE_WIDTH | DOUBLE_HEIGHT | DOUBLE_BOTH
```

Existing `PrintEmphasis` (`NORMAL` | `EMPHASIZED`) **unchanged**.

Combinations are allowed without receipt-specific concepts, e.g. `CENTER + EMPHASIZED + DOUBLE_BOTH`.

Defaults (`LEFT` + `NORMAL` scale + existing emphasis defaults) keep the current business receipt encode path visually equivalent until a later layout task opts into new styles.

#### ESC/POS commands in scope (FROZEN)

| Command | Bytes (MVP) | Meaning |
|---------|-------------|---------|
| `ESC @` | `0x1B 0x40` | initialize / start of every document |
| `ESC a n` | `0x1B 0x61 n` | physical alignment: `n=0` LEFT, `1` CENTER, `2` RIGHT |
| `ESC E n` | `0x1B 0x45 n` | emphasis: `n=0` NORMAL, `n=1` EMPHASIZED (bold) — existing |
| `GS ! n` | `0x1D 0x21 n` | character width/height scale (≤2× only) |
| `ESC t n` | `0x1B 0x74 n` | physical character table select — **only if** profile selector configured |

**GS ! MVP subset (exact `n`):**

| `PrintTextScale` | `n` | Width × Height |
|------------------|----|----------------|
| `NORMAL` | `0x00` | 1× / 1× |
| `DOUBLE_WIDTH` | `0x01` | 2× / 1× |
| `DOUBLE_HEIGHT` | `0x10` | 1× / 2× |
| `DOUBLE_BOTH` | `0x11` | 2× / 2× |

Multipliers above 2×: **NOT** in HW-001.

Underline and other style commands: **NOT** required by this freeze.

#### Deterministic per-line formatting (FROZEN)

- Prevent style leakage across lines.
- Each `PrintableLine` carries explicit alignment, emphasis, and textScale.
- Encoder **may** emit the corresponding commands **before every line** (explicit state preferred over clever delta-only optimization that risks leakage).
- After all document lines, force final formatting reset at least to: alignment **LEFT**, emphasis **NORMAL**, textScale **NORMAL**.
- `ESC @` remains at the **start** of the document (before optional `ESC t`).

#### Encoder command order (FROZEN)

1. `ESC @`
2. Optional `ESC t n` **iff** `PrinterProfile.escPosCodeTable != null`
3. For each line: alignment + emphasis + scale + encoded text + LF (`0x0A`)
4. Final formatting reset (LEFT + NORMAL emphasis + NORMAL scale)
5. `feedLines` × LF
6. Optional cut **only if** `supportsCut` / cut contract allows (unchanged D-052 variants)

No `ESC t` when `escPosCodeTable == null`.

#### JVM charset vs physical ESC/POS table (FROZEN)

Two distinct configurations:

| Field | Role |
|-------|------|
| `PrinterProfile.codePage` | **JVM Charset name** — Unicode text → bytes (existing D-052) |
| `PrinterProfile.escPosCodeTable: Int? = null` | **Physical** ESC/POS table selector byte for `ESC t n` |

- `null` → do **not** send `ESC t`.
- non-null → must be a **valid single selector byte** (`0..255`); emit `ESC t n` once after init.
- **Do not** freeze a NETUM pair (JVM charset + `ESC t`) in this decision — choose after paper evidence.
- € / accent behavior: keep existing encoder fallback (`€`→`EUR` when unencodable; no silent `?` / invented glyph maps).

#### PrinterProfile ownership (FROZEN)

**Owns (physical / device config):**

- `paperWidthMm`
- `charsPerLine`
- `codePage` (JVM charset)
- `escPosCodeTable: Int?` (physical ESC/POS table; new)
- `feedLines`
- `supportsCut`
- `cutCommandVariant`
- `pricePrintMode`

**Must not own (document / layout style):**

- title / item / total / order-number / note font sizes or styles
- receipt alignment choices as profile fields

Those belong to `PrintableDocument` / `ReceiptComposer` (later), not the frozen hardware profile.

#### Business receipt during HW-001 (FROZEN)

- `ReceiptComposer` business styling **unchanged**.
- New `PrintableLine` defaults (`LEFT`, `NORMAL` scale) preserve existing receipts.
- Future intended use (not HW-001): header/title → CENTER + emphasis + larger scale; order number → emphasized/scaled; items → normal; total → emphasized/scaled; notes → normal.

#### Calibration harness (FROZEN)

Required synthetic document (not a real order), recognizable sections:

```text
HW-001 NETUM CALIBRATION

[A] WIDTH — rulers for candidate normal-width line lengths
[B] SPECIAL CHARS — Italian accents + euro handling
[C] ALIGNMENT — LEFT / CENTER / RIGHT
[D] EMPHASIS — NORMAL / BOLD
[E] SIZE — NORMAL / DOUBLE HEIGHT / DOUBLE WIDTH / DOUBLE BOTH
[F] FEED — clearly visible feed markers
```

**Cutter section:** **NOT** in the first calibration sheet.

androidTest inputs:

- `printerId` from instrumentation args (existing pattern).
- Controllable TEST `PrinterProfile` fields: at least `charsPerLine`, JVM `codePage`, optional `escPosCodeTable`, `feedLines` via instrumentation args and/or test-local fixtures.
- **No** DataStore persistence of calibration values in HW-001.
- **No** hardcoded NETUM name/MAC as profile identity beyond the passed `printerId`.

#### Values not frozen yet (FROZEN as “open until paper”)

Do **not** freeze final NETUM values in D-060:

- `charsPerLine` candidates may include 32 / 42 / 48 for observation only
- JVM `codePage`
- `escPosCodeTable`
- `feedLines` (TEST `2` is not definitive)
- `supportsCut` / `cutCommandVariant` (remain `false` / `null` until hardware proves auto-cut)

A **second freeze after hardware** will record the final physical `PrinterProfile`.

#### Special characters calibration (FROZEN)

Paper must exercise at least: `à è é ì ò ù €`.
If `€` is not representable in the chosen JVM charset (+ optional physical table): keep encoder €→`EUR` fallback; no silent invented mappings.

#### Text size calibration (FROZEN)

Print and compare on paper: `NORMAL`, `DOUBLE_HEIGHT`, `DOUBLE_WIDTH`, `DOUBLE_BOTH`.
Operator later chooses which scales suit headers / order number / total — **business choice deferred** (not part of printer-profile freeze).

#### Cutter (FROZEN policy)

- Current: `supportsCut = false`, `cutCommandVariant = null`.
- Keep during early calibration steps; **no cut bytes** until physical auto-cutter evidence.
- If no evidence: final `supportsCut = false`.

#### Hardware sequence (FROZEN — one step at a time)

1. **STEP 1** — baseline + width ruler
2. **STEP 2** — physical text size / alignment / emphasis
3. **STEP 3** — code page / special characters
4. **STEP 4** — `feedLines`
5. **STEP 5** — cutter capability only if relevant
6. **STEP 6** — freeze final physical `PrinterProfile` (separate post-hardware decision)

Rationale: establish geometry + formatter commands first; then glyph interpretation; then spacing/cutter; finally freeze profile.

#### Minimal implementation set (guidance, not values)

**A. Calibration harness only** — synthetic sheet + androidTest (PHONE + NETUM).
**B. Encoder capability additions** — `PrintableLine` alignment/scale + `ESC a` / `GS !` / optional `ESC t` + final reset (generic model; not ad-hoc raw-only test bytes).
**C. PrinterProfile** — add `escPosCodeTable`; final numeric values after STEP 6.
**D. Receipt styling** — deferred.

#### Scope guard (FROZEN)

Out of HW-001: production receipt redesign; settings UI; printer selection UI; BT discovery/SCAN/location; retry; reconnect; backend/cloud; Room; migrations.

Schema: **DB v2 unchanged**. Migration: **NONE**.

#### Open questions blocking READY

**NONE.**

Non-blocking (post-impl hardware): final NETUM `charsPerLine` / JVM+`ESC t` pair / `feedLines` / cutter evidence → second freeze.

#### HW-001 readiness

**READY FOR IMPLEMENTATION** (capability phase). Physical values: see **D-061** after paper.

### D-061 HW-001 NETUM M9 physical profile freeze (2026-09-16) — FROZEN

**Task:** HW-001 — post-hardware profile freeze (STEP 6 after paper evidence).
**Status:** **FROZEN**.
**Depends on:** D-060 FROZEN; HW-001 capability implementation + PHONE+NETUM calibration evidence.
**Base HEAD (docs freeze point):** `8d0aec7` (D-060); HW-001 implementation remains uncommitted until authorized commit.

#### Purpose (FROZEN)

Freeze the **operational NETUM physical `PrinterProfile` values for M9** after paper validation. Does **not** redesign business receipts. Does **not** start BT-006.

#### Hardware evidence (FROZEN summary)

| Area | Observation | Outcome |
|------|-------------|---------|
| WIDTH | 32 fits; 42 fits; 48 wraps by ~5 chars | **charsPerLine=42** safe NORMAL layout width; exact 43–47 max **NOT CLAIMED** |
| FORMAT / text size | Operator approved global **DOUBLE_BOTH** (2×w+2×h) as M9 preferred base | Preference recorded; **not** a `PrinterProfile` field |
| CODE PAGE | ISO-8859-1 + no `ESC t` → Italian accents wrong; €→EUR fallback | **REJECTED** for NETUM |
| CODE PAGE | JVM **IBM00858** + `ESC t` selector **19** → àèéìòù + € correct | **VALIDATED** |
| FEED | `feedLines=3` operationally approved | **FROZEN for M9**; fine tuning deferred post-M9 real use |
| CUTTER | No automatic cutter; manual tear only | **supportsCut=false**, `cutCommandVariant=null` |

#### Frozen M9 NETUM physical profile (FROZEN)

```text
paperWidthMm      = 80
charsPerLine      = 42   # safe validated NORMAL-text layout width; NOT exact physical maximum
codePage          = IBM00858
escPosCodeTable   = 19
feedLines         = 3
supportsCut       = false
cutCommandVariant = null
```

#### Text scale (FROZEN — separate from profile)

- M9 preferred base print scale: **DOUBLE_BOTH** (GS ! `0x11`).
- Belongs to `PrintableDocument` / receipt styling — **not** stored on `PrinterProfile`.
- Detailed receipt hierarchy (title / order number / total / spacing / effective 2× wrap): **DEFERRED POST-M9 / REAL USE**.
- Do not treat this freeze as authorization to redesign production `ReceiptComposer` unless separately authorized.

#### Code page pair (FROZEN)

- JVM charset: **IBM00858** (literal accents + literal euro encodable; no EUR fallback required for this pair).
- Physical table: **`ESC t` n=19** (validated on hardware; frozen for M9 NETUM).
- ISO-8859-1 without `ESC t`: rejected for NETUM accents.

#### Cutter (FROZEN)

- `supportsCut = false`, `cutCommandVariant = null`.
- No further cutter hardware tests required for this device.

#### Feed (FROZEN for M9)

- `feedLines = 3` operationally approved.
- Fine tuning deferred until post-M9 real-use testing.

#### Scope guard (FROZEN)

Out of this freeze: BT-006 settings UI; BT-007; production receipt redesign; discovery/retry/reconnect; Room; migrations; HW-002 ten consecutive prints (separate).

Schema: **DB v2 unchanged**. Migration: **NONE**.

#### Open questions

**NONE** blocking M9 profile use. Non-blocking deferred: post-M9 visual refinement; feed fine tuning; exact charsPerLine maximum above 42.

#### HW-001 status after this freeze

**HW-001 COMPLETE** at `3d05c91`. Next: **BT-006** under **D-062**.

### D-062 BT-006 Printer settings UI contract (2026-09-16) — FROZEN

**Task:** BT-006 — Printer settings UI
**Status:** **FROZEN** — **BT-006 READY FOR IMPLEMENTATION**.
**Base HEAD:** `3d05c91` (HW-001 COMPLETE).
**Depends on:** BT-001..005 COMPLETE; HW-001 / D-061 COMPLETE; PRINT-003 / D-050 COMPLETE; PRINT-007 `PrinterProfileProvider` abstraction COMPLETE.
**Docs-only freeze:** no production/test code changes in this decision.

#### Purpose (FROZEN)

Deliver cashier-facing **Impostazioni → Stampante** UI to:

1. request Bluetooth permission when needed;
2. list **bonded** devices only;
3. select / clear persisted printer identity;
4. show coherent selected / stale / empty / error states;
5. expose **PricePrintMode** UI (persistence already PRINT-003);
6. wire a **concrete production `PrinterProfileProvider`** using `selected_printer_id` + **D-061** physical constants + DataStore `pricePrintMode`.

Does **not** execute test print (BT-007). Does **not** discover/pair. Does **not** edit D-061 physical calibration. Does **not** redesign receipt typography.

#### Navigation entry (FROZEN)

- Entry: Home → **IMPOSTAZIONI** → existing `SettingsScreen` route (`CassaDestination.SETTINGS`).
- Add a **Stampante** section on the **same** `SettingsScreen` (alongside Numerazione).
- **No** new NavHost destination required for MVP.
- Rationale: Numerazione already uses this shell (UX §19); IA tree places Stampante under Impostazioni.

#### Bonded-only / discovery (FROZEN)

- List via existing `BondedBluetoothDevicesProvider` only.
- **NO** discovery, SCAN, `BLUETOOTH_SCAN`, location permission, in-app pairing/`createBond`.
- To associate a new printer: CTA → **open Android system Bluetooth settings** (pairing happens outside the app).

#### Permission UX (FROZEN)

- Uses BT-001 `BluetoothPermissionManager` APIs only (no invented permission state machine).
- On section enter / load:
  - if required runtime permission missing **and** `isRuntimePermissionRequestNeeded()` → **request once** via Compose activity result launcher;
  - show **Permission required** state with explicit CTA to request if not auto-requested / after dismiss;
  - **Denied** (still requestable): Italian error + **RIPROVA** (request again);
  - **Not requestable / permanently denied** (`!isRuntimePermissionRequestNeeded` && still missing): Italian error + CTA **Apri impostazioni app** (system app settings), not a fake Bluetooth SCAN path.
- Pre-API 31: no Bluetooth runtime prompt (BT-001).
- Avoid permission request loops (single request per user action / one auto-request per enter cycle).

#### Bluetooth disabled / unavailable (FROZEN)

- BT-002 list path does **not** own `BluetoothDisabled`.
- BT-006 UI **may** check adapter enabled/null via a thin platform helper (not RFCOMM; not inventing new `PrinterError` for listing).
- States:
  - adapter **null** → **Bluetooth non disponibile**;
  - adapter present + **disabled** → **Bluetooth disattivato** + CTA open system Bluetooth settings;
  - do **not** present disabled as “nessun dispositivo associato”.
- `PrinterError.BluetoothDisabled` remains owned by connect/print (BT-004) for later BT-007/PRINT paths.

#### Device list / display (FROZEN)

- Primary label: non-blank `BondedBluetoothDevice.name`; else **`Dispositivo Bluetooth`**.
- Secondary line: show **Bluetooth address (`id`)** always (disambiguates duplicate names; UX §20 technical id “se utile”).
- Do **not** hardcode vendor strings / real lab MAC / “NETUM” / “BlueTooth Printer”.
- Duplicate names retained as distinct rows (distinct ids) — D-056.
- Ordering: provider order (D-056) unchanged.

#### Selection semantics (FROZEN)

- Tap row → **immediate** persist of exact `device.id` via `PrinterSettingsRepository.setSelectedPrinterId` (same immediate-persist pattern as Numerazione).
- No separate Save button.
- No MAC normalization; no printer-name persistence; no automatic RFCOMM; no automatic test print.
- Selected row must be recognizable without color-only (a11y).

#### Stale selection (FROZEN)

- Persisted `selected_printer_id` may remain after unpair (D-057) — **do not auto-clear**.
- UI must distinguish:
  - `selectedPrinterId` from DataStore;
  - whether that id is in the current bonded list.
- If selected id **not** in bonded list: show **selection unavailable / non attualmente associata** (Italian microcopy), keep id visible (masked/technical), CTAs: choose another bonded device **or** clear selection.
- Do not silently rewrite DataStore.

#### Clear selection (FROZEN)

- **IN SCOPE.** Expose explicit control **Nessuna stampante** / clear → `clearSelectedPrinterId()`.
- Useful for recovering from stale selection; API already exists (D-057).

#### PricePrintMode UI (FROZEN — IN SCOPE)

- Owner UI: **BT-006** (PRINT-003 parked public UI at M9/BT-006; D-050).
- Modes: `DETAILED` / `TOTAL_ONLY`; default DETAILED unchanged.
- Italian labels (freeze OPEN from D-050):
  - `DETAILED` → **Prezzi dettagliati**
  - `TOTAL_ONLY` → **Solo totale**
- Immediate persistence via `updatePricePrintMode`; failure → inline error + RIPROVA (Settings pattern).
- Does not change receipt layout rules (PRINT-004).

#### Physical profile (FROZEN)

- D-061 values are **NOT user-editable** in MVP.
- **Visibility:** hidden/internal (not shown as cashier controls).
- Concrete provider embeds D-061 constants; no calibration UI.

#### Concrete `PrinterProfileProvider` (FROZEN — BT-006 owns)

- Abstraction already exists (PRINT-007). **No production concrete impl yet** — gap closed by BT-006.
- BT-006 must add production provider + Hilt binding that:
  - `selected_printer_id == null` → `getActiveProfile() == null` → `PrinterNotConfigured` at service;
  - otherwise builds `PrinterProfile(id = selectedId, name = bondedNameOrFallback, …D-061 physical…, pricePrintMode = DataStore)`;
  - **never** hardcodes a real device MAC;
  - may return a profile for a **stale** selected id (connect later → `ConnectionFailed` per D-058), name fallback if not bonded.
- BT-007 / PRINT-020+ **consume** this provider; they do not redefine D-061.

#### Test print button (FROZEN)

- **`STAMPA DI PROVA` = BT-007 only.**
- BT-006 must **not** add the button, disabled stub, or invoke `PrinterService.testPrint()`.
- UX §20 lists test print on settings page; backlog split: selection/config = BT-006; invoke test print = BT-007 (button added then).

#### UI states (FROZEN minimum)

Cover at least:

- loading;
- permission required;
- permission denied (requestable / not requestable);
- Bluetooth unavailable;
- Bluetooth disabled;
- no bonded devices (+ CTA open system BT settings);
- bonded devices available;
- selected device present in list;
- selected device stale/unpaired;
- persistence failure (select / clear / price mode);
- bonded list load failure (`PermissionDenied` / `BluetoothUnavailable` from BT-002).

Feedback: prefer **inline state + RIPROVA** (existing Settings pattern). No snackbar required for successful selection if selected state is visible. No crash on recoverable errors.

#### ViewModel contract (FROZEN guidance)

Keep simple `StateFlow` + sealed UI state (match `SettingsViewModel` / TodayOrders patterns). Minimum fields/events:

**State inputs:** loading; permission flags; adapter availability/enabled; devices; `selectedPrinterId`; stale flag; `pricePrintMode`; error message.

**Events:** `onEnter`/`retryLoad`; `requestPermission` / `onPermissionResult`; `selectPrinter(id)`; `clearSelection`; `selectPricePrintMode`; `openSystemBluetoothSettings`; `openAppSettings` (permanent deny).

No mandatory complex MVI.

#### Tests (FROZEN ownership)

- JVM ViewModel tests (primary).
- Compose UI tests where project patterns already exist for Settings-like screens.
- Provider unit tests for null / selected / stale name fallback / D-061 fields / pricePrintMode.
- Coverage minimum: permission missing; granted+devices; no devices; selected; stale; persist success/failure; duplicate names; unnamed device; PricePrintMode update; clear selection.
- **Manual PHONE validation:** YES after implementation.
- **NETUM:** PARTIAL — needed for real bonded/selected evidence; **not** required for every UI state (disabled BT, permission deny, empty bonded can use phone-only).

#### Out of scope (FROZEN)

Discovery; pairing; RFCOMM/timeout changes; test print execution; receipt styling / DOUBLE_BOTH application; physical profile editor; Room; migrations; cloud; STAMPA enable (later PRINT tasks); BT-007+.

Schema: **DB v2 unchanged**. Migration: **NONE**.

#### Open questions blocking READY

**NONE.**

#### BT-006 readiness

**READY FOR IMPLEMENTATION.** Do not start **BT-007** until BT-006 COMPLETE (or explicitly authorized otherwise).

### D-063 BT-007 Test print UI contract (2026-09-16) — FROZEN

**Task:** BT-007 — Test print UI
**Status:** **FROZEN** — **BT-007 READY FOR IMPLEMENTATION**.
**Base HEAD:** `8b41233` (BT-006 COMPLETE).
**Depends on:** BT-001..006 COMPLETE; HW-001 / D-061; PRINT-007 `PrinterService.testPrint()` + Mutex; BT-004/005 driver.
**Docs-only freeze:** no production/test code changes in this decision.
**Does not reopen:** BT-006 accepted Minor (`permissionRequestAttempted` ViewModel-memory only).

#### Purpose (FROZEN)

Deliver cashier-facing **Impostazioni → Stampante → STAMPA DI PROVA** that:

1. invokes existing `PrinterService.testPrint()` once per explicit tap;
2. uses `PrinterProfileProvider` (selected id + D-061 + PricePrintMode);
3. shows success/failure feedback without mutating orders;
4. respects BT-005 (no auto retry/reconnect) and BT-006 (no silent printer substitution).

Does **not** own PRINT-020..024, HW-002, receipt typography redesign, discovery/pairing, transport changes, or physical profile editing.

#### Navigation / placement (FROZEN)

- Same `SettingsScreen` Stampante section as BT-006.
- Add button label exactly: **`STAMPA DI PROVA`** (UX §20 / PRINTING_SPEC §26).
- **No** new NavHost destination.

#### Enablement (FROZEN)

**Enabled** only when all hold:

- printer-settings UI is `Ready`;
- `selectedPrinterId != null`;
- `selectedIsStale == false` (id present in current bonded list);
- no test-print job in progress.

**Disabled / unavailable** when:

- no selected printer;
- stale/unpaired selection;
- permission / Bluetooth unavailable or disabled / load error states (existing BT-006 section states);
- test print already `PRINTING`.

Do **not** silently use another bonded device. Do **not** auto-clear stale selection.

#### Profile / API (FROZEN)

- Call site: `PrinterService.testPrint(): PrintResult` (no parameters — actual interface).
- Profile resolution: inside service via `PrinterProfileProvider.getActiveProfile()`; `null` → `PrinterError.PrinterNotConfigured`.
- Physical constants remain D-061 (80 / 42 / IBM00858 / ESC t 19 / feed 3 / no cutter).
- Never hardcode real device MAC/name.
- UI/ViewModel must **not** call `PrinterDriver` directly.

#### Test content (FROZEN)

Reuse existing `DefaultPrinterService.testPrintDocument()` **unchanged** (D-054 / Q3):

- EMPHASIZED: `TEST STAMPANTE`
- NORMAL: `Cassa`
- NORMAL: `à è ì ò ù €`
- `PrintKind.DRAFT` technical only; no Order; no numbering; no `ReceiptComposer`.

**Typography:** no DOUBLE_BOTH / spacing / feed redesign in BT-007. M9 DOUBLE_BOTH preference remains deferred post-M9 for business receipts.

#### Execution (FROZEN)

- Explicit user tap only → one job.
- `DefaultPrinterService` whole-job Mutex remains SoT (serialize with any future print jobs).
- While `PRINTING`: disable button + visible progress.
- Double-tap / recomposition / ON_RESUME must **not** start another job.
- Job owned by ViewModel coroutine (survives UI recreation without restarting); **not** resumed across process death.
- Failure → no automatic retry; when idle again, user may tap **STAMPA DI PROVA** for a new job (no separate Riprova required unless existing error CTA pattern is reused).

#### Success UX (FROZEN)

- On `PrintResult.Success`: transient feedback **`Test stampa inviato`**.
- Meaning: service completed connect/print/disconnect lifecycle successfully (D-054). Do **not** claim absolute physical-paper certainty beyond that.
- Prefer ephemeral Snackbar or equivalent short-lived message; clear printing progress.

#### Error UX (FROZEN) — map existing `PrinterError` only (no new domain types)

| Error | User-facing (Italian intent) | CTA |
|---|---|---|
| `PermissionDenied` | Autorizzazione Bluetooth necessaria | existing BT-006 permission / app-settings flow |
| `BluetoothDisabled` | Bluetooth disattivato | Apri impostazioni Bluetooth |
| `PrinterNotConfigured` | Nessuna stampante selezionata | select printer in list |
| `ConnectionFailed` | Impossibile connettersi alla stampante | re-enable STAMPA DI PROVA when idle |
| `ConnectionLost` | Connessione interrotta durante la stampa di prova | re-enable STAMPA DI PROVA (no PRINT-024 order copy) |
| `Timeout` | Timeout di connessione alla stampante | re-enable STAMPA DI PROVA |
| `PrintFailed` / `UnsupportedEncoding` / `UnencodableCharacter` / `InvalidPrinterProfile` / `Unknown` | Impossibile completare la stampa di prova | re-enable STAMPA DI PROVA |
| `OrderNotFound` / `InvalidOrderState` | Unexpected for testPrint — treat as Unknown | same |

**No** automatic retry. **No** PRINT-024 “potrebbe essere stato stampato” order semantics for BT-007.

#### ViewModel ownership (FROZEN)

- Prefer extending existing `PrinterSettingsViewModel` with orthogonal test-print fields (`isTestPrinting` / message or sealed test-print phase) rather than a new Nav destination.
- Inject `PrinterService` when Hilt graph is wired.
- Do not redesign BT-006 permission/adapter architecture; handle service-returned PermissionDenied/BluetoothDisabled races safely.

#### DI note (FROZEN)

Production `PrinterService` / driver / encoder may still lack Hilt bindings after BT-006. **BT-007 owns minimal Singleton wiring** to make `testPrint()` callable from settings UI. Do not redesign Mutex/driver contracts.

#### Tests (FROZEN)

JVM (primary): A no selection / NotConfigured; B stale → no service call; C valid → one `testPrint`; D double-tap while printing → one job; E success feedback; F–K error mappings; L no crash on unexpected; M manual retry after failure; N no order/DB mutation (PRINT-T027). Compose optional: enabled/disabled + progress.

#### Manual hardware (FROZEN)

Samsung + paired NETUM test printer **REQUIRED** after implementation/code review (one tap → one paper; no duplicate; idle restore). Do not run in this freeze task.

#### Out of scope (FROZEN)

PRINT-020..024; HW-002; discovery/SCAN/location/pairing; RFCOMM/timeout changes; receipt styling; physical profile editor; Room/schema/migrations; cloud.

Schema: **DB v2 unchanged**. Migration: **NONE**.

#### Open questions blocking READY

**NONE.**

#### BT-007 readiness

**READY FOR IMPLEMENTATION.**


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
Owned by **HW-001** under **D-060 FROZEN**. Need physical paper evidence before freezing final NETUM:
- charsPerLine (candidates only until STEP 6);
- JVM `codePage` + optional `escPosCodeTable` (`ESC t`);
- euro / accented Italian glyphs;
- feedLines;
- text size/alignment/emphasis capability;
- cutter only if hardware evidence.

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
