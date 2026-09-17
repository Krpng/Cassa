# 07 — Specifica Stampa 80 mm Bluetooth ESC/POS — CASSA

## 1. Scopo

Stampare una comanda/ricevuta operativa **non fiscale**.

Hardware test iniziale:
- NETUM 80 mm;
- Bluetooth;
- ESC/POS;
- strappo manuale accettato;
- cutter non richiesto.

La futura stampante può essere diversa.

## 2. Separazione architetturale

```text
Order snapshot
 -> ReceiptComposer
 -> PrintableDocument
 -> EscPosEncoder
 -> PrinterDriver
 -> Bluetooth SPP
 -> Printer
```

Nessun codice di dominio deve verificare `NETUM`.

## 3. PrinterProfile

Campi consigliati:
- id/name;
- paperWidthMm = 80;
- charsPerLine;
- codePage (JVM Charset name — text → bytes);
- escPosCodeTable opzionale (`Int?`; selettore fisico `ESC t n`; **D-060**; distinto da `codePage`);
- feedLines;
- supportsCut;
- cutCommandVariant opzionale;
- pricePrintMode.

Default test:
- 80 mm;
- `supportsCut=false`;
- `DETAILED`;
- `escPosCodeTable=null` (nessun `ESC t`).

`charsPerLine`, JVM `codePage`, eventuale `escPosCodeTable` e feed sono calibrati su hardware (**HW-001**). Valori operativi NETUM M9 (**D-061 FROZEN**):

- `paperWidthMm = 80`
- `charsPerLine = 42` (safe NORMAL layout width; exact max **not** claimed — 48 wraps)
- `codePage = IBM00858`
- `escPosCodeTable = 19`
- `feedLines = 3`
- `supportsCut = false`
- `cutCommandVariant = null`

Preferenza tipografica M9 (non nel profilo): base business **DOUBLE_BOTH** — **D-065 FROZEN** + IMPLEMENTED. Layout width must be scale-aware (**D-067 FROZEN**). Raffinamento gerarchia tipografica / spacing / feed: **DEFERRED POST-M9**.

`charsPerLine = 42` resta la larghezza calibrata a font **NORMAL** sul `PrinterProfile`. **Non** impostare 21 sul profilo.

Larghezza di layout effettiva (**D-067**):

```text
horizontalScaleMultiplier(NORMAL)         = 1
horizontalScaleMultiplier(DOUBLE_HEIGHT)  = 1
horizontalScaleMultiplier(DOUBLE_WIDTH)   = 2
horizontalScaleMultiplier(DOUBLE_BOTH)    = 2

layoutWidth = max(1, floor(charsPerLine / multiplier))
```

Con profilo 42 + business `DOUBLE_BOTH` → layout width **21** (separatori, wrap, center, price placement) **prima** di assegnare `textScale`. Owner: `ReceiptTextLayout` / composer layout — **non** encoder, **non** PrinterProfile.

PrinterProfile **non** possiede stili business (font titolo/item/totale, allineamenti receipt, textScale): quelli vivono in `PrintableDocument` / layout.

## 4. Tipi stampa

### DRAFT
Header:
`BOZZA`

Nessun numero.

### FINAL
Header:
solo `displayNumber`, molto grande e centrato.

### REPRINT
Stesso contenuto finale.
Non scrivere `RISTAMPA`.

## 5. Informazioni NON stampate

- data;
- ora;
- dicitura fiscale;
- numero fiscale;
- metodo pagamento;
- nome cliente;
- logo/nome attività nel v1;
- QR code;
- etichetta `ORDINE` obbligatoria;
- `RISTAMPA`.

## 6. Ordine sezioni

Sempre:
1. PIZZE
2. FRITTURA
3. BIBITE

Categorie vuote:
- omesse.

All'interno:
- `createdSequence`.

## 7. Header

Finale:
```text
================================
              A37
================================
```

Sequenziale:
```text
================================
              037
================================
```

Bozza:
```text
================================
             BOZZA
================================
```

Il renderer adatta il numero di `=` a `charsPerLine`.

## 8. Sezioni

Compatte:
```text
---------- PIZZE ----------
```

Preferire bold se disponibile.
Non sprecare righe con separatori eccessivi.

## 9. Nomi

Usare:
`productPrintedNameSnapshot`.

Fallback è già risolto nello snapshot.

Nomi lunghi:
- wrap su più righe;
- non troncare informazioni essenziali;
- non comprimere il font automaticamente per far entrare tutto.

## 10. Prezzi — modalità default DETAILED

L'utente ha scelto di tenere i prezzi finché la leggibilità reale resta buona.

### Pizza automatica
Esempio qty 1:
```text
1x Margherita                  7,00
   + Provola                   3,00
   + Funghi                    3,00
   - Mozzarella
```

Qty 2:
```text
2x Margherita                 14,00
   + Provola                   6,00
```

Prezzi mostrati sono estesi per quantità.

### Frittura/Bibita
```text
2x Coca Cola                   5,00
```

### automaticExtrasPricing=false
Main line mostra il prezzo finale esteso; additions senza prezzo:
```text
1x Pizza Fritta               13,00
   + Provola
   + Prosciutto cotto
```

### Manual price
Il manuale rende non significativa la decomposizione automatica.
Stampare:
- prezzo finale sulla main line;
- additions senza prezzo.

```text
1x Margherita                 13,00
   + Provola
   - Mozzarella
```

Questo evita che i prezzi visibili non sommino al final override.

## 11. TOTAL_ONLY

Supporto tecnico:
`PricePrintMode.TOTAL_ONLY`.

Esempio:
```text
1x Margherita
   + Provola
   - Mozzarella

2x Coca Cola

--------------------------------
TOTALE                        24,50
```

Default:
`DETAILED`.

Non serve una UI pubblica obbligatoria nel v1; può essere preferenza printer/developer.

## 12. Rimozioni

Sempre:
`- Nome ingrediente`

Mai prezzo.
Mai sottrarre dal totale.

## 13. Additions

Sempre:
`+ Nome stampato`.

Prezzo visibile solo se:
- DETAILED;
- nessun manual override;
- charged price > 0 / automatic pricing applicato.

Addition €0:
- può essere stampata senza `0,00` per leggibilità, es. `+ Ben cotta`.

## 14. Note

Riga:
`NOTA: ...`

Nota generale:
```text
NOTE ORDINE:
...
```

Posizione:
- dopo le sezioni;
- prima del totale.

Note lunghe:
- wrap.

## 15. Totale

Sempre in finale e bozza:
```text
--------------------------------
TOTALE                        35,00
```

**Non** ricalcolare dal menu/catalogo corrente.

Sorgente autoritativa (**D-066 FROZEN**):

- **DRAFT** (`PrintKind.DRAFT`): `CalculateOrderTotal.fromPersistedItems(order.items).orderTotal` — stessa semantica dell’Acceptance Preview (ORD-022). **Non** usare `Order.total` / `orders.totalCents` stale come SoT mentre lo status è DRAFT.
- **FINAL** (`PrintKind.FINAL`): `order.total` congelato all’accettazione.

La stampa bozza **non** scrive `orders.totalCents`.

## 16. Feed/cut

Fine:
- `feedLines` configurabili;
- test iniziale 3–5 righe, calibrare.

Se `supportsCut=true` in futura stampante:
- encoder può inviare cut dopo feed;
- il dominio non cambia.

NETUM test:
- manual tear;
- niente cut command necessario.

## 17. Stato stampa

```text
IDLE
 -> CONNECTING
 -> PRINTING
 -> SUCCESS
 -> IDLE
```

Errori:
- BluetoothDisabled
- PermissionDenied
- PrinterNotConfigured
- ConnectionFailed
- ConnectionLost
- Timeout
- PrintFailed
- UnsupportedEncoding
- Unknown

## 18. Sequenza Accetta e stampa

Obbligatoria:
```text
AcceptOrder transaction
 -> COMMIT
 -> load Accepted snapshot
 -> compose
 -> connect
 -> print
```

Mai:
```text
print -> accept
```

Mai DB transaction aperta durante I/O Bluetooth.

## 19. Failure semantics

Se print fallisce dopo commit:
- Accepted resta Accepted;
- numero resta consumato;
- retry usa stesso numero.

Se bozza fallisce:
- draft resta Draft;
- nessun numero.

## 20. Exactly-once vs physical printer

L'app può impedire:
- doppio tap;
- stampe concorrenti.

Non può garantire "exactly once" fisico se:
- byte sono stati inviati;
- connessione cade prima della conferma;
- stampante ha già stampato parzialmente/completamente.

Quindi su `ConnectionLost` durante write:
- classificare outcome come incerto;
- messaggio: verificare la carta prima di `RIPROVA`.

## 21. Mutex

`PrinterService` usa `Mutex`.
Una sola sessione/stampa alla volta.

## 22. Bluetooth

MVP:
- printer paired via Android Settings;
- app seleziona bonded device.

Driver:
- RFCOMM/SPP;
- OutputStream;
- timeout;
- close socket in finally.

SPP UUID (D-058): `00001101-0000-1000-8000-00805F9B34FB`.
RFCOMM socket (D-058 Q1-A): **secure only** — `createRfcommSocketToServiceRecord(SPP_UUID)`.
Insecure RFCOMM / secure→insecure fallback: **NOT ALLOWED** in BT-004 (contract revision required if hardware proves insecure necessary).

Timeout / disconnect / error mapping (**D-059 COMPLETE** / BT-005 `3aad082`):
- Connect timeout default **10_000 ms**, injected at driver/transport (not PrinterProfile / DataStore / Room); close attempt socket to interrupt blocking `BluetoothSocket.connect()`; map to `PrinterError.Timeout`.
- **Q2-A connect-only** — no explicit write/flush timeout in MVP (future contract revision required).
- Write/flush `IOException` after CONNECTED → `ConnectionLost` (uncertain outcome §20); microcopy → PRINT-024.
- No automatic print retry; no automatic reconnect algorithm; next explicit job reconnects via normal `PrinterService` lifecycle (D-054).

Calibrazione profilo / formattazione testo (**D-060 FROZEN** / HW-001 READY): vedi §23–§24 / decision log D-060.
Non bloccare main thread.

## 23. ESC/POS encoder

Responsabilità (baseline + **D-060**):
- init (`ESC @`);
- optional physical code-table select (`ESC t n`) when `escPosCodeTable != null`;
- physical alignment (`ESC a`);
- bold/emphasis (`ESC E`);
- character scale ≤2× (`GS !`: NORMAL / DOUBLE_WIDTH / DOUBLE_HEIGHT / DOUBLE_BOTH);
- normal size reset;
- line feed (LF);
- text encoding via JVM charset (`codePage`);
- optional cut when `supportsCut`.

Ordine documento (**D-060**):
1. `ESC @`
2. optional `ESC t`
3. per ogni riga: alignment + emphasis + scale + text + LF
4. reset finale ≥ LEFT + NORMAL emphasis + NORMAL scale
5. `feedLines` × LF
6. optional cut

`PrintableLine` defaults (`alignment=LEFT`, `textScale=NORMAL`) restano i default del modello. Per gli scontrini business di produzione M9, `DefaultReceiptComposer` deve impostare **`textScale=DOUBLE_BOTH`** su tutte le linee emesse (**D-065**), senza redesign di contenuto/emphasis/alignment.

Non mettere layout business nel driver Bluetooth.
Non inventare mapping silenziosi per `€`/glifi; fallback encoder esistente (`€`→`EUR` se non rappresentabile).

## 24. Charset

Due livelli distinti (**D-060**):
- `codePage` = JVM Charset (Unicode → bytes);
- `escPosCodeTable` = selettore tabella fisica stampante (`ESC t`), opzionale.

Calibrare su carta (**HW-001**):
- `à è é ì ò ù`;
- apostrofi;
- `€`.

Se `€` non disponibile nella code page JVM scelta:
- encoder usa fallback `EUR`;
- non sostituire con carattere illeggibile / mapping silenzioso inventato.

Normalizzare caratteri tipografici non supportati:
- smart quotes -> quote/apostrofo standard;
- dash unicode -> `-`.

La coppia definitiva JVM + `ESC t` per NETUM **non** è congelata finché non c’è evidenza hardware.

## 25. FakePrinterDriver

Deve poter:
- catturare bytes/documento;
- simulare Success;
- Timeout;
- ConnectionLost;
- PrintFailed;
- NotConfigured.

Usato nei test senza hardware.

## 26. Test print

Pagina impostazioni:
`STAMPA DI PROVA`.

Contenuto:
- titolo test;
- linee;
- numeri;
- €;
- accenti;
- larghezza;
- testo lungo;
- feed.

Non deve creare Order.

## 26bis. Draft print UI (PRINT-020 / D-064)

Anteprima accettazione (Acceptance Preview):
`STAMPA BOZZA`.

Invoca `PrinterService.printDraft(orderId)`.

- header carta `BOZZA`;
- nessun `displayNumber`;
- ordine resta `DRAFT`;
- nessuna mutation business/Room;
- nessuna auto-retry;
- tipografia base business **DOUBLE_BOTH**: **D-065** (non ownership di PRINT-020); layout scale-aware **D-067**; PRINT-020 **COMPLETE** (Samsung+NETUM hardware PASS).

## 26ter. Accepted print UI (PRINT-021 / D-068)

Accepted Order Detail (ARCH-004 / Today Orders) e post-accept Accepted screen:

`STAMPA` (mai `RISTAMPA`).

Invoca `PrinterService.printAccepted(orderId)`.

- header carta = frozen `displayNumber` (`PrintKind.FINAL`);
- **non** `BOZZA`;
- ordine resta `ACCEPTED` immutabile;
- nessuna mutation business/Room/numbering;
- nessuna auto-retry;
- tipografia/layout: **D-065** / **D-067** (non ownership di PRINT-021);
- **PRINT-021 COMPLETE** (Samsung+NETUM hardware PASS);
- **non** accept+print (**PRINT-022** / **D-069 COMPLETE** — Samsung+NETUM success + BT-off failure PASS);
- **non** retry UX dedicata (**PRINT-023**);
- **non** uncertain-outcome microcopy (**PRINT-024**).

## 26quater. Accept then automatic FINAL print (PRINT-022 / D-069) — COMPLETE

Acceptance Preview Ready:

`ACCETTA` (nessuna nuova destinazione; nessuna CTA separata `ACCETTA E STAMPA` in questo task).

Sequenza obbligatoria:

```text
AcceptOrder transaction
 -> COMMIT
 -> AcceptOrderResult.Accepted(orderId=…)
 -> PrinterService.printAccepted(orderId)
```

- PrintKind = FINAL; header = frozen `displayNumber` (non BOZZA); total = `order.total`;
- D-065 / D-067 invariati via path esistente;
- zero print su fallimento accept;
- fallimento print → ACCEPTED resta; feedback `Ordine accettato. ` + errore mappato;
- successo print → `Ordine inviato alla stampante`;
- one-shot sul path comando; **no** auto-print da observation Accepted;
- riuso `acceptedPrintJob` / `AcceptedPrintUiState` (blocca STAMPA manuale concorrente);
- **PRINT-022 COMPLETE** (success-path + Bluetooth-disabled failure-path hardware PASS);
- dopo fallimento, re-tap `STAMPA` = retry stesso ordine (**PRINT-023 / D-070**, no new CTA);
- **non** PRINT-024.

## 26quinquies. Retry same accepted order (PRINT-023 / D-070) — functionally satisfied

Dopo un tentativo di stampa Accepted completato (manuale PRINT-021 o automatico PRINT-022):

nuovo tap esplicito **`STAMPA`**
→ `PrinterService.printAccepted(stesso orderId)`

- stesso `displayNumber` / snapshot / `order.total`; PrintKind FINAL; non BOZZA;
- D-065 / D-067 invariati;
- bloccato durante `Printing`; disponibile dopo Error/Success/Idle;
- nessuna auto-retry; nessun AcceptOrder; nessuna numerazione;
- label **`STAMPA`** only (mai `RISTAMPA`);
- nessuna persistenza job fallito;
- esito incerto → **PRINT-024**.

## 27. Hardware validation NETUM

Checklist (**HW-001 / D-060** capability + **D-061** physical freeze + **D-065** business base scale):
- pairing;
- selezione bonded device (`printerId` instrumentation);
- connect / transport (BT-004/005);
- 80 mm;
- chars per line — M9 frozen **42** (safe NORMAL layout width; exact max not claimed; **not** redefined to 21 for DOUBLE_BOTH);
- allineamento / grassetto / scale ≤2× (M9 production business base **DOUBLE_BOTH** per **D-065**; hierarchy/wrap refinement deferred);
- JVM **IBM00858** + `ESC t` **19**;
- € / accenti — hardware PASS with frozen pair;
- feed **3** / strappo manuale;
- cutter: **none** (`supportsCut=false`);
- 10 stampe consecutive = **HW-002**.

Profilo fisico NETUM M9: **D-061 FROZEN**. Base scale scontrino business: **D-065 FROZEN**. Raffinamento visuale avanzato: **DEFERRED POST-M9**.
