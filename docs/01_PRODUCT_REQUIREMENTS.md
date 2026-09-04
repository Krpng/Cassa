# 01 — Product Requirements Document — CASSA

## 1. Executive summary

Cassa è un'app Android locale per una pizzeria, pensata per velocizzare la composizione degli ordini al banco/cassa, permettere personalizzazioni precise, salvare ogni ordine accettato in archivio e stampare una comanda non fiscale su stampante termica 80 mm via Bluetooth/ESC-POS.

Il prodotto deve funzionare senza Internet e senza account. La priorità è la rapidità operativa, la chiarezza dei dati e la robustezza contro crash, doppi tap e problemi di stampa.

## 2. Utente principale

Operatore della pizzeria al banco/cassa.

Contesto:
- uso ripetuto e veloce;
- dispositivo Android dedicato o semi-dedicato;
- menu relativamente piccolo;
- necessità di trovare prodotti rapidamente;
- frequenti modifiche alle pizze;
- necessità di recuperare e ristampare ordini;
- rete non necessaria.

## 3. Problema

La composizione manuale di ordini con quantità, aggiunte, rimozioni e note è soggetta a:
- lentezza;
- errori di trascrizione;
- prezzo errato;
- difficoltà di ricerca;
- perdita dell'ordine se l'app viene chiusa;
- ristampe non affidabili;
- confusione sulla numerazione giornaliera.

## 4. Obiettivi MVP

1. Creare un ordine in pochi tap.
2. Cercare prodotti per nome o ingrediente.
3. Personalizzare le pizze senza liste inutilmente grandi.
4. Calcolare prezzi e totale in modo deterministico.
5. Salvare il DRAFT dopo ogni modifica.
6. Accettare l'ordine una sola volta.
7. Assegnare numerazione corretta per giornata operativa.
8. Consultare gli ordini accettati.
9. Duplicare un ordine storico.
10. Importare/aggiornare il menu da ODS.
11. Stampare bozza/finale/ristampa su 80 mm.
12. Cambiare stampante in futuro senza riscrivere il dominio.

## 5. Scope MVP — MUST

### Ordini
- nuovo ordine;
- un solo DRAFT;
- autosave;
- recupero DRAFT dopo riavvio;
- ricerca;
- filtri categoria;
- quick add;
- personalizzazione;
- quantità;
- note riga;
- nota generale;
- aggiunte;
- rimozioni;
- prezzo manuale;
- totale;
- anteprima;
- accettazione;
- accettazione + stampa;
- bozza stampabile;
- immutabilità Accepted.

### Menu
- Pizze;
- Frittura;
- Bibite;
- Ingredienti;
- Aggiunte;
- gestione manuale;
- attiva/disattiva;
- import ODS;
- preview import;
- rollback su errore.

### Archivio
- ordini di oggi;
- archivio per data;
- ricerca numero;
- dettaglio;
- ristampa;
- duplicazione.

### Numerazione
- sequenziale;
- casuale;
- stato separato per businessDate;
- cambio modalità in giornata;
- no numero su DRAFT.

### Stampa
- Bluetooth;
- ESC/POS;
- 80 mm;
- una copia;
- numero grande;
- categorie ordinate;
- prezzi dettagliati default;
- totale;
- retry;
- test stampante.

## 6. SHOULD v1 / rifiniture importanti

- UI accessibile e leggibile;
- supporto corretto font scale Android;
- test hardware ripetuti;
- messaggi errore contestuali;
- empty state e loading state;
- import preview dettagliata;
- fake printer per sviluppo;
- build release pulita e senza log sensibili.

## 7. Fuori scope v1

Non implementare:
- login;
- utenti/ruoli;
- cloud;
- sincronizzazione multi-device;
- iOS;
- web;
- pagamenti;
- contanti/carta/resto;
- integrazione POS bancario;
- Registratore Telematico/fiscalità;
- fatturazione elettronica;
- magazzino;
- CRM;
- loyalty;
- marketplace;
- AI;
- geolocalizzazione;
- notifiche push;
- chat;
- scontistica generale;
- cancellazione ordini Accepted;
- modifica ordini Accepted;
- backup/export automatico;
- stampa universale per ogni modello del mercato.

## 8. Flusso core

```text
HOME
  ↓
NUOVO ORDINE
  ↓
RICERCA/FILTRO
  ↓
QUICK ADD oppure DETTAGLIO
  ↓
PERSONALIZZAZIONE / QUANTITÀ
  ↓
ORDINE CORRENTE + TOTALE
  ↓
COMPLETA
  ↓
ANTEPRIMA
  ├─ MODIFICA
  ├─ STAMPA BOZZA
  ├─ ACCETTA
  └─ ACCETTA E STAMPA
        ↓
      ACCEPTED
        ↓
STAMPA / HOME / NUOVO ORDINE
```

## 9. Requisiti non funzionali principali

- Offline completo per flusso core.
- Nessuna perdita dell'intero ordine per crash.
- Transazioni atomiche sulle operazioni critiche.
- Storico riproducibile tramite snapshot.
- Nessun calcolo monetario floating point.
- Dominio indipendente dalla stampante specifica.
- Permessi Android minimi.
- Nessun dato business inviato a servizi esterni.
- UI in italiano.
- Testabilità elevata.

## 10. Criteri di successo funzionale

Il MVP è utilizzabile quando:
- un ordine completo può essere creato, accettato, ritrovato e ristampato;
- un crash non elimina il DRAFT;
- la numerazione non duplica all'interno delle regole previste;
- 2.600 codici casuali del primo ciclo sono distinti;
- l'import ODS non produce aggiornamenti parziali;
- modifica menu non altera ordini storici;
- fallimento stampa non annulla Accepted;
- la NETUM di test stampa una comanda leggibile.

## 11. Assunzioni

- Un solo operatore/dispositivo alla volta.
- Inizio giornata operativa fissato a 05:00.
- Timezone `Europe/Rome`.
- Menu di dimensioni compatibili con ricerca in memoria.
- La stampante test è già associabile via Bluetooth dal dispositivo Android.

## 12. Rischi

- Esito fisico di una stampa può essere incerto se la connessione cade dopo l'invio dei byte.
- ODS fornito può contenere errori di dati.
- Il modello NETUM può avere subset ESC/POS/code page peculiari.
- Mancanza di backup nel v1 comporta rischio perdita dati se il dispositivo viene perso o resettato.
