const API = "/api";

const $ = id => document.getElementById(id);

async function request(path, options = {}) {
    const response = await fetch(API + path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...options.headers
        }
    });

    let data;
    try {
        data = await response.json();
    } catch {
        data = {};
    }

    if (!response.ok) {
        throw new Error(data.message || data.error || "Something went wrong.");
    }

    return data;
}

function showMessage(text, error = false) {
    $("message").textContent = text;
    $("message").classList.toggle("error", error);
}

function makePatientCard(patient, position) {
    const card = document.createElement("article");
    card.className = `patient sev-${patient.severity}`;

    const info = document.createElement("div");
    const name = document.createElement("strong");
    name.textContent = patient.name;

    const detail = document.createElement("small");
    detail.textContent =
        `Patient #${patient.id} · Severity ${patient.severity}/5`;

    info.append(name, detail);

    const badge = document.createElement("span");
    badge.textContent = position;
    badge.style.color = patient.severity >= 4 ? "#fca5a5" : "#93c5fd";
    badge.style.whiteSpace = "nowrap";

    card.append(info, badge);
    return card;
}

function renderList(containerId, patients, emptyText, label) {
    const container = $(containerId);
    container.replaceChildren();

    if (!patients.length) {
        const empty = document.createElement("p");
        empty.className = "empty";
        empty.textContent = emptyText;
        container.append(empty);
        return;
    }

    patients.forEach((patient, index) => {
        container.append(makePatientCard(patient, label(index, patient)));
    });
}

async function refresh() {
    try {
        const state = await request("/state");

        $("waitingCount").textContent = state.waitingCount;
        $("treatedCount").textContent = state.treatedCount;
        $("waitingBadge").textContent = state.waitingCount;
        $("nextSeverity").textContent = state.waiting.length
            ? `${state.waiting[0].severity}/5`
            : "—";

        renderList(
            "waitingList",
            state.waiting,
            "No patients waiting.",
            index => index === 0 ? "NEXT" : `#${index + 1}`
        );

        renderList(
            "historyList",
            state.history,
            "No treatments recorded yet.",
            index => index === 0 ? "LATEST" : `#${index + 1}`
        );

        $("treatBtn").disabled = !state.waiting.length;
        $("undoBtn").disabled = !state.history.length;
    } catch (error) {
        showMessage(
            "Cannot connect to the Java backend. Start the server first.",
            true
        );
    }
}

$("patientForm").addEventListener("submit", async event => {
    event.preventDefault();

    try {
        const name = $("patientName").value.trim();
        const severity = Number($("severity").value);

        const result = await request("/patient", {
            method: "POST",
            body: JSON.stringify({ name, severity })
        });

        $("patientForm").reset();
        showMessage(result.message);
        await refresh();
    } catch (error) {
        showMessage(error.message, true);
    }
});

$("treatBtn").addEventListener("click", async () => {
    try {
        const result = await request("/treat", { method: "POST" });
        showMessage(`Treated: ${result.patient.name}`);
        await refresh();
    } catch (error) {
        showMessage(error.message, true);
    }
});

$("undoBtn").addEventListener("click", async () => {
    try {
        const result = await request("/undo", { method: "POST" });
        showMessage(`Returned to waiting list: ${result.patient.name}`);
        await refresh();
    } catch (error) {
        showMessage(error.message, true);
    }
});

$("refreshBtn").addEventListener("click", refresh);

refresh();
