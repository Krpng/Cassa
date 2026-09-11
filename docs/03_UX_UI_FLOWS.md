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

### Header
- back/home non distruttivo;
- eventuale indicazione `Ordine in corso`.

### Ricerca
Placeholder:
`Cerca prodotto o ingrediente...`

### Filtri
`TUTTI | PIZZE | FRITTURA | BIBITE`

Filtro e ricerca si combinano.

### Risultati
Riga:
```text
Margherita
Pomodoro, Fior di latte... (opzionale/compatto)
€ 7,00                         [+]
```

Se match ingrediente:
```text
Margherita
Contiene: Parmigiano
€ 7,00                         [+]
```

Tap riga:
- apre dettaglio.

Tap `+`:
- quick add standard.

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

### Nota ordine (ORD-021)

Nella schermata principale del DRAFT (`Nuovo ordine` / ordine in corso), dopo l'elenco delle righe e prima dell'area finale totale/azioni, una sezione distinta:

```text
NOTA ORDINE
[campo multilinea]
[SALVA NOTA]
```

Regole UX:
- chiaramente riconoscibile come nota dell'**intero ordine**, non di una singola riga;
- campo multilinea leggibile, con scrolling/layout adeguati;
- salvataggio **solo** tramite `SALVA NOTA` (nessun autosave per carattere);
- `SALVA NOTA` può essere disabilitato quando il testo editor coincide con il valore persistito;
- touch target `>= 48dp`;
- con tastiera aperta: campo usabile e `SALVA NOTA` raggiungibile (`imePadding` / `navigationBarsPadding` appropriati);
- accessibilità coerente con i pattern esistenti;
- la sezione **non** è sticky se il layout attuale non lo richiede;
- i controlli ORD-020 (`+/-`, `RIMUOVI`, tap riga → dettaglio) restano invariati.

Semantica di salvataggio/empty/null, guard DRAFT/ACCEPTED, protezione editor dirty da riemissioni Flow, e recovery: vedi `docs/02_BUSINESS_RULES.md` (Nota generale ordine ORD-021).

`orders.generalNote` non attiva l'highlight viola delle pizze personalizzate.

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
- non cambiare il layout più del necessario.

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

Footer/sticky:
- `TOTALE` (live, ORD-022);
- `COMPLETA` (placeholder/preservato; non implementato da ORD-022).

### Empty state
Se nessuna riga:
`Aggiungi un prodotto per iniziare l'ordine.`

Un DRAFT senza items resta esistente (vuoto); Home/recovery seguono le regole già definite per draft vuoto.
La sezione `NOTA ORDINE` resta disponibile anche su DRAFT vuoto.
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
