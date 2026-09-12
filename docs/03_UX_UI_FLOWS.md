# 03 — UX/UI, schermate e flussi — CASSA

## 1. Principi UX

- Velocità prima dell'estetica decorativa.
- Azioni principali sempre evidenti.
- Testi in italiano.
- Pulsanti con etichetta testuale, non sole icone per azioni critiche.
- Touch target ampi.
- Contrasto elevato.
- Supportare font scale Android senza tagli di contenuto critico.
- Non usare il colore come unico segnale.
- Stati error/loading/empty espliciti.
- Evitare dialog non necessari durante il flusso rapido.
- Conferma obbligatoria solo per azioni distruttive: eliminazione draft, sostituzione draft, rimozione riga ordine.

## 2. Information architecture

```text
HOME
├── NUOVO ORDINE
│   ├── Ricerca/filtri
│   ├── Dettaglio prodotto
│   ├── Personalizzazione pizza
│   └── Anteprima
├── ORDINI DI OGGI
│   └── Dettaglio Accepted
├── ARCHIVIO
│   └── Dettaglio Accepted
├── MENU
│   ├── Pizze
│   ├── Frittura
│   ├── Bibite
│   ├── Aggiunte
│   ├── Modifica prodotto
│   └── Importa ODS
└── IMPOSTAZIONI
    ├── Numerazione
    └── Stampante
```

## 3. Home

### Scopo
Far partire un ordine nel minor tempo possibile.

### Gerarchia
1. `NUOVO ORDINE` — CTA dominante.
2. Banner DRAFT se presente.
3. `ORDINI DI OGGI`.
4. `ARCHIVIO`.
5. Menu e Impostazioni secondari.

### Stato con draft
```text
ORDINE IN CORSO
3 articoli · € 27,50
[RIPRENDI ORDINE]
```

### Nuovo ordine con draft esistente
Dialog:
```text
Esiste già un ordine in corso.

[RIPRENDI]
[ELIMINA E CREA NUOVO]
[ANNULLA]
```

Elimina richiede conferma secondaria se il draft non è vuoto.

## 4. Recupero DRAFT all'avvio

Se non vuoto:
```text
Hai un ordine non completato.

[RIPRENDI]
[ELIMINA]
```

`ELIMINA`:
- conferma;
- cancella draft in transazione.

Se vuoto:
- non mostrare recupero.

## 5. Nuovo ordine

### Header (M5 UX — compact order workspace)

Nella parte alta della schermata principale del DRAFT, tre azioni testuali coerenti visivamente con l'attuale `INDIETRO` / `CassaBackButton`:

```text
[ NOTE ]        [ CERCA ]        [ ← INDIETRO ]
```

Posizionamento:
- `NOTE` → alto sinistra;
- `CERCA` → alto centro;
- `INDIETRO` → alto destra.

Regole:
- touch target `>= 48dp`;
- accessibilità corretta (content description distinti);
- stile coerente con `CassaBackButton` senza obbligatoriamente duplicarne l'implementazione se non appropriato;
- `INDIETRO` resta non distruttivo (stesso significato di back/home attuale).

Sotto l'header: titolo `Nuovo ordine` (o indicazione equivalente `Ordine in corso` se già prevista).

### Schermata principale — contenuto (compact)

La schermata principale **non** contiene più:
- editor inline `NOTA ORDINE`;
- pulsante inline `SALVA NOTA`;
- campo ricerca inline.

La schermata principale **mantiene**:
- `Nuovo ordine`;
- `ORDINE CORRENTE` + righe ordine;
- selettore categorie `TUTTI | PIZZE | FRITTURA | BIBITE`;
- elenco catalogo/prodotti della categoria selezionata;
- sticky `TOTALE` (ORD-022).

Controlli ORD-020 invariati: `+/-`, `RIMUOVI`, tap riga → dettaglio.
Custom pizza highlight invariato.

Obiettivo: liberare spazio verticale per il catalogo su smartphone portrait senza rimuovere funzioni.

### Filtri (schermata principale)

`TUTTI | PIZZE | FRITTURA | BIBITE`

Sulla schermata principale i filtri categoria guidano il catalogo inline.
La vista dedicata `CERCA` ha un **proprio** selettore categorie (stesse etichette), indipendente dal filtro MAIN: vedi sezione *Vista dedicata CERCA*.

### Catalogo principale (schermata ordine)

Riga:
```text
Margherita
Pomodoro, Fior di latte... (opzionale/compatto)
€ 7,00                         [+]
```

Se match ingrediente (solo nella vista `CERCA` dedicata):
```text
Margherita
Contiene: Parmigiano
€ 7,00                         [+]
```

Tap riga:
- apre dettaglio.

Tap `+`:
- quick add standard (stesso flow ORD esistente).

### Ordine corrente
Ogni riga del DRAFT mostra:
- controlli quantità `[-] quantity [+]`;
- nome;
- modifiche;
- nota sintetica;
- prezzo finale/line total;
- azione esplicita `RIMUOVI`;
- tap sulla riga (fuori da `+/-` e `RIMUOVI`) apre il dettaglio.

Regole quantità dalla lista:
- `[+]` aumenta di 1 e persiste subito;
- `[-]` con `quantity > 1` diminuisce di 1 e persiste subito;
- `[-]` con `quantity = 1` è disabilitato/non azionabile e non muta;
- `quantity = 0` non è ammesso e non elimina la riga.

`RIMUOVI`:
```text
Rimuovere questa riga?

[ANNULLA]
[RIMUOVI]
```

- `ANNULLA`: nessuna scrittura;
- `RIMUOVI` confermato: elimina atomicamente la riga e i suoi child modifier.

Touch target almeno `48dp`. `RIMUOVI` resta distinto dai controlli quantità.

### Nota ordine — overlay (ORD-021 + M5 UX compact)

`NOTE` apre una sovraimpressione/modal chiaramente separata dalla schermata ordine.

```text
NOTA ORDINE
[campo multilinea]
[ANNULLA]  [SALVA]
```

All'apertura:
- editor inizializzato dal `generalNote` **persistito**;
- dirty = false rispetto al valore Room.

`ANNULLA`:
- chiude overlay;
- nessuna mutation;
- modifiche locali non salvate scartate;
- `generalNote` persistito invariato.

`SALVA`:
- usa `UpdateGeneralNote` esistente;
- normalizzazione / guard / `updatedAt` ORD-021 invariati;
- successo → chiude overlay e torna all'ordine;
- fallimento → overlay resta aperto, testo locale preservato, errore visibile, retry possibile.

Flow re-emission mentre l'overlay è dirty:
- **non** sovrascrive il testo digitato (stesso contratto ORD-021).

Blank/whitespace → persisted `null`. Nessun autosave.

`NOTE` resta disponibile anche su DRAFT vuoto.

Semantica normativa completa: `docs/02_BUSINESS_RULES.md` (Nota generale ordine ORD-021).
`orders.generalNote` non attiva l'highlight viola delle pizze personalizzate.

### Vista dedicata CERCA (M5 UX compact)

Tap `CERCA` apre una vista dedicata alla ricerca (preferire stato/feature locale; nuova Navigation destination solo se strettamente necessaria).

```text
                        [ ← INDIETRO ]

Cerca prodotto
[campo ricerca]
TUTTI | PIZZE | FRITTURA | BIBITE
[risultati]
```

Layout filtri:
- subito sotto il campo ricerca;
- subito sopra i risultati;
- stile e comportamento coerenti con i chip della schermata MAIN;
- preferire riuso del componente/filtro esistente invece di duplicare UI;
- la lista risultati continua a occupare tutto lo spazio verticale restante.

In questa vista **non** mostrare:
- editor general note;
- lista ordine;
- sticky totale;

salvo necessità tecnica/accessibilità non invasiva.

#### Filtro categoria in CERCA (indipendente da MAIN)

All'apertura di `CERCA`:
- search category filter = `TUTTI` (default).

Il filtro della vista `CERCA` è **indipendente** dal filtro categoria della schermata MAIN.

Esempio:
- MAIN = `FRITTURA`;
- apro `CERCA` → search filter = `TUTTI`;
- in `CERCA` seleziono `PIZZE`;
- torno a MAIN → MAIN resta `FRITTURA`.

Riaprendo successivamente `CERCA`:
- search category filter riparte da `TUTTI`.

Nessuna persistenza DB/DataStore per questo stato UI (solo stato feature/in-memory della sessione di ricerca).

Semantica filtri (come in MAIN):
- `TUTTI` → nessun filtro categoria;
- `PIZZE` → `category == PIZZA`;
- `FRITTURA` → `category == FRITTURA`;
- `BIBITE` → `category == BIBITA`.

#### Query + categoria (AND)

Query e category filter lavorano in **AND**:
- risultato = prodotto che soddisfa la ricerca esistente **e** la categoria selezionata (se non `TUTTI`).

Esempio: query `pomodoro` + filtro `PIZZE` → solo pizze che matchano `pomodoro`.

Semantica ricerca: **invariata** rispetto al motore esistente:
- nome + ingredienti;
- case / accent tolerant;
- ranking corrente;
- solo prodotti attivi;
- **non** creare un secondo search engine.

Il category filter si applica ai candidati/risultati **senza** alterare ranking e normalizzazione del motore esistente.

`INDIETRO` (nella vista CERCA):
- torna alla schermata ordine corrente;
- stesso DRAFT;
- filtro MAIN invariato.

Quick-add (`+`) da risultato:
- persiste tramite lo stesso flow ORD esistente;
- **resta** nella schermata CERCA (non chiude la ricerca);
- query e category filter della ricerca restano invariati;
- nessuna modifica a merge policy, quantity rules, pricing, snapshots, total calculation.

Placeholder campo: `Cerca prodotto o ingrediente...` (o equivalente coerente).

### Totale live (ORD-022)

Nella schermata ordine DRAFT, il footer/area finale mostra chiaramente:

```text
TOTALE
€xx,xx
```

Regole UX:
- valore reattivo allo stato **persistito** Room (`order_items`), non a editor/form dirty o catalogo live;
- totale molto evidente e leggibile;
- accessibilità coerente con i pattern esistenti;
- rispettare `safeDrawing` / navigation insets;
- sticky nella schermata **principale**;
- **non** mostrare un secondo totale nella vista CERCA.

Empty DRAFT (nessuna riga):
```text
TOTALE
€0,00
```

Il totale resta visibile anche nello stato vuoto (insieme al messaggio empty state esistente). Non creare righe fittizie.

`COMPLETA`:
- **fuori scope ORD-022**;
- se il pulsante/placeholder esiste già, preservarlo senza aggiungere nuove business mutation;
- Acceptance / numerazione / salvataggio definitivo di `orders.totalCents` appartengono a task/milestone successive.

Semantica di calcolo, source of truth, `orders.totalCents` non-authoritative sul DRAFT, overflow e reattività: vedi `docs/02_BUSINESS_RULES.md` (Totale live DRAFT ORD-022).

Footer/sticky (solo schermata principale):
- `TOTALE` (live, ORD-022);
- `COMPLETA` (placeholder/preservato; non implementato da ORD-022).

### Empty state
Se nessuna riga:
`Aggiungi un prodotto per iniziare l'ordine.`

Un DRAFT senza items resta esistente (vuoto); Home/recovery seguono le regole già definite per draft vuoto.
L'azione `NOTE` resta disponibile anche su DRAFT vuoto.
Il `TOTALE` resta visibile a `€0,00` anche su DRAFT vuoto.

### Nessun risultato
`Nessun prodotto trovato.`

## 6. Dettaglio prodotto generico

Per tutte le categorie:
- nome;
- prezzo;
- quantità `[-] n [+]`;
- nota;
- modifica prezzo;
- salva;
- annulla.

Il dettaglio resta il flusso di editing avanzato. Il `+/-` e `RIMUOVI` della lista ordine (ORD-020) non sostituiscono e non alterano questo editor.

Per pizza aggiungere:
- `Aggiunte`;
- `Rimuovi ingrediente`.

Frittura/Bibita:
- nessuna sezione aggiunte/rimozioni.

## 7. Personalizzazione pizza

Layout:
```text
< Nome pizza >

Quantità
[-] 1 [+]

AGGIUNTE
[Aggiungi]

RIMOZIONI
[Rimuovi ingrediente]

NOTA
[________________]

PREZZO
€ 11,00
[Modifica prezzo]

[SALVA] [ANNULLA]
```

Se manual:
```text
€ 13,00  ✎
[Ripristina prezzo automatico]
```

### Lista rimozioni
Solo ingredienti della pizza.

### Lista aggiunte
Solo additions attive.

### `automaticExtrasPricing=false`
UI può mostrare:
`Le aggiunte non modificano automaticamente il prezzo di questo prodotto.`

Non menzionare nomi hardcoded.

## 8. Modifica riga aggregata pizza

Se pizza standard quantità > 1 e si apre una personalizzazione:

```text
Vuoi modificare una pizza o tutte?

[MODIFICA UNA]
[MODIFICA TUTTE]
[ANNULLA]
```

`Modifica una`:
- split atomico solo quando l'utente salva una personalizzazione valida.

Il cambio quantità tramite `+/-` della lista ordine non usa questo prompt.
Il caso editor `MODIFICA UNA` + cambio quantità nello stesso form resta fuori da ORD-020 e conserva il rifiuto conservativo corrente.

## 9. Anteprima DRAFT

Ordine visivo:
1. PIZZE
2. FRITTURA
3. BIBITE

Categorie vuote omesse.

Azioni:
- `MODIFICA`;
- `STAMPA` (bozza);
- `ACCETTA`;
- `ACCETTA E STAMPA`;
- `HOME`;
- `NUOVO ORDINE`.

`HOME` non elimina il draft.

`NUOVO ORDINE` mentre il DRAFT corrente esiste applica la regola single-draft:
- `RIPRENDI` (resta sul corrente);
- `ELIMINA E CREA NUOVO`;
- `ANNULLA`.

### Errore ordine vuoto
Non consentire apertura/accettazione oppure mostra:
`Aggiungi almeno un prodotto.`

## 10. Stato durante Accept

Proteggere doppio tap:
- disabilitare CTA;
- mostrare progress minimale.

Il dominio resta comunque responsabile dell'idempotenza.

## 11. Schermata Accepted

Dopo `ACCETTA` o `ACCETTA E STAMPA`:
- **non tornare automaticamente Home**.

Mostra:
- numero grande;
- ordine;
- totale;
- data/ora su schermo;
- `STAMPA`/`RISTAMPA`;
- `HOME`;
- `NUOVO ORDINE`.

Nascondi:
- `ACCETTA`;
- `ACCETTA E STAMPA`;
- modifica.

## 12. Stampa fallita

Se ordine già accettato:
```text
Ordine A37 salvato.

Impossibile stampare.

[RIPROVA]
[CHIUDI]
```

Se outcome fisico incerto:
```text
La connessione si è interrotta durante la stampa.
Verifica se la copia è uscita prima di riprovare.

[RIPROVA]
[CHIUDI]
```

Se bozza:
- stesso pattern;
- nessun numero;
- draft invariato.

## 13. Ordini di oggi

Lista:
- displayNumber;
- acceptedAt;
- totale;
- eventualmente count righe.

Ordine:
- acceptedAt DESC.

Empty:
`Nessun ordine accettato nella giornata operativa corrente.`

Tap:
- dettaglio.

## 14. Archivio

Controlli:
- ricerca numero;
- chip `OGGI`;
- `IERI`;
- `SCEGLI DATA`.

Risultati:
- displayNumber;
- data;
- ora;
- totale.

Stesso numero su più record:
- mostra data/ora per disambiguare.

## 15. Dettaglio Accepted

Mostra:
- numero;
- data/ora;
- sezioni;
- modifiche;
- note;
- prezzi;
- totale.

Azioni:
- `RISTAMPA`;
- `NUOVO ORDINE DA QUESTO`;
- `HOME`.

Nessun controllo edit.

## 16. Duplicazione

Se nessun draft:
- crea draft;
- naviga a ordine.

Se draft esistente:
```text
Esiste già un ordine in corso.

[RIPRENDI]
[ELIMINA E DUPLICA]
[ANNULLA]
```

## 17. Gestione menu

Landing:
- `IMPORTA ODS`;
- `PIZZE`;
- `FRITTURA`;
- `BIBITE`;
- `AGGIUNTE`.

Lista:
- ricerca;
- attivo/inattivo;
- prezzo;
- tap modifica.

### Modifica prodotto
Campi:
- nome;
- nome stampato;
- categoria;
- prezzo;
- ingredienti;
- `Prezzo automatico aggiunte` (`automaticExtrasPricing`);
- attivo.

Aggiunte:
- nome;
- nome stampato;
- prezzo;
- attivo.

Preferire disattivazione alla cancellazione.

## 18. Import ODS

Flusso:
1. `Seleziona file ODS`.
2. parsing.
3. validazione.
4. preview.
5. conferma.
6. import atomico.
7. risultato.

Preview:
```text
Nuovi: 12
Aggiornati: 3
Invariati: 35
Avvisi: 1
Errori: 0

[CONFERMA IMPORTAZIONE]
[ANNULLA]
```

Se errori bloccanti:
- disabilitare conferma;
- elencare foglio/riga/campo/messaggio.

## 19. Impostazioni numerazione

Radio/segmented:
- `Numerazione ordinata`;
- `Numerazione casuale progressiva`.

Cambio immediato per le prossime accettazioni.
Non riscrive ordini esistenti.

## 20. Impostazioni stampante

MVP consigliato:
- pairing effettuato nelle impostazioni Android;
- app elenca dispositivi Bluetooth già associati;
- selezione stampante;
- stato;
- test stampa.

Mostrare:
- nome dispositivo;
- indirizzo mascherato/tecnico solo se utile;
- `STAMPA DI PROVA`.

Errori:
- Bluetooth spento;
- permesso negato;
- nessun dispositivo selezionato;
- connessione fallita.

## 21. Stati di loading

Usare solo quando operazione percepibile:
- import ODS;
- accesso database iniziale se necessario;
- connessione/stampa.

Non mostrare spinner per ogni tap locale istantaneo.

## 22. Accessibilità

- Material 3.
- touch target almeno coerenti con linee guida Android;
- testo non inferiore a dimensioni leggibili;
- supporto font scaling;
- contrasto adeguato;
- `contentDescription` per icone non decorative;
- focus order sensato;
- non affidarsi a rosso/verde da soli;
- nomi lunghi con wrapping, non shrink aggressivo.
