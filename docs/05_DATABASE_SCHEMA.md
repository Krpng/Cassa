# 05 — Database Schema Room/SQLite — CASSA

## 1. Principi

- Room/SQLite è source of truth.
- Monetary values = `Long` cents.
- Order IDs = UUID string.
- Accepted storico tramite snapshot **finché l'ordine esiste** (giornata corrente).
- Foreign key e indici espliciti.
- Accepted della giornata precedente: hard delete automatico (RET-001); **non** retention indefinita.
- Migrazioni versionate, mai destructive fallback in release.

## 2. `products`

| Campo | Tipo | Null | Note |
|---|---|---:|---|
| id | Long PK autoincrement | no | interno |
| name | String | no | display UI |
| normalizedName | String | no | lookup/import/search |
| printedName | String | sì | fallback a name |
| category | String enum | no | PIZZA/FRITTURA/BIBITA |
| priceCents | Long | no | Prezzo Asporto |
| automaticExtrasPricing | Boolean | no | default true nuovi |
| active | Boolean | no | default true |
| createdAt | Instant/Long | no | |
| updatedAt | Instant/Long | no | |

Vincolo:
- UNIQUE(normalizedName).

`Prezzo Sala` non viene salvato.

## 3. `ingredients`

| Campo | Tipo | Null |
|---|---|---:|
| id | Long PK | no |
| name | String | no |
| normalizedName | String | no |
| active | Boolean | no |

UNIQUE(normalizedName).

## 4. `product_ingredients`

| Campo | Tipo | Note |
|---|---|---|
| productId | Long FK | PK part |
| ingredientId | Long FK | PK part |
| displayOrder | Int | ordine cella ODS/manuale |

PK `(productId, ingredientId)`.

## 5. `additions`

| Campo | Tipo | Null | Note |
|---|---|---:|---|
| id | Long PK | no | |
| name | String | no | UI |
| normalizedName | String | no | unique |
| printedName | String | sì | fallback |
| priceCents | Long | no | >=0 |
| active | Boolean | no | |
| createdAt | Long | no | |
| updatedAt | Long | no | |

UNIQUE(normalizedName).

## 6. `orders`

| Campo | Tipo | Null | Note |
|---|---|---:|---|
| id | String UUID PK | no | identità reale |
| status | String | no | DRAFT/ACCEPTED |
| draftSlot | Int | sì | 1 per DRAFT, null Accepted |
| displayNumber | String | sì | assegnato Accept |
| numberingMode | String | sì | snapshot modalità |
| numberingCycle | Int | sì | solo random |
| businessDate | String ISO date | sì | Accept |
| createdAt | Long | no | |
| updatedAt | Long | no | |
| acceptedAt | Long | sì | |
| totalCents | Long | no | 0 draft vuoto |
| generalNote | String | sì | |
| sourceOrderId | String | sì | duplicazione |

Indice unique:
- `draftSlot` unique.

SQLite consente più `NULL`, quindi:
- DRAFT -> 1;
- ACCEPTED -> null.

Indici:
- `(status)`;
- `(businessDate, acceptedAt)`;
- `(displayNumber)`;
- `(businessDate, displayNumber)`.

`displayNumber` NON unique.

## 7. `order_items`

| Campo | Tipo | Null | Note |
|---|---|---:|---|
| id | String UUID PK | no | |
| orderId | String FK | no | CASCADE |
| productId | Long FK | sì | riferimento catalogo |
| productNameSnapshot | String | no | |
| productPrintedNameSnapshot | String | no | risolto |
| categorySnapshot | String | no | |
| quantity | Int | no | >0 |
| baseUnitPriceCents | Long | no | |
| automaticExtrasTotalCents | Long | no | |
| manualUnitPriceCents | Long | sì | null=auto |
| finalUnitPriceCents | Long | no | |
| automaticExtrasPricingSnapshot | Boolean | no | |
| note | String | sì | |
| createdSequence | Long/Int | no | ordine inserimento |

`hasManualPrice` si deriva da `manualUnitPriceCents != null`, evitando stato ridondante.

## 8. `order_item_additions`

| Campo | Tipo | Null | Note |
|---|---|---:|---|
| id | String UUID PK | no | |
| orderItemId | String FK | no | CASCADE |
| additionId | Long FK | sì | |
| additionNameSnapshot | String | no | |
| additionPrintedNameSnapshot | String | no | |
| listedPriceCents | Long | no | prezzo catalogo snapshot |
| chargedPriceCents | Long | no | 0 se no auto pricing |
| displayOrder | Int | no | |

## 9. `order_item_removals`

| Campo | Tipo | Null |
|---|---|---:|
| id | String UUID PK | no |
| orderItemId | String FK | no |
| ingredientId | Long FK | sì |
| ingredientNameSnapshot | String | no |
| displayOrder | Int | no |

Nessun prezzo.

## 10. `app_settings`

Singleton row `id=1`.

| Campo | Tipo | Default |
|---|---|---|
| id | Int | 1 |
| numberingMode | String | SEQUENTIAL |
| businessDayStartMinutes | Int | 300 |
| timezoneId | String | Europe/Rome |
| updatedAt | Long | |

Questi dati sono in Room perché business critical. `numberingMode` è la source of truth della modalità corrente (D-040 / NUM-005). Valore stringa non riconosciuto come `SEQUENTIAL|RANDOM`: errore esplicito di storage/config; **non** rewrite silenzioso della row.

## 11. `numbering_state`

PK: `businessDate`.

| Campo | Tipo | Default |
|---|---|---|
| businessDate | String | |
| nextSequentialNumber | Long | 1 |
| randomCycle | Int | 1 |
| randomSeed | Long | filler `0` finché non inizializzato |
| randomSeedInitialized | Boolean (`INTEGER`) | `0` / false |
| randomPosition | Int | 0 |
| updatedAt | Long | |

### Semantica `randomSeedInitialized` (FREEZE-A patch)

- `randomSeedInitialized = false`: RANDOM seed **non** inizializzato. Il valore fisico in `randomSeed` **non** è business-meaningful (filler tecnico consentito: `0L` perché la colonna resta NOT NULL). **Non** interpretare `randomSeed == 0` come “uninitialized”.
- `randomSeedInitialized = true`: `randomSeed` è autorevole. `0L` è un seed **valido** quando initialized=true. Nessun Long è riservato come sentinel.

Creazione riga da primo uso **SEQUENTIAL**:

- `randomSeedInitialized = false`;
- `randomSeed = 0L` (filler only);
- sequential allocation **non** inizializza il seed RANDOM.

Prima necessità **RANDOM**:

- se `randomSeedInitialized == false` → generate seed once, persist `randomSeed`, set `randomSeedInitialized = true` (stessa transaction di inizializzazione RANDOM).

Quando cambia ciclo random (dopo consume della posizione 2599):

- `randomCycle += 1`;
- **stesso** `randomSeed` (non rigenerare);
- `randomSeedInitialized` resta `true`;
- `randomPosition = 0`;
- nuova permutazione deterministica da `(randomSeed, randomCycle)` per FREEZE-A (XorShift32 + Fisher–Yates).

`SEQUENTIAL` e `RANDOM` condividono la riga `numbering_state` per `businessDate` ma i campi di stato **non** si azzerano al cambio modalità.

### Migration additiva richiesta (non ancora implementata)

Aggiungere colonna:

```sql
ALTER TABLE numbering_state
ADD COLUMN randomSeedInitialized INTEGER NOT NULL DEFAULT 0
```

- incrementare DB version;
- **no** destructive migration;
- **no** recreate DB;
- righe esistenti: `randomSeedInitialized = false` (RANDOM non ancora implementato/consumato in v1).
Vedere backlog **DB-010**.

## 12. Printer settings

Non è necessaria una tabella Room nel design finale.

DataStore:
- selectedPrinterId/address;
- deviceName opzionale;
- paperWidthMm;
- charsPerLine;
- codePage;
- feedLines;
- supportsCut;
- pricePrintMode.

## 13. Relazioni Room

DTO/relations:
- `ProductWithIngredients`;
- `OrderWithItems`;
- `OrderItemWithModifiers`;
- `FullOrder`.

Caricare un ordine in forma consistente, evitando query N+1 incontrollate.

## 14. Snapshot

### Quando
Quando una riga viene creata, salvare:
- nome;
- printedName risolto;
- categoria;
- base price;
- automatic extras pricing flag;
- addition names/prices;
- removal names.

Quando viene accettata:
- total;
- numbering;
- business date;
- acceptedAt.

### Perché
Una modifica menu futura non altera:
- ordini Accepted ancora presenti (giornata corrente);
- ristampa di quegli ordini;
- eventuali future duplicazioni (se riattivate dal prodotto).

## 15. Vincoli applicativi

Room/database + dominio devono garantire:
- qty > 0;
- price cents >=0;
- status validi;
- un solo draft;
- Accepted immutable;
- addition solo su pizza;
- removal solo su pizza;
- Accept non vuoto.

Le regole category/modifier possono essere nel dominio anche se SQLite non ha check complessi.

## 16. Transazione `AcceptOrder` (FREEZE-B)

Una sola `@Transaction` Room. Preview/`COMPLETA` **non** entra qui (zero write, zero consume).

Pseudocodice:

```text
@Transaction
acceptOrder(orderId):
    order = get(orderId)
    require(order exists && order.status == DRAFT)
    items = getFullPersistedItems(orderId)
    require(items.isNotEmpty())

    total = checkedSum(finalUnitPriceCents * quantity)   // ORD-022 Money; overflow => abort
    now = clockProvider.now()                            // singolo logical now
    businessDate = calculator(now, settings)
    mode = settings.numberingMode                        // SEQUENTIAL | RANDOM

    state = getOrCreateNumberingState(businessDate)
    // SEQUENTIAL create: randomSeedInitialized=false, randomSeed=0L filler only
    // RANDOM first-need: if !randomSeedInitialized → generate seed once,
    // persist randomSeed, set randomSeedInitialized=true (same transaction)

    if mode == SEQUENTIAL:
        display = formatSequential(state.nextSequentialNumber)
        state.nextSequentialNumber += 1
        cycle = null
        // must NOT set randomSeedInitialized=true; must NOT regenerate randomSeed
    else:
        // require randomSeedInitialized==true after first-need init above
        perm = xorShift32FisherYates(state.randomSeed, state.randomCycle)  // FREEZE-A
        display = indexToCode(perm[state.randomPosition])
        cycle = state.randomCycle
        if state.randomPosition == 2599:
            state.randomCycle += 1
            state.randomPosition = 0
            // seed UNCHANGED; randomSeedInitialized stays true
        else:
            state.randomPosition += 1

    update numbering_state
    update order:
        status=ACCEPTED
        draftSlot=null
        displayNumber=display
        numberingMode=mode
        numberingCycle=cycle   // solo RANDOM; non stampato in UI
        businessDate=...
        acceptedAt=now
        totalCents=total       // snapshot from persisted items; ignore stale draft totalCents
        updatedAt=now
    // commit: all-or-nothing
```

Fallimento / crash mid-transaction: solo stati A (tutto DRAFT + numbering invariato) o B (ACCEPTED completo + numbering avanzato una volta). Doppio accept concorrente: una sola riuscita; seconda `AlreadyAccepted`/`OrderNotDraft` senza consume.

Nota: `clockProvider.now()` e tutti i campi timestamp della stessa accept usano lo stesso logical `now`.

## 17. Transazione split

`3x standard -> modifica una`:
- read item;
- verify order Draft;
- verify qty >1;
- original quantity -1;
- create clone quantity 1;
- applica modificatori/prezzo alla clone;
- commit.

Se fallisce:
- resta `3x`.

## 18. Transazione duplicate (ARCH-006 / D-046)

Una sola Room transaction. Sequenza semantica:

1. verifica source `ACCEPTED` + `businessDate == currentBusinessDate`;
2. verifica assenza active DRAFT (se presente → typed conflict, **zero writes**; risoluzione UX = ARCH-007);
3. crea order DRAFT (`draftSlot=1`, `sourceOrderId=source.id`, no display/businessDate/acceptedAt);
4. copia items con **nuovi** UUID, snapshot/prezzi/`createdSequence` exact, `productId` COPY;
5. copia additions/removals con **nuovi** child UUID, snapshot + `additionId`/`ingredientId` COPY;
6. `generalNote` COPY; **non** copiare `totalCents` Accepted come autoritativo (totale DRAFT = ORD-022 da items).

Se fallisce: rollback completo (nessun DRAFT parziale).
Schema: DB version = 2 unchanged; `sourceOrderId` già presente; migration NONE.

## 19. Import transaction

Il parser/validator prepara un piano fuori transazione.

Dentro:
- upsert products;
- replace ingredients quando la colonna è presente;
- upsert additions;
- nessuna delete di assenti;
- commit completo o rollback.

## 20. Query

### Active draft
`status=DRAFT AND draftSlot=1`.

### Today (only retained Accepted)
`status=ACCEPTED AND businessDate=:currentBusinessDate ORDER BY acceptedAt DESC`.

### Archive date / cross-day search — OBSOLETE
> **CANCELLED BY PRODUCT SCOPE CHANGE (M7).** Nessuna query archivio multi-giorno né search `displayNumber` cross-day.

### Products
active by category + all with ingredients.

### Daily purge (RET-001)
Hard delete `ACCEPTED` where `businessDate < :currentBusinessDate`.

MVP delete order (no schema migration; DB version = 2):
1. delete `order_item_removals` for items of target orders (FK `NO ACTION`);
2. delete target `orders` (cascade items → additions).

Non tocca: DRAFT, `numbering_state`, catalog.

## 21. Migrazioni

Regola:
- ogni schema change incrementa DB version;
- migrazione esplicita;
- test migration;
- mai `fallbackToDestructiveMigration()` in release.

RET-001: **migration = NONE** (DB version resta 2).

## 22. Retention

> **SUPERSEDED (M7 freeze).** La retention indefinita Accepted è cancellata.

Contratto vigente:
- consultabili solo `ACCEPTED` della `currentBusinessDate`;
- hard delete definitivo per `businessDate < currentBusinessDate` (RET-001);
- nessuna retention nascosta / soft-delete.

Questo comportamento va reso noto nelle note release.
