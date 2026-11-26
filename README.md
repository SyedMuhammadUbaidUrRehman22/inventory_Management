# RetailPro – Retail Store Sales & Inventory Management

A desktop point-of-sale and inventory system for a single retail store, built with Java Swing and MySQL.
University Software Design & Architecture (SDA) project.

## Project status

The 36 functional requirements (FR1–FR36) are delivered in four phases. See [`docs/FR_Phase_Plan.md`](docs/FR_Phase_Plan.md).

| Phase | Scope | Status |
|---|---|---|
| 1 – Foundation | Login and roles, products, categories, suppliers, supplier prices, search (FR1–FR6, FR28–FR30) | ✅ Done |
| 2 – Purchasing and stock receiving | Purchase orders, receiving stock, low-stock and supplier purchase reports (FR7–FR13, FR31, FR34) | ✅ Done |
| 3 – Sales | New sale, stock checks, discount, receipt, sales reports (FR14–FR21, FR33) | ✅ Done |
| 4 – Returns, adjustments, reports | Returns, stock adjustments, inventory and sales reports, printing (FR22–FR27, FR32, FR35, FR36) | ✅ Done |

Also built, beyond FR1–FR36: Manager sign-up, Manager-owned staff accounts, change password, and adding products from Excel.

## What it can do today

- **Sign in** as a Manager, Cashier or Storekeeper. Each role sees only the menu items it is allowed to use.
- **Products:** register and edit products (code, name, category, unit, selling price, reorder level, status),
  add categories, and search by code, name or category. New products always start with zero stock.
- **Suppliers:** register and edit suppliers, link products to them with each supplier's own purchase price.
- **Purchasing:** Managers create purchase orders for one supplier; Storekeepers record what actually arrives,
  which is the only way stock goes up apart from returns.
- **Sales:** Cashiers make sales with stock checks, a fixed discount and a printable receipt.
- **Returns:** Cashiers record returns against a completed sale. Stock goes back up only when the return is
  accepted and the item is reusable.
- **Stock adjustments:** Storekeepers write off damaged, expired or missing stock, with a full history.
- **Reports** (Manager): low stock, current inventory, stock movements, sales by day or date range, supplier-wise
  purchases and product-wise sales. Every report can be printed.
- **Import products from Excel:** Managers and Storekeepers can add many new products at once from an `.xlsx` file.
- **Staff accounts:** a Manager creates Cashier and Storekeeper accounts, and can deactivate or reactivate them.
- **Manager sign-up** from the login screen, protected by a setup code.
- **Change password** for every user.

## Requirements

- **JDK 17 or newer** (`java -version` should work in a terminal)
- **MySQL 8.0.16 or newer** (needed for CHECK constraints)
- Maven does **not** need to be installed: the project includes the Maven Wrapper (`mvnw` / `mvnw.cmd`),
  which downloads it on first use.

## Setup

### 1. Create the database

`database/schema.sql` creates the `retail_pos` database with all tables and seed data.

> ⚠️ Running it **drops and recreates** every table in `retail_pos`, deleting any existing data.

Use one of these:

- **MySQL Workbench:** *File → Open SQL Script…* → choose `database/schema.sql` → run it (⚡).
- **Command Prompt (Windows)** or a macOS/Linux terminal, from the project folder:
  ```
  mysql -u root -p < database/schema.sql
  ```
  On Windows, if `mysql` is not found, use the full path, e.g.
  `"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"`.
  (PowerShell does not support `<`; use Command Prompt or Workbench.)

### 2. Configure the connection

Copy `src/main/resources/db.properties.example` to `src/main/resources/db.properties` and fill in your MySQL details:

```properties
db.url=jdbc:mysql://localhost:3306/retail_pos?serverTimezone=UTC
db.user=your_mysql_user
db.password=your_mysql_password

# Optional: code needed to sign up as a Manager on the login screen. Empty = sign-up turned off.
app.manager_signup_code=
```

`db.properties` is git-ignored, so your credentials are never committed. Do not commit it.

### 3. Run the app

From the project folder:

```
# Windows
.\mvnw.cmd compile exec:java

# macOS / Linux
./mvnw compile exec:java
```

The sign-in window opens after a few seconds. Closing it stops the app.
You can also open the folder as a Maven project in NetBeans or IntelliJ and run `com.retailpos.App`.

### Default accounts

`schema.sql` creates one account per role. All use the password **`1234`**. Change them after the first sign-in
(sidebar → *Change password*).

| Username | Role |
|---|---|
| `manager` | Manager |
| `cashier` | Cashier (belongs to `manager`) |
| `storekeeper` | Storekeeper (belongs to `manager`) |

### Upgrading a database created before staff accounts

If your `retail_pos` database was created from the Phase 1 `schema.sql` and you want to keep its data, run
`database/migrate_staff_accounts.sql` once instead of re-running `schema.sql`. It adds the `created_by` and
`active` columns to `users` and gives existing Cashier and Storekeeper accounts to the first Manager.

## Running the tests

```
.\mvnw.cmd test        # Windows
./mvnw test            # macOS / Linux
```

The unit tests use in-memory stand-ins for the database, so they do **not** need MySQL.

## Roles and permissions

| Function | Manager | Cashier | Storekeeper |
|---|:-:|:-:|:-:|
| Register / edit products, categories, suppliers, supplier prices | ✓ | | |
| Add new products from Excel (never edits existing products or stock) | ✓ | | ✓ |
| Create / manage purchase orders *(Phase 2)* | ✓ | | |
| Record stock receipts *(Phase 2)* | | | ✓ |
| Record stock adjustments *(Phase 4)* | | | ✓ |
| Create sales, record returns *(Phases 3–4)* | | ✓ | |
| Reports *(Phases 2–4)* | ✓ | | |
| Search products | ✓ | ✓ | ✓ |
| Create and deactivate own Cashier / Storekeeper accounts | ✓ | | |
| Change own password | ✓ | ✓ | ✓ |

Every restricted action is checked in the service layer, not only hidden in the menu.

### Accounts

- **Managers** sign up from the login screen ("New manager? Create an account"), which only appears when
  `app.manager_signup_code` is set. The setup code must be entered, so nobody at the till can make themselves a Manager.
- **Cashiers and Storekeepers** are created by a Manager on the *Staff* screen and belong to that Manager.
  A Manager only sees and manages their own staff, and cannot create another Manager.
- Accounts are **deactivated, never deleted**, so past sales keep their cashier. Deactivated accounts cannot sign in.
- Passwords need at least 8 characters with letters and numbers, and are stored as BCrypt hashes.

## Adding products from Excel

On the *Products* screen, click **Import from Excel**:

1. **Download template**: an `.xlsx` with the column names in row 1 and a *Help* sheet listing the rules and your categories.
2. Fill in one product per row: **Product code, Product name, Category, Unit, Selling price** (required),
   **Reorder level, Status** (optional).
3. **Choose file…**: every row is checked and you see either a preview or a list of problems with row numbers.
4. **Import**: all products are added in one transaction, or none if anything fails.

Rules: only new product codes (existing products are never changed), categories must already exist,
stock is never imported (new products start at 0), at most 2,000 rows / 10 MB per file.

## Project structure

```
database/
  schema.sql                   Creates the database, tables and seed data
  migrate_staff_accounts.sql   Upgrades a Phase 1 database without losing data
docs/
  FR_Phase_Plan.md / .xlsx     The 36 requirements and their phases
  GUI_Screens/                 Screen mockups (SVG)
src/main/java/com/retailpos/
  App.java                     Entry point: look and feel, wiring, login window
  model/                       Entities from the class diagram (no SQL, no Swing)
  dao/                         One interface + JDBC implementation per entity; the only place with SQL
  service/                     Business rules, validation, permission checks (AccessControl, ProductCatalog)
  ui/                          Swing screens; they call services only
  util/                        DB connection, password hashing, formatting, Excel reading/writing
src/main/resources/
  db.properties.example        Copy to db.properties (git-ignored) and fill in
src/test/java/                 JUnit 5 tests for the services, with in-memory fake DAOs
```

Layers only call downwards: **ui → service → dao → database**.

## Tech stack

- Java 17, Maven (via Maven Wrapper)
- Swing with [FlatLaf](https://www.formdev.com/flatlaf/) for the look and feel
- MySQL 8 through plain JDBC (no ORM)
- jBCrypt for password hashing
- Apache POI for reading and writing Excel files
- JUnit 5

## More documentation

- [`docs/FR_Phase_Plan.md`](docs/FR_Phase_Plan.md): all requirements by phase
- [`docs/GUI_Screens/`](docs/GUI_Screens): mockups of the 11 main screens (the account and import screens have none)
- [`decision.md`](decision.md): every design decision made so far, and why
- [`CLAUDE.md`](CLAUDE.md): domain model, business rules and coding conventions for contributors
