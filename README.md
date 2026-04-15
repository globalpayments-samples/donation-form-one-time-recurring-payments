# Donation Form — One-Time & Recurring Payments

This project demonstrates how to build a donation form that processes one-time donations and sets up recurring donations using the Global Payments Drop-In UI and GP API SDK.

## Overview

Developers can learn how to:

- Tokenize card data client-side using the GP Drop-In UI
- Charge a tokenized card for an immediate one-time donation
- Set up a recurring donation using `StoredCredential` with payer-initiated, first-sequence recurring type
- Route both donation types through a single `POST /process-donation` endpoint

## Features

- **One-Time Donations** — Charge tokenized cards for immediate one-time payments
- **Recurring Donations** — Set up recurring billing with `StoredCredential` metadata
- **GP Drop-In UI** — PCI-compliant card data capture via pre-built payment form
- **Flexible Scheduling** — Monthly, weekly, quarterly, and annual recurring frequencies
- **Duration Options** — Ongoing, end-date, or fixed number of payments
- **Multi-Language Support** — Identical implementation across PHP, Node.js, .NET, and Java
- **Access Token Management** — Server-generated tokens for Drop-In UI initialization

## Available Implementations

- [Node.js (Express)](./nodejs)
- [Java (Jakarta EE Servlet / Tomcat)](./java)
- [.NET Core (ASP.NET Minimal APIs)](./dotnet)
- [PHP](./php)

## How It Works

```
Phase 1 — Tokenization (client-side):
  Browser → GP Drop-In UI → GP API → payment_reference token

Phase 2 — Charge (server-side):
  Frontend → POST /process-donation → Backend → GP API SDK → GP API
```

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/get-access-token` | Generate scoped access token for Drop-In UI initialization |
| `POST` | `/process-donation` | Process one-time or recurring donation |

## 🚀 Quick Start

### Prerequisites
- Global Payments GP API account with sandbox credentials ([Sign up here](https://developer.globalpay.com/))
- Development environment for your chosen language
- Package manager (npm, composer, maven, or dotnet)

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/globalpayments-samples/donation-form-one-time-recurring-payments.git
   cd donation-form-one-time-recurring-payments
   ```

2. **Choose your implementation**
   ```bash
   cd php  # or nodejs, dotnet, java
   ```

3. **Configure environment**
   ```bash
   cp .env.sample .env
   # Edit .env with your GP API credentials:
   # GP_APP_ID=your_gp_api_app_id_here
   # GP_APP_KEY=your_gp_api_app_key_here
   # GP_APP_ENVIRONMENT=sandbox
   ```

4. **Install dependencies and run**
   ```bash
   ./run.sh
   ```

   Or manually per language:
   ```bash
   # PHP
   composer install && php -S localhost:8000

   # Node.js
   npm install && npm start

   # .NET
   dotnet restore && dotnet run

   # Java
   mvn clean compile cargo:run
   ```

5. **Access the application**
   Open [http://localhost:8000](http://localhost:8000) in your browser

## 🧪 Development & Testing

### Test Cards (GP API Sandbox)

| Card | Number | CVV | Expiry |
|------|--------|-----|--------|
| **Visa (Approved)** | 4263970000005262 | 123 | Any future date |
| **Visa (Declined)** | 4000120000001154 | 123 | Any future date |

### Donation Types

| Type | SDK Call | StoredCredential |
|------|---------|------------------|
| One-time | `card.charge().withCurrency().execute()` | Not required |
| Recurring | `card.charge().withCurrency().withStoredCredential().execute()` | Payer-initiated, Recurring, First sequence |

### Recurring Parameters

| Field | Values | Description |
|-------|--------|-------------|
| `frequency` | `monthly`, `weekly`, `quarterly`, `annually` | Billing cadence |
| `start_date` | `YYYY-MM-DD` | First charge date (defaults to today) |
| `duration_type` | `ongoing`, `end_date`, `num_payments` | How long the series runs |
| `end_date` | `YYYY-MM-DD` | Required when `duration_type` is `end_date` |
| `num_payments` | integer | Required when `duration_type` is `num_payments` |

## 💳 Payment Flow

### One-Time Donation
1. Donor fills in amount and card details via Drop-In UI
2. Drop-In UI tokenizes card, returning a `payment_reference`
3. Frontend sends token + amount to `POST /process-donation` with `payment_type: "one-time"`
4. Backend charges the token via GP API SDK
5. Success response with transaction ID and confirmation

### Recurring Donation
1. Donor fills in amount, frequency, start date, and card details
2. Drop-In UI tokenizes card, returning a `payment_reference`
3. Frontend sends token + recurring params to `POST /process-donation` with `payment_type: "recurring"`
4. Backend charges with `StoredCredential` (payer-initiated, first-sequence, recurring)
5. Success response with transaction ID, `cardBrandTransactionId`, and schedule details

## 🔧 API Reference

### POST /get-access-token

Generates a short-lived access token for initializing the GP Drop-In UI. No request body required.

**Response:**
```json
{
  "success": true,
  "token": "eyJhbGciOiJSUzI1NiIsInR5...",
  "expiresIn": 600
}
```

### POST /process-donation

Processes a one-time or recurring donation.

**One-time request:**
```json
{
  "payment_type": "one-time",
  "payment_reference": "PMT_abc123",
  "amount": "25.00",
  "currency": "USD",
  "donor_name": "Jane Smith",
  "donor_email": "jane@example.com"
}
```

**Recurring request:**
```json
{
  "payment_type": "recurring",
  "payment_reference": "PMT_abc123",
  "amount": "10.00",
  "currency": "USD",
  "donor_name": "Jane Smith",
  "donor_email": "jane@example.com",
  "frequency": "monthly",
  "start_date": "2025-02-01",
  "duration_type": "ongoing"
}
```

**Success response (recurring):**
```json
{
  "success": true,
  "message": "Recurring donation set up successfully!",
  "data": {
    "transactionId": "TXN_xyz789",
    "cardBrandTransactionId": "MCT_abc123",
    "status": "CAPTURED",
    "amount": 10.00,
    "currency": "USD",
    "frequency": "monthly",
    "startDate": "2025-02-01",
    "durationType": "ongoing"
  }
}
```

**Error response:**
```json
{
  "success": false,
  "message": "Donation processing failed",
  "error": {
    "code": "GATEWAY_ERROR",
    "details": "Transaction declined"
  }
}
```

## Platform-Specific Entry Points

| Implementation | Entry File | Framework |
|---|---|---|
| Node.js | `server.js` | Express 4.x |
| Java | `ProcessPaymentServlet.java` | Jakarta EE / Tomcat 10 |
| .NET | `Program.cs` | ASP.NET Minimal APIs / .NET 9 |
| PHP | `process-donation.php` | Built-in server |

## Configuration

Each implementation reads credentials from a `.env` file. Copy `.env.sample` to `.env` and fill in your values.

| Variable | Required | Description |
|---|---|---|
| `GP_APP_ID` | Yes | GP API application ID from developer.globalpayments.com |
| `GP_APP_KEY` | Yes | GP API application key |
| `GP_APP_ENVIRONMENT` | No | `sandbox` (default) or `production` (PHP uses `GP_ENVIRONMENT`) |

## Prerequisites

- Developer account and sandbox credentials at [developer.globalpayments.com](https://developer.globalpayments.com)
- Language runtime for your chosen implementation (see each subfolder's README)

## Test Cards

| Brand | Number | CVV | Expiry |
|-------|--------|-----|--------|
| Visa | 4263 9826 4026 9299 | 123 | Any future |
| Mastercard | 5425 2334 2424 1200 | 123 | Any future |

## Security Considerations

- Store credentials in `.env` files and never commit them to version control
- The `payment_reference` token is single-use and short-lived — do not log or store it
- Use HTTPS in production
- The Drop-In UI handles all raw card data; your server never sees card numbers, CVVs, or expiry dates

## Resources

- [Global Payments Developer Portal](https://developer.globalpayments.com/)
- [API Reference](https://developer.globalpayments.com/api/references-overview)
- [Test Cards](https://developer.globalpayments.com/resources/test-cards)
- [Drop-In UI Guide](https://developer.globalpayments.com/docs/payments/online/drop-in-ui-guide)
- [PHP SDK](https://github.com/globalpayments/php-sdk)
- [Node.js SDK](https://github.com/globalpayments/node-sdk)
- [Java SDK](https://github.com/globalpayments/java-sdk)
- [.NET SDK](https://github.com/globalpayments/dotnet-sdk)

## Community

- 🌐 **Developer Portal** — [developer.globalpayments.com](https://developer.globalpayments.com)
- 💬 **Discord** — [Join the community](https://discord.gg/myER9G9qkc)
- 📋 **GitHub Discussions** — [github.com/orgs/globalpayments/discussions](https://github.com/orgs/globalpayments/discussions)
- 📧 **Newsletter** — [Subscribe](https://www.globalpayments.com/en-gb/modals/newsletter)
- 💼 **LinkedIn** — [Global Payments for Developers](https://www.linkedin.com/showcase/global-payments-for-developers/posts/?feedView=all)

Have a question or found a bug? [Open an issue](https://github.com/globalpayments-samples/donation-form-one-time-recurring-payments/issues) or reach out at [communityexperience@globalpay.com](mailto:communityexperience@globalpay.com).

## License

MIT
