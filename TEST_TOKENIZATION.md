# Tokenization Test Cases - Complete Validation

This document provides step-by-step instructions to verify all tokenization test cases pass.

## Prerequisites

- Application is running: `./gradlew bootRun`
- Check stored tokens endpoint working: `http://localhost:8080/api/debug/tokens`
- Use valid test card (e.g., Amex)

---

## Test Case 1: subscription-create → subscription-payment

**Goal**: Create a token, then immediately use it for payment.

### Steps:

1. **Create subscription (zero-auth)**
   ```bash
   curl -X POST http://localhost:8080/api/subscription-create \
     -H "Content-Type: application/json" \
     -d '{"paymentMethod":{"type":"scheme","brand":"amex","encryptedCardNumber":"...","encryptedExpiryMonth":"...","encryptedExpiryYear":"...","encryptedSecurityCode":"...","holderName":"J. Smith","checkoutAttemptId":"..."},"browserInfo":{"screenWidth":1440,"screenHeight":900,"colorDepth":24,"userAgent":"Mozilla/5.0","javaEnabled":false,"language":"en-US","acceptHeader":"*/*","timeZoneOffset":240}}'
   ```

2. **Watch console for**:
   - ✅ `🔑 SHOPPER REFERENCE SET: shopper-XXXXXXXXX`
   - ✅ `✅ TOKEN STORED FROM API RESPONSE`
   - ✅ Shopper and Token values printed

3. **Copy the shopper reference and token from console**
   - Example: `shopper-1773160359771` + `FDPVSS5GCHTDMNT5`

4. **Check tokens are stored**
   ```bash
   curl http://localhost:8080/api/debug/tokens
   ```
   Should return tokens in the response.

5. **Use token for payment with MATCHING shopper reference**
   ```bash
   curl -X POST http://localhost:8080/api/subscription-payment \
     -H "Content-Type: application/json" \
     -d '{
       "shopperReference": "shopper-1773160359771",
       "recurringDetailReference": "FDPVSS5GCHTDMNT5",
       "amount": 500
     }'
   ```

6. **Verify success**:
   - Console shows: `=== ADYEN CHECKOUT API RESPONSE ===`
   - Status Code: `200`
   - ✅ Payment successful

**Status**: [ ] PASS / [ ] FAIL

---

## Test Case 2: subscription-create → subscription-cancel

**Goal**: Create a token, then delete it.

### Steps:

1. **Create subscription** (same as Test Case 1, steps 1-3)

2. **Cancel with SAME shopper reference**
   ```bash
   curl -X POST http://localhost:8080/api/subscriptions-cancel \
     -H "Content-Type: application/json" \
     -d '{
       "shopperReference": "shopper-1773160359771",
       "recurringDetailReference": "FDPVSS5GCHTDMNT5"
     }'
   ```

3. **Verify success**:
   - Console shows: `✅ CANCEL SUBSCRIPTION RESPONSE`
   - Status Code: `200`
   - Response should show success

4. **Verify token removed**:
   ```bash
   curl http://localhost:8080/api/debug/tokens
   ```
   Should show `totalTokens: 0` or token no longer listed

**Status**: [ ] PASS / [ ] FAIL

---

## Test Case 3: subscription-create → subscription-payment → subscription-cancel

**Goal**: Full lifecycle - create, pay, cancel.

### Steps:

1. **Create subscription** (steps 1-3 from Test Case 1)

2. **Make payment** (step 5 from Test Case 1 with MATCHING shopper reference)

3. **Cancel subscription** (step 2-3 from Test Case 2 with MATCHING shopper reference)

4. **Verify all steps succeeded**

**Status**: [ ] PASS / [ ] FAIL

---

## Test Case 4: subscription-create → subscription-payment → subscription-cancel → subscription-payment

**Goal**: Create, pay, cancel, then try to pay again (should fail).

### Steps:

1. **Create subscription** (Test Case 1, steps 1-3)

2. **Make first payment** (Test Case 1, step 5)

3. **Cancel subscription** (Test Case 2, step 2)

4. **Try second payment** (should fail - token deleted)
   ```bash
   curl -X POST http://localhost:8080/api/subscription-payment \
     -H "Content-Type: application/json" \
     -d '{
       "shopperReference": "shopper-1773160359771",
       "recurringDetailReference": "FDPVSS5GCHTDMNT5",
       "amount": 500
     }'
   ```

5. **Verify failure**:
   - Console shows: `❌ ADYEN API ERROR RECEIVED`
   - Error Code: `800` (Contract not found)
   - Status Code: `422`
   - ✅ **This is expected - token was deleted**

**Status**: [ ] PASS / [ ] FAIL

---

## Webhook Tests

### Webhook 1: AUTHORISATION webhook

**Goal**: Application handles AUTHORISATION webhook and extracts token.

### Verify:

1. When you create a subscription, watch console for:
   ```
   ╔════════════════════════════════════════════╗
   ║     ✅ AUTHORISATION WEBHOOK RECEIVED     ║
   ╚════════════════════════════════════════════╝
   Event Code: AUTHORISATION
   ...
   🎯 TOKEN INFORMATION FOUND IN AUTH:
      Stored Payment Method ID: FDPVSS5GCHTDMNT5
      Shopper Reference: shopper-1773160359771
   ```

2. Token should be stored from webhook

**Status**: [ ] PASS / [ ] FAIL

---

### Webhook 2: RECURRING_CONTRACT webhook

**Goal**: Application handles RECURRING_CONTRACT webhook (optional confirmatory webhook).

### Verify:

1. When you create a subscription, watch console around 30 seconds later for:
   ```
   ╔════════════════════════════════════════════╗
   ║  ✅ RECURRING_CONTRACT WEBHOOK RECEIVED   ║
   ╚════════════════════════════════════════════╝
   Event Code: RECURRING_CONTRACT
   ...
   🎯 TOKEN SUCCESSFULLY STORED:
      Token/Recurring Detail Ref: FDPVSS5GCHTDMNT5
      Shopper Reference: shopper-1773160359771
   ```

2. Verify token is properly linked to shopper

**Status**: [ ] PASS / [ ] FAIL

---

## Summary Checklist

After testing all cases, mark them:

✅ **Flow Tests**:
- [ ] Test Case 1: subscription-create → subscription-payment ✅ PASS
- [ ] Test Case 2: subscription-create → subscription-cancel ✅ PASS
- [ ] Test Case 3: 3-step flow ✅ PASS
- [ ] Test Case 4: 4-step full cycle ✅ PASS

✅ **Webhook Tests**:
- [ ] AUTHORISATION webhook handling ✅ PASS
- [ ] RECURRING_CONTRACT webhook handling ✅ PASS

---

## Critical Success Criteria

For a test to **PASS**, verify:

1. ✅ HTTP status codes are 2xx (success) or correctly fail with 422/400
2. ✅ Console output shows expected messages with checkmarks ✅
3. ✅ API responses contain correct data
4. ✅ Tokens are properly stored and retrieved
5. ✅ Shopper references match across operations
6. ✅ Webhooks are received and logged properly

---

## Troubleshooting

### Problem: "Contract not found" (Error 800)

**Solution**: Ensure shopper reference matches exactly between operations.

```
❌ WRONG:
- Created with: shopper-1773160359771
- Used payment with: shopper-1773160359653

✅ CORRECT:
- Created with: shopper-1773160359771
- Used payment with: shopper-1773160359771
```

### Problem: Token not found

**Solutions**:
1. Check `/api/debug/tokens` endpoint
2. Verify AUTHORISATION or RECURRING_CONTRACT webhook was received
3. Ensure token extraction worked in webhook handler

### Problem: Cancel fails with 404

**Solution**: Check Adyen endpoint URL - must be:
```
https://pal-test.adyen.com/pal/servlet/Recurring/v1/disable
```

---

## Notes

- Each test should use a **fresh subscription** (create new one for each test)
- **Shopper references MUST match exactly** across all operations
- **Tokens** are case-sensitive
- **Webhooks** may take 5-30 seconds to arrive
- Test with **valid Adyen test cards** only

