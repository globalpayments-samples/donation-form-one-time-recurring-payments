# PHP — Donation Form

One-time and recurring donation processing using PHP and the Global Payments GP API SDK.

## Prerequisites

| Runtime | Version | Package Manager |
|---|---|---|
| PHP | 8.1+ | Composer 2 |

## Dependencies

| Package | Version |
|---|---|
| `globalpayments/php-sdk` | ^13.4 |
| `vlucas/phpdotenv` | ^5.5 |

## Quick Start

1. Navigate to the `php` directory
2. Copy `.env.sample` to `.env` and fill in your GP API credentials
3. Install dependencies:
   ```bash
   composer install
   ```
4. Start the server:
   ```bash
   php -S 0.0.0.0:8000
   ```
5. Open `http://localhost:8000`

## Project Structure

```
php/
├── index.html              # Donation form with GP Drop-In UI
├── get-access-token.php    # Access token endpoint
├── process-donation.php    # Routes to one-time or recurring handler
├── process-one-time.php    # One-time donation logic
├── process-recurring.php   # Recurring donation logic
├── composer.json           # Dependencies
├── .env.sample             # Environment variable template
├── Dockerfile
└── run.sh
```

## API Endpoints

PHP uses individual script files rather than a framework router.

### `POST /get-access-token.php`

Generates a short-lived access token for the GP Drop-In UI. No request body required.

**Response:**
```json
{
  "success": true,
  "token": "eyJhbGciOiJSUzI1NiIsInR5...",
  "expiresIn": 600
}
```

### `POST /process-donation.php`

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
    "timestamp": "2025-01-15 12:00:00"
  }
}
```

## Payment Flow

1. The browser loads `index.html` and calls `POST /get-access-token.php` to initialize the GP Drop-In UI
2. The donor enters card details; the Drop-In UI tokenizes them and returns a `payment_reference`
3. The frontend submits the donation form to `POST /process-donation.php`
4. `process-donation.php` reads `payment_type` from the JSON body and uses `require` to delegate to `process-one-time.php` or `process-recurring.php`
5. The included file initializes the GP API SDK, validates the request, charges the token, and writes the JSON response

## Configuration

Copy `.env.sample` to `.env`:

```env
GP_APP_ID=your-app-id
GP_APP_KEY=your-app-key
GP_ENVIRONMENT=sandbox
```

| Variable | Required | Description |
|---|---|---|
| `GP_APP_ID` | Yes | GP API application ID |
| `GP_APP_KEY` | Yes | GP API application key |
| `GP_ENVIRONMENT` | No | `sandbox` (default) or `production` |

> **Note:** PHP uses `GP_ENVIRONMENT` (not `GP_APP_ENVIRONMENT`) to match the variable read in `get-access-token.php`.

## Running with Docker

```bash
bash run.sh
```

## Security Considerations

- Never commit `.env` to version control
- Do not log `payment_reference` tokens
- Use HTTPS in production
