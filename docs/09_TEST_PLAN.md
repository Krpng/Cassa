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

## 10. Accept

### ACCEPT-T001
DRAFT non vuoto -> Accepted con numero/date/total.

### ACCEPT-T002
Empty draft -> rejected.

### ACCEPT-T003
Accepted -> second Accept rejected.

### ACCEPT-T004
draftSlot becomes null.

### ACCEPT-T005
acceptedAt/businessDate use same logical now.

### ACCEPT-T006
UI remains Accepted screen after success.

### ACCEPT-T007
Accepted update methods rejected.

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
