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

function setLog(message, payload) {
    const log = document.getElementById("preauth-log");
    if (!log) {
        return;
    }

    const time = new Date().toLocaleTimeString();
    const content = payload ? `${message}\n${JSON.stringify(payload, null, 2)}` : message;
    log.textContent = `[${time}] ${content}`;
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
}

async function runApi(path, body) {
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
    return json;
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
                        actions.reject();
                        return;
                    }

                    actions.resolve({
                        resultCode: response.resultCode,
                        action: response.action,
                        order: response.order
                    });
                } catch (error) {
                    console.error(error);
                    setLog("Preauthorisation error", { message: error.message });
                    actions.reject();
                }
            },
            onPaymentCompleted: (result, component) => {
                console.info("onPaymentCompleted", result, component);
                setLog("Payment completed", result);
            },
            onPaymentFailed: (result, component) => {
                console.info("onPaymentFailed", result, component);
                setLog("Payment failed", result);
            },
            onError: (error, component) => {
                console.error("onError", error.name, error.message, error.stack, component);
                setLog("Checkout error", { name: error.name, message: error.message });
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
                        actions.reject();
                        return;
                    }

                    actions.resolve({ resultCode: response.resultCode });
                } catch (error) {
                    console.error(error);
                    setLog("Details error", { message: error.message });
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
    } catch (error) {
        console.error(error);
        setLog("Checkout init error", { message: error.message });
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
                return;
            }
            const input = document.getElementById("pspReference");
            if (input) {
                input.value = state.lastPspReference;
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
                return;
            }
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
                return;
            }
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
                return;
            }
            await runApi("/api/refund", { pspReference, refundAmount });
        });
    }

    const cancelBtn = document.getElementById("cancel-btn");
    if (cancelBtn) {
        cancelBtn.addEventListener("click", async () => {
            const pspReference = getInputValue("pspReference");
            if (!pspReference) {
                setLog("PSP reference is required");
                return;
            }
            await runApi("/api/cancel", { pspReference });
        });
    }
}

initButtons();
startPreauthCheckout();
