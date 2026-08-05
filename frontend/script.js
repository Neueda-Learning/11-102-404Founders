const API_BASE = (window.PAYMENT_API_BASE || window.localStorage.getItem("PAYMENT_API_BASE") || "http://localhost:8080").replace(/\/$/, "");

const state = {
	accounts: [],
	campaigns: [],
	payments: [],
	tickets: [],
	selectedPaymentId: null,
	pendingPaymentPayload: null
};

document.addEventListener("DOMContentLoaded", async () => {
	requestAnimationFrame(() => document.body.classList.add("is-loaded"));
	initNavbar();
	initReveal();
	initFormInteractions();
	initFilters();
	await bootstrapData();
	hideLoadingScreen();
});

async function bootstrapData() {
	try {
		const [accounts, campaigns, payments, tickets] = await Promise.all([
			fetchJson("/api/accounts"),
			fetchJson("/api/campaigns"),
			fetchJson("/api/payments"),
			fetchJson("/api/tickets")
		]);

		state.accounts = Array.isArray(accounts) ? accounts : [];
		state.campaigns = Array.isArray(campaigns) ? campaigns : [];
		state.payments = Array.isArray(payments) ? payments : [];
		state.tickets = Array.isArray(tickets) ? tickets : [];
	} catch (error) {
		state.accounts = [];
		state.campaigns = [];
		state.payments = [];
		state.tickets = [];
		showToast("API connection failed", extractErrorMessage(error), "error");
	}

	populatePaymentFormOptions();
	renderPaymentsTable();
	renderDataHub();
	updateKpis();

	if (state.payments.length > 0) {
		await selectPayment(state.payments[0].id);
	} else {
		renderPaymentSnapshot(null);
		document.getElementById("auditTimeline").innerHTML = "<li><small>No payment selected.</small></li>";
		document.getElementById("ticketList").innerHTML = "<div class=\"ticket-item\"><p>No payment selected.</p></div>";
	}
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

function initReveal() {
	const sections = document.querySelectorAll("[data-reveal]");
	const observer = new IntersectionObserver(
		(entries) => {
			entries.forEach((entry) => {
				if (entry.isIntersecting) {
					entry.target.classList.add("visible");
					observer.unobserve(entry.target);
				}
			});
		},
		{ threshold: 0.14 }
	);
	sections.forEach((section) => observer.observe(section));
}

function initFormInteractions() {
	const paymentTypeSelect = document.getElementById("paymentType");
	const campaignField = document.getElementById("campaignField");
	const campaignSelect = document.getElementById("crowdfundingCampaignId");
	const sourceAccountSelect = document.getElementById("sourceAccountId");
	const destinationAccountSelect = document.getElementById("destinationAccountId");
	const currencyCodeSelect = document.getElementById("currencyCode");
	const destinationCurrencySelect = document.getElementById("destinationCurrencyCode");
	const form = document.getElementById("paymentForm");
	const resetBtn = document.getElementById("resetPaymentFormBtn");
	const dialog = document.getElementById("confirmDialog");
	const confirmText = document.getElementById("confirmText");

	sourceAccountSelect.addEventListener("change", () => {
		const selectedAccount = state.accounts.find((account) => Number(account.id) === Number(sourceAccountSelect.value));
		if (selectedAccount) {
			currencyCodeSelect.value = selectedAccount.currencyCode;
		}
	});

	destinationAccountSelect.addEventListener("change", () => {
		const selectedAccount = state.accounts.find((account) => Number(account.id) === Number(destinationAccountSelect.value));
		if (selectedAccount) {
			destinationCurrencySelect.value = selectedAccount.currencyCode;
		}
	});

	paymentTypeSelect.addEventListener("change", () => {
		const isCrowdfunding = paymentTypeSelect.value === "CROWDFUNDING_PAYMENT";
		campaignField.hidden = !isCrowdfunding;
		campaignSelect.required = isCrowdfunding;
	});

	resetBtn.addEventListener("click", () => {
		form.reset();
		campaignField.hidden = true;
		campaignSelect.required = false;
		showToast("Form reset", "Payment form has been cleared.", "warn");
	});

	form.addEventListener("submit", (event) => {
		event.preventDefault();
		if (!form.reportValidity()) {
			return;
		}

		const payload = {
			sourceAccountId: Number(document.getElementById("sourceAccountId").value),
			destinationAccountId: Number(document.getElementById("destinationAccountId").value),
			amount: Number(document.getElementById("amount").value),
			currencyCode: document.getElementById("currencyCode").value,
			destinationCurrencyCode: document.getElementById("destinationCurrencyCode").value,
			paymentType: document.getElementById("paymentType").value
		};

		if (payload.paymentType === "CROWDFUNDING_PAYMENT") {
			payload.crowdfundingCampaignId = Number(document.getElementById("crowdfundingCampaignId").value);
		}

		state.pendingPaymentPayload = payload;
		confirmText.textContent = `Create ${payload.paymentType} payment of ${payload.currencyCode} ${payload.amount.toFixed(2)} from account ${payload.sourceAccountId} to ${payload.destinationAccountId}?`;
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

			showToast("Payment created", `${created.paymentReference} is now in ${created.status}.`, "success");
			state.pendingPaymentPayload = null;
			await refreshPaymentsOnly(created.id);
			form.reset();
			campaignField.hidden = true;
			campaignSelect.required = false;
		} catch (error) {
			showToast("Create failed", extractErrorMessage(error), "error");
		}
	});
}

function initFilters() {
	const search = document.getElementById("paymentSearch");
	const status = document.getElementById("paymentStatusFilter");
	const refreshBtn = document.getElementById("refreshPaymentsBtn");

	search.addEventListener("input", renderPaymentsTable);
	status.addEventListener("change", renderPaymentsTable);
	refreshBtn.addEventListener("click", async () => {
		await refreshPaymentsOnly(state.selectedPaymentId);
	});
}

function populatePaymentFormOptions() {
	const sourceSelect = document.getElementById("sourceAccountId");
	const destinationSelect = document.getElementById("destinationAccountId");
	const campaignSelect = document.getElementById("crowdfundingCampaignId");

	sourceSelect.innerHTML = "";
	destinationSelect.innerHTML = "";

	state.accounts.forEach((account) => {
		const label = `${account.id} - ${account.accountHolderName} (${account.currencyCode})`;
		sourceSelect.appendChild(new Option(label, String(account.id)));
		destinationSelect.appendChild(new Option(label, String(account.id)));
	});

	campaignSelect.innerHTML = "";
	const activeCampaigns = state.campaigns.filter((campaign) => campaign.status === "ACTIVE");
	activeCampaigns.forEach((campaign) => {
		const label = `${campaign.id} - ${campaign.campaignName} (${campaign.targetCurrency})`;
		campaignSelect.appendChild(new Option(label, String(campaign.id)));
	});

	if (state.accounts.length > 0) {
		sourceSelect.value = String(state.accounts[0].id);
		destinationSelect.value = String(state.accounts[0].id);
		document.getElementById("currencyCode").value = state.accounts[0].currencyCode;
		document.getElementById("destinationCurrencyCode").value = state.accounts[0].currencyCode;
	}
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
			<td>${payment.sourceAccountId} -> ${payment.destinationAccountId}</td>
			<td>${payment.currencyCode} ${formatAmount(payment.amount)}</td>
			<td>${payment.paymentType || "NORMAL_PAYMENT"}</td>
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

	referenceLabel.textContent = payment.paymentReference || `Payment ${payment.id}`;
	const rows = [
		["Payment ID", payment.id],
		["Source account", payment.sourceAccountId],
		["Destination account", payment.destinationAccountId],
		["Amount", `${payment.currencyCode} ${formatAmount(payment.amount)}`],
		["Payment type", payment.paymentType],
		["Status", payment.status],
		["Error code", payment.errorCode || "-"],
		["Campaign ID", payment.crowdfundingCampaignId || "-"],
		["Created at", formatDateTime(payment.createdAt)],
		["Completed at", formatDateTime(payment.completedAt)]
	];

	snapshot.innerHTML = rows
		.map((row) => `<div class="kv-row"><span>${row[0]}</span><strong>${row[1]}</strong></div>`)
		.join("");
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

		timeline.innerHTML = history
			.map((item) => {
				const fromStatus = item.fromStatus || "-";
				return `<li><strong>${fromStatus} -> ${item.toStatus}</strong><br><small>${formatDateTime(item.changedAt)}</small></li>`;
			})
			.join("");
	} catch (error) {
		timeline.innerHTML = "<li><small>Unable to load timeline from API.</small></li>";
	}
}

async function renderRelatedTickets(paymentId) {
	try {
		const response = await fetchJson(`/api/tickets?paymentId=${encodeURIComponent(paymentId)}`);
		state.tickets = Array.isArray(response) ? response : [];
	} catch (error) {
		state.tickets = [];
	}

	const list = document.getElementById("ticketList");
	const related = state.tickets.filter((ticket) => Number(ticket.paymentId) === Number(paymentId));

	if (related.length === 0) {
		list.innerHTML = "<div class=\"ticket-item\"><p>No related tickets for this payment.</p></div>";
		return;
	}

	list.innerHTML = related
		.map((ticket) => `
			<article class="ticket-item">
				<h4>${ticket.ticketNumber}</h4>
				<p>${ticket.title}</p>
				<p>${ticket.priority} | ${ticket.status}</p>
			</article>
		`)
		.join("");
}

function renderDataHub() {
	const accountsList = document.getElementById("accountsList");
	const campaignsList = document.getElementById("campaignsList");
	const openTicketsList = document.getElementById("openTicketsList");
	const openTickets = state.tickets.filter((ticket) => ticket.status === "OPEN" || ticket.status === "IN_PROGRESS");

	document.getElementById("accountsCount").textContent = `${state.accounts.length}`;
	document.getElementById("campaignsCount").textContent = `${state.campaigns.length}`;
	document.getElementById("openTicketsCount").textContent = `${openTickets.length}`;

	accountsList.innerHTML = state.accounts
		.map((account) => `
			<article class="list-item">
				<div>
					<h4>${account.id} - ${account.accountHolderName}</h4>
					<p>${account.currencyCode} | Balance ${formatAmount(account.balance)} | ${account.accountStatus}</p>
				</div>
			</article>
		`)
		.join("");

	campaignsList.innerHTML = state.campaigns
		.map((campaign) => `
			<article class="list-item">
				<div>
					<h4>${campaign.id} - ${campaign.campaignName}</h4>
					<p>${campaign.status} | ${campaign.targetCurrency} ${formatAmount(campaign.currentAmount)} / ${formatAmount(campaign.targetAmount)}</p>
				</div>
			</article>
		`)
		.join("");

	openTicketsList.innerHTML = openTickets.length === 0
		? "<article class=\"list-item\"><p>No open tickets.</p></article>"
		: openTickets
			.map((ticket) => `
				<article class="list-item">
					<div>
						<h4>${ticket.ticketNumber}</h4>
						<p>${ticket.status} | ${ticket.priority} | Payment ${ticket.paymentId || "N/A"}</p>
					</div>
				</article>
			`)
			.join("");
}

function updateKpis() {
	const total = state.payments.length;
	const completed = state.payments.filter((payment) => payment.status === "COMPLETED" || payment.status === "SUCCESS").length;
	const failed = state.payments.filter((payment) => payment.status === "FAILED").length;

	animateKpi(document.querySelector("[data-kpi='payments']"), total);
	animateKpi(document.querySelector("[data-kpi='completed']"), completed);
	animateKpi(document.querySelector("[data-kpi='failed']"), failed);
}

function animateKpi(element, target) {
	if (!element) {
		return;
	}
	const duration = 850;
	const start = performance.now();
	const initial = Number(element.textContent) || 0;

	function frame(now) {
		const progress = Math.min((now - start) / duration, 1);
		const eased = 1 - Math.pow(1 - progress, 3);
		const value = Math.round(initial + (target - initial) * eased);
		element.textContent = value.toLocaleString();
		if (progress < 1) {
			requestAnimationFrame(frame);
		}
	}

	requestAnimationFrame(frame);
}

async function refreshPaymentsOnly(selectIdAfter) {
	try {
		state.payments = await fetchJson("/api/payments");
		renderPaymentsTable();
		updateKpis();

		if (state.payments.length > 0) {
			const selected = selectIdAfter || state.payments[0].id;
			await selectPayment(selected);
		}
		showToast("Payments refreshed", "Latest payment list loaded.", "success");
	} catch (error) {
		showToast("Refresh failed", extractErrorMessage(error), "error");
	}
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
		} catch (error) {
			try {
				message = await response.text();
			} catch (textError) {
				// Ignore secondary parse errors.
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
	const loadingScreen = document.getElementById("loadingScreen");
	window.setTimeout(() => loadingScreen.classList.add("hidden"), 600);
}

function formatAmount(value) {
	const number = Number(value);
	return Number.isNaN(number) ? "0.00" : number.toFixed(2);
}

function formatDateTime(value) {
	if (!value) {
		return "-";
	}
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return String(value);
	}
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

