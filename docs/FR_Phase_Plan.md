# Retail POS – FR Phase Plan

Text copy of `FR_Phase_Plan.xlsx` (the spreadsheet is the original; keep both in sync).

## Summary

| Phase | Phase Name | FR Count | Main Classes |
|---|---|---|---|
| Phase 1 | Foundation (access, products, suppliers) | 9 | User (Manager, Cashier, Storekeeper), AccessControl, Product, Category, Supplier, ProductSupplier, ProductCatalog |
| Phase 2 | Purchasing and stock receiving | 9 | PurchaseOrder, PurchaseOrderItem, PurchaseOrderStatus, StockReceipt, StockMovement (Receipt), ReportService (partial) |
| Phase 3 | Sales transactions | 9 | Sale, SaleItem, SaleStatus, SalesReceipt, StockMovement (Sale) |
| Phase 4 | Returns, adjustments and remaining reports | 9 | Return, StockAdjustment, AdjustmentType, StockMovement (Return, Adjustment), Report, ReportService (complete) |
| **Total** | | **36** | |

## Phase 1 – Foundation (access, products, suppliers)

| FR | Requirement |
|---|---|
| FR1 | Register products (code, name, unit, selling price, reorder level, status) |
| FR2 | Product categories |
| FR3 | Register suppliers |
| FR4 | Many-to-many supplier and product link |
| FR5 | Supplier-specific purchase price |
| FR6 | Search by code, name or category |
| FR28 | Manager, Cashier and Storekeeper roles |
| FR29 | Login with username and password |
| FR30 | Role-based restriction of functions |

## Phase 2 – Purchasing and stock receiving

| FR | Requirement |
|---|---|
| FR7 | Create a purchase order for one supplier |
| FR8 | Purchase order items (quantity, price) |
| FR9 | Purchase order total |
| FR10 | Purchase order status lifecycle |
| FR11 | Record received quantities |
| FR12 | Increase stock only for received quantities |
| FR13 | Record receipt date and quantity |
| FR31 | Low-stock report (drives reordering) |
| FR34 | Supplier-wise purchase report |

## Phase 3 – Sales transactions

| FR | Requirement |
|---|---|
| FR14 | Create a sale |
| FR15 | Sale items (quantity, selling price) |
| FR16 | Show stock before adding an item |
| FR17 | Block quantities above available stock |
| FR18 | Line totals, subtotal, discount, final amount |
| FR19 | Reduce stock when the sale is finalized |
| FR20 | Record date/time and cashier |
| FR21 | Printable receipt |
| FR33 | Daily and date-range sales reports |

## Phase 4 – Returns, adjustments and remaining reports

| FR | Requirement |
|---|---|
| FR22 | Returns only against a completed sale |
| FR23 | Record return details |
| FR24 | Restock accepted reusable returns |
| FR25 | Stock adjustments (damaged, expired, missing) |
| FR26 | Record adjustment details |
| FR27 | Adjustment history |
| FR32 | Current inventory and stock-movement reports |
| FR35 | Product-wise sales report |
| FR36 | Print selected reports |
