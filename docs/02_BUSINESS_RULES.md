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
- rimozione;
- quantità;
- aggiunte;
- rimozioni ingredienti;
- nota;
- prezzo manuale;
- reset prezzo.

Room è la source of truth.

Non mantenere un carrello solo in memoria in attesa di un "salva".

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
- note;
- prezzo manuale.

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
`lineTotal = finalUnitPrice * quantity`.
`orderTotal = somma(lineTotal)`.

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
