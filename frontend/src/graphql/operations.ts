import { gql } from "@apollo/client";

// =============================================================================
// ConsentHub GraphQL Fragments, Queries & Mutations
// 1:1 Parity with docs/contracts/graphql-schema.graphqls
// =============================================================================

export const CONSENT_STATUS_BUNDLE_FIELDS = gql`
  fragment ConsentStatusBundleFields on ConsentStatusBundle {
    subjectKey
    hasConsented
    needsReconsent
    shouldShowBanner
    consentedPolicyVersion
    updatedAt
    activePolicyVersion {
      id
      versionNumber
      versionLabel
      title
      summary
      contentMarkdown
      requiresReconsent
      isActive
      publishedByEmail
      publishedAt
      createdAt
    }
    regionRule {
      id
      regionCode
      regimeName
      consentModel
      bannerStyle
      showRejectAll
      retentionDays
      updatedAt
    }
    decisions {
      purposeId
      slug
      name
      description
      legalBasis
      isEssential
      granted
      source
      updatedAt
    }
  }
`;

export const GET_ME = gql`
  query GetMe {
    me {
      id
      email
      fullName
      role
      region
      subjectKey
      createdAt
      updatedAt
    }
  }
`;

// Max SduiNode tree depth is <= 5 per ADR-009 & sdui-schema.json.
// Total GraphQL field depth here is 8 (<= MaxQueryDepthInstrumentation(8)).
export const GET_SDUI_SCREEN = gql`
  query GetSduiScreen($name: ScreenName!, $context: ScreenContextInput) {
    screen(name: $name, context: $context) {
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
          actions {
            trigger
            type
            payload
          }
          children {
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
              actions {
                trigger
                type
                payload
              }
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
    }
  }
`;

export const GET_MY_CONSENT_STATE = gql`
  ${CONSENT_STATUS_BUNDLE_FIELDS}
  query GetMyConsentState($subjectKey: String, $region: RegionCode) {
    myConsentState(subjectKey: $subjectKey, region: $region) {
      ...ConsentStatusBundleFields
    }
  }
`;

export const GET_MY_CONSENT_HISTORY = gql`
  query GetMyConsentHistory($subjectKey: String, $limit: Int = 50) {
    myConsentHistory(subjectKey: $subjectKey, limit: $limit) {
      id
      subjectKey
      purposeSlug
      purposeName
      previousGranted
      newGranted
      policyVersionLabel
      regionCode
      source
      ipHash
      userAgentSummary
      experimentVariant
      proofHash
      occurredAt
    }
  }
`;

export const GET_MY_DATA_REQUESTS = gql`
  query GetMyDataRequests {
    myDataRequests {
      id
      userId
      userEmail
      userFullName
      requestType
      status
      reason
      adminNotes
      processedByEmail
      requestedAt
      updatedAt
      completedAt
    }
  }
`;

export const GET_PURPOSES_AND_POLICIES = gql`
  query GetPurposesAndPolicies($includeInactive: Boolean = false) {
    purposes(includeInactive: $includeInactive) {
      id
      slug
      name
      description
      legalBasis
      isEssential
      active
      displayOrder
      createdAt
      updatedAt
    }
    policyVersions {
      id
      versionNumber
      versionLabel
      title
      summary
      contentMarkdown
      requiresReconsent
      isActive
      publishedByEmail
      publishedAt
      createdAt
    }
  }
`;

export const GET_REGION_RULES = gql`
  query GetRegionRules {
    regionRules {
      id
      regionCode
      regimeName
      consentModel
      bannerStyle
      showRejectAll
      retentionDays
      updatedAt
    }
  }
`;

export const GET_ADMIN_ANALYTICS = gql`
  query GetAdminAnalytics($days: Int = 30) {
    adminAnalytics(days: $days) {
      days
      totalSubjects
      totalConsentEvents
      overallOptInRate
      reconsentCompletionRate
      pendingDsarCount
      activeExperimentsCount
      dailyMetrics {
        date
        acceptAllCount
        rejectOptionalCount
        customCount
        totalInteractions
        optInRate
      }
      regionalBreakdown {
        regionCode
        regimeName
        totalSubjects
        optInCount
        optOutCount
        consentRate
      }
      purposeOptInRates {
        purposeSlug
        purposeName
        isEssential
        grantedCount
        deniedCount
        optInRate
      }
    }
  }
`;

export const GET_ADMIN_EXPERIMENTS = gql`
  query GetAdminExperiments {
    adminExperiments {
      id
      slug
      name
      description
      targetScreen
      targetRegion
      status
      totalExposures
      totalConversions
      overallConsentRate
      variants {
        key
        name
        weight
        headline
        body
        primaryCtaLabel
        secondaryCtaLabel
        layoutTone
        exposures
        conversions
        consentRate
      }
      createdAt
      updatedAt
    }
  }
`;

export const GET_ADMIN_DATA_REQUESTS = gql`
  query GetAdminDataRequests($status: DataRequestStatus) {
    adminDataRequests(status: $status) {
      id
      userId
      userEmail
      userFullName
      requestType
      status
      reason
      adminNotes
      processedByEmail
      requestedAt
      updatedAt
      completedAt
    }
  }
`;

export const GET_ADMIN_AUDIT_LOGS = gql`
  query GetAdminAuditLogs($limit: Int = 100, $actionFilter: String) {
    adminAuditLogs(limit: $limit, actionFilter: $actionFilter) {
      id
      sequenceNum
      actorId
      actorEmail
      actorRole
      action
      entityType
      entityId
      details
      prevHash
      entryHash
      createdAt
    }
  }
`;

export const VERIFY_AUDIT_CHAIN = gql`
  query VerifyAuditChain {
    verifyAuditChain {
      chainValid
      totalEntriesChecked
      brokenAtSequenceNum
      message
      verifiedAt
    }
  }
`;

export const GET_ADMIN_API_KEYS = gql`
  query GetAdminApiKeys {
    adminApiKeys {
      id
      name
      keyPrefix
      allowedOrigins
      scopes
      active
      lastUsedAt
      createdAt
    }
  }
`;

// --- Mutations ---

export const SAVE_CONSENT_PREFERENCES = gql`
  ${CONSENT_STATUS_BUNDLE_FIELDS}
  mutation SaveConsentPreferences($input: SaveConsentInput!) {
    saveConsentPreferences(input: $input) {
      ...ConsentStatusBundleFields
    }
  }
`;

export const ACCEPT_ALL_CONSENT = gql`
  ${CONSENT_STATUS_BUNDLE_FIELDS}
  mutation AcceptAllConsent(
    $subjectKey: String
    $region: RegionCode
    $source: String
    $experimentId: ID
    $variantKey: String
  ) {
    acceptAllConsent(
      subjectKey: $subjectKey
      region: $region
      source: $source
      experimentId: $experimentId
      variantKey: $variantKey
    ) {
      ...ConsentStatusBundleFields
    }
  }
`;

export const REJECT_OPTIONAL_CONSENT = gql`
  ${CONSENT_STATUS_BUNDLE_FIELDS}
  mutation RejectOptionalConsent(
    $subjectKey: String
    $region: RegionCode
    $source: String
    $experimentId: ID
    $variantKey: String
  ) {
    rejectOptionalConsent(
      subjectKey: $subjectKey
      region: $region
      source: $source
      experimentId: $experimentId
      variantKey: $variantKey
    ) {
      ...ConsentStatusBundleFields
    }
  }
`;

export const UPDATE_MY_PROFILE = gql`
  mutation UpdateMyProfile($input: UpdateProfileInput!) {
    updateMyProfile(input: $input) {
      id
      email
      fullName
      role
      region
      subjectKey
      createdAt
      updatedAt
    }
  }
`;

export const CREATE_DATA_REQUEST = gql`
  mutation CreateDataRequest($input: CreateDataRequestInput!) {
    createDataRequest(input: $input) {
      id
      userId
      userEmail
      userFullName
      requestType
      status
      reason
      adminNotes
      processedByEmail
      requestedAt
      updatedAt
      completedAt
    }
  }
`;

export const UPSERT_PURPOSE = gql`
  mutation UpsertPurpose($input: UpsertPurposeInput!) {
    upsertPurpose(input: $input) {
      id
      slug
      name
      description
      legalBasis
      isEssential
      active
      displayOrder
      createdAt
      updatedAt
    }
  }
`;

export const PUBLISH_POLICY_VERSION = gql`
  mutation PublishPolicyVersion($input: PublishPolicyVersionInput!) {
    publishPolicyVersion(input: $input) {
      id
      versionNumber
      versionLabel
      title
      summary
      contentMarkdown
      requiresReconsent
      isActive
      publishedByEmail
      publishedAt
      createdAt
    }
  }
`;

export const UPDATE_REGION_RULE = gql`
  mutation UpdateRegionRule($input: UpdateRegionRuleInput!) {
    updateRegionRule(input: $input) {
      id
      regionCode
      regimeName
      consentModel
      bannerStyle
      showRejectAll
      retentionDays
      updatedAt
    }
  }
`;

export const CREATE_EXPERIMENT = gql`
  mutation CreateExperiment($input: CreateExperimentInput!) {
    createExperiment(input: $input) {
      id
      slug
      name
      description
      targetScreen
      targetRegion
      status
      totalExposures
      totalConversions
      overallConsentRate
      variants {
        key
        name
        weight
        headline
        body
        primaryCtaLabel
        secondaryCtaLabel
        layoutTone
        exposures
        conversions
        consentRate
      }
      createdAt
      updatedAt
    }
  }
`;

export const UPDATE_EXPERIMENT_STATUS = gql`
  mutation UpdateExperimentStatus($id: ID!, $status: ExperimentStatus!) {
    updateExperimentStatus(id: $id, status: $status) {
      id
      slug
      name
      description
      targetScreen
      targetRegion
      status
      totalExposures
      totalConversions
      overallConsentRate
      variants {
        key
        name
        weight
        headline
        body
        primaryCtaLabel
        secondaryCtaLabel
        layoutTone
        exposures
        conversions
        consentRate
      }
      createdAt
      updatedAt
    }
  }
`;

export const PROCESS_DATA_REQUEST = gql`
  mutation ProcessDataRequest($input: ProcessDataRequestInput!) {
    processDataRequest(input: $input) {
      id
      userId
      userEmail
      userFullName
      requestType
      status
      reason
      adminNotes
      processedByEmail
      requestedAt
      updatedAt
      completedAt
    }
  }
`;

export const CREATE_API_KEY = gql`
  mutation CreateApiKey($input: CreateApiKeyInput!) {
    createApiKey(input: $input) {
      plainTextKey
      apiKey {
        id
        name
        keyPrefix
        allowedOrigins
        scopes
        active
        lastUsedAt
        createdAt
      }
    }
  }
`;

export const REVOKE_API_KEY = gql`
  mutation RevokeApiKey($id: ID!) {
    revokeApiKey(id: $id)
  }
`;
