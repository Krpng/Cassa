# 13 — Audit file `Menu giuseppe.ods`

## 1. Scopo

Questo documento fotografa il file ODS fornito durante la progettazione.
Non è una business rule: il file potrà essere corretto.

## 2. Struttura rilevata

### Foglio1 — prodotti
Header:
1. `Prodotto`
2. `Prezzo Asporto`
3. `Prezzo Sala`
4. `Nome stampato`
5. `Categoria`
6. `Ingredienti`

Righe dati: **54**.

Distribuzione attuale:
- Pizze: **41**
- Frittura: **13**
- Bibite: **0**

Il fatto che oggi non ci siano Bibite non rimuove la categoria Bibite dalla specifica.

### Foglio2 — aggiunte
Header:
1. `Prodotto`
2. `Prezzo`
3. `Nome stampato`

Righe dati: **40**.

### Foglio3
Vuoto.
Deve essere ignorato.

## 3. Stato campi opzionali

Nel file analizzato:
- tutte le 54 celle `Ingredienti` prodotto sono vuote;
- tutte le celle `Nome stampato` prodotto sono vuote;
- tutte le celle `Nome stampato` aggiunte sono vuote.

Conseguenze:
- fallback `Nome stampato -> Prodotto` deve funzionare;
- ricerca per ingredienti non può essere validata con questo sample finché gli ingredienti non vengono popolati.

## 4. Righe addition bloccanti rilevate

Indicizzazione con header come riga 1.

### Foglio2 riga 31
- Prodotto: `Cipolle`
- Prezzo: `Gorgonzola`

Errore:
`Gorgonzola` non è un prezzo valido.

### Foglio2 riga 32
- Prodotto: `Pomodoro sorrento`
- Prezzo: vuoto

Errore:
prezzo obbligatorio.

### Foglio2 riga 33
- Prodotto: `Gorgonzola`
- Prezzo: vuoto

Errore.

### Foglio2 riga 41
- Prodotto: `Mignon`
- Prezzo: vuoto

Errore.

Finché presenti, preview import deve mostrare errori e bloccare commit.

## 5. Valori €0 validi

Esempi rilevati:
- Pomodoro;
- Bionda;
- Ben cotta;
- Alta di pasta;
- Bassa di pasta.

Devono essere accettati come additions a zero.

## 6. Ortografia

Il file contiene stringhe come:
- `Margerita`;
- `Wrustel e patate`;
- `Prorcini`.

Il parser non deve correggerle.

Se l'utente le corregge cambiando realmente il nome:
- la reimportazione può trattarle come nuovi prodotti, secondo regola di rename.

## 7. Prezzo Sala

Nel sample è vuoto.
Anche se in futuro fosse popolato o invalido:
- ignorarlo completamente.

## 8. Product rows

Le righe prodotto osservate sono strutturalmente valide per i campi obbligatori:
- name;
- Prezzo Asporto;
- categoria.

Non assumere che dati correnti siano definitivi.

## 9. Checklist prima del primo import reale

- [ ] correggere `Cipolle | Gorgonzola`;
- [ ] prezzo `Pomodoro sorrento`;
- [ ] prezzo `Gorgonzola`;
- [ ] prezzo `Mignon`;
- [ ] aggiungere ingredienti alle pizze se si vuole ricerca/removal completa;
- [ ] aggiungere Bibite se previste nel menu operativo;
- [ ] valorizzare Nome stampato solo dove differisce;
- [ ] import preview con 0 errori;
- [ ] dopo import configurare `automaticExtrasPricing=false` su `Pizza fritta` e `Ripieno` (tramite dati/UI, mai hardcode nel dominio).
