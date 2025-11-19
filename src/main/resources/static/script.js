const analyzeBtn = document.getElementById("analyzeBtn");

analyzeBtn.addEventListener("click", async function() {
  const urlInput = document.getElementById("urlInput");
  const url = urlInput.value.trim();
  const card = document.querySelector(".main-card");

  // Remove any old results
  const oldResult = document.getElementById("result-box");
  if (oldResult) oldResult.remove();

  // Helper to display result
  function showResult(html) {
    const div = document.createElement("div");
    div.id = "result-box";
    div.className = "mt-4 animate-in";
    div.innerHTML = html;
    card.appendChild(div);
  }

  if (!url) {
    showResult(`<div class="error-msg">⚠️ Please enter a valid website URL.</div>`);
    return;
  }

  // Show loading message
  showResult(`<div class="loading-msg">🔎 Analyzing website... please wait</div>`);

try {
  const backendURL =
  window.location.hostname === "localhost"
    ? "http://localhost:8080"
    : "https://privacyanalyzer.onrender.com";

const response = await fetch(`${backendURL}/analyze`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ website: url })
  });

  if (!response.ok) throw new Error(`Server error: ${response.status}`);

  const data = await response.json();

  // ✅ Instead of creating another div, reuse the same one:
  const resultBox = document.getElementById("result-box");
  if (resultBox) resultBox.innerHTML = `
    <div class="results-glass">
      <div class="score-row">
        <div class="score-circle">${data.privacyGrade}</div>
        <div class="ml-3" style="padding-top:1.4rem;">
          ${
            data.privacyGrade === "A+" || data.privacyGrade === "A"
              ? `<span class="risk-low"><span class="icon">🟢</span>A+ Secure</span>`
              : data.privacyGrade === "B"
              ? `<span class="risk-medium"><span class="icon">🟡</span>B Moderate</span>`
              : `<span class="risk-high"><span class="icon">🔵</span>${data.privacyGrade || "C"} Risk</span>`
          }
        </div>
      </div>
      <div class="summary mt-3 mb-2">${data.analysisSummary}</div>
      <div class="small text-secondary mb-2" style="font-style:italic;color:#90caf9;">${data.examples}</div>
      <table class="mb-2">
        <tr><th>Cookies</th><td>${data.cookiesFound}</td></tr>
        <tr><th>Third-party domains</th><td>${data.thirdPartyFound}</td></tr>
        <tr><th>Storage entries</th><td>${data.storageFound}</td></tr>
      </table>
      ${
        data.thirdPartyDomains && data.thirdPartyDomains.length > 0
          ? `<h6>3rd Parties Detected:</h6><ul>${data.thirdPartyDomains
              .slice(0, 5)
              .map(d => `<li>${d}</li>`)
              .join("")}</ul>`
          : ""
      }
    </div>
  `;
} catch (err) {
  const resultBox = document.getElementById("result-box");
  if (resultBox) resultBox.innerHTML = `<div class="error-msg">❌ Error: ${err.message}</div>`;
}
});
