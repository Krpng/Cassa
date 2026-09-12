# Cassa — Current State

## 1. Purpose of this document

This document is the operational handoff for a coding agent starting without the prior conversation history. It records the verified repository checkpoint, completed scope, frozen decisions, known regressions, and the exact boundary of the next task.

The normative sources remain, in precedence order, `AGENTS.md`, `docs/00_SOURCE_OF_TRUTH.md`, the product/business documents, the specialist specifications, the test plan, and the backlog. This file summarizes the current implementation state; it does not replace those sources.

## 2. Last approved production checkpoint

Verified on 2026-09-12:

```yaml
branch: main
HEAD: fcafbba8c327602c6e44d421bc81d7862068923d
HEAD commit: "docs: finalize m5 milestone"
last UX production commit: 67d599257ce29974447ea2f61a5cac7ea9b2cfad
last UX production commit message: "feat: compact order workspace with search filters"
last bugfix commit: 3830754dede13ea80b270f02cb68d5fb61377f69
ORD-001..ORD-022: COMPLETE
ORDER-059..ORDER-083: COMPLETE
compact workspace refinement: COMPLETE
custom pizza highlight: COMPLETE
Draft conflict delete popup loop regression: FIXED
Demo M5: COMPLETE
M5: COMPLETE
PRE-M6 CONTRACT / READINESS AUDIT: COMPLETE
M6 CONTRACT FREEZE (FREEZE-A + FREEZE-B): COMPLETE (docs only; uncommitted until user requests commit)
M6: NOT STARTED
next step: M6 implementation starting at NUM-001 (only when explicitly authorized)
origin/main: synchronized at fcafbba (freeze docs may be local uncommitted)
```

Contract docs for the compact UX shell:

```text
3803df3822f24ab33e7273e935c45acac70747ea
docs: freeze compact order workspace ux

4dfb4c530669d967d353ea9f4ba840009a771f44
docs: add search category filter contract
```

Post-Demo M5 regression fix (M5 remains COMPLETE):

```text
3830754dede13ea80b270f02cb68d5fb61377f69
fix: prevent draft replacement dialog loop
```

The new agent must verify that `3830754dede13ea80b270f02cb68d5fb61377f69` remains `HEAD` / `origin/main` when starting from this handoff (or a later approved checkpoint). Do not rewrite history, and keep each approved backlog task isolated.

## 3. Current milestone

```yaml
M0 Bootstrap: COMPLETE
M1 Core/domain: COMPLETE
M2 Room: COMPLETE
M3 Menu/search/manual admin: COMPLETE
M4 ODS import: COMPLETE
M5 Draft order core: COMPLETE
M6 Preview/acceptance/numbering: NOT STARTED
```

M5 is **COMPLETE**: `ORD-001`..`ORD-022`, compact order workspace (`ORDER-059`..`ORDER-083`), and Demo M5 end-to-end validation are done. Pre-M6 audit and **M6 CONTRACT FREEZE** (FREEZE-A random numbering + FREEZE-B acceptance preview/UX) are **COMPLETE** in normative docs. Do **not** begin M6 production implementation until explicitly authorized. Do not invent `ORD-023`. NUM/ACCEPT tasks are contract READY (see backlog); archive, duplicate, and printing remain later milestones.

## 4. Completed tasks

- `APP-001..005`: Android/Compose bootstrap, dependencies, package structure, Hilt, and navigation shell.
- `CORE-001..008`: money, text normalization, clock, business date, domain models, pricing, merge policy, and order number formatting.
- `DB-001..009`: catalog/order/settings entities, relations, DAO/read models, repository foundation, single-draft invariant, production `CassaDatabase`, and exported schema baseline.
- `MENU-001..008`: logical catalog CRUD, reactive catalog flows, deterministic product search, menu UI, and product/addition editors.
- `ODS-001..011`: SAF picker, structural ODS parsing, sheet detection, row/price parsing, validation, planning, preview, atomic Room commit, and flag preservation.
- `ORD-001..022`: draft lifecycle/recovery/conflict handling, order screen, catalog search/actions, persisted quick-add/list/detail, additions/removals, pricing variants, custom-line behavior, the aggregated-pizza edit-scope prompt, the atomic `MODIFICA UNA` split, draft-list quantity change plus explicit line removal, the order-level general note, and the live DRAFT total derived from persisted `order_items`.
- Post-`ORD-020` UX mini-task: customized pizza rows in the draft list use a soft purple background when the pizza is customized.
- M5 UX refinement — compact order workspace: COMPLETE (`ORDER-059`..`ORDER-083`), including dedicated SEARCH category filters independent from MAIN.
- Demo M5: COMPLETE (manual Samsung validation tests 1..4 PASS).
- Draft conflict delete popup loop regression: FIXED (`3830754` — M5 remains COMPLETE).

Relevant additional regression checkpoint:

- `8d4810173847d47afe587f9d189d7b0b8a12b3b7`: safe handling of trailing LibreOffice repeated padding in ODS files.

M6 and later milestones are **NOT STARTED**. Do not treat acceptance, numbering, archive, duplication of accepted orders, fake printing, or physical NETUM printing as implemented.

## 5. Current next task

```yaml
task: M6 implementation (NUM-001 first) — ONLY when explicitly authorized
title: Implement sequential numbering service per FREEZE-A/B contracts
priority: P0
status: NOT STARTED
depends_on: M6 CONTRACT FREEZE COMPLETE
milestone: M6
```

Do **not** start M6 production coding without an explicit user authorize. Contracts for NUM-001..005 and ACCEPT-001..007 are READY (NUM-004 blocked by NUM-003 implementation; NUM-005 UI ownership D-040 TBD). Do not invent unfinished M5 features. Do not begin ARCH/PRINT/BT/HW from this handoff.

## 6. Frozen architecture decisions

- Native Android application written in Kotlin.
- Jetpack Compose with Material 3 for UI.
- Navigation Compose for routing.
- Room/SQLite is the business-data source of truth.
- Offline-first, one cash-desk device, no backend/login/cloud in the MVP.
- Hilt for dependency injection.
- Coroutines with `Flow`/`StateFlow` for asynchronous and reactive state.
- DataStore for device/printer preferences that are not business-transactional; business-critical settings remain in Room.
- Android Storage Access Framework for ODS file selection.
- Single Gradle `:app` module; package separation instead of premature module splitting.
- `minSdk 26`.
- Primary flow: `Compose -> ViewModel -> UseCase -> Repository -> Room`.
- Composables do not access DAO/repository directly and contain no business logic.
- Domain code must not depend on Android, Compose, Room, Bluetooth, NETUM/vendor SDKs, or printer transports.
- `ClockProvider` supplies time for business mutations; avoid scattered global `now()` calls.
- Monetary values use `Money` backed by `Long` cents. `Double` and `Float` are forbidden for monetary representation/calculation.
- Historical order state is reconstructed from snapshots, never from live catalog values.

## 7. Frozen business rules

- Order statuses are only `DRAFT` and `ACCEPTED`; the only transition is `DRAFT -> ACCEPTED`.
- At most one active draft exists, enforced structurally through the unique `draftSlot` convention.
- An accepted order is immutable. Repository/domain guards must enforce this, not only UI visibility.
- A draft has no display number, business date, or acceptance timestamp.
- The operational day starts at 05:00 using the explicitly configured timezone (`Europe/Rome` initially) and `businessDayStartMinutes = 300`.
- Standard quick-add merges the same compatible standard product row.
- Separately created customized pizzas never auto-merge, even if their customizations are identical.
- A pizza is customized by any Addition, Removal, non-blank Note, or Manual price.
- One `order_item` with `quantity = N` represents N identical units with the same snapshots and customization.
- Additions and removals are pizza-only. Removals may only reference ingredients belonging to the original pizza composition.
- Inactive catalog records cannot be added to new orders but remain available through historical snapshots.

## 8. Order/customization behavior

Frozen behavior through `ORD-020`:

- Persisted `1x` standard pizza may receive modifiers and increase quantity after explicit apply-all confirmation.
- Persisted `1x` customized pizza may increase quantity after explicit apply-all confirmation.
- Persisted customized `2x` pizza may increase to `3x`, retaining the same row and modifiers after confirmation.
- A customized quantity greater than one remains a single `order_item` because every unit is identical.
- A standard aggregated pizza (`quantity > 1`) opens the scope prompt before personalization:
  - `MODIFICA UNA`
  - `MODIFICA TUTTE`
  - `ANNULLA`
- `MODIFICA TUTTE` updates the same row atomically, preserves quantity and `orderItemId`, and applies the customization to every unit.
- `MODIFICA UNA` records the selected scope but writes nothing on selection alone. The atomic split occurs only when the user saves a valid customization.
- `ANNULLA` performs no writes and leaves the persisted order unchanged.
- Existing customized multi-quantity rows do not use the standard-aggregated prompt.
- Custom split rows never auto-merge with each other, even when their customizations are identical.
- Draft-list quantity and removal follow the `ORD-020` contract recorded in section 19.

### Non-frozen note — `MODIFY_ONE` plus quantity change

Outside `ORD-020` scope. Current conservative behavior: when the edit scope is `MODIFY_ONE`, a simultaneous quantity change is rejected instead of inventing an unspecified semantics.

This is **not** a frozen general business rule. Do not treat it as a permanent contract. The current conservative rejection is preserved until a later normative source defines the case.

## 9. Pricing behavior

Persisted pricing fields:

- `baseUnitPriceCents`
- `automaticExtrasTotalCents`
- `manualUnitPriceCents`
- `finalUnitPriceCents`

Frozen rules:

- `PricingCalculator` is the central pure-Kotlin pricing primitive.
- All prices are `Money`/`Long` cents; no floating-point money.
- With `automaticExtrasPricing = true`, selected additions are charged and contribute to the automatic unit price.
- With `automaticExtrasPricing = false`, additions remain selectable and keep their listed-price snapshot, but `chargedPriceCents = 0` and the automatic unit price remains the base price.
- Removals never reduce price.
- `manualUnitPriceCents != null` has absolute precedence over automatic pricing.
- `manualUnitPriceCents = null` means no manual override.
- `manualUnitPriceCents = 0` is a valid manual override of EUR 0.00.
- Resetting manual price stores `null` and restores the current automatic snapshot-based price.
- `lineTotal = finalUnitPrice * quantity`.

## 10. Draft/accepted behavior

- Room is the only source of truth; every significant draft mutation is persisted.
- The single active draft is protected by `draftSlot = 1`; accepted rows use `draftSlot = null`.
- Startup recovery appears only for a non-empty draft.
- Home observes the active draft via repository `Flow` and shows `ORDINE IN CORSO` only when non-empty.
- Resuming reuses the same draft ID; it does not create a second draft.
- Draft deletion is explicit and cannot delete accepted orders.
- Replacing an existing draft uses the atomic `replaceDraft` path and is protected against double taps.
- `ACCEPTED` orders remain read-only and are not recalculated from the current menu.
- Acceptance, numbering, accepted preview, and printing belong to later milestones and are not implemented merely because draft editing exists.

## 11. ODS import status

M4 is complete: picker, parser, validation, planning, preview, and atomic Room import are implemented.

Key frozen rules:

- ODS is selected through SAF; no broad storage permission.
- Sheets are identified from normalized headers, independently of sheet names.
- `Prezzo Asporto` is the product price; `Prezzo Sala` is completely ignored.
- Matching/updating uses `normalizedName`; category is a mutable attribute, not product identity.
- Records absent from the ODS are not deleted, deactivated, or otherwise changed.
- Existing `active` and `automaticExtrasPricing` flags are preserved on reimport; new records use explicit defaults of `true`.
- For optional managed columns, absent means preserve existing data on update, while present-but-blank clears or applies the documented fallback.
- The ingredients column absent preserves existing relations; present and blank clears them.
- Parsing preserves original display text and never applies spelling correction, fuzzy matching, or autocorrect.
- Prices are parsed exactly without floating point; Addition price zero is valid.
- Import commit is a single Room transaction with rollback on failure.
- Trailing LibreOffice repeated-cell/repeated-row padding is handled safely without unbounded expansion.

See `docs/06_ODS_IMPORT_SPEC.md` for the complete normative import contract.

## 12. Known resolved regressions

### Modifier `LazyColumn` duplicate-key crash

Cause:

```text
Addition id=2
Removal id=2
```

Addition and Removal IDs belong to different namespaces but were previously used as global numeric `LazyColumn` keys, causing a duplicate-key `IllegalArgumentException` during scrolling.

Frozen fix:

```text
addition:<id>
removal:<id>
```

Do not revert to shared numeric keys. Rapid scrolling, selection state, sticky Save, safe drawing, navigation-bar padding, and IME padding must remain stable.

### Home draft-conflict dialog loop

Preserve all parts of the resolved behavior:

- close the conflict dialog before mutation;
- use atomic `replaceDraft`, never separate delete/create operations;
- keep an in-progress gate against repeated taps;
- restore the dialog only on error;
- navigate once on success;
- a Room `Flow` re-emission must not reopen the dialog.

#### Draft conflict delete popup loop regression — FIXED

Found after Demo M5, fixed before the final M5 docs checkpoint commit. M5 stays **COMPLETE**.

```text
3830754dede13ea80b270f02cb68d5fb61377f69
fix: prevent draft replacement dialog loop
```

Root cause:

- during the transition from the conflict dialog to the secondary replace confirmation,
  disposing the first `AlertDialog` invoked `onDismissRequest` → `cancelNewOrderConflict()` → `clearConflict()`;
- that cleared `showReplaceConfirmation` and restarted the conflict cycle.

Frozen fix:

- `cancelNewOrderConflict()` is a no-op when `showReplaceConfirmation == true` or `isNewOrderOperationInProgress == true`;
- conflict dialog closed before the mutation;
- in-progress gate active;
- Flow cannot reopen the conflict during replace;
- success → navigation once;
- failure → dialog restorable;
- double action → no double replace / navigation.

### ODS LibreOffice repeat padding

Trailing repeated empty cells/rows must be skipped or bounded safely. Do not restore behavior that expands spreadsheet padding into oversized in-memory rows or raises the previous 256-cell false positive on the real LibreOffice file.

### Editor WindowInsets and navigation

Editor Save actions remain reachable above navigation bars and the IME. The shared `CassaBackButton` remains in the approved top-right safe area where used; system/gesture Back remains unchanged.

## 13. Manual tests already completed

The following manual checks were explicitly completed on Samsung `SM-S931B`:

### M3/M4 checkpoints

- Menu navigation, sections, search, product/addition editing, validation, active/inactive state, persistence, and editor insets.
- ODS preview against the real LibreOffice structure after the repeated-padding parser fix.
- M4 end-to-end import was declared complete and manually validated before order work began.

### `ORD-015`

- Manual price set and reset.
- Charged Addition plus reset returns to automatic pricing.
- Reset changes the form first and persists through Save.

### Modifier stress regression

- Repeated rapid scrolling across Additions and Removals: PASS.
- Scrolling with an Addition selected: PASS.
- Selection preserved, sticky Save visible, no crash or Android error popup.

### `ORD-017`

- Note plus quantity increase with apply-all confirmation: PASS.
- Manual price EUR 0.00 plus quantity increase with apply-all confirmation: PASS.
- Already aggregated standard row blocks premature customization before scope selection: PASS.

### `ORD-018`

- Prompt visible only for a standard aggregated pizza with `quantity > 1`: PASS.
- `MODIFICA TUTTE` keeps the same `order_item`, preserves quantity, and applies customization to the whole row: PASS.
- `MODIFICA UNA` performs no premature split or mutation: PASS.
- `ANNULLA` performs no write and leaves persisted state unchanged: PASS.

### `ORD-019`

- TEST 1 PASS: `3x` standard → `MODIFICA UNA` → Addition → Save → `2x` standard + `1x` custom.
- TEST 2 PASS: leave and reopen the order → split structure and customization persist.
- TEST 3 PASS: second `MODIFICA UNA` with the identical Addition → two separate custom rows → no auto-merge.

### `ORD-020`

- TEST 1 PASS: `2x` standard → `+` → `3x`, persistence after reopen.
- TEST 2 PASS: `3 → 2 → 1`, minus blocked at `1`, no delete-on-zero.
- TEST 3 PASS: custom quantity increase preserves customization and unit price.
- TEST 4 PASS: remove dialog cancel + confirm; persistence correct.
- TEST 5 PASS: remove last item → empty DRAFT still exists.

### Customized pizza row highlight (post-`ORD-020` UX mini-task)

- TEST VISIVO 1 PASS: standard vs custom distinction correct; alpha `0.22` approved.
- TEST VISIVO 2 PASS: manual price EUR `0,00` → row highlighted.
- TEST VISIVO 3 PASS: note/removal → highlighted; full custom removal returns to normal background.

### `ORD-021`

- TEST 1 PASS: multiline general note saved and persisted after reopen; `generalNote` does not affect custom pizza highlight.
- TEST 2 PASS: dirty unsaved text preserved during quantity mutation / Flow re-emission.
- TEST 3 PASS: clearing the note → functionally persisted `null` after reopen.
- TEST 4 PASS: `generalNote` and item note independent; item note remains and still triggers purple highlight.

### `ORD-022`

- TEST 1 PASS: empty DRAFT → `TOTALE 0,00 €`; quick-add updates total; reopen total persistent/coherent.
- TEST 2 PASS: quantity `+` / `-` → live total updated correctly.
- TEST 3 PASS: Addition updates total; manual price uses override; reset manual returns to automatic.
- TEST 4 PASS: manual price `€0,00` → line/order total `€0,00`.
- TEST 5 PASS: `RIMUOVI` last row → empty DRAFT → `TOTALE 0,00 €`.
- TEST 6 PASS: `3x` standard → `MODIFICA UNA` → `2x` standard + `1x` custom → total equals exact sum of the two persisted rows; reopen unchanged.

### M5 UX refinement — compact order workspace (Samsung visual)

- TEST VISIVO 1 PASS: MAIN compact; catalog vertical space correct with empty DRAFT and with an order line.
- TEST VISIVO 2 PASS: `NOTE` overlay; cancel / save / reopen correct.
- TEST VISIVO 3 PASS: dedicated `CERCA`; quick-add; return to the same DRAFT.
- TEST VISIVO 4 PASS: system Back from SEARCH and from NOTE overlay.
- TEST VISIVO 5 PASS: SEARCH filters `TUTTI` / `PIZZE` / `FRITTURA` / `BIBITE`; query + category AND; quick-add preserves query/filter; MAIN and SEARCH independent; reopen SEARCH → `TUTTI`.

### Demo M5 (Samsung end-to-end)

- DEMO M5 TEST 1 PASS: complex persisted DRAFT with `2x` standard pizza, `1x` separately customized pizza (Addition, Removal, item note), second pizza with manual price, Frittura, Bibita, multiline general order note, and persisted live total.
- DEMO M5 TEST 2 PASS: live quantity mutation, custom pizza modification, additional Addition, manual price update, general note update; persisted total updates correctly; no duplicate/merge regression.
- DEMO M5 TEST 3 PASS: remove app from Recents → reopen → same DRAFT recovered (rows, quantities, customizations, item note, manual price, general note, total).
- DEMO M5 TEST 4 PASS: Android Force Stop → reopen → Room recovery identical (same items/ordering/quantities/Addition/Removal/item note/manual price/general note/total); no duplicate DRAFT/rows; no catalog repricing; no crash.

### Draft conflict delete popup loop (Samsung regression)

- BUGFIX POPUP LOOP — TEST 1 PASS: existing DRAFT → Home → `NUOVO ORDINE` → `ELIMINA E CREA NUOVO` → confirm → new DRAFT → no popup loop → navigation once.
- BUGFIX POPUP LOOP — TEST 2 PASS: `ANNULLA` conflict → existing DRAFT preserved → no navigation → conflict can reopen normally on the next attempt.
- BUGFIX POPUP LOOP — TEST 3 PASS: system Back → cancel/no mutation; repeated/double confirmation → one replace → one navigation → no duplicate DRAFT.

Some edge cases are covered by automated tests but were not necessarily repeated manually. Do not describe an automated check as a manual hardware check. In particular, `ORDER-012` transaction rollback is covered by automation and was not provoked manually on the device.

## 14. Deferred/manual checks still open

- Demo M5 is **COMPLETE**. Do not reopen M5 work unless a regression requires a dedicated fix task.
- A dedicated manual comparison of both `automaticExtrasPricing=true` and `false` paths was deferred; these paths have automated coverage and must remain green.
- Acceptance, numbering, archive, duplicate, and final printing flows belong to future milestones (M6+) and have **not** been validated as completed features and are **not** implemented by M5.
- Physical NETUM printer calibration remains open: pairing, width, code page, euro/accent rendering, feed, reconnect, interrupted-print semantics, and repeated-print stability.
- Hardware items in the checklist of `docs/09_TEST_PLAN.md` and `docs/07_PRINTING_SPEC.md` must not be marked complete without real printer validation.
- Pre-M6 audit + M6 CONTRACT FREEZE (FREEZE-A/B) are COMPLETE in docs; M6 production remains NOT STARTED until authorized.

## 15. ORD-018 final behavior

`ORD-018` introduced `AggregatedPizzaEditScope` and a Compose prompt for standard aggregated pizzas.

Final behavior:

1. Opening a standard aggregated pizza with quantity greater than one shows the one/all/cancel prompt.
2. `MODIFICA TUTTE` enables customization and sends the explicit apply-all intent through the existing use case/repository path.
3. Room atomically updates the existing item; quantity and item ID remain unchanged.
4. `MODIFICA UNA` records its explicit scope but, until Save of a valid customization, performs no write; the split belongs to `ORD-019`.
5. `ANNULLA`, dialog dismissal, and system Back leave the database unchanged.
6. The prompt does not appear for non-pizza rows, quantity-one rows, or already customized multi-quantity pizza rows.

Relevant implementation and regression-test files:

- `app/src/main/java/it/krpng/cassa/feature/order/OrderItemDetailViewModel.kt`
- `app/src/main/java/it/krpng/cassa/feature/order/OrderItemDetailScreen.kt`
- `app/src/main/java/it/krpng/cassa/data/repository/RoomOrderRepository.kt`
- corresponding JVM and Android tests under `app/src/test` and `app/src/androidTest`

## 16. ORD-019 final behavior

`ORD-019` implemented the atomic split for `MODIFICA UNA`.

Final behavior:

```text
MODIFICA UNA
→ does not write on selection alone
→ split only on Save of a valid customization

3x standard
→ 2x source standard
+ 1x new custom
```

Source row:

- same `orderItemId`;
- quantity decreased by one;
- same `createdSequence`.

New row:

- new UUID;
- `quantity = 1`;
- next available `createdSequence` in the order;
- customization applied exclusively to the new row.

Transaction and pricing:

- single Room transaction;
- full rollback on failure;
- `orders.updatedAt` via `ClockProvider`;
- source product/printed-name/category/base-price/automatic-extras and related snapshots preserved without re-reading the live catalog;
- pricing recalculated through existing `PricingCalculator` primitives;
- custom split rows never auto-merge.

### Frozen `createdSequence` decision — implemented

When `MODIFICA UNA` creates a new `order_item` through the atomic split:

- the source row keeps its own `createdSequence`;
- the new row receives the next available `createdSequence` in the order;
- the new row is therefore treated as a newly inserted `order_item`;
- existing `order_item` rows are not renumbered or reordered;
- `createdSequence` remains deterministic and unique according to existing conventions.

Example:

```text
before:

Margherita 3x          createdSequence=1
Crocchè                createdSequence=2
Coca Cola              createdSequence=3

MODIFICA UNA on Margherita

after:

Margherita 2x          createdSequence=1
Crocchè                createdSequence=2
Coca Cola              createdSequence=3
Margherita custom 1x   createdSequence=4
```

Automated coverage linked to `ORD-019`:

- `ORDER-010`: `3x` standard, Modify one → `2x` standard plus `1x` customized — covered.
- `ORDER-012`: transaction failure leaves the original structure intact — covered.
- `UI-004`: critical Compose flow for `3x` standard → Modify one — covered.
- `ORDER-011` (Modify all) remains covered by `ORD-018`.

Relevant implementation files:

- `app/src/main/java/it/krpng/cassa/domain/repository/OrderRepository.kt`
- `app/src/main/java/it/krpng/cassa/domain/usecase/SplitStandardPizzaItem.kt`
- `app/src/main/java/it/krpng/cassa/data/repository/RoomOrderRepository.kt`
- `app/src/main/java/it/krpng/cassa/feature/order/OrderItemDetailViewModel.kt`
- corresponding JVM and Android tests under `app/src/test` and `app/src/androidTest`

## 17. ORD-020 final behavior

`ORD-020` implemented draft-list quantity change and explicit line removal.

List controls:

```text
[-] quantity [+]
RIMUOVI
```

Quantity:

- `[+]`: `quantity + 1`, immediate persistence.
- `[-]` with `quantity > 1`: `quantity - 1`, immediate persistence.
- `[-]` with `quantity = 1`: disabled / no mutation.
- `quantity = 0` is invalid and does not mean delete.

A quantity change preserves:

- the same `order_item`;
- the same `createdSequence`;
- the same snapshots;
- the same customizations;
- the same unit price;
- no split;
- no merge;
- no repricing.

Removal:

- `RIMUOVI` opens a confirmation dialog;
- `ANNULLA` writes nothing;
- confirm deletes children and the item atomically;
- removing the last row leaves an existing empty `DRAFT`;
- `ACCEPTED` remains immutable;
- `orders.updatedAt` uses `ClockProvider`.

Automated coverage linked to `ORD-020`:

- `ORDER-013`..`ORDER-025` are implemented/covered.

Relevant implementation files:

- `app/src/main/java/it/krpng/cassa/domain/usecase/ChangeQuantity.kt`
- `app/src/main/java/it/krpng/cassa/domain/usecase/RemoveOrderItem.kt`
- `app/src/main/java/it/krpng/cassa/data/repository/RoomOrderRepository.kt`
- `app/src/main/java/it/krpng/cassa/feature/order/NewOrderScreen.kt`
- `app/src/main/java/it/krpng/cassa/feature/order/NewOrderViewModel.kt`
- corresponding JVM and Android tests under `app/src/test` and `app/src/androidTest`

Production commit:

```text
30098949315edfd7d6f98469ab01a94a39d1636f
feat: manage order line quantity and removal
```

## 18. Customized pizza row highlight

Completed as a visual-only mini-task after `ORD-020`.

Detection rule:

```text
category == PIZZA
AND (
  Addition
  OR Removal
  OR note is not blank
  OR manualUnitPrice != null
)
```

Notes:

- `manualUnitPrice = EUR 0,00` is a valid customization and must highlight;
- standard pizza → normal background;
- non-pizza rows → unchanged by this rule;
- removing all customizations returns the row to the normal background;
- background uses theme `tertiary` with alpha `0.22`;
- text/price colors, quantity/remove interactions, and `ORD-020` behavior are unchanged.

Approved commit:

```text
ad96197c66c68f306f15329d7d26a2a4a94b95d1
feat: highlight customized pizza rows
```

## 19. ORD-021 final behavior

`ORD-021` implemented the order-level general note on the DRAFT screen.

Field:

- `orders.generalNote` (schema already present; no migration);
- distinct from `order_items.note`.

UI (New Order / DRAFT) — shell after compact workspace:

```text
NOTE → overlay/modal
NOTA ORDINE
[multiline]
[ANNULLA] [SALVA]
```

- header action `NOTE` opens the overlay (inline editor / `SALVA NOTA` removed from MAIN);
- explicit save only — no per-keystroke autosave / debounce;
- `ANNULLA` / system Back from overlay: no persistence;
- `SALVA` uses the existing `UpdateGeneralNote` path.

Normalization:

- blank / whitespace-only → persisted `null`;
- non-empty → external trim only; internal spaces and significant line breaks preserved.

Guards and persistence:

- only `DRAFT` is editable; `ACCEPTED` rejected at repository/domain;
- Room is the source of truth;
- successful save updates `orders.updatedAt` via `ClockProvider`;
- dirty editor is not overwritten by unrelated Flow re-emissions (quantity +/-, remove, item edits);
- save failure keeps local text and allows retry;
- `orders.generalNote` does **not** make a pizza customized and does not trigger purple row highlight.

Automated coverage linked to `ORD-021`:

- `ORDER-026`..`ORDER-039` PASS.

Production commit:

```text
f880f8ec607266bad2507bcf18bd87ccfead3681
feat: add general order note
```

## 20. ORD-022 final behavior

`ORD-022` implemented the live DRAFT order total derived from persisted lines.

Source of truth:

- DRAFT live total = derived **only** from persisted `order_items`;
- never from dirty editors, temporary Compose/ViewModel values, or live catalog / current menu prices.

Formulas (`Money` / `Long` cents only; no `Double` / `Float`):

```text
lineTotalCents = finalUnitPriceCents * quantity
orderTotalCents = checked sum of lineTotalCents
```

`orders.totalCents`:

- **not** authoritative for the DRAFT live total;
- **not** synchronized by ORD-022 after draft mutations;
- future Acceptance will persist the definitive snapshot (out of scope).

Reactive path:

```text
Room mutation completed
→ Flow/read model emits persisted state
→ derived total recalculated
→ UI updates
```

No optimistic total before persistence.

Empty DRAFT:

- UI shows `TOTALE` `0,00 €` (visible even with zero lines).

No effect on total:

- dirty unsaved editors;
- live catalog changes;
- item note alone;
- `generalNote` (saved or dirty).

Manual price `€0`:

- valid override → `finalUnitPriceCents = 0` → line/order contribution `€0`.

Atomic split:

- no special-case logic; sum the two persisted rows' line totals.

Overflow:

- explicit `AmountOverflow` / equivalent; no silent wraparound / corrupted numeric total.

UI:

- sticky footer `TOTALE` outside the scrollable catalog area;
- `COMPLETA` unchanged / outside ORD-022 scope.

Automated coverage linked to `ORD-022`:

- `ORDER-040`..`ORDER-058` PASS.

Production commit:

```text
c7a94fd802a6300c76b7b9905624c2d824ad98ab
feat: derive live draft total from persisted items
```

## 21. M5 UX refinement — compact order workspace

Completed mini-task after `ORD-022`. Production commit:

```text
67d599257ce29974447ea2f61a5cac7ea9b2cfad
feat: compact order workspace with search filters
```

Automated coverage:

- `ORDER-059`..`ORDER-074`: PASS / COMPLETE
- `ORDER-075`..`ORDER-083`: PASS / COMPLETE

### MAIN shell

```text
[ NOTE ]        [ CERCA ]        [ ← INDIETRO ]
```

- inline general-note editor: removed
- inline search field: removed
- categories preserved: `TUTTI` / `PIZZE` / `FRITTURA` / `BIBITE`
- catalog preserved
- sticky `TOTALE` preserved (ORD-022)
- vertical catalog space increased on portrait smartphones

### NOTE

- `NOTE` opens a modal/overlay
- `generalNote` semantics: ORD-021 unchanged
- `ANNULLA`: no persistence
- `SALVA`: existing `UpdateGeneralNote` path
- save failure: local text preserved; overlay stays open; retry possible
- system Back from overlay: cancel / no write

### CERCA

- dedicated in-feature `SEARCH` mode
- no new Navigation destination
- search field + filters `TUTTI` / `PIZZE` / `FRITTURA` / `BIBITE`
- search engine: existing implementation reused
- query + category: AND
- default SEARCH category: `TUTTI`
- MAIN category state independent from SEARCH category
- quick-add preserves SEARCH mode, query, and SEARCH category
- reopen SEARCH: category resets to `TUTTI`
- system Back / `INDIETRO`: return to the same DRAFT with MAIN filter unchanged

### Invariants unchanged by this UX shell

- quick-add business behavior: unchanged
- search ranking: unchanged
- ORD-020: unchanged
- ORD-021: unchanged
- ORD-022: unchanged
- DB / schema / migration: unchanged

### M5 frozen outcome

With Demo M5 complete, M5 now guarantees:

- persistent single active DRAFT
- quick-add
- aggregation rules
- customized pizza no-merge rules
- atomic single-unit split
- quantity management
- explicit line removal
- additions/removals
- item notes
- manual pricing
- general order note
- persisted-derived live total
- compact mobile workspace
- dedicated search workspace
- search category filters
- process-death recovery

M5 does **not** implement or validate:

- acceptance
- order numbering
- accepted-order archive
- duplication of accepted order
- fake printing
- physical NETUM printing

Those belong to later milestones after M6; M6 contracts are frozen (FREEZE-A/B) but production is NOT STARTED.

## 22. Rules for the next coding agent

1. Read `AGENTS.md`, `docs/17_CURRENT_STATE.md`, the exact backlog task, linked requirements, linked test plan, and traceability matrix before changing files. Consult `README_CODEX.md` and `docs/11_CODEX_WORKFLOW.md` only for workflow conventions that remain applicable; they are non-normative for Cursor, must not override agent-agnostic instructions, and must not introduce Codex-specific behavior into Cursor work.
2. Implement one backlog task at a time and stop at its boundary.
3. Do not anticipate later tasks or add infrastructure merely because it may be useful later.
4. Keep `Compose -> ViewModel -> UseCase -> Repository -> Room`; do not move business logic into Composables or DAOs.
5. Preserve all frozen invariants in this document and the normative sources.
6. Do not commit or push before explicit user approval after review and required testing.
7. Do not clear application data. Never use `pm clear` or uninstall as a test shortcut unless explicitly authorized.
8. Do not use `git reset`, `git restore`, `git checkout`, `git rebase`, or `git amend` unless explicitly requested.
9. Make deliberate, minimal source edits and preserve unrelated working-tree changes.
10. Run all task-required JVM, build, Android-test assembly, and connected tests before requesting review. Never report an unexecuted test as PASS.
11. Keep the Samsung awake/unlocked when device tests require Compose UI interaction; do not erase its persisted Cassa data.
12. Report the exact files changed, test results, assumptions, deferred behavior, and open issues.
13. Do not change documentation to justify behavior that contradicts higher-precedence requirements.
14. Stop and report if the task contract is contradictory, requires a destructive migration, or would violate an architectural boundary.
15. Next implementable M6 task is **NUM-001** only when explicitly authorized. Do not begin M6 / NUM / ACCEPT / archive / duplicate / printing production implementation without authorization. FREEZE-A/B contracts are COMPLETE.

## 23. Verification baseline

Latest verified M5-complete baseline (Demo M5 + draft-conflict popup-loop regression fix):

```yaml
./gradlew.bat test: PASS — 384 JVM
./gradlew.bat assembleDebug: PASS
./gradlew.bat assembleDebugAndroidTest: PASS
./gradlew.bat connectedDebugAndroidTest: PASS — 82 tests on Samsung SM-S931B
./gradlew.bat installDebug: PASS
compact workspace visual tests 1..5: PASS
Demo M5 tests 1..4: PASS
BUGFIX POPUP LOOP tests 1..3: PASS
device: Samsung SM-S931B
app data cleared: NO
```

Historical note — compact-workspace UX verification immediately after `67d5992` (before the popup-loop regression fix) was:

```yaml
./gradlew.bat test: PASS — 382 JVM
./gradlew.bat connectedDebugAndroidTest: PASS — 81 tests on Samsung SM-S931B
```

Documented repository state after M6 CONTRACT FREEZE (docs only; may be uncommitted):

```yaml
Source HEAD before freeze: fcafbba8c327602c6e44d421bc81d7862068923d
HEAD commit: "docs: finalize m5 milestone"
last bugfix commit: 3830754dede13ea80b270f02cb68d5fb61377f69
ORD-001..ORD-022: COMPLETE
ORDER-059..ORDER-083: COMPLETE
Demo M5: COMPLETE
M5: COMPLETE
PRE-M6 AUDIT: COMPLETE
M6 CONTRACT FREEZE: COMPLETE
M6: NOT STARTED
next step: NUM-001 when explicitly authorized
```

A documentation-only freeze does not mean M6 has begun. No production code or test code was changed by FREEZE-A/B.
