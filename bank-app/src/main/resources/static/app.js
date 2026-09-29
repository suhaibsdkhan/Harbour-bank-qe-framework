'use strict';

const money = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' });
const $ = (id) => document.getElementById(id);

async function api(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    });
    const body = response.status === 204 ? null : await response.json();
    if (!response.ok) {
        const error = new Error(body?.detail || `Request failed (${response.status})`);
        error.code = body?.code;
        error.fieldErrors = body?.errors || [];
        throw error;
    }
    return body;
}

function showAlert(kind, message) {
    const alert = $('alert');
    alert.className = `alert ${kind}`;
    alert.dataset.kind = kind;
    alert.textContent = message;
}

function showError(error) {
    const fields = error.fieldErrors.map((e) => `${e.field} ${e.message}`).join('; ');
    const code = error.code ? ` [${error.code}]` : '';
    showAlert('error', `${error.message}${fields ? ': ' + fields : ''}${code}`);
}

function formValues(form) {
    return Object.fromEntries(new FormData(form).entries());
}

async function loadAccounts() {
    const accounts = await api('/api/accounts');
    const body = $('accounts-body');
    body.innerHTML = '';
    for (const a of accounts) {
        const row = document.createElement('tr');
        row.setAttribute('data-testid', `account-row-${a.accountNumber}`);
        row.innerHTML = `
            <td><a href="#details" data-testid="account-link">${a.accountNumber}</a></td>
            <td data-testid="account-owner"></td>
            <td>${a.type}</td>
            <td class="num" data-testid="account-balance">${money.format(a.balance)}</td>
            <td class="status-${a.status}" data-testid="account-status">${a.status}</td>`;
        row.querySelector('[data-testid=account-owner]').textContent = a.ownerName;
        row.querySelector('a').addEventListener('click', () => showDetails(a.accountNumber));
        body.appendChild(row);
    }
    $('no-accounts').classList.toggle('hidden', accounts.length > 0);

    for (const select of document.querySelectorAll('.account-select')) {
        const current = select.value;
        select.innerHTML = accounts
            .map((a) => `<option value="${a.accountNumber}">${a.accountNumber}</option>`)
            .join('');
        for (const option of select.options) {
            const owner = accounts.find((a) => a.accountNumber === option.value).ownerName;
            option.textContent = `${option.value} · ${owner}`;
        }
        if (accounts.some((a) => a.accountNumber === current)) select.value = current;
    }
    return accounts;
}

async function showDetails(accountNumber) {
    const [account, entries] = await Promise.all([
        api(`/api/accounts/${accountNumber}`),
        api(`/api/accounts/${accountNumber}/transactions`),
    ]);
    $('details').classList.remove('hidden');
    $('details-number').textContent = account.accountNumber;
    $('details-balance').textContent = money.format(account.balance);
    const body = $('transactions-body');
    body.innerHTML = '';
    for (const e of entries) {
        const row = document.createElement('tr');
        row.setAttribute('data-testid', 'transaction-row');
        row.innerHTML = `
            <td>${new Date(e.createdAt).toLocaleString('en-CA')}</td>
            <td data-testid="transaction-description"></td>
            <td data-testid="transaction-type">${e.type}</td>
            <td class="num" data-testid="transaction-amount">${money.format(e.amount)}</td>
            <td class="num">${money.format(e.balanceAfter)}</td>`;
        row.querySelector('[data-testid=transaction-description]').textContent = e.description || '';
        body.appendChild(row);
    }
}

$('open-account-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const values = formValues(event.target);
    try {
        const account = await api('/api/accounts', {
            method: 'POST',
            body: JSON.stringify({ ...values, initialDeposit: values.initialDeposit || '0' }),
        });
        showAlert('success', `Account ${account.accountNumber} opened for ${account.ownerName}`);
        event.target.reset();
        await loadAccounts();
    } catch (error) {
        showError(error);
    }
});

$('deposit-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const { account, amount } = formValues(event.target);
    try {
        const updated = await api(`/api/accounts/${account}/deposits`, {
            method: 'POST',
            body: JSON.stringify({ amount }),
        });
        showAlert('success', `Deposited ${money.format(amount)}. New balance ${money.format(updated.balance)}`);
        await loadAccounts();
    } catch (error) {
        showError(error);
    }
});

$('transfer-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const values = formValues(event.target);
    try {
        const transfer = await api('/api/transfers', {
            method: 'POST',
            headers: { 'Idempotency-Key': crypto.randomUUID() },
            body: JSON.stringify({ ...values, description: values.description || null }),
        });
        showAlert('success', `Transfer ${transfer.reference} completed: ${money.format(transfer.amount)} sent to ${transfer.toAccount}`);
        await loadAccounts();
        await showDetails(transfer.fromAccount);
    } catch (error) {
        showError(error);
    }
});

$('refresh').addEventListener('click', loadAccounts);

loadAccounts().catch(showError);
