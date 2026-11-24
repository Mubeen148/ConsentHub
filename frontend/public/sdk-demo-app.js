(function () {
  "use strict";

  var stateBox = document.getElementById("sdk-state-json");
  var eventLog = document.getElementById("sdk-event-log");
  var subjectBadge = document.getElementById("sdk-subject-badge");
  var regionSelect = document.getElementById("sdk-region-select");
  var btnOpen = document.getElementById("btn-open-banner");
  var btnReset = document.getElementById("btn-reset-visitor");

  var purposeChips = {
    essential: document.getElementById("chip-essential"),
    analytics: document.getElementById("chip-analytics"),
    personalization: document.getElementById("chip-personalization"),
    marketing: document.getElementById("chip-marketing"),
    ads: document.getElementById("chip-ads")
  };

  function appendLog(message) {
    if (!eventLog) return;
    var item = document.createElement("div");
    item.className = "log-line";
    var time = new Date().toISOString().slice(11, 23);
    item.textContent = "[" + time + "] " + message;
    eventLog.insertBefore(item, eventLog.firstChild);
  }

  function renderConsentSnapshot(detail) {
    if (!detail) return;
    if (stateBox) {
      stateBox.textContent = JSON.stringify(detail, null, 2);
    }
    if (subjectBadge) {
      subjectBadge.textContent = detail.subjectKey || "anon:initializing";
    }
    Object.keys(purposeChips).forEach(function (slug) {
      var chip = purposeChips[slug];
      if (!chip) return;
      var granted = window.ConsentHub ? window.ConsentHub.hasConsent(slug) : slug === "essential";
      chip.textContent = slug + ": " + (granted ? "GRANTED" : "BLOCKED");
      chip.className = "chip " + (granted ? "chip-on" : "chip-off");
    });
  }

  window.addEventListener("consenthub:change", function (e) {
    renderConsentSnapshot(e.detail);
    appendLog(
      "consenthub:change -> region=" +
        (e.detail.regionCode || "GLOBAL") +
        ", hasConsented=" +
        Boolean(e.detail.hasConsented) +
        ", policy=" +
        (e.detail.policyVersion || "v2.0")
    );
  });

  if (regionSelect) {
    regionSelect.addEventListener("change", function () {
      var nextRegion = regionSelect.value;
      appendLog("Switching simulated SDK region to " + nextRegion + "...");
      if (window.ConsentHub) {
        window.ConsentHub.setRegion(nextRegion, true);
      }
    });
  }

  if (btnOpen) {
    btnOpen.addEventListener("click", function () {
      appendLog("Re-opening ConsentHub SDK banner...");
      if (window.ConsentHub) {
        window.ConsentHub.openBanner();
      }
    });
  }

  if (btnReset) {
    btnReset.addEventListener("click", function () {
      appendLog("Generating fresh pseudonymous visitor subjectKey...");
      if (window.ConsentHub) {
        window.ConsentHub.resetSubject();
      }
    });
  }

  appendLog("Host storefront booted; awaiting ConsentHub SDK initialization...");
})();
