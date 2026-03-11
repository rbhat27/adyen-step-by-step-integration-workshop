package com.adyen.workshop.controllers;

import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.*;
import com.adyen.model.payment.AdjustAuthorisationRequest;
import com.adyen.model.payment.CaptureRequest;
import com.adyen.model.payment.CancelRequest;
import com.adyen.model.payment.RefundRequest;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.PaymentApi;
import com.adyen.service.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for using the Adyen payments API.
 */
@RestController
public class ApiController {
    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;
    private final PaymentApi paymentApi;
    private final RestTemplate restTemplate;

    public ApiController(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi, PaymentApi paymentApi, RestTemplate restTemplate) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
        this.paymentApi = paymentApi;
        this.restTemplate = restTemplate;
    }

    // Step 0
    @GetMapping("/hello-world")
    public ResponseEntity<String> helloWorld() {
        return ResponseEntity.ok()
                .body("This is the 'Hello World' from the workshop - You've successfully finished step 0!");
    }

    // Step 7
    @PostMapping("/api/paymentMethods")
    public ResponseEntity<PaymentMethodsResponse> paymentMethods() throws IOException, ApiException {
        var paymentMethodsRequest = new PaymentMethodsRequest();
        paymentMethodsRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());

        log.info("Retrieving available Payment Methods from Adyen");
        var response = paymentsApi.paymentMethods(paymentMethodsRequest);
        log.info("Payment Methods response received");
        return ResponseEntity.ok().body(response);
    }

    // Step 9 - Implement the /payments call to Adyen.
    @PostMapping("/api/payments")
    public ResponseEntity<PaymentResponse> payments(@RequestBody PaymentRequest body) throws IOException, ApiException {
        var paymentRequest = new PaymentRequest();

        var amount = new Amount()
                .currency("EUR")
                .value(9998L);
        paymentRequest.setAmount(amount);
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(body.getPaymentMethod());

        var orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);
        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");

        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        paymentRequest.setAuthenticationData(authenticationData);

        paymentRequest.setOrigin("https://localhost:8080");
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setShopperIP("192.168.0.1");
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

        var billingAddress = new BillingAddress();
        billingAddress.setCity("Amsterdam");
        billingAddress.setCountry("NL");
        billingAddress.setPostalCode("1012KK");
        billingAddress.setStreet("Rokin");
        billingAddress.setHouseNumberOrName("49");
        paymentRequest.setBillingAddress(billingAddress);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Processing payment request, reference: {}", orderRef);
        var response = paymentsApi.payments(paymentRequest, requestOptions);
        
        log.info("Payment response received, resultCode: {}", response.getResultCode());
        return ResponseEntity.ok().body(response);
    }

    // Step 13 - Handle details call
    @PostMapping("/api/payments/details")
    public ResponseEntity<PaymentDetailsResponse> paymentsDetails(@RequestBody PaymentDetailsRequest detailsRequest)
            throws IOException, ApiException {
        log.info("Processing payment details");
        var response = paymentsApi.paymentsDetails(detailsRequest);
        log.info("Payment details response received, resultCode: {}", response.getResultCode());
        return ResponseEntity.ok().body(response);
    }

    // Step 14 - Handle Redirect 3DS2 during payment.
    @GetMapping("/handleShopperRedirect")
    public RedirectView redirect(@RequestParam(required = false) String payload,
            @RequestParam(required = false) String redirectResult) throws IOException, ApiException {
        var paymentDetailsRequest = new PaymentDetailsRequest();
        PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails();

        if (redirectResult != null && !redirectResult.isEmpty()) {
            paymentCompletionDetails.redirectResult(redirectResult);
        } else if (payload != null && !payload.isEmpty()) {
            paymentCompletionDetails.payload(payload);
        }

        paymentDetailsRequest.setDetails(paymentCompletionDetails);

        log.info("Processing redirect 3DS2");
        var paymentsDetailsResponse = paymentsApi.paymentsDetails(paymentDetailsRequest);
        
        log.info("Redirect processed, resultCode: {}", paymentsDetailsResponse.getResultCode());

        var redirectURL = "http://localhost:8080/result/";
        switch (paymentsDetailsResponse.getResultCode()) {
            case AUTHORISED:
                redirectURL += "success";
                break;
            case PENDING:
            case RECEIVED:
                redirectURL += "pending";
                break;
            case REFUSED:
                redirectURL += "failed";
                break;
            default:
                redirectURL += "error";
                break;
        }
        return new RedirectView(redirectURL + "?reason=" + paymentsDetailsResponse.getResultCode());
    }

    // Step 15 - Tokenization: Implement /api/subscription-create endpoint
    @PostMapping("/api/subscription-create")
    public ResponseEntity<PaymentResponse> subscriptionCreate(@RequestBody PaymentRequest body)
            throws IOException, ApiException {
        var paymentRequest = new PaymentRequest();

        var amount = new Amount()
                .currency("EUR")
                .value(0L);
        paymentRequest.setAmount(amount);
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(body.getPaymentMethod());

        var orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);
        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");

        String shopperReference = "shopper-" + System.currentTimeMillis();
        paymentRequest.setShopperReference(shopperReference);
        log.info("Creating subscription for shopper: {}", shopperReference);

        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        paymentRequest.setStorePaymentMethod(true);

        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        paymentRequest.setAuthenticationData(authenticationData);

        paymentRequest.setOrigin("https://localhost:8080");
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setShopperIP("192.168.0.1");
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

        var billingAddress = new BillingAddress();
        billingAddress.setCity("Amsterdam");
        billingAddress.setCountry("NL");
        billingAddress.setPostalCode("1012KK");
        billingAddress.setStreet("Rokin");
        billingAddress.setHouseNumberOrName("49");
        paymentRequest.setBillingAddress(billingAddress);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        var response = paymentsApi.payments(paymentRequest, requestOptions);
        
        log.info("Subscription created, resultCode: {}", response.getResultCode());

        // Extract and store token from response
        if (response.getAdditionalData() != null && !response.getAdditionalData().isEmpty()) {
            String token = response.getAdditionalData().get("recurring.recurringDetailReference");
            if (token == null || token.isEmpty()) {
                token = response.getAdditionalData().get("tokenization.storedPaymentMethodId");
            }
            
            if (token != null && !token.isEmpty()) {
                WebhookController.storeToken(shopperReference, token);
                log.info("Token stored for shopper: {}", shopperReference);
            }
        }

        return ResponseEntity.ok().body(response);
    }

    // Step 3 - Tokenization: Implement /api/subscription-payment endpoint
    @PostMapping("/api/subscription-payment")
    public ResponseEntity<Map<String, Object>> subscriptionPayment(@RequestBody Map<String, Object> body)
            throws IOException, ApiException {
        String shopperReference = (String) body.get("shopperReference");
        String recurringDetailReference = (String) body.get("recurringDetailReference");
        
        if (recurringDetailReference == null) {
            recurringDetailReference = WebhookController.getToken(shopperReference);
        }
      
        if (recurringDetailReference == null) {
            log.warn("Token not found for shopper: {}", shopperReference);
            var errorResponse = new HashMap<String, Object>();
            errorResponse.put("error", "Token not found");
            errorResponse.put("message", "No recurring detail reference found");
            return ResponseEntity.status(400).body(errorResponse);
        }

        log.info("Processing subscription payment for shopper: {}", shopperReference);
       
        Object amountObj = body.get("amount");
        Long amountValue = amountObj != null ? ((Number) amountObj).longValue() : 500L;
      
        try {
            var requestBody = new HashMap<String, Object>();
            var amountMap = new HashMap<String, Object>();
            amountMap.put("value", amountValue);
            amountMap.put("currency", "EUR");
            requestBody.put("amount", amountMap);
           
            var paymentMethodMap = new HashMap<String, String>();
            paymentMethodMap.put("type", "card");
            paymentMethodMap.put("storedPaymentMethodId", recurringDetailReference);
            requestBody.put("paymentMethod", paymentMethodMap);
           
            requestBody.put("reference", "SUBSC-" + UUID.randomUUID().toString());
            requestBody.put("merchantAccount", applicationConfiguration.getAdyenMerchantAccount());
            requestBody.put("returnUrl", "http://localhost:8080/handleShopperRedirect");
            requestBody.put("shopperReference", shopperReference);
            requestBody.put("shopperInteraction", "ContAuth");
            requestBody.put("recurringProcessingModel", "Subscription");
           
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-API-key", applicationConfiguration.getAdyenApiKey());
           
            try {
                var entity = new org.springframework.http.HttpEntity<>(requestBody, headers);
               
                var response = restTemplate.postForEntity(
                    "https://checkout-test.adyen.com/v70/payments",
                    entity,
                    Map.class
                );
               
                var responseBody = response.getBody();
                log.info("Payment processed, status: {}, resultCode: {}", 
                    response.getStatusCode(), 
                    responseBody != null ? responseBody.get("resultCode") : "unknown");
               
                var resultMap = new HashMap<String, Object>(responseBody != null ? responseBody : new HashMap<>());
                return ResponseEntity.ok(resultMap);
               
            } catch (org.springframework.web.client.HttpClientErrorException httpEx) {
                log.error("Adyen HTTP error: Status={}", httpEx.getStatusCode());
                var errorResponse = new HashMap<String, Object>();
                errorResponse.put("error", "Adyen API Error");
                errorResponse.put("statusCode", httpEx.getStatusCode().value());
                errorResponse.put("message", httpEx.getResponseBodyAsString());
                return ResponseEntity.status(httpEx.getStatusCode()).body(errorResponse);
               
            } catch (org.springframework.web.client.RestClientException restEx) {
                log.error("Adyen REST client error: {}", restEx.getMessage());
                var errorResponse = new HashMap<String, Object>();
                errorResponse.put("error", "REST Client Error");
                errorResponse.put("message", restEx.getMessage());
                return ResponseEntity.status(500).body(errorResponse);
            }
           
        } catch (Exception e) {
            log.error("Error processing subscription payment", e);
            var errorResponse = new HashMap<String, Object>();
            errorResponse.put("error", "Processing Error");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // Step 4 - Tokenization: Implement /api/subscriptions-cancel endpoint
    @PostMapping("/api/subscriptions-cancel")
    public ResponseEntity<Map<String, Object>> subscriptionCancel(@RequestBody Map<String, Object> body) {
        String shopperReference = (String) body.get("shopperReference");
        String recurringDetailReference = (String) body.get("recurringDetailReference");

        if (recurringDetailReference == null) {
            var errorResponse = new HashMap<String, Object>();
            errorResponse.put("error", "Missing recurringDetailReference");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        log.info("Cancelling subscription for shopper: {}", shopperReference);
      
        try {
            var requestBody = new HashMap<String, Object>();
            requestBody.put("shopperReference", shopperReference);
            requestBody.put("recurringDetailReference", recurringDetailReference);
            requestBody.put("merchantAccount", applicationConfiguration.getAdyenMerchantAccount());
           
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-API-key", applicationConfiguration.getAdyenApiKey());
           
            try {
                var entity = new org.springframework.http.HttpEntity<>(requestBody, headers);
               
                var response = restTemplate.postForEntity(
                    "https://pal-test.adyen.com/pal/servlet/Recurring/v1/disable",
                    entity,
                    Map.class
                );
               
                var responseBody = response.getBody();
                log.info("Subscription cancelled, status: {}", response.getStatusCode());
               
                WebhookController.removeToken(shopperReference);
               
                var resultMap = new HashMap<String, Object>(responseBody != null ? responseBody : new HashMap<>());
                return ResponseEntity.ok(resultMap);
               
            } catch (org.springframework.web.client.HttpClientErrorException httpEx) {
                log.error("Adyen HTTP error: Status={}", httpEx.getStatusCode());
                var errorResponse = new HashMap<String, Object>();
                errorResponse.put("error", "Adyen API Error");
                errorResponse.put("statusCode", httpEx.getStatusCode().value());
                errorResponse.put("message", httpEx.getResponseBodyAsString());
                return ResponseEntity.status(httpEx.getStatusCode()).body(errorResponse);
               
            } catch (org.springframework.web.client.RestClientException restEx) {
                log.error("Adyen REST client error: {}", restEx.getMessage());
                var errorResponse = new HashMap<String, Object>();
                errorResponse.put("error", "REST Client Error");
                errorResponse.put("message", restEx.getMessage());
                return ResponseEntity.status(500).body(errorResponse);
            }
           
        } catch (Exception e) {
            log.error("Error processing subscription cancel", e);
            var errorResponse = new HashMap<String, Object>();
            errorResponse.put("error", "Processing Error");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // === PREAUTHORISATION MODULE ===

    // Step 1 - Preauthorisation
    @PostMapping("/api/preauthorisation")
    public ResponseEntity<PaymentResponse> preauthorisation(@RequestBody PaymentRequest body)
            throws IOException, ApiException {
        var paymentRequest = new PaymentRequest();

        var amount = new Amount()
                .currency("EUR")
                .value(1000L);
        paymentRequest.setAmount(amount);
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(body.getPaymentMethod());

        var orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);
        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");
        paymentRequest.setCaptureDelayHours(7);

        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        paymentRequest.setAuthenticationData(authenticationData);

        paymentRequest.setOrigin("https://localhost:8080");
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setShopperIP("192.168.0.1");
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

        var billingAddress = new BillingAddress();
        billingAddress.setCity("Amsterdam");
        billingAddress.setCountry("NL");
        billingAddress.setPostalCode("1012KK");
        billingAddress.setStreet("Rokin");
        billingAddress.setHouseNumberOrName("49");
        paymentRequest.setBillingAddress(billingAddress);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Processing preauthorisation, reference: {}", orderRef);
        var response = paymentsApi.payments(paymentRequest, requestOptions);
        
        log.info("Preauthorisation response received, resultCode: {}", response.getResultCode());
        return ResponseEntity.ok().body(response);
    }

    // Step 2 - Preauthorisation: Modify amount
    @PostMapping("/api/modify-amount")
    public ResponseEntity<Map<String, Object>> modifyAmount(@RequestBody Map<String, Object> body)
            throws IOException, ApiException {
        String pspReference = (String) body.get("pspReference");
        Long modifyAmount = ((Number) body.get("modifyAmount")).longValue();

        if (pspReference == null) {
            log.error("pspReference is required");
            return ResponseEntity.badRequest().build();
        }

        log.info("Adjusting authorization amount for pspReference: {}, amount: {}", pspReference, modifyAmount);
      
        try {
            var modifyRequest = new AdjustAuthorisationRequest();
            modifyRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
            modifyRequest.setOriginalReference(pspReference);
           
            var modifyRequestAmount = new com.adyen.model.payment.Amount();
            modifyRequestAmount.setCurrency("EUR");
            modifyRequestAmount.setValue(modifyAmount);
            modifyRequest.setModificationAmount(modifyRequestAmount);
           
            var response = paymentApi.adjustAuthorisation(modifyRequest);
           
            log.info("Amount adjustment response received");
            
            var result = new HashMap<String, Object>();
            result.put("status", "success");
            result.put("message", "Amount adjustment request submitted");
            result.put("pspReference", pspReference);
            result.put("modifyAmount", modifyAmount);
            result.put("modificationPspReference", response.getPspReference());
           
            return ResponseEntity.ok().body(result);
        } catch (ApiException e) {
            log.error("Adyen API error adjusting authorization", e);
            var error = new HashMap<String, Object>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            error.put("statusCode", e.getStatusCode());
            return ResponseEntity.status(500).body(error);
        } catch (Exception e) {
            log.error("Error adjusting authorization", e);
            var error = new HashMap<String, Object>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // Step 3 - Preauthorisation: Capture
    @PostMapping("/api/capture")
    public ResponseEntity<Map<String, Object>> capture(@RequestBody Map<String, Object> body)
            throws IOException, ApiException {
        String pspReference = (String) body.get("pspReference");
        Long captureAmount = ((Number) body.get("captureAmount")).longValue();

        if (pspReference == null) {
            log.error("pspReference is required");
            return ResponseEntity.badRequest().build();
        }

        log.info("Capturing payment with pspReference: {}, amount: {}", pspReference, captureAmount);
      
        try {
            var captureRequest = new CaptureRequest();
            captureRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
            captureRequest.setOriginalReference(pspReference);
           
            var captureRequestAmount = new com.adyen.model.payment.Amount();
            captureRequestAmount.setCurrency("EUR");
            captureRequestAmount.setValue(captureAmount);
            captureRequest.setModificationAmount(captureRequestAmount);
           
            var response = paymentApi.capture(captureRequest);
           
            log.info("Capture processed successfully");
            
            var result = new HashMap<String, Object>();
            result.put("status", "success");
            result.put("message", "Capture request submitted");
            result.put("pspReference", pspReference);
            result.put("captureAmount", captureAmount);
            result.put("modificationPspReference", response.getPspReference());
           
            return ResponseEntity.ok().body(result);
        } catch (ApiException e) {
            log.error("Adyen API error capturing payment", e);
            var error = new HashMap<String, Object>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            error.put("statusCode", e.getStatusCode());
            return ResponseEntity.status(500).body(error);
        } catch (Exception e) {
            log.error("Error capturing payment", e);
            var error = new HashMap<String, Object>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // Step 4 - Preauthorisation: Cancel
    @PostMapping("/api/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@RequestBody Map<String, Object> body)
            throws IOException, ApiException {
        String pspReference = (String) body.get("pspReference");

        if (pspReference == null) {
            log.error("pspReference is required");
            return ResponseEntity.badRequest().build();
        }

        log.info("Cancelling payment with pspReference: {}", pspReference);
      
        try {
            var cancelRequest = new CancelRequest();
            cancelRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
            cancelRequest.setOriginalReference(pspReference);
           
            var response = paymentApi.cancel(cancelRequest);
           
            log.info("Cancellation processed successfully");
            
            var result = new HashMap<String, Object>();
            result.put("status", "success");
            result.put("message", "Cancellation request submitted");
            result.put("pspReference", pspReference);
            result.put("modificationPspReference", response.getPspReference());
           
            return ResponseEntity.ok().body(result);
        } catch (ApiException e) {
            log.error("Adyen API error cancelling payment", e);
            var error = new HashMap<String, Object>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            error.put("statusCode", e.getStatusCode());
            return ResponseEntity.status(500).body(error);
        } catch (Exception e) {
            log.error("Error cancelling payment", e);
            var error = new HashMap<String, Object>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // Step 5 - Preauthorisation: Refund
    @PostMapping("/api/refund")
    public ResponseEntity<Map<String, Object>> refund(@RequestBody Map<String, Object> body)
            throws IOException, ApiException {
        String pspReference = (String) body.get("pspReference");
        Long refundAmount = ((Number) body.get("refundAmount")).longValue();

        if (pspReference == null) {
            log.error("pspReference is required");
            return ResponseEntity.badRequest().build();
        }

        log.info("Refunding payment with pspReference: {}, amount: {}", pspReference, refundAmount);
      
        try {
            var refundRequest = new RefundRequest();
            refundRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
            refundRequest.setOriginalReference(pspReference);
           
            var refundRequestAmount = new com.adyen.model.payment.Amount();
            refundRequestAmount.setCurrency("EUR");
            refundRequestAmount.setValue(refundAmount);
            refundRequest.setModificationAmount(refundRequestAmount);
           
            var response = paymentApi.refund(refundRequest);
           
            log.info("Refund processed successfully");
            
            var result = new HashMap<String, Object>();
            result.put("status", "success");
            result.put("message", "Refund request submitted");
            result.put("pspReference", pspReference);
            result.put("refundAmount", refundAmount);
            result.put("modificationPspReference", response.getPspReference());
           
            return ResponseEntity.ok().body(result);
        } catch (ApiException e) {
            log.error("Adyen API error refunding payment", e);
            var error = new HashMap<String, Object>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            error.put("statusCode", e.getStatusCode());
            return ResponseEntity.status(500).body(error);
        } catch (Exception e) {
            log.error("Error refunding payment", e);
            var error = new HashMap<String, Object>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // Debug endpoint to check what tokens are stored
    @GetMapping("/api/debug/tokens")
    public ResponseEntity<Map<String, Object>> debugTokens() {
        log.info("Debug tokens endpoint called");
        
        var allTokens = WebhookController.getAllTokens();
        log.info("Retrieved {} tokens", allTokens.size());
       
        var response = new HashMap<String, Object>();
        response.put("totalTokens", allTokens.size());
        response.put("tokens", allTokens);
        return ResponseEntity.ok(response);
    }
}

