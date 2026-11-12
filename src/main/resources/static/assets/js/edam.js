(function () {
  "use strict";

  document.addEventListener("DOMContentLoaded", function () {
    var toggleButton = document.getElementById("edam-sidebar-toggle");
    var sidebar = document.getElementById("edam-sidebar");
    if (toggleButton && sidebar) {
      toggleButton.addEventListener("click", function () {
        sidebar.classList.toggle("is-collapsed");
      });
    }

    var healthButton = document.getElementById("edam-health-check-btn");
    var healthOutput = document.getElementById("edam-health-output");
    if (healthButton && healthOutput) {
      healthButton.addEventListener("click", function () {
        var endpoint = healthButton.getAttribute("data-health-url") || "/api/v1/health";
        healthOutput.textContent = "Checking platform health...";
        fetch(endpoint, {
          headers: {
            "Accept": "application/json"
          }
        })
          .then(function (response) {
            var corrId = response.headers.get("X-Correlation-ID") || "n/a";
            return response.json().then(function (payload) {
              return { status: response.status, corrId: corrId, payload: payload };
            });
          })
          .then(function (result) {
            healthOutput.textContent = JSON.stringify({
              httpStatus: result.status,
              correlationHeader: result.corrId,
              response: result.payload
            }, null, 2);
          })
          .catch(function () {
            healthOutput.textContent = "Unable to reach health endpoint.";
          });
      });
    }
  });
})();
