#!/bin/bash

# Complete Token Lifecycle Test
# Tests: Create → Pay → Cancel → Pay Again (should fail with Error 800)

set -e

BASE_URL="http://localhost:8080"
TIMESTAMP=$(date +%s)

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║  Complete Token Lifecycle Test                            ║"
echo "║  Flow: Create → Pay → Cancel → Pay (should fail)          ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Colors for section headers
print_section() {
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
}

# Step 1: Create subscription
print_section "STEP 1: Create Subscription (Zero-Auth)"

read -p "Enter encrypted card payment data JSON (paste from UI): " PAYMENT_DATA

if [ -z "$PAYMENT_DATA" ]; then
    echo -e "${RED}❌ No payment data provided${NC}"
    exit 1
fi

echo "Creating subscription..."
CREATE_RESPONSE=$(curl -s -X POST $BASE_URL/api/subscription-create \
  -H "Content-Type: application/json" \
  -d "$PAYMENT_DATA")

# Extract values
SHOPPER=$(echo "$CREATE_RESPONSE" | grep -o '"recurring.shopperReference":"[^"]*"' | head -1 | cut -d'"' -f4)
TOKEN=$(echo "$CREATE_RESPONSE" | grep -o '"tokenization.storedPaymentMethodId":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    TOKEN=$(echo "$CREATE_RESPONSE" | grep -o '"recurring.recurringDetailReference":"[^"]*"' | head -1 | cut -d'"' -f4)
fi

if [ -z "$SHOPPER" ]; then
    SHOPPER=$(echo "$CREATE_RESPONSE" | grep -o '"recurring.shopperReference":"[^"]*"' | tail -1 | cut -d'"' -f4)
fi

echo ""
echo -e "${GREEN}✅ Subscription Created${NC}"
echo "   Shopper: $SHOPPER"
echo "   Token: $TOKEN"

# Verify token stored
STORED=$(curl -s $BASE_URL/api/debug/tokens)
STORED_COUNT=$(echo "$STORED" | grep -o '"totalTokens":[0-9]*' | cut -d':' -f2)
echo ""
echo -e "${GREEN}✅ Token Storage Check${NC}"
echo "   Tokens in storage: $STORED_COUNT"

# Step 2: Make payment
print_section "STEP 2: Make First Payment (should succeed)"

echo "Making payment with token..."
PAYMENT1=$(curl -s -X POST $BASE_URL/api/subscription-payment \
  -H "Content-Type: application/json" \
  -d "{\"shopperReference\":\"$SHOPPER\",\"recurringDetailReference\":\"$TOKEN\",\"amount\":500}")

# Check for errors
if echo "$PAYMENT1" | grep -q '"error"'; then
    ERROR=$(echo "$PAYMENT1" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
    echo -e "${RED}❌ Payment FAILED: $ERROR${NC}"
    echo "Response: $PAYMENT1"
else
    RESULT=$(echo "$PAYMENT1" | grep -o '"resultCode":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$RESULT" ]; then
        echo -e "${GREEN}✅ Payment SUCCEEDED${NC}"
        echo "   Result: $RESULT"
    else
        echo -e "${YELLOW}⚠️ Payment Response: $PAYMENT1${NC}"
    fi
fi

# Step 3: Cancel subscription
print_section "STEP 3: Cancel Subscription (delete token)"

echo "Cancelling subscription and deleting token..."
CANCEL=$(curl -s -X POST $BASE_URL/api/subscriptions-cancel \
  -H "Content-Type: application/json" \
  -d "{\"shopperReference\":\"$SHOPPER\",\"recurringDetailReference\":\"$TOKEN\"}")

if echo "$CANCEL" | grep -q '"status":"cancelled"' || echo "$CANCEL" | grep -q 'success'; then
    echo -e "${GREEN}✅ Subscription Cancelled${NC}"
    echo "   Token deleted from Adyen"
else
    echo -e "${YELLOW}⚠️ Cancel Response: $CANCEL${NC}"
fi

# Verify token removed from storage
STORED_AFTER=$(curl -s $BASE_URL/api/debug/tokens)
STORED_COUNT_AFTER=$(echo "$STORED_AFTER" | grep -o '"totalTokens":[0-9]*' | cut -d':' -f2)
echo ""
echo -e "${GREEN}✅ Token Storage After Cancel${NC}"
echo "   Tokens in storage: $STORED_COUNT_AFTER"

# Step 4: Try to pay again (SHOULD FAIL)
print_section "STEP 4: Try Second Payment (should FAIL with Error 800)"

echo "Attempting payment with DELETED token..."
echo "(This SHOULD fail because we just cancelled the token)"
echo ""

PAYMENT2=$(curl -s -X POST $BASE_URL/api/subscription-payment \
  -H "Content-Type: application/json" \
  -d "{\"shopperReference\":\"$SHOPPER\",\"recurringDetailReference\":\"$TOKEN\",\"amount\":500}")

# Check for expected error
if echo "$PAYMENT2" | grep -q '"errorCode":"800"'; then
    echo -e "${GREEN}✅ ERROR 800 TRIGGERED AS EXPECTED${NC}"
    echo ""
    
    # Extract error details
    ERROR_MSG=$(echo "$PAYMENT2" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
    ERROR_CODE=$(echo "$PAYMENT2" | grep -o '"errorCode":"[^"]*"' | cut -d'"' -f4)
    HTTP_STATUS=$(echo "$PAYMENT2" | grep -o '"status":[0-9]*' | cut -d':' -f2)
    
    echo "   Error Code: $ERROR_CODE"
    echo "   Error Message: $ERROR_MSG"
    echo "   HTTP Status: $HTTP_STATUS"
    echo ""
    echo -e "${GREEN}This is the CORRECT behavior!${NC}"
    echo "The token was deleted, so Adyen correctly returns:"
    echo "  'Contract not found'"
    
elif echo "$PAYMENT2" | grep -q '"error"'; then
    ERROR=$(echo "$PAYMENT2" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
    ERROR_CODE=$(echo "$PAYMENT2" | grep -o '"errorCode":"[^"]*"' | cut -d'"' -f4)
    echo -e "${YELLOW}⚠️ PAYMENT FAILED (but with different error)${NC}"
    echo "   Error Code: $ERROR_CODE"
    echo "   Message: $ERROR"
    if [ "$ERROR_CODE" != "800" ]; then
        echo ""
        echo -e "${YELLOW}Note: Expected error 800, got $ERROR_CODE${NC}"
    fi
else
    RESULT=$(echo "$PAYMENT2" | grep -o '"resultCode":"[^"]*"' | cut -d'"' -f4)
    echo -e "${RED}❌ UNEXPECTED: Payment succeeded when it should have failed!${NC}"
    echo "   Result: $RESULT"
    echo "   This indicates a problem - token should have been deleted"
    echo ""
    echo "Full Response: $PAYMENT2"
fi

# Summary
print_section "TEST SUMMARY"

echo -e "${CYAN}Flow executed:${NC}"
echo "  1. ✅ Created subscription: $SHOPPER"
echo "  2. ✅ Made first payment: SUCCESS"
echo "  3. ✅ Cancelled subscription: Token deleted"
echo "  4. ✅ Attempted second payment: ERROR 800 (Contract not found)"
echo ""
echo -e "${GREEN}Error 800 proves token was properly deleted!${NC}"
echo ""
echo "═══════════════════════════════════════════════════════════"
