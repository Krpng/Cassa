# 06 — Specifica Import Menu ODS — CASSA

## 1. Formato supportato

MVP:
- `.ods` OpenDocument Spreadsheet.

Non è requisito:
- `.xlsx`;
- `.xls`;
- Google Sheets remoto.

Accesso file:
- Android Storage Access Framework (`OpenDocument`);
- nessun permesso storage globale.

## 2. Riconoscimento fogli

Non dipendere da nomi `Foglio1`, `Foglio2`.

Scansionare i fogli e riconoscerli per header normalizzati.

### Foglio prodotti
Header richiesti:
- `Prodotto`
- `Prezzo Asporto`
- `Categoria`

Header opzionali:
- `Prezzo Sala` — sempre ignorato;
- `Nome stampato`;
- `Ingredienti`.

### Foglio aggiunte
Header richiesti:
- `Prodotto`
- `Prezzo`

Header opzionale:
- `Nome stampato`.

Se più fogli soddisfano ambiguamente lo stesso ruolo:
- errore bloccante.

Fogli vuoti:
- ignorati.

## 3. Campi prodotti

### Prodotto
Obbligatorio.
Display name.

### Prezzo Asporto
Obbligatorio.
Unico prezzo prodotto usato dall'app.

### Prezzo Sala
Ignorato completamente:
- non salvare;
- non validare;
- non bloccare import anche se vuoto o non numerico.

### Nome stampato
Opzionale.
Se colonna presente:
- valore non vuoto -> salva;
- vuoto -> salva null, fallback runtime a Prodotto.

Se colonna assente:
- per nuovo prodotto null;
- per update preservare valore esistente per evitare cancellazione involontaria di un campo che il file non sta gestendo.

### Categoria
Obbligatoria.
Valori ammessi, normalizzati case/space:
- Pizze -> PIZZA;
- Frittura -> FRITTURA;
- Bibite -> BIBITA.

Altro:
- errore bloccante.

### Ingredienti
Opzionali, separati da virgola.

Se colonna presente:
- cella vuota -> lista vuota e sostituisce ingredienti del prodotto;
- valorizzata -> parse/replace.

Se colonna assente:
- nuovo prodotto -> lista vuota;
- update -> preserva ingredienti esistenti.

## 4. Campi aggiunte

### Prodotto
Nome addition obbligatorio.

### Prezzo
Obbligatorio.
`0,00` valido.

### Nome stampato
Stesse regole prodotti.

## 5. Normalizzazione

### Display
Non correggere ortografia.

Consentito:
- trim;
- collapse spazi multipli.

Non fare:
- `Margerita -> Margherita`;
- `Wrustel -> Würstel`;
- `Prorcini -> Porcini`;
- traduzioni.

### Technical normalized name
Per confronto:
1. trim;
2. collapse whitespace;
3. lowercase locale-neutral;
4. normalizzazione Unicode;
5. rimozione diacritici per matching tecnico.

Il display originale resta intatto.

Conseguenza:
- differenze case/spazi/accenti possono identificare lo stesso record;
- vera modifica ortografica del nome produce record nuovo.

## 6. Parsing prezzi

Accettare:
- ODS numeric cell;
- `3`;
- `3,00`;
- `3.00`;
- `€ 3,00`;
- spazi innocui.

Convertire a centesimi con decimal parsing, non Double.

Regole:
- >= 0;
- massimo due decimali significativi;
- input ambiguo/non numerico -> errore;
- nessun rounding silenzioso di più di 2 decimali.

## 7. Righe

Riga completamente vuota:
- ignora.

Riga parzialmente compilata:
- valida tutti i campi richiesti;
- errore bloccante se manca un obbligatorio.

Non ignorare silenziosamente righe problematiche.

## 8. Duplicati input

Prodotti:
- duplicato `normalizedName` nello stesso import -> errore bloccante.

Aggiunte:
- duplicato normalizedName -> errore.

Ingredienti nella stessa cella:
- dedup normalizzato;
- preserva prima forma display e ordine.

## 9. Matching con DB

Prodotto importato:
- se normalizedName esiste -> UPDATE;
- altrimenti -> NEW.

Addition:
- stesso criterio nella tabella additions.

Prodotto/addition DB assente dal nuovo ODS:
- non cancellare;
- non disattivare;
- non modificare.

Rinomina reale:
- nuovo normalizedName -> nuovo record;
- vecchio record rimane.

## 10. Campi non presenti in ODS

### `automaticExtrasPricing`
Non viene importato.

Nuovo prodotto:
- default `true`.

Update:
- preservare valore DB.

Configurare manualmente prodotti speciali dall'app.
Non hardcodare nomi.

### `active`
Nuovo:
- `true`.

Update:
- preservare stato DB.

ODS non riattiva automaticamente un prodotto inattivo.

## 11. Campi aggiornati

Quando la colonna è presente nel file:
- price;
- category;
- printedName;
- ingredients.

Nome:
- è la chiave di match e display importato.

`updatedAt` aggiornato solo su reali modifiche.

## 12. Pipeline

```text
User selects URI
 -> read ZIP
 -> parse content.xml
 -> detect sheets
 -> raw rows
 -> normalize
 -> validate
 -> compare DB
 -> ImportPlan
 -> preview
 -> user confirm
 -> single DB transaction
 -> success
```

## 13. Parser ODS

ODS è ZIP.

Leggere `content.xml`.

Gestire almeno:
- table:table;
- table:table-row;
- table:table-cell;
- text:p;
- `table:number-columns-repeated`;
- `table:number-rows-repeated`;
- tipi numeric/currency e testo;
- righe/celle vuote;
- namespace ODF.

Non assumere che ogni cella sia rappresentata una volta.

Limitare protezioni:
- evitare espansioni enormi di repeated rows/columns;
- impostare limiti ragionevoli per un menu;
- errore chiaro su file malformato.

## 14. Preview

Modello:
```text
Nuovi
Aggiornati
Invariati
Warnings
Errors
```

Per ogni errore:
- nome foglio;
- numero riga;
- campo;
- valore;
- messaggio.

Esempio:
```text
Foglio2, riga 31, Prezzo:
"Gorgonzola" non è un prezzo valido.
```

## 15. Warnings

Warning non bloccanti possibili:
- foglio vuoto;
- nome stampato molto lungo;
- nessun prodotto in una categoria ammessa;
- tutte celle ingredienti vuote.

Non trasformare warning in correzioni automatiche.

## 16. Commit

Una sola transazione.

Se fallisce:
- rollback;
- menu precedente intatto.

Non fare upsert durante la fase di parsing/preview.

## 17. Test obbligatori

- file valido;
- Prezzo Sala invalido ignorato;
- 0 addition valido;
- product price non numerico blocca;
- categoria sconosciuta blocca;
- partial row blocca;
- fully empty ignora;
- duplicate product blocca;
- duplicate addition blocca;
- sheet rename funziona;
- empty third sheet ignora;
- update prodotto;
- add nuovo;
- assente resta;
- rollback;
- printedName fallback;
- ingredients parse/dedup;
- repeated ODS cells.

## 18. Nota sul file reale fornito

Vedere `13_CURRENT_MENU_AUDIT.md`.

Il file attuale ha struttura compatibile, ma contiene alcune righe addition con prezzi non validi/mancanti e quindi **non deve essere considerato un import di produzione finché corretto**.
