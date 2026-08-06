/* ============================================================
   landing.js  –  PayFlow Hub two-step landing page
   Step 1: Select / Create User
   Step 2: Select / Create Account for that user
   ============================================================ */

const API_BASE = (window.PAYMENT_API_BASE ||
                  localStorage.getItem("PAYMENT_API_BASE") ||
                  "http://10.9.70.68:8082").replace(/\/$/, "");
const THEME_KEY   = "PPS_THEME";
const ACCOUNT_KEY = "PPS_SELECTED_ACCOUNT";

let allUsers     = [];
let allAccounts  = [];   // accounts for currently-selected user
let selectedUser = null;

let createUserModal    = null;
let createAccountModal = null;

/* ====== Init ====== */
document.addEventListener("DOMContentLoaded", () => {
  requestAnimationFrame(() => document.body.classList.add("is-loaded"));
  applySavedTheme();
  createUserModal    = bootstrap.Modal.getOrCreateInstance(document.getElementById("createUserModal"));
  createAccountModal = bootstrap.Modal.getOrCreateInstance(document.getElementById("createAccountModal"));
  initThemeToggle();
  initCreateUserFlow();
  initCreateAccountFlow();
  loadUsers();
});

/* ====== Theme ====== */
function applySavedTheme() {
  const saved = localStorage.getItem(THEME_KEY);
  if (saved === "dark" || saved === "light") {
    document.documentElement.setAttribute("data-bs-theme", saved);
    updateThemeBtn(saved);
  }
}

function initThemeToggle() {
  document.getElementById("themeToggle")?.addEventListener("click", () => {
    const cur  = document.documentElement.getAttribute("data-bs-theme") || "light";
    const next = cur === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-bs-theme", next);
    localStorage.setItem(THEME_KEY, next);
    updateThemeBtn(next);
  });
}

function updateThemeBtn(theme) {
  const icon  = document.getElementById("themeIcon");
  const label = document.getElementById("themeLabel");
  if (icon)  icon.className   = theme === "dark" ? "bi bi-moon-fill me-1" : "bi bi-sun-fill me-1";
  if (label) label.textContent = theme === "dark" ? "Dark" : "Light";
}

/* ====== STEP 1 – Load and render users ====== */
async function loadUsers() {
  show("loadingState");
  hide("usersGrid");
  hide("errorState");
  hide("emptyState");

  try {
    const res = await fetch(`${API_BASE}/api/users`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    allUsers = await res.json();
    hide("loadingState");

    if (!Array.isArray(allUsers) || allUsers.length === 0) {
      show("emptyState");
      return;
    }
    renderUsers(allUsers);
    show("usersGrid");
  } catch {
    hide("loadingState");
    show("errorState");
  }
}

function renderUsers(users) {
  const grid = document.getElementById("usersGrid");
  grid.innerHTML = users.map((user, i) => `
    <div class="col-sm-6 col-lg-4 pf-in" style="animation-delay:${i * 0.07}s">
      <div class="pf-card pf-card-clickable p-4 h-100" onclick="selectUser(${user.id})">
        <div class="d-flex align-items-start gap-3 mb-3">
          <div class="pf-avatar">${initials(user.fullName)}</div>
          <div class="flex-grow-1 overflow-hidden">
            <h6 class="fw-bold mb-0 text-truncate">${esc(user.fullName)}</h6>
            <small class="text-muted">${esc(user.email || "")}</small>
          </div>
        </div>
        <div class="mb-3 small text-muted">
          <span class="me-3"><i class="bi bi-currency-exchange me-1"></i>${esc(user.defaultCurrency || "INR")}</span>
          <span><i class="bi bi-speedometer2 me-1"></i>Limit: ${fmtAmt(user.dailyTransactionLimit || 5000)}</span>
        </div>
        <button class="btn btn-pf btn w-100 rounded-pill"
                onclick="event.stopPropagation();selectUser(${user.id})">
          <i class="bi bi-arrow-right-circle me-1"></i>Select User
        </button>
      </div>
    </div>
  `).join("");
}

/* ====== Transition to STEP 2 ====== */
async function selectUser(userId) {
  selectedUser = allUsers.find(u => u.id === userId) || null;
  if (!selectedUser) return;

  document.getElementById("userAvatarStep2").textContent = initials(selectedUser.fullName);
  document.getElementById("userNameStep2").textContent   = selectedUser.fullName;
  document.getElementById("userEmailStep2").textContent  = selectedUser.email || "";

  hide("stepUser");
  show("stepAccount");
  await loadUserAccounts(userId);
}

function backToUsers() {
  selectedUser = null;
  hide("stepAccount");
  hide("accountsGrid");
  hide("accountsEmptyState");
  show("stepUser");
}

/* ====== STEP 2 – Accounts for selected user ====== */
async function loadUserAccounts(userId) {
  show("accountsLoadingState");
  hide("accountsGrid");
  hide("accountsEmptyState");

  try {
    const res = await fetch(`${API_BASE}/api/users/${userId}/accounts`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    allAccounts = await res.json();
    hide("accountsLoadingState");

    if (!Array.isArray(allAccounts) || allAccounts.length === 0) {
      show("accountsEmptyState");
      return;
    }
    renderAccounts(allAccounts);
    show("accountsGrid");
  } catch {
    hide("accountsLoadingState");
    show("accountsEmptyState");
  }
}

function renderAccounts(accs) {
  const grid = document.getElementById("accountsGrid");
  grid.innerHTML = accs.map((acc, i) => `
    <div class="col-sm-6 col-lg-4 pf-in" style="animation-delay:${i * 0.07}s">
      <div class="pf-card pf-card-clickable p-4 h-100" onclick="selectAccount(${acc.id})">
        <div class="d-flex align-items-start gap-3 mb-2">
          <div class="pf-avatar" style="font-size:.9rem">${esc(acc.currencyCode || "?")}</div>
          <div class="flex-grow-1 overflow-hidden">
            <h6 class="fw-bold mb-0 text-truncate">${esc(acc.accountType || acc.accountHolderName || "Account")}</h6>
            <small class="text-muted">${acc.accountNumber ? "#" + esc(acc.accountNumber) : "ID: " + acc.id}</small>
          </div>
          <span class="badge ${acc.accountStatus === "ACTIVE" ? "bg-success" : "bg-secondary"} flex-shrink-0">
            ${esc(acc.accountStatus)}
          </span>
        </div>
        <div class="mb-1 small text-muted">${esc(acc.bankName || "")}</div>
        <div class="mb-4">
          <small class="text-muted d-block mb-1">Available Balance</small>
          <h4 class="fw-bold text-primary mb-0">${esc(acc.currencyCode)} ${fmtAmt(acc.balance)}</h4>
        </div>
        <button class="btn btn-pf btn w-100 rounded-pill"
                onclick="event.stopPropagation();selectAccount(${acc.id})">
          <i class="bi bi-arrow-right-circle me-1"></i>Select Account
        </button>
      </div>
    </div>
  `).join("");
}

function selectAccount(id) {
  const acc = allAccounts.find(a => a.id === id);
  if (!acc) return;
  localStorage.setItem(ACCOUNT_KEY, JSON.stringify(acc));
  window.location.href = "dashboard.html";
}

/* ====== Create New User + first account ====== */
function initCreateUserFlow() {
  document.getElementById("createUserBtn")?.addEventListener("click", createUserAndAccount);
}

async function createUserAndAccount() {
  clearAlert("createUserAlertArea");

  const fullName        = document.getElementById("newUserFullName")?.value.trim();
  const email           = document.getElementById("newUserEmail")?.value.trim().toLowerCase();
  const phoneNumber     = document.getElementById("newUserPhone")?.value.trim() || "";
  const defaultCurrency = document.getElementById("newUserCurrency")?.value || "INR";
  const accountType     = document.getElementById("newUserAccountType")?.value.trim() || "Checking Account";
  const bankName        = document.getElementById("newUserBankName")?.value.trim() || "PayFlow Bank";
  const openingBalance  = Number(document.getElementById("newUserBalance")?.value || 0);
  const dailyLimit      = Number(document.getElementById("newUserDailyLimit")?.value || 5000);

  if (!fullName) return setAlert("createUserAlertArea", "Full name is required.", "warning");
  if (!isValidPersonName(fullName)) return setAlert("createUserAlertArea", "Name contains invalid characters.", "warning");
  if (!email) return setAlert("createUserAlertArea", "Email is required.", "warning");
  if (!isValidEmail(email)) return setAlert("createUserAlertArea", "Please enter a valid email address.", "warning");
  if (phoneNumber && !isValidPhone(phoneNumber)) return setAlert("createUserAlertArea", "Phone number must be 10 to 15 digits.", "warning");
  if (!accountType) return setAlert("createUserAlertArea", "Account type is required.", "warning");
  if (!bankName) return setAlert("createUserAlertArea", "Bank name is required.", "warning");
  if (!SUPPORTED_CURRENCY_UI.has(defaultCurrency)) return setAlert("createUserAlertArea", "Only INR/USD are supported.", "warning");
  if (Number.isNaN(openingBalance) || openingBalance < 0) return setAlert("createUserAlertArea", "Opening balance cannot be negative.", "warning");
  if (Number.isNaN(dailyLimit) || dailyLimit <= 0) return setAlert("createUserAlertArea", "Daily limit must be greater than zero.", "warning");

  const btn = document.getElementById("createUserBtn");
  setLoading(btn, true, "Creating...");

  try {
    const user = await fetchJson("/api/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ fullName, email, phoneNumber: phoneNumber || null, defaultCurrency, dailyTransactionLimit: dailyLimit })
    });

    const accountNumber = `PF${Date.now()}`.slice(-10);
    const bankIfsc      = `PFLW${String(Date.now()).slice(-4)}`;

    const createdAccount = await fetchJson(`/api/users/${user.id}/accounts`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        accountHolderName: fullName,
        currencyCode: defaultCurrency,
        balance: openingBalance,
        accountType,
        bankName,
        bankIfsc,
        accountNumber
      })
    });

    document.getElementById("createUserForm")?.reset();
    document.getElementById("newUserCurrency").value    = "INR";
    document.getElementById("newUserAccountType").value = "Checking Account";
    document.getElementById("newUserBankName").value    = "PayFlow Bank";
    document.getElementById("newUserBalance").value     = "0.00";
    document.getElementById("newUserDailyLimit").value  = "5000.00";
    createUserModal?.hide();

    localStorage.setItem(ACCOUNT_KEY, JSON.stringify(createdAccount));
    window.location.href = "dashboard.html";
  } catch (error) {
    const msg = String(error?.message || "");
    if (msg.toLowerCase().includes("failed to fetch")) {
      setAlert("createUserAlertArea", "Cannot reach backend service. Please ensure server is running and try again.", "danger");
    } else {
      setAlert("createUserAlertArea", msg || "Unable to create user.", "danger");
    }
  } finally {
    setLoading(btn, false, '<i class="bi bi-person-plus-fill me-1"></i>Create User');
  }
}

/* ====== Create New Account for existing selected user ====== */
function initCreateAccountFlow() {
  document.getElementById("createAccountBtn")?.addEventListener("click", createAccountForUser);
}

async function createAccountForUser() {
  clearAlert("createAccountAlertArea");
  if (!selectedUser) return setAlert("createAccountAlertArea", "No user selected.", "danger");

  const accountType    = document.getElementById("newAccountType")?.value.trim() || "Savings Account";
  const currencyCode   = document.getElementById("newAccountCurrency")?.value || "INR";
  const openingBalance = Number(document.getElementById("newAccountBalance")?.value || 0);
  const bankName       = document.getElementById("newAccountBankName")?.value.trim() || "PayFlow Bank";
  const accountStatus  = document.getElementById("newAccountStatus")?.value || "ACTIVE";

  if (!accountType) return setAlert("createAccountAlertArea", "Account type is required.", "warning");
  if (!bankName) return setAlert("createAccountAlertArea", "Bank name is required.", "warning");
  if (!SUPPORTED_CURRENCY_UI.has(currencyCode)) return setAlert("createAccountAlertArea", "Only INR/USD are supported.", "warning");
  if (!["ACTIVE", "INACTIVE"].includes(accountStatus)) return setAlert("createAccountAlertArea", "Invalid account status.", "warning");
  if (Number.isNaN(openingBalance) || openingBalance < 0)
    return setAlert("createAccountAlertArea", "Opening balance cannot be negative.", "warning");

  const btn = document.getElementById("createAccountBtn");
  setLoading(btn, true, "Creating...");

  try {
    const accountNumber = `PF${Date.now()}`.slice(-10);
    const bankIfsc      = `PFLW${String(Date.now()).slice(-4)}`;

    const created = await fetchJson(`/api/users/${selectedUser.id}/accounts`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        accountHolderName: selectedUser.fullName,
        currencyCode,
        balance: openingBalance,
        accountType,
        bankName,
        bankIfsc,
        accountNumber,
        accountStatus
      })
    });

    createAccountModal?.hide();
    clearAlert("createAccountAlertArea");
    localStorage.setItem(ACCOUNT_KEY, JSON.stringify(created));
    window.location.href = "dashboard.html";
  } catch (error) {
    const msg = String(error?.message || "");
    if (msg.toLowerCase().includes("failed to fetch")) {
      setAlert("createAccountAlertArea", "Cannot reach backend service. Please ensure server is running and try again.", "danger");
    } else {
      setAlert("createAccountAlertArea", msg || "Unable to create account.", "danger");
    }
  } finally {
    setLoading(btn, false, '<i class="bi bi-plus-circle me-1"></i>Create Account');
  }
}

/* ====== Helpers ====== */
function show(id) {
  const el = document.getElementById(id);
  if (el) el.classList.remove("d-none");
}

function hide(id) {
  const el = document.getElementById(id);
  if (el) el.classList.add("d-none");
}

function initials(name) {
  return (name || "?").trim().split(/\s+/).map(w => w[0]).join("").slice(0, 2).toUpperCase();
}

function fmtAmt(v) {
  const n = Number(v);
  return isNaN(n) ? "0.00" : n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function esc(str) {
  return String(str ?? "").replace(/[&<>"']/g, c =>
    ({ "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;" }[c]));
}

function setAlert(areaId, msg, type) {
  const area = document.getElementById(areaId);
  if (!area) return;
  area.innerHTML = `
    <div class="alert alert-${type} py-2 small d-flex align-items-center gap-2 mb-3">
      <i class="bi bi-exclamation-triangle-fill flex-shrink-0"></i>
      ${esc(msg)}
    </div>`;
}

function clearAlert(areaId) {
  const area = document.getElementById(areaId);
  if (area) area.innerHTML = "";
}

async function fetchJson(path, options) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, options);
  } catch {
    throw new Error("Failed to fetch");
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const data = await response.json();
      if (data && typeof data.message === "string") message = data.message;
    } catch {
      try { message = await response.text(); } catch {}
    }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

const SUPPORTED_CURRENCY_UI = new Set(["INR", "USD"]);

function isValidPersonName(name) {
  return /^[A-Za-z][A-Za-z\s.'-]{1,79}$/.test(name);
}

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function isValidPhone(phone) {
  const digits = String(phone || "").replace(/\D/g, "");
  return digits.length >= 10 && digits.length <= 15;
}

function setLoading(btn, loading, label) {
  if (!btn) return;
  btn.disabled  = loading;
  btn.innerHTML = loading
    ? `<span class="spinner-border spinner-border-sm me-1" role="status"></span>${label}`
    : label;
}
