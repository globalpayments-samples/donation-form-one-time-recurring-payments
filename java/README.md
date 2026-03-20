# Java — Donation Form

One-time and recurring donation processing using Jakarta EE servlets and the Global Payments GP API SDK.

## Prerequisites

| Runtime | Version | Build Tool |
|---|---|---|
| JDK | 23 | Maven 3.8+ |

## Dependencies

| Package | Version |
|---|---|
| `globalpayments-sdk` | 14.2.20 |
| `dotenv-java` | 3.0.0 |
| `jakarta.servlet-api` | 5.0.0 |

## Quick Start

1. Navigate to the `java` directory
2. Copy `.env.sample` to `.env` and fill in your GP API credentials
3. Build the project:
   ```bash
   mvn clean package
   ```
4. Start the server:
   ```bash
   mvn cargo:run
   ```
5. Open `http://localhost:8000`

## Project Structure

```
java/
├── src/
│   └── main/
│       ├── java/com/globalpayments/example/
│       │   └── ProcessPaymentServlet.java  # Servlet handling both endpoints
│       └── webapp/
│           └── index.html                  # Donation form with GP Drop-In UI
├── pom.xml            # Maven build + Cargo plugin for embedded Tomcat 10
├── .env.sample        # Environment variable template
├── Dockerfile
└── run.sh
```

## API Endpoints

The servlet is mapped to both `/get-access-token` and `/process-donation` via `@WebServlet`.

### `POST /get-access-token`

Generates a short-lived access token for the GP Drop-In UI. No request body required.

**Response:**
```json
{
  "success": true,
  "token": "eyJhbGciOiJSUzI1NiIsInR5...",
  "expiresIn": 600
}
```

### `POST /process-donation`

Processes a one-time donation or initiates a recurring donation.

**Request parameters:**

| Field | Type | Required | Description |
|---|---|---|---|
| `payment_type` | string | Yes | `"one-time"` or `"recurring"` |
| `payment_reference` | string | Yes | Token from GP Drop-In UI |
| `amount` | string/number | Yes | Donation amount (must be > 0) |
| `currency` | string | No | ISO currency code (default: `"USD"`) |
| `donor_name` | string | Yes | Full name of donor |
| `donor_email` | string | Yes | Email address of donor |
| `frequency` | string | Recurring only | `monthly`, `weekly`, `quarterly`, `annually` |
| `start_date` | string | No | `YYYY-MM-DD` (default: today) |
| `duration_type` | string | No | `ongoing`, `end_date`, `num_payments` (default: `ongoing`) |
| `end_date` | string | Conditional | `YYYY-MM-DD` — required when `duration_type` is `end_date` |
| `num_payments` | integer | Conditional | Required when `duration_type` is `num_payments` |

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
    "details": "Transaction declined"
  }
}
```

## Payment Flow

1. The browser loads `index.html` and calls `POST /get-access-token` to initialize the GP Drop-In UI
2. The donor enters card details; the Drop-In UI tokenizes them and returns a `payment_reference`
3. The frontend submits the donation form to `POST /process-donation`
4. `ProcessPaymentServlet.doPost()` reads `payment_type` from the JSON body and dispatches to `processOneTime()` or `processRecurring()`
5. The GP API SDK charges the token and returns a transaction response
6. The SDK is configured once in the servlet's `init()` method and reused across requests

## Configuration

Copy `.env.sample` to `.env`:

```env
GP_APP_ID=your-app-id
GP_APP_KEY=your-app-key
GP_APP_ENVIRONMENT=sandbox
```

| Variable | Required | Description |
|---|---|---|
| `GP_APP_ID` | Yes | GP API application ID |
| `GP_APP_KEY` | Yes | GP API application key |
| `GP_APP_ENVIRONMENT` | No | `sandbox` (default) or `production` |

## Running with Docker

```bash
bash run.sh
```

## Security Considerations

- Never commit `.env` to version control
- Do not log `payment_reference` tokens
- Use HTTPS in production
