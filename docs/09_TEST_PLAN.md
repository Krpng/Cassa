# 09 — Test Plan — CASSA

## 1. Strategia

Livelli:
- unit test: regole pure;
- integration test: Room/repository/transazioni;
- UI test: flussi critici;
- hardware test: Bluetooth/NETUM.

Priorità:
1. pricing;
2. businessDate;
3. numerazione;
4. merge/split;
5. Accept atomic;
6. draft recovery;
7. ODS;
8. snapshot (Accepted giornata corrente);
9. stampa;
10. daily purge / retention (RET-001).

## 2. Business date

### DATE-001
02/09 04:59 -> 01/09.

### DATE-002
02/09 05:00 -> 02/09.

### DATE-003
03/09 01:30 -> 02/09.

### DATE-004
23:59 -> stessa calendar date.

### DATE-005
Test con timezone Europe/Rome usando FakeClock.

## 3. Sequenziale

### NUM-T001
Primo = `001`.

### NUM-T002
001,002,003.

### NUM-T003
Nuova businessDate -> 001.

### NUM-T004
998,999,1000,1001.

### NUM-T005
Due Accept concorrenti sullo stesso Draft -> una sola transizione, un solo numero.

### NUM-T006 [P0] Mode switch preserves sequential resume
Default mode = `SEQUENTIAL`. Sequential state progresses. Switch `S → R`: random state progresses. Switch `R → S`: sequential resumes previous counter (no reset). Switch `S → R` again: random resumes previous `randomSeed` + `randomSeedInitialized` + `randomCycle` + `randomPosition` (no reset of either mode state).

## 4. Random

### NUM-T010
Regex `^[A-Z][0-9]{2}$`.

### NUM-T011
Primi 2600 -> 2600 distinct.

### NUM-T012
Ordine 2601 -> cycle=2, position avanza nel nuovo ciclo.

### NUM-T013
BusinessDate nuova -> state indipendente.

### NUM-T014
Restart simulato: stesso seed/position produce sequenza coerente senza duplicate.

### NUM-T015 [P0] Mode switch preserves full RANDOM state
Switch `RANDOM → SEQUENTIAL → RANDOM` non resetta lo stato RANDOM completo: `randomSeed`, `randomSeedInitialized`, `randomCycle`, `randomPosition` restano identici / proseguono correttamente (non solo `randomPosition`).

### NUM-T016 [P0] FREEZE-A algorithm
XorShift32 + Fisher–Yates: stessa `(randomSeed, randomCycle)` → stessa permutazione bit-identica; vietato usare Random/shuffle non specificati come contratto.

### NUM-T017 [P0] Index mapping
`0→A00`, `99→A99`, `100→B00`, `2599→Z99`.

### NUM-T018 [P0] Seed once
Prima necessità RANDOM con `randomSeedInitialized == false` → genera e persiste `randomSeed`, set `randomSeedInitialized = true` **una sola volta**; restart non rigenera; avanzamento ciclo **non** cambia seed.

### NUM-T019 [P0] Preview/display non consuma
Calcolare/mostrare prossimo codice senza Accept riuscito lascia `randomPosition` invariato.

### NUM-T020 [P0] Sequential-created state leaves RANDOM uninitialized
Creazione/`getOrCreate` da allocazione SEQUENTIAL → `randomSeedInitialized == false` (filler `randomSeed` ignorabile).

### NUM-T021 [P0] Sequential never initializes RANDOM seed
Allocazioni SEQUENTIAL successive **non** impostano `randomSeedInitialized = true` e **non** generano/sostituiscono un seed RANDOM autorevole.

### NUM-T022 [P0] Initialized=false ignores physical randomSeed
Con `randomSeedInitialized == false`, il valore fisico in `randomSeed` (incluso `0L` filler) **non** è usato come seed business.

### NUM-T023 [P0] Seed 0 valid when initialized
`randomSeedInitialized == true` e `randomSeed == 0L` → seed persistito **valido**; non reinterpretare come uninitialized.

### NUM-T024 [P0] Restart keeps initialized seed
Dopo inizializzazione RANDOM: stesso `randomSeed` + `randomSeedInitialized == true` dopo restart/process death.

### NUM-T025 [P0] Default numbering mode is SEQUENTIAL
`app_settings` get/create singleton → `numberingMode = SEQUENTIAL`.

### NUM-T026 [P0] Persisted numbering mode survives restart
Dopo set `RANDOM` (o `SEQUENTIAL`), nuova istanza repository/process legge lo stesso `app_settings.numberingMode`.

### NUM-T027 [P0] Mode persistence S→R and R→S
Update mode `SEQUENTIAL → RANDOM` e `RANDOM → SEQUENTIAL` persiste correttamente su `app_settings.numberingMode`.

### NUM-T028 [P0] Full sequential state preserved across mode switches
Dopo uso SEQUENTIAL, switch a RANDOM e ritorno: `nextSequentialNumber` (e campi sequential) invariati / riprendono correttamente.

### NUM-T029 [P0] Full RANDOM state preserved across mode switches
Dopo uso RANDOM, switch a SEQUENTIAL e ritorno: `randomSeed`, `randomSeedInitialized`, `randomCycle`, `randomPosition` invariati.

### NUM-T030 [P0] DRAFT unaffected by numbering mode change
Cambio settings mode non modifica contenuto/status del DRAFT aperto.

### NUM-T031 [P0] Settings mode change consumes no number
Cambio `numberingMode` non avanza `nextSequentialNumber` né `randomPosition` / cycle e non chiama seed provider.

### NUM-T032 [P0] ACCEPTED orders unaffected by mode change
Contract-ready; integration deferred to ACCEPT-003/005: cambio mode non muta ordini `ACCEPTED` (`displayNumber`, `acceptedAt`, `businessDate`, `totalCents`, snapshots).

### NUM-T033 [P0] Future AcceptOrder reads current persisted mode
Contract-ready; integration deferred to ACCEPT-003: `AcceptOrder` alloca secondo `app_settings.numberingMode` corrente al momento dell’accept.

### NUM-T034 [P0] Settings UI reflects persisted mode
`SettingsScreen` mostra selected = valore persistito; dopo save riuscito riflette il nuovo mode.

### NUM-T035 [P0] UI save failure does not invent persisted state
Se update mode fallisce: UI resta allineata alla selezione persistita precedente; nessun “optimistic permanent” locale.

## 5. Pricing

### PRICE-001
Base 700 + 200 + 200 = 1100.

### PRICE-002
Removal non sottrae.

### PRICE-003
automaticExtrasPricing=false: base invariata, charged additions 0.

### PRICE-004
manual override prende precedenza.

### PRICE-005
reset torna automatico.

### PRICE-006
qty moltiplica final unit price.

### PRICE-007
addition 0 valida.

### PRICE-008
No overflow ragionevole su quantità/prezzi; validare range UI.

## 6. Search

### SEARCH-001
startsWith nome prima.

### SEARCH-002
contains nome.

### SEARCH-003
startsWith ingrediente.

### SEARCH-004
contains ingrediente.

### SEARCH-005
case insensitive.

### SEARCH-006
accent tolerant.

### SEARCH-007
matchedIngredient valorizzato.

### SEARCH-008
inactive esclusi.

## 7. Merge

### ORDER-001
2 quick add Margherita -> 2x.

### ORDER-002
3 Coca -> 3x.

### ORDER-003
2 personalizzate separate identiche -> 2 righe.

### ORDER-004
custom qty=2 -> una riga 2x.

### ORDER-005
nota rende pizza customized.

### ORDER-006
manual price rende pizza customized.

## 8. Split

### ORDER-010
3x standard, modifica una -> 2x standard +1 custom.

### ORDER-011
3x standard, modifica tutte -> 3x custom.

### ORDER-012
Failure durante transaction -> struttura originale.

## 8b. Remove line / change quantity (ORD-020)

### ORDER-013
Lista ordine: standard `2x` → `[+]` → `3x` sullo stesso `orderItemId`, stessa `createdSequence`, stessi snapshot; unit price invariato.

### ORDER-014
Lista ordine: standard `2x` → `[-]` → `1x` sullo stesso `orderItemId`; nessuna eliminazione.

### ORDER-015
Lista ordine: `quantity = 1` → `[-]` disabilitato/non azionabile → nessuna mutazione; la riga resta.

### ORDER-016
Lista ordine: riga customized cambia quantità → stesse Addition/Removal/Note/manual price/snapshot/`createdSequence`; cambia solo `quantity`; `finalUnitPrice` invariato; `lineTotal = finalUnitPrice * quantity`.

### ORDER-017
`RIMUOVI` su riga standard → conferma → riga assente; `orders` resta; `updatedAt` aggiornato.

### ORDER-018
`RIMUOVI` su riga customized con Addition e Removal → conferma → riga e child assenti; nessun orphan in `order_item_additions` / `order_item_removals`.

### ORDER-019
Dialog `Rimuovere questa riga?` → `ANNULLA` → nessuna scrittura; riga e children invariati.

### ORDER-020
`RIMUOVI` sull'unica riga del DRAFT → conferma → DRAFT esistente ma vuoto; la row `orders` non viene eliminata automaticamente.

### ORDER-021
`changeQuantity` / `removeOrderItem` con item di un altro order → ownership failure; nessuna scrittura.

### ORDER-022
Mutation ORD-020 su order `ACCEPTED` → rifiutata; struttura invariata.

### ORDER-023
Ogni mutation ORD-020 riuscita aggiorna `orders.updatedAt` tramite `ClockProvider`.

### ORDER-024
Dopo `RIMUOVI` confermato non restano child orphan per l'item eliminato.

### ORDER-025
Cambio quantità o rimozione persistiti → kill/reopen processo → stesso stato Room (DRAFT, items, children, prezzi).

## 8c. General note (ORD-021)

### ORDER-026
DRAFT nuovo/senza nota → `orders.generalNote` è `null`; l'editor `NOTA ORDINE` si inizializza vuoto.

### ORDER-027
`null` → testo non blank → `SALVA NOTA` → `generalNote` persistito uguale al testo trim esterno; `updatedAt` aggiornato.

### ORDER-028
Testo A persistito → modifica editor a testo B → `SALVA NOTA` → `generalNote = B` (dopo trim esterno se applicabile).

### ORDER-029
Testo persistito → editor blank/whitespace → `SALVA NOTA` → `generalNote = null`.

### ORDER-030
Input con spazi esterni (`"   consegna dopo le 21   "`) → `SALVA NOTA` → persistito `"consegna dopo le 21"`; spazi interni e line break significativi preservati.

### ORDER-031
Nota multilinea con line break interni → `SALVA NOTA` → stessi line break persistiti (solo trim esterno).

### ORDER-032
Dopo `SALVA NOTA` riuscito → Home → riapri DRAFT / kill-reopen processo → stessa `generalNote` da Room.

### ORDER-033
`SALVA NOTA` riuscito aggiorna `orders.updatedAt` tramite `ClockProvider` / FakeClock (non system clock diretto).

### ORDER-034
Mutation general note su order inesistente → order not found; nessuna scrittura.

### ORDER-035
Mutation general note su order `ACCEPTED` → rifiutata a repository/domain; `generalNote` e struttura invariati.

### ORDER-036
`orders.generalNote = "Consegna alle 21"` e `order_items.note = "Ben cotta"` sullo stesso DRAFT → coesistono; salvare/modificare/cancellare una non muta l'altra.

### ORDER-037
Editor `NOTA ORDINE` dirty (testo locale non salvato) → Flow da quantity +/-, remove o modifica item → l'editor **non** viene sovrascritto dal valore persistito.

### ORDER-038
`SALVA NOTA` fallisce → testo locale resta; `generalNote` persistito invariato; errore coerente; retry possibile.

### ORDER-039
Solo `orders.generalNote` valorizzata, pizza senza Addition/Removal/item note/manual price → pizza **non** customized e **senza** highlight viola.

## 8d. Totale live DRAFT (ORD-022)

### ORDER-040
DRAFT senza item → UI mostra `TOTALE` `€0,00`; totale resta visibile.

### ORDER-041
Una sola riga standard persistita → `orderTotalCents = finalUnitPriceCents * quantity` di quella riga; UI allineata.

### ORDER-042
Più righe persistite → `orderTotalCents = sum(lineTotalCents)`; UI allineata.

### ORDER-043
Quantity `+` / `-` persistita → totale live aggiornato con i nuovi `lineTotalCents`; nessun ricalcolo unitario dal catalogo.

### ORDER-044
`RIMUOVI` confermato sull'unica riga → DRAFT vuoto con `TOTALE` `€0,00`.

### ORDER-045
Quick add persistito → totale live aggiornato includendo la nuova riga (o qty incrementata) dallo stato Room.

### ORDER-046
Addition salvata con `automaticExtrasPricing=true` → `finalUnitPriceCents` già aggiornato in Room; totale live = somma dei `lineTotalCents` persistiti.

### ORDER-047
Addition salvata con `automaticExtrasPricing=false` → additions charged 0 già riflesse in `finalUnitPriceCents`; totale live coerente, senza surcharge extras.

### ORDER-048
Manual price salvato (`manualUnitPriceCents != null`) → totale live usa `finalUnitPriceCents` persistito (override), non il prezzo automatico/catalogo.

### ORDER-049
Manual price `€0` (`manualUnitPriceCents = 0`) → override valido; `lineTotalCents = 0` per quella riga; totale live aggiornato di conseguenza.

### ORDER-050
Reset manual price salvato → `finalUnitPriceCents` torna al pricing automatico persistito; totale live aggiornato di conseguenza.

### ORDER-051
Atomic split `MODIFICA UNA` → due righe persistite; totale live = somma dei rispettivi `lineTotalCents`; nessuna logica speciale di split nel calcolo totale.

### ORDER-052
Solo item note salvata (stesso prezzo/qty) → totale invariato.

### ORDER-053
`generalNote` salvata / cancellata → totale invariato (`generalNote` non è input di pricing).

### ORDER-054
Editor/form dirty non salvato (item detail o `NOTA ORDINE`) → totale live resta quello derivato dallo stato Room corrente; nessun optimistic total.

### ORDER-055
Dopo persistenza di un item, cambio prezzo/nome nel catalogo live → totale DRAFT invariato (usa snapshot/`finalUnitPriceCents` persistiti, non il menu corrente).

### ORDER-056
Totale live calcolato da item persistiti → kill/reopen o Home → riapri DRAFT → stesso totale derivato dallo stesso stato Room (senza dipendere da `orders.totalCents` come source of truth DRAFT).

### ORDER-057
`finalUnitPriceCents * quantity` oltre il range `Money` supportato → `AmountOverflow` / errore equivalente; nessun totale numericamente corrotto/wraparound.

### ORDER-058
Somma dei `lineTotalCents` oltre il range `Money` supportato → `AmountOverflow` / errore equivalente; nessun totale numericamente corrotto/wraparound.

## 8e. Compact order workspace (M5 UX refinement)

Mini-task UX: liberare spazio verticale sulla schermata DRAFT spostando nota e ricerca fuori dalla shell principale. Nessuna modifica a ORD-019/020/021 persistence semantics / ORD-022 total / search algorithm / quick-add business / highlight / Home-recovery.

### ORDER-059
Schermata principale DRAFT: assente editor inline `NOTA ORDINE` e assente pulsante inline `SALVA NOTA`.

### ORDER-060
Schermata principale DRAFT: assente campo ricerca inline; presenti header `NOTE` / `CERCA` / `INDIETRO`, `ORDINE CORRENTE`, categorie, catalogo, sticky `TOTALE`.

### ORDER-061
Tap `NOTE` → apre overlay/modal `NOTA ORDINE` separato dalla schermata ordine.

### ORDER-062
Apertura overlay → editor inizializzato dal `generalNote` persistito (vuoto se `null`).

### ORDER-063
Overlay dirty → `ANNULLA` chiude senza mutation; `generalNote` persistito invariato; modifiche locali scartate.

### ORDER-064
Overlay → `SALVA` riuscito → `UpdateGeneralNote` / normalizzazione ORD-021; overlay chiuso; ritorno all'ordine con nota persistita.

### ORDER-065
Overlay → `SALVA` fallisce → overlay resta aperto; testo locale preservato; errore visibile; retry possibile; persistito invariato.

### ORDER-066
Overlay dirty + Flow da quantity/remove/item → editor locale **non** sovrascritto (contratto ORD-021 dirty).

### ORDER-067
Tap `CERCA` → apre vista dedicata ricerca (campo + risultati); senza lista ordine, nota, sticky totale.

### ORDER-068
Da vista `CERCA`, `INDIETRO` → torna alla schermata ordine dello stesso DRAFT.

### ORDER-069
Stessa query nella vista `CERCA` (con filtro search `TUTTI`) → stessi risultati/ranking del motore di ricerca esistente (nome+ingredienti, case/accent tolerant, solo active).

### ORDER-070
Quick-add `+` da risultato `CERCA` → persiste col flow ORD esistente; la vista `CERCA` resta aperta.

### ORDER-071
Dopo quick-add da `CERCA` → `INDIETRO` all'ordine → prodotto/qty aggiornati nello stato Room; sticky `TOTALE` coerente (ORD-022).

### ORDER-072
Schermata principale dopo refinement: controlli ORD-020 (`+/-`, `RIMUOVI`, tap riga) invariati nel comportamento.

### ORDER-073
Custom pizza highlight (viola) invariato dopo refinement.

### ORDER-074
Header `NOTE` / `CERCA` / `INDIETRO`: touch target `>= 48dp` e content description accessibili distinti.

### ORDER-075
Vista `CERCA`: presenti filtri `TUTTI | PIZZE | FRITTURA | BIBITE` subito sotto il campo ricerca e sopra i risultati (stile coerente con MAIN).

### ORDER-076
Apertura `CERCA` → search category filter default = `TUTTI` (indipendente dal filtro categoria corrente di MAIN).

### ORDER-077
In `CERCA`, filtro `PIZZE` → solo prodotti `category == PIZZA` (rispetto a query corrente / blank).

### ORDER-078
In `CERCA`, filtro `FRITTURA` → solo prodotti `category == FRITTURA`.

### ORDER-079
In `CERCA`, filtro `BIBITE` → solo prodotti `category == BIBITA`.

### ORDER-080
Query non vuota + filtro categoria search (es. `pomodoro` + `PIZZE`) → intersezione AND: match ricerca esistente **e** categoria selezionata; ranking/normalizzazione del motore invariati.

### ORDER-081
Cambio filtro nella vista `CERCA` → filtro categoria della schermata MAIN **invariato** al ritorno (`INDIETRO`).

### ORDER-082
Quick-add da `CERCA` → query e search category filter restano invariati; vista ricerca resta aperta.

### ORDER-083
Chiudi `CERCA` e riapri → search category filter riparte da `TUTTI` (nessuna persistenza DB/DataStore dello stato filtro search).

## 9. Draft

### DRAFT-001
Persist order, close/recreate process -> recovery.

### DRAFT-002
Riprendi conserva children e manual price.

### DRAFT-003
Elimina con conferma.

### DRAFT-004
Second draft blocked by unique slot.

### DRAFT-005
Empty draft not shown recovery.

### DRAFT-006
Home non elimina draft.

## 10. Accept / Preview (FREEZE-B)

### ACCEPT-T001
DRAFT non vuoto -> Accepted con numero/date/total.

### ACCEPT-T002
Empty draft -> rejected (e `COMPLETA` disabled: non apre preview).

### ACCEPT-T003
Accepted -> second Accept rejected.

### ACCEPT-T004
draftSlot becomes null.

### ACCEPT-T005
acceptedAt/businessDate use same logical now.

### ACCEPT-T006
UI remains Accepted screen after success.

### ACCEPT-T007
Accepted update methods rejected (repository/use case).

### ACCEPT-T008 [P0] Preview zero writes
Aprire `COMPLETA` / chiudere preview (INDIETRO/Back) → nessuna mutation Room.

### ACCEPT-T009 [P0] Preview zero number consume
Aprire preview → `numbering_state` invariato (SEQUENTIAL e RANDOM).

### ACCEPT-T010 [P0] Preview category order
Solo categorie non vuote nell'ordine `PIZZE` → `FRITTURA` → `BIBITE`.

### ACCEPT-T011 [P0] Preview within-category order
Dentro categoria: `createdSequence ASC` (no alpha, no catalog re-read).

### ACCEPT-T012 [P0] Preview total ignores stale `orders.totalCents`
Totale preview = `SUM(finalUnitPriceCents * quantity)` da items persistiti.

### ACCEPT-T013 [P0] Accept recalculates + snapshots total
`AcceptOrder` scrive `totalCents` dal ricalcolo checked items; non dal totale DRAFT stale.

### ACCEPT-T014 [P0] Accept atomic with numbering
Fallimento qualsiasi passo → DRAFT + numbering invariati; successo → ACCEPTED + numbering avanzato una volta.

### ACCEPT-T015 [P0] Accept failure consumes zero number
Precondizione fallita / overflow → zero consume.

### ACCEPT-T016 [P0] Overflow blocks accept
Overflow Money → errore esplicito, zero write, zero number.

### ACCEPT-T017 [P0] Concurrent / double accept
Due Accept sullo stesso DRAFT → un solo ACCEPTED, un solo numero; seconda `AlreadyAccepted`/`OrderNotDraft`.

### ACCEPT-T018 [P0] Process death mid-accept
Dopo restart solo stato A (DRAFT+numbering invariati) o B (ACCEPTED+numbering avanzato una volta); vietato parziale.

### ACCEPT-T019 [P0] Post-accept CTAs M6
Dopo successo: CTAs accept assenti; `STAMPA` visibile ma disabled; `HOME` ok; `NUOVO ORDINE` apre fresh DRAFT (non riusa ACCEPTED).

### ACCEPT-T020 [P1] Empty DRAFT COMPLETA
`COMPLETA` disabled su DRAFT senza items; tap non apre preview.

## 11. Snapshot

### SNAP-001
Menu price changes after Accepted -> historical price unchanged.

### SNAP-002
Product name/printedName changes -> reprint old name.

### SNAP-003
Addition price changes -> historical reprint old price.

### SNAP-004
Deactivate product -> historical order readable.

## 12. Duplicate (ARCH-006 / D-046 COMPLETE + ARCH-007 / D-047 COMPLETE)

> Scope: current-day ACCEPTED → new DRAFT. ARCH-006 path (no active DRAFT) COMPLETE (`67be68b`). Conflict resolution UX = ARCH-007 COMPLETE (`fcd614b`; contract `86baf12`). DUP-005 reject path remains ARCH-006 repository contract; UI resolution = ARCH-007 dialog.

Baseline M7 close: DUP-001..005 PASS; ARCH-T027..033 PASS; ARCH7-T001..T015 PASS; Manual ARCH-007 5/5 PASS; JVM 506 PASS; connected 160 PASS.

### DUP-001 [ARCH-006]
Exact lines / product snapshots / qty / additions / removals / item notes / `createdSequence` exact copy; new item+child UUIDs.

### DUP-002 [ARCH-006]
Prices/customizations copied including `manualUnitPriceCents = 0`; no catalog reread; no reprice; auto-extras snapshot + finalUnitPrice COPY.

### DUP-003 [ARCH-006]
New order identity (DRAFT, null display/acceptedAt/businessDate) + `sourceOrderId` = source ACCEPTED id; `productId`/`additionId`/`ingredientId` COPY exact.

### DUP-004 [ARCH-006]
Source ACCEPTED unchanged (snapshots/children/`updatedAt`).

### DUP-005 [ARCH-006; UI resolution = ARCH-007]
Existing active DRAFT (incl. empty persisted) → typed conflict + zero writes (no partial DRAFT; no delete/replace in ARCH-006).

### ARCH-T027 [ARCH-006]
Source guard: only current-day ACCEPTED; DRAFT / missing / old ACCEPTED → reject + zero writes.

### ARCH-T028 [ARCH-006]
`generalNote` COPY (null stays null; no extra trim on duplicate).

### ARCH-T029 [ARCH-006]
`createdSequence` exact COPY (no renumber; relative order preserved).

### ARCH-T030 [ARCH-006]
`productId` / `additionId` / `ingredientId` COPY exact; no catalog name resolve.

### ARCH-T031 [ARCH-006]
Transaction failure → full rollback; no partial DRAFT/items.

### ARCH-T032 [ARCH-006]
Success → open new DRAFT in standard NewOrder UI (not detail, not Home).

### ARCH-T033 [ARCH-006]
On valid detail: `NUOVO ORDINE DA QUESTO` VISIBLE+ENABLED; `STAMPA` remains VISIBLE+DISABLED.

### ARCH7-T001 [ARCH-007]
Active DRAFT → conflict dialog visible (title/actions frozen).

### ARCH7-T002 [ARCH-007]
ANNULLA → zero writes + stay on accepted detail.

### ARCH7-T003 [ARCH-007]
RIPRENDI → existing DRAFT opened in NewOrder; zero writes.

### ARCH7-T004 [ARCH-007]
RIPRENDI works with empty persisted DRAFT.

### ARCH7-T005 [ARCH-007]
ELIMINA DRAFT E DUPLICA → old DRAFT deleted + new duplicated DRAFT created (ONE Room txn).

### ARCH7-T006 [ARCH-007]
Source ACCEPTED remains immutable after replace.

### ARCH7-T007 [ARCH-007]
New DRAFT `sourceOrderId` = source Accepted id.

### ARCH7-T008 [ARCH-007]
Faithful snapshot/pricing copy incl. manual €0 (ARCH-006 semantics preserved).

### ARCH7-T009 [ARCH-007]
Old DRAFT with items fully removed including child rows.

### ARCH7-T010 [ARCH-007]
Empty old DRAFT replaced correctly (DRAFT row deleted; new DRAFT inserted).

### ARCH7-T011 [ARCH-007]
Failure during duplication → full rollback; old DRAFT preserved intact.

### ARCH7-T012 [ARCH-007]
No partial new DRAFT after failure.

### ARCH7-T013 [ARCH-007]
Stale/invalid source (or DraftMissing / DraftChanged) → typed error + no writes that drop the old DRAFT.

### ARCH7-T014 [ARCH-007]
Replace success → navigate to standard NewOrder(`newDraftId`).

### ARCH7-T015 [ARCH-007]
No catalog reread / no reprice on replace path.

## 13. Today / Accepted list (current business day only)

### ARCH-T001
Today = current businessDate.

### ARCH-T002
02:00 belongs previous day (businessDate cutoff).

### ARCH-T003
DESC acceptedAt.

### ARCH-T004 — OBSOLETE
> **CANCELLED BY PRODUCT SCOPE CHANGE (M7).** same displayNumber on different days — archivio cross-day rimosso.

### ARCH-T005 — OBSOLETE
> **CANCELLED BY PRODUCT SCOPE CHANGE (M7).** random same code cycle across retained multi-day archive — non applicabile senza archivio storico.

## 13b. Daily purge / retention (RET-001)

### RET-T001
old ACCEPTED (`businessDate < currentBusinessDate`) is deleted.

### RET-T002
current-businessDate ACCEPTED is preserved.

### RET-T003
active DRAFT is preserved across purge / 05:00 boundary.

### RET-T004
removals/items/additions of deleted orders leave no orphan rows.

### RET-T005
`numbering_state` is unchanged by purge.

### RET-T006
repeated purge is idempotent.

### RET-T007
04:59 and 05:00 resolve different `currentBusinessDate` correctly for purge predicate.

### RET-T008
draft created before 05:00 and accepted after 05:00 receives the new businessDate.

## 13c. Current-day Accepted detail (ARCH-004) — D-045

> Test ID dedicati. Non riusare ARCH-T001..003 (lista Today).

### ARCH-T010
Today row tap → apre Accepted detail dello stesso `orderId`.

### ARCH-T011
`ACCEPTED` current-day → detail Content (header + sezioni snapshot).

### ARCH-T012
`ACCEPTED` con `businessDate` precedente → Unavailable / Not found.

### ARCH-T013
`DRAFT` → Unavailable / Not found (non renderizzato come Accepted detail).

### ARCH-T014
missing / purged mid-open (RET-001) → Unavailable; no crash; no stale data.

### ARCH-T015
snapshot-only: cambio catalogo dopo accept non altera name/prezzi/modifiche mostrate.

### ARCH-T016
`generalNote` Accepted mostrata read-only quando presente.

### ARCH-T017
item note snapshot mostrata read-only quando presente.

### ARCH-T018
additions / removals = snapshot Accepted (ordine displayOrder).

### ARCH-T019
quantity + finalUnitPrice + line total (`finalUnitPriceCents * quantity`) da snapshot.

### ARCH-T020
sezioni PIZZE → FRITTURA → BIBITE; vuote omesse; within: `createdSequence ASC` (`AcceptancePreviewOrdering`).

### ARCH-T021
controlli edit assenti (`+/-`, `RIMUOVI`, edit note/additions/price, `ACCETTA`, `COMPLETA`).

### ARCH-T022
CTA `STAMPA` VISIBLE + DISABLED (pre-PRINT).

### ARCH-T023
label `RISTAMPA` assente.

### ARCH-T024
`NUOVO ORDINE DA QUESTO` assente **finché solo ARCH-004** (attivata da ARCH-006 / ARCH-T033).

### ARCH-T025
`INDIETRO` e System Back → `ORDINI DI OGGI`.

### ARCH-T026
`HOME` → Home (senza riaprire preview/edit/accept).

## 14. ODS

### ODS-001
Valid file import.

### ODS-002
Prezzo Sala invalid ignored.

### ODS-003
currency strings parse to cents.

### ODS-004
partial product row block.

### ODS-005
invalid product price block.

### ODS-006
unknown category block.

### ODS-007
0 addition valid.

### ODS-008
existing product update.

### ODS-009
absent product remains.

### ODS-010
rollback preserves DB.

### ODS-011
sheet names changed -> still detect.

### ODS-012
empty third sheet ignored.

### ODS-013
duplicate normalized product block.

### ODS-014
duplicate addition block.

### ODS-015
printedName blank fallback.

### ODS-016
ingredient list trim/dedup/order.

### ODS-017
column ingredients present blank -> clear.

### ODS-018
column ingredients absent -> preserve update.

### ODS-019
existing automaticExtrasPricing preserved.

### ODS-020
existing active preserved.

### ODS-021
repeated rows/cells ODS handled safely.

## 15. Printing formatter

### PRINT-T001
Draft header BOZZA, no number.

### PRINT-T002
Final header number, no date/time.

### PRINT-T003
section order P/F/B.

### PRINT-T004
empty category omitted.

### PRINT-T005
charged additions price shown in Detailed.

### PRINT-T006
removal no price.

### PRINT-T007
note wrap.

### PRINT-T008
total correct.

### PRINT-T009
one print request.

### PRINT-T010
manual price -> additions no price breakdown.

### PRINT-T011
automaticExtrasPricing false -> additions no price.

### PRINT-T012
TotalOnly no line prices.

### PRINT-T013
printedName snapshot used.

### PRINT-T014
long names wrap.

## 16. Printer service

### PRINT-T020
Not configured -> typed error.

### PRINT-T021
Bluetooth disabled.

### PRINT-T022
Connection lost, Accepted remains.

### PRINT-T022b (PRINT-024 / D-071)
`ConnectionLost` → uncertain microcopy:
- DRAFT: `Stampa non confermata. Controlla lo scontrino prima di stampare di nuovo.`
- manual ACCEPTED: same base copy
- PRINT-022 auto: `Ordine accettato. Stampa non confermata. Controlla lo scontrino prima di stampare di nuovo.`
Definite errors (BluetoothDisabled / PrinterNotConfigured / PermissionDenied / ConnectionFailed) retain existing wording. No auto-retry. Business state unchanged.

### PRINT-T023
Retry same number.

### PRINT-T024
Mutex blocks concurrent.

### PRINT-T025
Draft retry still no number.

### PRINT-T026
Fake timeout.

### PRINT-T027
Test print doesn't create order.

## 17. UI critical flows

Automatizzare pochi flussi robusti.

### UI-001
Home -> New -> quick add -> complete -> accept.

### UI-002
Search ingredient -> product match reason.

### UI-003
Customize pizza -> addition/removal/note -> save.

### UI-004
3x standard -> modify one.

### UI-005
Restart -> recover draft.

### UI-006
Ordini di oggi → dettaglio Accepted giornata corrente (ARCH-004 / ARCH-T010..026).
Duplicazione da detail: ARCH-006 / D-046 COMPLETE (DUP-001..005, ARCH-T027..033) + ARCH-007 / D-047 COMPLETE (ARCH7-T001..T015). Ex “Archive → detail → duplicate”: **OBSOLETE**.

### UI-007
Import -> preview -> confirm.

### UI-008
Print error -> retry/close.

### UI-009
Preview DRAFT -> `NUOVO ORDINE` -> single-draft conflict; nessun secondo DRAFT viene creato senza eliminazione esplicita.

## 18. Hardware NETUM

Manual:
- [ ] Android pairing
- [ ] selection in app
- [ ] connect
- [ ] print
- [ ] reconnect
- [ ] 80mm alignment
- [ ] € 
- [ ] à è ì ò ù
- [ ] apostrophes
- [ ] long names
- [ ] number large
- [ ] feed
- [ ] 10 consecutive prints
- [ ] printer off
- [ ] reconnect/retry
- [ ] connection loss behavior

## 19. Definition of Passed

PASS:
- expected result obtained;
- no unintended DB changes;
- no leaked additional number;
- no crash;
- no regression in linked tests.

Milestone complete:
- all P0/P1 linked tests green;
- build successful;
- manual demo target completed.

## 20. Test fixtures

Creare builders:
- StandardPizza;
- NoAutoExtrasPizza;
- Drink;
- Fry;
- AdditionFree;
- AdditionPaid;
- DraftOrderBuilder;
- AcceptedOrderBuilder;
- FakeClock;
- FakePrinter.

Evitare test che dipendono dal menu reale dell'utente, tranne test dedicati di import sample.

## 21. Migration tests

Ogni DB version >1:
- schema export;
- migration test da versione precedente;
- dati ordini/snapshot preservati.
