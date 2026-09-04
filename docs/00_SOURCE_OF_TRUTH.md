# 00 — Source of Truth, precedenza e conflitti risolti

## Scopo

Questo documento impedisce a sviluppatori e coding agent di implementare vecchie proposte ormai superate.

Il materiale di progettazione deriva da:
- conversazioni iterative sul progetto Cassa;
- file di metodo App Architect;
- appunti specifici su pizzeria, stampante e menu;
- file reale `Menu giuseppe.ods`.

Durante la progettazione alcune idee sono cambiate. Questo pacchetto contiene la **versione consolidata finale v1**.

## Precedenza normativa

In caso di conflitto, applicare nell'ordine:

1. `AGENTS.md`.
2. Questo documento.
3. `01_PRODUCT_REQUIREMENTS.md` e `02_BUSINESS_RULES.md`.
4. Specifiche specialistiche `03`–`08`.
5. `09_TEST_PLAN.md`.
6. `10_IMPLEMENTATION_BACKLOG.md`.
7. `12_DECISION_LOG_OPEN_ISSUES.md`.
8. I file/appunti originali caricati, utilizzati come storico e contesto.

Se due documenti di questo pacchetto confliggono, **non scegliere autonomamente**: segnalare il conflitto.

## Decisioni finali che sostituiscono appunti intermedi

### Stampante
Finale:
- NETUM 80 mm Bluetooth/ESC-POS usata come **hardware di test**;
- cutter non necessario;
- architettura vendor-independent;
- futura stampante migliore sostituibile tramite driver.

Superato:
- Epson TM-m30III come stampante obbligatoria;
- dipendenza diretta da SDK Epson/NETUM.

### Fiscalità
Finale:
- la stampa è una comanda/ricevuta **non fiscale**;
- nessuna simulazione di Registratore Telematico.

### Pagamenti
Finale:
- il v1 corrente **non registra metodo di pagamento, contanti, carta o resto**.

Superato:
- appunti intermedi che descrivevano vendita + metodo pagamento.

### Ordine vs vendita
Il concetto di dominio v1 è **Order/Ordine**, con stati `DRAFT` e `ACCEPTED`.
Non creare un dominio separato `Sale/Vendita` finché non viene richiesto.

### Accessibilità e profili stampa
Finale:
- leggibilità e accessibilità base sono requisiti trasversali;
- il numero ordine deve essere molto visibile;
- nomi lunghi vanno a capo;
- non esiste nel v1 una UI obbligatoria `Standard/Grande/Extra grande`.

Esiste invece un'opzione tecnica `PricePrintMode`:
- `DETAILED` default;
- `TOTAL_ONLY` disponibile per calibrazione futura.

### Persistenza stato stampa
Finale:
- v1 gestisce stato stampa operativo/transiente;
- l'ordine accettato resta persistito;
- non è richiesta una tabella `print_jobs`.

Un print-job persistente è possibile evoluzione futura.

### Menu ODS
La specifica più recente include:
- `Nome stampato`;
- `Prezzo Sala` ignorato;
- matching/reimportazione per nome normalizzato;
- prodotti assenti dal nuovo file non vengono eliminati;
- rinomina reale viene trattata come nuovo prodotto.

### Identità prodotto
Finale consigliato:
- `products.normalizedName` univoco nell'insieme prodotti;
- la categoria è un attributo modificabile, non parte dell'identità di reimportazione;
- aggiunte hanno namespace/tabella separata, quindi possono condividere il nome con un prodotto.

### Impostazioni
Finale:
- impostazioni business critiche (`numberingMode`, inizio giornata, timezone) in Room;
- preferenze device/printer non transazionali possono stare in DataStore.

Questo evita che `AcceptOrder` dipenda da una preferenza non transazionale.

## Cosa è congelato

- Android nativo.
- Kotlin.
- Jetpack Compose.
- Room/SQLite.
- Hilt.
- Coroutines/Flow.
- Offline-first.
- Singolo dispositivo.
- Nessun account/backend.
- Menu Pizze/Frittura/Bibite.
- Aggiunte separate.
- Ricerca per prodotto + ingredienti.
- DRAFT autosalvato.
- Un solo DRAFT.
- Accettazione atomica.
- business day 05:00.
- numerazione sequenziale/casuale.
- archivio ordini accettati.
- duplicazione ordine.
- stampa 80 mm non fiscale.
- ESC/POS/Bluetooth.
- una copia automatica.
- bozza senza numero.
- ristampa senza etichetta "RISTAMPA".

## Cosa non è ancora fisicamente validato

- caratteri per riga NETUM;
- code page;
- simbolo euro;
- lettere accentate;
- feed lines;
- stabilità connessione;
- eventuale supporto cutter su future stampanti.

## Regola per nuove decisioni

Ogni modifica futura deve:
1. avere una motivazione;
2. indicare quale requisito sostituisce;
3. aggiornare business rules, test e backlog collegati;
4. non cambiare dati storici senza migrazione esplicita.
