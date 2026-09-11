# 02 — Business Rules — CASSA

## 1. Stati ordine

Stati ammessi:
- `DRAFT`
- `ACCEPTED`

Transizione valida:

`DRAFT -> ACCEPTED`

Non esistono nel v1:
- PAID;
- COMPLETED distinto da ACCEPTED;
- CANCELLED;
- DELETED per Accepted.

### Immutabilità
Dopo `ACCEPTED`:
- non modificare righe;
- non modificare quantità;
- non modificare prezzi;
- non modificare note;
- non ricalcolare da menu corrente;
- consentire solo lettura, ristampa e duplicazione.

La regola deve essere enforced nel dominio/repository, non solo nascondendo pulsanti.

## 2. Un solo DRAFT

Deve esistere al massimo un DRAFT non vuoto.

Home:
- se esiste: mostra `ORDINE IN CORSO` + `RIPRENDI ORDINE`.

Avvio app:
- DRAFT non vuoto: `RIPRENDI` / `ELIMINA`.
- DRAFT vuoto: può essere eliminato automaticamente e non deve bloccare.

Nuovo ordine con DRAFT esistente:
- `RIPRENDI`;
- `ELIMINA E CREA NUOVO`;
- `ANNULLA`.

Eliminazione DRAFT non vuoto:
- richiede conferma.

Duplicazione ordine storico con DRAFT esistente:
- applicare lo stesso conflitto: non creare un secondo DRAFT.

## 3. Persistenza DRAFT

Ogni modifica significativa deve essere persistita subito:
- aggiunta prodotto;
- rimozione riga ordine;
- quantità (inclusi `+`/`-` dalla lista ordine);
- aggiunte;
- rimozioni ingredienti;
- nota di riga (`order_items.note`);
- prezzo manuale;
- reset prezzo.

Room è la source of truth.

Non mantenere un carrello solo in memoria in attesa di un "salva".

I controlli quantità `+`/`-` della lista ordine persistono immediatamente: non richiedono apertura del dettaglio né `SALVA`.

### Eccezione ORD-021 — nota generale ordine

La nota generale (`orders.generalNote`) **non** usa autosave per carattere né debounce.
Si persiste solo con azione esplicita `SALVA NOTA`, secondo la sezione dedicata.

## 4. Categorie

Enum dominio:
- `PIZZA`
- `FRITTURA`
- `BIBITA`

Mapping display:
- Pizze
- Frittura
- Bibite

Le aggiunte non sono una categoria prodotto.

## 5. Ricerca

Query normalizzata:
- trim;
- collapse spazi;
- case-insensitive;
- accent-tolerant.

Priorità:
1. nome prodotto `startsWith`;
2. nome prodotto `contains`;
3. ingrediente `startsWith`;
4. ingrediente `contains`.

Tie-breaker:
- nome normalizzato alfabetico oppure ordine stabile deterministico.

Se il match è per ingrediente:
- mostra `Contiene: <ingrediente>`.

Prodotti inattivi:
- non compaiono nei risultati e non possono essere aggiunti.

## 6. Quick add e tap riga

Risultato prodotto:
- tap sulla riga: apre dettagli/personalizzazione;
- pulsante `+`: aggiunge versione standard.

Quick add standard:
- pizza senza modifiche: merge su riga standard già esistente;
- Frittura: merge per prodotto standard;
- Bibita: merge per prodotto standard.

## 7. Quantità e merge

### Pizza standard
Ripetute aggiunte quick:
`1x -> 2x -> 3x`.

### Pizza personalizzata
È personalizzata se ha almeno uno:
- addition;
- removal;
- note di riga (`order_items.note` non blank);
- prezzo manuale (`manualUnitPrice != null`, incluso EUR 0,00).

`orders.generalNote` **non** rende una pizza personalizzata e non attiva l'highlight viola delle righe pizza.

Pizze personalizzate inserite separatamente:
- non vengono deduplicate, anche se identiche.

Esempio:
```text
1x Margherita - Mozzarella
1x Margherita - Mozzarella
```
resta due righe.

Se l'utente imposta quantità 2 all'interno della stessa personalizzazione:
```text
2x Margherita - Mozzarella
```
resta una riga quantità 2.

### Cambio quantità dalla lista ordine (ORD-020)

Sulla schermata ordine, ogni riga del DRAFT espone `[-] quantity [+]`.

Regole:
- `quantity` persistita deve restare `>= 1`;
- `[+]` → `quantity = quantity + 1`;
- `[-]` con `quantity > 1` → `quantity = quantity - 1`;
- `[-]` con `quantity = 1` → nessuna mutazione; il controllo è disabilitato/non azionabile;
- `quantity = 0` non è uno stato valido;
- il decremento `1 → 0` **non** significa eliminazione della riga.

Il cambio quantità opera sulla stessa `order_item`:
- stesso `orderItemId`;
- stesso `createdSequence`;
- stessi snapshot;
- per riga customized: stesse Addition, Removal, Note, manual price;
- nessuna nuova riga, nessuno split, nessun merge;
- nessun ricalcolo del prezzo unitario dal catalogo live.

Campi unitari conservati invariati:
- `baseUnitPriceCents`
- `automaticExtrasTotalCents`
- `manualUnitPriceCents`
- `finalUnitPriceCents`

`lineTotal = finalUnitPrice * quantity` con le primitive `Money`/`Long` esistenti.

Questo flusso lista è distinto dall'editor dettaglio e non usa `AggregatedPizzaEditScope`.

### Rimozione riga dalla lista ordine (ORD-020)

L'eliminazione di una riga avviene solo tramite azione esplicita `RIMUOVI`, mai tramite `quantity = 0`.

Dopo conferma:
- elimina atomicamente `order_item_additions`, `order_item_removals` e `order_item` appartenenti alla riga, secondo lo schema reale (cascade o delete espliciti dei child; nessun child orfano);
- aggiorna `orders.updatedAt` tramite `ClockProvider`;
- non elimina automaticamente la row `orders` anche se resta senza items.

Un DRAFT senza items resta un DRAFT vuoto e segue le regole Home/recovery già esistenti.

Guard obbligatori a repository/domain:
- order esistente;
- item esistente;
- item appartenente all'order;
- `order.status == DRAFT`;
- `ACCEPTED` immutabile.

### Nota generale ordine (ORD-021)

Campo: `orders.generalNote` (già previsto dallo schema). Distinto da `order_items.note`.

Semantica empty/null congelata:
- testo `null` / vuoto / solo whitespace → persistire `generalNote = null`;
- testo non vuoto → trim solo degli spazi esterni; preservare testo interno, spazi interni e line break significativi.

Esempio:
```text
"   consegna dopo le 21   " → "consegna dopo le 21"
```

Cancellazione: campo portato a blank e `SALVA NOTA` → `generalNote = null`.

MVP: nessun limite di lunghezza applicativo arbitrario (100/255/500…). Vincoli di stampa futuri appartengono alla specifica di stampa, non a ORD-021.

Salvataggio:
- solo con `SALVA NOTA` esplicito;
- vietati autosave per carattere, debounce automatico, salvataggio implicito tramite quantity/remove;
- salvataggio riuscito: persiste `generalNote`, aggiorna `orders.updatedAt` tramite `ClockProvider` (un solo timestamp logico; vietato system clock diretto), aggiorna lo stato osservato;
- il pulsante può essere disabilitato quando il valore editor è identico al valore persistito.

Guard repository/domain obbligatori:
- order esistente;
- `order.status == DRAFT`;
- `ACCEPTED` immutabile: ogni mutation diretta di `generalNote` su ACCEPTED è rifiutata anche a repository/domain, non solo in UI.

`orders.generalNote` non è input di pricing e non influenza `orderTotalCents` (vedi Totale live DRAFT ORD-022). Una general note dirty non salvata non ha alcun effetto sul totale.

Indipendenza da note riga:
- `orders.generalNote` e `order_items.note` coesistono e non si sovrascrivono;
- salvare/modificare/cancellare una non modifica l'altra.

Editor dirty vs Flow:
- il valore persistito inizializza l'editor all'apertura/caricamento;
- se l'utente ha testo locale non ancora salvato (`dirty`), una riemissione Flow da operazioni non correlate (quantity +/-, remove, modifica item) **non** deve sovrascrivere l'editor;
- dopo salvataggio riuscito: persisted == editor, `dirty = false`.

Errore salvataggio:
- testo locale resta disponibile;
- `generalNote` persistito resta invariato;
- errore coerente con i pattern esistenti;
- l'utente può riprovare;
- nessuna perdita silenziosa del testo digitato.

Recovery/reopen: dopo salvataggio riuscito, Home → riapri DRAFT mostra la stessa nota tramite Room (nessuna cache parallela).

### Totale live DRAFT (ORD-022)

Per un ordine con `status == DRAFT`, il totale live deriva **esclusivamente** dagli `order_items` persistiti in Room.

Source of truth congelata:
- `persisted order_items` = unica source of truth del live total DRAFT.

Il totale **non** deve derivare da:
- editor locale non salvato;
- form dirty;
- catalogo live;
- prezzo corrente del menu;
- valori temporanei Compose/ViewModel.

Formula (cents / `Money`):
```text
lineTotalCents = finalUnitPriceCents * quantity
orderTotalCents = sum(lineTotalCents di tutti gli order_items persistiti)
```

Usare esclusivamente `Money` / `Long` cents. Vietati `Double` e `Float`.

#### `orders.totalCents` nel DRAFT

ORD-022 **non** mantiene `orders.totalCents` sincronizzato dopo ogni mutation del DRAFT.

`orders.totalCents` **non** è la source of truth del totale live del DRAFT.

Non introdurre write aggiuntive a `orders.totalCents` per:
- quick add;
- quantity change;
- remove line;
- item edit;
- split.

Il totale DRAFT viene **derivato** dagli item persistiti.

Il futuro flusso di Acceptance ricalcolerà il totale dagli item persistiti e salverà lo snapshot definitivo in `orders.totalCents`; Acceptance, numerazione e salvataggio definitivo di `orders.totalCents` sono **fuori scope ORD-022**.

Separazione congelata:
- DRAFT live total = valore **derivato** dagli `order_items` persistiti;
- ACCEPTED `totalCents` = **snapshot** persistito (task/milestone successive).

#### Live / reactive

"Live" significa:
1. Room mutation completata;
2. Flow/read model emette nuovo stato persistito;
3. totale derivato viene ricalcolato;
4. UI aggiornata.

Nessun optimistic total basato su stato non ancora persistito.

Il totale deve reagire automaticamente a qualsiasi modifica **persistita** che cambia righe/prezzi/quantità, in particolare:
- quick add;
- quantity `+`;
- quantity `-`;
- `RIMUOVI`;
- Addition salvata;
- Removal salvata;
- manual price salvato;
- reset manual price salvato;
- `MODIFICA TUTTE` salvato;
- atomic split `MODIFICA UNA`.

Note:
- Removal ingrediente non abbassa il prezzo secondo le business rule esistenti: può causare una nuova emissione Flow, ma il totale può restare invariato;
- modifica della sola item note → totale invariato;
- modifica di `generalNote` (anche dopo `SALVA NOTA`) → totale invariato;
- editor dirty / form non salvati → totale invariato (resta calcolato dallo stato Room corrente).

#### Pricing esistente (nessuna nuova regola)

ORD-022 non introduce nuove regole di pricing. Usa esclusivamente i valori **già persistiti**:
- `finalUnitPriceCents`;
- `quantity`.

Quindi:
- `automaticExtrasPricing = true` → additions charged già riflesse in `finalUnitPriceCents`;
- `automaticExtrasPricing = false` → additions charged 0 già riflesse in `finalUnitPriceCents`;
- `manualUnitPriceCents != null` → precedence già riflessa in `finalUnitPriceCents`;
- `manualUnitPriceCents = 0` → override valido → `finalUnitPriceCents = 0` → `lineTotalCents = 0`.

ORD-022 **non** deve ricalcolare il prezzo unitario dal catalogo o dalle customization.

#### Atomic split

Dopo ORD-019, source row + new custom row sono due righe persistite. Il totale live è semplicemente la somma dei rispettivi `lineTotalCents`. Nessuna logica speciale di split dentro il calcolo totale.

#### Empty DRAFT

Un DRAFT senza item deve mostrare:
```text
TOTALE
€0,00
```

Il totale resta visibile anche nello stato vuoto. Non creare una riga prodotto fittizia e non cancellare il DRAFT.

#### Overflow

Se `finalUnitPriceCents * quantity` oppure la somma dei line total supera il range supportato dalle primitive `Money` esistenti → `AmountOverflow` / errore equivalente già usato dal dominio. Vietato overflow silenzioso/wraparound. Non mostrare un totale numericamente corrotto.

#### Fuori scope ORD-022

- `COMPLETA` / Acceptance / Preview / numerazione;
- write di `orders.totalCents` sul DRAFT;
- nuovi flussi ACCEPTED/archivio/stampa dedicati.

## 8. Modifica una / tutte

Solo caso ufficialmente richiesto:
pizza standard aggregata con `quantity > 1`.

Esempio:
`3x Margherita`.

Se l'utente vuole personalizzarla:
- `MODIFICA UNA`;
- `MODIFICA TUTTE`.

`MODIFICA UNA` deve eseguire atomicamente:
- decremento riga standard 3 -> 2;
- creazione nuova riga quantità 1;
- applicazione personalizzazione alla nuova riga.

`MODIFICA TUTTE`:
- mantiene una riga quantità 3;
- applica la personalizzazione all'intera riga.

Per Frittura/Bibita, eventuali note/prezzo manuale sono proprietà dell'intera riga aggregata nel v1.

### Fuori scope ORD-020 — `MODIFICA UNA` + cambio quantità nell'editor

Il caso editor pizza aggregata → `MODIFICA UNA` → cambio quantità nello stesso form **non** appartiene a ORD-020.

Comportamento conservativo corrente preservato: il salvataggio con quantità modificata in quello scope viene rifiutato.

Non è una business rule generale definitiva. Il `+/-` della lista ordine è un flusso differente.

## 9. Aggiunte

Disponibili solo per `PIZZA`.

Le aggiunte provengono dal catalogo `additions`.

Possono avere prezzo `0`.

Ogni addition applicata salva snapshot:
- nome;
- nome stampato risolto;
- prezzo di listino;
- prezzo addebitato.

## 10. Rimozioni

Disponibili solo per `PIZZA`.

La lista selezionabile contiene esclusivamente gli ingredienti associati a quella pizza.

Rimozione:
- non riduce il prezzo;
- viene stampata con `-`.

Ingredienti non presenti nella pizza:
- non possono essere selezionati come rimozione.

## 11. Pricing

Tutti gli importi sono centesimi interi.

### Prezzo automatico normale
Se `automaticExtrasPricing = true`:
`automaticUnitPrice = baseUnitPrice + somma(chargedAdditionPrice)`.

Per ogni addition:
`chargedPrice = listedPrice`.

### Prezzo aggiunte disattivato
Se `automaticExtrasPricing = false`:
- le aggiunte sono selezionabili;
- `chargedPrice = 0`;
- `automaticUnitPrice = baseUnitPrice`.

Non hardcodare nomi speciali.
L'operatore configura il flag nel prodotto.

Configurazione business iniziale attesa sul menu corrente:
- `Pizza fritta` -> `automaticExtrasPricing=false`;
- `Ripieno` -> `automaticExtrasPricing=false`.

Questi nomi sono dati di configurazione iniziale, non condizioni `if` nel codice.

### Rimozioni
`0` impatto prezzo.

### Override manuale
Se `manualUnitPriceCents != null`:
`finalUnitPrice = manualUnitPrice`.

Il manuale ha precedenza assoluta.

### Reset
`Ripristina prezzo automatico`:
- imposta manual price a null;
- ricalcola il final dal pricing automatico.

### Totali
Per ogni riga persistita (in cents / `Money`):
```text
lineTotalCents = finalUnitPriceCents * quantity
orderTotalCents = sum(lineTotalCents di tutti gli order_items persistiti)
```

Usare esclusivamente `Money` / `Long` cents. Vietati `Double` e `Float`.

Overflow: operazioni checked/safe coerenti con le primitive `Money` esistenti (`addExact` / `multiplyExact` o equivalente). Vietato overflow silenzioso/wraparound. Se `finalUnitPriceCents * quantity` oppure la somma dei line total supera il range supportato → `AmountOverflow` / errore equivalente già usato dal dominio. Non mostrare un totale numericamente corrotto. Non introdurre un nuovo limite monetario arbitrario.

Semantica del totale live DRAFT e del ruolo di `orders.totalCents`: vedi Totale live DRAFT (ORD-022).

## 12. Snapshot e mutabilità menu

Una riga ordine salva dati necessari a riprodurre il prezzo e la stampa.

Se il menu cambia dopo:
- Accepted resta invariato;
- ristampa resta invariata;
- duplicazione copia esattamente snapshot e prezzi storici.

Un DRAFT già composto non deve cambiare prezzo automaticamente quando viene reimportato il menu.

Nuove righe usano il menu corrente.

## 13. Business date

Configurazione v1:
- timezone: `Europe/Rome`;
- inizio giornata: `05:00`;
- `businessDayStartMinutes = 300`.

Regola:
- timestamp locale >= 05:00 -> data calendario corrente;
- timestamp locale < 05:00 -> data calendario precedente.

Esempi:
- 02/09 04:59 -> businessDate 01/09;
- 02/09 05:00 -> 02/09;
- 03/09 01:30 -> 02/09.

Calcolare al momento dell'accettazione, non alla creazione DRAFT.

## 14. Numerazione sequenziale

Per ogni businessDate:
- inizia `001`;
- 002, 003...
- 999;
- 1000, 1001... senza limite a tre cifre.

`displayNumber` è stringa.

Cambio modalità e ritorno a sequenziale nello stesso giorno:
- riprende dal successivo sequenziale non consumato.

## 15. Numerazione casuale

Formato:
`[A-Z][00-99]`.

Spazio:
26 * 100 = 2.600 codici.

Primo ciclo di una businessDate:
- nessuna ripetizione.

Dopo 2.600 assegnazioni:
- incrementa `randomCycle` (ciclo 1 -> 2);
- genera nuova sequenza/permutazione;
- i codici possono ripetersi rispetto al ciclo precedente.

Ogni businessDate è indipendente.

Implementazione:
- deterministica per `randomSeed + randomCycle`;
- posizione persistita;
- generatore di permutazione stabile attraverso riavvii;
- non usare rejection sampling vicino all'esaurimento.

`numberingCycle` viene salvato nell'ordine casuale ma non mostrato/stampato.

## 16. Cambio modalità numerazione

Esempio valido:
```text
001
002
F37
M81
003
```

Lo stato sequenziale e casuale della stessa businessDate devono essere preservati separatamente.

## 17. Assegnazione numero

Numero assegnato esclusivamente durante `AcceptOrder`.

Non assegnare:
- alla creazione draft;
- in anteprima;
- su stampa bozza.

## 18. AcceptOrder

Transazione atomica:
1. carica DRAFT;
2. verifica `status == DRAFT`;
3. verifica almeno una riga;
4. calcola totale;
5. calcola businessDate;
6. legge modalità numerazione business-critical;
7. carica/crea numbering state;
8. assegna displayNumber;
9. aggiorna numbering state;
10. imposta status Accepted;
11. imposta acceptedAt;
12. imposta businessDate;
13. salva total;
14. imposta draftSlot null;
15. commit.

Solo dopo commit:
- se azione `ACCETTA E STAMPA`, avvia stampa.

Doppio tap / concorrenza:
- una sola transizione può riuscire;
- una seconda chiamata su Accepted fallisce senza consumare numero.

## 19. Stampa bozza

- mantiene `DRAFT`;
- header `BOZZA`;
- nessun numero;
- nessun acceptedAt;
- nessun archivio Accepted;
- nessun consumo numerazione.

## 20. Fallimento stampa dopo Accept

Ordine rimane `ACCEPTED`.

UI:
`Impossibile stampare. [RIPROVA] [CHIUDI]`.

Retry:
- usa stesso order ID;
- stesso displayNumber;
- stessi snapshot;
- nessuna nuova accettazione.

## 21. Ristampa

- disponibile da Accepted screen, Today e Archive;
- nessuna etichetta `RISTAMPA`;
- output funzionalmente identico alla stampa finale dello stesso ordine;
- una sola copia per azione esplicita.

## 22. Archivio

Solo `ACCEPTED`.

`Ordini di oggi`:
- businessDate corrente;
- più recente -> meno recente.

Archivio:
- Oggi;
- Ieri;
- Scegli data;
- ricerca displayNumber.

Lo stesso numero può esistere:
- in date diverse;
- in cicli casuali diversi dello stesso giorno dopo 2.600 ordini.

Usare ID interno + timestamp per disambiguare.

## 23. Duplicazione

Crea nuovo `DRAFT` copiando:
- righe;
- quantità;
- addition;
- removal;
- note;
- prezzi;
- snapshot.

Non copia:
- status Accepted;
- displayNumber;
- acceptedAt;
- businessDate.

Salvare `sourceOrderId`.

Il nuovo draft non ricalcola prezzi dal menu corrente.

## 24. Menu attivo/inattivo

Disattivazione, non cancellazione.

Inactive:
- non aggiungibile a nuovi ordini;
- storico intatto;
- riferimenti/snapshot intatti.

Import:
- prodotti assenti nel nuovo file non vengono automaticamente disattivati.

## 25. Nome stampato

Prodotto/addition possono avere:
- `name`: nome UI;
- `printedName`: nome preferito in stampa.

Se `printedName` è vuoto/null:
- fallback a `name`.

Snapshot di stampa deve preservare il nome risolto usato dall'ordine.

## 26. Regole non presenti nel v1

Non derivare regole su:
- pagamenti;
- metodi di pagamento;
- resto;
- sconti;
- cliente;
- tavolo;
- consegna;
- fiscale;
se non vengono aggiunte con requisito esplicito.
