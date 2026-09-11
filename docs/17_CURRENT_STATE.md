# Cassa — Current State

## 1. Purpose of this document

This document is the operational handoff for a coding agent starting without the prior conversation history. It records the verified repository checkpoint, completed scope, frozen decisions, known regressions, and the exact boundary of the next task.

The normative sources remain, in precedence order, `AGENTS.md`, `docs/00_SOURCE_OF_TRUTH.md`, the product/business documents, the specialist specifications, the test plan, and the backlog. This file summarizes the current implementation state; it does not replace those sources.

## 2. Last approved production checkpoint

Verified on 2026-09-11:

```yaml
branch: main
production checkpoint: a24627e49ecc3da1c171a8f266b9089e0ed683c7
commit: "feat: split aggregated pizza atomically"
last completed implementation task: ORD-019
ORD-001..ORD-019: COMPLETE
ORD-020: NOT STARTED
origin/main: synchronized
```

The new agent must verify that `a24627e49ecc3da1c171a8f266b9089e0ed683c7` remains the last approved production implementation checkpoint. Do not rewrite history, and keep each approved backlog task isolated.

## 3. Current milestone

```yaml
M0 Bootstrap: COMPLETE
M1 Core/domain: COMPLETE
M2 Room: COMPLETE
M3 Menu/search/manual admin: COMPLETE
M4 ODS import: COMPLETE
M5 Draft order core: IN PROGRESS
```

Within M5, `ORD-001` through `ORD-019` are complete. `ORD-020` is the next task and has not been started.

## 4. Completed tasks

- `APP-001..005`: Android/Compose bootstrap, dependencies, package structure, Hilt, and navigation shell.
- `CORE-001..008`: money, text normalization, clock, business date, domain models, pricing, merge policy, and order number formatting.
- `DB-001..009`: catalog/order/settings entities, relations, DAO/read models, repository foundation, single-draft invariant, production `CassaDatabase`, and exported schema baseline.
- `MENU-001..008`: logical catalog CRUD, reactive catalog flows, deterministic product search, menu UI, and product/addition editors.
- `ODS-001..011`: SAF picker, structural ODS parsing, sheet detection, row/price parsing, validation, planning, preview, atomic Room commit, and flag preservation.
- `ORD-001..019`: draft lifecycle/recovery/conflict handling, order screen, catalog search/actions, persisted quick-add/list/detail, additions/removals, pricing variants, custom-line behavior, the aggregated-pizza edit-scope prompt, and the atomic `MODIFICA UNA` split.

Relevant additional regression checkpoint:

- `8d4810173847d47afe587f9d189d7b0b8a12b3b7`: safe handling of trailing LibreOffice repeated padding in ODS files.

Tasks after `ORD-019` are not complete unless a later checkpoint explicitly records otherwise.

## 5. Current next task

```yaml
task: ORD-020
title: Remove line/change quantity
priority: P0
status: NOT STARTED
milestone: M5 — Draft order core
```

Read the exact contract again before coding. `docs/10_IMPLEMENTATION_BACKLOG.md` names the task, while `docs/02_BUSINESS_RULES.md`, `docs/03_UX_UI_FLOWS.md`, `docs/05_DATABASE_SCHEMA.md`, `docs/09_TEST_PLAN.md`, and `docs/16_TRACEABILITY_MATRIX.md` define its supported behavior and tests. Do not invent requirements that those documents do not state.

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

Frozen behavior through `ORD-019`:

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

### Non-frozen note — `MODIFY_ONE` plus quantity change

Current conservative behavior: when the edit scope is `MODIFY_ONE`, a simultaneous quantity change is rejected instead of inventing an unspecified semantics.

This is **not** a frozen business rule. Do not treat it as a permanent contract, and do not change it until `ORD-020` or another normative source defines the case.

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

Some edge cases are covered by automated tests but were not necessarily repeated manually. Do not describe an automated check as a manual hardware check. In particular, `ORDER-012` transaction rollback is covered by automation and was not provoked manually on the device.

## 14. Deferred/manual checks still open

- The complete M5 demo (complex draft, process termination, and identical recovery) remains pending until M5 is complete.
- A dedicated manual comparison of both `automaticExtrasPricing=true` and `false` paths was deferred; these paths have automated coverage and must remain green.
- Acceptance, numbering, archive, duplicate, and final printing flows belong to future milestones and have not been validated as completed features.
- Physical NETUM printer calibration remains open: pairing, width, code page, euro/accent rendering, feed, reconnect, interrupted-print semantics, and repeated-print stability.
- Hardware items in the checklist of `docs/09_TEST_PLAN.md` and `docs/07_PRINTING_SPEC.md` must not be marked complete without real printer validation.

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

Do not begin `ORD-020` while documenting or revisiting `ORD-019`.

## 17. Rules for the next coding agent

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

## 18. Verification baseline

Latest verified `ORD-019` baseline:

```yaml
./gradlew test: PASS — 323 tests
./gradlew assembleDebug: PASS
./gradlew assembleDebugAndroidTest: PASS
./gradlew connectedDebugAndroidTest: PASS — 55 tests on Samsung SM-S931B
./gradlew installDebug: PASS
device: Samsung SM-S931B
app data cleared: NO
```

Documented repository state after the approved `ORD-019` production commit:

```yaml
HEAD (production): a24627e49ecc3da1c171a8f266b9089e0ed683c7
HEAD commit: "feat: split aggregated pizza atomically"
last approved production implementation checkpoint: a24627e49ecc3da1c171a8f266b9089e0ed683c7
last completed production task: ORD-019
ORD-001..ORD-019: COMPLETE
ORD-020: NOT STARTED
next task: ORD-020 — Remove line/change quantity [P0]
```

A later documentation-only update of this handoff file may leave `HEAD` ahead of the production checkpoint without meaning that any production work after `ORD-019` has begun. No production code or test file was changed while creating or updating this handoff document.
