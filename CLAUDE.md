# Retail Store Sales & Inventory Management System

Desktop POS + inventory app for a single retail store. University SDA project.
Source of truth: the Analysis/Design Class Diagrams and Sequence Diagrams in `docs/`.
Requirements are FR1–FR36 (see `docs/`). Do not invent features outside them
(no tax, payment gateway, customer accounts, barcode hardware, multi-store).

## Tech stack
- Java 17, Maven
- UI: Swing + FlatLaf (`FlatLightLaf.setup()` in `main`), NetBeans GUI builder
- Database: MySQL via plain JDBC (no ORM)
- Excel import: Apache POI (`poi-ooxml`), used only in `util.SpreadsheetUtil`
- Tests: JUnit 5

## Package layout (`com.retailpos`)
| Package | Contains | Rules |
|---|---|---|
| `model` | Entity classes from the class diagram | Private fields, getters/setters, no SQL, no Swing |
| `model.enums` | `PurchaseOrderStatus`, `SaleStatus`, `AdjustmentType`, `StockMovementType` | Values exactly as below |
| `dao` | One interface + one JDBC implementation per entity (`ProductDao`, `ProductDaoJdbc`) | The ONLY place SQL is written. Always `PreparedStatement` |
| `service` | `AccessControl`, `ProductCatalog`, `ReportService`, `SaleService`, `PurchaseService`, `ReturnService`, `StockService` | Business rules, validation, permission checks, transactions |
| `ui` | One `JFrame`/`JPanel` per screen | Calls services only. No SQL, no business logic in event handlers |
| `util` | `DBConnection`, `PasswordUtil`, formatting helpers | Stateless helpers |

Layer direction is strict: `ui → service → dao → database`. Never skip a layer.

## Domain model (do not rename)
- `User` (abstract): userID, username, passwordHash, createdByID, active. Subclasses `Manager`, `Cashier`, `Storekeeper`
- `Product`: productCode, name, unit, sellingPrice, reorderLevel, status, currentStockQuantity; belongs to one `Category`
- `Supplier`: supplierID, name, address, contactInfo
- `ProductSupplier`: product ↔ supplier link with `purchasePrice` (many-to-many)
- `PurchaseOrder` (poNumber, orderDate, status, totalValue) contains 1..* `PurchaseOrderItem` (orderedQuantity, purchasePrice, receivedQuantity)
- `StockReceipt`: receiptID, receiptDate, quantityReceived; against one `PurchaseOrderItem`
- `Sale` (saleID, dateTime, status, subtotal, discount, finalAmount, cashier) contains 1..* `SaleItem` (quantity, sellingPrice, lineTotal); generates 0..1 `SalesReceipt`
- `Return`: returnID, returnDate, quantity, reason, isAccepted, isReusable; against one `SaleItem`
- `StockAdjustment`: adjustmentID, adjustmentType, quantity, date, reason, recorded by a `User`
- `StockMovement`: movementID, movementType, quantityChange, dateTime, sourceReference, balanceAfter, responsible `User`
- `Report`: reportID, reportType, generatedAt

Enums:
- `PurchaseOrderStatus`: PREPARED, ORDERED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED
- `SaleStatus`: DRAFT, COMPLETED
- `AdjustmentType`: DAMAGED, EXPIRED, MISSING
- `StockMovementType`: RECEIPT, SALE, RETURN, ADJUSTMENT

## Business rules (must hold)
- Money: `BigDecimal`, never `double`. Quantities: `int`.
- Every stock change creates exactly one `StockMovement` and updates `currentStockQuantity`
  in the SAME database transaction (commit both or roll back both).
- Stock increases only from: actual receipts (FR12) and accepted AND reusable returns (FR24).
- Stock decreases only from: finalized sales (FR19) and adjustments (FR25).
- A source (receipt, sale, return) must never apply stock twice; check status / source reference first.
- Sale: line total = quantity × sellingPrice; subtotal = sum of lines; final = subtotal − fixed discount.
  Show current stock before adding an item (FR16); reject quantity > stock (FR17).
  `finalize()` re-checks stock, reduces stock, records cashier + date/time, sets COMPLETED.
- Purchase order: one supplier, 1..* items; total = sum of item totals. Reject over-receipt.
  Cancel only before any receipt. Status updates automatically on receiving.
- Return: only against a COMPLETED sale; quantity cannot exceed unreturned quantity sold.
- Low stock = `currentStockQuantity <= reorderLevel`.
- Passwords stored hashed (`PasswordUtil`), never plain text.
- User accounts (added beyond FR1–FR36 at the team's request): a Manager signs up only with the
  setup code (`app.manager_signup_code` in `db.properties`; blank = sign-up off). Cashier and
  Storekeeper accounts are created by a Manager and belong to them (`createdByID`); a Manager sees
  and (de)activates only their own staff and cannot create another Manager. Accounts are
  deactivated, never deleted; a deactivated account cannot sign in.
- Every restricted service method starts with `accessControl.requirePermission(user, "<functionName>")`.

## Role permissions
| Function | Manager | Cashier | Storekeeper |
|---|---|---|---|
| Register product / supplier / supplier price | ✓ | | |
| Import NEW products from Excel (no edits, no stock) | ✓ | | ✓ |
| Create / manage purchase order | ✓ | | |
| Record stock receipt | | | ✓ |
| Record stock adjustment | | | ✓ |
| Create / finalize sale, record return | | ✓ | |
| Search products | ✓ | ✓ | ✓ |
| Reports | ✓ | | |
| Create / deactivate own Cashier & Storekeeper accounts | ✓ | | |
| Change own password (current password required) | ✓ | ✓ | ✓ |

## Screens (`ui`, match `docs/GUI_Screens`)
LoginFrame, DashboardFrame, ProductsPanel, SuppliersPanel, PurchaseOrderPanel,
ReceiveStockPanel, NewSalePanel, SalesReceiptDialog, ReturnsPanel,
StockAdjustmentsPanel, ReportsPanel, plus ManagerSignUpDialog, StaffPanel, ChangePasswordDialog and
ImportProductsDialog (no mockups).
Dashboard shows only menu items allowed for the logged-in role.

## Coding conventions
- Classes `PascalCase`, methods/fields `camelCase`, constants `UPPER_SNAKE_CASE`, DB tables `snake_case`.
- Method names follow the class diagram (`addItem`, `calculateSubtotal`, `finalize`, `restockIfEligible`, …).
- Validation failures throw a custom `BusinessException` with a user-readable message; the UI shows it in a `JOptionPane`.
- Close JDBC resources with try-with-resources.
- Add a short Javadoc on public service methods naming the FR they implement, e.g. `/** FR17: ... */`.

## How to work in this repo
- Do NOT rename, remove or change the fields of existing `model` classes or `dao` interfaces
  unless the task explicitly says so. If one needs changing, stop and say so.
- Only touch files related to the current task. No drive-by refactors or reformatting.
- One feature at a time; keep changes small enough to review in one pull request.
- Database schema changes go in `database/schema.sql`, plus a note in the pull request description.
- Before finishing: code compiles (`mvn compile`), and the tests for the changed service pass (`mvn test`).