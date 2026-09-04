# CASSA — Pre-Development Pack / Manuale Codex

Versione documentale: **1.0 — 2026-09-02**

Questo pacchetto è la specifica operativa del progetto **Cassa**, applicazione Android locale per acquisizione, personalizzazione, accettazione, archiviazione e stampa di ordini di pizzeria.

## Come usare questo pacchetto

1. Copiare `AGENTS.md`, `README_CODEX.md` e la cartella `docs/` nella root del repository Android.
2. Fare leggere a Codex prima `AGENTS.md`.
3. Leggere i documenti nell'ordine indicato in `docs/00_SOURCE_OF_TRUTH.md`.
4. Implementare **un task di backlog per volta**, salvo richiesta esplicita diversa.
5. Per ogni task:
   - leggere requisiti e regole collegate;
   - leggere i test collegati;
   - proporre un piano breve;
   - applicare modifiche minime;
   - eseguire test;
   - riportare file modificati, test e problemi aperti.
6. Non reinterpretare requisiti già congelati. Se una specifica sembra contraddittoria, fermarsi e applicare la gerarchia di fonti di `00_SOURCE_OF_TRUTH.md`.

## Indice rapido

- `AGENTS.md` — regole operative obbligatorie per il coding agent.
- `docs/00_SOURCE_OF_TRUTH.md` — gerarchia delle decisioni e conflitti risolti.
- `docs/01_PRODUCT_REQUIREMENTS.md` — PRD e scope MVP.
- `docs/02_BUSINESS_RULES.md` — regole di dominio definitive.
- `docs/03_UX_UI_FLOWS.md` — schermate, stati, flussi, microcopy.
- `docs/04_ANDROID_ARCHITECTURE.md` — stack e architettura Android.
- `docs/05_DATABASE_SCHEMA.md` — schema Room/SQLite e transazioni.
- `docs/06_ODS_IMPORT_SPEC.md` — importazione menu ODS.
- `docs/07_PRINTING_SPEC.md` — stampa Bluetooth ESC/POS 80 mm.
- `docs/08_SECURITY_PRIVACY_AND_NFR.md` — requisiti non funzionali, privacy, sicurezza.
- `docs/09_TEST_PLAN.md` — piano test completo.
- `docs/10_IMPLEMENTATION_BACKLOG.md` — milestone e task ordinati.
- `docs/11_CODEX_WORKFLOW.md` — modalità di lavoro e prompt.
- `docs/12_DECISION_LOG_OPEN_ISSUES.md` — decisioni, assunzioni, punti ancora da verificare.
- `docs/13_CURRENT_MENU_AUDIT.md` — audit del file `Menu giuseppe.ods` fornito come esempio.
- `docs/14_RELEASE_CHECKLIST.md` — checklist per build utilizzabile.
- `docs/15_GLOSSARY.md` — glossario.
- `docs/16_TRACEABILITY_MATRIX.md` — tracciabilità requisito → task → test.

## Regola fondamentale

Il valore dell'MVP è la velocità e affidabilità del flusso:

`Nuovo ordine → aggiunta/personalizzazione → anteprima → accettazione → archivio → stampa`

L'app è **offline-first**, su **un singolo dispositivo Android**, senza backend e senza login.

La stampa è **non fiscale**. La stampante NETUM 80 mm è il dispositivo di test iniziale, non un vincolo permanente dell'architettura.

## Stato delle decisioni

Sono congelati per v1:
- piattaforma Android nativa;
- Kotlin + Jetpack Compose;
- Room/SQLite;
- un solo DRAFT attivo;
- business day alle 05:00;
- numerazione sequenziale o casuale giornaliera;
- categorie Pizze / Frittura / Bibite;
- aggiunte e rimozioni pizza;
- prezzo manuale;
- import ODS;
- archivio ordini accettati;
- duplicazione ordine;
- Bluetooth ESC/POS;
- stampa 80 mm, una copia, bozza e ristampa.

Restano da calibrare fisicamente:
- profilo reale della stampante NETUM;
- charset/€;
- caratteri per riga;
- avanzamento carta;
- stabilità Bluetooth.

Non sono parte del v1 corrente:
- pagamenti contanti/carta;
- integrazione POS bancario;
- fiscale/RT;
- cloud;
- multi-device;
- account/login;
- iOS/web;
- magazzino avanzato;
- CRM;
- fatturazione elettronica;
- scontistica generale;
- backup/export automatico.
