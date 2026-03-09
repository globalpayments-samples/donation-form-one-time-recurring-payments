import express from 'express';
import * as dotenv from 'dotenv';
import crypto from 'crypto';
import {
    ServicesContainer,
    GpApiConfig,
    CreditCardData,
    StoredCredential,
    Environment,
    Channel,
    StoredCredentialInitiator,
    StoredCredentialType,
    StoredCredentialSequence
} from 'globalpayments-api';

dotenv.config();

const app = express();
const port = process.env.PORT || 8000;

app.use(express.static('.'));
app.use(express.json());

let sdkConfigured = false;

function ensureSdkConfigured() {
    if (sdkConfigured) return;
    const config = new GpApiConfig();
    config.appId = process.env.GP_APP_ID;
    config.appKey = process.env.GP_APP_KEY;
    config.environment = (process.env.GP_APP_ENVIRONMENT || 'sandbox') === 'production'
        ? Environment.Production
        : Environment.Test;
    config.channel = Channel.CardNotPresent;
    config.country = 'US';
    ServicesContainer.configureService(config);
    sdkConfigured = true;
}

app.post('/get-access-token', async (req, res) => {
    try {
        const appId = process.env.GP_APP_ID;
        const appKey = process.env.GP_APP_KEY;

        if (!appId || !appKey) {
            throw new Error('Missing required credentials: GP_APP_ID and GP_APP_KEY');
        }

        const nonce = crypto.randomBytes(16).toString('hex');
        const secret = crypto.createHash('sha512').update(nonce + appKey).digest('hex');

        const apiEndpoint = (process.env.GP_APP_ENVIRONMENT || 'sandbox') === 'production'
            ? 'https://apis.globalpay.com/ucp/accesstoken'
            : 'https://apis.sandbox.globalpay.com/ucp/accesstoken';

        const response = await fetch(apiEndpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-GP-Version': '2021-03-22'
            },
            body: JSON.stringify({
                app_id: appId,
                nonce,
                secret,
                grant_type: 'client_credentials',
                seconds_to_expire: 600,
                permissions: ['PMT_POST_Create_Single']
            })
        });

        const data = await response.json();

        if (!response.ok || !data.token) {
            const errorMessage = data.error_description || data.message || 'Failed to generate access token';
            throw new Error(errorMessage);
        }

        res.json({
            success: true,
            token: data.token,
            expiresIn: data.seconds_to_expire || 600
        });

    } catch (error) {
        res.status(500).json({
            success: false,
            message: 'Error generating access token',
            error: error.message
        });
    }
});

app.post('/process-donation', async (req, res) => {
    const { payment_type } = req.body;

    if (!payment_type) {
        return res.status(400).json({
            success: false,
            message: 'Missing payment_type',
            error: 'payment_type must be "one-time" or "recurring"'
        });
    }

    if (payment_type === 'one-time') {
        return processOneTime(req, res);
    } else if (payment_type === 'recurring') {
        return processRecurring(req, res);
    } else {
        return res.status(400).json({
            success: false,
            message: 'Invalid payment_type',
            error: 'payment_type must be "one-time" or "recurring"'
        });
    }
});

async function processOneTime(req, res) {
    try {
        ensureSdkConfigured();

        const { payment_reference, amount, donor_name, donor_email } = req.body;
        const currency = req.body.currency || 'USD';

        if (!payment_reference) throw new Error('Missing payment reference');
        if (!amount || parseFloat(amount) <= 0) throw new Error('Invalid amount');
        if (!donor_name) throw new Error('Missing donor name');
        if (!donor_email) throw new Error('Missing donor email');

        const card = new CreditCardData();
        card.token = payment_reference;

        const response = await card.charge(parseFloat(amount))
            .withCurrency(currency)
            .execute();

        if (response.responseCode === '00' || response.responseCode === 'SUCCESS') {
            return res.json({
                success: true,
                message: 'Thank you for your donation!',
                data: {
                    transactionId: response.transactionId,
                    status: response.responseMessage,
                    amount: parseFloat(amount),
                    currency,
                    donorName: donor_name,
                    donorEmail: donor_email,
                    timestamp: new Date().toISOString().replace('T', ' ').substring(0, 19)
                }
            });
        } else {
            throw new Error('Transaction declined: ' + (response.responseMessage || 'Unknown error'));
        }

    } catch (error) {
        const name = error.constructor.name;
        if (name === 'GatewayException') {
            return res.status(400).json({
                success: false,
                message: 'Donation processing failed',
                error: { code: 'GATEWAY_ERROR', details: error.message, timestamp: new Date().toISOString() }
            });
        } else if (name === 'ApiException') {
            return res.status(400).json({
                success: false,
                message: 'API error',
                error: { code: 'API_ERROR', details: error.message, timestamp: new Date().toISOString() }
            });
        } else {
            return res.status(500).json({
                success: false,
                message: 'Donation processing failed',
                error: { code: 'SERVER_ERROR', details: error.message, timestamp: new Date().toISOString() }
            });
        }
    }
}

async function processRecurring(req, res) {
    try {
        ensureSdkConfigured();

        const { payment_reference, amount, donor_name, donor_email, frequency } = req.body;
        const currency = req.body.currency || 'USD';
        const startDate = req.body.start_date || new Date().toISOString().split('T')[0];
        const durationType = req.body.duration_type || 'ongoing';
        const endDate = req.body.end_date || null;
        const numPayments = req.body.num_payments ? parseInt(req.body.num_payments, 10) : null;

        if (!payment_reference) throw new Error('Missing payment reference');
        if (!amount || parseFloat(amount) <= 0) throw new Error('Invalid amount');
        if (!donor_name) throw new Error('Missing donor name');
        if (!donor_email) throw new Error('Missing donor email');
        if (!frequency) throw new Error('Missing frequency for recurring donation');

        const card = new CreditCardData();
        card.token = payment_reference;

        const storedCredential = new StoredCredential();
        storedCredential.initiator = StoredCredentialInitiator.Payer;
        storedCredential.type = StoredCredentialType.RECURRING;
        storedCredential.sequence = StoredCredentialSequence.FIRST;

        const response = await card.charge(parseFloat(amount))
            .withCurrency(currency)
            .withStoredCredentials(storedCredential)
            .execute();

        if (response.responseCode === '00' || response.responseCode === 'SUCCESS') {
            const data = {
                transactionId: response.transactionId,
                cardBrandTransactionId: response.cardBrandTransactionId || null,
                status: response.responseMessage,
                amount: parseFloat(amount),
                currency,
                donorName: donor_name,
                donorEmail: donor_email,
                frequency,
                startDate,
                durationType,
                timestamp: new Date().toISOString().replace('T', ' ').substring(0, 19)
            };

            if (durationType === 'end_date' && endDate) data.endDate = endDate;
            if (durationType === 'num_payments' && numPayments) data.numPayments = numPayments;

            return res.json({
                success: true,
                message: 'Recurring donation set up successfully!',
                data
            });
        } else {
            throw new Error('Transaction declined: ' + (response.responseMessage || 'Unknown error'));
        }

    } catch (error) {
        const name = error.constructor.name;
        if (name === 'GatewayException') {
            return res.status(400).json({
                success: false,
                message: 'Recurring donation setup failed',
                error: { code: 'GATEWAY_ERROR', details: error.message, timestamp: new Date().toISOString() }
            });
        } else if (name === 'ApiException') {
            return res.status(400).json({
                success: false,
                message: 'API error',
                error: { code: 'API_ERROR', details: error.message, timestamp: new Date().toISOString() }
            });
        } else {
            return res.status(500).json({
                success: false,
                message: 'Recurring donation setup failed',
                error: { code: 'SERVER_ERROR', details: error.message, timestamp: new Date().toISOString() }
            });
        }
    }
}

app.listen(port, '0.0.0.0', () => {
    console.log(`Server running at http://localhost:${port}`);
});
