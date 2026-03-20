<?php

declare(strict_types=1);

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

ini_set('display_errors', '0');

$inputData = json_decode(file_get_contents('php://input'), true);

if (empty($inputData['payment_type'])) {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Missing payment_type',
        'error' => 'payment_type must be "one-time" or "recurring"'
    ]);
    exit;
}

$paymentType = $inputData['payment_type'];

if ($paymentType === 'one-time') {
    require __DIR__ . '/process-one-time.php';
} elseif ($paymentType === 'recurring') {
    require __DIR__ . '/process-recurring.php';
} else {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Invalid payment_type',
        'error' => 'payment_type must be "one-time" or "recurring"'
    ]);
}
