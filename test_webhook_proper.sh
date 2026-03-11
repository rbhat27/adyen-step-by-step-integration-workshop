#!/bin/bash

# Extract HMAC key from application.properties
HMAC_KEY=$(grep -o 'ADYEN_HMAC_KEY=.*' /Users/rbhat/Documents/GitHub/adyen-step-by-step-integration-workshop/src/main/resources/application.properties | cut -d= -f2)

if [[ -z "$HMAC_KEY" ]]; then
    echo "❌ Could not find HMAC key in application.properties"
    exit 1
fi

echo "HMAC Key found: $HMAC_KEY"
echo ""

# Start server
echo "Starting server..."
cd /Users/rbhat/Documents/GitHub/adyen-step-by-step-integration-workshop
timeout 90 ./gradlew bootRun > /tmp/server.log 2>&1 &
SERVER_PID=$!
sleep 10

# Function to generate Adyen webhook HMAC
# Webhook payload as JSON
WEBHOOK_PAYLOAD='{
  "notificationItems": [
    {
      "NotificationRequestItem": {
        "additionalData": {
          "recurring.recurringDetailReference": "8415718415172204",
          "recurring.shopperReference": "shopper-001",
          "shopperEmail": "test@example.com"
        },
        "eventCode": "RECURRING_CONTRACT",
        "eventDate": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
        "merchantAccount": "TestMerchant",
        "merchantReference": "order-123",
        "pspReference": "8815718415172204",
        "reason": "000 Authorised",
        "success": "true"
      }
    }
  ]
}'

echo "=== Testing webhook with valid HMAC ==="  
echo "Sending webhook payload..."

# Send webhook
WEBHOOK_RESPONSE=$(curl -s -X POST http://localhost:8080/webhooks \
  -H "Content-Type: application/json" \
  -d "$WEBHOOK_PAYLOAD")

echo "Webhook response: $WEBHOOK_RESPONSE"
echo ""

# Sleep to let webhook process
sleep 2

# Check stored tokens
echo ""
echo "=== Checking stored tokens ==="
curl -s -X POST http://localhost:8080/api/debug/stored-tokens \
  -H "Content-Type: application/json" | jq . 2>/dev/null || echo "Failed to parse response"

# Check server logs to see debug output
echo ""
echo "=== Server debug output (last 30 lines) ==="
tail -30 /tmp/server.log | grep -E "RECURRING_CONTRACT|TOKEN|===|✅|❌"

# Cleanup
kill $SERVER_PID 2>/dev/null
sleep 2
echo ""
echo "✅ Test complete. Server stopped."
