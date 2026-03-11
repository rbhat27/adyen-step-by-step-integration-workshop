const clientKey = document.getElementById("clientKey").innerHTML;

const state = {
    lastPspReference: null
};

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

function setLog(message, payload) {
    const log = document.getElementById("preauth-log");
    if (!log) {
        return;
    }

    const timestamp = new Date().toLocaleTimeString();
    
    // Create structured log entry
    let logHtml = `<div class="subscription-log-entry">`;
    logHtml += `<div class="subscription-log-timestamp">[${timestamp}]</div>`;
    logHtml += `<div class="subscription-log-message">${message}</div>`;
    
    if (payload) {
        logHtml += `<pre style="margin-top: 8px; color: #a0a0a0; font-size: 11px;">${JSON.stringify(payload, null, 2)}</pre>`;
    }
    logHtml += `</div>`;

    // Add to beginning of log
    log.innerHTML = logHtml + log.innerHTML;
    console.log(`[${timestamp}] ${message}`, payload);
}

function updatePspReference(pspReference) {
    if (!pspReference) {
        return;
    }
    state.lastPspReference = pspReference;
    const input = document.getElementById("pspReference");
    if (input && !input.value) {
        input.value = pspReference;
    }
    setLog("Stored PSP reference", { pspReference });
    showToast(`PSP Reference stored: ${pspReference}`, "success");
}

async function runApi(path, body) {
    try {
        const response = await fetch(path, {
            method: "POST",
            body: JSON.stringify(body || {}),
            headers: {
                "Content-Type": "application/json"
            }
        });

        const json = await response.json();
        setLog(`${path} response`, json);
        
        if (json && json.pspReference) {
            updatePspReference(json.pspReference);
        }

        // Show toast based on response
        if (json && json.status === "success") {
            showToast(json.message || "Operation successful", "success");
        } else if (json && json.error) {
            showToast(json.message || "Operation failed", "error");
        }

        return json;
    } catch (error) {
        console.error(error);
        setLog("API Error", { message: error.message });
        showToast("Error: " + error.message, "error");
        throw error;
    }
}

async function startPreauthCheckout() {
    try {
        const AdyenWeb = await getAdyenWeb();
        const { AdyenCheckout, Dropin } = AdyenWeb;

        const paymentMethodsResponse = await fetch("/api/paymentMethods", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            }
        }).then(response => response.json());

        const configuration = {
            paymentMethodsResponse: paymentMethodsResponse,
            clientKey,
            locale: "en_US",
            countryCode: "NL",
            environment: "test",
            showPayButton: true,
            translations: {
                "en-US": {
                    "creditCard.securityCode.label": "CVV/CVC"
                }
            },
            onSubmit: async (stateData, component, actions) => {
                try {
                    if (!stateData.isValid) {
                        actions.reject();
                        return;
                    }

                    const response = await fetch("/api/preauthorisation", {
                        method: "POST",
                        body: stateData.data ? JSON.stringify(stateData.data) : "",
                        headers: {
                            "Content-Type": "application/json"
                        }
                    }).then(response => response.json());

                    setLog("/api/preauthorisation response", response);
                    if (response && response.pspReference) {
                        updatePspReference(response.pspReference);
                    }

                    if (!response.resultCode) {
                        showToast("Preauthorisation failed", "error");
                        actions.reject();
                        return;
                    }

                    showToast(`Preauthorisation ${response.resultCode}`, response.resultCode === "Authorised" ? "success" : "warning");
                    actions.resolve({
                        resultCode: response.resultCode,
                        action: response.action,
                        order: response.order
                    });
                } catch (error) {
                    console.error(error);
                    setLog("Preauthorisation error", { message: error.message });
                    showToast("Preauthorisation error", "error");
                    actions.reject();
                }
            },
            onPaymentCompleted: (result, component) => {
                console.info("onPaymentCompleted", result, component);
                setLog("Payment completed", result);
                showToast("Payment completed successfully", "success");
            },
            onPaymentFailed: (result, component) => {
                console.info("onPaymentFailed", result, component);
                setLog("Payment failed", result);
                showToast("Payment failed", "error");
            },
            onError: (error, component) => {
                console.error("onError", error.name, error.message, error.stack, component);
                setLog("Checkout error", { name: error.name, message: error.message });
                showToast("Checkout error: " + error.message, "error");
            },
            onAdditionalDetails: async (stateData, component, actions) => {
                try {
                    const response = await fetch("/api/payments/details", {
                        method: "POST",
                        body: stateData.data ? JSON.stringify(stateData.data) : "",
                        headers: {
                            "Content-Type": "application/json"
                        }
                    }).then(response => response.json());

                    setLog("/api/payments/details response", response);
                    if (!response.resultCode) {
                        showToast("Details submission failed", "error");
                        actions.reject();
                        return;
                    }

                    showToast(`Details processed: ${response.resultCode}`, "info");
                    actions.resolve({ resultCode: response.resultCode });
                } catch (error) {
                    console.error(error);
                    setLog("Details error", { message: error.message });
                    showToast("Details error", "error");
                    actions.reject();
                }
            }
        };

        const paymentMethodsConfiguration = {
            card: {
                showBrandIcon: true,
                hasHolderName: true,
                holderNameRequired: true,
                name: "Credit or debit card",
                amount: {
                    value: 1000,
                    currency: "EUR"
                },
                placeholders: {
                    cardNumber: "1234 5678 9012 3456",
                    expiryDate: "MM/YY",
                    securityCodeThreeDigits: "123",
                    securityCodeFourDigits: "1234",
                    holderName: "Developer Relations Team"
                }
            }
        };

        const adyenCheckout = await AdyenCheckout(configuration);
        new Dropin(adyenCheckout, { paymentMethodsConfiguration }).mount(document.getElementById("payment"));
        
        setLog("Checkout initialized", { environment: "test" });
    } catch (error) {
        console.error(error);
        setLog("Checkout init error", { message: error.message });
        showToast("Checkout initialization error", "error");
    }
}

function getInputValue(id) {
    const input = document.getElementById(id);
    if (!input) {
        return "";
    }
    return input.value.trim();
}

function getAmountValue(id) {
    const raw = getInputValue(id);
    if (!raw) {
        return null;
    }
    const parsed = Number(raw);
    return Number.isNaN(parsed) ? null : parsed;
}

function initButtons() {
    const useLastBtn = document.getElementById("use-last-psp");
    if (useLastBtn) {
        useLastBtn.addEventListener("click", () => {
            if (!state.lastPspReference) {
                setLog("No PSP reference available yet");
                showToast("No PSP reference available yet", "warning");
                return;
            }
            const input = document.getElementById("pspReference");
            if (input) {
                input.value = state.lastPspReference;
                showToast("PSP reference loaded", "success");
            }
        });
    }

    const modifyBtn = document.getElementById("modify-btn");
    if (modifyBtn) {
        modifyBtn.addEventListener("click", async () => {
            const pspReference = getInputValue("pspReference");
            const modifyAmount = getAmountValue("modifyAmount");
            if (!pspReference || modifyAmount === null) {
                setLog("PSP reference and modify amount are required");
                showToast("PSP reference and modify amount are required", "warning");
                return;
            }
            showToast("Adjusting authorisation...", "info");
            await runApi("/api/modify-amount", { pspReference, modifyAmount });
        });
    }

    const captureBtn = document.getElementById("capture-btn");
    if (captureBtn) {
        captureBtn.addEventListener("click", async () => {
            const pspReference = getInputValue("pspReference");
            const captureAmount = getAmountValue("captureAmount");
            if (!pspReference || captureAmount === null) {
                setLog("PSP reference and capture amount are required");
                showToast("PSP reference and capture amount are required", "warning");
                return;
            }
            showToast("Capturing payment...", "info");
            await runApi("/api/capture", { pspReference, captureAmount });
        });
    }

    const refundBtn = document.getElementById("refund-btn");
    if (refundBtn) {
        refundBtn.addEventListener("click", async () => {
            const pspReference = getInputValue("pspReference");
            const refundAmount = getAmountValue("refundAmount");
            if (!pspReference || refundAmount === null) {
                setLog("PSP reference and refund amount are required");
                showToast("PSP reference and refund amount are required", "warning");
                return;
            }
            showToast("Processing refund...", "info");
            await runApi("/api/refund", { pspReference, refundAmount });
        });
    }

    const cancelBtn = document.getElementById("cancel-btn");
    if (cancelBtn) {
        cancelBtn.addEventListener("click", async () => {
            const pspReference = getInputValue("pspReference");
            if (!pspReference) {
                setLog("PSP reference is required");
                showToast("PSP reference is required", "warning");
                return;
            }
            showToast("Cancelling payment...", "info");
            await runApi("/api/cancel", { pspReference });
        });
    }
}

initButtons();
startPreauthCheckout();

