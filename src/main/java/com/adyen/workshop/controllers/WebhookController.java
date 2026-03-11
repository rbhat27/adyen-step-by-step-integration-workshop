package com.adyen.workshop.controllers;


import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.io.IOException;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;


/**
* REST controller for receiving Adyen webhook notifications
*/
@RestController
public class WebhookController {
   private final Logger log = LoggerFactory.getLogger(WebhookController.class);


   private final ApplicationConfiguration applicationConfiguration;


   private final HMACValidator hmacValidator;


   // In-memory storage for tokens (recurring detail references)
   // In production, this should be stored in a database
   private static final Map<String, String> tokenStorage = new HashMap<>();


   @Autowired
   public WebhookController(ApplicationConfiguration applicationConfiguration, HMACValidator hmacValidator) {
       this.applicationConfiguration = applicationConfiguration;
       this.hmacValidator = hmacValidator;
   }


   // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
   @PostMapping("/webhooks")
   public ResponseEntity<String> webhooks(@RequestBody String json) throws Exception {
       log.info("Received: {}", json);
       
       // IMPORTANT DEBUG: Log that webhook was received
       System.out.println("\n╔════════════════════════════════════════════╗");
       System.out.println("║        🔔 WEBHOOK RECEIVED AT SERVER       ║");
       System.out.println("╚════════════════════════════════════════════╝");
       
       var notificationRequest = NotificationRequest.fromJson(json);
       var notificationRequestItem = notificationRequest.getNotificationItems().stream().findFirst();


       try {
           NotificationRequestItem item = notificationRequestItem.get();


           // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
           if (!hmacValidator.validateHMAC(item, this.applicationConfiguration.getAdyenHmacKey())) {
               log.warn("Could not validate HMAC signature for incoming webhook message: {}", item);
               System.out.println("❌ HMAC VALIDATION FAILED");
               System.out.println("===========================================\n");
               return ResponseEntity.unprocessableEntity().build();
           }


           // Step 17 - Handle webhooks
           String eventCode = item.getEventCode();
           System.out.println("Event Code: " + eventCode);
           System.out.println("PSP Reference: " + item.getPspReference());
           System.out.println("Success: " + item.isSuccess());
           log.info("Handling webhook event: {}", eventCode);


           switch (eventCode) {
               // Tokenization webhooks
               case "RECURRING_CONTRACT":
                   System.out.println("→ Routing to: handleRecurringContractWebhook()");
                   // Handle RECURRING_CONTRACT webhook
                   // This webhook contains the recurringDetailReference (token) we need for future payments
                   handleRecurringContractWebhook(item);
                   break;
               case "AUTHORISATION":
                   System.out.println("→ Routing to: handleAuthorisationWebhook()");
                   // Handle AUTHORISATION webhook
                   handleAuthorisationWebhook(item);
                   break;
               // Preauthorisation webhooks
               case "AUTHORISATION_ADJUSTMENT":
                   System.out.println("→ Routing to: handleAuthorisationAdjustmentWebhook()");
                   // Handle authorization adjustment webhook
                   handleAuthorisationAdjustmentWebhook(item);
                   break;
               case "CAPTURE":
                   System.out.println("→ Routing to: handleCaptureWebhook()");
                   // Handle capture webhook
                   handleCaptureWebhook(item);
                   break;
               case "CAPTURE_FAILED":
                   System.out.println("→ Routing to: handleCaptureFailedWebhook()");
                   // Handle capture failed webhook
                   handleCaptureFailedWebhook(item);
                   break;
               case "TECHNICAL_CANCEL":
                   System.out.println("→ Routing to: handleTechnicalCancelWebhook()");
                   // Handle technical cancellation webhook
                   handleTechnicalCancelWebhook(item);
                   break;
               case "CANCELLATION":
                   System.out.println("→ Routing to: handleCancellationWebhook()");
                   // Handle cancellation webhook
                   handleCancellationWebhook(item);
                   break;
               case "REFUND":
                   System.out.println("→ Routing to: handleRefundWebhook()");
                   // Handle refund webhook
                   handleRefundWebhook(item);
                   break;
               case "REFUND_FAILED":
                   System.out.println("→ Routing to: handleRefundFailedWebhook()");
                   // Handle refund failed webhook
                   handleRefundFailedWebhook(item);
                   break;
               case "REFUNDED_REVERSED":
                   System.out.println("→ Routing to: handleRefundedReversedWebhook()");
                   // Handle refunded reversed webhook
                   handleRefundedReversedWebhook(item);
                   break;
               default:
                   System.out.println("⚠️ Unhandled webhook event code: " + eventCode);
                   log.info("Unhandled webhook event code: {}", eventCode);
           }


           // Success, log it for now
           System.out.println("✅ Webhook processed successfully");
           System.out.println("===========================================\n");
           log.info("Received webhook with event {}", item.toString());


           return ResponseEntity.ok("[accepted]");
       } catch (SignatureException e) {
           // Handle invalid signature
           System.out.println("❌ SIGNATURE EXCEPTION");
           System.out.println("===========================================\n");
           return ResponseEntity.unprocessableEntity().build();
       } catch (Exception e) {
           // Handle all other errors
           System.out.println("❌ EXCEPTION: " + e.getMessage());
           e.printStackTrace();
           System.out.println("===========================================\n");
           return ResponseEntity.status(500).build();
       }
   }


   /**
    * Handle RECURRING_CONTRACT webhook
    * This webhook contains the recurringDetailReference which is the token we need
    */
   private void handleRecurringContractWebhook(NotificationRequestItem item) {
       log.info("Processing RECURRING_CONTRACT webhook");
       
       // Log all available data
       System.out.println("\n╔════════════════════════════════════════════╗");
       System.out.println("║  ✅ RECURRING_CONTRACT WEBHOOK RECEIVED   ║");
       System.out.println("╚════════════════════════════════════════════╝");
       System.out.println("PSP Reference: " + item.getPspReference());
       System.out.println("Merchant Reference: " + item.getMerchantReference());
       System.out.println("Event Code: " + item.getEventCode());
       
       // Log all additional data fields
       if (item.getAdditionalData() != null && !item.getAdditionalData().isEmpty()) {
           System.out.println("\n📋 Additional Data fields:");
           item.getAdditionalData().forEach((key, value) -> {
               System.out.println("  " + key + " = " + value);
           });
       } else {
           System.out.println("No additional data found");
       }
      
       // Try multiple field names for the token
       String recurringDetailReference = null;
       String shopperReference = null;
       
       if (item.getAdditionalData() != null) {
           // Try Adyen's standard field name (older API)
           recurringDetailReference = item.getAdditionalData().get("recurring.recurringDetailReference");
           
           // Try alternative field name from tokenization response (newer API)
           if (recurringDetailReference == null || recurringDetailReference.isEmpty()) {
               recurringDetailReference = item.getAdditionalData().get("tokenization.storedPaymentMethodId");
           }
           
           // Also try pspReference as backup
           if (recurringDetailReference == null || recurringDetailReference.isEmpty()) {
               recurringDetailReference = item.getPspReference();
           }
           
           // Get shopper reference - try multiple field names
           shopperReference = item.getAdditionalData().get("recurring.shopperReference");
           if (shopperReference == null || shopperReference.isEmpty()) {
               shopperReference = item.getAdditionalData().get("tokenization.shopperReference");
           }
           if (shopperReference == null || shopperReference.isEmpty()) {
               shopperReference = item.getAdditionalData().get("shopperReference");
           }
       }
       
       if (recurringDetailReference != null && !recurringDetailReference.isEmpty()) {
           // Store the token for later use
           // In production, store this in a database with the shopper reference
           if (shopperReference == null || shopperReference.isEmpty()) {
               shopperReference = item.getMerchantReference();
           }
           if (shopperReference == null || shopperReference.isEmpty()) {
               shopperReference = item.getPspReference();
           }
           
           tokenStorage.put(shopperReference, recurringDetailReference);
          
           System.out.println("\n🎯 TOKEN SUCCESSFULLY STORED:");
           System.out.println("   Token/Recurring Detail Ref: " + recurringDetailReference);
           System.out.println("   Shopper Reference: " + shopperReference);
           System.out.println("   Token Length: " + recurringDetailReference.length());
           System.out.println("===========================================\n");
           
           log.info("✅ STORED TOKEN - Shopper: {}, Token: {}", shopperReference, recurringDetailReference);
       } else {
           System.out.println("\n❌ NO TOKEN FOUND in RECURRING_CONTRACT webhook");
           System.out.println("   Checked fields:\n" +
               "     - recurring.recurringDetailReference\n" +
               "     - tokenization.storedPaymentMethodId\n" +
               "     - PSP Reference");
           System.out.println("===========================================\n");
           log.warn("❌ NO TOKEN FOUND in RECURRING_CONTRACT webhook");
       }
   }


   /**
    * Handle AUTHORISATION webhook
    * This webhook indicates a payment has been authorized
    */
   private void handleAuthorisationWebhook(NotificationRequestItem item) {
       log.info("Processing AUTHORISATION webhook");
       log.info("Payment reference: {}", item.getPspReference());
       log.info("Merchant reference: {}", item.getMerchantReference());
       
       // Log all available data for debugging
       System.out.println("\n╔════════════════════════════════════════════╗");
       System.out.println("║     ✅ AUTHORISATION WEBHOOK RECEIVED     ║");
       System.out.println("╚════════════════════════════════════════════╝");
       System.out.println("PSP Reference: " + item.getPspReference());
       System.out.println("Merchant Reference: " + item.getMerchantReference());
       System.out.println("Success: " + item.isSuccess());
       System.out.println("Event Code: " + item.getEventCode());
       
       // Log all additional data fields
       if (item.getAdditionalData() != null && !item.getAdditionalData().isEmpty()) {
           System.out.println("\n📋 Additional Data fields:");
           item.getAdditionalData().forEach((key, value) -> {
               System.out.println("  " + key + " = " + value);
           });
       } else {
           System.out.println("No additional data found");
       }
       
       // Extract and log token information if present
       if (item.getAdditionalData() != null) {
           String storedPaymentMethodId = item.getAdditionalData().get("tokenization.storedPaymentMethodId");
           String recurringDetailReference = item.getAdditionalData().get("recurring.recurringDetailReference");
           String shopperReference = item.getAdditionalData().get("tokenization.shopperReference");
           String recurringModel = item.getAdditionalData().get("recurringProcessingModel");
           
           if (storedPaymentMethodId != null && !storedPaymentMethodId.isEmpty()) {
               System.out.println("\n🎯 TOKEN INFORMATION FOUND IN AUTH:");
               System.out.println("   Stored Payment Method ID: " + storedPaymentMethodId);
               System.out.println("   Shopper Reference: " + shopperReference);
               System.out.println("   Recurring Model: " + recurringModel);
               
               // Store token if available
               if (shopperReference != null && !shopperReference.isEmpty()) {
                   tokenStorage.put(shopperReference, storedPaymentMethodId);
                   log.info("✅ TOKEN STORED FROM AUTH - Shopper: {}, Token: {}", shopperReference, storedPaymentMethodId);
               }
           } else if (recurringDetailReference != null && !recurringDetailReference.isEmpty()) {
               System.out.println("\n🎯 TOKEN INFORMATION FOUND IN AUTH (Legacy):");
               System.out.println("   Recurring Detail Reference: " + recurringDetailReference);
               String shopperRef = item.getAdditionalData().get("recurring.shopperReference");
               if (shopperRef != null && !shopperRef.isEmpty()) {
                   tokenStorage.put(shopperRef, recurringDetailReference);
               }
           } else {
               if (item.isSuccess()) {
                   System.out.println("\n⚠️  Authorization successful but no token information found");
                   System.out.println("    This could be a standard payment or a subsequent subscription payment");
               } else {
                   System.out.println("\n❌ Authorization FAILED");
                   System.out.println("    No token information found");
               }
           }
       }
       System.out.println("===========================================\n");
      
       log.info("Authorization processed for reference: {}", item.getPspReference());
   }


   /**
    * Handle AUTHORISATION_ADJUSTMENT webhook
    * This webhook indicates an authorization amount adjustment
    */
   private void handleAuthorisationAdjustmentWebhook(NotificationRequestItem item) {
       log.info("Processing AUTHORISATION_ADJUSTMENT webhook");
       log.info("PSP Reference: {}", item.getPspReference());
       log.info("Merchant Reference: {}", item.getMerchantReference());
       log.info("Success: {}", item.isSuccess());
      
       if (item.isSuccess()) {
           log.info("Authorization adjustment successful for reference: {}", item.getPspReference());
       } else {
           log.warn("Authorization adjustment failed for reference: {}", item.getPspReference());
       }
   }


   /**
    * Handle CAPTURE webhook
    * This webhook indicates a payment has been captured
    */
   private void handleCaptureWebhook(NotificationRequestItem item) {
       log.info("Processing CAPTURE webhook");
       log.info("PSP Reference: {}", item.getPspReference());
       log.info("Merchant Reference: {}", item.getMerchantReference());
       log.info("Success: {}", item.isSuccess());
      
       if (item.isSuccess()) {
           log.info("Payment captured successfully for reference: {}", item.getPspReference());
       } else {
           log.warn("Payment capture failed for reference: {}", item.getPspReference());
       }
   }


   /**
    * Handle CAPTURE_FAILED webhook
    * This webhook indicates a capture failure
    */
   private void handleCaptureFailedWebhook(NotificationRequestItem item) {
       log.warn("Processing CAPTURE_FAILED webhook");
       log.warn("PSP Reference: {}", item.getPspReference());
       log.warn("Merchant Reference: {}", item.getMerchantReference());
       log.warn("Reason: {}", item.getReason());
       log.warn("Payment capture failed for reference: {}", item.getPspReference());
   }


   /**
    * Handle TECHNICAL_CANCEL webhook
    * This webhook indicates a technical cancellation
    */
   private void handleTechnicalCancelWebhook(NotificationRequestItem item) {
       log.info("Processing TECHNICAL_CANCEL webhook");
       log.info("PSP Reference: {}", item.getPspReference());
       log.info("Merchant Reference: {}", item.getMerchantReference());
       log.info("Success: {}", item.isSuccess());
      
       if (item.isSuccess()) {
           log.info("Technical cancellation successful for reference: {}", item.getPspReference());
       } else {
           log.warn("Technical cancellation failed for reference: {}", item.getPspReference());
       }
   }


   /**
    * Handle CANCELLATION webhook
    * This webhook indicates a payment has been cancelled
    */
   private void handleCancellationWebhook(NotificationRequestItem item) {
       log.info("Processing CANCELLATION webhook");
       log.info("PSP Reference: {}", item.getPspReference());
       log.info("Merchant Reference: {}", item.getMerchantReference());
       log.info("Success: {}", item.isSuccess());
      
       if (item.isSuccess()) {
           log.info("Payment cancellation successful for reference: {}", item.getPspReference());
       } else {
           log.warn("Payment cancellation failed for reference: {}", item.getPspReference());
       }
   }


   /**
    * Handle REFUND webhook
    * This webhook indicates a payment has been refunded
    */
   private void handleRefundWebhook(NotificationRequestItem item) {
       log.info("Processing REFUND webhook");
       log.info("PSP Reference: {}", item.getPspReference());
       log.info("Merchant Reference: {}", item.getMerchantReference());
       log.info("Success: {}", item.isSuccess());
      
       if (item.isSuccess()) {
           log.info("Payment refunded successfully for reference: {}", item.getPspReference());
       } else {
           log.warn("Payment refund failed for reference: {}", item.getPspReference());
       }
   }


   /**
    * Handle REFUND_FAILED webhook
    * This webhook indicates a refund failure
    */
   private void handleRefundFailedWebhook(NotificationRequestItem item) {
       log.warn("Processing REFUND_FAILED webhook");
       log.warn("PSP Reference: {}", item.getPspReference());
       log.warn("Merchant Reference: {}", item.getMerchantReference());
       log.warn("Reason: {}", item.getReason());
       log.warn("Payment refund failed for reference: {}", item.getPspReference());
   }


   /**
    * Handle REFUNDED_REVERSED webhook
    * This webhook indicates a refund reversal
    */
   private void handleRefundedReversedWebhook(NotificationRequestItem item) {
       log.warn("Processing REFUNDED_REVERSED webhook");
       log.warn("PSP Reference: {}", item.getPspReference());
       log.warn("Merchant Reference: {}", item.getMerchantReference());
       log.warn("Reason: {}", item.getReason());
       log.warn("Refund reversal for reference: {}", item.getPspReference());
   }


   /**
    * Get stored token for a shopper
    * This is used internally by the subscription payment endpoint
    */
   public static String getToken(String shopperReference) {
       return tokenStorage.get(shopperReference);
   }

   /**
    * Store a token for a shopper
    * This can be called directly from API responses or from webhooks
    */
   public static void storeToken(String shopperReference, String token) {
       if (shopperReference != null && token != null && !shopperReference.isEmpty() && !token.isEmpty()) {
           tokenStorage.put(shopperReference, token);
           System.out.println("📌 Token stored: " + shopperReference + " -> " + token);
       }
   }

   /**
    * Remove a token for a shopper (when subscription is cancelled)
    */
   public static void removeToken(String shopperReference) {
       if (shopperReference != null && !shopperReference.isEmpty()) {
           String removed = tokenStorage.remove(shopperReference);
           if (removed != null) {
               System.out.println("🗑️  Token removed: " + shopperReference + " (was: " + removed + ")");
           }
       }
   }

   /**
    * Get all stored tokens
    * This is used for debugging
    */
   public static Map<String, String> getAllTokens() {
       return new HashMap<>(tokenStorage);
   }

   /**
    * Debug endpoint: List all stored tokens
    * This helps verify that tokens are being properly stored from webhooks
    */
   @PostMapping("/api/debug/stored-tokens")
   public ResponseEntity<Map<String, Object>> getStoredTokens() {
       var response = new HashMap<String, Object>();
       response.put("totalTokens", tokenStorage.size());
       response.put("tokens", tokenStorage);
       response.put("debug", "All currently stored payment method tokens from webhooks");
       return ResponseEntity.ok(response);
   }
}
