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
| Additions | ORD-011/013 | PRICE, PRINT |
| Removals | ORD-012 | PRICE-002, PRINT-T006 |
| Manual price | ORD-014/015 | PRICE-004/005 |
| Accepted immutable | ACCEPT-003/005/007 | ACCEPT-T003/007 |
| Atomic accept | ACCEPT-003/004 | ACCEPT-T001..005 |
| Sequential numbering | NUM-001 | NUM-T001..006 |
| Random numbering | NUM-002..004 | NUM-T010..015 |
| Mode switch | NUM-005 | NUM-T006/T015 |
| Today/archive | ARCH-001..005 | ARCH-T001..005 |
| Historical snapshots | DB-004, ARCH-005 | SNAP-001..004 |
| Duplicate | ARCH-006/007 | DUP-001..005 |
| ODS parse | ODS-001..006 | ODS-001..007/011/012/021 |
| ODS validate | ODS-007 | ODS-004..007/013/014 |
| ODS reimport | ODS-008/010/011 | ODS-008..010/017..020 |
| Printed name | DB-001/004, ODS, PRINT | ODS-015, SNAP-002, PRINT-T013 |
| Draft print | PRINT-002/004/020 | PRINT-T001/T025 |
| Final print | PRINT-002/004/021 | PRINT-T002..014 |
| Accept+print after commit | PRINT-022 | PRINT-T022/023 |
| Bluetooth | BT-001..005 | PRINT-T020..026 + HW |
| Printer settings/test | BT-006/007 | PRINT-T027 + HW |
| One copy/mutex | PRINT-007/024 | PRINT-T009/T024 |
| Non-fiscal | PRINT spec/release | manual review |
| Privacy/min permissions | QA-006/007/008 | release checklist |
| Migration safety | DB-009, QA-009 | migration tests |
| Accessibility | QA-011 | UI/manual |
