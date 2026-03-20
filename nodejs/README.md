# Node.js — Donation Form

One-time and recurring donation processing using Express.js and the Global Payments GP API SDK.

## Prerequisites

| Runtime | Version | Package Manager |
|---|---|---|
| Node.js | 18+ | npm |

## Dependencies

| Package | Version |
|---|---|
| `globalpayments-api` | ^3.10.6 |
| `express` | ^4.18.2 |
| `dotenv` | ^16.3.1 |

## Quick Start

1. Navigate to the `nodejs` directory
2. Copy `.env.sample` to `.env` and fill in your GP API credentials
3. Install dependencies:
   ```bash
   npm install
   ```
4. Start the server:
   ```bash
   npm start
   ```
5. Open `http://localhost:8000`

## Project Structure

```
nodejs/
├── server.js          # Express server, SDK config, route handlers
├── index.html         # Donation form with GP Drop-In UI
├── package.json       # Dependencies
├── .env.sample        # Environment variable template
├── Dockerfile
└── run.sh
```

## API Endpoints

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
    "details": "Transaction declined",
    "timestamp": "2025-01-15T12:00:00.000Z"
  }
}
```

## Payment Flow

1. The browser loads `index.html` and calls `POST /get-access-token` to initialize the GP Drop-In UI
2. The donor enters card details; the Drop-In UI tokenizes them and returns a `payment_reference`
3. The frontend submits the donation form to `POST /process-donation`
4. `server.js` reads `payment_type` and routes to `processOneTime()` or `processRecurring()`
5. The GP API SDK charges the token and returns a transaction response

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
