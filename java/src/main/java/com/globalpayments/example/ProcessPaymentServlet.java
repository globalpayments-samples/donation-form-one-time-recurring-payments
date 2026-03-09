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
                throw new Exception("Missing required credentials: GP_APP_ID and GP_APP_KEY");
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
                throw new Exception(errorMessage);
            }

            int expiresIn = responseData.has("seconds_to_expire")
                ? responseData.get("seconds_to_expire").getAsInt()
                : 600;

            response.getWriter().write(String.format(
                "{\"success\":true,\"token\":\"%s\",\"expiresIn\":%d}",
                responseData.get("token").getAsString(),
                expiresIn
            ));

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"Error generating access token\",\"error\":\"%s\"}",
                escapeJson(e.getMessage())
            ));
        }
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
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write(
                    "{\"success\":false,\"message\":\"Missing payment_type\"," +
                    "\"error\":\"payment_type must be one-time or recurring\"}"
                );
                return;
            }

            String paymentType = body.get("payment_type").getAsString();

            if ("one-time".equals(paymentType)) {
                processOneTime(body, response);
            } else if ("recurring".equals(paymentType)) {
                processRecurring(body, response);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write(
                    "{\"success\":false,\"message\":\"Invalid payment_type\"," +
                    "\"error\":\"payment_type must be one-time or recurring\"}"
                );
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"Server error\"," +
                "\"error\":{\"code\":\"SERVER_ERROR\",\"details\":\"%s\"}}",
                escapeJson(e.getMessage())
            ));
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
                throw new Exception("Missing payment reference");
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
                throw new Exception("Invalid amount");
            if (donorName == null || donorName.isEmpty())
                throw new Exception("Missing donor name");
            if (donorEmail == null || donorEmail.isEmpty())
                throw new Exception("Missing donor email");

            CreditCardData card = new CreditCardData();
            card.setToken(paymentReference);

            Transaction transaction = card.charge(amount)
                .withCurrency(currency)
                .execute();

            if ("00".equals(transaction.getResponseCode()) || "SUCCESS".equals(transaction.getResponseCode())) {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
                response.getWriter().write(String.format(
                    "{\"success\":true,\"message\":\"Thank you for your donation!\"," +
                    "\"data\":{\"transactionId\":\"%s\",\"status\":\"%s\",\"amount\":%s," +
                    "\"currency\":\"%s\",\"donorName\":\"%s\",\"donorEmail\":\"%s\",\"timestamp\":\"%s\"}}",
                    transaction.getTransactionId(),
                    escapeJson(transaction.getResponseMessage()),
                    amount.toPlainString(),
                    currency,
                    escapeJson(donorName),
                    escapeJson(donorEmail),
                    timestamp
                ));
            } else {
                throw new Exception("Transaction declined: " + transaction.getResponseMessage());
            }

        } catch (GatewayException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"Donation processing failed\"," +
                "\"error\":{\"code\":\"GATEWAY_ERROR\",\"details\":\"%s\"}}",
                escapeJson(e.getMessage())
            ));
        } catch (ApiException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"API error\"," +
                "\"error\":{\"code\":\"API_ERROR\",\"details\":\"%s\"}}",
                escapeJson(e.getMessage())
            ));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"Donation processing failed\"," +
                "\"error\":{\"code\":\"SERVER_ERROR\",\"details\":\"%s\"}}",
                escapeJson(e.getMessage())
            ));
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
                throw new Exception("Missing payment reference");
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
                throw new Exception("Invalid amount");
            if (donorName == null || donorName.isEmpty())
                throw new Exception("Missing donor name");
            if (donorEmail == null || donorEmail.isEmpty())
                throw new Exception("Missing donor email");
            if (frequency == null || frequency.isEmpty())
                throw new Exception("Missing frequency for recurring donation");

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
                String cardBrandTxId = transaction.getCardBrandTransactionId() != null
                    ? "\"" + transaction.getCardBrandTransactionId() + "\""
                    : "null";

                StringBuilder dataJson = new StringBuilder();
                dataJson.append(String.format(
                    "{\"transactionId\":\"%s\",\"cardBrandTransactionId\":%s,\"status\":\"%s\"," +
                    "\"amount\":%s,\"currency\":\"%s\",\"donorName\":\"%s\",\"donorEmail\":\"%s\"," +
                    "\"frequency\":\"%s\",\"startDate\":\"%s\",\"durationType\":\"%s\",\"timestamp\":\"%s\"",
                    transaction.getTransactionId(),
                    cardBrandTxId,
                    escapeJson(transaction.getResponseMessage()),
                    amount.toPlainString(),
                    currency,
                    escapeJson(donorName),
                    escapeJson(donorEmail),
                    frequency,
                    startDate,
                    durationType,
                    timestamp
                ));

                if ("end_date".equals(durationType) && endDate != null) {
                    dataJson.append(String.format(",\"endDate\":\"%s\"", endDate));
                }
                if ("num_payments".equals(durationType) && numPayments != null) {
                    dataJson.append(String.format(",\"numPayments\":%d", numPayments));
                }
                dataJson.append("}");

                response.getWriter().write(String.format(
                    "{\"success\":true,\"message\":\"Recurring donation set up successfully!\",\"data\":%s}",
                    dataJson
                ));
            } else {
                throw new Exception("Transaction declined: " + transaction.getResponseMessage());
            }

        } catch (GatewayException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"Recurring donation setup failed\"," +
                "\"error\":{\"code\":\"GATEWAY_ERROR\",\"details\":\"%s\"}}",
                escapeJson(e.getMessage())
            ));
        } catch (ApiException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"API error\"," +
                "\"error\":{\"code\":\"API_ERROR\",\"details\":\"%s\"}}",
                escapeJson(e.getMessage())
            ));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"Recurring donation setup failed\"," +
                "\"error\":{\"code\":\"SERVER_ERROR\",\"details\":\"%s\"}}",
                escapeJson(e.getMessage())
            ));
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
