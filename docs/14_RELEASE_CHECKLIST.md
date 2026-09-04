# 14 — Release Checklist v1 — CASSA

## Build
- [ ] debug build green
- [ ] release build green
- [ ] no debug-only code in release
- [ ] versionName/versionCode set
- [ ] minSdk/target valid
- [ ] version catalog clean

## Database
- [ ] schema export
- [ ] no destructive migration
- [ ] single Draft verified
- [ ] Accepted immutable
- [ ] snapshot verified
- [ ] business settings defaults
- [ ] numbering tests green

## Order
- [ ] quick add
- [ ] search name
- [ ] search ingredient
- [ ] category filters
- [ ] additions
- [ ] removals
- [ ] note
- [ ] manual price
- [ ] reset
- [ ] custom quantity
- [ ] modify one/all
- [ ] total
- [ ] general note
- [ ] crash recovery

## Accept
- [ ] empty rejected
- [ ] sequential
- [ ] random
- [ ] double tap
- [ ] remains on Accepted screen
- [ ] Home/New order buttons
- [ ] no edit Accepted

## Archive
- [ ] today businessDate
- [ ] archive date
- [ ] search number
- [ ] same number multiple dates
- [ ] detail
- [ ] duplicate
- [ ] reprint

## Menu
- [ ] manual products
- [ ] active/inactive
- [ ] additions
- [ ] autoExtras flag
- [ ] printedName

## ODS
- [ ] valid ODS
- [ ] invalid rows block
- [ ] Sala ignored
- [ ] €0 additions
- [ ] categories
- [ ] ingredients
- [ ] printedName
- [ ] preview
- [ ] rollback
- [ ] sample corrected

## Printer
- [ ] permissions
- [ ] bonded selection
- [ ] test print
- [ ] number large
- [ ] BOZZA
- [ ] sections order
- [ ] prices
- [ ] total
- [ ] notes
- [ ] accents
- [ ] €
- [ ] long wrapping
- [ ] feed
- [ ] retry
- [ ] one-copy behavior
- [ ] mutex
- [ ] 10 consecutive prints
- [ ] printer off/reconnect

## Privacy/security
- [ ] no INTERNET if unused
- [ ] no global storage permission
- [ ] release logs privacy-safe
- [ ] cloud backup disabled/controlled
- [ ] no customer PII fields
- [ ] no fiscal claims

## Accessibility
- [ ] font scale test
- [ ] touch targets
- [ ] contrast
- [ ] icons labelled
- [ ] no clipped main CTA
- [ ] long menu names

## Manual smoke scenario

1. Launch.
2. Import corrected menu.
3. Configure special product autoExtras pricing.
4. Select printer.
5. Test print.
6. New order.
7. Search ingredient.
8. Add standard pizza twice.
9. Modify one.
10. Add paid/free addition.
11. Remove ingredient.
12. Manual price + reset.
13. Add frittura/bibita.
14. Complete.
15. Print draft.
16. Accept and print.
17. Force print error/retry if possible.
18. Verify Today.
19. Reprint.
20. Duplicate.
21. Kill app with new Draft.
22. Reopen and recover.
23. Accept after 05:00 boundary via test clock in test environment.

Release candidate può essere considerata pronta solo dopo smoke e test P0/P1.
