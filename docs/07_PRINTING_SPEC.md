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
- codePage;
- feedLines;
- supportsCut;
- cutCommandVariant opzionale;
- pricePrintMode.

Default test:
- 80 mm;
- `supportsCut=false`;
- `DETAILED`.

`charsPerLine` e code page vengono calibrati su hardware.

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

Valore da snapshot/order total, non ricalcolato dal menu corrente.

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

Non bloccare main thread.

## 23. ESC/POS encoder

Responsabilità:
- init;
- alignment;
- bold;
- double size per header;
- normal size;
- line feed;
- text encoding;
- optional cut.

Non mettere layout business nel driver Bluetooth.

## 24. Charset

Calibrare:
- `à è ì ò ù`;
- apostrofi;
- `€`.

Se `€` non disponibile nella code page:
- PrinterProfile può usare fallback `EUR`;
- non sostituire con carattere illeggibile.

Normalizzare caratteri tipografici non supportati:
- smart quotes -> quote/apostrofo standard;
- dash unicode -> `-`.

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

## 27. Hardware validation NETUM

Checklist:
- pairing;
- selezione bonded device;
- connect;
- reconnect;
- 80 mm;
- chars per line;
- €;
- accenti;
- numero grande;
- note lunghe;
- 10 stampe consecutive;
- spegnimento durante stampa;
- retry;
- feed/strappo.

Solo dopo validazione, salvare PrinterProfile definitivo.
