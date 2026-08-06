/* ============================================================
   dashboard.js  –  PayFlow Hub main dashboard
   ============================================================ */

const API_BASE = (window.PAYMENT_API_BASE ||
                  localStorage.getItem("PAYMENT_API_BASE") ||
                  "http://localhost:8080").replace(/\/$/, "");
const THEME_KEY = "PPS_THEME";
const ACCOUNT_KEY = "PPS_SELECTED_ACCOUNT";
const SUPPORTED_UI_CURRENCIES = ["INR", "USD"];
const FOREX_FEE_RATE = 0.018;
const EXCHANGE_RATE_INR_PER_USD = 93; // 1 USD = 93 INR
const PAYMENT_FORM_IDS = {
  modal: {
    source: "sourceAccountId",
    destination: "destinationAccountId",
    amount: "paymentAmount",
    currency: "currencyCode",
    destinationCurrency: "destinationCurrencyCode",
    submit: "submitPaymentBtn",
    alertArea: "paymentAlertArea"
  },
  inline: {
    source: "inlineSourceAccountId",
    destination: "inlineDestinationAccountId",
    amount: "inlinePaymentAmount",
    currency: "inlineCurrencyCode",
    destinationCurrency: "inlineDestinationCurrencyCode",
    submit: "inlineSubmitPaymentBtn",
    alertArea: "inlinePaymentAlertArea"
  }
};

const state = {
  selectedAccount: null,
  userId: null,
  user: null,
  accounts: [],
  allAccounts: [],
  payments: [],
  filteredPayments: [],
  tickets: [],
  campaigns: [],
  dashboard: null,
  currentTicketPaymentId: null,
  pendingPayment: null,
  paymentCountdownTimer: null
};

let bsPaymentModal;
let bsTicketModal;
let bsHistoryModal;
let bsCampaignModal;
let bsDisputeModal;
let bsPaymentConfirmModal;
let bsToast;

document.addEventListener("DOMContentLoaded", async () => {
  requestAnimationFrame(() => document.body.classList.add("is-loaded"));
  applySavedTheme();
  if (!loadSelectedAccount()) {
    window.location.href = "index.html";
    return;
  }

  bsPaymentModal  = new bootstrap.Modal(document.getElementById("paymentModal"));
  bsTicketModal   = new bootstrap.Modal(document.getElementById("ticketModal"));
  bsHistoryModal  = new bootstrap.Modal(document.getElementById("paymentHistoryModal"));
  bsCampaignModal = new bootstrap.Modal(document.getElementById("createCampaignModal"));
  bsDisputeModal  = new bootstrap.Modal(document.getElementById("disputeModal"));
  bsPaymentConfirmModal = new bootstrap.Modal(document.getElementById("paymentConfirmModal"));
  bsToast = new bootstrap.Toast(document.getElementById("pfToast"), { delay: 4200 });

  bindUi();
  await loadAllData();
});

function bindUi() {
  document.getElementById("themeToggle")?.addEventListener("click", toggleTheme);
  document.getElementById("refreshBtn")?.addEventListener("click", loadAllData);
  document.getElementById(PAYMENT_FORM_IDS.modal.submit)?.addEventListener("click", () => handleCreatePayment("modal"));
  document.getElementById(PAYMENT_FORM_IDS.inline.submit)?.addEventListener("click", () => handleCreatePayment("inline"));
  document.getElementById("submitTicketBtn")?.addEventListener("click", handleCreateTicket);
  document.getElementById("saveProfileBtn")?.addEventListener("click", handleProfileUpdate);
  document.getElementById("exportCsvBtn")?.addEventListener("click", exportCsv);
  document.getElementById("exportPdfBtn")?.addEventListener("click", exportPdf);
  document.getElementById("createCampaignBtn")?.addEventListener("click", () => {
    const select = document.getElementById("campaignBucketAccountId");
    if (select) {
      select.innerHTML = "";
      state.accounts.filter((a) => String(a.accountStatus || "").toUpperCase() === "ACTIVE").forEach((a) => {
        select.insertAdjacentHTML("beforeend", `<option value="${a.id}">${a.id} - ${esc(a.accountHolderName)} (${a.currencyCode})</option>`);
      });
    }
    bsCampaignModal.show();
  });
  document.getElementById("submitCampaignBtn")?.addEventListener("click", handleCreateCampaign);
  document.getElementById("submitDisputeBtn")?.addEventListener("click", handleCreateDispute);
  document.getElementById("confirmPaymentNowBtn")?.addEventListener("click", confirmPendingPaymentNow);
  document.getElementById("cancelPaymentConfirmBtn")?.addEventListener("click", cancelPendingPaymentConfirmation);
  document.getElementById("paymentConfirmCloseBtn")?.addEventListener("click", cancelPendingPaymentConfirmation);
  document.getElementById("paymentConfirmModal")?.addEventListener("hidden.bs.modal", cancelPendingPaymentConfirmation);

  [
    "referenceFilter", "senderFilter", "receiverFilter", "statusFilter", "paymentTypeFilter", "currencyFilter",
    "fromDateFilter", "toDateFilter", "dateFilter", "minAmountFilter", "maxAmountFilter", "timeWindowFilter"
  ].forEach((id) => {
    document.getElementById(id)?.addEventListener("input", loadPaymentsByFilter);
    document.getElementById(id)?.addEventListener("change", loadPaymentsByFilter);
  });

  ["modal", "inline"].forEach((key) => {
    const ids = PAYMENT_FORM_IDS[key];
    document.getElementById(ids.source)?.addEventListener("change", () => syncPaymentCurrencies(key));
    document.getElementById(ids.destination)?.addEventListener("change", () => syncPaymentCurrencies(key));
  });
}

function loadSelectedAccount() {
  try {
    const raw = localStorage.getItem(ACCOUNT_KEY);
    if (!raw) return false;
    state.selectedAccount = JSON.parse(raw);
    return !!state.selectedAccount?.id;
  } catch {
    return false;
  }
}

async function loadAllData() {
  clearGlobalAlert();
  try {
    const accountDetails = await fetchJson(`/api/accounts/${state.selectedAccount.id}`);
    state.selectedAccount = accountDetails;
    localStorage.setItem(ACCOUNT_KEY, JSON.stringify(accountDetails));
    state.userId = accountDetails?.user?.id ? Number(accountDetails.user.id) : null;
    if (!state.userId) {
      throw new Error("Could not identify the selected user. Please switch account and try again.");
    }

    const [workspace, allAccounts, campaigns] = await Promise.all([
      fetchJson(`/api/users/${state.userId}/workspace`),
      fetchJson("/api/accounts"),
      fetchJson("/api/campaigns")
    ]);

    state.user = workspace.user || null;
    state.accounts = Array.isArray(workspace.accounts) ? workspace.accounts : [];
    state.tickets = Array.isArray(workspace.tickets) ? workspace.tickets : [];
    state.dashboard = workspace.dashboard || null;
    state.allAccounts = Array.isArray(allAccounts) ? allAccounts : [];
    state.campaigns = Array.isArray(campaigns) ? campaigns : [];

    populatePaymentDropdowns();
    syncProfileSection();
    renderStats();
    renderDailyLimitPie();
    renderTicketsTable();
    renderCampaigns();
    await loadPaymentsByFilter();
  } catch (error) {
    showGlobalAlert(error.message || "Unable to load dashboard data.", "danger");
  }
}

async function loadPaymentsByFilter() {
  if (!state.userId) return;
  try {
    const params = new URLSearchParams();
    params.set("userId", String(state.userId));
    params.set("sortBy", "date");
    params.set("sortDir", "desc");

    setIfValue(params, "reference", getVal("referenceFilter"));
    setIfValue(params, "senderName", getVal("senderFilter"));
    setIfValue(params, "receiverName", getVal("receiverFilter"));
    setIfValue(params, "currency", getVal("currencyFilter"));
    setIfValue(params, "paymentType", getVal("paymentTypeFilter"));
    setIfValue(params, "timeWindow", getVal("timeWindowFilter"));
    setIfValue(params, "fromDate", getVal("fromDateFilter"));
    setIfValue(params, "toDate", getVal("toDateFilter"));
    setIfValue(params, "date", getVal("dateFilter"));
    setIfValue(params, "minAmount", getVal("minAmountFilter"));
    setIfValue(params, "maxAmount", getVal("maxAmountFilter"));

    const status = getVal("statusFilter");
    if (status) params.append("status", status);

    const response = await fetchJson(`/api/payments?${params.toString()}`);
    state.payments = Array.isArray(response) ? response : [];
    state.filteredPayments = state.payments;
    renderPaymentsTable();
    renderStats();
  } catch (error) {
    showGlobalAlert(error.message || "Unable to load payments.", "danger");
  }
}

function renderStats() {
  const mine = state.payments;
  const completed = mine.filter((p) => p.status === "COMPLETED" || p.status === "SUCCESS").length;
  const failed = mine.filter((p) => p.status === "FAILED").length;
  setText("statBalance", `${state.selectedAccount.currencyCode} ${fmtAmt(state.selectedAccount.balance)}`);
  setText("statAccountName", `Account #${state.selectedAccount.id}`);
  setText("statTotal", String(mine.length));
  setText("statCompleted", String(completed));
  setText("statFailed", String(failed));
}

function renderDailyLimitPie() {
  const dashboard = state.dashboard || {};
  const limit = Number(dashboard.dailyTransactionLimit || 0);
  const spent = Number(dashboard.spentToday || 0);
  const remaining = Math.max(0, Number(dashboard.remainingDailyLimit || limit - spent));
  const usedPercent = limit > 0 ? Math.min(100, (spent / limit) * 100) : 0;
  const usedDeg = (usedPercent / 100) * 360;

  setText("dailyLimitValue", fmtAmt(limit));
  setText("dailySpentValue", fmtAmt(spent));
  setText("dailyRemainingValue", fmtAmt(remaining));

  const pie = document.getElementById("dailyLimitPie");
  if (pie) {
    pie.style.background = `conic-gradient(#4169e1 0deg, #4169e1 ${usedDeg}deg, #dbe6ff ${usedDeg}deg, #dbe6ff 360deg)`;
  }
}

function populatePaymentDropdowns() {
  const sourceOptions = state.accounts.filter((a) => String(a.accountStatus || "").toUpperCase() === "ACTIVE");
  ["modal", "inline"].forEach((key) => {
    const ids = PAYMENT_FORM_IDS[key];
    const sourceSelect = document.getElementById(ids.source);
    const destSelect = document.getElementById(ids.destination);
    if (!sourceSelect || !destSelect) return;

    sourceSelect.innerHTML = "";
    destSelect.innerHTML = "";

    sourceOptions.forEach((account) => {
      sourceSelect.insertAdjacentHTML("beforeend", `<option value="${account.id}">${account.id} - ${esc(account.accountHolderName)} (${account.currencyCode})</option>`);
    });

    const defaultSource = state.selectedAccount.id;
    sourceSelect.value = String(defaultSource);
    refreshDestinationOptions(defaultSource, ids.destination);
    syncPaymentCurrencies(key);
  });
}

function refreshDestinationOptions(sourceId, destinationSelectId) {
  const destSelect = document.getElementById(destinationSelectId);
  if (!destSelect) return;
  destSelect.innerHTML = "";
  state.allAccounts
    .filter((account) => Number(account.id) !== Number(sourceId) && String(account.accountStatus || "").toUpperCase() === "ACTIVE")
    .forEach((account) => {
      destSelect.insertAdjacentHTML("beforeend", `<option value="${account.id}">${account.id} - ${esc(account.accountHolderName)} (${account.currencyCode})</option>`);
    });
}

function syncPaymentCurrencies(formKey) {
  if (formKey) {
    syncPaymentCurrenciesFor(formKey);
    return;
  }
  syncPaymentCurrenciesFor("modal");
  syncPaymentCurrenciesFor("inline");
}

function syncPaymentCurrenciesFor(formKey) {
  const ids = PAYMENT_FORM_IDS[formKey] || PAYMENT_FORM_IDS.modal;
  const sourceId = Number(getVal(ids.source));
  const destinationId = Number(getVal(ids.destination));

  if (sourceId) {
    refreshDestinationOptions(sourceId, ids.destination);
  }

  const source = state.allAccounts.find((a) => Number(a.id) === sourceId);
  const destination = state.allAccounts.find((a) => Number(a.id) === Number(getVal(ids.destination) || destinationId));

  if (source?.currencyCode) setVal(ids.currency, source.currencyCode);
  if (destination?.currencyCode) setVal(ids.destinationCurrency, destination.currencyCode);
}

/* ── Currency conversion helper (mirrors backend rate 1 USD = 93 INR) ── */
function computeSourceEquivalentUI(destinationAmount, sourceCurrency, destinationCurrency) {
  if (sourceCurrency === destinationCurrency) return Number(Number(destinationAmount).toFixed(2));
  if (sourceCurrency === "INR" && destinationCurrency === "USD") {
    return Number((destinationAmount * EXCHANGE_RATE_INR_PER_USD).toFixed(2));
  }
  if (sourceCurrency === "USD" && destinationCurrency === "INR") {
    return Number((destinationAmount / EXCHANGE_RATE_INR_PER_USD).toFixed(2));
  }
  return Number(Number(destinationAmount).toFixed(2));
}

/* ── Build payment confirmation message ── */
function buildPaymentConfirmMsg(destinationAmount, sourceCurrency, destinationCurrency, label) {
  const isCross = sourceCurrency !== destinationCurrency;
  const involvesUsd = [sourceCurrency, destinationCurrency].includes("USD");
  const sourceEquivalent = computeSourceEquivalentUI(destinationAmount, sourceCurrency, destinationCurrency);
  const fee = involvesUsd ? Number((sourceEquivalent * FOREX_FEE_RATE).toFixed(2)) : 0;
  const finalCharge = Number((sourceEquivalent + fee).toFixed(2));
  const sourceToDestinationRate = sourceCurrency === destinationCurrency
    ? 1
    : (sourceCurrency === "USD" ? EXCHANGE_RATE_INR_PER_USD : 1 / EXCHANGE_RATE_INR_PER_USD);

  let msg = `Confirm ${label || "Payment"}?\n\n`;
  msg += `Destination amount:    ${fmtAmt(destinationAmount)} ${destinationCurrency}\n`;
  msg += `Source equivalent:     ${fmtAmt(sourceEquivalent)} ${sourceCurrency}\n`;
  if (isCross) {
    msg += `Exchange rate:         ${sourceCurrency}->${destinationCurrency} = ${sourceToDestinationRate.toFixed(6)}\n`;
  }
  if (involvesUsd) {
    msg += `Forex fee (1.8%):      ${fmtAmt(fee)} ${sourceCurrency}\n`;
  }
  msg += `Final amount deducted: ${fmtAmt(finalCharge)} ${sourceCurrency}`;
  return { msg, fee, finalCharge, sourceEquivalent, isCross, involvesUsd };
}

async function handleCreateCampaign() {
  clearAlert("createCampaignAlertArea");
  const name = getVal("campaignName").trim();
  const description = getVal("campaignDescription").trim();
  const category = getVal("campaignCategory");
  const currency = getVal("campaignCurrency");
  const targetAmount = Number(getVal("campaignTargetAmount"));
  const deadline = getVal("campaignDeadline");
  const payoutAccountId = Number(getVal("campaignBucketAccountId"));

  if (!name) {
    setAlert("createCampaignAlertArea", "Campaign name is required.", "warning");
    return;
  }
  if (!targetAmount || targetAmount <= 0) {
    setAlert("createCampaignAlertArea", "Target amount must be greater than zero.", "warning");
    return;
  }
  if (!deadline) {
    setAlert("createCampaignAlertArea", "Deadline is required.", "warning");
    return;
  }
  if (!payoutAccountId) {
    setAlert("createCampaignAlertArea", "Please select a creator payout account.", "warning");
    return;
  }

  try {
    const created = await fetchJson("/api/campaigns", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        campaignName: name,
        description,
        donationCategory: category,
        targetAmount,
        targetCurrency: currency,
        campaignEndDate: deadline,
        creatorPayoutAccountId: payoutAccountId,
        // Backward compatibility field; backend now creates dedicated bucket per campaign.
        bucketAccountId: payoutAccountId,
        status: "ACTIVE"
      })
    });
    bsCampaignModal.hide();
    document.getElementById("campaignName").value = "";
    document.getElementById("campaignDescription").value = "";
    document.getElementById("campaignTargetAmount").value = "";
    document.getElementById("campaignDeadline").value = "";
    await loadAllData();
    showToast("Campaign created!", `"${esc(created.campaignName)}" is now live.`, "success");
    document.getElementById("crowdfundingSection")?.scrollIntoView({ behavior: "smooth" });
  } catch (error) {
    setAlert("createCampaignAlertArea", error.message || "Unable to create campaign.", "danger");
  }
}

function renderPaymentsTable() {
  const tbody = document.getElementById("paymentsTableBody");
  if (!tbody) return;

  if (!state.filteredPayments.length) {
    tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">No transactions found.</td></tr>';
    return;
  }

  // Collect all account IDs that belong to this user for role detection
  const myAccountIds = new Set(state.accounts.map(a => Number(a.id)));

  tbody.innerHTML = state.filteredPayments.map((payment) => {
    const sender   = accountName(payment.sourceAccountId);
    const receiver = accountName(payment.destinationAccountId);
    const isSender   = myAccountIds.has(Number(payment.sourceAccountId));
    const isReceiver = myAccountIds.has(Number(payment.destinationAccountId));

    const disputeSenderBtn = isSender
      ? `<button class="btn btn-xs btn-outline-warning py-0 px-1 ms-1" style="font-size:.72rem" data-dispute-sender="${payment.id}" title="Wrong Recipient Dispute"><i class="bi bi-flag-fill"></i> Wrong Recipient</button>`
      : "";
    const disputeReceiverBtn = isReceiver
      ? `<button class="btn btn-xs btn-outline-info py-0 px-1 ms-1" style="font-size:.72rem" data-dispute-receiver="${payment.id}" title="Report Unexpected Payment"><i class="bi bi-exclamation-circle"></i> Unexpected</button>`
      : "";
    const canReverse = isReceiver
      && ["COMPLETED", "SUCCESS"].includes(String(payment.status || "").toUpperCase())
      && !payment.reversalPaymentId
      && !payment.originalPaymentId
      && !["CROWDFUNDING", "CROWDFUNDING_PAYMENT"].includes(String(payment.paymentType || "").toUpperCase());
    const reverseBtn = canReverse
      ? `<button class="btn btn-xs btn-outline-success py-0 px-1 ms-1" style="font-size:.72rem" data-reverse-payment-id="${payment.id}" title="Return Payment"><i class="bi bi-arrow-counterclockwise"></i> Return Payment</button>`
      : "";

    return `
      <tr>
        <td><button class="btn btn-link p-0 text-decoration-none" data-history-id="${payment.id}">${esc(payment.paymentReference || `#${payment.id}`)}</button></td>
        <td>${fmtDate(payment.createdAt)}</td>
        <td>${esc(sender)}</td>
        <td>${esc(receiver)}</td>
        <td>${esc(payment.destinationCurrencyCode || payment.currencyCode)} ${fmtAmt(payment.amount)}</td>
        <td>${esc(payment.paymentType || "NORMAL_PAYMENT")}</td>
        <td><span class="badge ${badgeClass(payment.status)}">${esc(payment.status || "-")}</span></td>
        <td>
          <button class="btn btn-sm btn-outline-primary" data-ticket-payment-id="${payment.id}">Raise Ticket</button>
          ${disputeSenderBtn}${disputeReceiverBtn}${reverseBtn}
        </td>
      </tr>
    `;
  }).join("");

  tbody.querySelectorAll("[data-ticket-payment-id]").forEach((button) => {
    button.addEventListener("click", () => openTicketModal(Number(button.getAttribute("data-ticket-payment-id"))));
  });
  tbody.querySelectorAll("[data-history-id]").forEach((button) => {
    button.addEventListener("click", () => viewPaymentHistory(Number(button.getAttribute("data-history-id"))));
  });
  tbody.querySelectorAll("[data-dispute-sender]").forEach((button) => {
    button.addEventListener("click", () => openDisputeModal(Number(button.getAttribute("data-dispute-sender")), "SENDER"));
  });
  tbody.querySelectorAll("[data-dispute-receiver]").forEach((button) => {
    button.addEventListener("click", () => openDisputeModal(Number(button.getAttribute("data-dispute-receiver")), "RECEIVER"));
  });
  tbody.querySelectorAll("[data-reverse-payment-id]").forEach((button) => {
    button.addEventListener("click", () => handleReversePayment(Number(button.getAttribute("data-reverse-payment-id"))));
  });
}

async function handleCreatePayment(formKey = "modal") {
  const ids = PAYMENT_FORM_IDS[formKey] || PAYMENT_FORM_IDS.modal;
  clearAlert(ids.alertArea);
  const sourceId = Number(getVal(ids.source));
  const destinationId = Number(getVal(ids.destination));
  const amount = Number(getVal(ids.amount));
  const currencyCode = getVal(ids.currency);
  const destinationCurrencyCode = getVal(ids.destinationCurrency);

  if (!sourceId || !destinationId) {
    setAlert(ids.alertArea, "Please select valid source and destination accounts.", "warning");
    return;
  }
  if (sourceId === destinationId) {
    setAlert(ids.alertArea, "Source and destination accounts cannot be the same.", "warning");
    return;
  }
  if (!amount || amount <= 0) {
    setAlert(ids.alertArea, "Please fill all payment fields correctly.", "warning");
    return;
  }
  if (!SUPPORTED_UI_CURRENCIES.includes(currencyCode) || !SUPPORTED_UI_CURRENCIES.includes(destinationCurrencyCode)) {
    setAlert(ids.alertArea, "Only INR and USD payments are supported.", "warning");
    return;
  }

  const source = state.allAccounts.find((a) => Number(a.id) === sourceId);
  const destination = state.allAccounts.find((a) => Number(a.id) === destinationId);
  if (!source || !destination) {
    setAlert(ids.alertArea, "Selected accounts are invalid.", "warning");
    return;
  }

  const { isCross, involvesUsd } =
    buildPaymentConfirmMsg(amount, currencyCode, destinationCurrencyCode, "Payment");

  state.pendingPayment = {
    formKey,
    ids,
    payload: {
      userId: state.userId,
      sourceAccountId: sourceId,
      destinationAccountId: destinationId,
      amount,
      currencyCode,
      destinationCurrencyCode,
      paymentType: "NORMAL_PAYMENT",
      forexConfirmed: involvesUsd
    },
    summary: {
      sourceLabel: `${source.accountHolderName} (#${source.id})`,
      destinationLabel: `${destination.accountHolderName} (#${destination.id})`,
      amount,
      sourceCurrency: currencyCode,
      destinationCurrency: destinationCurrencyCode,
      isCross
    }
  };

  openPaymentConfirmationCountdown();
}

function openPaymentConfirmationCountdown() {
  if (!state.pendingPayment) return;
  clearPaymentCountdown();

  const summary = state.pendingPayment.summary;
  document.getElementById("paymentConfirmSummary").innerHTML = `
    <div class="row g-1">
      <div class="col-12"><strong>Confirm Payment?</strong></div>
      <div class="col-12"><span class="text-muted">Sender:</span> ${esc(summary.sourceLabel)}</div>
      <div class="col-12"><span class="text-muted">Receiver:</span> ${esc(summary.destinationLabel)}</div>
      <div class="col-6"><span class="text-muted">Amount:</span> ${fmtAmt(summary.amount)}</div>
      <div class="col-6"><span class="text-muted">Currency:</span> ${esc(summary.destinationCurrency)}</div>
      <div class="col-12"><span class="text-muted">Source Currency:</span> ${esc(summary.sourceCurrency)}</div>
      <div class="col-12"><span class="text-muted">Destination Currency:</span> ${esc(summary.destinationCurrency)}</div>
    </div>
  `;

  let left = 5;
  setText("paymentConfirmCountdown", String(left));
  bsPaymentConfirmModal.show();

  state.paymentCountdownTimer = window.setInterval(async () => {
    left -= 1;
    setText("paymentConfirmCountdown", String(Math.max(0, left)));
    if (left <= 0) {
      clearPaymentCountdown();
      bsPaymentConfirmModal.hide();
      await markPendingPaymentTimeout();
    }
  }, 1000);
}

function clearPaymentCountdown() {
  if (state.paymentCountdownTimer) {
    window.clearInterval(state.paymentCountdownTimer);
    state.paymentCountdownTimer = null;
  }
}

function cancelPendingPaymentConfirmation() {
  clearPaymentCountdown();
  state.pendingPayment = null;
}

async function confirmPendingPaymentNow() {
  if (!state.pendingPayment) return;
  const pending = state.pendingPayment;
  clearPaymentCountdown();
  bsPaymentConfirmModal.hide();
  await submitPaymentRequest(pending, false);
  state.pendingPayment = null;
}

async function markPendingPaymentTimeout() {
  if (!state.pendingPayment) return;
  const pending = state.pendingPayment;
  await submitPaymentRequest(pending, true);
  state.pendingPayment = null;
}

async function submitPaymentRequest(pending, isTimeout) {
  const { ids, payload, formKey, summary } = pending;
  try {
    const created = await fetchJson("/api/payments", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...payload, confirmationTimedOut: isTimeout })
    });

    await loadAllData();

    if (isTimeout) {
      window.alert("Payment Failed - Confirmation timeout.");
      return;
    }

    if (created.status === "FAILED") {
      const errMsg = created.errorCode || "Payment failed.";
      setAlert(ids.alertArea, errMsg, "danger");
      window.alert(`❌ Payment Failed\n\n${errMsg}`);
      return;
    }

    if (formKey === "modal") bsPaymentModal.hide();
    setVal(ids.amount, "");

    const remaining = created.remainingDailyLimit != null
      ? Number(created.remainingDailyLimit)
      : Number(state.dashboard?.remainingDailyLimit || 0);
    const actualFee = Number(created.forexFee || 0);
    const actualFinal = Number(created.finalChargedAmount || payload.amount);
    const actualSourceEquivalent = Number(created.convertedAmount || payload.amount);
    window.alert(
      `✅ Payment Successful!\n\n`
      + `Destination amount:    ${fmtAmt(created.amount || payload.amount)} ${payload.destinationCurrencyCode}\n`
      + `Source equivalent:     ${fmtAmt(actualSourceEquivalent)} ${payload.currencyCode}\n`
      + (summary.isCross ? `Exchange rate:         ${payload.currencyCode}->${payload.destinationCurrencyCode} = ${Number(created.exchangeRate || 1).toFixed(6)}\n` : "")
      + (actualFee > 0 ? `Forex fee (1.8%):      ${fmtAmt(actualFee)} ${payload.currencyCode}\n` : "")
      + `Final charged:         ${fmtAmt(actualFinal)} ${payload.currencyCode}\n`
      + `Remaining daily limit: ${fmtAmt(remaining)}`
    );
  } catch (error) {
    const message = error && error.message ? error.message : "Payment failed.";
    if (!isTimeout) {
      setAlert(ids.alertArea, message, "danger");
      window.alert(`❌ Payment Failed\n\n${message}`);
    } else {
      window.alert("Payment Failed - Confirmation timeout.");
    }
  }
}

function renderCampaigns() {
  const grid = document.getElementById("campaignsGrid");
  if (!grid) return;

  const badge = document.getElementById("campaignCountBadge");
  if (badge) {
    const activeCount = state.campaigns.filter((c) => c.status === "ACTIVE").length;
    badge.textContent = `${activeCount} Active`;
  }

  if (!state.campaigns.length) {
    grid.innerHTML = '<div class="col-12 text-center text-muted py-4">No active campaigns yet. Create one with the button above!</div>';
    return;
  }

  const sourceOptions = state.accounts
    .filter((a) => String(a.accountStatus || "").toUpperCase() === "ACTIVE")
    .map((a) => `<option value="${a.id}">${a.id} - ${esc(a.accountHolderName)} (${a.currencyCode})</option>`)
    .join("");

  grid.innerHTML = state.campaigns.map((campaign) => {
    const raised = Number(campaign.currentAmount || 0);
    const target = Number(campaign.targetAmount || 0);
    const remaining = Math.max(0, target - raised);
    const pct = target > 0 ? Math.min(100, (raised / target) * 100) : 0;
    const deadline = campaign.campaignEndDate;
    const daysLeft = deadline
      ? Math.max(0, Math.ceil((new Date(deadline + "T00:00:00") - new Date()) / 86400000))
      : null;
    const category = campaign.donationCategory || "";
    const isCompleted = String(campaign.status || "").toUpperCase() === "COMPLETED";
    const fillHeight = Math.max(2, pct);
    return `
      <div class="col-md-6">
        <div class="pf-card p-3 h-100 d-flex flex-column campaign-bucket-card ${isCompleted ? "campaign-completed" : ""}">
          <div class="d-flex justify-content-between align-items-start mb-1">
            <h6 class="fw-bold mb-0">${esc(campaign.campaignName || `Campaign #${campaign.id}`)}</h6>
            ${category ? `<span class="badge bg-info text-dark ms-1 flex-shrink-0">${esc(category)}</span>` : ""}
          </div>
          <p class="small text-muted mb-2">${esc(campaign.description || "")}</p>

          <div class="campaign-bucket-wrap mb-2">
            <div class="campaign-bucket" title="${pct.toFixed(1)}% funded">
              <div class="campaign-bucket-fill" style="height:${fillHeight.toFixed(1)}%"></div>
            </div>
            <div class="small text-muted text-center mt-1">Bucket Fill: ${pct.toFixed(1)}%</div>
          </div>

          <div class="d-flex justify-content-between small text-muted mb-2">
            <span>${pct.toFixed(1)}% funded</span>
            <span><span class="badge ${isCompleted ? "bg-success" : "bg-primary"}">${esc(campaign.status || "ACTIVE")}</span></span>
          </div>
          <div class="row g-1 small mb-2 text-center">
            <div class="col-4"><div class="text-muted">Collected</div><div class="fw-semibold text-success">${esc(campaign.targetCurrency)} ${fmtAmt(raised)}</div></div>
            <div class="col-4"><div class="text-muted">Remaining</div><div class="fw-semibold text-warning">${esc(campaign.targetCurrency)} ${fmtAmt(remaining)}</div></div>
            <div class="col-4"><div class="text-muted">Target</div><div class="fw-semibold">${esc(campaign.targetCurrency)} ${fmtAmt(target)}</div></div>
          </div>
          <div class="d-flex flex-wrap gap-3 small text-muted mb-3">
            ${deadline ? `<span><i class="bi bi-calendar3 me-1"></i>Deadline: ${deadline}</span>` : ""}
            ${daysLeft !== null ? `<span><i class="bi bi-clock me-1"></i>${daysLeft} day${daysLeft !== 1 ? "s" : ""} left</span>` : ""}
          </div>
          <div class="mt-auto ${isCompleted ? "opacity-75" : ""}">
            <div class="mb-2">
              <label class="form-label small fw-semibold mb-1">Donate from account</label>
              <select class="form-select form-select-sm" id="donateAccount-${campaign.id}" ${isCompleted ? "disabled" : ""}>${sourceOptions}</select>
            </div>
            <div class="input-group">
              <input type="number" min="0.01" step="0.01" class="form-control form-control-sm"
                     id="donate-${campaign.id}" placeholder="Amount (${esc(campaign.targetCurrency)})" ${isCompleted ? "disabled" : ""}>
              <button class="btn btn-success btn-sm" data-donate-id="${campaign.id}" ${isCompleted ? "disabled" : ""}>
                <i class="bi bi-heart-fill me-1"></i>Donate
              </button>
            </div>
          </div>
        </div>
      </div>
    `;
  }).join("");

  grid.querySelectorAll("[data-donate-id]").forEach((button) => {
    button.addEventListener("click", () => handleDonate(Number(button.getAttribute("data-donate-id"))));
  });
}


async function handleDonate(campaignId) {
  const campaign = state.campaigns.find((item) => Number(item.id) === Number(campaignId));
  if (!campaign) return;
  if (String(campaign.status || "").toUpperCase() === "COMPLETED") {
    showToast("Campaign completed", "This campaign already reached its target. Further donations are disabled.", "info");
    return;
  }
  const amount = Number(getVal(`donate-${campaignId}`));
  if (!amount || amount <= 0) {
    showToast("Invalid amount", "Enter contribution amount greater than zero.", "warning");
    return;
  }

  const sourceAccountId = Number(getVal(`donateAccount-${campaignId}`)) || state.selectedAccount.id;
  const sourceAccount = state.allAccounts.find((a) => Number(a.id) === sourceAccountId) || state.selectedAccount;
  const sourceCurrency = sourceAccount.currencyCode;
  const destinationCurrency = campaign.targetCurrency;

  if (!SUPPORTED_UI_CURRENCIES.includes(sourceCurrency) || !SUPPORTED_UI_CURRENCIES.includes(destinationCurrency)) {
    showToast("Unsupported currency", "Only INR and USD campaigns are supported.", "warning");
    return;
  }

  const { msg, isCross, involvesUsd } =
    buildPaymentConfirmMsg(amount, sourceCurrency, destinationCurrency, `Donation to "${campaign.campaignName}"`);
  if (!window.confirm(msg)) return;

  try {
    const payment = await fetchJson(`/api/campaigns/${campaignId}/contribute`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: state.userId,
        sourceAccountId,
        amount,
        currencyCode: sourceCurrency,
        idempotencyKey: `${campaignId}-${sourceAccountId}-${amount}-${Date.now()}`,
        forexConfirmed: involvesUsd
      })
    });
    await loadAllData();
    if (payment.status === "FAILED") {
      showToast("Donation failed", payment.errorCode || "Failed", "danger");
      window.alert(`❌ Donation Failed\n\n${payment.errorCode || "Validation failed."}`);
      return;
    }
    showToast("Donation processed", `${payment.paymentReference} — ${payment.status}`, "success");
    const remaining = payment.remainingDailyLimit != null
      ? Number(payment.remainingDailyLimit)
      : Number(state.dashboard?.remainingDailyLimit || 0);
    const actualFee = Number(payment.forexFee || 0);
    const actualSourceEquivalent = Number(payment.convertedAmount || amount);
    window.alert(
      `✅ Donation Successful!\n\nCampaign: ${campaign.campaignName}\n`
      + `Destination amount:    ${fmtAmt(amount)} ${destinationCurrency}\n`
      + `Source equivalent:     ${fmtAmt(actualSourceEquivalent)} ${sourceCurrency}\n`
      + (isCross ? `Exchange rate:         ${sourceCurrency}->${destinationCurrency} = ${Number(payment.exchangeRate || 1).toFixed(6)}\n` : "")
      + (actualFee > 0 ? `Forex fee (1.8%):      ${fmtAmt(actualFee)} ${sourceCurrency}\n` : "")
      + `Remaining daily limit: ${fmtAmt(remaining)}`
    );
  } catch (error) {
    window.alert(`❌ Donation Failed\n\n${error.message || "Donation failed."}`);
  }
}

async function handleReversePayment(paymentId) {
  const payment = state.payments.find((item) => Number(item.id) === Number(paymentId));
  if (!payment) return;

  const senderName = accountName(payment.sourceAccountId);
  const receiverName = accountName(payment.destinationAccountId);
  const amount = fmtAmt(payment.amount);
  const currency = payment.destinationCurrencyCode || payment.currencyCode || "";
  const reference = payment.paymentReference || `#${payment.id}`;

  const confirmed = window.confirm(
    "Are you sure you want to return this payment to the sender?\n\n"
    + `Reference: ${reference}\n`
    + `Original sender: ${senderName}\n`
    + `Receiver: ${receiverName}\n`
    + `Amount: ${amount} ${currency}`
  );
  if (!confirmed) return;

  const reversalReason = "Returned by receiver";
  try {
    await fetchJson(`/api/payments/${paymentId}/reverse`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId: state.userId, reason: reversalReason })
    });

    await loadAllData();
    window.alert("Payment returned successfully. Your balance has been updated.");
    showToast("Payment reversed", `Reversal created for ${reference}.`, "success");
  } catch (error) {
    const message = error.message || "Unable to reverse this payment.";
    window.alert(`Payment reversal failed.\n\n${message}`);
    showToast("Reversal failed", message, "danger");
  }
}

function openTicketModal(paymentId) {
  state.currentTicketPaymentId = paymentId;
  setVal("ticketPaymentId", String(paymentId));
  setVal("ticketTitle", "");
  setVal("ticketDescription", "");
  setVal("ticketIssueType", "FAILED_PAYMENT");
  setVal("ticketPriority", "MEDIUM");
  clearAlert("ticketAlertArea");
  bsTicketModal.show();
}

function openDisputeModal(paymentId, role) {
  const payment = state.payments.find(p => Number(p.id) === paymentId);
  if (!payment) return;

  const isSender = (role === "SENDER");
  document.getElementById("disputeModalTitle").textContent = isSender
    ? "Wrong Recipient Dispute"
    : "Report Unexpected Payment";

  // Identify the account belonging to this user for the dispute
  const myAccountIds = new Set(state.accounts.map(a => Number(a.id)));
  const myAccountId  = isSender
    ? (myAccountIds.has(Number(payment.sourceAccountId))      ? payment.sourceAccountId      : null)
    : (myAccountIds.has(Number(payment.destinationAccountId)) ? payment.destinationAccountId : null);

  setVal("disputePaymentId", String(paymentId));
  setVal("disputeRole",      role);
  setVal("disputeDescription", "");
  setVal("disputeIssueType",   isSender ? "WRONG_RECIPIENT" : "OTHER");
  setVal("disputePriority",    "HIGH");
  clearAlert("disputeAlertArea");

  // Payment summary card
  document.getElementById("disputePaymentSummary").innerHTML = `
    <div class="row g-1">
      <div class="col-6"><span class="text-muted">Reference:</span> <strong>${esc(payment.paymentReference || "-")}</strong></div>
      <div class="col-6"><span class="text-muted">Status:</span> <span class="badge ${badgeClass(payment.status)}">${esc(payment.status)}</span></div>
      <div class="col-6"><span class="text-muted">Sender:</span> ${esc(accountName(payment.sourceAccountId))}</div>
      <div class="col-6"><span class="text-muted">Receiver:</span> ${esc(accountName(payment.destinationAccountId))}</div>
      <div class="col-6"><span class="text-muted">Amount:</span> ${esc(payment.destinationCurrencyCode || payment.currencyCode)} ${fmtAmt(payment.amount)}</div>
      <div class="col-6"><span class="text-muted">Your role:</span> <strong>${esc(role)}</strong></div>
    </div>
  `;

  bsDisputeModal.show();
}

async function handleCreateDispute() {
  clearAlert("disputeAlertArea");
  const paymentId   = Number(getVal("disputePaymentId"));
  const role        = getVal("disputeRole");
  const description = getVal("disputeDescription").trim();
  const issueType   = getVal("disputeIssueType");
  const priority    = getVal("disputePriority");

  if (!paymentId || !description) {
    setAlert("disputeAlertArea", "Description is required.", "warning");
    return;
  }

  const payment = state.payments.find(p => Number(p.id) === paymentId);
  if (!payment) return;

  const myAccountIds = new Set(state.accounts.map(a => Number(a.id)));
  const accountId    = role === "SENDER"
    ? (myAccountIds.has(Number(payment.sourceAccountId))      ? payment.sourceAccountId      : null)
    : (myAccountIds.has(Number(payment.destinationAccountId)) ? payment.destinationAccountId : null);

  if (!accountId) {
    setAlert("disputeAlertArea", "Could not determine your account for this transaction.", "danger");
    return;
  }

  const title = role === "SENDER"
    ? `Wrong Recipient – ${payment.paymentReference || `#${paymentId}`}`
    : `Unexpected Payment – ${payment.paymentReference || `#${paymentId}`}`;

  try {
    await fetchJson(`/api/payments/${paymentId}/tickets`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: state.userId,
        title,
        description,
        issueType,
        priority
      })
    });
    bsDisputeModal.hide();
    await loadAllData();
    showToast("Dispute submitted", `${title} has been filed.`, "success");
  } catch (error) {
    setAlert("disputeAlertArea", error.message || "Unable to submit dispute.", "danger");
  }
}

async function handleCreateTicket() {
  clearAlert("ticketAlertArea");
  const paymentId = Number(getVal("ticketPaymentId"));
  const title = getVal("ticketTitle");
  const description = getVal("ticketDescription");
  const issueType = getVal("ticketIssueType");
  const priority = getVal("ticketPriority");

  if (!paymentId) {
    setAlert("ticketAlertArea", "Related transaction is required.", "warning");
    return;
  }
  if (!title.trim()) {
    setAlert("ticketAlertArea", "Title is required.", "warning");
    return;
  }
  if (!description.trim()) {
    setAlert("ticketAlertArea", "Description is required.", "warning");
    return;
  }
  if (!issueType) {
    setAlert("ticketAlertArea", "Issue type is required.", "warning");
    return;
  }
  if (!priority) {
    setAlert("ticketAlertArea", "Priority is required.", "warning");
    return;
  }

  try {
    await fetchJson(`/api/payments/${paymentId}/tickets`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId: state.userId, title, description, issueType, priority })
    });
    bsTicketModal.hide();
    await loadAllData();
    showToast("Ticket created", "Support ticket linked to transaction.", "success");
  } catch (error) {
    setAlert("ticketAlertArea", error.message || "Unable to create ticket.", "danger");
  }
}

function renderTicketsTable() {
  const tbody = document.getElementById("ticketsTableBody");
  if (!tbody) return;
  if (!state.tickets.length) {
    tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">No tickets found.</td></tr>';
    return;
  }

  tbody.innerHTML = state.tickets
    .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
    .map((ticket) => `
      <tr>
        <td>${esc(ticket.ticketNumber || `T-${ticket.id}`)}</td>
        <td>${ticket.paymentId || "-"}</td>
        <td>${esc(ticket.title || "-")}</td>
        <td>${esc(ticket.priority || "-")}</td>
        <td>
          <select class="form-select form-select-sm" data-ticket-status-id="${ticket.id}">
            ${["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"].map((status) => `<option value="${status}" ${ticket.status === status ? "selected" : ""}>${status}</option>`).join("")}
          </select>
        </td>
        <td>${fmtDate(ticket.createdAt)}</td>
        <td><button class="btn btn-sm btn-outline-primary" data-ticket-update-id="${ticket.id}">Update</button></td>
      </tr>
    `).join("");

  tbody.querySelectorAll("[data-ticket-update-id]").forEach((button) => {
    button.addEventListener("click", () => updateTicket(Number(button.getAttribute("data-ticket-update-id"))));
  });
}

async function updateTicket(ticketId) {
  const select = document.querySelector(`[data-ticket-status-id='${ticketId}']`);
  if (!select) return;
  const status = select.value;
  try {
    await fetchJson(`/api/tickets/${ticketId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status, resolutionSummary: status === "RESOLVED" ? "Resolved by user" : null })
    });
    await loadAllData();
    showToast("Ticket updated", `Ticket set to ${status}.`, "success");
  } catch (error) {
    showToast("Update failed", error.message || "Unable to update ticket", "danger");
  }
}

function syncProfileSection() {
  setVal("profileName", state.user?.fullName || "");
  setVal("profileEmail", state.user?.email || "");
  setVal("profileAccounts", String(state.accounts.length));
  setVal("profileDailyLimit", state.user?.dailyTransactionLimit != null ? String(state.user.dailyTransactionLimit) : "5000.00");
}

async function handleProfileUpdate() {
  const limit = Number(getVal("profileDailyLimit"));
  if (!limit || limit <= 0) {
    showToast("Invalid limit", "Daily transaction limit must be greater than zero.", "warning");
    return;
  }
  try {
    await fetchJson(`/api/users/${state.userId}/daily-limit`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ dailyTransactionLimit: limit })
    });
    await loadAllData();
    showToast("Profile updated", "Daily transaction limit saved.", "success");
  } catch (error) {
    showToast("Update failed", error.message || "Unable to update profile", "danger");
  }
}

function exportCsv() {
  if (!state.filteredPayments.length) {
    showToast("No data", "No filtered transactions to export.", "warning");
    return;
  }
  const headers = [
    "Transaction Reference", "Date", "Sender", "Receiver", "Original Amount", "Source Currency",
    "Destination Currency", "Converted Amount", "Exchange Rate", "Forex Fee",
    "Final Charged Amount", "Payment Type", "Status", "Failure Reason"
  ];
  const rows = state.filteredPayments.map((payment) => [
    payment.paymentReference || "",
    fmtDate(payment.createdAt),
    accountName(payment.sourceAccountId),
    accountName(payment.destinationAccountId),
    fmtAmt(payment.amount),
    payment.currencyCode || "",
    payment.destinationCurrencyCode || "",
    fmtAmt(payment.convertedAmount || 0),
    payment.exchangeRate != null ? Number(payment.exchangeRate).toFixed(6) : "",
    fmtAmt(payment.forexFee || 0),
    fmtAmt(payment.finalChargedAmount || payment.amount || 0),
    payment.paymentType || "",
    payment.status || "",
    payment.errorCode || ""
  ]);

  const csv = [headers, ...rows].map((row) => row.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(",")).join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `transactions-${Date.now()}.csv`;
  anchor.click();
  URL.revokeObjectURL(url);
}

function exportPdf() {
  if (!state.filteredPayments.length) {
    showToast("No data", "No filtered transactions to export.", "warning");
    return;
  }

  const html = `
    <html><head><title>Filtered Transactions</title></head><body>
      <h3>Filtered Transaction Report</h3>
      <table border="1" cellspacing="0" cellpadding="6">
        <thead><tr><th>Reference</th><th>Date</th><th>Sender</th><th>Receiver</th><th>Original</th><th>Src Curr</th><th>Dest Curr</th><th>Converted</th><th>Rate</th><th>Forex Fee</th><th>Final Charged</th><th>Type</th><th>Status</th><th>Failure Reason</th></tr></thead>
        <tbody>
          ${state.filteredPayments.map((payment) => `
            <tr>
              <td>${esc(payment.paymentReference || "")}</td>
              <td>${esc(fmtDate(payment.createdAt))}</td>
              <td>${esc(accountName(payment.sourceAccountId))}</td>
              <td>${esc(accountName(payment.destinationAccountId))}</td>
              <td>${esc(fmtAmt(payment.amount))}</td>
              <td>${esc(payment.currencyCode || "")}</td>
              <td>${esc(payment.destinationCurrencyCode || "")}</td>
              <td>${esc(fmtAmt(payment.convertedAmount || 0))}</td>
              <td>${esc(payment.exchangeRate != null ? Number(payment.exchangeRate).toFixed(6) : "")}</td>
              <td>${esc(fmtAmt(payment.forexFee || 0))}</td>
              <td>${esc(fmtAmt(payment.finalChargedAmount || payment.amount || 0))}</td>
              <td>${esc(payment.paymentType || "")}</td>
              <td>${esc(payment.status || "")}</td>
              <td>${esc(payment.errorCode || "")}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </body></html>
  `;

  const popup = window.open("", "_blank");
  if (!popup) {
    showToast("Blocked", "Popup blocked. Allow popups to export PDF.", "warning");
    return;
  }
  popup.document.write(html);
  popup.document.close();
  popup.focus();
  popup.print();
}

async function viewPaymentHistory(paymentId) {
  const payment = state.payments.find((item) => Number(item.id) === Number(paymentId));
  if (!payment) return;

  document.getElementById("paymentDetailArea").innerHTML = `
    <div class="row g-2 pf-surface-2 p-3">
      <div class="col-6"><small class="text-muted d-block">Reference</small><span>${esc(payment.paymentReference || "-")}</span></div>
      <div class="col-6"><small class="text-muted d-block">Original Amount</small><span>${fmtAmt(payment.amount)} ${esc(payment.destinationCurrencyCode || "")}</span></div>
      <div class="col-6"><small class="text-muted d-block">Source Currency</small><span>${esc(payment.currencyCode || "-")}</span></div>
      <div class="col-6"><small class="text-muted d-block">Destination Currency</small><span>${esc(payment.destinationCurrencyCode || "-")}</span></div>
      <div class="col-6"><small class="text-muted d-block">Converted Amount</small><span>${fmtAmt(payment.convertedAmount || 0)} ${esc(payment.currencyCode || "")}</span></div>
      <div class="col-6"><small class="text-muted d-block">Exchange Rate</small><span>${payment.exchangeRate != null ? Number(payment.exchangeRate).toFixed(6) : "-"}</span></div>
      <div class="col-6"><small class="text-muted d-block">Forex Fee</small><span>${fmtAmt(payment.forexFee || 0)} ${esc(payment.currencyCode || "")}</span></div>
      <div class="col-6"><small class="text-muted d-block">Final Charged</small><span>${fmtAmt(payment.finalChargedAmount || payment.amount || 0)} ${esc(payment.currencyCode || "")}</span></div>
      <div class="col-6"><small class="text-muted d-block">Status</small><span>${esc(payment.status || "-")}</span></div>
      <div class="col-6"><small class="text-muted d-block">Failure</small><span>${esc(payment.errorCode || "-")}</span></div>
    </div>
  `;

  document.getElementById("paymentHistoryArea").innerHTML = '<div class="text-center py-3"><div class="spinner-border spinner-border-sm text-primary"></div></div>';
  bsHistoryModal.show();

  try {
    const history = await fetchJson(`/api/payments/${paymentId}/history`);
    if (!Array.isArray(history) || !history.length) {
      document.getElementById("paymentHistoryArea").innerHTML = '<p class="text-muted small text-center py-2">No history found.</p>';
      return;
    }
    document.getElementById("paymentHistoryArea").innerHTML = history.map((item) => `
      <div class="border-bottom py-2">
        <div class="small fw-semibold">${esc(item.fromStatus || "-")} -> ${esc(item.toStatus || "-")}</div>
        <div class="small text-muted">${fmtDate(item.changedAt)} | ${esc(item.description || "Status updated")}</div>
      </div>
    `).join("");
  } catch {
    document.getElementById("paymentHistoryArea").innerHTML = '<p class="text-muted small text-center py-2">Could not load payment history.</p>';
  }
}

function accountName(accountId) {
  const account = state.allAccounts.find((item) => Number(item.id) === Number(accountId));
  return account ? `${account.accountHolderName} (#${account.id})` : `Account #${accountId}`;
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
        // Ignore.
      }
    }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

function applySavedTheme() {
  const saved = localStorage.getItem(THEME_KEY);
  if (saved === "dark" || saved === "light") {
    document.documentElement.setAttribute("data-bs-theme", saved);
    syncThemeIcon(saved);
  }
}

function toggleTheme() {
  const current = document.documentElement.getAttribute("data-bs-theme") || "light";
  const next = current === "dark" ? "light" : "dark";
  document.documentElement.setAttribute("data-bs-theme", next);
  localStorage.setItem(THEME_KEY, next);
  syncThemeIcon(next);
}

function syncThemeIcon(theme) {
  const icon = document.getElementById("themeIcon");
  if (icon) icon.className = theme === "dark" ? "bi bi-moon-fill" : "bi bi-sun-fill";
}

function setIfValue(params, key, value) {
  if (value != null && String(value).trim() !== "") params.set(key, String(value).trim());
}

function getVal(id) {
  const element = document.getElementById(id);
  return element ? element.value : "";
}

function setVal(id, value) {
  const element = document.getElementById(id);
  if (element) element.value = value;
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element) element.textContent = value;
}

function fmtAmt(value) {
  const amount = Number(value);
  return Number.isNaN(amount) ? "0.00" : amount.toFixed(2);
}

function fmtDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return `${date.toLocaleDateString()} ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
}

function badgeClass(status) {
  const map = {
    CREATED: "badge-created",
    VALIDATED: "badge-validated",
    PROCESSING: "badge-sent",
    COMPLETED: "badge-completed",
    REVERSED: "badge-cancelled",
    SUCCESS: "badge-success",
    FAILED: "badge-failed",
    CANCELLED: "badge-cancelled"
  };
  return map[String(status || "").toUpperCase()] || "bg-secondary";
}

function setAlert(areaId, message, type) {
  const area = document.getElementById(areaId);
  if (!area) return;
  area.innerHTML = `<div class="alert alert-${type} py-2 small mb-2">${esc(message)}</div>`;
}

function clearAlert(areaId) {
  const area = document.getElementById(areaId);
  if (area) area.innerHTML = "";
}

function showGlobalAlert(message, type) {
  const area = document.getElementById("globalAlertArea");
  if (!area) return;
  area.innerHTML = `<div class="alert alert-${type}">${esc(message)}</div>`;
}

function clearGlobalAlert() {
  const area = document.getElementById("globalAlertArea");
  if (area) area.innerHTML = "";
}

function showToast(title, message, type) {
  const icons = {
    success: "bi-check-circle-fill text-success",
    danger: "bi-x-circle-fill text-danger",
    warning: "bi-exclamation-triangle-fill text-warning",
    info: "bi-info-circle-fill text-info"
  };
  const icon = document.getElementById("toastIcon");
  if (icon) icon.className = `bi ${icons[type] || icons.info} fs-5`;
  setText("toastTitle", title);
  setText("toastBody", message);
  bsToast.show();
}

function esc(str) {
  return String(str ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
