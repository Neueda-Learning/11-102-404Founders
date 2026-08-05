const API_BASE = (window.PAYMENT_API_BASE || window.localStorage.getItem("PAYMENT_API_BASE") || "http://localhost:8080").replace(/\/$/, "");
const THEME_KEY = "PPS_THEME";
const state = {
users: [],
accounts: [],
selectedUserAccounts: [],
campaigns: [],
payments: [],
tickets: [],
selectedUserId: null,
selectedPaymentId: null,
pendingPaymentPayload: null
};
document.addEventListener("DOMContentLoaded", async () => {
applySavedTheme();
requestAnimationFrame(() => document.body.classList.add("is-loaded"));
initNavbar();
initThemeToggle();
initProfileMenu();
initReveal();
initFormInteractions();
initFilters();
initQuickActions();
initUserManagement();
initAddAccount();
initStatusActions();
await bootstrapData();
hideLoadingScreen();
});
async function bootstrapData() {
setLoadingState(true);
try {
const [users, accounts, campaigns, payments, tickets] = await Promise.all([
fetchJson("/api/users"),
fetchJson("/api/accounts"),
fetchJson("/api/campaigns"),
fetchJson("/api/payments"),
fetchJson("/api/tickets")
]);
state.users = Array.isArray(users) ? users : [];
state.accounts = Array.isArray(accounts) ? accounts : [];
state.campaigns = Array.isArray(campaigns) ? campaigns : [];
state.payments = Array.isArray(payments) ? payments : [];
state.tickets = Array.isArray(tickets) ? tickets : [];
state.selectedUserId = resolveSelectedUserId();
await refreshSelectedUserAccounts();
} catch (error) {
state.users = [];
state.accounts = [];
state.selectedUserAccounts = [];
state.campaigns = [];
state.payments = [];
state.tickets = [];
showToast("API connection failed", extractErrorMessage(error), "error");
}
renderUserSelector();
populatePaymentFormOptions();
renderPaymentsTable();
renderDataHub();
updateKpis();
updateDashboardSummary();
renderRecentTransactions();
renderSpendingCategories();
syncProfile();
updateSelectedUserHint();
if (state.payments.length > 0) {
await selectPayment(state.payments[0].id);
} else {
renderPaymentSnapshot(null);
document.getElementById("auditTimeline").innerHTML = "<li><small>No payment selected.</small></li>";
document.getElementById("ticketList").innerHTML = '<div class="ticket-item"><p>No payment selected.</p></div>';
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
navLinks.querySelectorAll("a").forEach((link) => {
link.addEventListener("click", () => {
navLinks.classList.remove("open");
navToggle.setAttribute("aria-expanded", "false");
});
});
document.getElementById("refreshAllBtn").addEventListener("click", async () => {
showToast("Refreshing", "Fetching latest backend data...", "warn");
await bootstrapData();
showToast("Refreshed", "Workspace is synced with API data.", "success");
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
dropdown.querySelectorAll("button").forEach((btn) => {
btn.addEventListener("click", () => {
showToast("Profile", `${btn.textContent} is UI-only in this demo.`, "warn");
dropdown.setAttribute("hidden", "hidden");
trigger.setAttribute("aria-expanded", "false");
});
});
}
function syncProfile() {
const profileName = document.getElementById("profileName");
const profileSubtext = document.getElementById("profileSubtext");
const avatar = document.querySelector(".avatar");
const selectedUser = getSelectedUser();
if (!selectedUser) {
profileName.textContent = "Workspace User";
profileSubtext.textContent = "Payments Team";
avatar.textContent = "U";
return;
}
profileName.textContent = selectedUser.fullName;
profileSubtext.textContent = selectedUser.email || "Payments Team";
avatar.textContent = String(selectedUser.fullName || "U").trim().charAt(0).toUpperCase() || "U";
}
function initReveal() {
const observer = new IntersectionObserver((entries) => {
entries.forEach((entry) => {
if (entry.isIntersecting) {
entry.target.classList.add("visible");
observer.unobserve(entry.target);
}
});
}, { threshold: 0.14 });
document.querySelectorAll("[data-reveal]").forEach((section) => observer.observe(section));
}
function initQuickActions() {
document.getElementById("quickActionCreate").addEventListener("click", () => jumpTo("workspace"));
document.getElementById("quickActionTransactions").addEventListener("click", () => jumpTo("payments"));
document.getElementById("quickActionAudit").addEventListener("click", () => jumpTo("details"));
document.getElementById("quickActionDataHub").addEventListener("click", () => jumpTo("datahub"));
}
function jumpTo(sectionId) {
document.getElementById(sectionId).scrollIntoView({ behavior: "smooth", block: "start" });
}
function initUserManagement() {
const userSelect = document.getElementById("selectedUserId");
userSelect.addEventListener("change", async () => {
state.selectedUserId = userSelect.value ? Number(userSelect.value) : null;
window.localStorage.setItem("PPS_SELECTED_USER_ID", state.selectedUserId ? String(state.selectedUserId) : "");
await refreshSelectedUserAccounts();
populatePaymentFormOptions();
renderDataHub();
updateDashboardSummary();
syncProfile();
updateSelectedUserHint();
});
const addUserButtons = [document.getElementById("addUserBtn"), document.getElementById("addUserBtnDataHub")].filter(Boolean);
const addUserDialog = document.getElementById("addUserDialog");
const cancelAddUserBtn = document.getElementById("cancelAddUserBtn");
const submitAddUserBtn = document.getElementById("submitAddUserBtn");
const addUserError = document.getElementById("addUserError");
addUserButtons.forEach((button) => {
button.addEventListener("click", () => {
document.getElementById("addUserForm").reset();
addUserError.style.display = "none";
addUserDialog.showModal();
});
});
cancelAddUserBtn.addEventListener("click", () => addUserDialog.close());
submitAddUserBtn.addEventListener("click", async () => {
const fullName = document.getElementById("newUserName").value.trim();
const email = document.getElementById("newUserEmail").value.trim();
if (!fullName) {
showAddUserError("Full name is required.");
return;
}
if (!email) {
showAddUserError("Email is required.");
return;
}
submitAddUserBtn.disabled = true;
submitAddUserBtn.textContent = "Creating...";
try {
const created = await fetchJson("/api/users", {
method: "POST",
headers: { "Content-Type": "application/json" },
body: JSON.stringify({ fullName, email })
});
state.users = await fetchJson("/api/users");
state.selectedUserId = created.id;
window.localStorage.setItem("PPS_SELECTED_USER_ID", String(created.id));
await refreshSelectedUserAccounts();
renderUserSelector();
populatePaymentFormOptions();
renderDataHub();
updateDashboardSummary();
syncProfile();
updateSelectedUserHint();
addUserDialog.close();
showToast("User created", `${created.fullName} is ready. Add an account next.`, "success");
window.setTimeout(() => document.getElementById("addAccountBtn").click(), 150);
} catch (error) {
showAddUserError(extractErrorMessage(error));
} finally {
submitAddUserBtn.disabled = false;
submitAddUserBtn.textContent = "Create User";
}
});
function showAddUserError(message) {
addUserError.textContent = message;
addUserError.style.display = "block";
}
}
function initFormInteractions() {
const paymentTypeSelect = document.getElementById("paymentType");
const campaignField = document.getElementById("campaignField");
const campaignSelect = document.getElementById("crowdfundingCampaignId");
const sourceAccountSelect = document.getElementById("sourceAccountId");
const destinationAccountSelect = document.getElementById("destinationAccountId");
const currencyCodeSelect = document.getElementById("currencyCode");
const form = document.getElementById("paymentForm");
const resetBtn = document.getElementById("resetPaymentFormBtn");
const dialog = document.getElementById("confirmDialog");
const confirmText = document.getElementById("confirmText");
sourceAccountSelect.addEventListener("change", () => {
const selectedAccount = state.selectedUserAccounts.find((account) => Number(account.id) === Number(sourceAccountSelect.value));
if (selectedAccount) {
currencyCodeSelect.value = selectedAccount.currencyCode;
}
});
destinationAccountSelect.addEventListener("change", () => {
const selectedAccount = state.accounts.find((account) => Number(account.id) === Number(destinationAccountSelect.value));
if (selectedAccount && paymentTypeSelect.value === "CROWDFUNDING") {
const maybeCampaign = state.campaigns.find((campaign) => Number(campaign.bucketAccountId) === Number(selectedAccount.id));
if (maybeCampaign) {
document.getElementById("crowdfundingCampaignId").value = String(maybeCampaign.id);
}
}
});
paymentTypeSelect.addEventListener("change", () => {
const isCrowdfunding = paymentTypeSelect.value === "CROWDFUNDING";
campaignField.hidden = !isCrowdfunding;
campaignSelect.required = isCrowdfunding;
});
resetBtn.addEventListener("click", () => {
form.reset();
campaignField.hidden = true;
campaignSelect.required = false;
populatePaymentFormOptions();
showToast("Form reset", "Payment form cleared.", "warn");
});
form.addEventListener("submit", (event) => {
event.preventDefault();
if (!form.reportValidity()) return;
if (!state.selectedUserId) {
showToast("User required", "Select a user before creating a payment.", "error");
return;
}
if (state.selectedUserAccounts.length === 0) {
showToast("Account required", "Create an account for the selected user first.", "error");
return;
}
const sourceAccountId = Number(document.getElementById("sourceAccountId").value);
const sourceAccount = state.selectedUserAccounts.find((account) => Number(account.id) === sourceAccountId);
if (!sourceAccount) {
showToast("Invalid source account", "Select a source account that belongs to the selected user.", "error");
return;
}
const payload = {
userId: state.selectedUserId,
sourceAccountId,
destinationAccountId: Number(document.getElementById("destinationAccountId").value),
amount: Number(document.getElementById("amount").value),
currencyCode: document.getElementById("currencyCode").value,
destinationCurrencyCode: document.getElementById("destinationCurrencyCode") ? document.getElementById("destinationCurrencyCode").value : null,
paymentType: document.getElementById("paymentType").value,
sourceAccountNumber: sourceAccount.accountNumber || null
};
const destinationAccount = state.accounts.find((account) => Number(account.id) === Number(payload.destinationAccountId));
if (destinationAccount && destinationAccount.accountNumber) {
payload.destinationAccountNumber = destinationAccount.accountNumber;
}
if (!payload.destinationCurrencyCode && destinationAccount) {
payload.destinationCurrencyCode = destinationAccount.currencyCode;
}
if (payload.paymentType === "CROWDFUNDING") {
payload.crowdfundingCampaignId = Number(document.getElementById("crowdfundingCampaignId").value);
}
state.pendingPaymentPayload = payload;
confirmText.textContent = `Create ${payload.paymentType} payment of ${payload.currencyCode} ${payload.amount.toFixed(2)} from account ${payload.sourceAccountId} for user ${state.selectedUserId} to ${payload.destinationAccountId}?`;
dialog.showModal();
});
dialog.addEventListener("close", async () => {
if (dialog.returnValue !== "confirm" || !state.pendingPaymentPayload) {
state.pendingPaymentPayload = null;
return;
}
try {
const created = await fetchJson("/api/payments", {
method: "POST",
headers: { "Content-Type": "application/json" },
body: JSON.stringify(state.pendingPaymentPayload)
});
showToast("Payment created", `${created.paymentReference} is now ${created.status}. Balances updated.`, "success");
state.pendingPaymentPayload = null;
await refreshAllDataAfterMutation(created.id);
form.reset();
campaignField.hidden = true;
campaignSelect.required = false;
populatePaymentFormOptions();
} catch (error) {
showToast("Create failed", extractErrorMessage(error), "error");
}
});
}
function initAddAccount() {
const openButtons = [document.getElementById("addAccountBtn"), document.getElementById("quickAddAccountBtn")].filter(Boolean);
const dialog = document.getElementById("addAccountDialog");
const cancelBtn = document.getElementById("cancelAddAccountBtn");
const submitBtn = document.getElementById("submitAddAccountBtn");
const errEl = document.getElementById("addAccountError");
const selectedUserLabel = document.getElementById("addAccountSelectedUser");
openButtons.forEach((button) => {
button.addEventListener("click", () => {
if (!state.selectedUserId) {
showToast("User required", "Create or select a user before adding an account.", "warn");
document.getElementById("addUserBtn").click();
return;
}
document.getElementById("addAccountForm").reset();
errEl.style.display = "none";
const selectedUser = getSelectedUser();
selectedUserLabel.textContent = selectedUser
? `Selected user: ${selectedUser.fullName} (${selectedUser.email})`
: "No user selected.";
dialog.showModal();
});
});
cancelBtn.addEventListener("click", () => dialog.close());
submitBtn.addEventListener("click", async () => {
if (!state.selectedUserId) {
showAddAccountError("Select a user first.");
return;
}
const name = document.getElementById("newAccountName").value.trim();
const balance = document.getElementById("newAccountBalance").value.trim();
const currency = document.getElementById("newAccountCurrency").value;
if (!balance || Number.isNaN(Number(balance)) || Number(balance) < 0) {
showAddAccountError("Please enter a valid initial balance.");
return;
}
const payload = {
accountHolderName: name || null,
currencyCode: currency,
balance: Number(balance),
isBucketAccount: document.getElementById("newAccountIsBucket").checked,
accountNumber: document.getElementById("newAccountNumber").value.trim() || null,
bankName: document.getElementById("newAccountBankName").value.trim() || null,
bankIfsc: document.getElementById("newAccountBankIfsc").value.trim() || null
};
const dailyLimit = document.getElementById("newAccountDailyLimit").value.trim();
if (dailyLimit && !Number.isNaN(Number(dailyLimit))) {
payload.maxDailyLimit = Number(dailyLimit);
}
submitBtn.disabled = true;
submitBtn.textContent = "Creating...";
try {
const created = await fetchJson(`/api/users/${state.selectedUserId}/accounts`, {
method: "POST",
headers: { "Content-Type": "application/json" },
body: JSON.stringify(payload)
});
dialog.close();
await refreshAllDataAfterMutation();
showToast("Account created", `${created.accountHolderName} (${created.currencyCode}) added for selected user.`, "success");
} catch (error) {
showAddAccountError(extractErrorMessage(error));
} finally {
submitBtn.disabled = false;
submitBtn.textContent = "Create Account";
}
});
function showAddAccountError(message) {
errEl.textContent = message;
errEl.style.display = "block";
}
}
function initStatusActions() {
document.getElementById("markCompletedBtn").addEventListener("click", () => updateSelectedPaymentStatus("COMPLETED"));
document.getElementById("markFailedBtn").addEventListener("click", () => updateSelectedPaymentStatus("FAILED"));
document.getElementById("cancelPaymentBtn").addEventListener("click", async () => {
if (!state.selectedPaymentId) return;
try {
await fetchJson(`/api/payments/${state.selectedPaymentId}/cancel`, { method: "PATCH" });
showToast("Cancelled", "Payment cancelled successfully.", "warn");
await refreshAllDataAfterMutation(state.selectedPaymentId);
} catch (error) {
showToast("Cancel failed", extractErrorMessage(error), "error");
}
});
}
async function updateSelectedPaymentStatus(newStatus) {
if (!state.selectedPaymentId) return;
try {
await fetchJson(`/api/payments/${state.selectedPaymentId}/status`, {
method: "PATCH",
headers: { "Content-Type": "application/json" },
body: JSON.stringify({ status: newStatus })
});
showToast("Status updated", `Payment marked as ${newStatus}.`, "success");
await refreshAllDataAfterMutation(state.selectedPaymentId);
} catch (error) {
showToast("Update failed", extractErrorMessage(error), "error");
}
}
function initFilters() {
document.getElementById("paymentSearch").addEventListener("input", renderPaymentsTable);
document.getElementById("paymentStatusFilter").addEventListener("change", renderPaymentsTable);
document.getElementById("refreshPaymentsBtn").addEventListener("click", async () => {
await refreshPaymentsOnly(state.selectedPaymentId);
});
}
function renderUserSelector() {
const userSelect = document.getElementById("selectedUserId");
userSelect.innerHTML = "";
if (state.users.length === 0) {
userSelect.appendChild(new Option("No users yet", ""));
userSelect.disabled = true;
return;
}
userSelect.disabled = false;
state.users.forEach((user) => {
userSelect.appendChild(new Option(`${user.fullName} (${user.email})`, String(user.id)));
});
if (state.selectedUserId && state.users.some((user) => Number(user.id) === Number(state.selectedUserId))) {
userSelect.value = String(state.selectedUserId);
} else {
state.selectedUserId = Number(state.users[0].id);
userSelect.value = String(state.selectedUserId);
window.localStorage.setItem("PPS_SELECTED_USER_ID", String(state.selectedUserId));
}
}
async function refreshSelectedUserAccounts() {
if (!state.selectedUserId) {
state.selectedUserAccounts = [];
return;
}
try {
const response = await fetchJson(`/api/accounts?userId=${encodeURIComponent(state.selectedUserId)}`);
state.selectedUserAccounts = Array.isArray(response) ? response : [];
} catch {
state.selectedUserAccounts = [];
}
}
function populatePaymentFormOptions() {
const sourceSelect = document.getElementById("sourceAccountId");
const destinationSelect = document.getElementById("destinationAccountId");
const campaignSelect = document.getElementById("crowdfundingCampaignId");
const currencySelect = document.getElementById("currencyCode");
sourceSelect.innerHTML = "";
destinationSelect.innerHTML = "";
if (state.selectedUserAccounts.length === 0) {
sourceSelect.appendChild(new Option("No accounts for selected user", ""));
sourceSelect.disabled = true;
} else {
sourceSelect.disabled = false;
state.selectedUserAccounts.forEach((account) => {
sourceSelect.appendChild(new Option(`${account.id} – ${account.accountHolderName} (${account.currencyCode})`, String(account.id)));
});
sourceSelect.value = String(state.selectedUserAccounts[0].id);
currencySelect.value = state.selectedUserAccounts[0].currencyCode;
}
if (state.accounts.length === 0) {
destinationSelect.appendChild(new Option("No accounts available", ""));
destinationSelect.disabled = true;
} else {
destinationSelect.disabled = false;
state.accounts.forEach((account) => {
destinationSelect.appendChild(new Option(`${account.id} – ${account.accountHolderName} (${account.currencyCode})`, String(account.id)));
});
const preferredDestination = state.accounts.find((account) => Number(account.id) !== Number(sourceSelect.value)) || state.accounts[0];
destinationSelect.value = String(preferredDestination.id);
}
campaignSelect.innerHTML = "";
state.campaigns.filter((campaign) => campaign.status === "ACTIVE").forEach((campaign) => {
campaignSelect.appendChild(new Option(`${campaign.id} – ${campaign.campaignName} (${campaign.targetCurrency})`, String(campaign.id)));
});
}
function renderPaymentsTable() {
const tbody = document.getElementById("paymentsTableBody");
const emptyState = document.getElementById("paymentsEmptyState");
const query = document.getElementById("paymentSearch").value.trim().toLowerCase();
const statusFilter = document.getElementById("paymentStatusFilter").value;
const filtered = state.payments
.filter((payment) => {
const matchQuery = !query || String(payment.paymentReference || "").toLowerCase().includes(query);
const matchStatus = statusFilter === "ALL" || payment.status === statusFilter;
return matchQuery && matchStatus;
})
.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
tbody.innerHTML = "";
if (filtered.length === 0) {
emptyState.hidden = false;
return;
}
emptyState.hidden = true;
filtered.forEach((payment) => {
const tr = document.createElement("tr");
if (payment.id === state.selectedPaymentId) {
tr.classList.add("active-row");
}
tr.innerHTML = `
<td><strong>${payment.paymentReference || "-"}</strong></td>
<td>${payment.sourceAccountId} → ${payment.destinationAccountId}</td>
<td>${payment.currencyCode} ${formatAmount(payment.amount)}</td>
<td>${payment.paymentType || "REGULAR"}</td>
<td><span class="status-chip ${statusClass(payment.status)}">${payment.status}</span></td>
<td>${formatDateTime(payment.createdAt)}</td>
`;
tr.addEventListener("click", () => selectPayment(payment.id));
tbody.appendChild(tr);
});
}
async function selectPayment(paymentId) {
state.selectedPaymentId = paymentId;
renderPaymentsTable();
const payment = state.payments.find((item) => item.id === paymentId);
renderPaymentSnapshot(payment);
const actionsDiv = document.getElementById("statusActions");
actionsDiv.hidden = !(payment && payment.status === "SENT");
await renderPaymentHistory(paymentId);
await renderRelatedTickets(paymentId);
}
function renderPaymentSnapshot(payment) {
const referenceLabel = document.getElementById("selectedPaymentReference");
const snapshot = document.getElementById("paymentSnapshot");
if (!payment) {
referenceLabel.textContent = "Select a payment row";
snapshot.innerHTML = "";
return;
}
const sourceAccount = state.accounts.find((account) => Number(account.id) === Number(payment.sourceAccountId));
const sourceUserName = sourceAccount && sourceAccount.user ? sourceAccount.user.fullName : "-";
referenceLabel.textContent = payment.paymentReference || `Payment ${payment.id}`;
const rows = [
["Payment ID", payment.id],
["Source account", payment.sourceAccountId],
["Source user", sourceUserName],
["Destination account", payment.destinationAccountId],
["Amount", `${payment.currencyCode} ${formatAmount(payment.amount)}`],
["Destination amount", `${payment.destinationCurrencyCode || payment.currencyCode} ${formatAmount(payment.convertedAmount || payment.amount)}`],
["Forex fee", formatAmount(payment.forexFee || 0)],
["Payment type", payment.paymentType],
["Status", payment.status],
["Error code", payment.errorCode || "-"],
["Campaign ID", payment.crowdfundingCampaignId || "-"],
["Created at", formatDateTime(payment.createdAt)],
["Completed at", formatDateTime(payment.completedAt)]
];
snapshot.innerHTML = rows.map((row) => `<div class="kv-row"><span>${row[0]}</span><strong>${row[1]}</strong></div>`).join("");
}
async function renderPaymentHistory(paymentId) {
const timeline = document.getElementById("auditTimeline");
timeline.innerHTML = "<li><small>Loading timeline...</small></li>";
try {
const history = await fetchJson(`/api/payments/${paymentId}/history`);
if (!Array.isArray(history) || history.length === 0) {
timeline.innerHTML = "<li><small>No status transitions recorded.</small></li>";
return;
}
timeline.innerHTML = history.map((item) => {
const fromStatus = item.fromStatus || "-";
const description = item.description ? `<br><small>${item.description}</small>` : "";
return `<li><strong>${fromStatus} → ${item.toStatus}</strong><br><small>${formatDateTime(item.changedAt)}</small>${description}</li>`;
}).join("");
} catch {
timeline.innerHTML = "<li><small>Unable to load timeline from API.</small></li>";
}
}
async function renderRelatedTickets(paymentId) {
try {
const response = await fetchJson(`/api/tickets?paymentId=${encodeURIComponent(paymentId)}`);
state.tickets = Array.isArray(response) ? response : [];
} catch {
state.tickets = [];
}
const list = document.getElementById("ticketList");
const related = state.tickets.filter((ticket) => Number(ticket.paymentId) === Number(paymentId));
if (related.length === 0) {
list.innerHTML = '<div class="ticket-item"><p>No related tickets for this payment.</p></div>';
return;
}
list.innerHTML = related.map((ticket) => `
<article class="ticket-item">
<h4>${ticket.ticketNumber}</h4>
<p>${ticket.title}</p>
<p>${ticket.priority} | ${ticket.status}${ticket.userId ? ` | User ${ticket.userId}` : ""}</p>
</article>
`).join("");
}
function renderDataHub() {
const openTickets = state.tickets.filter((ticket) => ticket.status === "OPEN" || ticket.status === "IN_PROGRESS");
const selectedUser = getSelectedUser();
const usersList = document.getElementById("usersList");
document.getElementById("accountsCount").textContent = String(state.selectedUserAccounts.length);
document.getElementById("usersCount").textContent = String(state.users.length);
document.getElementById("campaignsCount").textContent = String(state.campaigns.length);
document.getElementById("openTicketsCount").textContent = String(openTickets.length);
document.getElementById("accountsList").innerHTML = state.selectedUserAccounts.length === 0
? `<article class="list-item"><p>${selectedUser ? "No accounts yet for selected user. Use + Add Account." : "Select or create a user first."}</p></article>`
: state.selectedUserAccounts.map((account) => `
<article class="list-item">
<div>
<h4>${account.id} – ${account.accountHolderName}</h4>
<p>${account.currencyCode} | Balance ${formatAmount(account.balance)} | ${account.accountStatus}${account.isBucketAccount ? " | Bucket" : ""}</p>
</div>
</article>
`).join("");
usersList.innerHTML = state.users.length === 0
? '<article class="list-item"><p>No users created yet.</p></article>'
: state.users.map((user) => `
<article class="list-item${Number(user.id) === Number(state.selectedUserId) ? " active-row" : ""}">
<div>
<h4>${user.fullName}</h4>
<p>${user.email}</p>
</div>
<span class="status-chip ${Number(user.id) === Number(state.selectedUserId) ? "status-sent" : "status-created"}">${Number(user.id) === Number(state.selectedUserId) ? "SELECTED" : "USER"}</span>
</article>
`).join("");
document.getElementById("campaignsList").innerHTML = state.campaigns.length === 0
? '<article class="list-item"><p>No campaigns yet.</p></article>'
: state.campaigns.map((campaign) => `
<article class="list-item">
<div>
<h4>${campaign.id} – ${campaign.campaignName}</h4>
<p>${campaign.status} | ${campaign.targetCurrency} ${formatAmount(campaign.currentAmount)} / ${formatAmount(campaign.targetAmount)}</p>
</div>
</article>
`).join("");
document.getElementById("openTicketsList").innerHTML = openTickets.length === 0
? '<article class="list-item"><p>No open tickets.</p></article>'
: openTickets.map((ticket) => `
<article class="list-item">
<div>
<h4>${ticket.ticketNumber}</h4>
<p>${ticket.status} | ${ticket.priority} | Payment ${ticket.paymentId || "N/A"}${ticket.userId ? ` | User ${ticket.userId}` : ""}</p>
</div>
</article>
`).join("");
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
const selectedUser = getSelectedUser();
const scopedAccounts = state.selectedUserAccounts;
const scopedPayments = selectedUser
? state.payments.filter((payment) => scopedAccounts.some((account) => Number(account.id) === Number(payment.sourceAccountId)))
: state.payments;
const totalBalance = scopedAccounts.reduce((sum, account) => sum + Number(account.balance || 0), 0);
const completedIncome = scopedPayments
.filter((payment) => payment.status === "COMPLETED")
.reduce((sum, payment) => sum + Number(payment.amount || 0), 0);
const failedExpense = scopedPayments
.filter((payment) => payment.status === "FAILED")
.reduce((sum, payment) => sum + Number(payment.amount || 0), 0);
const total = scopedPayments.length;
const successRate = total === 0 ? 0 : Math.round((scopedPayments.filter((payment) => payment.status === "COMPLETED").length / total) * 100);
document.getElementById("totalBalanceValue").textContent = formatAmount(totalBalance);
document.getElementById("incomeValue").textContent = formatAmount(completedIncome);
document.getElementById("expenseValue").textContent = formatAmount(failedExpense);
document.getElementById("successRateValue").textContent = `${successRate}%`;
document.getElementById("walletBalanceValue").textContent = formatAmount(totalBalance);
document.getElementById("walletUserLabel").textContent = selectedUser ? selectedUser.fullName : "No user selected";
}
function renderRecentTransactions() {
const list = document.getElementById("recentTransactionsList");
const recent = [...state.payments]
.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
.slice(0, 5);
if (recent.length === 0) {
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
if (entries.length === 0) {
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
function animateKpi(element, target) {
if (!element) return;
const duration = 850;
const start = performance.now();
const initial = Number(element.textContent) || 0;
function frame(now) {
const progress = Math.min((now - start) / duration, 1);
const eased = 1 - Math.pow(1 - progress, 3);
const value = Math.round(initial + (target - initial) * eased);
element.textContent = value.toLocaleString();
if (progress < 1) requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
}
async function refreshPaymentsOnly(selectIdAfter) {
setLoadingState(true);
try {
state.payments = await fetchJson("/api/payments");
renderPaymentsTable();
updateKpis();
updateDashboardSummary();
renderRecentTransactions();
renderSpendingCategories();
if (state.payments.length > 0) {
const selected = selectIdAfter || state.payments[0].id;
await selectPayment(selected);
}
showToast("Payments refreshed", "Latest payment list loaded.", "success");
} catch (error) {
showToast("Refresh failed", extractErrorMessage(error), "error");
}
setLoadingState(false);
}
async function refreshAllDataAfterMutation(selectPaymentId) {
const [users, accounts, campaigns, payments, tickets] = await Promise.all([
fetchJson("/api/users"),
fetchJson("/api/accounts"),
fetchJson("/api/campaigns"),
fetchJson("/api/payments"),
fetchJson("/api/tickets")
]);
state.users = Array.isArray(users) ? users : [];
state.accounts = Array.isArray(accounts) ? accounts : [];
state.campaigns = Array.isArray(campaigns) ? campaigns : [];
state.payments = Array.isArray(payments) ? payments : [];
state.tickets = Array.isArray(tickets) ? tickets : [];
state.selectedUserId = resolveSelectedUserId();
await refreshSelectedUserAccounts();
renderUserSelector();
populatePaymentFormOptions();
renderPaymentsTable();
renderDataHub();
updateKpis();
updateDashboardSummary();
renderRecentTransactions();
renderSpendingCategories();
syncProfile();
updateSelectedUserHint();
if (state.payments.length > 0) {
const selectedId = selectPaymentId && state.payments.some((payment) => Number(payment.id) === Number(selectPaymentId))
? selectPaymentId
: state.payments[0].id;
await selectPayment(selectedId);
}
}
async function fetchJson(path, options) {
const response = await fetch(`${API_BASE}${path}`, options);
if (!response.ok) {
let message = `Request failed (${response.status})`;
try {
const data = await response.json();
if (data && typeof data.message === "string") message = data.message;
} catch {
try { message = await response.text(); } catch { }
}
throw new Error(message);
}
if (response.status === 204) return null;
return response.json();
}
function hideLoadingScreen() {
window.setTimeout(() => document.getElementById("loadingScreen").classList.add("hidden"), 450);
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
window.setTimeout(() => toast.remove(), 3100);
}
function extractErrorMessage(error) {
if (error && typeof error.message === "string" && error.message.trim().length > 0) {
return error.message;
}
return "Unexpected error from API.";
}
function getSelectedUser() {
return state.users.find((user) => Number(user.id) === Number(state.selectedUserId)) || null;
}
function resolveSelectedUserId() {
const stored = window.localStorage.getItem("PPS_SELECTED_USER_ID");
if (stored && state.users.some((user) => Number(user.id) === Number(stored))) {
return Number(stored);
}
return state.users.length > 0 ? Number(state.users[0].id) : null;
}
function updateSelectedUserHint() {
const hint = document.getElementById("selectedUserHint");
const selectedUser = getSelectedUser();
if (!selectedUser) {
hint.textContent = "Create or select a user first. Payments will debit only from that user's own accounts.";
return;
}
if (state.selectedUserAccounts.length === 0) {
hint.textContent = `${selectedUser.fullName} has no accounts yet. Create an account before making payments.`;
return;
}
hint.textContent = `${selectedUser.fullName} currently has ${state.selectedUserAccounts.length} account(s). Choose one of them as the source account.`;
}