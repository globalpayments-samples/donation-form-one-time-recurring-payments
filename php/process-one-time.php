<?php

declare(strict_types=1);

require_once __DIR__ . '/vendor/autoload.php';

use Dotenv\Dotenv;
use GlobalPayments\Api\ServiceConfigs\Gateways\GpApiConfig;
use GlobalPayments\Api\ServicesContainer;
use GlobalPayments\Api\PaymentMethods\CreditCardData;
use GlobalPayments\Api\Entities\Enums\Environment;
use GlobalPayments\Api\Entities\Enums\Channel;
use GlobalPayments\Api\Entities\Exceptions\GatewayException;
use GlobalPayments\Api\Entities\Exceptions\ApiException;

function configureSdk(): void
{
    $dotenv = Dotenv::createImmutable(__DIR__);
    $dotenv->load();

    $config = new GpApiConfig();
    $config->appId = $_ENV['GP_APP_ID'];
    $config->appKey = $_ENV['GP_APP_KEY'];
    $config->environment = ($_ENV['GP_APP_ENVIRONMENT'] ?? 'sandbox') === 'production'
        ? Environment::PRODUCTION
        : Environment::TEST;
    $config->channel = Channel::CardNotPresent;
    $config->country = 'US';

    ServicesContainer::configureService($config);
}

configureSdk();

// DEBUG: Log credential verification (REMOVE IN PRODUCTION)
error_log('=== GP API Configuration Debug ===');
error_log('APP_ID loaded: ' . (isset($_ENV['GP_APP_ID']) ? 'YES' : 'NO'));
error_log('APP_KEY loaded: ' . (isset($_ENV['GP_APP_KEY']) ? 'YES' : 'NO'));
error_log('APP_KEY length: ' . (isset($_ENV['GP_APP_KEY']) ? strlen($_ENV['GP_APP_KEY']) : 0));
error_log('Environment: ' . ($_ENV['GP_APP_ENVIRONMENT'] ?? 'NOT_SET'));
error_log('==================================');

try {
    if (empty($inputData['payment_reference'])) {
        throw new Exception('Missing payment reference');
    }

    if (empty($inputData['amount']) || floatval($inputData['amount']) <= 0) {
        throw new Exception('Invalid amount');
    }

    if (empty($inputData['donor_name'])) {
        throw new Exception('Missing donor name');
    }

    if (empty($inputData['donor_email'])) {
        throw new Exception('Missing donor email');
    }

    $paymentReference = $inputData['payment_reference'];
    $amount = floatval($inputData['amount']);
    $currency = $inputData['currency'] ?? 'USD';
    $donorName = $inputData['donor_name'];
    $donorEmail = $inputData['donor_email'];

    $card = new CreditCardData();
    $card->token = $paymentReference;

    $response = $card->charge($amount)
        ->withCurrency($currency)
        ->execute();

    if ($response->responseCode === '00' || $response->responseCode === 'SUCCESS') {
        echo json_encode([
            'success' => true,
            'message' => 'Thank you for your donation!',
            'data' => [
                'transactionId' => $response->transactionId,
                'status' => $response->responseMessage,
                'amount' => $amount,
                'currency' => $currency,
                'donorName' => $donorName,
                'donorEmail' => $donorEmail,
                'timestamp' => date('Y-m-d H:i:s')
            ]
        ]);
    } else {
        throw new Exception('Transaction declined: ' . ($response->responseMessage ?? 'Unknown error'));
    }

} catch (GatewayException $e) {
    error_log('=== Gateway Error ===');
    error_log('Error Message: ' . $e->getMessage());
    error_log('Error Code: ' . $e->getCode());
    error_log('Stack Trace: ' . $e->getTraceAsString());
    error_log('====================');

    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Donation processing failed',
        'error' => [
            'code' => 'GATEWAY_ERROR',
            'details' => $e->getMessage(),
            'timestamp' => date('Y-m-d H:i:s')
        ]
    ]);
} catch (ApiException $e) {
    error_log('=== API Error ===');
    error_log('Error Message: ' . $e->getMessage());
    error_log('Error Code: ' . $e->getCode());
    error_log('Stack Trace: ' . $e->getTraceAsString());
    error_log('=================');

    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'API error',
        'error' => [
            'code' => 'API_ERROR',
            'details' => $e->getMessage(),
            'timestamp' => date('Y-m-d H:i:s')
        ]
    ]);
} catch (Exception $e) {
    error_log('=== General Server Error ===');
    error_log('Error Type: ' . get_class($e));
    error_log('Error Message: ' . $e->getMessage());
    error_log('Error Code: ' . $e->getCode());
    error_log('File: ' . $e->getFile() . ':' . $e->getLine());
    error_log('Stack Trace: ' . $e->getTraceAsString());
    error_log('==========================');

    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'Donation processing failed',
        'error' => [
            'code' => 'SERVER_ERROR',
            'details' => $e->getMessage(),
            'timestamp' => date('Y-m-d H:i:s')
        ]
    ]);
}
