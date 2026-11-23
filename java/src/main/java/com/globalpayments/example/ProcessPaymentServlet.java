package com.globalpayments.example;

import com.global.api.ServicesContainer;
import com.global.api.entities.StoredCredential;
import com.global.api.entities.Transaction;
import com.global.api.entities.enums.Channel;
import com.global.api.entities.enums.Environment;
import com.global.api.entities.enums.StoredCredentialInitiator;
import com.global.api.entities.enums.StoredCredentialSequence;
import com.global.api.entities.enums.StoredCredentialType;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.entities.exceptions.GatewayException;
import com.global.api.paymentMethods.CreditCardData;
import com.global.api.serviceConfigs.GpApiConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@WebServlet(urlPatterns = {"/get-access-token", "/process-donation"})
public class ProcessPaymentServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Dotenv dotenv = Dotenv.load();
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    /** A request problem whose details are safe to send back to the browser. */
    private static class DonationException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String details;

        DonationException(String details) {
            super(details);
            this.details = details;
        }

        String getDetails() {
            return details;
        }
    }

    @Override
    public void init() throws ServletException {
        try {
            GpApiConfig config = new GpApiConfig();
            config.setAppId(dotenv.get("GP_APP_ID"));
            config.setAppKey(dotenv.get("GP_APP_KEY"));
            String env = dotenv.get("GP_APP_ENVIRONMENT", "sandbox");
            config.setEnvironment("production".equals(env) ? Environment.PRODUCTION : Environment.TEST);
            config.setChannel(Channel.CardNotPresent);
            config.setCountry("US");
            ServicesContainer.configureService(config);
        } catch (Exception e) {
            throw new ServletException("Failed to configure Global Payments SDK", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String path = request.getServletPath();
        if ("/get-access-token".equals(path)) {
            handleGetAccessToken(response);
        } else if ("/process-donation".equals(path)) {
            handleProcessDonation(request, response);
        }
    }

    private void handleGetAccessToken(HttpServletResponse response) throws IOException {
        try {
            String appId = dotenv.get("GP_APP_ID");
            String appKey = dotenv.get("GP_APP_KEY");
            String envName = dotenv.get("GP_APP_ENVIRONMENT", "sandbox");

            if (appId == null || appKey == null || appId.isEmpty() || appKey.isEmpty()) {
                throw new DonationException("Missing required credentials: GP_APP_ID and GP_APP_KEY");
            }

            // Generate nonce: 16 random bytes as lowercase hex
            SecureRandom random = new SecureRandom();
            byte[] nonceBytes = new byte[16];
            random.nextBytes(nonceBytes);
            String nonce = HexFormat.of().formatHex(nonceBytes);

            // Compute secret: SHA-512(nonce + appKey)
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hashBytes = digest.digest((nonce + appKey).getBytes(StandardCharsets.UTF_8));
            String secret = HexFormat.of().formatHex(hashBytes);

            String apiEndpoint = "production".equals(envName)
                ? "https://apis.globalpay.com/ucp/accesstoken"
                : "https://apis.sandbox.globalpay.com/ucp/accesstoken";

            String requestBody = String.format(
                "{\"app_id\":\"%s\",\"nonce\":\"%s\",\"secret\":\"%s\"," +
                "\"grant_type\":\"client_credentials\",\"seconds_to_expire\":600," +
                "\"permissions\":[\"PMT_POST_Create_Single\"]}",
                appId, nonce, secret
            );

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .header("Content-Type", "application/json")
                .header("X-GP-Version", "2021-03-22")
                .header("Accept-Encoding", "identity")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonObject responseData = JsonParser.parseString(httpResponse.body()).getAsJsonObject();

            if (httpResponse.statusCode() != 200 || !responseData.has("token")) {
                String errorMessage = responseData.has("error_description")
                    ? responseData.get("error_description").getAsString()
                    : responseData.has("message")
                        ? responseData.get("message").getAsString()
                        : "Failed to generate access token";
                throw new DonationException(errorMessage);
            }

            int expiresIn = responseData.has("seconds_to_expire")
                ? responseData.get("seconds_to_expire").getAsInt()
                : 600;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("token", responseData.get("token").getAsString());
            result.addProperty("expiresIn", expiresIn);
            writeJson(response, HttpServletResponse.SC_OK, result);

        } catch (DonationException e) {
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                tokenError(e.getDetails()));
        } catch (Exception e) {
            log("Error generating access token", e);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                tokenError("An unexpected error occurred"));
        }
    }

    private JsonObject tokenError(String details) {
        JsonObject result = new JsonObject();
        result.addProperty("success", false);
        result.addProperty("message", "Error generating access token");
        result.addProperty("error", details);
        return result;
    }

    private void handleProcessDonation(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();

            if (!body.has("payment_type") || body.get("payment_type").getAsString().isEmpty()) {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    simpleError("Missing payment_type", "payment_type must be one-time or recurring"));
                return;
            }

            String paymentType = body.get("payment_type").getAsString();

            if ("one-time".equals(paymentType)) {
                processOneTime(body, response);
            } else if ("recurring".equals(paymentType)) {
                processRecurring(body, response);
            } else {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    simpleError("Invalid payment_type", "payment_type must be one-time or recurring"));
            }

        } catch (Exception e) {
            log("Error processing donation request", e);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                errorResponse("Server error", "SERVER_ERROR", "An unexpected error occurred"));
        }
    }

    private void processOneTime(JsonObject body, HttpServletResponse response) throws IOException {
        try {
            String paymentReference = getString(body, "payment_reference");
            String donorName = getString(body, "donor_name");
            String donorEmail = getString(body, "donor_email");
            String currency = body.has("currency") && !body.get("currency").isJsonNull()
                ? body.get("currency").getAsString() : "USD";
            BigDecimal amount = getAmount(body);

            if (paymentReference == null || paymentReference.isEmpty())
                throw new DonationException("Missing payment reference");
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
                throw new DonationException("Invalid amount");
            if (donorName == null || donorName.isEmpty())
                throw new DonationException("Missing donor name");
            if (donorEmail == null || donorEmail.isEmpty())
                throw new DonationException("Missing donor email");

            CreditCardData card = new CreditCardData();
            card.setToken(paymentReference);

            Transaction transaction = card.charge(amount)
                .withCurrency(currency)
                .execute();

            if ("00".equals(transaction.getResponseCode()) || "SUCCESS".equals(transaction.getResponseCode())) {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
                JsonObject data = new JsonObject();
                data.addProperty("transactionId", transaction.getTransactionId());
                data.addProperty("status", nullToEmpty(transaction.getResponseMessage()));
                data.addProperty("amount", new BigDecimal(amount.toPlainString()));
                data.addProperty("currency", currency);
                data.addProperty("donorName", donorName);
                data.addProperty("donorEmail", donorEmail);
                data.addProperty("timestamp", timestamp);

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "Thank you for your donation!");
                result.add("data", data);
                writeJson(response, HttpServletResponse.SC_OK, result);
            } else {
                throw new DonationException("Transaction declined: " + transaction.getResponseMessage());
            }

        } catch (DonationException e) {
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                errorResponse("Donation processing failed", "SERVER_ERROR", e.getDetails()));
        } catch (GatewayException e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                errorResponse("Donation processing failed", "GATEWAY_ERROR", e.getMessage()));
        } catch (ApiException e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                errorResponse("API error", "API_ERROR", e.getMessage()));
        } catch (Exception e) {
            log("Donation processing failed", e);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                errorResponse("Donation processing failed", "SERVER_ERROR", "An unexpected error occurred"));
        }
    }

    private void processRecurring(JsonObject body, HttpServletResponse response) throws IOException {
        try {
            String paymentReference = getString(body, "payment_reference");
            String donorName = getString(body, "donor_name");
            String donorEmail = getString(body, "donor_email");
            String currency = body.has("currency") && !body.get("currency").isJsonNull()
                ? body.get("currency").getAsString() : "USD";
            String frequency = getString(body, "frequency");
            String startDate = body.has("start_date") && !body.get("start_date").isJsonNull()
                ? body.get("start_date").getAsString()
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String durationType = body.has("duration_type") && !body.get("duration_type").isJsonNull()
                ? body.get("duration_type").getAsString() : "ongoing";
            String endDate = body.has("end_date") && !body.get("end_date").isJsonNull()
                ? body.get("end_date").getAsString() : null;
            Integer numPayments = body.has("num_payments") && !body.get("num_payments").isJsonNull()
                ? body.get("num_payments").getAsInt() : null;
            BigDecimal amount = getAmount(body);

            if (paymentReference == null || paymentReference.isEmpty())
                throw new DonationException("Missing payment reference");
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
                throw new DonationException("Invalid amount");
            if (donorName == null || donorName.isEmpty())
                throw new DonationException("Missing donor name");
            if (donorEmail == null || donorEmail.isEmpty())
                throw new DonationException("Missing donor email");
            if (frequency == null || frequency.isEmpty())
                throw new DonationException("Missing frequency for recurring donation");

            CreditCardData card = new CreditCardData();
            card.setToken(paymentReference);

            StoredCredential storedCredential = new StoredCredential();
            storedCredential.setInitiator(StoredCredentialInitiator.CardHolder);
            storedCredential.setType(StoredCredentialType.Recurring);
            storedCredential.setSequence(StoredCredentialSequence.First);

            Transaction transaction = card.charge(amount)
                .withCurrency(currency)
                .withStoredCredential(storedCredential)
                .execute();

            if ("00".equals(transaction.getResponseCode()) || "SUCCESS".equals(transaction.getResponseCode())) {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);

                JsonObject data = new JsonObject();
                data.addProperty("transactionId", transaction.getTransactionId());
                data.addProperty("cardBrandTransactionId", transaction.getCardBrandTransactionId());
                data.addProperty("status", nullToEmpty(transaction.getResponseMessage()));
                data.addProperty("amount", new BigDecimal(amount.toPlainString()));
                data.addProperty("currency", currency);
                data.addProperty("donorName", donorName);
                data.addProperty("donorEmail", donorEmail);
                data.addProperty("frequency", frequency);
                data.addProperty("startDate", startDate);
                data.addProperty("durationType", durationType);
                data.addProperty("timestamp", timestamp);

                if ("end_date".equals(durationType) && endDate != null) {
                    data.addProperty("endDate", endDate);
                }
                if ("num_payments".equals(durationType) && numPayments != null) {
                    data.addProperty("numPayments", numPayments);
                }

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "Recurring donation set up successfully!");
                result.add("data", data);
                writeJson(response, HttpServletResponse.SC_OK, result);
            } else {
                throw new DonationException("Transaction declined: " + transaction.getResponseMessage());
            }

        } catch (DonationException e) {
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                errorResponse("Recurring donation setup failed", "SERVER_ERROR", e.getDetails()));
        } catch (GatewayException e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                errorResponse("Recurring donation setup failed", "GATEWAY_ERROR", e.getMessage()));
        } catch (ApiException e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                errorResponse("API error", "API_ERROR", e.getMessage()));
        } catch (Exception e) {
            log("Recurring donation setup failed", e);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                errorResponse("Recurring donation setup failed", "SERVER_ERROR", "An unexpected error occurred"));
        }
    }

    private String getString(JsonObject body, String key) {
        return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : null;
    }

    private BigDecimal getAmount(JsonObject body) {
        if (!body.has("amount") || body.get("amount").isJsonNull()) return null;
        try {
            return body.get("amount").getAsBigDecimal();
        } catch (Exception e) {
            return null;
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private JsonObject simpleError(String message, String error) {
        JsonObject result = new JsonObject();
        result.addProperty("success", false);
        result.addProperty("message", message);
        result.addProperty("error", error);
        return result;
    }

    private JsonObject errorResponse(String message, String code, String details) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("details", details);

        JsonObject result = new JsonObject();
        result.addProperty("success", false);
        result.addProperty("message", message);
        result.add("error", error);
        return result;
    }

    /** Writes a JSON body. Gson escapes HTML characters, so echoed values can't become markup. */
    private void writeJson(HttpServletResponse response, int status, JsonObject body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(GSON.toJson(body));
    }
}
