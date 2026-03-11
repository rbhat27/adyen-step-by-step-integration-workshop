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

function logEvent(message, data = null) {
    const logElement = document.getElementById("subscription-log");
    const timestamp = new Date().toLocaleTimeString();
    let logMessage = `[${timestamp}] ${message}`;
    if (data) {
        logMessage += `\n${JSON.stringify(data, null, 2)}`;
    }
    logElement.innerText = logMessage + "\n---\n" + logElement.innerText;
    console.log(logMessage, data);
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
            : `<span style="color: orange;">⏳ Awaiting webhook...</span>`;
        const statusDisplay = sub.tokenReceived ? "✅ Ready" : "⏳ Pending";
        
        html += `
            <div class="subscription-card">
                <div class="subscription-card-header">
                    <strong>${shopperRef}</strong>
                    <span class="subscription-card-date">${new Date(sub.createdAt).toLocaleDateString()}</span>
                </div>
                <div class="subscription-card-body">
                    <p><strong>Token:</strong> ${tokenDisplay}</p>
                    <p><strong>Status:</strong> ${statusDisplay}</p>
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
            },
            onError: (error, component) => {
                console.error("onError", error.name, error.message, error.stack, component);
                logEvent("Error during subscription creation", {
                    name: error.name,
                    message: error.message
                });
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
    } catch (error) {
        console.error(error);
        logEvent("Error initializing subscription checkout", error);
        alert("Error occurred. Look at console for details.");
    }
}

function handleSubscriptionCompleted(response) {
    const shopperRef = document.getElementById("shopperReference").value;
    
    logEvent("Subscription created successfully", response);

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
            alert(`Subscription created for ${shopperRef}. \n\nWaiting for confirmation webhook...  \n\nThe RECURRING_CONTRACT webhook will contain the actual token. You can copy it from the server logs or debug panel once it arrives.\n\nUse the token in the "Make a Recurring Payment" section to charge this subscription.`);
        }, 500);
    } else if (shopperRef) {
        logEvent("Subscription creation incomplete", {
            resultCode: response.resultCode,
            expectedResut: "Authorised"
        });
        alert(`Subscription creation failed with result: ${response.resultCode}`);
    }
}

// Handle charge subscription button
document.getElementById("chargeBtn").addEventListener("click", async () => {
    const shopperReference = document.getElementById("shopperReference").value;
    const recurringToken = document.getElementById("recurringToken").value;
    const paymentAmount = parseFloat(document.getElementById("paymentAmount").value) * 100; // Convert to minor units

    if (!shopperReference) {
        alert("Please enter a shopper reference");
        return;
    }

    if (!recurringToken) {
        alert("Please enter the recurring detail reference (token). You can get this from the RECURRING_CONTRACT webhook log below.");
        return;
    }

    try {
        logEvent("Making recurring payment", { shopperReference, recurringToken, amount: paymentAmount });

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

        logEvent("Recurring payment response", response);

        if (response.resultCode === "Authorised") {
            alert(`Payment of EUR ${(paymentAmount / 100).toFixed(2)} successful!`);
        } else if (response.resultCode === "Pending") {
            alert(`Payment pending for shopper ${shopperReference}`);
        } else {
            alert(`Payment declined: ${response.resultCode}`);
        }
    } catch (error) {
        console.error(error);
        logEvent("Error making recurring payment", error);
        alert("Error making recurring payment. Check console for details.");
    }
});

// Handle cancel subscription button
document.getElementById("cancelBtn").addEventListener("click", async () => {
    const shopperRef = document.getElementById("cancelShopperRef").value;
    const recurringRef = document.getElementById("cancelRecurringRef").value;

    if (!shopperRef || !recurringRef) {
        alert("Please enter both shopper reference and recurring detail reference");
        return;
    }

    try {
        logEvent("Cancelling subscription", { shopperRef, recurringRef });

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

        logEvent("Subscription cancellation response", response);

        if (response.status === "cancelled") {
            alert(`Subscription cancelled for ${shopperRef}`);
            delete subscriptions[shopperRef];
            localStorage.setItem("subscriptions", JSON.stringify(subscriptions));
            displaySubscriptions();
        } else {
            alert(`Error: ${response.message}`);
        }
    } catch (error) {
        console.error(error);
        logEvent("Error cancelling subscription", error);
        alert("Error cancelling subscription. Check console for details.");
    }
});

// Initialize on page load
displaySubscriptions();
startCheckout();
