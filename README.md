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

The Drop-In UI handles card data capture and returns a `payment_reference` token. The server then uses the GP API SDK to charge that token — either as a straight charge (one-time) or with `StoredCredential` metadata attached (recurring).

## Donation Types

| Type | SDK Call | StoredCredential |
|---|---|---|
| One-time | `card.charge().withCurrency().execute()` | Not required |
| Recurring | `card.charge().withCurrency().withStoredCredential().execute()` | Payer-initiated, Recurring, First sequence |

## Recurring Donation Parameters

| Field | Values | Description |
|---|---|---|
| `frequency` | `monthly`, `weekly`, `quarterly`, `annually` | Billing cadence |
| `start_date` | `YYYY-MM-DD` | First charge date (defaults to today) |
| `duration_type` | `ongoing`, `end_date`, `num_payments` | How long the recurring series runs |
| `end_date` | `YYYY-MM-DD` | Required only when `duration_type` is `end_date` |
| `num_payments` | integer | Required only when `duration_type` is `num_payments` |

## API Endpoints

All implementations expose the same two endpoints.

### `POST /get-access-token`

Generates a short-lived access token for initializing the GP Drop-In UI. No request body required.

**Response:**
```json
{
  "success": true,
  "token": "eyJhbGciOiJSUzI1NiIsInR5...",
  "expiresIn": 600
}
```

### `POST /process-donation`

Processes a one-time donation or initiates a recurring donation setup.

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

**One-time success response:**
```json
{
  "success": true,
  "message": "Thank you for your donation!",
  "data": {
    "transactionId": "TXN_abc123",
    "status": "CAPTURED",
    "amount": 25.00,
    "currency": "USD",
    "donorName": "Jane Smith",
    "donorEmail": "jane@example.com",
    "timestamp": "2025-01-15 12:00:00"
  }
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

**Recurring success response:**
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
    "donorName": "Jane Smith",
    "donorEmail": "jane@example.com",
    "frequency": "monthly",
    "startDate": "2025-02-01",
    "durationType": "ongoing",
    "timestamp": "2025-01-15 12:00:00"
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
    "details": "Transaction declined",
    "timestamp": "2025-01-15T12:00:00.000Z"
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
- 📋 **GitHub Discussions** — [github.com/globalpayments-samples](https://github.com/globalpayments-samples)
- 📧 **Newsletter** — [Subscribe](https://www.globalpayments.com/en-gb/modals/newsletter)
- 💼 **LinkedIn** — [Global Payments for Developers](https://www.linkedin.com/showcase/global-payments-for-developers/posts/?feedView=all)

Have a question or found a bug? [Open an issue](https://github.com/globalpayments-samples/donation-form-one-time-recurring-payments/issues) or reach out at [communityexperience@globalpay.com](mailto:communityexperience@globalpay.com).

## License

MIT
