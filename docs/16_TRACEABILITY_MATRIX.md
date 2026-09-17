# 16 — Traceability Matrix — CASSA

Questa matrice collega requisiti, backlog e test.

| Area/Requisito | Task principali | Test principali |
|---|---|---|
| Money cents | CORE-001 | PRICE-* |
| Business day 05:00 | CORE-003/004 | DATE-001..005 |
| Search ranking | CORE-002, MENU-005 | SEARCH-001..008 |
| Single Draft | DB-003/008, ORD-001..004 | DRAFT-001..006 |
| Autosave DB | ORD-008..022 | DRAFT-001/002 |
| Pizza merge | CORE-007, ORD-008 | ORDER-001..006 |
| Custom pizza no merge | ORD-016 | ORDER-003..006 |
| Modify one/all | ORD-018/019 | ORDER-010..012 |
| Remove line / change quantity | ORD-020 | ORDER-013..025 |
| General note | ORD-021 | ORDER-026..039 |
| Live total from persisted items | ORD-022 | ORDER-040..058 |
| Compact order workspace (note/search shell) | M5 UX refinement | ORDER-059..074 |
| Search view category filters (independent of MAIN) | M5 UX refinement | ORDER-075..083 |
| Additions | ORD-011/013 | PRICE, PRINT |
| Removals | ORD-012 | PRICE-002, PRINT-T006 |
| Manual price | ORD-014/015 | PRICE-004/005 |
| Accepted immutable | ACCEPT-003/005/007 | ACCEPT-T003/007 |
| Atomic accept | ACCEPT-003/004 (ACCEPT-003 NEXT) | ACCEPT-T001..005, ACCEPT-T013..018 |
| Acceptance preview (zero write / zero number) | ACCEPT-001 COMPLETE; ACCEPT-002 COMPLETE (satisfied by ACCEPT-001) | ACCEPT-T008..012, ACCEPT-T020 (T008/T009/T020 cover ACCEPT-002 actions; T010..012 preview content/order) |
| Accept total snapshot from persisted items | ACCEPT-003 (NEXT; NOT STARTED) | ACCEPT-T012/013/016 |
| Post-accept M6 UI (STAMPA disabled) | ACCEPT-006/007 | ACCEPT-T006/019 |
| Sequential numbering | NUM-001 | NUM-T001..006, NUM-T020/T021, NUM-T028 |
| Random numbering (FREEZE-A) | NUM-002..004, DB-010 | NUM-T010..024, NUM-T029 |
| RANDOM seed init marker | DB-010, D-042 | NUM-T018, NUM-T020..024 |
| Mode switch + Settings numbering UI (D-040 RESOLVED) | NUM-005 | NUM-T006, NUM-T015, NUM-T025..035 |
| Today (current business day) | ARCH-001 COMPLETE | ARCH-T001..003 |
| Daily Accepted purge / retention | RET-001 | RET-T001..008 |
| Current-day Accepted detail | ARCH-004 COMPLETE (D-045) | ARCH-T010..026; SNAP-001..004 (immutability retained) |
| Archive date filters / cross-day search / historical archive | ARCH-002/003/005 **OBSOLETE** | ARCH-T004/T005 **OBSOLETE** |
| Historical snapshots as archive feature | ARCH-005 **OBSOLETE** | SNAP-* for multi-day archive **OBSOLETE**; snapshots remain for current-day immutability/reprint |
| Duplicate current-day Accepted → DRAFT | ARCH-006 **COMPLETE** (D-046; 67be68b); ARCH-007 **COMPLETE** (D-047; 86baf12 / fcd614b) | DUP-001..005 PASS; ARCH-T027..033 PASS; ARCH7-T001..T015 PASS |
| ODS parse | ODS-001..006 | ODS-001..007/011/012/021 |
| ODS validate | ODS-007 | ODS-004..007/013/014 |
| ODS reimport | ODS-008/010/011 | ODS-008..010/017..020 |
| Printed name | DB-001/004, ODS, PRINT | ODS-015, SNAP-002, PRINT-T013 |
| Printer contracts/models | PRINT-001 **COMPLETE** (D-048; `581f27d`) | contract/unit PASS |
| PrintableDocument/ReceiptComposer contracts | PRINT-002 **COMPLETE** (D-049; `c0ce4e7`) | contract/unit PASS |
| PricePrintMode preference (DataStore) | PRINT-003 **COMPLETE** (D-050; `bb71f72`) | unit PASS; PRINT-T012 rendering deferred to PRINT-004 |
| Receipt formatter / ReceiptComposer body | PRINT-004 **COMPLETE** (D-051; `ba2c8e8`) | PRINT-T001..T008, T010..T014 PASS |
| EscPosEncoder | PRINT-005 **COMPLETE** (D-052; `cab2c1a`) | byte unit PASS |
| FakePrinterDriver | PRINT-006 **COMPLETE** (D-053; `0a98b0f`) | PRINT-T026 PASS + Fake unit PASS |
| PrinterService + Mutex | PRINT-007 **COMPLETE** (D-054; `cee8162`) | PRINT-T009/T020/T022/T024/T027 + service T023/T025 + Q6b PASS |
| Bluetooth runtime permissions | BT-001 **COMPLETE** (D-055; `345dce6`) | unit permission matrix; PRINT-T021 partial (PermissionDenied path only) |
| List bonded Bluetooth devices | BT-002 **COMPLETE** (D-056; `d97cb49`) | unit PASS; PHONE ONLY androidTest PASS |
| Persist selected printer id | BT-003 **COMPLETE** (D-057; `e44c3e4`) | JVM DataStore unit PASS |
| Draft print | PRINT-020 **COMPLETE** (D-064); Samsung+NETUM hardware PASS | ViewModel 32 PASS; paper PASS (DRAFT 22,50; remains DRAFT) |
| M9 business receipt base text scale | **D-065 COMPLETE** (`2ac8233`; `DOUBLE_BOTH`) | composer unit PASS; hardware PASS |
| Draft printable total source | **D-066 COMPLETE** (`2ac8233`) | composer regression 14+6+2.50→22,50 PASS; hardware PASS |
| Scale-aware receipt layout width | **D-067 COMPLETE** (`f7dad97`; 42÷2=21 under DOUBLE_BOTH; profile stays 42) | layoutWidth A–Q PASS; paper PASS |
| Final / accepted manual print | PRINT-021 **COMPLETE** (D-068); Samsung+NETUM hardware PASS | ViewModel 33+40 PASS; UI PASS; paper PASS (001 / 28,00; no BOZZA) |
| Accept+print after commit | PRINT-022 **COMPLETE** (D-069); Samsung+NETUM success + BT-off failure PASS | ViewModel 53 PASS; UI 11 PASS; paper PASS; no rollback on print fail |
| Retry same accepted order | PRINT-023 **READY** (D-070 FROZEN; functionally satisfied via STAMPA re-tap) | ViewModel A–Q; BT-off→on hardware after tests |
| Bluetooth RFCOMM/SPP driver | BT-004 **COMPLETE** (D-058; `82cc98f`; Q1-A secure) | unit + PHONE+NETUM hardware PASS |
| Timeout/disconnect/error mapping | BT-005 **COMPLETE** (D-059; `3aad082`; connect 10_000 ms; Q2-A connect-only) | unit + PHONE+NETUM (normal + local BT-off → ConnectionLost) |
| NETUM ESC/POS calibration | HW-001 **COMPLETE** (D-060 capabilities + D-061 physical freeze; READY TO COMMIT) | PHONE+NETUM WIDTH/FORMAT/CODEPAGE/FEED + ORDER_PREVIEW evidence |
| Printer settings/test | BT-006 **COMPLETE** (D-062); BT-007 **COMPLETE** (D-063) | ViewModel 43 PASS; assembleDebug/AndroidTest PASS; Samsung+NETUM one-tap test print PASS |
| One copy/mutex | PRINT-007 | PRINT-T009/T024 |
| Non-fiscal | PRINT spec/release | manual review |
| Privacy/min permissions | QA-006/007/008 | release checklist |
| Migration safety | DB-009, QA-009 | migration tests |
| Accessibility | QA-011 | UI/manual |
