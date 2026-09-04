# 15 — Glossario — CASSA

**Accepted** — ordine definitivo salvato, immutabile.

**Addition / Aggiunta** — modificatore selezionabile per pizza, con prezzo listino >=0.

**automaticExtrasPricing** — flag prodotto: se false, additions non incrementano automaticamente il prezzo.

**businessDate** — giornata operativa assegnata in base alla soglia 05:00.

**DRAFT** — unico ordine modificabile in corso.

**displayNumber** — numero mostrato/stampato, non identità DB.

**ESC/POS** — famiglia di comandi per stampante termica.

**FinalUnitPrice** — prezzo unitario effettivo dopo regole e override.

**Ingredient** — ingrediente associato a pizza, usato per ricerca e rimozioni.

**Manual price** — override esplicito del prezzo automatico.

**normalizedName** — forma tecnica per matching/search; non sostituisce display text.

**Order snapshot** — copie di nomi/prezzi/modificatori conservate per storico.

**PricePrintMode.DETAILED** — stampa prezzi riga/additions quando coerenti.

**PricePrintMode.TOTAL_ONLY** — stampa solo totale.

**PrinterProfile** — configurazione device/layout/charset.

**Quick add** — pulsante `+` che aggiunge prodotto standard senza aprire detail.

**Random cycle** — blocco di massimo 2.600 numeri casuali distinti in una businessDate.

**Removal / Rimozione** — ingrediente della pizza escluso, senza riduzione prezzo.

**Ristampa** — nuova stampa di Accepted con stesso numero e snapshot, senza label dedicata.

**SPP/RFCOMM** — collegamento seriale Bluetooth Classic.

**Source order** — Accepted da cui è stato duplicato un Draft.

**draftSlot** — vincolo DB usato per garantire un solo DRAFT.

**printedName** — nome alternativo destinato alla stampa.

**listedPrice** — prezzo addition da catalogo.

**chargedPrice** — prezzo addition effettivamente addebitato.
