# Donation Form — One-Time & Recurring Payments

A comprehensive multi-language demonstration of donation payment processing using the Global Payments GP API. This example showcases both one-time and recurring donation flows, featuring the GP Drop-In UI for secure client-side card tokenization and StoredCredential-based recurring billing setup across multiple programming languages.

## 🚀 Features

### Core Payment Capabilities
- **One-Time Donations** - Immediate card charges for single donations
- **Recurring Donations** - Subscription-based recurring billing with StoredCredential metadata
- **GP Drop-In UI Integration** - Secure client-side card tokenization (PCI-compliant)
- **Flexible Scheduling** - Monthly, weekly, quarterly, and annual billing frequencies
- **Duration Options** - Ongoing, end date, or fixed number of payments

### Development & Testing
- **GP API Sandbox** - Full sandbox environment for development and testing
- **Comprehensive Web Interface** - Unified donation form for both payment types
- **Test Card Support** - Use GP API sandbox test cards for development
- **Consistent API Design** - Identical endpoints and behavior across all implementations

### Technical Features
- **Single Endpoint Processing** - Routes one-time and recurring donations through `/process-donation`
- **Access Token Generation** - Backend generates scoped tokens for Drop-In UI initialization
- **StoredCredential Support** - Payer-initiated, first-sequence recurring credential storage
- **Environment Configuration** - Secure credential management with .env files

## 🌐 Available Implementations

Each implementation provides identical functionality with language-specific best practices:

| Language | Framework | Requirements | Status |
|----------|-----------|--------------|--------|
| **[PHP](./php/)** - ([Preview](https://githubbox.com/globalpayments-samples/donation-form-one-time-recurring-payments/tree/main/php)) | Native PHP | PHP 7.4+, Composer | ✅ Complete |
| **[Node.js](./nodejs/)** - ([Preview](https://githubbox.com/globalpayments-samples/donation-form-one-time-recurring-payments/tree/main/nodejs)) | Express.js | Node.js 18+, npm | ✅ Complete |
| **[.NET](./dotnet/)** - ([Preview](https://githubbox.com/globalpayments-samples/donation-form-one-time-recurring-payments/tree/main/dotnet)) | ASP.NET Core | .NET 9.0+ | ✅ Complete |
| **[Java](./java/)** - ([Preview](https://githubbox.com/globalpayments-samples/donation-form-one-time-recurring-payments/tree/main/java)) | Jakarta EE | Java 11+, Maven | ✅ Complete |

## 🏗️ Architecture Overview

### Frontend Architecture
- **GP Drop-In UI** - Secure card data capture via Global Payments hosted UI component
- **Unified Donation Form** - Single form supporting both one-time and recurring donation types
- **Real-Time Validation** - Client-side form validation and donation type switching
- **Responsive Design** - Clean interface with donation amount presets and custom input

### Backend Architecture
- **RESTful API Design** - Consistent endpoints across all implementations
- **Token-Based Processing** - Server charges the `payment_reference` token from Drop-In UI
- **StoredCredential Handling** - Attaches recurring metadata on first-sequence charges
- **Error Handling** - Structured error responses with categorized error codes

### How It Works

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

## 🔧 Customization

### Extending Functionality
Each implementation provides a solid foundation for:
- **Custom Donation Amounts** - Modify presets and currency options
- **Donor Receipts** - Add email confirmation and tax receipt generation
- **Campaign Tracking** - Associate donations with fundraising campaigns
- **Payment Method Management** - Store and reuse donor payment methods
- **Reporting** - Add donation history and analytics dashboards

### Production Considerations
Before deploying to production:
- **Security** - Store credentials in `.env`, never commit to version control
- **HTTPS** - Always use HTTPS in production environments
- **Token Handling** - The `payment_reference` is single-use and short-lived
- **PCI Compliance** - Drop-In UI handles all card data; your server never sees raw card details
- **Logging** - Add secure logging with PII protection

## 🤝 Contributing

This project serves as a reference implementation for GP API donation form integration. When contributing:
- Maintain consistency across all language implementations
- Follow each language's best practices and conventions
- Ensure thorough testing in the sandbox environment
- Update documentation to reflect any changes

## 📄 License

MIT License — see [LICENSE](./LICENSE) for details.

## 🆘 Support

- **Global Payments Developer Portal**: [https://developer.globalpay.com/](https://developer.globalpay.com/)
- **GP API Reference**: [https://developer.globalpay.com/api](https://developer.globalpay.com/api)
- **SDK Documentation**: Language-specific SDK guides in each implementation directory

---

**Note**: This is a demonstration application for development and testing purposes. For production use, implement additional security measures, error handling, and compliance requirements specific to your use case.
