const API = "/api/expenses";
const money = n => "₹" + Number(n).toLocaleString("en-IN", {
  minimumFractionDigits: 2, maximumFractionDigits: 2
});

document.getElementById("date").value =
  new Date().toLocaleDateString("en-CA");

async function loadExpenses() {
  try {
    const response = await fetch(API);
    if (!response.ok) throw new Error("Could not load expenses");
    const data = await response.json();
    const tbody = document.getElementById("expenses");
    tbody.replaceChildren();

    let total = 0;
    const categories = {};

    data.forEach(e => {
      total += Number(e.amount);
      categories[e.category] = (categories[e.category] || 0) + Number(e.amount);

      const tr = document.createElement("tr");
      [e.description, money(e.amount), e.category, e.date].forEach(value => {
        const td = document.createElement("td");
        td.textContent = value;
        tr.appendChild(td);
      });

      const action = document.createElement("td");
      const btn = document.createElement("button");
      btn.textContent = "Delete";
      btn.className = "delete";
      btn.onclick = () => deleteExpense(e.id);
      action.appendChild(btn);
      tr.appendChild(action);
      tbody.appendChild(tr);
    });

    document.getElementById("total").textContent = money(total);
    document.getElementById("count").textContent = data.length;
    document.getElementById("empty").hidden = data.length > 0;

    const top = Object.entries(categories).sort((a,b) => b[1] - a[1])[0];
    document.getElementById("top").textContent = top ? top[0] : "—";

    document.getElementById("insight").textContent = !data.length
      ? "Add expenses to receive a spending insight."
      : top
        ? `${top[0]} is your highest spending category at ${money(top[1])}. Review this category to find possible savings.`
        : "Keep tracking your expenses.";
  } catch (error) {
    document.getElementById("message").textContent =
      "Cannot connect to the server. Check that the Java application is running.";
  }
}

document.getElementById("expenseForm").addEventListener("submit", async event => {
  event.preventDefault();
  const description = document.getElementById("description").value.trim();
  const amount = Number(document.getElementById("amount").value);
  const date = document.getElementById("date").value;

  if (!description || amount <= 0 || !date) {
    document.getElementById("message").textContent =
      "Please enter a description, positive amount and date.";
    return;
  }

  try {
    const response = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ description, amount, date })
    });
    if (!response.ok) throw new Error("Save failed");
    document.getElementById("expenseForm").reset();
    document.getElementById("date").value =
      new Date().toLocaleDateString("en-CA");
    document.getElementById("message").textContent = "Expense saved successfully!";
    await loadExpenses();
  } catch {
    document.getElementById("message").textContent =
      "Could not save. Check the Java terminal and database connection.";
  }
});

async function deleteExpense(id) {
  try {
    const response = await fetch(`${API}?id=${id}`, { method: "DELETE" });
    if (!response.ok) throw new Error("Delete failed");
    await loadExpenses();
  } catch {
    document.getElementById("message").textContent = "Could not delete expense.";
  }
}

loadExpenses();

document.getElementById("parseButton").addEventListener("click", () => {
  const text = document.getElementById("smartInput").value.trim();
  const message = document.getElementById("parseMessage");

  if (!text) {
    message.textContent = "Please describe your expense first.";
    return;
  }

  // Extract the amount
  const amountMatch = text.match(
    /(?:₹|rs\.?|inr\s*)?\s*(\d+(?:,\d{3})*(?:\.\d{1,2})?)/i
  );

  if (!amountMatch) {
    message.textContent = "Please include an amount, e.g. ₹250.";
    return;
  }

  const amount = Number(amountMatch[1].replace(/,/g, ""));

  if (amount <= 0) {
    message.textContent = "Amount must be greater than zero.";
    return;
  }

  // Extract today's or yesterday's date
  const date = new Date();
  const lower = text.toLowerCase();

  if (lower.includes("yesterday")) {
    date.setDate(date.getDate() - 1);
  }

  const dateValue =
    date.getFullYear() + "-" +
    String(date.getMonth() + 1).padStart(2, "0") + "-" +
    String(date.getDate()).padStart(2, "0");

  // Extract description after "on" or "for"
  let description = text
    .replace(/₹\s*/g, "")
    .replace(/\brs\.?\s*/gi, "")
    .replace(/\binr\s*/gi, "")
    .replace(/\d+(?:,\d{3})*(?:\.\d{1,2})?/g, "")
    .replace(/\b(spent|paid|bought|purchased|expense|expenses|for|on|today|yesterday)\b/gi, " ")
    .replace(/[.,!?]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  if (!description) {
    message.textContent =
      "Please include what you spent money on, e.g. lunch.";
    return;
  }

  // Fill the existing expense form automatically
  document.getElementById("description").value = description;
  document.getElementById("amount").value = amount;
  document.getElementById("date").value = dateValue;

  message.textContent =
    `Understood: ${description}, ₹${amount}, ${dateValue}. Review the form and click Add Expense.`;
});

