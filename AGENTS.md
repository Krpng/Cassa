# AGENTS.md — Regole obbligatorie per Codex sul progetto CASSA

## 1. Prima di modificare codice

Leggi nell'ordine:
1. `README_CODEX.md`
2. `docs/00_SOURCE_OF_TRUTH.md`
3. il documento funzionale collegato al task;
4. `docs/02_BUSINESS_RULES.md`;
5. `docs/09_TEST_PLAN.md`;
6. la sezione del task in `docs/10_IMPLEMENTATION_BACKLOG.md`.

Non iniziare l'implementazione se il task non ha obiettivo, dipendenze e criteri di accettazione sufficientemente chiari.

## 2. Metodo di lavoro

Per ogni task:
1. identifica l'ID del task;
2. ispeziona il repository e i file rilevanti;
3. restituisci un piano di massimo 8 punti;
4. modifica solo ciò che serve al task;
5. aggiungi/aggiorna i test associati;
6. esegui build/test pertinenti;
7. non proseguire automaticamente al task successivo;
8. consegna un report finale.

Formato report:

```text
Task: <ID>

Stato:
COMPLETATO | PARZIALE | BLOCCATO

Implementazione:
- ...

Test eseguiti:
- <test> PASS/FAIL

File modificati:
- ...

Decisioni tecniche:
- ...

Problemi aperti:
- ...
```

## 3. Regole architetturali non negoziabili

- UI Compose senza business logic.
- Flusso: `Compose -> ViewModel -> Use Case -> Repository -> Data source`.
- Room/SQLite è la source of truth per ordini e menu.
- Non inserire dipendenze NETUM nel dominio.
- Stampa astratta dietro `PrinterService`/`PrinterDriver`.
- ESC/POS separato dal trasporto Bluetooth.
- Nessun backend, login o cloud nel v1.
- Nessun uso di `Double`/`Float` per il denaro: usare centesimi interi (`Long`).
- Nessuna business rule basata sul nome `"Pizza Fritta"` o `"Ripieno"`: usare `automaticExtrasPricing`.
- Gli ordini `ACCEPTED` sono immutabili.
- Un ordine viene stampato dopo il commit dell'accettazione, mai dentro la transazione DB.
- Il fallimento stampa non annulla l'ordine accettato.
- Un DRAFT viene persistito a ogni modifica significativa.
- Un solo DRAFT non vuoto può essere attivo.
- Non assegnare numero alla bozza.
- Non consumare numeri su stampa bozza.
- `businessDate` viene calcolata al momento dell'accettazione.
- Non rendere `displayNumber` chiave univoca globale.
- Le ristampe usano snapshot storici, non il menu corrente.
- Le personalizzazioni pizza inserite separatamente non vengono deduplicate automaticamente.
- Non introdurre correzioni ortografiche automatiche dei dati ODS.

## 4. Regole di scope

Non implementare senza task esplicito:
- pagamenti;
- cassa fiscale/RT;
- integrazione bancaria;
- cloud/sync;
- account/login;
- iOS/web;
- analytics cloud;
- sconti;
- magazzino;
- CRM;
- backup/export;
- print job persistenti;
- refactor globali;
- microservizi;
- nuove categorie prodotto oltre PIZZA/FRITTURA/BIBITA.

## 5. Dipendenze

- Preferire librerie AndroidX/Jetpack stabili.
- Centralizzare versioni in `gradle/libs.versions.toml`.
- Non aggiungere nuove librerie se il task è risolvibile in modo semplice con SDK/Jetpack.
- Per ODS, preferire parser mirato ZIP + XML invece di una suite Office pesante, salvo evidenza tecnica contraria.
- Motivare ogni nuova dipendenza.

## 6. Test

Ogni task business-critical deve avere test.
Priorità massima:
- pricing;
- business date;
- numerazione;
- merge/split righe;
- accettazione atomica;
- crash recovery;
- import ODS/rollback;
- formatter di stampa;
- retry stampa.

Non dichiarare un task DONE se i test associati falliscono.

## 7. Error handling

- Errori tipizzati a livello dominio/servizio.
- Niente `catch(Exception)` silenziosi.
- Niente messaggi tecnici grezzi all'utente.
- Niente log release con note ordine, contenuto completo degli ordini o identificativi hardware inutili.
- Se l'esito fisico di una stampa è incerto dopo perdita connessione, informare l'operatore prima del retry.

## 8. Modifica requisiti

Non modificare i documenti per giustificare un'implementazione diversa.
Se il requisito appare impossibile o incoerente:
1. non inventare;
2. descrivi il conflitto;
3. proponi la modifica minima;
4. attendi decisione prima di cambiare il contratto funzionale.
