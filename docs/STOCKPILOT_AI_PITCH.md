# StockPilot AI Pitch Document

## 1. One-Line Pitch

StockPilot AI is an AI-powered inventory intelligence layer that connects with Tally, Excel, CSV, XML, or other ERP exports to help businesses predict stockouts, reduce dead stock, improve profit, and make better buying decisions.

## 2. Simple Explanation

Many small and medium businesses already use Tally, Excel, or simple ERP software for accounting, GST, invoices, ledgers, and historical reports. But these tools usually do not tell the owner what action to take next.

StockPilot AI does not try to replace Tally. It works beside Tally.

The business can export inventory, sales, purchase, customer, and supplier data from Tally or Excel, upload it into StockPilot AI, and immediately see:

- which items may run out soon;
- which products are not moving;
- how much money is blocked in dead stock;
- what to reorder and in what quantity;
- why profit dropped this month;
- which customers have pending payments;
- which imported product names need cleanup;
- what warehouse staff should do today.

## 3. Product Message

Connect Tally or Excel. Predict stockouts. Reduce dead stock. Improve profit.

## 4. Problem

Retailers, wholesalers, FMCG distributors, hardware shops, and small warehouses often have useful data, but it is scattered across Tally, Excel, paper notes, and manual stock registers.

Common pain points:

- owners find out about stockouts only after losing sales;
- slow-moving products keep blocking money;
- reorder decisions are based on guesswork;
- messy product names make reports unreliable;
- customer outstanding payments are not followed up on time;
- warehouse staff need faster mobile stock entry;
- Tally data is useful, but business owners still need decision-making insights;
- small businesses cannot afford expensive enterprise BI or AI systems.

## 5. Solution

StockPilot AI turns existing business data into practical daily actions.

It imports data from Tally, Excel, CSV, XML, and JSON, stores it in a proper stock ledger, runs forecasting and business rules, and shows simple dashboards and recommendations.

The goal is not only to show reports. The goal is to answer:

- What should I reorder today?
- Which products are wasting my money?
- Which customer should I call for payment?
- Why did my profit reduce?
- Which items need cleanup after Tally import?
- Which warehouse has low stock?

## 6. How StockPilot AI Is Different From Tally

Tally is the system of record.

Tally is used for:

- accounting;
- GST and compliance;
- ledgers;
- vouchers;
- invoicing;
- statutory reports;
- historical accounting reports.

StockPilot AI is the decision engine.

StockPilot AI is used for:

- AI demand forecasting;
- reorder recommendations;
- stockout prediction;
- dead-stock action center;
- profit-drop explanation;
- margin leakage detection;
- product cleanup and duplicate detection;
- mobile warehouse operations;
- dealer/customer ordering portal;
- multi-source import from Tally, Excel, CSV, XML, and JSON.

Positioning statement:

StockPilot AI is designed to work with Tally, not replace it.

## 7. Target Customers

The first target users are:

- small retail shops;
- grocery and FMCG distributors;
- hardware, electrical, plumbing, and wholesale businesses;
- small warehouses;
- multi-branch businesses;
- businesses currently using Tally, Excel, or basic ERP tools.

The ideal early customer is a business owner who already has sales and stock data but does not have a smart system that tells them what action to take next.

## 8. MVP Scope

The MVP is a working full-stack product with backend APIs, database schema, web dashboard, mobile app, authentication, demo data, import flow, AI-style insights, forecasting, and role-based access.

The current MVP includes:

- secure login and registration;
- tenant/business setup;
- retail, wholesale, and hybrid business mode;
- role-based access control;
- product, customer, supplier, and warehouse management;
- stock ledger model;
- purchase invoice flow that increases stock;
- sales invoice flow that reduces stock;
- stock adjustment and transfer support;
- dashboard with real calculated data;
- low-stock alerts;
- dead-stock insights;
- demand forecasting;
- smart reorder suggestions;
- profit-drop assistant;
- Tally/Excel/CSV/XML/JSON import pipeline;
- staging, validation, preview, and commit flow;
- data-quality cleanup checks;
- AI assistant with suggested questions;
- customer/dealer portal;
- mobile app for warehouse staff;
- payment reminders and received-payment flow;
- audit logs;
- Docker/local setup documentation;
- backend, web, and mobile validation commands.

## 9. Core Product Features

### Inventory Intelligence

StockPilot AI uses a stock ledger instead of only storing a product quantity. Every stock change is recorded as a movement.

Examples:

- purchase increases stock;
- sale reduces stock;
- transfer creates one outgoing and one incoming movement;
- adjustment requires a reason;
- opening balance is stored as a movement.

This makes stock more auditable and suitable for Tally imports, returns, damaged stock, multi-warehouse usage, and forecasting.

### Forecasting

The system uses historical sales and stock data to estimate:

- next 7 days demand;
- next 30 days demand;
- average daily demand;
- expected stockout date;
- reorder point;
- recommended reorder quantity.

The reorder engine considers:

- current stock;
- recent sales trend;
- supplier lead time;
- safety stock;
- minimum order quantity;
- pending purchase orders;
- pending sales orders;
- warehouse-level stock.

### Dead-Stock Action Center

The system identifies products that have stock but no recent sales.

It shows:

- product name;
- warehouse;
- quantity;
- stock value;
- last sold date;
- days since last sale;
- blocked capital;
- recommended action.

Suggested actions can include discounting, bundling, transferring, stopping reorder, returning to supplier, or marking as clearance.

### Profit-Drop Assistant

The owner can ask:

Why did my profit drop this month?

The system checks real business data and explains possible reasons, such as:

- sales dropped compared to the previous month;
- purchase cost increased;
- gross margin reduced;
- discounts increased;
- returns increased;
- slow-moving products blocked capital;
- low-margin customers increased purchases.

It does not invent numbers. If data is missing, it says what data is missing.

### Data Quality Cleanup

Imported product names from Tally or Excel can be messy.

Example:

- MAGGI 70GM
- MAGGI MASALA 70 G
- MAGGI NOODLES 70GM
- MAGGI MASLA 70

StockPilot AI can detect similar products and suggest a normalized name, brand, category, and unit size.

Example suggestion:

- brand: Maggi
- category: Instant Noodles
- unit size: 70g
- normalized name: Maggi Masala Noodles 70g

### Dealer/Customer Portal

Wholesale customers can log in and:

- view assigned products;
- see customer-specific pricing;
- place sales orders;
- view outstanding balance;
- see invoices;
- reorder previous items.

This is a strong differentiator because most accounting systems focus on internal users, while StockPilot AI also supports customer-facing ordering.

### Mobile Warehouse App

The mobile app is designed for staff, not only owners.

Staff can:

- log in;
- search products;
- enter barcode manually;
- perform quick stock in;
- perform quick stock out;
- upload invoice files;
- see low-stock alerts;
- view reorder suggestions;
- record stock adjustments with reason.

## 10. User Roles

The product supports multiple roles:

- Owner;
- Admin;
- Manager;
- Warehouse Staff;
- Sales Staff;
- Purchase Manager;
- Accountant;
- Viewer;
- Auditor;
- Customer User.

Each role sees only the relevant parts of the system. For example, warehouse staff should not see profit analysis, and customer users should only see their own portal.

## 11. Demo Flow

A good demo can be explained in this order:

1. Log in as the demo business owner.
2. Open dashboard and show stock value, monthly sales, profit, low-stock count, dead-stock value, and outstanding receivables.
3. Show Today's Actions to explain how the product gives priority tasks.
4. Open Products and show product catalog.
5. Create a purchase and show stock increasing.
6. Create a sale and show stock reducing.
7. Open Forecasts and show reorder suggestions.
8. Open Dead Stock and show blocked money.
9. Open Data Quality and show duplicate/messy products.
10. Open Tally Integration and show import status and data-quality warnings.
11. Ask AI Assistant: Why did my profit drop this month?
12. Log in as a customer user and show the dealer portal.
13. Open mobile app and show quick warehouse stock operations.

## 12. Example Pitch Script

Here is a simple way to explain it to a friend:

Most small businesses already use Tally or Excel, but they still make stock decisions manually. They know what happened in the past, but they do not always know what to do next.

StockPilot AI connects with their existing Tally or Excel data and turns it into business actions. It predicts which products may go out of stock, identifies dead stock, recommends reorder quantity, explains profit drops, and helps follow up customer payments.

It is not an accounting replacement. Tally remains the accounting system. StockPilot AI becomes the intelligence layer on top of it.

For example, a distributor can upload Tally exports and quickly see: Parle-G may stock out in 6 days, Surf Excel cost increased, Maggi duplicate items need cleanup, and Rs 42,000 is blocked in slow-moving stock. The owner can then reorder, adjust prices, clear old stock, and call customers for payment.

The MVP already has a backend, database, web dashboard, mobile app, import system, forecasting, AI assistant, stock ledger, role-based access, and demo data.

## 13. Why This Can Become a Business

The opportunity is strong because:

- millions of small businesses use Tally and Excel;
- many cannot afford enterprise BI systems;
- owners want practical actions, not complicated analytics;
- inventory mistakes directly affect cash flow;
- dead stock and stockouts are visible pain points;
- AI can be positioned as an assistant, not a replacement;
- the product can start as an affordable SaaS layer.

## 14. Possible Pricing Model

Simple MVP pricing options:

- Free trial for 14 days;
- Starter plan for small retail shops;
- Distributor plan for multi-warehouse and customer portal;
- Premium plan with advanced AI insights and multiple users;
- onboarding/import setup fee for businesses with complex Tally data.

Example:

- Starter: Rs 999/month;
- Growth: Rs 2,999/month;
- Distributor: Rs 5,999/month;
- Setup service: one-time import and cleanup assistance.

## 15. Technical Stack

Backend:

- Java 21;
- Spring Boot 3;
- Spring Security;
- JWT authentication;
- Spring Data JPA;
- PostgreSQL;
- Flyway migrations;
- Redis caching;
- MinIO-compatible object storage;
- OpenAPI/Swagger;
- Maven;
- Docker Compose support.

Web App:

- Next.js;
- React;
- TypeScript;
- Tailwind CSS;
- TanStack Query;
- responsive admin dashboard;
- charts, tables, forms, role-aware pages.

Mobile App:

- React Native;
- Expo;
- TypeScript;
- Android build support;
- mobile stock operations and customer/staff workflows.

AI/Intelligence:

- local rule-based AI service;
- optional LLM provider abstraction;
- forecasting logic;
- profit explanation logic;
- reorder engine;
- product cleanup suggestions.

## 16. What Is Already Working In The MVP

The project currently includes:

- backend APIs;
- authentication;
- tenant isolation;
- role permissions;
- stock ledger;
- purchases and sales;
- dashboard calculations;
- import staging and validation;
- forecasting;
- AI assistant;
- data quality checks;
- dead-stock page;
- customer portal;
- payment reminder flow;
- mobile app structure;
- demo mode;
- tests and build validation.

Recent production-hardening work also added:

- stricter tenant isolation;
- AI permission checks;
- portal catalog isolation;
- guardrail tests to catch unsafe tenant-owned repository access;
- documentation for security behavior.

## 17. Current MVP Limitations

This is still an MVP, so some areas can be improved before real customer launch:

- production-grade Tally import mapping needs more real-world samples;
- customer price-list assignment needs a polished admin UI;
- mobile UI can be made more field-staff friendly;
- OCR is currently local/mock-style, not a real paid OCR integration;
- advanced accounting/GST features are intentionally not part of the product;
- billing/subscription module is not fully implemented;
- more reports and exports can be added;
- deployment needs production cloud setup and monitoring.

## 18. Roadmap

Short-term improvements:

- improve portal catalog assignment UI;
- improve mobile warehouse UX;
- add more Tally import templates;
- add better product merge workflow;
- add customer payment reminder notifications;
- add WhatsApp/SMS follow-up integrations;
- improve purchase-order creation from reorder suggestions.

Medium-term improvements:

- real OCR provider integration;
- supplier portal;
- multi-branch approval workflows;
- predictive pricing/margin recommendations;
- advanced category-level forecasting;
- data export and backup module;
- subscription billing.

Long-term vision:

StockPilot AI can become the intelligence operating system for small distributors and retailers. Tally remains the accounting system, while StockPilot AI becomes the daily decision layer for stock, profit, reorder, customer ordering, and warehouse action.

## 19. Best Closing Statement

StockPilot AI helps business owners move from looking at old reports to taking smarter daily actions.

It does not replace the systems they already trust. It connects with them, understands their data, and tells them what to do next.

That is the core value: better stock decisions, less blocked money, fewer stockouts, and clearer profit visibility.
