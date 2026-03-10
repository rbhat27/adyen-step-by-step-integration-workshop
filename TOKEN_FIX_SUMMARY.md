# Subscription Payment Token Issue - FIXED

## Problem Identified
The fake token format `token-1773155163877` was being generated and stored instead of real Adyen tokens.

### Root Causes
1. **Client-side fake token generation** (PRIMARY ISSUE)
   - File: `src/main/resources/static/subscription.js` line 179
   - Code was generating: `token: token-${Date.now()}`
   - This fake token was then being stored in browser localStorage
   - Later attempts to use this fake token resulted in "Contract not found" errors from Adyen

2. **Webhook handler incompleteness** (SECONDARY ISSUE)
   - File: `src/main/java/com/adyen/workshop/controllers/WebhookController.java`
   - Only looking for `recurring.recurringDetailReference` field
   - Not checking for `tokenization.storedPaymentMethodId` (which Adyen might return in newer API versions)

3. **UI display breaking** (TERTIARY ISSUE)
   - `displaySubscriptions()` function was trying to display `.token.substring()` even when token was undefined

## Fixes Applied

### 1. Removed Fake Token Generation
✅ Updated `handleSubscriptionCompleted()` to:
- NOT generate fake `token-${Date.now()}` tokens
- Store subscription metadata with status="AWAITING_WEBHOOK"
- Set `tokenReceived: false` flag
- Show user message to wait for webhook

### 2. Enhanced Webhook Handler
✅ Updated webhook handler to check for:
- `recurring.recurringDetailReference` (standard field)
- `tokenization.storedPaymentMethodId` (alternative field)
- `PSP reference` (fallback)

And for shopper reference:
- `recurring.shopperReference`
- `tokenization.shopperReference`
- `shopperReference` (fallback)

### 3. Fixed UI Display
✅ Updated `displaySubscriptions()` to:
- Safely handle undefined tokens
- Show "⏳ Awaiting webhook..." when token is missing
- Show "✅ Ready" or "⏳ Pending" status properly

## Correct Subscription Flow Now

1. **Create Subscription (Zero-Auth)**
   - User enters card in Drop-in
   - Frontend sends encrypted card data to `/api/subscription-create`
   - Backend makes zero-auth payment to Adyen
   - Adyen responds with payment result
   - Frontend shows "⏳ Subscription created. Waiting for webhook..."

2. **Wait for RECURRING_CONTRACT Webhook**
   - Adyen sends `RECURRING_CONTRACT` webhook with actual recurring detail reference
   - Backend webhook handler stores real token in memory (or database in production)
   - Token has format like: `8415718415172204` (NOT `token-...`)

3. **Make Recurring Payment**
   - User manually enters shopper reference and token from webhook logs
   - OR token is auto-loaded from server storage
   - Frontend sends to `/api/subscription-payment`
   - Backend uses token in request to Adyen
   - Payment is processed successfully

4. **Charge Subscription Workflow** 
   - RECURRING_CONTRACT webhook arrives from Adyen
   - Webhook handler extracts real token `recurring.recurringDetailReference`
   - Token stored in WebhookController.tokenStorage
   - User can use token to charge subscription via `/api/subscription-payment`

## How to Test

### Manual Test (Recommended for now)
1. Start the application: `./gradlew bootRun`
2. Go to http://localhost:8080/subscription
3. Create a subscription with Drop-in form
4. Watch the console for RECURRING_CONTRACT webhook
5. Look for line: `✅ TOKEN STORED: [real-token-value] for shopper: [shopper-ref]`
6. Copy the real token value
7. In "Make a Recurring Payment" section, paste the token
8. Click "Charge Subscription"

### Automated Test (with proper HMAC)
See `test_webhook_proper.sh` for testing with valid HMAC signatures

## Expected Token Format

**WRONG (Fake):**
```
token-1773155163877
token-1234567890123
```

**CORRECT (Real Adyen):**
```
8415718415172204
1234567812345678
```

Real tokens are 16-digit numbers (or other formats) from Adyen's systems.

## Verification

Run application and check:
- ✅ No more fake tokens like `token-1773155163877`
- ✅ Real tokens stored from webhook with format like `8415718415172204`
- ✅ Webhook logs show proper field names being processed
- ✅ Subscription payments work when using real webhook tokens
- ✅ "Contract not found" errors should be gone (when using real tokens)

## Files Modified

1. `src/main/resources/static/subscription.js`
   - Removed fake token generation in `handleSubscriptionCompleted()`
   - Fixed `displaySubscriptions()` for undefined tokens
   - Updated UI messages

2. `src/main/java/com/adyen/workshop/controllers/WebhookController.java`
   - Enhanced token extraction with fallback field names
   - Improved logging for debugging

## Next Steps for Production

1. **Database Storage**: Replace in-memory `tokenStorage` Map with persistent database
2. **Webhook Polling**: Implement client-side polling to refresh stored tokens
3. **Better UX**: Auto-load tokens via `/api/debug/stored-tokens` to user interface
4. **Token Encryption**: Encrypt stored tokens at rest
5. **Token Expiration**: Handle token expiration and renewal
6. **Shopper Account**: Link tokens to user accounts/sessions

## Debugging Commands

Check stored tokens:
```bash
curl -X POST http://localhost:8080/api/debug/stored-tokens \
  -H "Content-Type: application/json"
```

View subscription logs:
```bash
tail -f /tmp/server.log | grep -E "RECURRING_CONTRACT|TOKEN|===|✅|❌"
```
