/**
 * config.js — Central configuration for the Matir Bank frontend.
 * Change BACKEND_BASE_URL when switching between local dev (ngrok) and production (HuggingFace).
 */

const CONFIG = {
    // Local dev: default to localhost:8080
    // Production: Change the HuggingFace Space URL placeholder to your deployed backend URL.
    // Or set it dynamically in the browser console: localStorage.setItem("BACKEND_BASE_URL", "https://your-backend.hf.space")
    BACKEND_BASE_URL: localStorage.getItem("BACKEND_BASE_URL") || (
        (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")
            ? "http://localhost:8080"
            : "https://your-username-matirbank-backend.hf.space" // Replace with your HuggingFace Space backend URL
    ),

    API: {
        AUTH_REGISTER: "/api/auth/register",
        AUTH_LOGIN:    "/api/auth/login",
        AUTH_SETUP:    "/api/auth/setup",
        AUTH_SETUP_STATUS: "/api/auth/setup/status",
        ACCOUNTS_MINE: "/api/accounts/mine",
        ACCOUNTS_ALL:  "/api/accounts",
        ACCOUNTS_OPEN: "/api/accounts/open",
        ACCOUNTS_CLOSE:"/api/accounts/close",
        ACCOUNTS_APPROVE: "/api/accounts/approve",
        ACCOUNTS_FREEZE: "/api/accounts/freeze",
        ACCOUNTS_UNFREEZE: "/api/accounts/unfreeze",
        TX_DEPOSIT:    "/api/transactions/deposit",
        TX_WITHDRAW:   "/api/transactions/withdraw",
        TX_TRANSFER:   "/api/transactions/transfer",
        TX_HISTORY:    (acctNum) => `/api/transactions/history/${acctNum}`,
        TX_ALL:        "/api/transactions",
        LOANS_APPLY:   "/api/loans/apply",
        LOANS_MINE:    "/api/loans/mine",
        LOANS_PENDING: "/api/loans/pending",
        LOANS_ALL:     "/api/loans",
        LOAN_APPROVE:  (id) => `/api/loans/${id}/approve`,
        LOAN_REJECT:   (id) => `/api/loans/${id}/reject`,
        ADMIN_ME:      "/api/admin/me",
        ADMIN_CUSTOMERS: "/api/admin/customers",
        ADMIN_ADMINS:  "/api/admin/admins",
        ADMIN_CONFIG:  "/api/admin/config",
    }
};

// Helper: make an authenticated API call
async function apiCall(path, method = "GET", body = null) {
    const token = localStorage.getItem("mb_token");
    const options = {
        method,
        headers: {
            "Content-Type": "application/json",
            ...(token ? { "Authorization": `Bearer ${token}` } : {})
        }
    };
    if (body) options.body = JSON.stringify(body);

    const res = await fetch(CONFIG.BACKEND_BASE_URL + path, options);
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw { status: res.status, message: data.message || data.error || "Request failed" };
    return data;
}

// Auth helpers
function getToken() { return localStorage.getItem("mb_token"); }
function getRole() { return localStorage.getItem("mb_role"); }
function getUserId() { return localStorage.getItem("mb_userId"); }

function saveAuth(token) {
    localStorage.setItem("mb_token", token);
    // Decode JWT to get role and userId
    try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        localStorage.setItem("mb_role", payload.role);
        localStorage.setItem("mb_userId", payload.sub);
    } catch (e) {}
}

function logout() {
    localStorage.removeItem("mb_token");
    localStorage.removeItem("mb_role");
    localStorage.removeItem("mb_userId");
    window.location.href = "index.html";
}

function requireAuth(allowedRoles = []) {
    const token = getToken();
    if (!token) { window.location.href = "index.html"; return false; }
    if (allowedRoles.length > 0 && !allowedRoles.includes(getRole())) {
        alert("Access denied.");
        logout();
        return false;
    }
    return true;
}

function formatBDT(amount) {
    return "৳ " + Number(amount).toLocaleString("en-IN", { minimumFractionDigits: 2 });
}

function formatDate(dt) {
    if (!dt) return "";
    return new Date(dt).toLocaleString("en-GB", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

function showToast(message, type = "success") {
    const toast = document.getElementById("toast");
    if (!toast) return;
    toast.textContent = message;
    toast.className = `toast toast--${type} toast--show`;
    setTimeout(() => toast.classList.remove("toast--show"), 3500);
}
