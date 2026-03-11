const clientKey = document.getElementById("clientKey").innerHTML;

// In-memory storage for subscriptions (in production, this would be a database)
let subscriptions = JSON.parse(localStorage.getItem("subscriptions")) || {};
let receivedTokens = {}; // Store tokens from RECURRING_CONTRACT webhooks

function getAdyenWeb() {
    return new Promise((resolve) => {
        if (window.AdyenWeb) {
            resolve(window.AdyenWeb);
            return;
        }

        const checkInterval = setInterval(() => {
            if (window.AdyenWeb) {
                clearInterval(checkInterval);
                resolve(window.AdyenWeb);
            }
        }, 100);
    });
}

// Toast notification system
function showToast(message, type = 'info') {
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;

    container.appendChild(toast);

    // Auto-remove after 4 seconds
    setTimeout(() => {
        toast.style.animation = 'slideOut 0.3s ease forwards';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function logEvent(message, data = null) {
    const logElement = document.getElementById("subscription-log");
    const timestamp = new Date().toLocaleTimeString();
    
    // Create structured log entry
    let logHtml = `<div class="subscription-log-entry">`;
    logHtml += `<div class="subscription-log-timestamp">[${timestamp}]</div>`;
    logHtml += `<div class="subscription-log-message">${message}</div>`;
    
    if (data) {
        logHtml += `<pre style="margin-top: 8px; color: #a0a0a0; font-size: 11px;">${JSON.stringify(data, null, 2)}</pre>`;
    }
    logHtml += `</div>`;

    // Add to beginning of log
    logElement.innerHTML = logHtml + logElement.innerHTML;
    console.log(`[${timestamp}] ${message}`, data);
}

function displaySubscriptions() {
    const list = document.getElementById("subscriptions-list");
    const subIds = Object.keys(subscriptions);
    
    if (subIds.length === 0) {
        list.innerHTML = '<p class="subscription-empty">No subscriptions yet. Create one to get started.</p>';
        return;
    }

    let html = '<div class="subscriptions-table">';
    subIds.forEach(shopperRef => {
        const sub = subscriptions[shopperRef];
        const tokenDisplay = sub.token 
            ? `<code>${sub.token.substring(0, 20)}...</code>` 
            : `<span style="color: #f39c12;">Awaiting webhook...</span>`;
        const statusClass = sub.tokenReceived ? 'ready' : 'pending';
        const statusText = sub.tokenReceived ? 'Ready' : 'Pending';
        
        html += `
            <div class="subscription-card">
                <div class="subscription-card-header">
                    <strong>${shopperRef}</strong>
                    <span class="subscription-card-date">${new Date(sub.createdAt).toLocaleDateString()}</span>
                </div>
                <div class="subscription-card-body">
                    <p><strong>Token:</strong> ${tokenDisplay}</p>
                    <p><strong>Status:</strong> <span class="subscription-card-status ${statusClass}">${statusText}</span></p>
                </div>
            </div>
        `;
    });
    html += '</div>';
    list.innerHTML = html;
}

async function startCheckout() {
    try {
        const AdyenWeb = await getAdyenWeb();
        const { AdyenCheckout, Dropin } = AdyenWeb;

        // Fetch payment methods for subscription creation
        const paymentMethodsResponse = await fetch("/api/paymentMethods", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            }
        }).then(response => response.json());

        const configuration = {
            paymentMethodsResponse: paymentMethodsResponse,
            clientKey,
            locale: "en_US",
            countryCode: 'NL',
            environment: "test",
            showPayButton: true,
            translations: {
                'en-US': {
                    'creditCard.securityCode.label': 'CVV/CVC'
                }
            },
            // Handle subscription creation
            onSubmit: async (state, component, actions) => {
                console.info("onSubmit for subscription creation", state, component, actions);
                try {
                    if (state.isValid) {
                        // Generate a shopper reference (in production, this would be from your system)
                        const shopperRef = `shopper-${Date.now()}`;
                        document.getElementById("shopperReference").value = shopperRef;

                        const { resultCode } = await fetch("/api/subscription-create", {
                            method: "POST",
                            body: state.data ? JSON.stringify(state.data) : "",
                            headers: {
                                "Content-Type": "application/json",
                            }
                        }).then(response => response.json());

                        logEvent("Subscription creation initiated", { resultCode, shopperRef });

                        if (!resultCode) {
                            console.warn("reject");
                            actions.reject();
                        } else {
                            actions.resolve({
                                resultCode
                            });
                        }
                    }
                } catch (error) {
                    console.error(error);
                    logEvent("Error during subscription creation", error);
                    actions.reject();
                }
            },
            onPaymentCompleted: (result, component) => {
                console.info("onPaymentCompleted", result, component);
                handleSubscriptionCompleted(result, component);
            },
            onPaymentFailed: (result, component) => {
                console.info("onPaymentFailed", result, component);
                logEvent("Subscription creation failed", result);
                showToast("Subscription creation failed. Please try again.", "error");
            },
            onError: (error, component) => {
                console.error("onError", error.name, error.message, error.stack, component);
                logEvent("Error during subscription creation", {
                    name: error.name,
                    message: error.message
                });
                showToast("An error occurred. Check console for details.", "error");
            }
        };

        // Optional configuration for cards
        const paymentMethodsConfiguration = {
            card: {
                showBrandIcon: true,
                hasHolderName: true,
                holderNameRequired: true,
                name: "Credit or debit card",
                amount: {
                    value: 0,
                    currency: "EUR",
                },
                placeholders: {
                    cardNumber: '1234 5678 9012 3456',
                    expiryDate: 'MM/YY',
                    securityCodeThreeDigits: '123',
                    securityCodeFourDigits: '1234',
                    holderName: 'Developer Relations Team'
                }
            }
        };

        // Start the AdyenCheckout and mount the element onto the `payment`-div.
        const adyenCheckout = await AdyenCheckout(configuration);
        const dropin = new Dropin(adyenCheckout, { paymentMethodsConfiguration: paymentMethodsConfiguration }).mount(document.getElementById("payment"));
        
        logEvent("Checkout initialized successfully", { environment: "test" });
    } catch (error) {
        console.error(error);
        logEvent("Error initializing subscription checkout", error);
        showToast("Error occurred. Look at console for details.", "error");
    }
}

function handleSubscriptionCompleted(response) {
    const shopperRef = document.getElementById("shopperReference").value;
    
    logEvent("Subscription created successfully", response);
    showToast("Subscription created! Waiting for webhook confirmation.", "success");

    // The real token will come from the RECURRING_CONTRACT webhook
    // DO NOT use fake tokens like `token-${Date.now()}`
    // Instead, wait for the webhook to arrive and store the real token
    if (shopperRef && response.resultCode === "Authorised") {
        // Store subscription metadata (without token - token comes from webhook)
        subscriptions[shopperRef] = {
            createdAt: new Date().toISOString(),
            resultCode: response.resultCode,
            status: "AWAITING_WEBHOOK",  // Mark as waiting for the RECURRING_CONTRACT webhook
            tokenReceived: false
        };
        localStorage.setItem("subscriptions", JSON.stringify(subscriptions));
        displaySubscriptions();

        logEvent("Subscription metadata stored (token pending from webhook)", subscriptions[shopperRef]);

        // Show message instructing user to watch for webhook callback
        setTimeout(() => {
            showToast("Webhook will contain the actual token. Use it in the payment section.", "info");
        }, 1500);
    } else if (shopperRef) {
        logEvent("Subscription creation incomplete", {
            resultCode: response.resultCode,
            expectedResult: "Authorised"
        });
        showToast(`Subscription creation failed: ${response.resultCode}`, "error");
    }
}

// Handle charge subscription button
document.getElementById("chargeBtn").addEventListener("click", async () => {
    const shopperReference = document.getElementById("shopperReference").value;
    const recurringToken = document.getElementById("recurringToken").value;
    const paymentAmount = parseFloat(document.getElementById("paymentAmount").value) * 100; // Convert to minor units

    if (!shopperReference) {
        showToast("Please enter a shopper reference", "warning");
        return;
    }

    if (!recurringToken) {
        showToast("Please enter the recurring detail reference (token)", "warning");
        return;
    }

    try {
        logEvent("Processing recurring payment", { shopperReference, amount: paymentAmount });
        showToast("Processing payment...", "info");

        const response = await fetch("/api/subscription-payment", {
            method: "POST",
            body: JSON.stringify({
                shopperReference: shopperReference,
                recurringDetailReference: recurringToken,
                amount: paymentAmount
            }),
            headers: {
                "Content-Type": "application/json",
            }
        }).then(response => response.json());

        logEvent("Payment response received", response);

        if (response.resultCode === "Authorised") {
            showToast(`Payment of EUR ${(paymentAmount / 100).toFixed(2)} successful!`, "success");
        } else if (response.resultCode === "Pending") {
            showToast(`Payment pending for shopper ${shopperReference}`, "warning");
        } else {
            showToast(`Payment declined: ${response.resultCode || 'Unknown error'}`, "error");
        }
    } catch (error) {
        console.error(error);
        logEvent("Error making recurring payment", error);
        showToast("Error making recurring payment. Check console for details.", "error");
    }
});

// Handle cancel subscription button
document.getElementById("cancelBtn").addEventListener("click", async () => {
    const shopperRef = document.getElementById("cancelShopperRef").value;
    const recurringRef = document.getElementById("cancelRecurringRef").value;

    if (!shopperRef || !recurringRef) {
        showToast("Please enter both shopper reference and recurring detail reference", "warning");
        return;
    }

    try {
        logEvent("Cancelling subscription", { shopperRef, recurringRef });
        showToast("Cancelling subscription...", "info");

        const response = await fetch("/api/subscriptions-cancel", {
            method: "POST",
            body: JSON.stringify({
                shopperReference: shopperRef,
                recurringDetailReference: recurringRef
            }),
            headers: {
                "Content-Type": "application/json",
            }
        }).then(response => response.json());

        logEvent("Cancellation response received", response);

        if (response.status === "cancelled") {
            showToast(`Subscription cancelled for ${shopperRef}`, "success");
            delete subscriptions[shopperRef];
            localStorage.setItem("subscriptions", JSON.stringify(subscriptions));
            displaySubscriptions();
        } else {
            showToast(`Error: ${response.message || 'Unknown error'}`, "error");
        }
    } catch (error) {
        console.error(error);
        logEvent("Error cancelling subscription", error);
        showToast("Error cancelling subscription. Check console for details.", "error");
    }
});

// Initialize on page load
displaySubscriptions();
startCheckout();

