# 05 — Database Schema Room/SQLite — CASSA

## 1. Principi

- Room/SQLite è source of truth.
- Monetary values = `Long` cents.
- Order IDs = UUID string.
- Accepted storico tramite snapshot.
- Foreign key e indici espliciti.
- Nessuna cancellazione automatica di Accepted.
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

Questi dati sono in Room perché business critical.

## 11. `numbering_state`

PK: `businessDate`.

| Campo | Tipo | Default |
|---|---|---|
| businessDate | String | |
| nextSequentialNumber | Long | 1 |
| randomCycle | Int | 1 |
| randomSeed | Long | generated |
| randomPosition | Int | 0 |
| updatedAt | Long | |

Quando cambia ciclo random:
- `randomCycle += 1`;
- nuova seed;
- `randomPosition = 0`.

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
- archivio;
- ristampa;
- duplicazione.

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

## 16. Transazione `AcceptOrder`

Pseudocodice:

```text
@Transaction
acceptOrder(orderId):
    order = get(orderId)
    require(order.status == DRAFT)
    items = getFullItems(orderId)
    require(items.isNotEmpty())

    now = clock.now()
    businessDate = calculator(now, settings)
    total = calculateFromPersistedItems(items)

    state = getOrCreateNumberingState(businessDate)

    if settings.numberingMode == SEQUENTIAL:
        display = format(state.nextSequentialNumber)
        state.nextSequentialNumber += 1
        cycle = null
    else:
        display = permutation(state.seed, state.cycle)[state.position]
        cycle = state.randomCycle
        advanceRandomState(state)

    update state

    update order:
        status=ACCEPTED
        draftSlot=null
        displayNumber=display
        numberingMode=settings.mode
        numberingCycle=cycle
        businessDate=...
        acceptedAt=now
        totalCents=total
```

Nota: `clock.now()` idealmente ottenuto prima o passato alla transazione, ma business decision e DB updates devono usare lo stesso timestamp.

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

## 18. Transazione duplicate

- verifica source Accepted;
- verifica assenza draft o conflitto risolto prima;
- crea order Draft;
- copia items e child modifiers;
- copia snapshot/prezzi;
- sourceOrderId=source.id;
- nessun display/businessDate/acceptedAt.

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

### Today
`status=ACCEPTED AND businessDate=:date ORDER BY acceptedAt DESC`.

### Archive date
same by selected businessDate.

### Search number
`status=ACCEPTED AND displayNumber LIKE ... ORDER BY acceptedAt DESC`.

### Products
active by category + all with ingredients.

## 21. Migrazioni

Regola:
- ogni schema change incrementa DB version;
- migrazione esplicita;
- test migration;
- mai `fallbackToDestructiveMigration()` in release.

## 22. Retention

Accepted non cancellabili nel v1.
Retention: locale indefinita finché l'utente non disinstalla/resetta o viene aggiunta futura gestione archivio.

Questo comportamento va reso noto nelle note release.
