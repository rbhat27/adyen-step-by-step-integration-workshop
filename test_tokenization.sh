#!/bin/bash

# Tokenization Test Script
# This script tests all tokenization flows

set -e

BASE_URL="http://localhost:8080"
TIMESTAMP=$(date +%s)

# Color codes for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Tokenization Integration Test Suite"
echo "=========================================="
echo ""

# Test valid Amex card (you'll need to provide encrypted data from UI)
read -p "Enter encrypted card payment data JSON (or press Enter for demo): " PAYMENT_DATA

if [ -z "$PAYMENT_DATA" ]; then
    echo -e "${YELLOW}⚠️  Demo mode: No actual card data provided${NC}"
    echo "To run real tests, use actual encrypted card data from the UI"
    exit 1
fi

# Test 1: Create subscription
echo ""
echo -e "${YELLOW}Test 1: subscription-create${NC}"
RESPONSE=$(curl -s -X POST $BASE_URL/api/subscription-create \
  -H "Content-Type: application/json" \
  -d "$PAYMENT_DATA")

SHOPPER=$(echo "$RESPONSE" | grep -o '"recurring.shopperReference":"[^"]*"' | head -1 | cut -d'"' -f4)
TOKEN=$(echo "$RESPONSE" | grep -o '"tokenization.storedPaymentMethodId":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    TOKEN=$(echo "$RESPONSE" | grep -o '"recurring.recurringDetailReference":"[^"]*"' | head -1 | cut -d'"' -f4)
fi

if [ -z "$TOKEN" ]; then
    echo -e "${RED}❌ FAILED: Could not extract token${NC}"
    echo "Response: $RESPONSE"
    exit 1
fi

echo -e "${GREEN}✅ Token created${NC}"
echo "   Shopper: $SHOPPER"
echo "   Token: $TOKEN"

# Check if tokens stored
STORED=$(curl -s $BASE_URL/api/debug/tokens)
STORED_COUNT=$(echo "$STORED" | grep -o '"totalTokens":[0-9]*' | cut -d':' -f2)

if [ "$STORED_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✅ Token stored in memory${NC}"
else
    echo -e "${RED}❌ Token not stored${NC}"
fi

# Test 2: Make payment
echo ""
echo -e "${YELLOW}Test 2: subscription-payment${NC}"
PAYMENT_RESPONSE=$(curl -s -X POST $BASE_URL/api/subscription-payment \
  -H "Content-Type: application/json" \
  -d "{\"shopperReference\":\"$SHOPPER\",\"recurringDetailReference\":\"$TOKEN\",\"amount\":500}")

PAYMENT_RESULT=$(echo "$PAYMENT_RESPONSE" | grep -o '"resultCode":"[^"]*"' | cut -d'"' -f4)

if [ "$PAYMENT_RESULT" = "Authorised" ] || [ "$PAYMENT_RESULT" = "Pending" ]; then
    echo -e "${GREEN}✅ Payment authorized${NC}"
    echo "   Result: $PAYMENT_RESULT"
else
    echo -e "${YELLOW}⚠️  Payment result: $PAYMENT_RESULT${NC}"
    echo "   Response: $PAYMENT_RESPONSE"
fi

# Test 3: Cancel subscription
echo ""
echo -e "${YELLOW}Test 3: subscription-cancel${NC}"
CANCEL_RESPONSE=$(curl -s -X POST $BASE_URL/api/subscriptions-cancel \
  -H "Content-Type: application/json" \
  -d "{\"shopperReference\":\"$SHOPPER\",\"recurringDetailReference\":\"$TOKEN\"}")

CANCEL_RESULT=$(echo "$CANCEL_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

if [ "$CANCEL_RESULT" = "cancelled" ] || echo "$CANCEL_RESPONSE" | grep -q "success"; then
    echo -e "${GREEN}✅ Subscription canceled${NC}"
else
    echo -e "${YELLOW}⚠️  Cancel result: $CANCEL_RESPONSE${NC}"
fi

# Verify token removed
STORED_AFTER=$(curl -s $BASE_URL/api/debug/tokens)
STORED_COUNT_AFTER=$(echo "$STORED_AFTER" | grep -o '"totalTokens":[0-9]*' | cut -d':' -f2)

if [ "$STORED_COUNT_AFTER" -eq 0 ]; then
    echo -e "${GREEN}✅ Token removed from storage${NC}"
else
    echo -e "${YELLOW}⚠️  Token still in storage: count=$STORED_COUNT_AFTER${NC}"
fi

echo ""
echo "=========================================="
echo -e "${GREEN}All tests completed!${NC}"
echo "Check console output for webhook details"
echo "=========================================="
