# Cassa — Current State

## 1. Purpose of this document

This document is the operational handoff for a coding agent starting without the prior conversation history. It records the verified repository checkpoint, completed scope, frozen decisions, known regressions, and the exact boundary of the next task.

The normative sources remain, in precedence order, `AGENTS.md`, `docs/00_SOURCE_OF_TRUTH.md`, the product/business documents, the specialist specifications, the test plan, and the backlog. This file summarizes the current implementation state; it does not replace those sources.

## 2. Last approved production checkpoint

Verified on 2026-09-11:

```yaml
branch: main
production checkpoint: a80986b6039f520874584c926817a65691bfbcc6
commit: "feat: add aggregated pizza edit scope prompt"
last completed implementation task: ORD-018
ORD-019: NOT STARTED
origin/main: synchronized
```

`docs/17_CURRENT_STATE.md` is created after this production checkpoint. A later `HEAD` may therefore point to a documentation-only handoff commit without indicating any production implementation after `ORD-018`.

The new agent must verify that `a80986b6039f520874584c926817a65691bfbcc6` remains the last approved production implementation checkpoint. Any commits between it and the start of `ORD-019` must be exclusively documentation/handoff changes. Do not rewrite history, and keep each approved backlog task isolated.

## 3. Current milestone

```yaml
M0 Bootstrap: COMPLETE
M1 Core/domain: COMPLETE
M2 Room: COMPLETE
M3 Menu/search/manual admin: COMPLETE
M4 ODS import: COMPLETE
M5 Draft order core: IN PROGRESS
```

Within M5, `ORD-001` through `ORD-018` are complete. `ORD-019` is the next task and has not been started.

## 4. Completed tasks

- `APP-001..005`: Android/Compose bootstrap, dependencies, package structure, Hilt, and navigation shell.
- `CORE-001..008`: money, text normalization, clock, business date, domain models, pricing, merge policy, and order number formatting.
- `DB-001..009`: catalog/order/settings entities, relations, DAO/read models, repository foundation, single-draft invariant, production `CassaDatabase`, and exported schema baseline.
- `MENU-001..008`: logical catalog CRUD, reactive catalog flows, deterministic product search, menu UI, and product/addition editors.
- `ODS-001..011`: SAF picker, structural ODS parsing, sheet detection, row/price parsing, validation, planning, preview, atomic Room commit, and flag preservation.
- `ORD-001..018`: draft lifecycle/recovery/conflict handling, order screen, catalog search/actions, persisted quick-add/list/detail, additions/removals, pricing variants, custom-line behavior, and the aggregated-pizza edit-scope prompt.

Relevant additional regression checkpoint:

- `8d4810173847d47afe587f9d189d7b0b8a12b3b7`: safe handling of trailing LibreOffice repeated padding in ODS files.

Tasks after `ORD-018` are not complete unless a later checkpoint explicitly records otherwise.

## 5. Current next task

```yaml
task: ORD-019
title: Atomic split
priority: P0
status: NOT STARTED
milestone: M5 — Draft order core
```

Read the exact contract again before coding. `docs/10_IMPLEMENTATION_BACKLOG.md` names the task, while `docs/02_BUSINESS_RULES.md`, `docs/03_UX_UI_FLOWS.md`, `docs/05_DATABASE_SCHEMA.md`, `docs/09_TEST_PLAN.md`, and `docs/16_TRACEABILITY_MATRIX.md` define its supported behavior and tests.

Primary implementation surfaces likely involved, to be confirmed rather than assumed:

- `app/src/main/java/it/krpng/cassa/domain/repository/OrderRepository.kt`
- `app/src/main/java/it/krpng/cassa/domain/usecase/UpdateOrderItem.kt` or a focused split use case
- `app/src/main/java/it/krpng/cassa/data/database/dao/OrderDao.kt`
- `app/src/main/java/it/krpng/cassa/data/repository/RoomOrderRepository.kt`
- `app/src/main/java/it/krpng/cassa/feature/order/OrderItemDetailViewModel.kt`
- related JVM, Room, and Compose tests

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

Frozen behavior through `ORD-018`:

- Persisted `1x` standard pizza may receive modifiers and increase quantity after explicit apply-all confirmation.
- Persisted `1x` customized pizza may increase quantity after explicit apply-all confirmation.
- Persisted customized `2x` pizza may increase to `3x`, retaining the same row and modifiers after confirmation.
- A customized quantity greater than one remains a single `order_item` because every unit is identical.
- A standard aggregated pizza (`quantity > 1`) opens the scope prompt before personalization:
  - `MODIFICA UNA`
  - `MODIFICA TUTTE`
  - `ANNULLA`
- `MODIFICA TUTTE` updates the same row atomically, preserves quantity and `orderItemId`, and applies the customization to every unit.
- `MODIFICA UNA` records the selected scope but performs no split and no mutation yet. The split is reserved for `ORD-019`.
- `ANNULLA` performs no writes and leaves the persisted order unchanged.
- Existing customized multi-quantity rows do not use the standard-aggregated prompt.
- Until `ORD-019`, there must be no clone, split, second order item, or partial mutation for `MODIFICA UNA`.

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

Some edge cases are covered by automated tests but were not necessarily repeated manually. Do not describe an automated check as a manual hardware check.

## 14. Deferred/manual checks still open

- `ORD-019` split behavior has not been implemented or manually tested.
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
4. `MODIFICA UNA` records its explicit scope but intentionally performs no write because the required split transaction belongs to `ORD-019`.
5. `ANNULLA`, dialog dismissal, and system Back leave the database unchanged.
6. The prompt does not appear for non-pizza rows, quantity-one rows, or already customized multi-quantity pizza rows.

Relevant implementation and regression-test files:

- `app/src/main/java/it/krpng/cassa/feature/order/OrderItemDetailViewModel.kt`
- `app/src/main/java/it/krpng/cassa/feature/order/OrderItemDetailScreen.kt`
- `app/src/main/java/it/krpng/cassa/data/repository/RoomOrderRepository.kt`
- corresponding JVM and Android tests under `app/src/test` and `app/src/androidTest`

## 16. ORD-019 boundary

The backlog defines `ORD-019 [P0] Atomic split`. The business rules, UX flow, database specification, test plan, and traceability matrix support this exact boundary:

Starting state:

```text
source standard pizza order_item
quantity > 1
edit scope = MODIFICA UNA
valid requested customization
```

Required atomic result when Save is confirmed:

- verify the owning order exists and is `DRAFT`;
- verify item ownership and that source quantity is greater than one;
- obtain one logical mutation timestamp from `ClockProvider` according to existing conventions;
- decrement the source standard row by one;
- create a new `order_item` with a new UUID and `quantity = 1`;
- preserve product, printed-name, category, base-price, automatic-extras-pricing, and other required source snapshots;
- apply the requested Addition/Removal/Note/Manual-price customization only to the new row;
- preserve Addition and Removal snapshots and calculate pricing through the existing pricing primitives;
- assign deterministic `createdSequence` according to the frozen decision below;
- update `orders.updatedAt` within the same transaction;
- conserve total quantity across the two rows;
- never create a quantity-zero row or accidental duplicate;
- commit every write together or roll back to the original aggregated row on any failure.

### Frozen decision added before ORD-019 implementation — `createdSequence`

This rule is a project decision recorded before coding. It is not yet implemented.

When `MODIFICA UNA` creates a new `order_item` through the atomic split:

- the source row keeps its own `createdSequence`;
- the new row receives the next available `createdSequence` in the order;
- the new row is therefore treated as a newly inserted `order_item`;
- existing `order_item` rows are not renumbered or reordered;
- `createdSequence` must remain deterministic and unique according to existing conventions.

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

Rationale:

- `createdSequence` represents insertion order;
- the split row is a new `order_item`;
- already persisted rows must not be reindexed;
- this keeps the transaction simple and deterministic.

Tests linked jointly to `ORD-018/019` by the traceability matrix:

- `ORDER-010`: `3x` standard, Modify one -> `2x` standard plus `1x` customized.
- `ORDER-011`: `3x` standard, Modify all -> one `3x` customized row. This path is already covered by `ORD-018`.
- `ORDER-012`: transaction failure leaves the original structure intact.
- `UI-004`: critical Compose flow for `3x` standard -> Modify one.

Do not generalize split behavior to other categories or edit scenarios unless a normative document explicitly requires it. Do not begin `ORD-020` line removal/change-quantity behavior while implementing `ORD-019`.

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

Latest verified `ORD-018` baseline:

```yaml
./gradlew test: PASS — 321 tests, 0 failures, 0 errors, 0 skipped
./gradlew assembleDebug: PASS
./gradlew assembleDebugAndroidTest: PASS
./gradlew connectedDebugAndroidTest: PASS — 49 tests, 0 failures
./gradlew installDebug: PASS
device: Samsung SM-S931B
app data cleared: NO
git diff --check before ORD-018 commit: PASS
```

Documented repository state before any `ORD-019` implementation:

```yaml
HEAD (documentation): 189172e2f66fb3e3002bb55d706430a6242d4b09
HEAD commit: "docs: add project handoff checkpoint"
handoff: committed and pushed
last approved production implementation checkpoint: a80986b6039f520874584c926817a65691bfbcc6
production commit: "feat: add aggregated pizza edit scope prompt"
last completed production task: ORD-018
ORD-019: NOT STARTED
no production task after ORD-018 has been started
```

`HEAD` may point at the documentation-only handoff commit without meaning that any production work after `ORD-018` has begun. The last approved production implementation checkpoint remains `a80986b6039f520874584c926817a65691bfbcc6`. No production code or test file was changed while creating or updating this handoff document.
