# 04 — Architettura Android — CASSA

## 1. Stack

| Layer | Scelta | Motivo |
|---|---|---|
| Piattaforma | Android nativo | Bluetooth/hardware e semplicità deployment |
| Linguaggio | Kotlin | Stack Android moderno |
| UI | Jetpack Compose + Material 3 | Stato dichiarativo, testabilità |
| Navigation | Navigation Compose | Routing |
| Stato async | Coroutines + Flow/StateFlow | Flussi Room/UI |
| DB | Room/SQLite | Transazioni locali, source of truth |
| DI | Hilt | Test/fake e separazione |
| Preferenze device | DataStore | stampante/profilo non transazionale |
| File picker | Storage Access Framework | permessi minimi |
| XML ODS | ZipInputStream + XmlPullParser | parser mirato |
| Print protocol | ESC/POS | portabilità stampanti |
| Trasporto | Bluetooth Classic/SPP | device test |
| Test | JUnit + Room tests + Compose tests + fakes | affidabilità |

`minSdk`: 26 consigliato.
`compileSdk/targetSdk`: stable corrente al momento dell'implementazione.

## 2. No backend

Nel v1:
- nessuna API server;
- nessuna autenticazione;
- nessun cloud;
- nessuna rete necessaria.

L'"API" dell'app è costituita da contratti Kotlin tra layer.

## 3. Modulo Gradle

Iniziare con:
`:app`

Non creare multi-module Gradle prematuro.

Separare tramite package. Estrarre moduli solo quando esiste un motivo concreto.

## 4. Package structure

```text
com.<company>.cassa
├── app
│   ├── CassaApplication.kt
│   ├── MainActivity.kt
│   └── navigation
├── core
│   ├── money
│   ├── datetime
│   ├── text
│   ├── result
│   └── coroutine
├── domain
│   ├── model
│   ├── repository
│   ├── usecase
│   ├── pricing
│   ├── numbering
│   ├── search
│   └── validation
├── data
│   ├── database
│   │   ├── entity
│   │   ├── dao
│   │   ├── relation
│   │   └── migration
│   ├── repository
│   ├── ods
│   ├── printer
│   │   ├── bluetooth
│   │   ├── escpos
│   │   ├── formatter
│   │   └── fake
│   └── preferences
├── feature
│   ├── home
│   ├── order
│   ├── preview
│   ├── today
│   ├── archive
│   ├── menu
│   ├── importmenu
│   └── settings
└── di
```

## 5. Dipendenze layer

Consentito:
```text
feature/ui -> domain
data -> domain
app -> feature/data/domain
```

Vietato:
```text
domain -> Compose
domain -> Room
domain -> BluetoothAdapter
domain -> Android Context
domain -> NETUM SDK
```

Eccezioni per value types Android-specific vanno isolate.

## 6. UI pattern

ViewModel:
- riceve use case;
- espone `StateFlow<UiState>`;
- riceve eventi/action;
- trasforma errori dominio in UI event/messaggi.

Compose screen:
```kotlin
@Composable
fun OrderScreen(
    state: OrderUiState,
    onAction: (OrderAction) -> Unit
)
```

Niente DAO/repository direttamente da Composable.

## 7. Modelli dominio principali

- Product
- Ingredient
- Addition
- Order
- OrderItem
- OrderItemAddition
- OrderItemRemoval
- ProductCategory
- OrderStatus
- NumberingMode
- BusinessDate
- Money
- PrinterState
- PrinterError
- PricePrintMode

## 8. Value object Money

Interno:
`Long cents`.

Operazioni:
- plus;
- minus solo dove ammesso;
- multiply by quantity;
- comparison;
- format locale italiano.

Non accettare `NaN`, floating rounding o stringhe non validate.

## 9. ClockProvider

```kotlin
interface ClockProvider {
    fun now(): Instant
}
```

Produzione:
`SystemClockProvider`.

Test:
`FakeClockProvider`.

Tutta la logica businessDate/acceptedAt usa provider.

## 10. BusinessDateCalculator

Pure Kotlin.

Input:
- Instant;
- ZoneId;
- business start minutes.

Output:
- LocalDate.

Nessun accesso a Activity/Context.

## 11. PricingCalculator

Pure Kotlin.

Input:
- base price;
- additions;
- automaticExtrasPricing;
- manual price.

Output:
- automaticExtrasTotal;
- automaticUnitPrice;
- finalUnitPrice.

Test esaustivi.

## 12. OrderLineMergePolicy

Pure Kotlin.

Decide se un quick add può incrementare una riga esistente.

Regole:
- pizza standard merge;
- pizza personalizzata no automatic merge;
- frittura standard merge;
- bibita standard merge.

Non usare equality generica dell'intero oggetto per decidere merge di personalizzate.

## 13. ProductSearchEngine

Dato catalogo attivo in memoria:
- normalizza query;
- calcola match class/rank;
- ritorna `SearchResult` con `matchReason`.

Esempio:
```kotlin
data class SearchResult(
    val product: Product,
    val rank: Int,
    val matchedIngredient: String? = null
)
```

## 14. Repositories

Contratti dominio consigliati:

```text
ProductRepository
AdditionRepository
OrderRepository
SettingsRepository
```

Printer separato come servizio/port.

### OrderRepository
Responsabilità:
- osservare draft;
- transazioni di scrittura;
- Accepted immutable;
- today/archive query;
- duplicate.

Non esporre Entity Room alla UI.

## 15. Use cases

Core:
- GetActiveDraft
- CreateDraft
- DeleteDraft
- AddProductToDraft
- UpdateOrderItem
- SplitStandardPizzaItem
- RemoveOrderItem
- SetManualPrice
- ResetAutomaticPrice
- CalculateOrderTotal
- AcceptOrder
- DuplicateAcceptedOrder
- GetTodayOrders
- SearchArchive
- AnalyzeMenuImport
- CommitMenuImport
- PrintDraft
- PrintAcceptedOrder
- TestPrinter

## 16. Room source of truth

Pattern:
```text
UI action
  -> ViewModel
  -> UseCase
  -> Repository
  -> Room transaction
  -> Flow emits
  -> ViewModel state
  -> Compose recomposes
```

Non aggiornare il carrello UI in modo ottimistico se non necessario.

## 17. Transazioni obbligatorie

- AcceptOrder.
- Split una/tutte.
- Delete/recreate draft.
- Duplicate accepted order.
- Commit import ODS.
- Modifiche item + children quando devono essere coerenti.

## 18. Impostazioni

### Room — business critical
`app_settings`:
- numberingMode;
- businessDayStartMinutes;
- timezoneId.

Motivo:
- AcceptOrder deve leggere un valore coerente nel perimetro DB.

### DataStore — device preferences
- selectedPrinter identifier;
- paper width/profile;
- charsPerLine calibrato;
- codePage;
- feed lines;
- supportsCut;
- PricePrintMode;
- eventuali preferenze UI.

## 19. ODS architecture

```text
URI
 -> ContentResolver InputStream
 -> OdsMenuParser
 -> RawMenuImport
 -> MenuImportValidator
 -> MenuImportPlan
 -> Preview UI
 -> CommitMenuImport
 -> Room transaction
```

Parser non scrive DB.

## 20. Printer architecture

```text
Order snapshot
 -> ReceiptComposer
 -> PrintableDocument
 -> EscPosEncoder
 -> PrinterDriver
 -> Bluetooth transport
 -> Physical printer
```

Interfacce:
```kotlin
interface PrinterDriver {
    suspend fun connect(profile: PrinterProfile): PrinterResult
    suspend fun print(data: ByteArray): PrinterResult
    suspend fun disconnect()
}

interface PrinterService {
    suspend fun printDraft(orderId: String): PrintResult
    suspend fun printAccepted(orderId: String): PrintResult
    suspend fun testPrint(): PrintResult
}
```

Implementazioni:
- `FakePrinterDriver`;
- `BluetoothEscPosPrinterDriver`.

## 21. Concorrenza stampa

`PrinterService` deve serializzare le stampe con `Mutex`.

La UI disabilita i pulsanti mentre stampa, ma la protezione reale è anche nel servizio.

## 22. Bluetooth MVP

Preferenza:
- pairing nel sistema Android;
- app seleziona un device già bonded.

Vantaggi:
- minore complessità;
- meno edge case discovery;
- permessi più contenuti.

Android 12+:
- gestire `BLUETOOTH_CONNECT`;
- `BLUETOOTH_SCAN` solo se si implementa discovery.

Pre-Android 12:
- seguire permessi necessari alla strategia scelta.

## 23. Error model

Sealed/domain errors:
- ValidationError
- OrderError
- ImportError
- PrinterError
- DatabaseError

Printer:
- BluetoothDisabled
- PermissionDenied
- PrinterNotConfigured
- ConnectionFailed
- ConnectionLost
- Timeout
- PrintFailed
- Unknown

Non propagare eccezioni raw alla UI.

## 24. Logging

Debug:
- eventi tecnici minimi.

Release:
- niente note ordine;
- niente full receipt;
- niente intero menu;
- niente hardware IDs non necessari;
- stack trace solo in strumenti locali se previsti e privacy-safe.

Nessun analytics cloud nel v1.

## 25. Backup

Disabilitare backup cloud automatico del DB nel v1 finché non viene definita una strategia di restore.

Conseguenza documentata:
- perdita/reset dispositivo può perdere archivio.

Backup/export manuale è post-MVP.

## 26. Build configuration

- Version catalog.
- Kotlin/AGP/Compose stable compatibili.
- KSP se richiesto da Room/Hilt della configurazione scelta.
- `debug` con fake/strumenti utili;
- `release` senza dati demo e log verbosi.

## 27. Alternative escluse per ora

Flutter/React Native:
- nessun vantaggio necessario per Android-only;
- aggiungono layer per Bluetooth.

Backend/BaaS:
- non richiesto.

Microservizi:
- non applicabili.

Full office library:
- costo/dimensione non giustificati per parser ODS mirato.

Vendor printer SDK:
- fallback solo se ESC/POS generico non copre hardware reale.
