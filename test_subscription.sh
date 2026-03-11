#!/bin/bash

# First, start the server
echo "Starting server..."
cd /Users/rbhat/Documents/GitHub/adyen-step-by-step-integration-workshop 
./gradlew bootRun > /tmp/server.log 2>&1 &
SERVER_PID=$!
sleep 8

# Test 1: Check health
echo "Test 1: Server health check"
curl -s http://localhost:8080 | grep -q "</html>" && echo "✅ Server is running" || echo "❌ Server failed to start"

# Test 2: Get payment methods
echo -e "\nTest 2: Get payment methods"
curl -s -X POST http://localhost:8080/api/paymentMethods \
  -H "Content-Type: application/json" \
  -d '{}' | jq . 2>/dev/null | head -20

# Test 3: Simulate webhook with RECURRING_CONTRACT
echo -e "\n\nTest 3: Simulating RECURRING_CONTRACT webhook"
WEBHOOK_PAYLOAD='{
  "item": {
    "eventCode": "RECURRING_CONTRACT",
    "pspReference": "8815718415172204",
    "merchantReference": "order-123",
    "success": true,
    "additionalData": {
      "recurring.recurringDetailReference": "8415718415172204",
      "recurring.shopperReference": "shopper-123",
      "shopperEmail": "test@example.com"
    }
  }
}'

curl -v -X POST http://localhost:8080/webhooks \
  -H "Content-Type: application/json" \
  -d "$WEBHOOK_PAYLOAD" 2>&1 | grep -E "HTTP/|TOKEN|STORED|===|✅|❌"

# Test 4: Check stored tokens
echo -e "\n\nTest 4: Check stored tokens"
curl -s -X POST http://localhost:8080/api/debug/stored-tokens \
  -H "Content-Type: application/json" | jq . 2>/dev/null

# Cleanup
kill $SERVER_PID 2>/dev/null
echo -e "\n✅ Test complete. Server stopped."
