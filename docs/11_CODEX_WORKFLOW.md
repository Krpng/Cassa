# 11 — Workflow Codex — CASSA

## 1. Obiettivo

Usare Codex come senior engineer controllato da specifiche, non come generatore one-shot dell'intera app.

## 2. Non usare prompt

Da evitare:
`Crea tutta l'app Cassa completa.`

Motivo:
- scope enorme;
- salti di requisiti;
- refactor imprevedibili;
- test insufficienti.

## 3. Prompt di avvio repository

```text
Analizza il repository Cassa senza modificare file.

Leggi:
- AGENTS.md
- README_CODEX.md
- docs/00_SOURCE_OF_TRUTH.md
- docs/04_ANDROID_ARCHITECTURE.md
- docs/10_IMPLEMENTATION_BACKLOG.md

Restituisci:
1. stack rilevato;
2. struttura cartelle;
3. stato rispetto al backlog;
4. task successivo non completato;
5. rischi o discrepanze.

Non implementare nulla.
```

## 4. Prompt task

```text
Implementa esclusivamente il task <ID> del progetto Cassa.

Prima:
1. leggi AGENTS.md;
2. leggi la definizione del task in docs/10_IMPLEMENTATION_BACKLOG.md;
3. leggi i requisiti collegati;
4. leggi i test pertinenti in docs/09_TEST_PLAN.md;
5. ispeziona i file del repository.

Poi restituisci un piano massimo 8 punti.

Implementa con modifiche minime.
Non iniziare task successivi.
Non cambiare stack/requisiti.
Aggiungi o aggiorna i test collegati.
Esegui i test pertinenti.

Consegna:
- stato task;
- riepilogo;
- file modificati;
- test con PASS/FAIL;
- decisioni tecniche;
- problemi aperti.
```

## 5. Prompt verifica milestone

```text
Verifica la milestone <Mx> senza aggiungere nuove feature.

Confronta implementazione con:
- requirements;
- business rules;
- backlog;
- test plan.

Esegui build e test applicabili.
Elenca:
- task completi;
- task incompleti;
- regressioni;
- debito tecnico bloccante;
- demo manuale da eseguire.

Non fare refactor non necessari.
```

## 6. Prompt debug

```text
Diagnostica questo bug di Cassa.

Task/regola collegata:
<...>

Atteso:
<...>

Attuale:
<...>

Riproduzione:
1. ...

Prima analizza senza modificare.
Individua la causa minima.
Poi proponi una patch limitata e i test di regressione.
Non cambiare requisiti per adattarli al bug.
```

## 7. Prompt hardware spike

```text
Lavora esclusivamente sullo spike Bluetooth/ESC-POS.

Non modificare domain/order/database salvo contratto PrinterDriver già previsto.
Obiettivo: connettere la stampante bonded selezionata e stampare una test page.

Verifica:
- connect;
- accents;
- euro;
- width;
- long text;
- feed;
- repeated prints;
- disconnect/reconnect.

Isola ogni workaround hardware nel driver/profile.
```

## 8. Prompt code review

```text
Esegui code review della modifica relativa al task <ID>.

Controlla:
- rispetto AGENTS.md;
- business rules;
- race/concurrency;
- transazioni;
- snapshot;
- no Double per money;
- error handling;
- test mancanti;
- regressioni.

Non modificare codice finché non hai elencato i problemi per severità.
```

## 9. Report obbligatorio

```text
Task: ORD-019

Stato: COMPLETATO

Modifiche:
- ...

Test:
- ORDER-010 PASS
- ORDER-012 PASS

File:
- ...

Rischi/limiti:
- nessuno

Prossimo task suggerito:
ORD-020
```

Il suggerimento non autorizza l'implementazione automatica del task successivo.

## 10. Regole di stop

Codex deve fermarsi se:
- manca una decisione critica;
- specifiche del pack si contraddicono;
- sarebbe necessaria una migrazione distruttiva;
- una libreria necessaria cambia il modello architetturale;
- hardware non supporta il contratto previsto;
- test business-critical falliscono.

## 11. Cosa può decidere autonomamente

Può scegliere:
- nomi di funzioni privati;
- dettagli layout non normativi;
- piccole refactor locali;
- struttura test fixture;
- implementazione interna equivalente.

Non può scegliere:
- nuove feature;
- cambio DB;
- cambio businessDate;
- change numbering semantics;
- merge personalizzate;
- hardcoded special products;
- cancellazione Accepted;
- nuovo backend;
- nuova fiscalità;
- cambiare output stampa normativo.

## 12. Nuove dipendenze

Prima di aggiungere:
- spiegare perché SDK/Jetpack non basta;
- impatto APK/manutenzione;
- licenza se rilevante;
- testabilità.

Non introdurre SDK vendor printer come prima scelta.

## 13. Modifiche schema

Task DB deve:
- aggiornare Entity;
- DAO/mapper;
- migration;
- schema export;
- migration tests;
- docs se contratto cambia.

## 14. Modifiche business rules

Richiedono:
- aggiornamento `02_BUSINESS_RULES.md`;
- test plan;
- backlog/traceability;
- decision log.

Non cambiare in modo silenzioso.

## 15. Fine progetto v1

Prima di considerare il progetto pronto:
- M0–M10 completate per P0/P1;
- release checklist;
- hardware test;
- ODS file di produzione corretto;
- smoke test reale.
