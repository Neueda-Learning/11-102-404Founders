/* ============================================================
   dashboard.js  –  PayFlow Hub main dashboard
   ============================================================ */

const API_BASE = (window.PAYMENT_API_BASE ||
                  localStorage.getItem("PAYMENT_API_BASE") ||
                  "http://localhost:8080").replace(/\/$/, "");
const THEME_KEY   = "PPS_THEME";
const ACCOUNT_KEY = "PPS_SELECTED_ACCOUNT";

/* ====== App state ====== */
let selectedAccount  = null;
let allAccounts      = [];
let allPayments      = [];
let allCampaigns     = [];
let selectedCampaign = null;

/* ====== Bootstrap modal instances ====== */
let bsPaymentModal, bsCrowdfundingModal, bsCampaignDetailModal, bsHistoryModal, bsToast;

/* ============================================================
   INIT
   ============================================================ */
document.addEventListener("DOMContentLoaded", () => {
  requestAnimationFrame(() => document.body.classList.add("is-loaded"));
  applySavedTheme();

  /* Require a selected account */
  try {
    const raw = localStorage.getItem(ACCOUNT_KEY);
    if (!raw) throw new Error("no account");
    selectedAccount = JSON.parse(raw);
  } catch {
    window.location.href = "index.html";
    return;
  }

  /* Bootstrap modals */
  bsPaymentModal       = new bootstrap.Modal(document.getElementById("paymentModal"));
  bsCrowdfundingModal  = new bootstrap.Modal(document.getElementById("crowdfundingModal"));
  bsCampaignDetailModal= new bootstrap.Modal(document.getElementById("campaignDetailModal"));
  bsHistoryModal       = new bootstrap.Modal(document.getElementById("paymentHistoryModal"));
  bsToast              = new bootstrap.Toast(document.getElementById("pfToast"), { delay: 4500 });

  /* Populate navbar immediately from cached account */
  updateNavProfile();

  /* Wire up events */
  initThemeToggle();
  initButtons();
  initPaymentForm();
  initCrowdfundingModal();
  initContribution();

  /* Load data */
  loadAllData();
});

/* ============================================================
   DATA LOADING
   ============================================================ */
async function loadAllData() {
  const [accRes, payRes, campRes] = await Promise.allSettled([
    fetchJson("/api/accounts"),
    fetchJson("/api/payments"),
    fetchJson("/api/campaigns"),
  ]);

  allAccounts  = accRes.status  === "fulfilled" && Array.isArray(accRes.value)  ? accRes.value  : [];
  allPayments  = payRes.status  === "fulfilled" && Array.isArray(payRes.value)  ? payRes.value  : [];
  allCampaigns = campRes.status === "fulfilled" && Array.isArray(campRes.value) ? campRes.value : [];

  /* Refresh selected account from live data */
  const fresh = allAccounts.find(a => a.id === selectedAccount.id);
  if (fresh) {
    selectedAccount = fresh;
    localStorage.setItem(ACCOUNT_KEY, JSON.stringify(fresh));
  }

  if (accRes.status === "rejected" && payRes.status === "rejected") {
    showGlobalAlert("Cannot connect to the backend. Make sure Spring Boot is running.", "danger");
  }

  updateNavProfile();
  populatePaymentDropdowns();
  renderStats();
  renderPaymentsTable();
}

/* ============================================================
   PROFILE / NAV
   ============================================================ */
function updateNavProfile() {
  setText("navAvatar",          initials(selectedAccount.accountHolderName));
  setText("navAccountName",     selectedAccount.accountHolderName);
  setText("dropdownAvatar",     initials(selectedAccount.accountHolderName));
  setText("dropdownAccountName",selectedAccount.accountHolderName);
  setText("dropdownAccountId",  `Account #${selectedAccount.id} · ${selectedAccount.currencyCode}`);
  setText("statAccountName",    `Account #${selectedAccount.id}`);
}

/* ============================================================
   STATS
   ============================================================ */
function renderStats() {
  const mine = allPayments.filter(p =>
    p.sourceAccountId === selectedAccount.id ||
    p.destinationAccountId === selectedAccount.id
  );

  setText("statBalance",   `${selectedAccount.currencyCode} ${fmtAmt(selectedAccount.balance)}`);
  setText("statTotal",     mine.length);
  setText("statCompleted", mine.filter(p => p.status === "COMPLETED" || p.status === "SUCCESS").length);
  setText("statFailed",    mine.filter(p => p.status === "FAILED").length);
}

/* ============================================================
   PAYMENTS TABLE
   ============================================================ */
function renderPaymentsTable() {
  const tbody = document.getElementById("paymentsTableBody");

  const mine = allPayments
    .filter(p => p.sourceAccountId === selectedAccount.id || p.destinationAccountId === selectedAccount.id)
    .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
    .slice(0, 25);

  if (mine.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="6" class="text-center py-5 text-muted">
          <i class="bi bi-inbox fs-2 d-block mb-2"></i>
          No transactions yet. Create your first payment!
        </td>
      </tr>`;
    return;
  }

  tbody.innerHTML = mine.map(p => `
    <tr onclick="viewPaymentHistory(${p.id})" title="Click to view status history">
      <td><span class="fw-semibold small">${esc(p.paymentReference || `#${p.id}`)}</span></td>
      <td>
        <small class="text-muted">
          ${p.sourceAccountId}
          <i class="bi bi-arrow-right mx-1"></i>
          ${p.destinationAccountId}
        </small>
      </td>
      <td><strong>${esc(p.currencyCode)} ${fmtAmt(p.amount)}</strong></td>
      <td><small class="text-muted">${esc(p.paymentType || "NORMAL_PAYMENT")}</small></td>
      <td><span class="badge ${badgeClass(p.status)}">${esc(p.status)}</span></td>
      <td><small class="text-muted">${fmtDate(p.createdAt)}</small></td>
    </tr>
  `).join("");
}

/* ============================================================
   PAYMENT FORM
   ============================================================ */
function populatePaymentDropdowns() {
  const src  = document.getElementById("sourceAccountId");
  const dest = document.getElementById("destinationAccountId");

  src.innerHTML  = "";
  dest.innerHTML = '<option value="">Select destination account</option>';

  const sourceAccount = allAccounts.find(a => a.id === selectedAccount.id) || selectedAccount;
  const sourceLabel = `${sourceAccount.id} – ${esc(sourceAccount.accountHolderName)} (${sourceAccount.currencyCode})`;
  src.insertAdjacentHTML("beforeend", `<option value="${sourceAccount.id}">${sourceLabel}</option>`);
  src.value = String(sourceAccount.id);
  src.disabled = true;

  const validDestinations = allAccounts.filter(account =>
    account.id !== sourceAccount.id
      && String(account.accountStatus || "").toUpperCase() === "ACTIVE"
      && String(account.currencyCode || "").toUpperCase() === String(sourceAccount.currencyCode || "").toUpperCase()
  );

  validDestinations.forEach(a => {
    const label = `${a.id} – ${esc(a.accountHolderName)} (${a.currencyCode})`;
    dest.insertAdjacentHTML("beforeend", `<option value="${a.id}">${label}</option>`);
  });

  if (validDestinations.length > 0) {
    dest.value = String(validDestinations[0].id);
    document.getElementById("destinationCurrencyCode").value = validDestinations[0].currencyCode;
  }

  document.getElementById("currencyCode").value = sourceAccount.currencyCode;
}

function initPaymentForm() {
  /* Auto-fill source currency when source account changes */
  document.getElementById("sourceAccountId")?.addEventListener("change", e => {
    const acc = allAccounts.find(a => a.id === Number(e.target.value));
    if (acc) document.getElementById("currencyCode").value = acc.currencyCode;
  });

  /* Auto-fill destination currency */
  document.getElementById("destinationAccountId")?.addEventListener("change", e => {
    const acc = allAccounts.find(a => a.id === Number(e.target.value));
    if (acc) document.getElementById("destinationCurrencyCode").value = acc.currencyCode;
  });

  /* Submit */
  document.getElementById("submitPaymentBtn")?.addEventListener("click", handlePaymentSubmit);
}

async function handlePaymentSubmit() {
  clearAlert("paymentAlertArea");

  const srcId  = Number(selectedAccount.id);
  const dstId  = Number(document.getElementById("destinationAccountId").value);
  const amount = Number(document.getElementById("paymentAmount").value);
  const curr   = document.getElementById("currencyCode").value;
  const dstCur = document.getElementById("destinationCurrencyCode").value;
  const userId = resolveCurrentUserId();

  if (!srcId)                       return setAlert("paymentAlertArea", "Source account not available.", "warning");
  if (!dstId)                       return setAlert("paymentAlertArea", "Please select a destination account.", "warning");
  if (srcId === dstId)              return setAlert("paymentAlertArea", "Source and destination must be different.", "warning");
  if (!amount || amount <= 0)       return setAlert("paymentAlertArea", "Enter a valid amount greater than zero.", "warning");
  if (!userId)                      return setAlert("paymentAlertArea", "Current user id is missing. Please reselect your account from landing page.", "warning");

  const sourceAcc = allAccounts.find(a => a.id === srcId);
  const destinationAcc = allAccounts.find(a => a.id === dstId);
  if (!sourceAcc || !destinationAcc) {
    return setAlert("paymentAlertArea", "Invalid source or destination account.", "warning");
  }
  if (String(sourceAcc.accountStatus || "").toUpperCase() !== "ACTIVE") {
    return setAlert("paymentAlertArea", "Source account is not active.", "warning");
  }
  if (String(destinationAcc.accountStatus || "").toUpperCase() !== "ACTIVE") {
    return setAlert("paymentAlertArea", "Destination account is not active.", "warning");
  }
  if (String(sourceAcc.currencyCode || "").toUpperCase() !== String(destinationAcc.currencyCode || "").toUpperCase()) {
    return setAlert("paymentAlertArea", "Destination currency must match source currency.", "warning");
  }
  if (Number(sourceAcc.balance || 0) < amount) {
    return setAlert("paymentAlertArea", "Insufficient source account balance.", "warning");
  }

  const btn = document.getElementById("submitPaymentBtn");
  setLoading(btn, true, "Processing…");

  try {
    const created = await fetchJson("/api/payments", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId,
        sourceAccountId: srcId,
        destinationAccountId: dstId,
        amount,
        currencyCode: curr,
        destinationCurrencyCode: dstCur,
        paymentType: "NORMAL_PAYMENT",
      }),
    });

    bsPaymentModal.hide();
    showToast("Payment Created", `${created.paymentReference} — Status: ${created.status}`, "success");
    document.getElementById("paymentForm").reset();
    await loadAllData();

  } catch (err) {
    setAlert("paymentAlertArea", err.message || "Payment failed. Please try again.", "danger");
  } finally {
    setLoading(btn, false, '<i class="bi bi-send me-1"></i>Send Payment');
  }
}

/* ============================================================
   CROWDFUNDING
   ============================================================ */
function initCrowdfundingModal() {
  document.getElementById("crowdfundingModal")
    .addEventListener("show.bs.modal", loadCampaigns);
}

async function loadCampaigns() {
  clearAlert("campaignsAlertArea");
  show("campaignsLoadingState");
  hide("campaignsGrid");
  hide("campaignsEmptyState");

  try {
    allCampaigns = await fetchJson("/api/campaigns");
    const active = allCampaigns.filter(c => c.status === "ACTIVE");

    hide("campaignsLoadingState");

    if (active.length === 0) { show("campaignsEmptyState"); return; }

    const grid = document.getElementById("campaignsGrid");
    grid.innerHTML = active.map((c, i) => {
      const raised  = Number(c.currentAmount || 0);
      const target  = Number(c.targetAmount  || 1);
      const pct     = Math.min(100, Math.round((raised / target) * 100));
      return `
        <div class="col-12 col-md-6 pf-in" style="animation-delay:${i * 0.07}s">
          <div class="pf-card campaign-card p-3 h-100" onclick="showCampaignDetail(${c.id})">
            <div class="d-flex align-items-center gap-2 mb-2">
              <div class="pf-avatar-sm" style="background:var(--pf-green-grad)">
                <i class="bi bi-heart-fill" style="font-size:.72rem"></i>
              </div>
              <div class="flex-grow-1 overflow-hidden">
                <h6 class="fw-bold mb-0 text-truncate small">${esc(c.campaignName || `Campaign #${c.id}`)}</h6>
                <span class="badge bg-success small">ACTIVE</span>
              </div>
              <span class="fw-semibold small text-success">${pct}%</span>
            </div>
            <div class="progress pf-progress mb-2">
              <div class="progress-bar bg-success" style="width:${pct}%"></div>
            </div>
            <div class="d-flex justify-content-between small text-muted">
              <span>Raised: <strong>${esc(c.targetCurrency || "")} ${fmtAmt(raised)}</strong></span>
              <span>Goal: <strong>${esc(c.targetCurrency || "")} ${fmtAmt(target)}</strong></span>
            </div>
          </div>
        </div>`;
    }).join("");

    show("campaignsGrid");

  } catch (err) {
    hide("campaignsLoadingState");
    setAlert("campaignsAlertArea", "Failed to load campaigns.", "danger");
  }
}

function showCampaignDetail(campaignId) {
  selectedCampaign = allCampaigns.find(c => c.id === campaignId);
  if (!selectedCampaign) return;

  const raised     = Number(selectedCampaign.currentAmount || 0);
  const target     = Number(selectedCampaign.targetAmount  || 1);
  const remaining  = Math.max(0, target - raised);
  const pct        = Math.min(100, Math.round((raised / target) * 100));
  const cur        = esc(selectedCampaign.targetCurrency || selectedAccount.currencyCode);

  setText("campaignDetailTitle", selectedCampaign.campaignName || `Campaign #${selectedCampaign.id}`);

  document.getElementById("campaignDetailArea").innerHTML = `
    <div class="pf-surface-2 p-3 mb-3">
      <div class="row g-3 text-center">
        <div class="col-4">
          <div class="fw-bold text-success">${cur} ${fmtAmt(raised)}</div>
          <small class="text-muted">Raised</small>
        </div>
        <div class="col-4">
          <div class="fw-bold">${cur} ${fmtAmt(target)}</div>
          <small class="text-muted">Goal</small>
        </div>
        <div class="col-4">
          <div class="fw-bold text-primary">${cur} ${fmtAmt(remaining)}</div>
          <small class="text-muted">Remaining</small>
        </div>
      </div>
    </div>
    <div class="progress pf-progress mb-1">
      <div class="progress-bar bg-success" style="width:${pct}%"></div>
    </div>
    <small class="text-muted d-block text-end">${pct}% funded</small>`;

  setText("contributionCurrency", cur);
  setEl("contributionAmount", el => { el.value = ""; });
  setText("contributionSource", `${selectedAccount.accountHolderName} (#${selectedAccount.id})`);
  clearAlert("contributionAlertArea");

  /* Hide crowdfunding modal, then show detail after transition */
  bsCrowdfundingModal.hide();
  document.getElementById("crowdfundingModal").addEventListener("hidden.bs.modal", () => {
    bsCampaignDetailModal.show();
  }, { once: true });
}

function initContribution() {
  document.getElementById("contributeBtn")?.addEventListener("click", handleContribution);

  document.getElementById("backToCampaigns")?.addEventListener("click", () => {
    bsCampaignDetailModal.hide();
    document.getElementById("campaignDetailModal").addEventListener("hidden.bs.modal", () => {
      bsCrowdfundingModal.show();
    }, { once: true });
  });
}

async function handleContribution() {
  clearAlert("contributionAlertArea");

  if (!selectedCampaign) return;
  const userId = resolveCurrentUserId();
  if (!userId) {
    return setAlert("contributionAlertArea", "Current user id is missing. Please go back and select your account again.", "warning");
  }

  const amount = Number(document.getElementById("contributionAmount").value);
  if (!amount || amount <= 0)
    return setAlert("contributionAlertArea", "Please enter a valid contribution amount.", "warning");

  const bucketId = selectedCampaign.bucketAccountId;
  if (!bucketId)
    return setAlert("contributionAlertArea", "Campaign has no bucket account configured.", "danger");

  const btn = document.getElementById("contributeBtn");
  setLoading(btn, true, "Contributing…");

  try {
    const cur = selectedCampaign.targetCurrency || selectedAccount.currencyCode;
    const created = await fetchJson("/api/payments", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId,
        sourceAccountId:       selectedAccount.id,
        destinationAccountId:  bucketId,
        amount,
        currencyCode:          cur,
        destinationCurrencyCode: cur,
        paymentType:           "CROWDFUNDING_PAYMENT",
        crowdfundingCampaignId: selectedCampaign.id,
      }),
    });

    bsCampaignDetailModal.hide();
    showToast("Contribution Sent! 🎉", `${created.paymentReference} — Thank you!`, "success");
    await loadAllData();

  } catch (err) {
    setAlert("contributionAlertArea", err.message || "Contribution failed. Try again.", "danger");
  } finally {
    setLoading(btn, false, '<i class="bi bi-heart-fill me-1"></i>Contribute');
  }
}

/* ============================================================
   PAYMENT HISTORY MODAL
   ============================================================ */
async function viewPaymentHistory(paymentId) {
  const payment = allPayments.find(p => p.id === paymentId);
  if (!payment) return;

  document.getElementById("paymentDetailArea").innerHTML = `
    <div class="row g-2 pf-surface-2 p-3 mb-1">
      <div class="col-6">
        <small class="text-muted d-block">Reference</small>
        <span class="fw-semibold small">${esc(payment.paymentReference || "–")}</span>
      </div>
      <div class="col-6">
        <small class="text-muted d-block">Amount</small>
        <span class="fw-semibold small">${esc(payment.currencyCode)} ${fmtAmt(payment.amount)}</span>
      </div>
      <div class="col-6">
        <small class="text-muted d-block">Status</small>
        <span class="badge ${badgeClass(payment.status)}">${esc(payment.status)}</span>
      </div>
      <div class="col-6">
        <small class="text-muted d-block">Type</small>
        <span class="small">${esc(payment.paymentType || "–")}</span>
      </div>
      <div class="col-6">
        <small class="text-muted d-block">Source</small>
        <span class="small">#${payment.sourceAccountId}</span>
      </div>
      <div class="col-6">
        <small class="text-muted d-block">Destination</small>
        <span class="small">#${payment.destinationAccountId}</span>
      </div>
    </div>`;

  document.getElementById("paymentHistoryArea").innerHTML =
    '<div class="text-center py-2"><div class="spinner-border spinner-border-sm text-primary"></div></div>';

  bsHistoryModal.show();

  try {
    const history = await fetchJson(`/api/payments/${paymentId}/history`);

    if (!history || history.length === 0) {
      document.getElementById("paymentHistoryArea").innerHTML =
        '<p class="text-muted text-center py-2 small">No history available.</p>';
      return;
    }

    document.getElementById("paymentHistoryArea").innerHTML = `
      <div class="list-group list-group-flush">
        ${history.map(h => `
          <div class="list-group-item px-0 border-0 py-2">
            <div class="d-flex align-items-start gap-2">
              <div class="pf-timeline-dot mt-1"></div>
              <div>
                <span class="fw-semibold small">${esc(h.fromStatus || "–")} → ${esc(h.toStatus)}</span>
                <small class="text-muted d-block">${fmtDate(h.changedAt)}</small>
              </div>
            </div>
          </div>`).join("")}
      </div>`;
  } catch {
    document.getElementById("paymentHistoryArea").innerHTML =
      '<p class="text-muted text-center py-2 small">Could not load history.</p>';
  }
}

/* ============================================================
   BUTTONS / REFRESH
   ============================================================ */
function initButtons() {
  document.getElementById("refreshBtn")?.addEventListener("click", async () => {
    await loadAllData();
    showToast("Refreshed", "All data reloaded.", "info");
  });

  document.getElementById("refreshPaymentsBtn")?.addEventListener("click", async () => {
    try {
      allPayments  = await fetchJson("/api/payments");
      allAccounts  = await fetchJson("/api/accounts");
      const fresh  = allAccounts.find(a => a.id === selectedAccount.id);
      if (fresh) { selectedAccount = fresh; localStorage.setItem(ACCOUNT_KEY, JSON.stringify(fresh)); }
      renderStats();
      renderPaymentsTable();
      showToast("Updated", "Transactions refreshed.", "success");
    } catch {
      showToast("Error", "Failed to refresh transactions.", "danger");
    }
  });
}

/* ============================================================
   THEME
   ============================================================ */
function applySavedTheme() {
  const saved = localStorage.getItem(THEME_KEY);
  if (saved === "dark" || saved === "light") {
    document.documentElement.setAttribute("data-bs-theme", saved);
    syncThemeIcon(saved);
  }
}

function initThemeToggle() {
  document.getElementById("themeToggle")?.addEventListener("click", () => {
    const cur  = document.documentElement.getAttribute("data-bs-theme") || "light";
    const next = cur === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-bs-theme", next);
    localStorage.setItem(THEME_KEY, next);
    syncThemeIcon(next);
  });
}

function syncThemeIcon(theme) {
  const icon = document.getElementById("themeIcon");
  if (icon) icon.className = theme === "dark" ? "bi bi-moon-fill" : "bi bi-sun-fill";
}

/* ============================================================
   UTILITIES
   ============================================================ */
async function fetchJson(path, options) {
  const url  = path.startsWith("http") ? path : `${API_BASE}${path}`;
  const resp = await fetch(url, options);
  if (!resp.ok) {
    let msg = `Request failed (${resp.status})`;
    try { const d = await resp.json(); if (d?.message) msg = d.message; } catch {}
    throw new Error(msg);
  }
  if (resp.status === 204) return null;
  return resp.json();
}

function initials(name) {
  return (name || "?").trim().split(/\s+/).map(w => w[0]).join("").slice(0, 2).toUpperCase();
}

function fmtAmt(v) {
  const n = Number(v);
  return isNaN(n) ? "0.00" : n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtDate(v) {
  if (!v) return "–";
  const d = new Date(v);
  if (isNaN(d.getTime())) return String(v);
  return d.toLocaleDateString() + " " + d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function badgeClass(status) {
  const map = {
    CREATED: "badge-created", VALIDATED: "badge-validated", SENT: "badge-sent",
    COMPLETED: "badge-completed", SUCCESS: "badge-success",
    FAILED: "badge-failed",    CANCELLED: "badge-cancelled", INITIATED: "badge-initiated",
  };
  return map[(status || "").toUpperCase()] || "bg-secondary";
}

function resolveCurrentUserId() {
  if (selectedAccount?.user?.id != null) {
    return Number(selectedAccount.user.id);
  }
  const fresh = allAccounts.find(account => account.id === selectedAccount?.id);
  if (fresh?.user?.id != null) {
    return Number(fresh.user.id);
  }
  return null;
}

function esc(str) {
  return String(str ?? "").replace(/[&<>"']/g, c =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function show(id)  { document.getElementById(id)?.classList.remove("d-none"); }
function hide(id)  { document.getElementById(id)?.classList.add("d-none"); }
function setText(id, val) { const el = document.getElementById(id); if (el) el.textContent = val; }
function setEl(id, fn)    { const el = document.getElementById(id); if (el) fn(el); }

function setLoading(btn, loading, label) {
  if (!btn) return;
  btn.disabled  = loading;
  btn.innerHTML = loading
    ? `<span class="spinner-border spinner-border-sm me-1" role="status"></span>${label}`
    : label;
}

function setAlert(areaId, msg, type) {
  const area = document.getElementById(areaId);
  if (!area) return;
  area.innerHTML = `
    <div class="alert alert-${type} py-2 small d-flex align-items-center gap-2 mb-2">
      <i class="bi bi-exclamation-triangle-fill flex-shrink-0"></i>
      ${esc(msg)}
    </div>`;
}

function clearAlert(areaId) {
  const area = document.getElementById(areaId);
  if (area) area.innerHTML = "";
}

function showGlobalAlert(msg, type) {
  const area = document.getElementById("globalAlertArea");
  if (area) area.innerHTML = `
    <div class="alert alert-${type} alert-dismissible d-flex align-items-center gap-2">
      <i class="bi bi-exclamation-triangle-fill flex-shrink-0"></i>
      <span>${esc(msg)}</span>
      <button type="button" class="btn-close ms-auto" data-bs-dismiss="alert"></button>
    </div>`;
}

function showToast(title, message, type) {
  const icons = { success: "bi-check-circle-fill text-success", danger: "bi-x-circle-fill text-danger",
                  warning: "bi-exclamation-triangle-fill text-warning", info: "bi-info-circle-fill text-info" };
  setEl("toastIcon",  el => { el.className = `bi ${icons[type] || icons.info} fs-5`; });
  setText("toastTitle", title);
  setText("toastBody",  message);
  bsToast.show();
}

