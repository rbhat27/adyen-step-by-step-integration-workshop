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
   private final Logger log = LoggerFactory.getLogger(ApiController.class);


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
   public ResponseEntity<String> helloWorld() throws Exception {
       return ResponseEntity.ok()
               .body("This is the 'Hello World' from the workshop - You've successfully finished step 0!");
   }


   // Step 7
   @PostMapping("/api/paymentMethods")
   public ResponseEntity<PaymentMethodsResponse> paymentMethods() throws IOException, ApiException {
       var paymentMethodsRequest = new PaymentMethodsRequest();
       paymentMethodsRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());


       log.info("Retrieving available Payment Methods from Adyen {}", paymentMethodsRequest);
       var response = paymentsApi.paymentMethods(paymentMethodsRequest);
       log.info("Payment Methods response from Adyen {}", response);
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
       // The returnUrl field basically means: Once done with the payment, where should
       // the application redirect you?
       paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");


       // Step 12 3DS2 Redirect - Add the following additional parameters to your
       // existing payment request for 3DS2 Redirect:
       // Note: Visa requires additional properties to be sent in the request, see
       // documentation for Redirect 3DS2:
       // https://docs.adyen.com/online-payments/3d-secure/redirect-3ds2/web-drop-in/#make-a-payment
       var authenticationData = new AuthenticationData();
       authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
       paymentRequest.setAuthenticationData(authenticationData);


       // Change the following lines, if you want to enable the Native 3DS2 flow:
       // Note: Visa requires additional properties to be sent in the request, see
       // documentation for Native 3DS2:
       // https://docs.adyen.com/online-payments/3d-secure/native-3ds2/web-drop-in/#make-a-payment
       // authenticationData.setThreeDSRequestData(new
       // ThreeDSRequestData().nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.PREFERRED));
       // paymentRequest.setAuthenticationData(authenticationData);


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


       // Step 11 - Optionally add the idempotency key
       var requestOptions = new RequestOptions();
       requestOptions.setIdempotencyKey(UUID.randomUUID().toString());


       // Log the JSON request being sent to Adyen
       System.out.println("\n=== ADYEN API REQUEST ===");
       System.out.println("Operation: Payment (Checkout)");
       System.out.println("Request JSON:");
       System.out.println(paymentRequest.toJson());
       System.out.println("========================\n");
       
       var response = paymentsApi.payments(paymentRequest, requestOptions); // add RequestOptions here
       
       // Log the JSON response from Adyen
       System.out.println("\n=== ADYEN API RESPONSE ===");
       System.out.println("Response JSON:");
       System.out.println(response.toJson());
       System.out.println("=======================\n");
       log.info("PaymentsResponse {}", response);


       return ResponseEntity.ok().body(response);
   }


   // Step 13 - Handle details call (triggered after the Native 3DS2 flow, called
   // from the frontend in step 14)
   @PostMapping("/api/payments/details")
   public ResponseEntity<PaymentDetailsResponse> paymentsDetails(@RequestBody PaymentDetailsRequest detailsRequest)
           throws IOException, ApiException {
       // Log the JSON request being sent to Adyen
       System.out.println("\n=== ADYEN API REQUEST ===");
       System.out.println("Operation: Payment Details (Checkout)");
       System.out.println("Request JSON:");
       System.out.println(detailsRequest.toJson());
       System.out.println("========================\n");
       
       var response = paymentsApi.paymentsDetails(detailsRequest);
       
       // Log the JSON response from Adyen
       System.out.println("\n=== ADYEN API RESPONSE ===");
       System.out.println("Response JSON:");
       System.out.println(response.toJson());
       System.out.println("=======================\n");
       log.info("PaymentDetailsResponse {}", response);
       return ResponseEntity.ok().body(response);
   }


   // Step 14 - Handle Redirect 3DS2 during payment.
   @GetMapping("/handleShopperRedirect")
   public RedirectView redirect(@RequestParam(required = false) String payload,
           @RequestParam(required = false) String redirectResult) throws IOException, ApiException {
       var paymentDetailsRequest = new PaymentDetailsRequest();


       PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails();


       // Handle redirect result or payload
       if (redirectResult != null && !redirectResult.isEmpty()) {
           // For redirect, you are redirected to an Adyen domain to complete the 3DS2
           // challenge
           // After completing the 3DS2 challenge, you get the redirect result from Adyen
           // in the returnUrl
           // We then pass on the redirectResult
           paymentCompletionDetails.redirectResult(redirectResult);
       } else if (payload != null && !payload.isEmpty()) {
           paymentCompletionDetails.payload(payload);
       }


       paymentDetailsRequest.setDetails(paymentCompletionDetails);


       // Log the JSON request being sent to Adyen
       System.out.println("\n=== ADYEN API REQUEST ===");
       System.out.println("Operation: Payment Details (Redirect 3DS2)");
       System.out.println("Request JSON:");
       System.out.println(paymentDetailsRequest.toJson());
       System.out.println("========================\n");
       
       var paymentsDetailsResponse = paymentsApi.paymentsDetails(paymentDetailsRequest);
       
       // Log the JSON response from Adyen
       System.out.println("\n=== ADYEN API RESPONSE ===");
       System.out.println("Response JSON:");
       System.out.println(paymentsDetailsResponse.toJson());
       System.out.println("=======================\n");
       log.info("PaymentsDetailsResponse {}", paymentsDetailsResponse);


       // Handle response and redirect user accordingly
       var redirectURL = "http://localhost:8080/result/"; // Update your url here by replacing `http://localhost:8080`
                                                          // with where your application is hosted (if needed)
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
   // This endpoint performs a zero-auth payment (0 EUR) to tokenize the card
   @PostMapping("/api/subscription-create")
   public ResponseEntity<PaymentResponse> subscriptionCreate(@RequestBody PaymentRequest body)
           throws IOException, ApiException {
       var paymentRequest = new PaymentRequest();


       // Zero-auth payment: amount is 0
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


       // CRITICAL: Set shopperReference - required for token storage
       // This links the token to a specific customer
       String shopperReference = "shopper-" + System.currentTimeMillis();
       paymentRequest.setShopperReference(shopperReference);
       System.out.println("🔑 SHOPPER REFERENCE SET: " + shopperReference);


       // For tokenization, we need to flag this as a subscription
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


       // Log the JSON request being sent to Adyen
       System.out.println("\n=== ADYEN API REQUEST ===");
       System.out.println("Operation: Subscription Create (Zero-Auth)");
       System.out.println("Shopper Reference: " + shopperReference);
       System.out.println("Request JSON:");
       System.out.println(paymentRequest.toJson());
       System.out.println("========================\n");
       
       var response = paymentsApi.payments(paymentRequest, requestOptions);
       
       // Log the JSON response from Adyen
       System.out.println("\n=== ADYEN API RESPONSE ===");
       System.out.println("Response JSON:");
       System.out.println(response.toJson());
       System.out.println("=======================\n");
       log.info("SubscriptionCreateResponse: {}", response);

       // CRITICAL: Extract and store token from response IMMEDIATELY
       // Don't wait for webhook - store it now!
       if (response.getAdditionalData() != null && !response.getAdditionalData().isEmpty()) {
           String token = null;
           
           // Try to get the token from the response
           token = response.getAdditionalData().get("recurring.recurringDetailReference");
           if (token == null || token.isEmpty()) {
               token = response.getAdditionalData().get("tokenization.storedPaymentMethodId");
           }
           
           if (token != null && !token.isEmpty()) {
               // Store the token immediately
               WebhookController.storeToken(shopperReference, token);
               System.out.println("\n✅ TOKEN STORED FROM API RESPONSE");
               System.out.println("   Shopper: " + shopperReference);
               System.out.println("   Token: " + token);
               System.out.println("=====================================\n");
               log.info("✅ Token stored from subscription-create response - Shopper: {}, Token: {}", shopperReference, token);
           }
       }


       return ResponseEntity.ok().body(response);
   }


   // Step 3 - Tokenization: Implement /api/subscription-payment endpoint
   // This endpoint uses the stored token to charge the user
   @PostMapping("/api/subscription-payment")
   public ResponseEntity<Map<String, Object>> subscriptionPayment(@RequestBody Map<String, Object> body)
           throws IOException, ApiException {
       
       // Get the shopper reference from the request
       String shopperReference = (String) body.get("shopperReference");
      
       // Try to get the token from the request first (manual entry), then from storage
       String recurringDetailReference = (String) body.get("recurringDetailReference");
       if (recurringDetailReference == null) {
           // Fall back to looking it up from WebhookController
           recurringDetailReference = WebhookController.getToken(shopperReference);
       }
      
       if (recurringDetailReference == null) {
           log.warn("Token not found for shopper: {}", shopperReference);
           var errorResponse = new HashMap<String, Object>();
           errorResponse.put("error", "Token not found");
           errorResponse.put("message", "No recurring detail reference found");
           return ResponseEntity.status(400).body(errorResponse);
       }

       log.info("Processing subscription payment for shopper: {}, token: {}", shopperReference, recurringDetailReference);
       
       // Get payment amount from request or use default
       Object amountObj = body.get("amount");
       Long amountValue = amountObj != null ? ((Number) amountObj).longValue() : 500L;
      
       try {
           log.info("Processing subscription payment for shopper: {}, token: {}, amount: {}", 
               shopperReference, recurringDetailReference, amountValue);
           
           // Log the exact token being used
           System.out.println("\n=== TOKEN DEBUG INFO ===");
           System.out.println("Shopper Reference: " + shopperReference);
           System.out.println("Recurring Detail Reference (Token): " + recurringDetailReference);
           System.out.println("Token is null: " + (recurringDetailReference == null));
           System.out.println("Token is empty: " + (recurringDetailReference != null && recurringDetailReference.isEmpty()));
           System.out.println("Token length: " + (recurringDetailReference != null ? recurringDetailReference.length() : 0));
           System.out.println("========================\n");
           
           // Build the request payload as a Map (will be serialized to JSON)
           var requestBody = new HashMap<String, Object>();
           
           // Amount object
           var amountMap = new HashMap<String, Object>();
           amountMap.put("value", amountValue);
           amountMap.put("currency", "EUR");
           requestBody.put("amount", amountMap);
           
           // PaymentMethod object - the key fix!
           var paymentMethodMap = new HashMap<String, String>();
           paymentMethodMap.put("type", "card");
           paymentMethodMap.put("storedPaymentMethodId", recurringDetailReference);
           requestBody.put("paymentMethod", paymentMethodMap);
           
           // Reference and merchant account
           requestBody.put("reference", "SUBSC-" + UUID.randomUUID().toString());
           requestBody.put("merchantAccount", applicationConfiguration.getAdyenMerchantAccount());
           requestBody.put("returnUrl", "http://localhost:8080/handleShopperRedirect");
           
           // Shopper details
           requestBody.put("shopperReference", shopperReference);
           requestBody.put("shopperInteraction", "ContAuth");
           requestBody.put("recurringProcessingModel", "Subscription");
           
           // Create HTTP headers with API key
           var headers = new HttpHeaders();
           headers.setContentType(MediaType.APPLICATION_JSON);
           headers.set("x-API-key", applicationConfiguration.getAdyenApiKey());
           
           // Log the request
           var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
           var requestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
           System.out.println("\n=== ADYEN CHECKOUT API REQUEST (Direct HTTP) ===");
           System.out.println("Operation: Subscription Payment with Stored Card");
           System.out.println("Endpoint: POST https://checkout-test.adyen.com/v70/payments");
           System.out.println("Shopper: " + shopperReference);
           System.out.println("Token: " + recurringDetailReference);
           System.out.println("Amount: " + amountValue + " EUR");
           System.out.println("Request JSON:");
           System.out.println(requestJson);
           System.out.println("====================================================\n");
           
           try {
               // Create the HTTP entity with body and headers
               var entity = new org.springframework.http.HttpEntity<>(requestBody, headers);
               
               // Make the direct HTTP POST to Adyen's API
               var response = restTemplate.postForEntity(
                   "https://checkout-test.adyen.com/v70/payments",
                   entity,
                   Map.class
               );
               
               // Extract the response body
               var responseBody = response.getBody();
               log.info("Subscription payment processed with status: {}", response.getStatusCode());
               if (responseBody != null) {
                   log.info("Response: {}", responseBody);
               }
               
               // Log the response
               var responseJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseBody);
               System.out.println("\n=== ADYEN CHECKOUT API RESPONSE ===");
               System.out.println("Status Code: " + response.getStatusCodeValue());
               System.out.println("Response JSON:");
               System.out.println(responseJson);
               System.out.println("====================================\n");
               
               // Return the response as a map
               var resultMap = new HashMap<String, Object>(responseBody != null ? responseBody : new HashMap<>());
               log.info("Payment processed successfully");
               return ResponseEntity.ok(resultMap);
               
           } catch (org.springframework.web.client.HttpClientErrorException httpEx) {
               // Log HTTP errors from Adyen
               log.error("Adyen HTTP error: Status={}, Body={}", httpEx.getStatusCode(), httpEx.getResponseBodyAsString());
               
               System.out.println("\n╔══════════════════════════════════════════╗");
               System.out.println("║     ❌ ADYEN API ERROR RECEIVED          ║");
               System.out.println("╚══════════════════════════════════════════╝");
               System.out.println("Status Code: " + httpEx.getStatusCode());
               System.out.println("Error Response: " + httpEx.getResponseBodyAsString());
               System.out.println("\n🔍 DEBUG INFO:");
               System.out.println("   Shopper Reference: " + shopperReference);
               System.out.println("   Token Being Used: " + recurringDetailReference);
               System.out.println("   Token Length: " + (recurringDetailReference != null ? recurringDetailReference.length() : "null"));
               System.out.println("   Token Looks Like Adyen Format: " + (recurringDetailReference != null && 
                   (recurringDetailReference.contains("Bx") || recurringDetailReference.contains("Nx") || 
                    recurringDetailReference.length() > 20)));
               
               // Try to parse error details
               try {
                   var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                   var errorObj = mapper.readValue(httpEx.getResponseBodyAsString(), Map.class);
                   String errorMessage = (String) errorObj.get("message");
                   String errorCode = String.valueOf(errorObj.get("errorCode"));
                   
                   System.out.println("\n   Error Code: " + errorCode);
                   System.out.println("   Error Message: " + errorMessage);
                   
                   if ("800".equals(errorCode)) {
                       System.out.println("\n   ⚠️  ERROR 800: 'Contract not found'");
                       System.out.println("       This means:");
                       System.out.println("       1. Token format is invalid");
                       System.out.println("       2. Token doesn't exist in Adyen");
                       System.out.println("       3. Shopper reference doesn't match");
                       System.out.println("       4. Token expired or deleted");
                   }
               } catch (Exception e) {
                   // Could not parse error details
               }
               
               System.out.println("==========================================\n");
               
               var errorResponse = new HashMap<String, Object>();
               errorResponse.put("error", "Adyen API Error");
               errorResponse.put("statusCode", httpEx.getStatusCode().value());
               errorResponse.put("message", httpEx.getResponseBodyAsString());
               return ResponseEntity.status(httpEx.getStatusCode()).body(errorResponse);
               
           } catch (org.springframework.web.client.RestClientException restEx) {
               // Log REST errors
               log.error("Adyen REST client error: {}", restEx.getMessage(), restEx);
               
               System.out.println("\n=== ADYEN API REST ERROR ===");
               System.out.println("Error: " + restEx.getMessage());
               System.out.println("=============================\n");
               
               var errorResponse = new HashMap<String, Object>();
               errorResponse.put("error", "REST Client Error");
               errorResponse.put("message", restEx.getMessage());
               return ResponseEntity.status(500).body(errorResponse);
           }
           
       } catch (Exception e) {
           log.error("Error processing subscription payment", e);
           e.printStackTrace();
           
           var errorResponse = new HashMap<String, Object>();
           errorResponse.put("error", "Processing Error");
           errorResponse.put("message", e.getMessage());
           return ResponseEntity.status(500).body(errorResponse);
       }
   }


   // Step 4 - Tokenization: Implement /api/subscriptions-cancel endpoint
   // This endpoint disables/deletes the stored token
   @PostMapping("/api/subscriptions-cancel")
   public ResponseEntity<Map<String, Object>> subscriptionCancel(@RequestBody Map<String, Object> body) {
       String shopperReference = (String) body.get("shopperReference");
       String recurringDetailReference = (String) body.get("recurringDetailReference");


       if (recurringDetailReference == null) {
           var errorResponse = new HashMap<String, Object>();
           errorResponse.put("error", "Missing recurringDetailReference");
           return ResponseEntity.badRequest().body(errorResponse);
       }

       log.info("Cancelling subscription for shopper: {}, token: {}", shopperReference, recurringDetailReference);
      
       try {
           // Build the request payload to disable stored payment method
           var requestBody = new HashMap<String, Object>();
           requestBody.put("shopperReference", shopperReference);
           requestBody.put("recurringDetailReference", recurringDetailReference);
           requestBody.put("merchantAccount", applicationConfiguration.getAdyenMerchantAccount());
           
           // Create HTTP headers with API key
           var headers = new HttpHeaders();
           headers.setContentType(MediaType.APPLICATION_JSON);
           headers.set("x-API-key", applicationConfiguration.getAdyenApiKey());
           
           // Log the request
           var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
           var requestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
           
           System.out.println("\n╔════════════════════════════════════════════╗");
           System.out.println("║   🗑️  CANCEL SUBSCRIPTION REQUEST          ║");
           System.out.println("╚════════════════════════════════════════════╝");
           System.out.println("Endpoint: POST https://pal-test.adyen.com/pal/servlet/Recurring/v1/disable");
           System.out.println("Shopper Reference: " + shopperReference);
           System.out.println("Recurring Detail Reference: " + recurringDetailReference);
           System.out.println("Request JSON:");
           System.out.println(requestJson);
           System.out.println("==========================================\n");
           
           try {
               // Create the HTTP entity with body and headers
               var entity = new org.springframework.http.HttpEntity<>(requestBody, headers);
               
               // Make the direct HTTP POST to Adyen's Recurring API
               var response = restTemplate.postForEntity(
                   "https://pal-test.adyen.com/pal/servlet/Recurring/v1/disable",
                   entity,
                   Map.class
               );
               
               // Extract the response body
               var responseBody = response.getBody();
               log.info("Subscription cancel processed with status: {}", response.getStatusCode());
               
               // Log the response
               var responseJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseBody);
               System.out.println("\n╔════════════════════════════════════════════╗");
               System.out.println("║   ✅ CANCEL SUBSCRIPTION RESPONSE          ║");
               System.out.println("╚════════════════════════════════════════════╝");
               System.out.println("Status Code: " + response.getStatusCodeValue());
               System.out.println("Response JSON:");
               System.out.println(responseJson);
               System.out.println("==========================================\n");
               
               // Remove token from local storage
               WebhookController.removeToken(shopperReference);
               
               // Return the response
               var resultMap = new HashMap<String, Object>(responseBody != null ? responseBody : new HashMap<>());
               log.info("Subscription cancelled successfully");
               return ResponseEntity.ok(resultMap);
               
           } catch (org.springframework.web.client.HttpClientErrorException httpEx) {
               // Log HTTP errors from Adyen
               log.error("Adyen HTTP error: Status={}, Body={}", httpEx.getStatusCode(), httpEx.getResponseBodyAsString());
               
               System.out.println("\n╔════════════════════════════════════════════╗");
               System.out.println("║     ❌ ADYEN API ERROR                     ║");
               System.out.println("╚════════════════════════════════════════════╝");
               System.out.println("Status Code: " + httpEx.getStatusCode());
               System.out.println("Error Response: " + httpEx.getResponseBodyAsString());
               System.out.println("==========================================\n");
               
               var errorResponse = new HashMap<String, Object>();
               errorResponse.put("error", "Adyen API Error");
               errorResponse.put("statusCode", httpEx.getStatusCode().value());
               errorResponse.put("message", httpEx.getResponseBodyAsString());
               return ResponseEntity.status(httpEx.getStatusCode()).body(errorResponse);
               
           } catch (org.springframework.web.client.RestClientException restEx) {
               // Log REST errors
               log.error("Adyen REST client error: {}", restEx.getMessage(), restEx);
               
               System.out.println("\n=== ADYEN API REST ERROR ===");
               System.out.println("Error: " + restEx.getMessage());
               System.out.println("=============================\n");
               
               var errorResponse = new HashMap<String, Object>();
               errorResponse.put("error", "REST Client Error");
               errorResponse.put("message", restEx.getMessage());
               return ResponseEntity.status(500).body(errorResponse);
           }
           
       } catch (Exception e) {
           log.error("Error processing subscription cancel", e);
           e.printStackTrace();
           
           System.out.println("\n=== ERROR ===");
           System.out.println("Exception: " + e.getMessage());
           System.out.println("============\n");
           
           var errorResponse = new HashMap<String, Object>();
           errorResponse.put("error", "Processing Error");
           errorResponse.put("message", e.getMessage());
           return ResponseEntity.status(500).body(errorResponse);
       }
   }


   // === PREAUTHORISATION MODULE ===


   // Step 1 - Preauthorisation: Implement /api/preauthorisation endpoint
   // This endpoint preauthorizes a payment (holds the amount but doesn't capture)
   @PostMapping("/api/preauthorisation")
   public ResponseEntity<PaymentResponse> preauthorisation(@RequestBody PaymentRequest body)
           throws IOException, ApiException {
       var paymentRequest = new PaymentRequest();


       // Preauth amount: 10 EUR
       var amount = new Amount()
               .currency("EUR")
               .value(1000L); // 10.00 EUR
       paymentRequest.setAmount(amount);
       paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
       paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);


       paymentRequest.setPaymentMethod(body.getPaymentMethod());


       var orderRef = UUID.randomUUID().toString();
       paymentRequest.setReference(orderRef);
       paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");


       // Set capture delay or manual capture to enable preauthorisation
       // This can be set per merchant account or per payment request
       paymentRequest.setCaptureDelayHours(7); // Manual capture window: 7 days


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


       // Log the JSON request being sent to Adyen
       System.out.println("\n=== ADYEN API REQUEST ===");
       System.out.println("Operation: Preauthorisation");
       System.out.println("Request JSON:");
       System.out.println(paymentRequest.toJson());
       System.out.println("========================\n");
       
       var response = paymentsApi.payments(paymentRequest, requestOptions);
       
       // Log the JSON response from Adyen
       System.out.println("\n=== ADYEN API RESPONSE ===");
       System.out.println("Response JSON:");
       System.out.println(response.toJson());
       System.out.println("=======================\n");
       log.info("PreauthorisationResponse: {}", response);


       return ResponseEntity.ok().body(response);
   }


   // Step 2 - Preauthorisation: Implement /api/modify-amount endpoint
   // This endpoint adjusts the amount of a preauthorized payment
   @PostMapping("/api/modify-amount")
   public ResponseEntity<Map<String, Object>> modifyAmount(@RequestBody Map<String, Object> body)
           throws IOException, ApiException {
       String pspReference = (String) body.get("pspReference");
       Long modifyAmount = ((Number) body.get("modifyAmount")).longValue();


       if (pspReference == null) {
           log.error("pspReference is required");
           return ResponseEntity.badRequest().build();
       }


       log.info("Adjusting authorization amount for pspReference: {}, new amount: {}", pspReference, modifyAmount);
      
       try {
           var modifyRequest = new AdjustAuthorisationRequest();
           modifyRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
           modifyRequest.setOriginalReference(pspReference);
           
           var modifyRequestAmount = new com.adyen.model.payment.Amount();
           modifyRequestAmount.setCurrency("EUR");
           modifyRequestAmount.setValue(modifyAmount);
           modifyRequest.setModificationAmount(modifyRequestAmount);
           
           // Log the JSON request being sent to Adyen
           System.out.println("\n=== ADYEN API REQUEST ===");
           System.out.println("Operation: AdjustAuthorisation");
           System.out.println("Request JSON:");
           System.out.println(modifyRequest.toJson());
           System.out.println("========================\n");
           
           var response = paymentApi.adjustAuthorisation(modifyRequest);
           
           // Log the JSON response from Adyen
           System.out.println("\n=== ADYEN API RESPONSE ===");
           System.out.println("Response JSON:");
           System.out.println(response.toJson());
           System.out.println("=======================\n");
           log.info("AdjustAuthorisationResponse: {}", response);
           
           var result = new HashMap<String, Object>();
           result.put("status", "success");
           result.put("message", "Amount adjustment request submitted");
           result.put("pspReference", pspReference);
           result.put("modifyAmount", modifyAmount);
           result.put("modificationPspReference", response.getPspReference());
           
           return ResponseEntity.ok().body(result);
       } catch (ApiException e) {
           log.error("Adyen API error adjusting authorization: status={}, responseBody={}", e.getStatusCode(), e.getResponseBody(), e);
           var error = new HashMap<String, Object>();
           error.put("status", "error");
           error.put("message", e.getMessage());
           error.put("statusCode", e.getStatusCode());
           error.put("responseBody", e.getResponseBody());
           return ResponseEntity.status(500).body(error);
       } catch (Exception e) {
           log.error("Error adjusting authorization", e);
           var error = new HashMap<String, Object>();
           error.put("status", "error");
           error.put("message", e.getMessage());
           return ResponseEntity.status(500).body(error);
       }
   }


   // Step 3 - Preauthorisation: Implement /api/capture endpoint
   // This endpoint captures a preauthorized payment
   @PostMapping("/api/capture")
   public ResponseEntity<Map<String, Object>> capture(@RequestBody Map<String, Object> body)
           throws IOException, ApiException {
       String pspReference = (String) body.get("pspReference");
       Long captureAmount = ((Number) body.get("captureAmount")).longValue();


       if (pspReference == null) {
           log.error("pspReference is required");
           return ResponseEntity.badRequest().build();
       }


       log.info("Capturing payment with pspReference: {}, capture amount: {}", pspReference, captureAmount);
      
       try {
           var captureRequest = new CaptureRequest();
           captureRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
           captureRequest.setOriginalReference(pspReference);
           
           var captureRequestAmount = new com.adyen.model.payment.Amount();
           captureRequestAmount.setCurrency("EUR");
           captureRequestAmount.setValue(captureAmount);
           captureRequest.setModificationAmount(captureRequestAmount);
           
           // Log the JSON request being sent to Adyen
           System.out.println("\n=== ADYEN API REQUEST ===");
           System.out.println("Operation: Capture");
           System.out.println("Request JSON:");
           System.out.println(captureRequest.toJson());
           System.out.println("========================\n");
           
           var response = paymentApi.capture(captureRequest);
           
           // Log the JSON response from Adyen
           System.out.println("\n=== ADYEN API RESPONSE ===");
           System.out.println("Response JSON:");
           System.out.println(response.toJson());
           System.out.println("=======================\n");
           
           var result = new HashMap<String, Object>();
           result.put("status", "success");
           result.put("message", "Capture request submitted");
           result.put("pspReference", pspReference);
           result.put("captureAmount", captureAmount);
           result.put("modificationPspReference", response.getPspReference());
           
           return ResponseEntity.ok().body(result);
       } catch (ApiException e) {
           log.error("Adyen API error capturing payment: status={}, responseBody={}", e.getStatusCode(), e.getResponseBody(), e);
           var error = new HashMap<String, Object>();
           error.put("status", "error");
           error.put("message", e.getMessage());
           error.put("statusCode", e.getStatusCode());
           error.put("responseBody", e.getResponseBody());
           return ResponseEntity.status(500).body(error);
       } catch (Exception e) {
           log.error("Error capturing payment", e);
           var error = new HashMap<String, Object>();
           error.put("status", "error");
           error.put("message", e.getMessage());
           return ResponseEntity.status(500).body(error);
       }
   }


   // Step 4 - Preauthorisation: Implement /api/cancel endpoint
   // This endpoint cancels a preauthorized payment
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
           
           // Log the JSON request being sent to Adyen
           System.out.println("\n=== ADYEN API REQUEST ===");
           System.out.println("Operation: Cancel");
           System.out.println("Request JSON:");
           System.out.println(cancelRequest.toJson());
           System.out.println("========================\n");
           
           var response = paymentApi.cancel(cancelRequest);
           
           // Log the JSON response from Adyen
           System.out.println("\n=== ADYEN API RESPONSE ===");
           System.out.println("Response JSON:");
           System.out.println(response.toJson());
           System.out.println("=======================\n");
           log.info("CancelResponse: {}", response);
           
           var result = new HashMap<String, Object>();
           result.put("status", "success");
           result.put("message", "Cancellation request submitted");
           result.put("pspReference", pspReference);
           result.put("modificationPspReference", response.getPspReference());
           
           return ResponseEntity.ok().body(result);
       } catch (ApiException e) {
           log.error("Adyen API error cancelling payment: status={}, responseBody={}", e.getStatusCode(), e.getResponseBody(), e);
           var error = new HashMap<String, Object>();
           error.put("status", "error");
           error.put("message", e.getMessage());
           error.put("statusCode", e.getStatusCode());
           error.put("responseBody", e.getResponseBody());
           return ResponseEntity.status(500).body(error);
       } catch (Exception e) {
           log.error("Error cancelling payment", e);
           var error = new HashMap<String, Object>();
           error.put("status", "error");
           error.put("message", e.getMessage());
           return ResponseEntity.status(500).body(error);
       }
   }


   // Step 5 - Preauthorisation: Implement /api/refund endpoint
   // This endpoint refunds a captured payment
   @PostMapping("/api/refund")
   public ResponseEntity<Map<String, Object>> refund(@RequestBody Map<String, Object> body)
           throws IOException, ApiException {
       String pspReference = (String) body.get("pspReference");
       Long refundAmount = ((Number) body.get("refundAmount")).longValue();


       if (pspReference == null) {
           log.error("pspReference is required");
           return ResponseEntity.badRequest().build();
       }


       log.info("Refunding payment with pspReference: {}, refund amount: {}", pspReference, refundAmount);
      
       try {
           var refundRequest = new RefundRequest();
           refundRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
           refundRequest.setOriginalReference(pspReference);
           
           var refundRequestAmount = new com.adyen.model.payment.Amount();
           refundRequestAmount.setCurrency("EUR");
           refundRequestAmount.setValue(refundAmount);
           refundRequest.setModificationAmount(refundRequestAmount);
           
           // Log the JSON request being sent to Adyen
           System.out.println("\n=== ADYEN API REQUEST ===");
           System.out.println("Operation: Refund");
           System.out.println("Request JSON:");
           System.out.println(refundRequest.toJson());
           System.out.println("========================\n");
           
           var response = paymentApi.refund(refundRequest);
           
           // Log the JSON response from Adyen
           System.out.println("\n=== ADYEN API RESPONSE ===");
           System.out.println("Response JSON:");
           System.out.println(response.toJson());
           System.out.println("=======================\n");
           log.info("RefundResponse: {}", response);
           
           var result = new HashMap<String, Object>();
           result.put("status", "success");
           result.put("message", "Refund request submitted");
           result.put("pspReference", pspReference);
           result.put("refundAmount", refundAmount);
           result.put("modificationPspReference", response.getPspReference());
           
           return ResponseEntity.ok().body(result);
       } catch (ApiException e) {
           log.error("Adyen API error refunding payment: status={}, responseBody={}", e.getStatusCode(), e.getResponseBody(), e);
           var error = new HashMap<String, Object>();
           error.put("status", "error");
           error.put("message", e.getMessage());
           error.put("statusCode", e.getStatusCode());
           error.put("responseBody", e.getResponseBody());
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
       System.out.println("\n╔════════════════════════════════════════════╗");
       System.out.println("║        📊 STORED TOKENS DEBUG INFO        ║");
       System.out.println("╚════════════════════════════════════════════╝");
       
       var allTokens = WebhookController.getAllTokens();
       
       System.out.println("Total tokens stored: " + allTokens.size());
       if (allTokens.isEmpty()) {
           System.out.println("❌ NO TOKENS STORED!");
       } else {
           System.out.println("\n🔑 Stored Tokens:");
           allTokens.forEach((shopper, token) -> {
               System.out.println("   Shopper: " + shopper);
               System.out.println("   Token: " + token);
               System.out.println("   Token Length: " + token.length());
               System.out.println();
           });
       }
       System.out.println("===========================================\n");
       
       var response = new HashMap<String, Object>();
       response.put("totalTokens", allTokens.size());
       response.put("tokens", allTokens);
       return ResponseEntity.ok(response);
   }


}
