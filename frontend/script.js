const API_BASE = (window.PAYMENT_API_BASE || window.localStorage.getItem("PAYMENT_API_BASE") || "http://localhost:8080").replace(/\/$/, "");
const THEME_KEY = "PPS_THEME";

const state = {
  users: [],
  accounts: [],
  campaigns: [],
  payments: [],
  tickets: [],
  dashboard: null,
  selectedUserId: null,
  activeAccountId: null,
  selectedPaymentId: null,
  pendingPaymentPayload: null,
  filteredPayments: []
};

document.addEventListener("DOMContentLoaded", async () => {
  applySavedTheme();
  requestAnimationFrame(() => document.body.classList.add("is-loaded"));
  initNavbar();
  initThemeToggle();
  initProfileMenu();
  initReveal();
  initQuickActions();
  initPaymentTypeToggle();
  initLanding();
  initPaymentForm();
  initPaymentMonitor();
  initTicketsModule();
  await bootstrapData();
  hideLoadingScreen();
});

async function bootstrapData() {
  setLoadingState(true);
  try {
    state.users = await fetchJson("/api/users");
    state.selectedUserId = resolveStoredUser();
    renderLandingUserOptions();
    if (state.selectedUserId) {
      await loadUserScope();
    } else {
      renderAccountSelection();
      renderScopedViews();
    }
  } catch (error) {
    showToast("Load failed", extractErrorMessage(error), "error");
  }
  setLoadingState(false);
}

function initNavbar() {
  const header = document.getElementById("siteHeader");
  const navToggle = document.getElementById("navToggle");
  const navLinks = document.getElementById("navLinks");

  window.addEventListener("scroll", () => {
    header.classList.toggle("scrolled", window.scrollY > 8);
  });

  navToggle.addEventListener("click", () => {
    const open = navLinks.classList.toggle("open");
    navToggle.setAttribute("aria-expanded", String(open));
  });

  navLinks.querySelectorAll("a").forEach((anchor) => {
    anchor.addEventListener("click", () => {
      navLinks.classList.remove("open");
      navToggle.setAttribute("aria-expanded", "false");
    });
  });

  document.getElementById("refreshAllBtn").addEventListener("click", async () => {
    await refreshAll();
  });
}

function initThemeToggle() {
  const toggle = document.getElementById("themeToggle");
  const label = document.getElementById("themeToggleLabel");

  const updateLabel = () => {
    label.textContent = document.body.getAttribute("data-theme") === "dark" ? "Dark" : "Light";
  };

  updateLabel();
  toggle.addEventListener("click", () => {
    const next = document.body.getAttribute("data-theme") === "dark" ? "light" : "dark";
    document.body.setAttribute("data-theme", next);
    window.localStorage.setItem(THEME_KEY, next);
    updateLabel();
  });
}

function applySavedTheme() {
  const saved = window.localStorage.getItem(THEME_KEY);
  if (saved === "dark" || saved === "light") {
    document.body.setAttribute("data-theme", saved);
  }
}

function initProfileMenu() {
  const root = document.getElementById("profileMenuRoot");
  const trigger = document.getElementById("profileTrigger");
  const dropdown = document.getElementById("profileDropdown");

  trigger.addEventListener("click", () => {
    const open = dropdown.hasAttribute("hidden");
    if (open) {
      dropdown.removeAttribute("hidden");
    } else {
      dropdown.setAttribute("hidden", "hidden");
    }
    trigger.setAttribute("aria-expanded", String(open));
  });

  document.addEventListener("click", (event) => {
    if (!root.contains(event.target)) {
      dropdown.setAttribute("hidden", "hidden");
      trigger.setAttribute("aria-expanded", "false");
    }
  });

  dropdown.querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => {
      showToast("Menu", `${button.textContent} is available in workspace mode.`, "warn");
      dropdown.setAttribute("hidden", "hidden");
      trigger.setAttribute("aria-expanded", "false");
    });
  });
}

function initQuickActions() {
  const bindings = [
    ["quickActionCreate", "workspace"],
    ["quickActionTransactions", "payments"],
    ["quickActionAudit", "details"],
    ["quickActionDataHub", "datahub"]
  ];
  bindings.forEach(([id, section]) => {
    const element = document.getElementById(id);
    if (element) {
      element.addEventListener("click", () => {
        document.getElementById(section).scrollIntoView({ behavior: "smooth", block: "start" });
      });
    }
  });
}

function initReveal() {
  const sections = document.querySelectorAll("[data-reveal]");
  if (!sections.length) {
    return;
  }

  // Visibility-first: ensure content is readable even if observers fail.
  sections.forEach((section) => section.classList.add("visible"));
}

function initLanding() {
  const continueBtn = document.getElementById("continueExistingUserBtn");
  const createBtn = document.getElementById("createLandingUserBtn");

  continueBtn.addEventListener("click", async () => {
    const userId = Number(document.getElementById("landingExistingUserSelect").value);
    if (!userId) {
      showValidationPopup("Please select an existing user to continue.");
      return;
    }
    state.selectedUserId = userId;
    window.localStorage.setItem("PPS_SELECTED_USER_ID", String(userId));
    await loadUserScope();
    jumpTo("accountSelection");
  });

  createBtn.addEventListener("click", async () => {
    const payload = {
      fullName: document.getElementById("landingFullName").value.trim(),
      email: document.getElementById("landingEmail").value.trim(),
      phoneNumber: document.getElementById("landingPhone").value.trim(),
      address: document.getElementById("landingAddress").value.trim(),
      country: document.getElementById("landingCountry").value.trim(),
      defaultCurrency: document.getElementById("landingDefaultCurrency").value,
      dailyTransactionLimit: Number(document.getElementById("landingDailyLimit").value || 5000)
    };

    if (!payload.fullName || !payload.email) {
      showValidationPopup("Full Name and Email are required to create a user.");
      return;
    }

    setLoadingState(true);
    try {
      const created = await fetchJson("/api/users", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      state.selectedUserId = Number(created.id);
      window.localStorage.setItem("PPS_SELECTED_USER_ID", String(created.id));
      state.users = await fetchJson("/api/users");
      renderLandingUserOptions();
      await loadUserScope();
      jumpTo("accountSelection");
      showToast("User created", `${created.fullName} created successfully.`, "success");
    } catch (error) {
      showValidationPopup(extractErrorMessage(error));
    }
    setLoadingState(false);
  });
}

async function loadUserScope() {
  if (!state.selectedUserId) {
    return;
  }
  const workspace = await fetchJson(`/api/users/${state.selectedUserId}/workspace`);
  state.accounts = Array.isArray(workspace.accounts) ? workspace.accounts : [];
  state.campaigns = Array.isArray(workspace.campaigns) ? workspace.campaigns : [];
  state.tickets = Array.isArray(workspace.tickets) ? workspace.tickets : [];
  state.dashboard = workspace.dashboard || null;
  if (state.activeAccountId == null || !state.accounts.some((account) => Number(account.id) === Number(state.activeAccountId))) {
    state.activeAccountId = null;
  }
  syncProfile();
  renderAccountSelection();
  renderScopedViews();
}

function renderLandingUserOptions() {
  const select = document.getElementById("landingExistingUserSelect");
  select.innerHTML = "";
  if (!state.users.length) {
    select.appendChild(new Option("No users available", ""));
    return;
  }
  state.users.forEach((user) => {
    select.appendChild(new Option(`${user.fullName} (${user.email})`, String(user.id)));
  });
  if (state.selectedUserId) {
    select.value = String(state.selectedUserId);
  }
}

function renderAccountSelection() {
  const container = document.getElementById("accountSelectionList");
  if (!state.selectedUserId) {
    container.innerHTML = '<article class="card panel"><p>Select or create a user first.</p></article>';
    return;
  }
  if (!state.accounts.length) {
    container.innerHTML = '<article class="card panel"><p>No accounts found for this user.</p></article>';
    return;
  }

  container.innerHTML = state.accounts.map((account) => {
    const active = Number(account.id) === Number(state.activeAccountId);
    return `
      <article class="card panel ${active ? "active-row" : ""}">
        <div class="panel-head">
          <h3>${account.accountType || "Account"}</h3>
          <small>${active ? "ACTIVE ACCOUNT" : "Select"}</small>
        </div>
        <div class="kv-grid">
          <div class="kv-row"><span>Account Number</span><strong>${account.accountNumber || "-"}</strong></div>
          <div class="kv-row"><span>Currency</span><strong>${account.currencyCode || "-"}</strong></div>
          <div class="kv-row"><span>Balance</span><strong>${formatAmount(account.balance)}</strong></div>
          <div class="kv-row"><span>Status</span><strong>${account.accountStatus || "-"}</strong></div>
        </div>
        <div class="form-actions" style="margin-top:0.9rem;">
          <button class="btn btn-primary" type="button" data-select-account="${account.id}">Use This Account</button>
        </div>
      </article>
    `;
  }).join("");

  container.querySelectorAll("[data-select-account]").forEach((button) => {
    button.addEventListener("click", async () => {
      state.activeAccountId = Number(button.getAttribute("data-select-account"));
      window.localStorage.setItem("PPS_ACTIVE_ACCOUNT_ID", String(state.activeAccountId));
      await refreshScopedCollections();
      renderAccountSelection();
      renderScopedViews();
      jumpTo("workspace");
    });
  });
}

function initPaymentTypeToggle() {
  const regularBtn = document.getElementById("paymentTypeRegular");
  const crowdfundingBtn = document.getElementById("paymentTypeCrowdfunding");
  const typeInput = document.getElementById("paymentType");
  const campaignField = document.getElementById("campaignField");
  const destinationCurrencyField = document.getElementById("destinationCurrencyField");

  const apply = (isCrowdfunding) => {
    typeInput.value = isCrowdfunding ? "CROWDFUNDING" : "REGULAR";
    regularBtn.classList.toggle("active", !isCrowdfunding);
    crowdfundingBtn.classList.toggle("active", isCrowdfunding);
    campaignField.hidden = !isCrowdfunding;
    destinationCurrencyField.hidden = isCrowdfunding;
  };

  regularBtn.addEventListener("click", () => apply(false));
  crowdfundingBtn.addEventListener("click", () => apply(true));
  apply(false);
}

function initPaymentForm() {
  const form = document.getElementById("paymentForm");
  const resetBtn = document.getElementById("resetPaymentFormBtn");
  const dialog = document.getElementById("confirmDialog");

  document.getElementById("sourceAccountId").addEventListener("change", populateDestinationOptions);
  document.getElementById("crowdfundingCampaignId").addEventListener("change", syncCampaignDestination);

  resetBtn.addEventListener("click", () => {
    form.reset();
    populatePaymentFormOptions();
  });

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    if (!state.selectedUserId || !state.activeAccountId) {
      showValidationPopup("Select user and active account first.");
      return;
    }

    const payload = buildPaymentPayload();
    if (!payload) {
      return;
    }

    state.pendingPaymentPayload = payload;
    document.getElementById("confirmText").textContent = `Proceed with ${payload.paymentType} payment of ${payload.currencyCode} ${Number(payload.amount).toFixed(2)}?`;
    dialog.showModal();
  });

  dialog.addEventListener("close", async () => {
    if (dialog.returnValue !== "confirm" || !state.pendingPaymentPayload) {
      state.pendingPaymentPayload = null;
      return;
    }

    setLoadingState(true);
    try {
      const created = await fetchJson("/api/payments", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(state.pendingPaymentPayload)
      });
      state.pendingPaymentPayload = null;
      await refreshScopedCollections(created.id);
      renderScopedViews();
      showToast("Payment processed", `${created.paymentReference} status: ${created.status}`, "success");
    } catch (error) {
      showValidationPopup(extractErrorMessage(error));
    }
    setLoadingState(false);
  });
}

function buildPaymentPayload() {
  const paymentType = document.getElementById("paymentType").value;
  const sourceAccountId = Number(document.getElementById("sourceAccountId").value || state.activeAccountId);
  const destinationAccountId = Number(document.getElementById("destinationAccountId").value);
  const amount = Number(document.getElementById("amount").value);
  const currencyCode = document.getElementById("currencyCode").value;

  if (!amount || amount <= 0) {
    showValidationPopup("Payment Failed - Amount must be greater than zero.");
    return null;
  }

  const sourceAccount = state.accounts.find((account) => Number(account.id) === sourceAccountId);
  if (!sourceAccount) {
    showValidationPopup("Payment Failed - Source account does not exist.");
    return null;
  }

  const payload = {
    userId: state.selectedUserId,
    sourceAccountId,
    destinationAccountId,
    amount,
    currencyCode,
    destinationCurrencyCode: document.getElementById("destinationCurrencyCode").value,
    paymentType,
    sourceAccountNumber: sourceAccount.accountNumber || null,
    destinationAccountNumber: (state.accounts.find((account) => Number(account.id) === destinationAccountId) || {}).accountNumber || null,
    idempotencyKey: `${state.selectedUserId}-${sourceAccountId}-${destinationAccountId}-${amount}-${currencyCode}-${paymentType}`
  };

  if (paymentType === "CROWDFUNDING") {
    payload.crowdfundingCampaignId = Number(document.getElementById("crowdfundingCampaignId").value);
  }

  return payload;
}

function populatePaymentFormOptions() {
  const sourceSelect = document.getElementById("sourceAccountId");
  const campaignSelect = document.getElementById("crowdfundingCampaignId");

  sourceSelect.innerHTML = "";
  if (!state.accounts.length) {
    sourceSelect.appendChild(new Option("No user accounts", ""));
    return;
  }

  const ordered = [...state.accounts].sort((a, b) => Number(a.id) - Number(b.id));
  ordered.forEach((account) => {
    sourceSelect.appendChild(new Option(`${account.accountType || "Account"} - ${account.accountNumber || account.id} (${account.currencyCode})`, String(account.id)));
  });

  sourceSelect.value = String(state.activeAccountId || ordered[0].id);
  const sourceAccount = state.accounts.find((account) => Number(account.id) === Number(sourceSelect.value));
  if (sourceAccount) {
    document.getElementById("currencyCode").value = sourceAccount.currencyCode;
  }

  campaignSelect.innerHTML = "";
  state.campaigns.filter((campaign) => campaign.status === "ACTIVE").forEach((campaign) => {
    campaignSelect.appendChild(new Option(`${campaign.campaignName} (${campaign.targetCurrency})`, String(campaign.id)));
  });

  populateDestinationOptions();
  syncCampaignDestination();
}

function populateDestinationOptions() {
  const sourceId = Number(document.getElementById("sourceAccountId").value || state.activeAccountId);
  const destinationSelect = document.getElementById("destinationAccountId");
  destinationSelect.innerHTML = "";

  state.accounts
    .filter((account) => Number(account.id) !== sourceId)
    .forEach((account) => {
      destinationSelect.appendChild(new Option(`${account.accountType || "Account"} - ${account.accountNumber || account.id} (${account.currencyCode})`, String(account.id)));
    });

  if (!destinationSelect.options.length) {
    destinationSelect.appendChild(new Option("No destination accounts", ""));
    return;
  }

  destinationSelect.selectedIndex = 0;
  const destination = state.accounts.find((account) => Number(account.id) === Number(destinationSelect.value));
  if (destination) {
    document.getElementById("destinationCurrencyCode").value = destination.currencyCode;
  }
}

function syncCampaignDestination() {
  const campaignId = Number(document.getElementById("crowdfundingCampaignId").value);
  const campaign = state.campaigns.find((item) => Number(item.id) === campaignId);
  if (!campaign) {
    return;
  }
  document.getElementById("destinationAccountId").value = String(campaign.bucketAccountId);
  document.getElementById("destinationCurrencyCode").value = campaign.targetCurrency;
}

function initPaymentMonitor() {
  ["paymentSearch", "paymentStatusFilter", "paymentTypeFilter", "paymentCurrencyFilter", "paymentFromDate", "paymentToDate", "paymentSourceAccountFilter", "paymentDestinationAccountFilter"]
    .forEach((id) => {
      const element = document.getElementById(id);
      if (element) {
        element.addEventListener("input", refreshPaymentsOnly);
        element.addEventListener("change", refreshPaymentsOnly);
      }
    });

  document.getElementById("refreshPaymentsBtn").addEventListener("click", refreshPaymentsOnly);
  document.getElementById("exportCsvBtn").addEventListener("click", exportFilteredCsv);
  document.getElementById("exportPdfBtn").addEventListener("click", exportFilteredPdf);
}

async function refreshPaymentsOnly() {
  await refreshScopedCollections(state.selectedPaymentId);
  renderScopedViews();
}

async function refreshScopedCollections(selectPaymentId) {
  if (!state.selectedUserId || !state.activeAccountId) {
    state.payments = [];
    state.filteredPayments = [];
    return;
  }

  const params = new URLSearchParams();
  params.set("userId", String(state.selectedUserId));
  params.set("sortBy", "date");
  params.set("sortDir", "desc");

  const status = document.getElementById("paymentStatusFilter").value;
  const type = document.getElementById("paymentTypeFilter").value;
  const currency = document.getElementById("paymentCurrencyFilter").value;
  const query = document.getElementById("paymentSearch").value.trim();
  const fromDate = document.getElementById("paymentFromDate").value;
  const toDate = document.getElementById("paymentToDate").value;
  const sourceFilter = document.getElementById("paymentSourceAccountFilter").value;
  const destinationFilter = document.getElementById("paymentDestinationAccountFilter").value;

  if (status !== "ALL") params.append("status", status);
  if (type !== "ALL") params.append("paymentType", type);
  if (currency !== "ALL") params.append("currency", currency);
  if (query) params.append("reference", query);
  if (fromDate) params.append("fromDate", fromDate);
  if (toDate) params.append("toDate", toDate);
  if (sourceFilter !== "ALL") params.append("sourceAccountId", sourceFilter);
  if (destinationFilter !== "ALL") params.append("destinationAccountId", destinationFilter);

  const userScope = await fetchJson(`/api/users/${state.selectedUserId}/workspace`);
  state.accounts = Array.isArray(userScope.accounts) ? userScope.accounts : [];
  state.campaigns = Array.isArray(userScope.campaigns) ? userScope.campaigns : [];
  state.tickets = Array.isArray(userScope.tickets) ? userScope.tickets : [];
  state.dashboard = userScope.dashboard || null;

  const allUserPayments = await fetchJson(`/api/payments?${params.toString()}`);
  const accountScoped = (Array.isArray(allUserPayments) ? allUserPayments : []).filter((payment) => {
    return Number(payment.sourceAccountId) === Number(state.activeAccountId)
      || Number(payment.destinationAccountId) === Number(state.activeAccountId);
  });

  state.payments = accountScoped;
  state.filteredPayments = accountScoped;

  renderMonitorFilters();

  if (state.payments.length) {
    const nextId = selectPaymentId && state.payments.some((payment) => Number(payment.id) === Number(selectPaymentId))
      ? selectPaymentId
      : state.payments[0].id;
    await selectPayment(nextId);
  } else {
    state.selectedPaymentId = null;
    renderPaymentSnapshot(null);
    document.getElementById("auditTimeline").innerHTML = "<li><small>No payment selected.</small></li>";
    document.getElementById("ticketList").innerHTML = '<div class="ticket-item"><p>No payment selected.</p></div>';
  }
}

function renderMonitorFilters() {
  const sourceSelect = document.getElementById("paymentSourceAccountFilter");
  const destinationSelect = document.getElementById("paymentDestinationAccountFilter");
  const currentSource = sourceSelect.value;
  const currentDestination = destinationSelect.value;

  sourceSelect.innerHTML = '<option value="ALL">ALL</option>';
  destinationSelect.innerHTML = '<option value="ALL">ALL</option>';

  state.accounts.forEach((account) => {
    const label = `${account.accountNumber || account.id} (${account.currencyCode})`;
    sourceSelect.appendChild(new Option(label, String(account.id)));
    destinationSelect.appendChild(new Option(label, String(account.id)));
  });

  if ([...sourceSelect.options].some((option) => option.value === currentSource)) sourceSelect.value = currentSource;
  if ([...destinationSelect.options].some((option) => option.value === currentDestination)) destinationSelect.value = currentDestination;
}

function renderScopedViews() {
  populatePaymentFormOptions();
  renderPaymentsTable();
  renderDataHub();
  updateKpis();
  updateDashboardSummary();
  renderRecentTransactions();
  renderSpendingCategories();
  renderCampaignCards();
  renderTicketsPage();
}

function renderPaymentsTable() {
  const tbody = document.getElementById("paymentsTableBody");
  const empty = document.getElementById("paymentsEmptyState");
  tbody.innerHTML = "";

  if (!state.filteredPayments.length) {
    empty.hidden = false;
    return;
  }
  empty.hidden = true;

  state.filteredPayments.forEach((payment) => {
    const row = document.createElement("tr");
    if (Number(payment.id) === Number(state.selectedPaymentId)) row.classList.add("active-row");
    row.innerHTML = `
      <td><strong>${payment.paymentReference || "-"}</strong></td>
      <td>${payment.sourceAccountId} → ${payment.destinationAccountId}</td>
      <td>${payment.currencyCode} ${formatAmount(payment.amount)}</td>
      <td>${payment.paymentType || "REGULAR"}</td>
      <td><span class="status-chip ${statusClass(payment.status)}">${payment.status}</span></td>
      <td>${formatDateTime(payment.createdAt)}</td>
    `;
    row.addEventListener("click", () => selectPayment(payment.id));
    tbody.appendChild(row);
  });
}

async function selectPayment(paymentId) {
  state.selectedPaymentId = Number(paymentId);
  renderPaymentsTable();
  const payment = state.payments.find((item) => Number(item.id) === Number(paymentId));
  renderPaymentSnapshot(payment || null);
  await renderPaymentHistory(paymentId);
  await renderRelatedTickets(paymentId);
}

function renderPaymentSnapshot(payment) {
  const referenceLabel = document.getElementById("selectedPaymentReference");
  const container = document.getElementById("paymentSnapshot");
  if (!payment) {
    referenceLabel.textContent = "Select a payment row";
    container.innerHTML = "";
    return;
  }
  referenceLabel.textContent = payment.paymentReference || `Payment ${payment.id}`;
  const rows = [
    ["Reference", payment.paymentReference || "-"],
    ["Source", payment.sourceAccountId],
    ["Destination", payment.destinationAccountId],
    ["Amount", `${payment.currencyCode} ${formatAmount(payment.amount)}`],
    ["Payment Type", payment.paymentType],
    ["Current Status", payment.status],
    ["Created Date", formatDateTime(payment.createdAt)],
    ["Completed Date", formatDateTime(payment.completedAt)],
    ["Error", payment.errorCode || "-"]
  ];
  container.innerHTML = rows.map((row) => `<div class="kv-row"><span>${row[0]}</span><strong>${row[1]}</strong></div>`).join("");
}

async function renderPaymentHistory(paymentId) {
  const timeline = document.getElementById("auditTimeline");
  timeline.innerHTML = "<li><small>Loading timeline...</small></li>";
  try {
    const history = await fetchJson(`/api/payments/${paymentId}/history`);
    if (!Array.isArray(history) || !history.length) {
      timeline.innerHTML = "<li><small>No status transitions recorded.</small></li>";
      return;
    }
    timeline.innerHTML = history.map((item) => {
      return `<li><strong>${item.fromStatus || "-"} → ${item.toStatus}</strong><br><small>${formatDateTime(item.changedAt)} | ${item.description || "Status updated"}</small></li>`;
    }).join("");
  } catch {
    timeline.innerHTML = "<li><small>Unable to load timeline.</small></li>";
  }
}

async function renderRelatedTickets(paymentId) {
  const list = document.getElementById("ticketList");
  const related = state.tickets.filter((ticket) => Number(ticket.paymentId) === Number(paymentId));
  if (!related.length) {
    list.innerHTML = '<div class="ticket-item"><p>No related tickets for this payment.</p></div>';
    return;
  }
  list.innerHTML = related.map((ticket) => `
    <article class="ticket-item">
      <h4>${ticket.ticketNumber}</h4>
      <p>${ticket.title}</p>
      <p>${ticket.priority} | ${ticket.status}</p>
    </article>
  `).join("");
}

function renderCampaignCards() {
  const list = document.getElementById("campaignsList");
  list.innerHTML = state.campaigns.map((campaign) => {
    const target = Number(campaign.targetAmount || 0);
    const current = Number(campaign.currentAmount || 0);
    const remaining = Math.max(target - current, 0);
    const percent = target > 0 ? Math.min((current / target) * 100, 100) : 0;
    const daysRemaining = campaign.campaignEndDate ? Math.max(0, Math.floor((new Date(campaign.campaignEndDate).getTime() - Date.now()) / 86400000)) : 0;
    return `
      <article class="list-item">
        <div>
          <h4>${campaign.campaignName}</h4>
          <p>${campaign.description || ""}</p>
          <p>Target ${campaign.targetCurrency} ${formatAmount(target)} | Collected ${formatAmount(current)} | Remaining ${formatAmount(remaining)}</p>
          <p>${percent.toFixed(2)}% complete | ${daysRemaining} days remaining</p>
          <div class="metric-box" style="margin-top:0.35rem;padding:0.4rem;">
            <div style="height:8px;background:var(--border);border-radius:999px;overflow:hidden;">
              <div style="height:8px;width:${percent}%;background:var(--accent-grad);"></div>
            </div>
          </div>
        </div>
      </article>
    `;
  }).join("");
}

function updateKpis() {
  const total = state.payments.length;
  const completed = state.payments.filter((payment) => payment.status === "COMPLETED").length;
  const failed = state.payments.filter((payment) => payment.status === "FAILED").length;
  animateKpi(document.querySelector("[data-kpi='payments']"), total);
  animateKpi(document.querySelector("[data-kpi='completed']"), completed);
  animateKpi(document.querySelector("[data-kpi='failed']"), failed);
}

function updateDashboardSummary() {
  const d = state.dashboard || {};
  const total = Number(d.totalPayments || 0);
  const success = Number(d.successfulPayments || 0);
  const successRate = total === 0 ? 0 : Math.round((success / total) * 100);

  document.getElementById("totalBalanceValue").textContent = formatAmount(d.totalBalance || 0);
  document.getElementById("incomeValue").textContent = formatAmount(d.income || 0);
  document.getElementById("expenseValue").textContent = formatAmount(d.expense || 0);
  document.getElementById("successRateValue").textContent = `${successRate}%`;
  document.getElementById("averageTransactionValue").textContent = formatAmount(d.averageTransactionAmount || 0);
  document.getElementById("largestTransactionValue").textContent = formatAmount(d.largestTransaction || 0);
  document.getElementById("crowdfundingDonationValue").textContent = formatAmount(d.crowdfundingDonations || 0);

  const active = state.accounts.find((account) => Number(account.id) === Number(state.activeAccountId));
  if (!active) {
    document.getElementById("walletAccountNumber").textContent = "No wallet selected";
    document.getElementById("walletBalanceValue").textContent = "0.00";
    document.getElementById("walletMeta").textContent = "Currency - Status - Available";
    return;
  }
  document.getElementById("walletAccountNumber").textContent = active.accountNumber || `Account ${active.id}`;
  document.getElementById("walletBalanceValue").textContent = `${active.currencyCode} ${formatAmount(active.balance)}`;
  document.getElementById("walletMeta").textContent = `${active.accountType || "Account"} | ${active.accountStatus} | Available ${formatAmount(active.balance)}`;
}

function renderRecentTransactions() {
  const list = document.getElementById("recentTransactionsList");
  const recent = [...state.payments].sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0)).slice(0, 5);
  if (!recent.length) {
    list.innerHTML = '<article class="stack-item"><strong>No transactions yet</strong><p>Create your first payment to see activity.</p></article>';
    return;
  }
  list.innerHTML = recent.map((payment) => `
    <article class="stack-item">
      <strong>${payment.paymentReference || `Payment ${payment.id}`}</strong>
      <p>${payment.currencyCode} ${formatAmount(payment.amount)} | ${payment.status}</p>
    </article>
  `).join("");
}

function renderSpendingCategories() {
  const list = document.getElementById("spendingCategoryList");
  const groups = state.payments.reduce((acc, payment) => {
    const key = payment.paymentType || "REGULAR";
    acc[key] = (acc[key] || 0) + Number(payment.amount || 0);
    return acc;
  }, {});
  const entries = Object.entries(groups).sort((a, b) => b[1] - a[1]);
  if (!entries.length) {
    list.innerHTML = '<article class="stack-item"><strong>No categories yet</strong><p>Categories will appear as payments are created.</p></article>';
    return;
  }
  list.innerHTML = entries.map(([type, amount]) => `
    <article class="stack-item">
      <strong>${type}</strong>
      <p>Total ${formatAmount(amount)}</p>
    </article>
  `).join("");
}

function renderDataHub() {
  const openTickets = state.tickets.filter((ticket) => ticket.status === "OPEN" || ticket.status === "IN_PROGRESS");
  document.getElementById("accountsCount").textContent = String(state.accounts.length);
  document.getElementById("usersCount").textContent = String(state.users.length);
  document.getElementById("campaignsCount").textContent = String(state.campaigns.length);
  document.getElementById("openTicketsCount").textContent = String(openTickets.length);

  document.getElementById("accountsList").innerHTML = state.accounts.map((account) => `
    <article class="list-item ${Number(account.id) === Number(state.activeAccountId) ? "active-row" : ""}">
      <div>
        <h4>${account.accountType || "Account"} - ${account.accountNumber || account.id}</h4>
        <p>${account.currencyCode} | Balance ${formatAmount(account.balance)} | ${account.accountStatus}</p>
      </div>
    </article>
  `).join("");

  document.getElementById("usersList").innerHTML = state.users.map((user) => `
    <article class="list-item ${Number(user.id) === Number(state.selectedUserId) ? "active-row" : ""}">
      <div>
        <h4>${user.fullName}</h4>
        <p>${user.email}</p>
      </div>
    </article>
  `).join("");
}

function initTicketsModule() {
  document.getElementById("raiseTicketBtn").addEventListener("click", raiseTicket);
  document.getElementById("ticketSearch").addEventListener("input", renderTicketsPage);
  document.getElementById("ticketStatusFilter").addEventListener("change", renderTicketsPage);

  const validationDialog = document.getElementById("validationDialog");
  validationDialog.addEventListener("close", () => {
    if (validationDialog.returnValue === "ticket") {
      jumpTo("tickets");
      document.getElementById("ticketDescription").focus();
      if (state.selectedPaymentId) {
        document.getElementById("ticketPaymentId").value = String(state.selectedPaymentId);
      }
    }
  });
}

async function raiseTicket() {
  if (!state.selectedUserId || !state.activeAccountId) {
    showValidationPopup("Select a user and active account before raising a ticket.");
    return;
  }

  const payload = {
    userId: state.selectedUserId,
    accountId: state.activeAccountId,
    paymentId: document.getElementById("ticketPaymentId").value ? Number(document.getElementById("ticketPaymentId").value) : null,
    ticketType: document.getElementById("ticketReason").value,
    priority: document.getElementById("ticketPriority").value,
    title: document.getElementById("ticketTitle").value.trim() || `Issue: ${document.getElementById("ticketReason").value}`,
    description: document.getElementById("ticketDescription").value.trim(),
    status: "OPEN"
  };

  if (!payload.description) {
    showValidationPopup("Ticket description is required.");
    return;
  }

  try {
    await fetchJson("/api/tickets", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const workspace = await fetchJson(`/api/users/${state.selectedUserId}/workspace`);
    state.tickets = Array.isArray(workspace.tickets) ? workspace.tickets : [];
    renderTicketsPage();
    showToast("Ticket raised", "Support ticket created successfully.", "success");
  } catch (error) {
    showValidationPopup(extractErrorMessage(error));
  }
}

function renderTicketsPage() {
  const query = document.getElementById("ticketSearch").value.trim().toLowerCase();
  const status = document.getElementById("ticketStatusFilter").value;
  const panel = document.getElementById("ticketsListPanel");

  const filtered = state.tickets
    .filter((ticket) => !query || String(ticket.ticketNumber || "").toLowerCase().includes(query) || String(ticket.title || "").toLowerCase().includes(query))
    .filter((ticket) => status === "ALL" || ticket.status === status)
    .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));

  if (!filtered.length) {
    panel.innerHTML = '<article class="list-item"><p>No tickets match this filter.</p></article>';
    return;
  }

  panel.innerHTML = filtered.map((ticket) => `
    <article class="list-item">
      <div>
        <h4>${ticket.ticketNumber || `Ticket ${ticket.id}`}</h4>
        <p>${ticket.title}</p>
        <p>${ticket.ticketType} | ${ticket.priority} | ${ticket.status}</p>
        <p>Payment ${ticket.paymentId || "N/A"} | Created ${formatDateTime(ticket.createdAt)}</p>
      </div>
      <div style="display:flex;gap:0.4rem;flex-wrap:wrap;margin-top:0.5rem;">
        <button class="btn btn-soft" data-ticket-status="IN_PROGRESS" data-ticket-id="${ticket.id}" type="button">IN_PROGRESS</button>
        <button class="btn btn-soft" data-ticket-status="RESOLVED" data-ticket-id="${ticket.id}" type="button">RESOLVE</button>
        <button class="btn btn-soft" data-ticket-status="CLOSED" data-ticket-id="${ticket.id}" type="button">CLOSE</button>
      </div>
    </article>
  `).join("");

  panel.querySelectorAll("[data-ticket-id]").forEach((button) => {
    button.addEventListener("click", async () => {
      await updateTicketStatus(Number(button.getAttribute("data-ticket-id")), button.getAttribute("data-ticket-status"));
    });
  });
}

async function updateTicketStatus(ticketId, status) {
  try {
    await fetchJson(`/api/tickets/${ticketId}/status?status=${encodeURIComponent(status)}`, {
      method: "PATCH"
    });
    const workspace = await fetchJson(`/api/users/${state.selectedUserId}/workspace`);
    state.tickets = Array.isArray(workspace.tickets) ? workspace.tickets : [];
    renderTicketsPage();
    showToast("Ticket updated", `Ticket moved to ${status}.`, "success");
  } catch (error) {
    showValidationPopup(extractErrorMessage(error));
  }
}

function exportFilteredCsv() {
  if (!state.filteredPayments.length) {
    showToast("Export skipped", "No filtered payments to export.", "warn");
    return;
  }

  const headers = ["Reference", "Source", "Destination", "Amount", "Currency", "Type", "Status", "Date"];
  const rows = state.filteredPayments.map((payment) => [
    payment.paymentReference || "",
    payment.sourceAccountId || "",
    payment.destinationAccountId || "",
    formatAmount(payment.amount),
    payment.currencyCode || "",
    payment.paymentType || "",
    payment.status || "",
    formatDateTime(payment.createdAt)
  ]);

  const csv = [headers, ...rows]
    .map((row) => row.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(","))
    .join("\n");

  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `payments-${Date.now()}.csv`;
  anchor.click();
  URL.revokeObjectURL(url);
}

function exportFilteredPdf() {
  if (!state.filteredPayments.length) {
    showToast("Export skipped", "No filtered payments to export.", "warn");
    return;
  }

  const html = `
    <html>
      <head><title>Filtered Payments</title></head>
      <body>
        <h2>Filtered Payment Report</h2>
        <table border="1" cellspacing="0" cellpadding="6">
          <thead>
            <tr>
              <th>Reference</th><th>Source</th><th>Destination</th><th>Amount</th><th>Currency</th><th>Type</th><th>Status</th><th>Date</th>
            </tr>
          </thead>
          <tbody>
            ${state.filteredPayments.map((payment) => `
              <tr>
                <td>${payment.paymentReference || ""}</td>
                <td>${payment.sourceAccountId || ""}</td>
                <td>${payment.destinationAccountId || ""}</td>
                <td>${formatAmount(payment.amount)}</td>
                <td>${payment.currencyCode || ""}</td>
                <td>${payment.paymentType || ""}</td>
                <td>${payment.status || ""}</td>
                <td>${formatDateTime(payment.createdAt)}</td>
              </tr>
            `).join("")}
          </tbody>
        </table>
      </body>
    </html>
  `;

  const popup = window.open("", "_blank");
  popup.document.write(html);
  popup.document.close();
  popup.focus();
  popup.print();
}

async function refreshAll() {
  setLoadingState(true);
  try {
    state.users = await fetchJson("/api/users");
    renderLandingUserOptions();
    if (state.selectedUserId) {
      await loadUserScope();
      if (state.activeAccountId) {
        await refreshScopedCollections(state.selectedPaymentId);
      }
      renderScopedViews();
    }
    showToast("Refreshed", "Workspace data refreshed.", "success");
  } catch (error) {
    showValidationPopup(extractErrorMessage(error));
  }
  setLoadingState(false);
}

function syncProfile() {
  const profileName = document.getElementById("profileName");
  const profileSubtext = document.getElementById("profileSubtext");
  const avatar = document.querySelector(".avatar");
  const user = state.users.find((item) => Number(item.id) === Number(state.selectedUserId));

  if (!user) {
    profileName.textContent = "Workspace User";
    profileSubtext.textContent = "Payments Team";
    avatar.textContent = "U";
    return;
  }

  profileName.textContent = user.fullName;
  profileSubtext.textContent = user.email || "Payments Team";
  avatar.textContent = String(user.fullName || "U").charAt(0).toUpperCase();
}

function resolveStoredUser() {
  const storedUserId = window.localStorage.getItem("PPS_SELECTED_USER_ID");
  const storedAccountId = window.localStorage.getItem("PPS_ACTIVE_ACCOUNT_ID");
  if (storedAccountId) {
    state.activeAccountId = Number(storedAccountId);
  }
  if (storedUserId && state.users.some((user) => Number(user.id) === Number(storedUserId))) {
    return Number(storedUserId);
  }
  return state.users.length ? Number(state.users[0].id) : null;
}

function showValidationPopup(message) {
  const dialog = document.getElementById("validationDialog");
  document.getElementById("validationMessage").textContent = message || "Validation failed.";
  if (!dialog.open) {
    dialog.showModal();
  }
}

function jumpTo(sectionId) {
  const section = document.getElementById(sectionId);
  if (section) {
    section.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

function animateKpi(element, target) {
  if (!element) return;
  const start = performance.now();
  const duration = 700;
  const initial = Number(element.textContent) || 0;
  function frame(now) {
    const progress = Math.min((now - start) / duration, 1);
    const value = Math.round(initial + (target - initial) * (1 - Math.pow(1 - progress, 3)));
    element.textContent = value.toLocaleString();
    if (progress < 1) requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
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
        // Ignore parse issues.
      }
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

function hideLoadingScreen() {
  const loading = document.getElementById("loadingScreen");
  window.setTimeout(() => loading.classList.add("hidden"), 450);
}

function setLoadingState(isLoading) {
  document.body.classList.toggle("is-busy", Boolean(isLoading));
}

function formatAmount(value) {
  const number = Number(value);
  return Number.isNaN(number) ? "0.00" : number.toFixed(2);
}

function formatDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return `${date.toLocaleDateString()} ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
}

function statusClass(status) {
  return `status-${String(status || "").toLowerCase()}`;
}

function showToast(title, message, type) {
  const stack = document.getElementById("toastStack");
  const toast = document.createElement("article");
  toast.className = `toast ${type || "success"}`;
  toast.innerHTML = `<strong>${title}</strong><p>${message}</p>`;
  stack.appendChild(toast);
  window.setTimeout(() => toast.remove(), 3200);
}

function extractErrorMessage(error) {
  if (error && typeof error.message === "string" && error.message.trim()) {
    return error.message;
  }
  return "Unexpected API error.";
}

