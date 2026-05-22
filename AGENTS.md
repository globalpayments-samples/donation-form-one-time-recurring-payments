# Donation Form — One-Time & Recurring Payments

> Build a donation form that tokenizes cards in the browser and sends one-time or first recurring charges through GP API SDKs in Node.js, PHP, Java, and .NET.

## Critical Patterns

1. **Every implementation builds the Drop-In access token request by hand.** `nodejs/server.js`, `php/get-access-token.php`, `java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java`, and `dotnet/Program.cs` all post directly to `/ucp/accesstoken` with `app_id`, a random `nonce`, `secret = SHA-512(nonce + GP_APP_KEY)`, `grant_type = client_credentials`, `seconds_to_expire = 600`, `permissions = ["PMT_POST_Create_Single"]`, and `X-GP-Version: 2021-03-22`. If any of those fields drift, the browser never gets a usable Drop-In token.
2. **`payment_type=recurring` is only the first recurring payment, not a scheduler.** `processRecurring()`, `ProcessRecurring()`, and the PHP recurring handler still call `charge()` immediately and only attach stored-credential metadata (`Payer`/`CardHolder`, `Recurring`, `First`). `frequency`, `start_date`, `end_date`, and `num_payments` are echoed back in the response; nothing is stored and no later rebill job exists.
3. **The browser is hard-wired to sandbox.** Every active frontend copy calls `GlobalPayments.configure()` with `apiVersion: '2021-03-22'` and `env: 'sandbox'` in `initializePaymentForm()`. Switching only backend credentials to production leaves tokenization pointed at sandbox.

## Repository Structure

### Node.js (Express)
- [`nodejs/server.js`](nodejs/server.js) — JavaScript reference backend; `ensureSdkConfigured()`, inline `POST /get-access-token`, inline `POST /process-donation`, `processOneTime()`, and `processRecurring()` cover the full server flow.
- [`nodejs/index.html`](nodejs/index.html) — active frontend; `initializePaymentForm()`, `cardForm.on('token-success', ...)`, `buildSuccessHtml()`, and the sandbox test-card helper drive the browser flow.
- [`nodejs/.env.sample`](nodejs/.env.sample) — sample GP API credentials; `PORT` is supported by `server.js` but not listed here.
- [`nodejs/package.json`](nodejs/package.json) — Node runtime dependencies; `globalpayments-api` is pinned here.
- Storage layer / mock helpers — none; recurring metadata only lives in the request/response, and the only test helper is the browser-side test-card picker.

### PHP (built-in server)
- [`php/process-donation.php`](php/process-donation.php) — dispatcher that branches on `payment_type` and `require`s the one-time or recurring handler.
- [`php/process-one-time.php`](php/process-one-time.php) — PHP one-time reference implementation; `configureSdk()` configures `GpApiConfig`, then charges the token.
- [`php/process-recurring.php`](php/process-recurring.php) — PHP recurring reference implementation; `configureSdk()` plus `withStoredCredential()` mark the first recurring payment.
- [`php/get-access-token.php`](php/get-access-token.php) — direct UCP access-token request builder using cURL.
- [`php/index.html`](php/index.html) — active frontend; `initializePaymentForm()`, `cardForm.on('token-success', ...)`, and `buildSuccessHtml()` post to the PHP endpoints.
- [`php/config.php`](php/config.php) — legacy config endpoint exposing `PUBLIC_API_KEY`; used by stale Docker health checks, not by the donation flow.
- [`php/.env.sample`](php/.env.sample) — sample env file; includes `GP_ENVIRONMENT` and `PUBLIC_API_KEY`.
- Storage layer / mock helpers — none beyond the frontend test-card picker.

### Java (Jakarta Servlet / embedded Tomcat)
- [`java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java`](java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java) — Java reference backend; `init()`, `handleGetAccessToken()`, `handleProcessDonation()`, `processOneTime()`, and `processRecurring()` implement everything.
- [`java/src/main/webapp/index.html`](java/src/main/webapp/index.html) — active frontend; same `initializePaymentForm()` and `cardForm.on('token-success', ...)` flow as the other browser copies.
- [`java/.env.sample`](java/.env.sample) — sample GP API credentials.
- [`java/pom.xml`](java/pom.xml) — Java SDK dependency plus Cargo/Tomcat config; the embedded servlet container is fixed to port `8000`.
- Storage layer / mock helpers — none beyond the frontend test-card picker.

### .NET (ASP.NET Minimal APIs)
- [`dotnet/Program.cs`](dotnet/Program.cs) — .NET reference backend; `ConfigureGlobalPaymentsSDK()`, `HandleGetAccessToken()`, `HandleProcessDonation()`, `ProcessOneTime()`, and `ProcessRecurring()` define the API.
- [`dotnet/wwwroot/index.html`](dotnet/wwwroot/index.html) — active frontend; same `initializePaymentForm()` and `cardForm.on('token-success', ...)` pattern as the other browser copies.
- [`dotnet/.env.sample`](dotnet/.env.sample) — sample GP API credentials; `PORT` is supported in `Program.cs` but not listed here.
- [`dotnet/dotnet.csproj`](dotnet/dotnet.csproj) — package references for `GlobalPayments.Api` and `DotEnv.Net`.
- Storage layer / mock helpers — none beyond the frontend test-card picker.

### Shared
- [`README.md`](README.md) — high-level walkthrough, request fields, and sandbox setup notes.
- [`docker-compose.yml`](docker-compose.yml) — stale multi-service scaffold; still references removed `python/` and `go/` apps and `/config` health checks for non-PHP services.
- [`index.html`](index.html) — shared starter-template page at repo root; not the active donation form for any implementation.
- [`package.json`](package.json) — root convenience script that starts the Node.js server.

## API Surface

All active implementations expose the same donation behavior. PHP uses file paths; the others use clean routes.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/get-access-token` (`/get-access-token.php` in PHP) | Create a 10-minute UCP access token for initializing the Drop-In UI. |
| `POST` | `/process-donation` (`/process-donation.php` in PHP) | Validate the donation payload, then branch to one-time charging or first recurring payment setup. |
| `GET` | `/config.php` (PHP only) | Return `PUBLIC_API_KEY` for the legacy starter-template flow used by Docker health checks, not by the donation form. |

## Environment Variables

```bash
GP_APP_ID=...            # Required everywhere; GP API app ID.
GP_APP_KEY=...           # Required everywhere; GP API app key and token-secret input.
GP_APP_ENVIRONMENT=...   # Node.js, Java, and .NET charge handlers; defaults to sandbox.
GP_ENVIRONMENT=...       # PHP only (all PHP handlers); selects sandbox vs production endpoint.
PORT=8000                # Node.js and .NET read this directly; php/run.sh also honors it. Not listed in any .env.sample.
PUBLIC_API_KEY=...       # PHP config.php only; not used by the donation form flow.
```

`nodejs/.env.sample`, `java/.env.sample`, and `dotnet/.env.sample` stay in sync. `php/.env.sample` intentionally diverges because it still supports `config.php` and uses `GP_ENVIRONMENT` for all PHP handlers rather than `GP_APP_ENVIRONMENT`.

## Test Cards / Sandbox Credentials

| Card | Number | CVV | Expiry |
|------|--------|-----|--------|
| Visa | `4263970000005262` | `123` | Any future date |
| Mastercard | `5425230000004415` | `123` | Any future date |
| Discover | `6011000000000087` | `123` | Any future date |
| Amex | `374101000000608` | `1234` | Any future date |
| JCB | `3566000000000000` | `123` | Any future date |
| Diners Club | `36256000000725` | `123` | Any future date |

Get sandbox credentials from [developer.globalpay.com](https://developer.globalpay.com/).

## API Request Shape

This matters because every backend makes the UCP token request directly instead of through an SDK helper:
- Header `X-GP-Version: 2021-03-22` — required version pin.
- Body `app_id` — your GP API app ID.
- Body `nonce` — 16 random bytes rendered as lowercase hex.
- Body `secret` — SHA-512 of `nonce + GP_APP_KEY`, not the raw key.
- Body `grant_type=client_credentials` — required token flow.
- Body `seconds_to_expire=600` — all implementations request a 10-minute token.
- Body `permissions=["PMT_POST_Create_Single"]` — scopes the token for one browser tokenization operation.

## Architecture Summary

**Tokenization flow:** active `index.html`/`wwwroot/index.html` copy → `POST /get-access-token` → `GlobalPayments.configure()` + Drop-In UI → `payment_reference` token.
**One-time donation flow:** browser `cardForm.on('token-success', ...)` → `POST /process-donation` with `payment_type=one-time` → backend `charge().withCurrency().execute()` → success/error JSON.
**First recurring payment flow:** same browser tokenization → `POST /process-donation` with recurring fields → backend `charge().withStoredCredential(...First...)` → response may include `cardBrandTransactionId`, but nothing schedules the next payment.

## Security Notes

This is demo code: there is no authentication, CSRF protection, rate limiting, donor-data persistence, or recurring-billing scheduler. `php/process-one-time.php` and `php/process-recurring.php` also log credential-presence and exception details with `error_log()`, which should not ship to production. Use HTTPS and add your own consent tracking, storage, retry logic, and cancellation handling before treating this as a real recurring-donations system.

## How to Run

```bash
cd nodejs && ./run.sh   # Node.js — http://localhost:8000
cd php && ./run.sh      # PHP — http://localhost:8000
cd java && ./run.sh     # Java — http://localhost:8000
cd dotnet && ./run.sh   # .NET — http://localhost:8000
```

Avoid `docker-compose up` unless you first repair `docker-compose.yml`; it still expects missing `python/` and `go/` directories and non-existent `/config` routes for Node.js, Java, and .NET.

## How to Verify

```bash
# Access token (replace with /get-access-token.php for PHP)
curl -X POST http://localhost:8000/get-access-token
# Expected: {"success":true,"token":"...","expiresIn":600}

# One-time donation (replace with /process-donation.php for PHP)
curl -X POST http://localhost:8000/process-donation \
  -H "Content-Type: application/json" \
  -d '{"payment_type":"one-time","payment_reference":"PMT_FROM_BROWSER","amount":"25.00","donor_name":"Test Donor","donor_email":"test@example.com"}'
# Expected: success JSON with transactionId, or a structured gateway/server error

# PHP legacy config endpoint
curl http://localhost:8000/config.php
# Expected: {"success":true,"data":{"publicApiKey":"..."}}
```

You cannot get a real `payment_reference` from curl alone; generate it in a browser through the Drop-In UI first. The browser is also the only place where you can confirm the sandbox test-card picker and recurring-form toggles work.

## Making Changes

All four implementations are intended to expose the same donation behavior. Apply backend contract changes to Node.js, PHP, Java, and .NET together, ideally one language per commit. Do not edit shared files like `README.md`, `docker-compose.yml`, `index.html`, or the root `package.json` unless the change genuinely applies across the repo. The active frontends live inside each language folder, so changing only one `index.html` creates drift. Do not add new `python/` or `go/` implementations unless explicitly asked; those services are absent even though `docker-compose.yml` still references them.

## SDK Versions

- Node.js: `globalpayments-api` `^3.10.6`
- PHP: `globalpayments/php-sdk` `^13.4`
- Java: `com.heartlandpaymentsystems:globalpayments-sdk` `14.2.20`
- .NET: `GlobalPayments.Api` `9.0.16`
