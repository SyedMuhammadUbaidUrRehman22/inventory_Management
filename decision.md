# Decisions

Every design and implementation decision made so far, with the reason for it.
Last updated: 2026-11-26 (Phase 1, user accounts, Excel import, Phase 2, transaction guard, Phase 4).

**[Team]** marks decisions the team asked for or chose. Everything else is an implementation choice made while
building. Those follow CLAUDE.md where it gives an answer and can be changed if the team prefers otherwise.

---

## 1. Project setup

**D1. Java 17 and Maven.** **[Team]**
The project is `com.retailpos:retail-pos` and compiles with `release 17`, so it runs on any JDK 17 or newer
(it is developed on JDK 25).
*Why:* the tech stack in CLAUDE.md.

**D2. The Maven Wrapper is included** (`mvnw`, `mvnw.cmd`, Maven 3.9.9).
*Why:* Maven was not installed. With the wrapper, anyone can build with just a JDK.
`.gitattributes` keeps `mvnw` with LF line endings and `mvnw.cmd` with CRLF, so both scripts work after cloning.

**D3. Libraries.** **[Team]**
- FlatLaf 3.5.4 for the look and feel.
- MySQL Connector/J 9.1.0, in `runtime` scope only: the code never imports it, and `DriverManager` loads it.
- jBCrypt 0.4 for password hashing.
- JUnit 5.11.3 for tests.
- Apache POI was added later (D60).

**D4. Database credentials live in `src/main/resources/db.properties`, which is git-ignored.** **[Team]**
A `db.properties.example` file with placeholders is committed instead.
*Why:* credentials must never reach the repository.
*Note:* a packaged `.jar` would include this file, so the settings must move outside the app before it is
distributed (D72).

**D5. `DBConnection` opens a new connection on every call and reads `db.properties` each time.** **[Team]**
Callers close the connection with try-with-resources.
*Why:* it is simple, and a single-store desktop app does not need a connection pool.

## 2. Database schema (`database/schema.sql`)

**D6. MySQL 8.0.16 or newer.** `schema.sql` drops and recreates every table.
*Why:* CHECK constraints are only enforced from 8.0.16, and a full reset is the simplest way to set up during
development. The README warns that running the script deletes data.

**D7. Table and column names avoid MySQL keywords.**
- `snake_case` throughout.
- The users table is `users`, and returns are in `sale_return`, because `USER` is a keyword and `RETURN` is reserved.
- The `StockAdjustment.date` field is stored as `adjustment_date`.

**D8. `product_code` (VARCHAR(30)) is the product's primary key.**
*Why:* the code is the identifier people use when searching, selling and linking suppliers.

**D9. Rules are also enforced by the database, as a second line of defence behind the service layer:**
- CHECKs on roles, statuses, and money, quantities and stock not being negative.
- `received_quantity BETWEEN 0 AND ordered_quantity`, so over-receipt is impossible.
- At most one receipt per sale (`UNIQUE sale_id`).
- One line per product per purchase order and per sale (UNIQUE pairs).
- `balance_after >= 0` on stock movements.

**D10. Types.**
- Money is `DECIMAL(12,2)` in the database and `BigDecimal` in Java, never `double`.
- Quantities are `INT` / `int`.
- Dates are `DATE` / `LocalDate` and `DATETIME` / `LocalDateTime`.

**[Team]**

**D11. Seed data.** **[Team]**
- Three categories: Groceries, Beverages, Household.
- One account per role: `manager`, `cashier`, `storekeeper`, all with password `1234` stored as a BCrypt hash.
- The seeded cashier and storekeeper belong to the seeded manager (D51).

*Why:* the database can be used for a demo straight away. Change the passwords after the first sign-in.

## 3. Domain model (`model`)

**D12. Model classes are plain data objects.**
- Private fields, a no-arg constructor and a full constructor, getters, setters and `toString`.
- No SQL and no Swing in these classes.

**[Team]**

**D13. Some fields were added beyond the CLAUDE.md list, where the class diagram's associations need them.**
- IDs for `PurchaseOrderItem` and `SaleItem`.
- Object links:
  - `PurchaseOrder.supplier` and `.items`
  - `PurchaseOrderItem.product`
  - `Sale.items`
  - `SaleItem.product`
  - `StockAdjustment.product` and `.recordedBy`
  - `StockMovement.product` and `.responsibleUser`
- `Category(categoryID, name)` and `SalesReceipt(receiptID, issuedAt, sale)`.

*Why:* associations have to be stored and navigated.

**D14. The role is the subclass.** `User` is abstract, with `Manager`, `Cashier` and `Storekeeper` as subclasses.
The database stores the role in the `role` column.

**D15. `User.toString()` leaves out the password hash**, so it never ends up in logs.

## 4. Documentation and mockups (`docs/`)

**D16. The FR phase plan is kept as the original `.xlsx` plus a Markdown copy** that is easier to read and compare. **[Team]**

**D17. Mockups 02–10 were renamed to match their content.** Each file held the previous screen, so the dashboard
was in `03_Products.svg`, for example.

**D18. The missing Reports mockup (`11_Reports.svg`, FR31–FR36) was designed in the same style.** **[Team]**
It was worked out from the other screens.

**D19. Fixes to the mockups.** **[Team]**
- Sidebars show only the menu items the screen's role may use, and the Manager's quick actions are limited to
  Manager functions.
- The discount is a **fixed amount**, not a percentage (CLAUDE.md). "Discount (10%)" was relabelled, and New Sale
  got a field for the discount amount.
- Card titles hidden behind tables and tables spilling out of cards were fixed.

## 5. Architecture and code structure

**D20. Strict layers: ui → service → dao → database.** The UI never uses DAO classes. **[Team]**

**D21. Each entity has a DAO interface plus a JDBC implementation.** They share a `JdbcDao` base class.
- Every query uses a `PreparedStatement` with try-with-resources.
- A single `connection()` hook means Phase 2 can add transactions without changing the DAO interfaces.

**D22. There are two kinds of error.**
- `BusinessException` is a mistake the user can fix. It is shown as a warning with a readable message.
- `DataAccessException` (unchecked) is a database failure. It is shown as an error dialog.

**D23. Permissions are function names** (e.g. `registerProduct`), and `AccessControl` holds the table of which role
has which.
- Every restricted service method starts with `accessControl.requirePermission(user, …)`.
- Hiding menu items is only for convenience. The service check is what actually enforces access.

**D24. LIKE searches escape `%` and `_`**, so those characters match literally.

**D25. Saving a supplier price is an upsert** (`INSERT … ON DUPLICATE KEY UPDATE purchase_price = ?`). The price is
bound twice rather than using the deprecated `VALUES()` function.

## 6. Phase 1 behaviour (FR1–FR6, FR28–FR30)

**D26. Stock never changes from the product form.** New products start at 0, and `ProductDao.update` never touches
`current_stock_quantity`.
*Why:* CLAUDE.md requires stock to change only through stock movements.

**D27. Selling and purchase prices must be above 0**, with at most 2 decimals and at most 9,999,999,999.99.
This is stricter than the database, which accepts 0.
*Why:* a price of zero is almost always a typing mistake.

**D28. Product status is `ACTIVE` or `DISCONTINUED`**, and `ACTIVE` is the default.

**D29. Text rules.**
- Required text is trimmed, and its length limits match the database columns.
- Optional text that is left blank is stored as NULL.

**D30. Category names are unique, ignoring case.**
Only the Manager can add categories, using a **"+" button** next to the Category field.
*Why:* FR2 needs a way to add categories, and the mockup had none.

**D31. Login shows the same message for an unknown username and a wrong password.**
*Why:* the screen does not reveal which usernames exist.

**D32. Every role can search on the Products screen; only the Manager sees the register/edit form.**

**D33. Only Active products are offered when linking a product to a supplier.**

## 7. User interface (`ui`)

**D34. Screens are hand-written Swing code, not NetBeans GUI-builder `.form` files.**
- *Why:* the code is easier to review, and the styling stays consistent.
- *Trade-off:* the screens cannot be edited in NetBeans' visual designer, although CLAUDE.md mentions it.

**D35. One theme for every screen.**
- FlatLaf light with the mockups' blue accent `#2563EB`.
- A shared `UiTheme` provides the colours, cards, buttons, fields and table style.

**D36. The menu is built from the user's permissions.**
- Later-phase items are shown, but open a "Phase N … not available yet" note.
- Screens are created the first time they are opened and refreshed when reopened.

**D37. The Dashboard home is a welcome card with quick actions.**
The mockup's figures (today's sales, low stock, open orders, returns) wait until Phases 2–4 produce that data.

**D38. There is no "Remember me" on the login screen.**
*Why:* it is not in the requirements, and it would mean storing credentials on the till.

**D39. Display formats.**
- Money: "Rs 1,450", or "Rs 350.50" when there are paise.
- Supplier IDs: "S-01".
- Statuses: "Active" / "Discontinued".

**D40. Layout.**
- The main window opens maximised and restores to 1280×800.
- Table headers line up with their columns, so number columns are right-aligned.

**D41. Wrapping text uses short single-line labels or wrapping text areas, not HTML labels.**
*Why:* HTML labels were cut off or overlapped other components when the layout changed.

## 8. Testing and verification

**D42. Unit tests use in-memory fake DAOs**, so `mvnw test` does not need MySQL.

**D43. Database behaviour is checked against throwaway databases.**
- They are built from `schema.sql` (`retail_pos_p1check`, `retail_pos_migcheck`) and dropped afterwards.
- The real `retail_pos` database is never used for testing.
- Screens are checked from screenshots rendered at the real display scaling.

## 9. User accounts (beyond FR1–FR36)

**D44. Manager sign-up, Manager-owned staff accounts and change password were added at the team's request,
following least privilege.** **[Team]** They are documented in CLAUDE.md.

**D45. Managers can only sign up if they know a setup code.** **[Team]**
- The code is `app.manager_signup_code` in `db.properties`.
- If it is blank, sign-up is turned off and the link is hidden.
- There can be several Managers.
- Rejected alternatives:
  - "First manager only": there could only ever be one Manager.
  - "Anyone, any time": anyone at the till could give themselves full access.

**D46. The setup code is checked before anything else, using a constant-time comparison.**
*Why:* without the code, the sign-up form does not reveal which usernames exist.

**D47. Staff accounts belong to the Manager who created them** (`users.created_by`).
- Managers have no owner.
- A database CHECK (`(role = 'MANAGER') = (created_by IS NULL)`) and a foreign key enforce this.

**[Team]**

**D48. Limits on what a Manager can do with accounts.**
- A Manager can only create Cashier and Storekeeper accounts, never another Manager.
- A Manager sees and (de)activates only their own staff.

**[Team]**

**D49. Accounts are deactivated, never deleted** (`users.active`).
- *Why:* sales and stock movements keep pointing at the user.
- Deactivated accounts cannot sign in.
- The "deactivated" message only appears after the correct password has been entered.

**D50. Account rules.**
- Passwords: 8–64 characters, with at least one letter and one number. The maximum is 64 because BCrypt only uses
  the first 72 bytes.
- Usernames: 3–50 characters, using letters, digits, `.`, `_` and `-`, and unique ignoring case.
- The rules apply to new accounts and to password changes. The seed accounts keep `1234` until changed.

**D51. The existing database was upgraded in place, not recreated.** **[Team]**
- The `ALTER TABLE` statements are in `database/migrate_staff_accounts.sql`.
- The `users` table was backed up first.
- Existing staff were given to the first Manager, and all other data was kept.

**D52. Change password.** **[Team]**
- Every role can change their own password, and only their own.
- The current password is required, and the new one must be different.
- The account is re-read from the database first, so a deactivated account cannot change its password.
- *Not built:* a Manager resetting a staff member's password, and forcing a password change on first sign-in.

**D53. New permissions:** `manageStaff` (Manager) and `changeOwnPassword` (every role).

## 10. Adding products from Excel (beyond FR1–FR36)

**D54. Managers and Storekeepers can import products** (permission `importProducts`). **[Team]**
The Storekeeper gets import only. They still cannot use the product form, edit products or add categories.

**D55. An import only adds new products.**
- A product code that already exists is reported as a problem and never updated.
- *Why:* an import, especially by a Storekeeper, must not be able to change existing products or prices.

**D56. Categories must already exist.**
*Why:* Storekeepers cannot create categories. A Manager adds any missing ones first.

**D57. Stock is never imported.** New products start at 0, and a stock column in the file is ignored, with a note
saying so (same rule as D26).

**D58. The file is previewed first, then imported all-or-nothing.**
- Every row is checked with the same rules as the product form, and problems are listed with their row numbers.
- *Import* adds everything in **one database transaction** (`ProductDao.insertAll`), or nothing.
- Everything is checked again at that moment, in case someone added the same code in the meantime.

**D59. The import is lenient about the file's layout.**
- Columns can be in any order, and short names such as "Code" and "Price" work.
- A code typed as a number stays "1001", not "1001.0".
- Prices such as "Rs 1,250" are accepted.
- Limit: 2,000 rows and 10 MB per file.

**D60. Excel files are read and written with Apache POI 5.5.1.**
- Only `util.SpreadsheetUtil` uses it.
- 5.5.1 was chosen over 6.0.0, a new major release from only a month earlier.

**D61. POI's logging uses Log4j's built-in simple logger, showing errors only** (`log4j2.component.properties`).
*Why:* this stops POI printing a "no logging provider" error, without adding a logging framework.

**D62. A template can be downloaded.**
- Its *Products* sheet has the column names.
- Its *Help* sheet lists the rules and the current categories.

**D63. The "Import from Excel" button sits at the top-right of the Products table.**
*Why:* in the search bar it squeezed the search field.

## 11. Running the app

**D64. The app is run with `mvnw compile exec:java`** (`exec-maven-plugin`, main class `com.retailpos.App`).

**D65. A double-click launcher (`.bat`) was offered but not created.**
Packaging the app as a `.jar` or `.exe` is postponed until the database settings move outside the app (D4).

## 12. Git and GitHub

**D66. Commits and pushes happen only when the team asks.**

**D67. Branches.** **[Team]**
- Code work uses a branch per feature, each built on the previous one:
  `phase-1` → `staff-accounts` → `product-import`.
- Everything is merged into `main` with merge commits:
  - PR #1: the FR phase plan and screen mockups.
  - PR #2: `phase-1`.
  - PR #3: `product-import`, which also brought in `staff-accounts`.
  - The README and this decision log were merged straight from `product-import`, without a pull request.
- Feature branches are kept after merging.

**D68. Phase 1 was committed as four commits, one per layer** (DAOs, AccessControl, ProductCatalog, UI).
Every commit builds and passes its tests on its own.

**D69. Pull requests are opened by hand from the GitHub "compare" links**, because the `gh` command-line tool is
not installed.

## 13. Known limitations (accepted for now)

**D70.** Deactivating someone who is already signed in only takes effect when they sign out.

**D71.** The seed accounts use password `1234` until someone changes them.

**D72.** `db.properties` is read from inside the app, so it would be packed into a `.jar`. It must move outside the
app before distribution (D65).

**D73.** The sign-up, Staff, Change password and Import screens have no mockups in `docs/GUI_Screens`.

**D74.** The new screens and dialogs were checked with tests and screenshots, but not yet by clicking through the
running app.

---

## 14. Phase 2: Purchasing and stock receiving (FR7–FR13, FR31, FR34, FR36)

**D75. PurchaseOrder and StockReceiving layer structure.**
- Domain models: `PurchaseOrder`, `PurchaseOrderItem`, `StockReceipt`, `StockMovement`, `Report`.
- DAOs: `PurchaseOrderDao`, `StockReceiptDao`, `StockMovementDao`, `ReportDao` with JDBC implementations.
- Services: `PurchaseService` (Manager: create/order/cancel POs), `StockService` (Storekeeper: receive stock and audit movements), `ReportService` (Manager: low-stock and supplier purchase reports).
- UI: `PurchaseOrderPanel` (matching `05_Create_Purchase_Order.svg`), `ReceiveStockPanel` (matching `06_Receive_Stock.svg`), `ReportsPanel` (matching `11_Reports.svg`).

**D76. Atomic stock update transactions.**
Every stock increase from a receipt executes in a single database transaction (`DBConnection.inTransaction`):
1. Records `StockReceipt`
2. Updates `PurchaseOrderItem.received_quantity`
3. Increments `Product.current_stock_quantity`
4. Inserts `StockMovement` (type `RECEIPT`, quantity `+qty`, balanceAfter = new stock, reference `PO-#/RC-#`)
5. Updates `PurchaseOrder` status (`PARTIALLY_RECEIVED` or `RECEIVED`)
If any step fails, all changes roll back atomically.

**D77. Purchase order lifecycle (FR10).**
- A PO starts as `PREPARED` or `ORDERED`.
- A `PREPARED` order can be marked as `ORDERED` when officially placed with the supplier.
- An order can only be cancelled before any stock receipt has been recorded (`received_quantity == 0` for all lines).
- Receiving stock automatically transitions status to `PARTIALLY_RECEIVED` or `RECEIVED`.

**D78. Over-receipt protection (FR11, FR12).**
- Quantities entered during receipt are validated to never exceed `ordered_quantity - received_quantity`.
- Only actual received quantities increase `current_stock_quantity` (FR12).

**D79. Reports and Printing (FR31, FR34, FR36).**
- Low-stock report filters products where `current_stock_quantity <= reorder_level` and computes deficit.
- Supplier-wise purchase report groups and aggregates purchase orders by supplier and date range.
- Reports log an entry in the `report` table on generation.
- Printing (FR36) uses standard Swing `JTable.print(...)` with styled headers and footers.


**D80. Code inside a transaction cannot commit or roll back on its own.**
Inside `DBConnection.inTransaction`, every DAO gets the same shared connection, wrapped so DAO code cannot end
the transaction early:
- `close()`, `commit()` and `setAutoCommit(..)` are ignored, and `getAutoCommit()` returns `false`.
  Only `inTransaction` commits.
- `rollback()` marks the transaction *rollback-only*. When the transaction finishes, `inTransaction` rolls back
  and throws a `DataAccessException` instead of committing.

A DAO call made outside a transaction still gets a normal connection, so `ProductDaoJdbc.insertAll` (Excel
import, D60) keeps committing its batch itself.
*Why:* before this change only `close()` was ignored. A DAO method that manages its own transaction, like
`insertAll`, would have committed part of the work early if it ran inside `inTransaction`, breaking the
"commit both or roll back both" rule for stock changes. Nothing does this yet, but Phase 3 (sales, returns,
adjustments) will add more transactions, so the guard went in first. Covered by `DBConnectionTest`.

## 15. Phase 4: Returns, adjustments and remaining reports (FR22–FR27, FR32, FR35, FR36)

**D81. Layer structure.**
- DAOs: new `ReturnDao` (table `sale_return`) and `StockAdjustmentDao`, each with a JDBC implementation.
  `StockMovementDao` gained one method, `search(product, type, from, to)`, for the movement report; nothing was
  renamed or removed. The tables were already in `schema.sql`, so there is no schema change.
- Services: new `ReturnService` (Cashier, `recordReturn`). Adjustments live in `StockService`
  (`recordAdjustment`, `getAdjustmentHistory`; Storekeeper), next to receipts. `ReportService` gained the FR32
  and FR35 reports.
- UI: `ReturnsPanel` (`09_Returns.svg`), `StockAdjustmentsPanel` (`10_Stock_Adjustments.svg`), and three more
  report cards in `ReportsPanel` (`11_Reports.svg`), in the mockup's order.
- `StockService` and `ReportService` keep their older constructors (as Phase 3 did for `ReportService`), so
  existing tests did not change. A service built without the new DAO says the feature is not available.

**D82. A return is recorded per sale line, several lines at once.**
The class diagram has one `Return` per `SaleItem`. The mockup lets the cashier tick several lines with one
reason, date and decision, so `recordReturn` takes `saleItemID → quantity` and saves one `Return` per line, all
in one transaction. One invalid line means nothing is saved.

**D83. Only accepted returns count as "returned".**
The rule "quantity cannot exceed the unreturned quantity sold" counts accepted returns only. A rejected return
means the customer kept the goods, so those units can still be returned later. Rejected returns are still
recorded (FR23), with `is_accepted = false`.

**D84. Restocking (FR24).**
Stock goes up only for a return that is accepted AND reusable (`restockIfEligible`), with one `RETURN` movement
(`+qty`, reference `SL-#####/RT-#####`) in the same transaction as the return. "Reusable" can only be ticked on an
accepted return; the service refuses the combination and the screen disables the box. The return date cannot be
in the future or before the sale date. The sale and its earlier returns are re-read inside the transaction, so two
returns recorded at once cannot exceed the quantity sold.

**D85. Adjustments (FR25–FR27).**
An adjustment needs a product, type (`DAMAGED`, `EXPIRED`, `MISSING`), quantity of at least 1, a date that is not
in the future, and a reason. Stock is read inside the transaction and the quantity cannot exceed it, so stock never
goes below zero (the database also has `balance_after >= 0`). The adjustment, the stock decrease and one
`ADJUSTMENT` movement (`-qty`, reference `ADJ-#####`) are saved together. Discontinued products can be adjusted,
so their remaining stock can be written off. History is newest first and can be filtered by product.

**D86. Report periods.**
The stock-movement (FR32) and product-wise sales (FR35) reports use the same dates as the sales report (FR33):
both empty means today, one empty means that single day, and From after To is refused. Each report logs a
`report` row (`CURRENT_INVENTORY`, `STOCK_MOVEMENTS`, `PRODUCT_SALES`).

**D87. What the new reports show.**
- Current inventory (FR32): every product, active and discontinued, with stock, reorder level, a low-stock flag,
  and stock value at selling price (there is no cost price per product; it depends on the supplier).
- Stock movements (FR32): oldest first, filtered by product and type, with units in, units out and net change.
- Product-wise sales (FR35): units, number of sales and revenue per product from completed sales, highest revenue
  first. Revenue is the sum of line totals, before the sale's fixed discount, because a discount applies to the
  whole sale, not to one product. Returns are not subtracted; they show in the stock-movement report.
- Printing (FR36) uses the existing `printReport` for every report.
