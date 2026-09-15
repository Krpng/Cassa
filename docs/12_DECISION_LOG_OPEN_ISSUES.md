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

### D-049 PRINT-002 PrintableDocument/ReceiptComposer freeze (2026-09-15)
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
- Public UI **not mandatory** (printing spec §11);
- no draft/accepted/print-dialog entry point;
- printer settings UI remains M9/BT-006;
- Italian user-facing labels: **OPEN** (glossary describes semantics only).

**Defers:**
- DETAILED/TOTAL_ONLY layout rendering → PRINT-004;
- Bluetooth/NETUM/Fake/encoder → later PRINT/M9;
- Room migration / `print_jobs` → none.

**Open (do not invent in PRINT-003):**
- exact DataStore key naming / Preferences file name (follow project conventions when DataStore is introduced);
- Italian Settings copy for DETAILED/TOTAL_ONLY;
- whether a Settings radio lands in PRINT-003 vs only M9 printer settings (public UI optional).


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
- **No** double-width, double-height, alignment, or underline commands (would break D-051 character-width assumptions; header centering already textual).
- After `ESC @`, emphasis state = NORMAL.
- Emit emphasis command **only on state change**.
- After all document lines, force final `ESC E 0` (deterministic final reset).

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

#### Command order (deterministic)
1. `ESC @`
2. For each line: optional emphasis transition + encoded text + LF
3. Final `ESC E 0`
4. `feedLines` × LF
5. Optional cut command
No other commands.

#### Owned tests
No existing numbered PRINT-T IDs. Implementation owns **new** byte-for-byte unit tests at least for:
ESC @; NORMAL line; EMPHASIZED line; NORMAL→EMPHASIZED→NORMAL transitions; final NORMAL reset; LF after every line; empty line; empty document; deterministic repeated encode; known charset; unknown charset → UnsupportedEncoding; smart-quote normalize; unicode-dash normalize; €→EUR when required; remaining unencodable → typed failure; feedLines 0 / >0 / <0→InvalidProfile; supportsCut=false no cut; FULL cut; PARTIAL cut; supportsCut=true + missing/unknown variant → InvalidProfile; no truncation/silent `?`.
Do **not** mark any test PASS in this freeze document.

#### Out of scope
`ESC t` code-page selection; NETUM-specific mappings; physical charset calibration; Bluetooth; concrete `PrinterDriver`; FakePrinter; PrinterService; Mutex; discovery/pairing/reconnect/permissions/UI; Room; migrations.

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
