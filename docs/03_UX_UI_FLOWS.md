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
- Conferma obbligatoria solo per azioni distruttive: eliminazione draft, sostituzione draft.

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
Ogni riga mostra:
- quantità;
- nome;
- modifiche;
- nota sintetica;
- prezzo finale/line total;
- affordance per modifica.

Footer/sticky:
- `TOTALE`;
- `COMPLETA`.

### Empty state
Se nessuna riga:
`Aggiungi un prodotto per iniziare l'ordine.`

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
