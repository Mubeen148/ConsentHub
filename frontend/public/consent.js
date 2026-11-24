/**
 * ConsentHub Embeddable Third-Party Privacy & Consent SDK (consent.js v1.0.0)
 *
 * Security & Privacy Guarantees (SEC-G04, PRIV-01):
 * - Zero external dependencies; isolated styles via Shadow DOM (with scoped fallback).
 * - Strict XSS safety: constructs all UI nodes via document.createElement + textContent (zero innerHTML).
 * - Stateless API-key authentication (credentials: 'omit') so first-party cookies are never leaked.
 * - Symmetric choice architecture: Accept All & Reject Optional share identical 44px height & weight.
 */
(function (global) {
  "use strict";

  var STORAGE_SUBJECT_KEY = "ch_sdk_subject_key";
  var STORAGE_CACHE_KEY = "ch_sdk_consent_cache";

  var state = {
    initialized: false,
    apiKey: "",
    apiBase: "",
    region: "",
    subjectKey: "",
    config: null,
    consent: null,
    modalOpen: false,
    hostEl: null,
    shadowRoot: null,
    customDecisions: {},
    listeners: []
  };

  function generateSubjectKey() {
    var bytes = new Uint8Array(12);
    if (global.crypto && typeof global.crypto.getRandomValues === "function") {
      global.crypto.getRandomValues(bytes);
    } else {
      for (var i = 0; i < bytes.length; i++) {
        bytes[i] = Math.floor(Math.random() * 256);
      }
    }
    var hex = "";
    for (var j = 0; j < bytes.length; j++) {
      hex += ("0" + bytes[j].toString(16)).slice(-2);
    }
    return "anon:sdk_" + hex;
  }

  function getOrCreateSubjectKey() {
    try {
      var existing = global.localStorage.getItem(STORAGE_SUBJECT_KEY);
      if (existing && /^anon:[a-zA-Z0-9_-]{8,112}$/.test(existing)) {
        return existing;
      }
      var created = generateSubjectKey();
      global.localStorage.setItem(STORAGE_SUBJECT_KEY, created);
      return created;
    } catch (_err) {
      return generateSubjectKey();
    }
  }

  function saveConsentCache(consentPayload) {
    try {
      global.localStorage.setItem(STORAGE_CACHE_KEY, JSON.stringify(consentPayload));
    } catch (_err) {
      // Ignore storage quota errors in private browsing modes
    }
  }

  function clearConsentCache() {
    try {
      global.localStorage.removeItem(STORAGE_CACHE_KEY);
    } catch (_err) {
      // Ignore
    }
  }

  function emitChange(detail) {
    for (var i = 0; i < state.listeners.length; i++) {
      try {
        state.listeners[i](detail);
      } catch (_cbErr) {
        // Isolate subscriber errors
      }
    }
    try {
      var evt = new CustomEvent("consenthub:change", { detail: detail });
      global.dispatchEvent(evt);
    } catch (_evtErr) {
      // Ignore old environments
    }
  }

  function el(tag, className, text) {
    var node = global.document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (typeof text === "string") {
      node.textContent = text;
    }
    return node;
  }

  function ensureHostMount() {
    if (state.hostEl && state.shadowRoot) {
      return state.shadowRoot;
    }
    var host = global.document.createElement("div");
    host.id = "consenthub-sdk-root";
    host.setAttribute("data-consenthub-sdk", "1.0.0");
    global.document.body.appendChild(host);

    var root = host.attachShadow ? host.attachShadow({ mode: "open" }) : host;
    var style = global.document.createElement("style");
    style.textContent = [
      ":host { all: initial; font-family: 'Plus Jakarta Sans', 'Inter', -apple-system, BlinkMacSystemFont, sans-serif; color: #141519; }",
      ".ch-backdrop { position: fixed; inset: 0; background: rgba(20, 21, 25, 0.48); z-index: 2147483640; display: flex; align-items: center; justify-content: center; padding: 16px; }",
      ".ch-dock { position: fixed; left: 16px; right: 16px; bottom: 16px; z-index: 2147483640; display: flex; justify-content: center; pointer-events: none; }",
      ".ch-card { pointer-events: auto; width: 100%; max-width: 760px; background: #FFFFFF; border: 1px solid #D0CBC0; border-radius: 16px; padding: 24px; box-sizing: border-box; box-shadow: 0 18px 40px rgba(20, 21, 25, 0.14); color: #141519; }",
      ".ch-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }",
      ".ch-badge { display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 9999px; font-size: 12px; font-weight: 600; background: #EFF6FF; color: #1E3A8A; border: 1px solid #BFDBFE; }",
      ".ch-badge-ccpa { background: #ECFEFF; color: #155E75; border-color: #A5F3FC; }",
      ".ch-badge-default { background: #F4F3EF; color: #3F424A; border-color: #D0CBC0; }",
      ".ch-meta { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; color: #5C5F69; }",
      ".ch-title { margin: 0 0 8px 0; font-size: 20px; line-height: 1.3; font-weight: 700; color: #141519; }",
      ".ch-body { margin: 0 0 16px 0; font-size: 14px; line-height: 1.55; color: #45474F; }",
      ".ch-purposes { border-top: 1px solid #E5E2DC; border-bottom: 1px solid #E5E2DC; padding: 12px 0; margin-bottom: 16px; max-height: 240px; overflow-y: auto; }",
      ".ch-purpose-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 10px 0; }",
      ".ch-purpose-row + .ch-purpose-row { border-top: 1px solid #F1EFEA; }",
      ".ch-purpose-name { font-size: 14px; font-weight: 600; color: #141519; margin: 0 0 2px 0; }",
      ".ch-purpose-desc { font-size: 12px; line-height: 1.45; color: #5C5F69; margin: 0; }",
      ".ch-locked { font-size: 12px; font-weight: 600; padding: 4px 10px; border-radius: 9999px; background: #F1EFEA; color: #363840; white-space: nowrap; }",
      ".ch-toggle { min-width: 48px; min-height: 44px; display: inline-flex; align-items: center; justify-content: flex-end; cursor: pointer; background: transparent; border: none; padding: 0; }",
      ".ch-toggle:focus-visible { outline: 2px solid #1D4ED8; outline-offset: 2px; border-radius: 8px; }",
      ".ch-track { width: 44px; height: 24px; border-radius: 9999px; background: #76726A; position: relative; transition: background 0.15s ease; }",
      ".ch-track-on { background: #047857; }",
      ".ch-thumb { width: 18px; height: 18px; border-radius: 9999px; background: #FFFFFF; position: absolute; top: 3px; left: 3px; transition: transform 0.15s ease; }",
      ".ch-track-on .ch-thumb { transform: translateX(20px); }",
      ".ch-actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }",
      ".ch-btn { min-height: 44px; padding: 0 18px; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; border: 1px solid transparent; transition: opacity 0.15s ease; }",
      ".ch-btn:focus-visible { outline: 2px solid #1D4ED8; outline-offset: 2px; }",
      ".ch-btn-primary { background: #141519; color: #FFFFFF; border-color: #141519; }",
      ".ch-btn-secondary { background: #FFFFFF; color: #141519; border-color: #76726A; }",
      ".ch-btn-ghost { background: transparent; color: #45474F; border-color: transparent; }",
      "@media (max-width: 640px) { .ch-actions { flex-direction: column; align-items: stretch; } .ch-btn { width: 100%; } }"
    ].join("\n");
    root.appendChild(style);

    var container = el("div", "ch-mount");
    root.appendChild(container);

    state.hostEl = host;
    state.shadowRoot = root;
    return root;
  }

  function clearBannerUi() {
    if (!state.shadowRoot) {
      return;
    }
    var mount = state.shadowRoot.querySelector(".ch-mount");
    if (mount) {
      while (mount.firstChild) {
        mount.removeChild(mount.firstChild);
      }
    }
    state.modalOpen = false;
  }

  function extractBannerCopy(config) {
    var regime = config.regionRule ? config.regionRule.regimeName : "DEFAULT";
    var headline = regime === "GDPR"
      ? "Your Privacy Choices"
      : (regime === "CCPA" ? "California Privacy & Right to Opt-Out" : "Transparent Privacy Controls");
    var body = config.activePolicyVersion && config.activePolicyVersion.summary
      ? config.activePolicyVersion.summary
      : "Manage how optional analytics, personalization, and advertising cookies are used on this site.";
    var acceptLabel = "Accept All";
    var rejectLabel = regime === "CCPA" ? "Do Not Sell or Share" : "Reject Optional";
    var experimentId = null;
    var variantKey = null;

    if (config.sduiBanner && config.sduiBanner.experiment) {
      experimentId = config.sduiBanner.experiment.experimentId || null;
      variantKey = config.sduiBanner.experiment.variantKey || null;
    }

    // Walk SDUI tree for server-composed Header and Button copy
    function walk(node) {
      if (!node || typeof node !== "object") {
        return;
      }
      if (node.type === "Header" && node.props) {
        if (typeof node.props.title === "string") headline = node.props.title;
        if (typeof node.props.subtitle === "string") body = node.props.subtitle;
      }
      if (node.type === "Button" && node.props && Array.isArray(node.actions)) {
        for (var i = 0; i < node.actions.length; i++) {
          var act = node.actions[i];
          if (act && act.type === "ACCEPT_ALL" && typeof node.props.label === "string") {
            acceptLabel = node.props.label;
          }
          if (act && act.type === "REJECT_OPTIONAL" && typeof node.props.label === "string") {
            rejectLabel = node.props.label;
          }
        }
      }
      if (Array.isArray(node.children)) {
        for (var c = 0; c < node.children.length; c++) {
          walk(node.children[c]);
        }
      }
    }

    if (config.sduiBanner && config.sduiBanner.root) {
      walk(config.sduiBanner.root);
    }

    return {
      regime: regime,
      headline: headline,
      body: body,
      acceptLabel: acceptLabel,
      rejectLabel: rejectLabel,
      experimentId: experimentId,
      variantKey: variantKey
    };
  }

  function renderBanner(showExpandedPurposes) {
    if (!state.config) {
      return;
    }
    ensureHostMount();
    var mount = state.shadowRoot.querySelector(".ch-mount");
    while (mount.firstChild) {
      mount.removeChild(mount.firstChild);
    }

    var config = state.config;
    var copy = extractBannerCopy(config);
    var isModal = config.regionRule && (config.regionRule.bannerStyle === "MODAL" || config.regionRule.consentModel === "OPT_IN");

    var wrapper = el("div", isModal ? "ch-backdrop" : "ch-dock");
    var card = el("div", "ch-card");
    card.setAttribute("role", "dialog");
    card.setAttribute("aria-modal", isModal ? "true" : "false");
    card.setAttribute("aria-label", copy.headline);

    var topRow = el("div", "ch-top");
    var badgeClass = "ch-badge" + (copy.regime === "CCPA" ? " ch-badge-ccpa" : (copy.regime === "DEFAULT" ? " ch-badge-default" : ""));
    var badgeLabel = copy.regime + " · " + (config.regionRule ? config.regionRule.regionCode : "GLOBAL");
    topRow.appendChild(el("span", badgeClass, badgeLabel));

    var versionMeta = "Policy " + (config.activePolicyVersion ? config.activePolicyVersion.versionLabel : "v1.0")
      + (copy.variantKey ? " · Variant: " + copy.variantKey : "");
    topRow.appendChild(el("span", "ch-meta", versionMeta));
    card.appendChild(topRow);

    card.appendChild(el("h2", "ch-title", copy.headline));
    card.appendChild(el("p", "ch-body", copy.body));

    if (showExpandedPurposes && Array.isArray(config.purposes)) {
      var listWrap = el("div", "ch-purposes");
      config.purposes.forEach(function (purpose) {
        var row = el("div", "ch-purpose-row");
        var info = el("div", "");
        info.appendChild(el("p", "ch-purpose-name", purpose.name));
        info.appendChild(el("p", "ch-purpose-desc", purpose.description));
        row.appendChild(info);

        if (purpose.isEssential) {
          row.appendChild(el("span", "ch-locked", "Always Active"));
        } else {
          var currentVal = Object.prototype.hasOwnProperty.call(state.customDecisions, purpose.slug)
            ? Boolean(state.customDecisions[purpose.slug])
            : Boolean(purpose.granted);
          state.customDecisions[purpose.slug] = currentVal;

          var toggleBtn = el("button", "ch-toggle");
          toggleBtn.type = "button";
          toggleBtn.setAttribute("role", "switch");
          toggleBtn.setAttribute("aria-checked", String(currentVal));
          toggleBtn.setAttribute("aria-label", "Toggle " + purpose.name);

          var track = el("span", "ch-track" + (currentVal ? " ch-track-on" : ""));
          track.appendChild(el("span", "ch-thumb"));
          toggleBtn.appendChild(track);

          toggleBtn.addEventListener("click", function () {
            var nextVal = !state.customDecisions[purpose.slug];
            state.customDecisions[purpose.slug] = nextVal;
            toggleBtn.setAttribute("aria-checked", String(nextVal));
            track.className = "ch-track" + (nextVal ? " ch-track-on" : "");
          });
          row.appendChild(toggleBtn);
        }
        listWrap.appendChild(row);
      });
      card.appendChild(listWrap);
    }

    var actions = el("div", "ch-actions");

    var acceptBtn = el("button", "ch-btn ch-btn-primary", copy.acceptLabel);
    acceptBtn.type = "button";
    acceptBtn.setAttribute("data-testid", "sdk-accept-all");
    acceptBtn.addEventListener("click", function () {
      submitSdkConsent("ACCEPT_ALL", null, copy.experimentId, copy.variantKey);
    });
    actions.appendChild(acceptBtn);

    var rejectBtn = el("button", "ch-btn ch-btn-secondary", copy.rejectLabel);
    rejectBtn.type = "button";
    rejectBtn.setAttribute("data-testid", "sdk-reject-optional");
    rejectBtn.addEventListener("click", function () {
      submitSdkConsent("REJECT_OPTIONAL", null, copy.experimentId, copy.variantKey);
    });
    actions.appendChild(rejectBtn);

    if (showExpandedPurposes) {
      var saveCustomBtn = el("button", "ch-btn ch-btn-secondary", "Save Custom Choices");
      saveCustomBtn.type = "button";
      saveCustomBtn.setAttribute("data-testid", "sdk-save-custom");
      saveCustomBtn.addEventListener("click", function () {
        submitSdkConsent("CUSTOM", state.customDecisions, copy.experimentId, copy.variantKey);
      });
      actions.appendChild(saveCustomBtn);
    } else {
      var customizeBtn = el("button", "ch-btn ch-btn-ghost", "Customize Purposes");
      customizeBtn.type = "button";
      customizeBtn.setAttribute("data-testid", "sdk-customize");
      customizeBtn.addEventListener("click", function () {
        renderBanner(true);
      });
      actions.appendChild(customizeBtn);
    }

    card.appendChild(actions);
    wrapper.appendChild(card);
    mount.appendChild(wrapper);
    state.modalOpen = true;
  }

  function buildHeaders() {
    var headers = {
      "Accept": "application/json",
      "Content-Type": "application/json",
      "X-API-Key": state.apiKey
    };
    if (state.region) {
      headers["X-Consent-Region"] = state.region;
    }
    return headers;
  }

  function fetchConfig(forceOpenBanner) {
    if (!state.apiKey) {
      return Promise.reject(new Error("ConsentHub SDK: missing data-api-key or apiKey option."));
    }
    var query = "?subjectKey=" + encodeURIComponent(state.subjectKey)
      + (state.region ? "&region=" + encodeURIComponent(state.region) : "");
    var url = state.apiBase + "/api/v1/sdk/config" + query;

    return global.fetch(url, {
      method: "GET",
      headers: buildHeaders(),
      credentials: "omit"
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error("SDK config request failed with HTTP " + res.status);
        }
        return res.json();
      })
      .then(function (data) {
        state.config = data;
        state.customDecisions = {};
        if (Array.isArray(data.purposes)) {
          data.purposes.forEach(function (p) {
            state.customDecisions[p.slug] = Boolean(p.granted);
          });
        }
        state.consent = {
          subjectKey: data.subjectKey,
          regionCode: data.regionRule ? data.regionRule.regionCode : "GLOBAL",
          policyVersion: data.activePolicyVersion ? data.activePolicyVersion.versionLabel : "v1.0",
          hasConsented: Boolean(data.hasConsented),
          needsReconsent: Boolean(data.needsReconsent),
          purposes: data.consentMap || {}
        };
        saveConsentCache(state.consent);
        emitChange(state.consent);

        if (forceOpenBanner || data.shouldShowBanner) {
          renderBanner(false);
        } else {
          clearBannerUi();
        }
        return state.consent;
      });
  }

  function submitSdkConsent(action, decisions, experimentId, variantKey) {
    var url = state.apiBase + "/api/v1/sdk/consent";
    var payload = {
      subjectKey: state.subjectKey,
      region: state.region || undefined,
      action: action,
      decisions: decisions || undefined,
      experimentId: experimentId || undefined,
      variantKey: variantKey || undefined
    };

    return global.fetch(url, {
      method: "POST",
      headers: buildHeaders(),
      credentials: "omit",
      body: JSON.stringify(payload)
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error("SDK consent submission failed with HTTP " + res.status);
        }
        return res.json();
      })
      .then(function (updated) {
        state.consent = updated;
        if (state.config) {
          state.config.hasConsented = updated.hasConsented;
          state.config.needsReconsent = updated.needsReconsent;
          state.config.shouldShowBanner = false;
          state.config.consentMap = updated.purposes;
          if (Array.isArray(state.config.purposes) && updated.purposes) {
            state.config.purposes.forEach(function (p) {
              if (Object.prototype.hasOwnProperty.call(updated.purposes, p.slug)) {
                p.granted = Boolean(updated.purposes[p.slug]);
              }
            });
          }
        }
        saveConsentCache(updated);
        clearBannerUi();
        emitChange(updated);
        return updated;
      });
  }

  var ConsentHub = {
    init: function (options) {
      var opts = options || {};
      state.apiKey = opts.apiKey || state.apiKey || "";
      state.apiBase = typeof opts.apiBase === "string" ? opts.apiBase.replace(/\/$/, "") : state.apiBase;
      state.region = typeof opts.region === "string" ? opts.region : state.region;
      state.subjectKey = opts.subjectKey || getOrCreateSubjectKey();
      state.initialized = true;
      return fetchConfig(Boolean(opts.forceOpen));
    },

    getConsent: function () {
      return state.consent ? JSON.parse(JSON.stringify(state.consent)) : null;
    },

    hasConsent: function (purposeSlug) {
      if (purposeSlug === "essential") {
        return true;
      }
      if (!state.consent || !state.consent.purposes) {
        return false;
      }
      return Boolean(state.consent.purposes[purposeSlug]);
    },

    openBanner: function () {
      if (state.config) {
        renderBanner(false);
        return Promise.resolve(state.consent);
      }
      return fetchConfig(true);
    },

    setRegion: function (regionCode, reopenBanner) {
      state.region = regionCode || "";
      return fetchConfig(Boolean(reopenBanner));
    },

    resetSubject: function () {
      clearConsentCache();
      var fresh = generateSubjectKey();
      state.subjectKey = fresh;
      try {
        global.localStorage.setItem(STORAGE_SUBJECT_KEY, fresh);
      } catch (_err) {
        // Ignore
      }
      return fetchConfig(true);
    },

    onChange: function (callback) {
      if (typeof callback === "function") {
        state.listeners.push(callback);
      }
      return function unsubscribe() {
        state.listeners = state.listeners.filter(function (fn) {
          return fn !== callback;
        });
      };
    }
  };

  global.ConsentHub = ConsentHub;

  // Auto-initialize when loaded via <script src="/consent.js" data-api-key="...">
  function autoBootFromScriptTag() {
    var script = global.document.currentScript
      || global.document.querySelector("script[data-api-key][src*='consent.js']");
    if (!script) {
      return;
    }
    var key = script.getAttribute("data-api-key");
    if (!key) {
      return;
    }
    var base = script.getAttribute("data-api-base") || "";
    var region = script.getAttribute("data-region") || "";
    var autoInit = script.getAttribute("data-auto-init");
    if (autoInit === "false") {
      return;
    }
    ConsentHub.init({
      apiKey: key,
      apiBase: base,
      region: region
    }).catch(function (_err) {
      // Fail gracefully without breaking host storefront
    });
  }

  if (global.document.readyState === "loading") {
    global.document.addEventListener("DOMContentLoaded", autoBootFromScriptTag);
  } else {
    autoBootFromScriptTag();
  }
})(window);
