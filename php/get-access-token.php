<?php

declare(strict_types=1);

require_once 'vendor/autoload.php';

use Dotenv\Dotenv;

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

try {
    $dotenv = Dotenv::createImmutable(__DIR__);
    $dotenv->load();

    if (empty($_ENV['GP_APP_ID']) || empty($_ENV['GP_APP_KEY'])) {
        throw new Exception('Missing required credentials: GP_APP_ID and GP_APP_KEY');
    }

    $nonce = bin2hex(random_bytes(16));

    $requestData = [
        'app_id' => $_ENV['GP_APP_ID'],
        'nonce' => $nonce,
        'secret' => hash('sha512', $nonce . $_ENV['GP_APP_KEY']),
        'grant_type' => 'client_credentials',
        'seconds_to_expire' => 600,
        'permissions' => ['PMT_POST_Create_Single']
    ];

    $apiEndpoint = ($_ENV['GP_ENVIRONMENT'] ?? 'sandbox') === 'production'
        ? 'https://apis.globalpay.com/ucp/accesstoken'
        : 'https://apis.sandbox.globalpay.com/ucp/accesstoken';

    $ch = curl_init($apiEndpoint);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => json_encode($requestData),
        CURLOPT_HTTPHEADER => [
            'Content-Type: application/json',
            'X-GP-Version: 2021-03-22'
        ],
        CURLOPT_ENCODING => '',
        CURLOPT_TIMEOUT => 30
    ]);

    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $curlError = curl_error($ch);
    curl_close($ch);

    if ($curlError) {
        throw new Exception('Connection error: ' . $curlError);
    }

    $responseData = json_decode($response, true);

    if ($httpCode !== 200 || empty($responseData['token'])) {
        $errorMessage = $responseData['error_description'] ?? $responseData['message'] ?? 'Failed to generate access token';
        throw new Exception($errorMessage);
    }

    echo json_encode([
        'success' => true,
        'token' => $responseData['token'],
        'expiresIn' => $responseData['seconds_to_expire'] ?? 600
    ]);

} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'Error generating access token',
        'error' => $e->getMessage()
    ]);
}
