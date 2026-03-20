using GlobalPayments.Api;
using GlobalPayments.Api.Entities;
using GlobalPayments.Api.PaymentMethods;
using dotenv.net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

// Alias to avoid conflict with System.Environment (which is in implicit usings)
using GpEnvironment = GlobalPayments.Api.Entities.Environment;

namespace DonationFormSample;

public class Program
{
    public static void Main(string[] args)
    {
        DotEnv.Load();

        var builder = WebApplication.CreateBuilder(args);
        var app = builder.Build();

        app.UseDefaultFiles();
        app.UseStaticFiles();

        ConfigureGlobalPaymentsSDK();
        ConfigureEndpoints(app);

        var port = System.Environment.GetEnvironmentVariable("PORT") ?? "8000";
        app.Urls.Add($"http://0.0.0.0:{port}");

        app.Run();
    }

    private static void ConfigureGlobalPaymentsSDK()
    {
        var appId = System.Environment.GetEnvironmentVariable("GP_APP_ID");
        var appKey = System.Environment.GetEnvironmentVariable("GP_APP_KEY");
        var environment = System.Environment.GetEnvironmentVariable("GP_APP_ENVIRONMENT") ?? "sandbox";

        ServicesContainer.ConfigureService(new GpApiConfig
        {
            AppId = appId,
            AppKey = appKey,
            Environment = environment == "production" ? GpEnvironment.PRODUCTION : GpEnvironment.TEST,
            Channel = Channel.CardNotPresent,
            Country = "US"
        });
    }

    private static void ConfigureEndpoints(WebApplication app)
    {
        app.MapPost("/get-access-token", (Delegate)HandleGetAccessToken);
        app.MapPost("/process-donation", (Delegate)HandleProcessDonation);
    }

    private static async Task<IResult> HandleGetAccessToken(HttpContext context)
    {
        try
        {
            var appId = System.Environment.GetEnvironmentVariable("GP_APP_ID");
            var appKey = System.Environment.GetEnvironmentVariable("GP_APP_KEY");
            var envName = System.Environment.GetEnvironmentVariable("GP_APP_ENVIRONMENT") ?? "sandbox";

            if (string.IsNullOrEmpty(appId) || string.IsNullOrEmpty(appKey))
                throw new Exception("Missing required credentials: GP_APP_ID and GP_APP_KEY");

            var nonceBytes = RandomNumberGenerator.GetBytes(16);
            var nonce = Convert.ToHexString(nonceBytes).ToLowerInvariant();
            var secretBytes = SHA512.HashData(Encoding.UTF8.GetBytes(nonce + appKey));
            var secret = Convert.ToHexString(secretBytes).ToLowerInvariant();

            var apiEndpoint = envName == "production"
                ? "https://apis.globalpay.com/ucp/accesstoken"
                : "https://apis.sandbox.globalpay.com/ucp/accesstoken";

            var requestBody = JsonSerializer.Serialize(new
            {
                app_id = appId,
                nonce,
                secret,
                grant_type = "client_credentials",
                seconds_to_expire = 600,
                permissions = new[] { "PMT_POST_Create_Single" }
            });

            using var handler = new System.Net.Http.HttpClientHandler
            {
                AutomaticDecompression = System.Net.DecompressionMethods.GZip | System.Net.DecompressionMethods.Deflate
            };
            using var httpClient = new HttpClient(handler);
            using var request = new HttpRequestMessage(HttpMethod.Post, apiEndpoint);
            request.Content = new StringContent(requestBody, Encoding.UTF8, "application/json");
            request.Headers.Add("X-GP-Version", "2021-03-22");

            var response = await httpClient.SendAsync(request);
            var responseBody = await response.Content.ReadAsStringAsync();
            var responseData = JsonSerializer.Deserialize<JsonElement>(responseBody);

            if (!response.IsSuccessStatusCode || !responseData.TryGetProperty("token", out var tokenElement))
            {
                var errorMessage = responseData.TryGetProperty("error_description", out var errDesc) ? errDesc.GetString() :
                                   responseData.TryGetProperty("message", out var msg) ? msg.GetString() :
                                   "Failed to generate access token";
                throw new Exception(errorMessage);
            }

            return Results.Ok(new
            {
                success = true,
                token = tokenElement.GetString(),
                expiresIn = responseData.TryGetProperty("seconds_to_expire", out var exp) ? exp.GetInt32() : 600
            });
        }
        catch (Exception ex)
        {
            return Results.Json(new
            {
                success = false,
                message = "Error generating access token",
                error = ex.Message
            }, statusCode: 500);
        }
    }

    private static async Task<IResult> HandleProcessDonation(HttpContext context)
    {
        JsonElement body;
        try
        {
            body = await JsonSerializer.DeserializeAsync<JsonElement>(context.Request.Body);
        }
        catch
        {
            return Results.BadRequest(new { success = false, message = "Invalid JSON body" });
        }

        if (!body.TryGetProperty("payment_type", out var paymentTypeEl) || string.IsNullOrEmpty(paymentTypeEl.GetString()))
        {
            return Results.BadRequest(new
            {
                success = false,
                message = "Missing payment_type",
                error = "payment_type must be \"one-time\" or \"recurring\""
            });
        }

        var paymentType = paymentTypeEl.GetString();

        if (paymentType == "one-time")
            return ProcessOneTime(body);
        else if (paymentType == "recurring")
            return ProcessRecurring(body);
        else
            return Results.BadRequest(new
            {
                success = false,
                message = "Invalid payment_type",
                error = "payment_type must be \"one-time\" or \"recurring\""
            });
    }

    private static IResult ProcessOneTime(JsonElement body)
    {
        try
        {
            var paymentReference = GetString(body, "payment_reference");
            var donorName = GetString(body, "donor_name");
            var donorEmail = GetString(body, "donor_email");
            var currency = GetString(body, "currency") ?? "USD";
            var amount = GetDecimal(body, "amount");

            if (string.IsNullOrEmpty(paymentReference)) throw new Exception("Missing payment reference");
            if (amount <= 0) throw new Exception("Invalid amount");
            if (string.IsNullOrEmpty(donorName)) throw new Exception("Missing donor name");
            if (string.IsNullOrEmpty(donorEmail)) throw new Exception("Missing donor email");

            var card = new CreditCardData { Token = paymentReference };

            var response = card.Charge(amount)
                .WithCurrency(currency)
                .Execute();

            if (response.ResponseCode == "00" || response.ResponseCode == "SUCCESS")
            {
                return Results.Ok(new
                {
                    success = true,
                    message = "Thank you for your donation!",
                    data = new
                    {
                        transactionId = response.TransactionId,
                        status = response.ResponseMessage,
                        amount = (double)amount,
                        currency,
                        donorName,
                        donorEmail,
                        timestamp = DateTime.UtcNow.ToString("yyyy-MM-dd HH:mm:ss")
                    }
                });
            }
            else
            {
                throw new Exception("Transaction declined: " + (response.ResponseMessage ?? "Unknown error"));
            }
        }
        catch (GatewayException ex)
        {
            return Results.Json(new
            {
                success = false,
                message = "Donation processing failed",
                error = new { code = "GATEWAY_ERROR", details = ex.Message, timestamp = DateTime.UtcNow.ToString("o") }
            }, statusCode: 400);
        }
        catch (ApiException ex)
        {
            return Results.Json(new
            {
                success = false,
                message = "API error",
                error = new { code = "API_ERROR", details = ex.Message, timestamp = DateTime.UtcNow.ToString("o") }
            }, statusCode: 400);
        }
        catch (Exception ex)
        {
            return Results.Json(new
            {
                success = false,
                message = "Donation processing failed",
                error = new { code = "SERVER_ERROR", details = ex.Message, timestamp = DateTime.UtcNow.ToString("o") }
            }, statusCode: 500);
        }
    }

    private static IResult ProcessRecurring(JsonElement body)
    {
        try
        {
            var paymentReference = GetString(body, "payment_reference");
            var donorName = GetString(body, "donor_name");
            var donorEmail = GetString(body, "donor_email");
            var currency = GetString(body, "currency") ?? "USD";
            var frequency = GetString(body, "frequency");
            var startDate = GetString(body, "start_date") ?? DateTime.UtcNow.ToString("yyyy-MM-dd");
            var durationType = GetString(body, "duration_type") ?? "ongoing";
            var endDate = GetString(body, "end_date");
            int? numPayments = body.TryGetProperty("num_payments", out var np) && np.ValueKind == JsonValueKind.Number
                ? np.GetInt32()
                : (int?)null;
            var amount = GetDecimal(body, "amount");

            if (string.IsNullOrEmpty(paymentReference)) throw new Exception("Missing payment reference");
            if (amount <= 0) throw new Exception("Invalid amount");
            if (string.IsNullOrEmpty(donorName)) throw new Exception("Missing donor name");
            if (string.IsNullOrEmpty(donorEmail)) throw new Exception("Missing donor email");
            if (string.IsNullOrEmpty(frequency)) throw new Exception("Missing frequency for recurring donation");

            var card = new CreditCardData { Token = paymentReference };

            var storedCredential = new StoredCredential
            {
                Initiator = StoredCredentialInitiator.CardHolder,
                Type = StoredCredentialType.Recurring,
                Sequence = StoredCredentialSequence.First
            };

            var response = card.Charge(amount)
                .WithCurrency(currency)
                .WithStoredCredential(storedCredential)
                .Execute();

            if (response.ResponseCode == "00" || response.ResponseCode == "SUCCESS")
            {
                return Results.Ok(new
                {
                    success = true,
                    message = "Recurring donation set up successfully!",
                    data = new
                    {
                        transactionId = response.TransactionId,
                        cardBrandTransactionId = response.CardBrandTransactionId,
                        status = response.ResponseMessage,
                        amount = (double)amount,
                        currency,
                        donorName,
                        donorEmail,
                        frequency,
                        startDate,
                        durationType,
                        endDate = durationType == "end_date" ? endDate : null,
                        numPayments = durationType == "num_payments" ? numPayments : (int?)null,
                        timestamp = DateTime.UtcNow.ToString("yyyy-MM-dd HH:mm:ss")
                    }
                });
            }
            else
            {
                throw new Exception("Transaction declined: " + (response.ResponseMessage ?? "Unknown error"));
            }
        }
        catch (GatewayException ex)
        {
            return Results.Json(new
            {
                success = false,
                message = "Recurring donation setup failed",
                error = new { code = "GATEWAY_ERROR", details = ex.Message, timestamp = DateTime.UtcNow.ToString("o") }
            }, statusCode: 400);
        }
        catch (ApiException ex)
        {
            return Results.Json(new
            {
                success = false,
                message = "API error",
                error = new { code = "API_ERROR", details = ex.Message, timestamp = DateTime.UtcNow.ToString("o") }
            }, statusCode: 400);
        }
        catch (Exception ex)
        {
            return Results.Json(new
            {
                success = false,
                message = "Recurring donation setup failed",
                error = new { code = "SERVER_ERROR", details = ex.Message, timestamp = DateTime.UtcNow.ToString("o") }
            }, statusCode: 500);
        }
    }

    private static string? GetString(JsonElement body, string key)
    {
        return body.TryGetProperty(key, out var el) && el.ValueKind != JsonValueKind.Null
            ? el.GetString()
            : null;
    }

    private static decimal GetDecimal(JsonElement body, string key)
    {
        if (!body.TryGetProperty(key, out var el)) return 0;
        if (el.ValueKind == JsonValueKind.Number) return el.GetDecimal();
        if (el.ValueKind == JsonValueKind.String && decimal.TryParse(el.GetString(), out var parsed)) return parsed;
        return 0;
    }
}
