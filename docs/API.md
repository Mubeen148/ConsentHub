# ConsentHub — Developer API & SDK Reference (`docs/API.md`)

> Full machine-readable contracts live in [`docs/contracts/`](file:///d:/The%20Company/Netflix/docs/contracts/):
> - [graphql-schema.graphqls](file:///d:/The%20Company/Netflix/docs/contracts/graphql-schema.graphqls)
> - [rest-endpoints.md](file:///d:/The%20Company/Netflix/docs/contracts/rest-endpoints.md)
> - [sdui-schema.json](file:///d:/The%20Company/Netflix/docs/contracts/sdui-schema.json)
> - [database-schema.sql](file:///d:/The%20Company/Netflix/docs/contracts/database-schema.sql)

---

## 1. Authentication & CSRF Handshake (`/api/v1/auth/*`)

### 1.1 Bootstrap CSRF Token
```bash
curl -i -c cookies.txt http://localhost:8080/api/v1/auth/csrf
```
Response (`200 OK`):
```json
{
  "csrfToken": "9f8c2b4e..."
}
```

### 1.2 Log In (`HttpOnly` Session Cookie)
```bash
curl -i -b cookies.txt -c cookies.txt \
  -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@consenthub.io","password":"UserPass123!"}'
```

### 1.3 Download GDPR Art. 20 / CCPA Portability Archive
```bash
curl -b cookies.txt -OJ http://localhost:8080/api/v1/privacy/export
```
Downloads `consenthub-data-export-YYYY-MM-DD.json` containing `exportMetadata`, `profile`, `currentConsentRecords`, `consentHistoryLedger` (with `proofHash`), `experimentAssignments`, and `dataSubjectRequests`.

---

## 2. GraphQL API (`POST /graphql`)

When calling `/graphql` with an authenticated `ch_access_token` cookie, always include the `X-CSRF-Token` header matching `ch_csrf_token`.

### 2.1 Fetch Server-Driven UI Screen (`CONSENT_BANNER` | `PREFERENCE_CENTER` | `PRIVACY_OVERVIEW`)
```graphql
query GetConsentBanner($context: ScreenContextInput) {
  screen(name: CONSENT_BANNER, context: $context) {
    schemaVersion
    screenName
    policyVersion
    needsReconsent
    regionCode
    regime
    experiment {
      experimentId
      experimentSlug
      variantKey
    }
    root {
      id
      type
      props
      actions {
        trigger
        type
        payload
      }
      children {
        id
        type
        props
        children {
          id
          type
          props
          actions {
            trigger
            type
            payload
          }
        }
      }
    }
  }
}
```
Variables:
```json
{
  "context": {
    "subjectKey": "anon:visitor_demo_12345",
    "region": "EU",
    "deviceType": "DESKTOP"
  }
}
```

### 2.2 Save Granular Consent Preferences
```graphql
mutation SaveChoices($input: SaveConsentInput!) {
  saveConsentPreferences(input: $input) {
    subjectKey
    hasConsented
    needsReconsent
    consentedPolicyVersion
    decisions {
      slug
      isEssential
      granted
      updatedAt
    }
  }
}
```

### 2.3 Verify Cryptographic Audit Hash Chain (`ROLE_ADMIN`)
```graphql
query VerifyLedger {
  verifyAuditChain {
    chainValid
    totalEntriesChecked
    brokenAtSequenceNum
    message
    verifiedAt
  }
}
```

---

## 3. Embeddable Third-Party SDK (`consent.js` + `/api/v1/sdk/*`)

### 3.1 Drop-In HTML Script Tag
```html
<script
  src="http://localhost:5173/consent.js"
  data-api-key="ch_live_demo_public_sdk_key_2026"
  data-region="EU"
  defer
></script>
```

### 3.2 JavaScript Runtime API
```javascript
// Check if visitor granted a specific purpose before firing analytics/pixels
if (window.ConsentHub.hasConsent("analytics")) {
  initProductAnalytics();
}

// Subscribe to real-time consent updates
window.addEventListener("consenthub:change", (event) => {
  console.log("Updated consent state:", event.detail);
});

// Programmatically re-open the regional banner from your site's footer link
document.getElementById("privacy-choices-link").addEventListener("click", () => {
  window.ConsentHub.openBanner();
});
```

### 3.3 Direct REST Calls with `X-API-Key`
```bash
# Fetch regional banner config + current consent map
curl -s "http://localhost:8080/api/v1/sdk/config?subjectKey=anon:site_visitor_001&region=US_CA" \
  -H "X-API-Key: ch_live_demo_public_sdk_key_2026"

# Record visitor choice from custom integration
curl -s -X POST "http://localhost:8080/api/v1/sdk/consent" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ch_live_demo_public_sdk_key_2026" \
  -d '{
    "subjectKey": "anon:site_visitor_001",
    "region": "US_CA",
    "action": "REJECT_OPTIONAL"
  }'
```
