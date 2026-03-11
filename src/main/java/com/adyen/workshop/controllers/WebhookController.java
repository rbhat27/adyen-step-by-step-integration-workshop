package com.adyen.workshop.controllers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for receiving Adyen webhook notifications
 */
@RestController
public class WebhookController {
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ApplicationConfiguration applicationConfiguration;
    private final HMACValidator hmacValidator;

    // In-memory storage for tokens (recurring detail references)
    private static final Map<String, String> tokenStorage = new HashMap<>();

    @Autowired
    public WebhookController(ApplicationConfiguration applicationConfiguration, HMACValidator hmacValidator) {
        this.applicationConfiguration = applicationConfiguration;
        this.hmacValidator = hmacValidator;
    }

    @PostMapping("/webhooks")
    public ResponseEntity<String> webhooks(@RequestBody String json) throws Exception {
        log.info("Received webhook notification");
        
        var notificationRequest = NotificationRequest.fromJson(json);
        var notificationRequestItem = notificationRequest.getNotificationItems().stream().findFirst();

        try {
            NotificationRequestItem item = notificationRequestItem.get();

            // Validate HMAC signature
            if (!hmacValidator.validateHMAC(item, this.applicationConfiguration.getAdyenHmacKey())) {
                log.warn("HMAC validation failed for incoming webhook");
                return ResponseEntity.unprocessableEntity().build();
            }

            String eventCode = item.getEventCode();
            log.info("Processing webhook event: {}", eventCode);

            switch (eventCode) {
                case "RECURRING_CONTRACT":
                    handleRecurringContractWebhook(item);
                    break;
                case "AUTHORISATION":
                    handleAuthorisationWebhook(item);
                    break;
                case "AUTHORISATION_ADJUSTMENT":
                    handleAuthorisationAdjustmentWebhook(item);
                    break;
                case "CAPTURE":
                    handleCaptureWebhook(item);
                    break;
                case "CAPTURE_FAILED":
                    handleCaptureFailedWebhook(item);
                    break;
                case "TECHNICAL_CANCEL":
                    handleTechnicalCancelWebhook(item);
                    break;
                case "CANCELLATION":
                    handleCancellationWebhook(item);
                    break;
                case "REFUND":
                    handleRefundWebhook(item);
                    break;
                case "REFUND_FAILED":
                    handleRefundFailedWebhook(item);
                    break;
                case "REFUNDED_REVERSED":
                    handleRefundedReversedWebhook(item);
                    break;
                default:
                    log.info("Unhandled webhook event code: {}", eventCode);
            }

            log.info("Webhook processed successfully, event: {}", eventCode);
            return ResponseEntity.ok("[accepted]");
            
        } catch (SignatureException e) {
            log.error("Signature exception processing webhook", e);
            return ResponseEntity.unprocessableEntity().build();
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(500).build();
        }
    }

    private void handleRecurringContractWebhook(NotificationRequestItem item) {
        log.info("Processing RECURRING_CONTRACT webhook, pspReference: {}", item.getPspReference());
        
        String recurringDetailReference = null;
        String shopperReference = null;
        
        if (item.getAdditionalData() != null) {
            recurringDetailReference = item.getAdditionalData().get("recurring.recurringDetailReference");
            if (recurringDetailReference == null || recurringDetailReference.isEmpty()) {
                recurringDetailReference = item.getAdditionalData().get("tokenization.storedPaymentMethodId");
            }
            if (recurringDetailReference == null || recurringDetailReference.isEmpty()) {
                recurringDetailReference = item.getPspReference();
            }
            
            shopperReference = item.getAdditionalData().get("recurring.shopperReference");
            if (shopperReference == null || shopperReference.isEmpty()) {
                shopperReference = item.getAdditionalData().get("tokenization.shopperReference");
            }
            if (shopperReference == null || shopperReference.isEmpty()) {
                shopperReference = item.getMerchantReference();
            }
        }
        
        if (recurringDetailReference != null && !recurringDetailReference.isEmpty()) {
            if (shopperReference == null || shopperReference.isEmpty()) {
                shopperReference = item.getMerchantReference();
            }
            if (shopperReference == null || shopperReference.isEmpty()) {
                shopperReference = item.getPspReference();
            }
            
            tokenStorage.put(shopperReference, recurringDetailReference);
            log.info("Token stored for shopper: {}", shopperReference);
        } else {
            log.warn("No token found in RECURRING_CONTRACT webhook");
        }
    }

    private void handleAuthorisationWebhook(NotificationRequestItem item) {
        log.info("Processing AUTHORISATION webhook, pspReference: {}, success: {}", 
            item.getPspReference(), item.isSuccess());
        
        if (item.getAdditionalData() != null) {
            String storedPaymentMethodId = item.getAdditionalData().get("tokenization.storedPaymentMethodId");
            String shopperReference = item.getAdditionalData().get("tokenization.shopperReference");
            
            if (storedPaymentMethodId != null && !storedPaymentMethodId.isEmpty() 
                && shopperReference != null && !shopperReference.isEmpty()) {
                tokenStorage.put(shopperReference, storedPaymentMethodId);
                log.info("Token stored from AUTHORISATION for shopper: {}", shopperReference);
            }
        }
    }

    private void handleAuthorisationAdjustmentWebhook(NotificationRequestItem item) {
        log.info("Processing AUTHORISATION_ADJUSTMENT webhook, pspReference: {}, success: {}", 
            item.getPspReference(), item.isSuccess());
    }

    private void handleCaptureWebhook(NotificationRequestItem item) {
        log.info("Processing CAPTURE webhook, pspReference: {}, success: {}", 
            item.getPspReference(), item.isSuccess());
    }

    private void handleCaptureFailedWebhook(NotificationRequestItem item) {
        log.warn("Processing CAPTURE_FAILED webhook, pspReference: {}, reason: {}", 
            item.getPspReference(), item.getReason());
    }

    private void handleTechnicalCancelWebhook(NotificationRequestItem item) {
        log.info("Processing TECHNICAL_CANCEL webhook, pspReference: {}, success: {}", 
            item.getPspReference(), item.isSuccess());
    }

    private void handleCancellationWebhook(NotificationRequestItem item) {
        log.info("Processing CANCELLATION webhook, pspReference: {}, success: {}", 
            item.getPspReference(), item.isSuccess());
    }

    private void handleRefundWebhook(NotificationRequestItem item) {
        log.info("Processing REFUND webhook, pspReference: {}, success: {}", 
            item.getPspReference(), item.isSuccess());
    }

    private void handleRefundFailedWebhook(NotificationRequestItem item) {
        log.warn("Processing REFUND_FAILED webhook, pspReference: {}, reason: {}", 
            item.getPspReference(), item.getReason());
    }

    private void handleRefundedReversedWebhook(NotificationRequestItem item) {
        log.warn("Processing REFUNDED_REVERSED webhook, pspReference: {}", item.getPspReference());
    }

    // Token storage methods
    public static String getToken(String shopperReference) {
        return tokenStorage.get(shopperReference);
    }

    public static void storeToken(String shopperReference, String token) {
        if (shopperReference != null && token != null && !shopperReference.isEmpty() && !token.isEmpty()) {
            tokenStorage.put(shopperReference, token);
            log.info("Token stored for shopper: {}", shopperReference);
        }
    }

    public static void removeToken(String shopperReference) {
        if (shopperReference != null && !shopperReference.isEmpty()) {
            String removed = tokenStorage.remove(shopperReference);
            if (removed != null) {
                log.info("Token removed for shopper: {}", shopperReference);
            }
        }
    }

    public static Map<String, String> getAllTokens() {
        return new HashMap<>(tokenStorage);
    }

    @PostMapping("/api/debug/stored-tokens")
    public ResponseEntity<Map<String, Object>> getStoredTokens() {
        var response = new HashMap<String, Object>();
        response.put("totalTokens", tokenStorage.size());
        response.put("tokens", tokenStorage);
        return ResponseEntity.ok(response);
    }
}

