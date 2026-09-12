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
8. snapshot/archive;
9. stampa.

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

### NUM-T006
Cambio a Random e ritorno -> sequenziale riprende.

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

### NUM-T015
Switch Sequential/Random non resetta randomPosition.

### NUM-T016 [P0] FREEZE-A algorithm
XorShift32 + Fisher–Yates: stessa `(randomSeed, randomCycle)` → stessa permutazione bit-identica; vietato usare Random/shuffle non specificati come contratto.

### NUM-T017 [P0] Index mapping
`0→A00`, `99→A99`, `100→B00`, `2599→Z99`.

### NUM-T018 [P0] Seed once
Prima necessità RANDOM crea e persiste seed una sola volta; restart non rigenera; avanzamento ciclo **non** cambia seed.

### NUM-T019 [P0] Preview/display non consuma
Calcolare/mostrare prossimo codice senza Accept riuscito lascia `randomPosition` invariato.

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

## 12. Duplicate

### DUP-001
Exact lines/qty/modifiers/notes/prices copied.

### DUP-002
No number/date/acceptedAt copied.

### DUP-003
sourceOrderId set.

### DUP-004
Source unchanged.

### DUP-005
Draft conflict prevents second draft until resolved.

## 13. Archive

### ARCH-T001
Today = current businessDate.

### ARCH-T002
02:00 belongs previous day.

### ARCH-T003
DESC acceptedAt.

### ARCH-T004
same displayNumber on different days both returned.

### ARCH-T005
random same code cycle 1/2 distinguish via ID/time.

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
Archive -> detail -> duplicate.

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
