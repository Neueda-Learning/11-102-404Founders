/* ============================================================
   landing.js  –  PayFlow Hub account-selection page
   ============================================================ */

const API_BASE = (window.PAYMENT_API_BASE ||
                  localStorage.getItem("PAYMENT_API_BASE") ||
                  "http://localhost:8080").replace(/\/$/, "");
const THEME_KEY   = "PPS_THEME";
const ACCOUNT_KEY = "PPS_SELECTED_ACCOUNT";

let accounts = [];
let createUserModal = null;

/* ====== Init ====== */
document.addEventListener("DOMContentLoaded", () => {
  requestAnimationFrame(() => document.body.classList.add("is-loaded"));
  applySavedTheme();
  createUserModal = bootstrap.Modal.getOrCreateInstance(document.getElementById("createUserModal"));
  initThemeToggle();
  initCreateUserFlow();
  loadAccounts();
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
  if (icon)  icon.className  = theme === "dark" ? "bi bi-moon-fill me-1" : "bi bi-sun-fill me-1";
  if (label) label.textContent = theme === "dark" ? "Dark" : "Light";
}

/* ====== Load accounts ====== */
async function loadAccounts() {
  show("loadingState");
  hide("accountsGrid");
  hide("errorState");
  hide("emptyState");

  try {
    const res = await fetch(`${API_BASE}/api/accounts`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    accounts = await res.json();

    hide("loadingState");

    if (!Array.isArray(accounts) || accounts.length === 0) {
      show("emptyState");
      return;
    }

    renderAccounts(accounts);
    show("accountsGrid");

  } catch {
    hide("loadingState");
    show("errorState");
  }
}

/* ====== Render account cards ====== */
function renderAccounts(accs) {
  const grid = document.getElementById("accountsGrid");
  grid.innerHTML = accs.map((acc, i) => `
    <div class="col-sm-6 col-lg-4 pf-in" style="animation-delay:${i * 0.07}s">
      <div class="pf-card pf-card-clickable p-4 h-100" onclick="selectAccount(${acc.id})">
        <div class="d-flex align-items-start gap-3 mb-3">
          <div class="pf-avatar">${initials(acc.accountHolderName)}</div>
          <div class="flex-grow-1 overflow-hidden">
            <h6 class="fw-bold mb-0 text-truncate">${esc(acc.accountHolderName)}</h6>
            <small class="text-muted">Account #${acc.id}</small>
          </div>
          <span class="badge ${acc.accountStatus === "ACTIVE" ? "bg-success" : "bg-secondary"} fs-xs">
            ${esc(acc.accountStatus)}
          </span>
        </div>

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

/* ====== Create user + first account ====== */
function initCreateUserFlow() {
  document.getElementById("createUserBtn")?.addEventListener("click", createUserAndAccount);
}

async function createUserAndAccount() {
  clearAlert("createUserAlertArea");

  const fullName = document.getElementById("newUserFullName")?.value.trim();
  const email = document.getElementById("newUserEmail")?.value.trim().toLowerCase();
  const defaultCurrency = document.getElementById("newUserCurrency")?.value || "USD";
  const accountType = document.getElementById("newUserAccountType")?.value.trim() || "Checking Account";
  const bankName = document.getElementById("newUserBankName")?.value.trim() || "PayFlow Bank";
  const openingBalance = Number(document.getElementById("newUserBalance")?.value || 0);

  if (!fullName) {
    return setAlert("createUserAlertArea", "Full name is required.", "warning");
  }
  if (!email) {
    return setAlert("createUserAlertArea", "Email is required.", "warning");
  }
  if (Number.isNaN(openingBalance) || openingBalance < 0) {
    return setAlert("createUserAlertArea", "Opening balance cannot be negative.", "warning");
  }

  const btn = document.getElementById("createUserBtn");
  setLoading(btn, true, "Creating...");

  try {
    const user = await fetchJson("/api/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        fullName,
        email,
        defaultCurrency
      })
    });

    const accountNumber = `PF${Date.now()}`.slice(-10);
    const bankIfsc = `PFLW${String(Date.now()).slice(-4)}`;

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
    document.getElementById("newUserCurrency").value = "USD";
    document.getElementById("newUserAccountType").value = "Checking Account";
    document.getElementById("newUserBankName").value = "PayFlow Bank";
    document.getElementById("newUserBalance").value = "0.00";
    createUserModal?.hide();

    localStorage.setItem(ACCOUNT_KEY, JSON.stringify(createdAccount));
    await loadAccounts();
    selectAccount(createdAccount.id);
  } catch (error) {
    setAlert("createUserAlertArea", error.message || "Unable to create user.", "danger");
  } finally {
    setLoading(btn, false, '<i class="bi bi-person-plus-fill me-1"></i>Create User');
  }
}

/* ====== Select account → dashboard ====== */
function selectAccount(id) {
  const acc = accounts.find(a => a.id === id);
  if (!acc) return;
  localStorage.setItem(ACCOUNT_KEY, JSON.stringify(acc));
  window.location.href = "dashboard.html";
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
  const response = await fetch(`${API_BASE}${path}`, options);
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const data = await response.json();
      if (data && typeof data.message === "string") {
        message = data.message;
      }
    } catch {
      try {
        message = await response.text();
      } catch {
        // ignore fallback parse errors
      }
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

function setLoading(btn, loading, label) {
  if (!btn) return;
  btn.disabled = loading;
  btn.innerHTML = loading
    ? `<span class="spinner-border spinner-border-sm me-1" role="status"></span>${label}`
    : label;
}

