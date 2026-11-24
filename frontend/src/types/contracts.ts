// =============================================================================
// ConsentHub — Frozen Contract Type Definitions
// Matches docs/contracts/graphql-schema.graphqls, rest-endpoints.md, sdui-schema.json
// =============================================================================

export type UserRole = "USER" | "ADMIN";
export type RegionCode = "EU" | "US_CA" | "GLOBAL";
export type RegulatoryRegime = "GDPR" | "CCPA" | "DEFAULT";
export type ConsentModel = "OPT_IN" | "OPT_OUT" | "NOTICE_ONLY";
export type BannerStyle = "MODAL" | "BANNER" | "COMPACT";
export type LegalBasis = "STRICTLY_NECESSARY" | "CONSENT" | "LEGITIMATE_INTEREST_OPT_OUT";
export type ScreenName = "CONSENT_BANNER" | "PREFERENCE_CENTER" | "PRIVACY_OVERVIEW";
export type ExperimentStatus = "DRAFT" | "RUNNING" | "PAUSED" | "CONCLUDED";
export type DataRequestType = "EXPORT" | "DELETE";
export type DataRequestStatus = "PENDING" | "IN_REVIEW" | "APPROVED" | "COMPLETED" | "REJECTED";

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  region: RegionCode;
  subjectKey: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConsentPurpose {
  id: string;
  slug: string;
  name: string;
  description: string;
  legalBasis: LegalBasis;
  isEssential: boolean;
  active: boolean;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface PolicyVersion {
  id: string;
  versionNumber: number;
  versionLabel: string;
  title: string;
  summary: string;
  contentMarkdown: string;
  requiresReconsent: boolean;
  isActive: boolean;
  publishedByEmail: string | null;
  publishedAt: string;
  createdAt: string;
}

export interface RegionRule {
  id: string;
  regionCode: RegionCode;
  regimeName: RegulatoryRegime;
  consentModel: ConsentModel;
  bannerStyle: BannerStyle;
  showRejectAll: boolean;
  retentionDays: number;
  updatedAt: string;
}

export interface PurposeConsentState {
  purposeId: string;
  slug: string;
  name: string;
  description: string;
  legalBasis: LegalBasis;
  isEssential: boolean;
  granted: boolean;
  source: string | null;
  updatedAt: string | null;
}

export interface ConsentStatusBundle {
  subjectKey: string;
  hasConsented: boolean;
  needsReconsent: boolean;
  shouldShowBanner: boolean;
  consentedPolicyVersion: string | null;
  activePolicyVersion: PolicyVersion;
  regionRule: RegionRule;
  decisions: PurposeConsentState[];
  updatedAt: string | null;
}

export interface ConsentHistoryEntry {
  id: string;
  subjectKey: string;
  purposeSlug: string;
  purposeName: string;
  previousGranted: boolean | null;
  newGranted: boolean;
  policyVersionLabel: string;
  regionCode: RegionCode;
  source: string;
  ipHash: string | null;
  userAgentSummary: string | null;
  experimentVariant: string | null;
  proofHash: string;
  occurredAt: string;
}

// --- SDUI Contract Types ---

export type CanonicalComponentType =
  | "Container"
  | "Card"
  | "Stack"
  | "BannerShell"
  | "Header"
  | "Text"
  | "Badge"
  | "Divider"
  | "PurposeToggleRow"
  | "PolicySummaryAccordion"
  | "ActionBar"
  | "Button"
  | "NoticeCallout"
  | "StatGrid"
  | "StatItem";

export type SduiActionTrigger = "onClick" | "onToggle";

export type SduiActionType =
  | "SUBMIT_CONSENT"
  | "ACCEPT_ALL"
  | "REJECT_OPTIONAL"
  | "REJECT_NON_ESSENTIAL"
  | "TOGGLE_PURPOSE"
  | "NAVIGATE"
  | "OPEN_PREFERENCES"
  | "DISMISS";

export interface SduiAction {
  trigger: string;
  type: string;
  payload?: Record<string, unknown> | null;
}

export interface SduiNode {
  id: string;
  type: string;
  props: Record<string, unknown>;
  children: SduiNode[];
  actions: SduiAction[];
}

export interface SduiExperimentContext {
  experimentId: string;
  experimentSlug: string;
  variantKey: string;
}

export interface SduiScreen {
  schemaVersion: string;
  screenName: ScreenName;
  policyVersion: string;
  needsReconsent: boolean;
  regionCode: RegionCode;
  regime: RegulatoryRegime;
  experiment: SduiExperimentContext | null;
  root: SduiNode;
}

export interface ScreenContextInput {
  subjectKey?: string;
  region?: RegionCode;
  locale?: string;
  deviceType?: string;
  previewVariantKey?: string;
}

// --- DSAR, Experimentation, Analytics, API Keys & Audit Types ---

export interface DataRequest {
  id: string;
  userId: string;
  userEmail: string;
  userFullName: string;
  requestType: DataRequestType;
  status: DataRequestStatus;
  reason: string | null;
  adminNotes: string | null;
  processedByEmail: string | null;
  requestedAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface ExperimentVariantMetric {
  key: string;
  name: string;
  weight: number;
  headline: string;
  body: string;
  primaryCtaLabel: string;
  secondaryCtaLabel: string;
  layoutTone: string;
  exposures: number;
  conversions: number;
  consentRate: number;
}

export interface ExperimentSummary {
  id: string;
  slug: string;
  name: string;
  description: string;
  targetScreen: ScreenName;
  targetRegion: RegionCode | null;
  status: ExperimentStatus;
  totalExposures: number;
  totalConversions: number;
  overallConsentRate: number;
  variants: ExperimentVariantMetric[];
  createdAt: string;
  updatedAt: string;
}

export interface DailyConsentMetric {
  date: string;
  acceptAllCount: number;
  rejectOptionalCount: number;
  customCount: number;
  totalInteractions: number;
  optInRate: number;
}

export interface RegionConsentMetric {
  regionCode: RegionCode;
  regimeName: RegulatoryRegime;
  totalSubjects: number;
  optInCount: number;
  optOutCount: number;
  consentRate: number;
}

export interface PurposeOptInMetric {
  purposeSlug: string;
  purposeName: string;
  isEssential: boolean;
  grantedCount: number;
  deniedCount: number;
  optInRate: number;
}

export interface ConsentAnalytics {
  days: number;
  totalSubjects: number;
  totalConsentEvents: number;
  overallOptInRate: number;
  reconsentCompletionRate: number;
  pendingDsarCount: number;
  activeExperimentsCount: number;
  dailyMetrics: DailyConsentMetric[];
  regionalBreakdown: RegionConsentMetric[];
  purposeOptInRates: PurposeOptInMetric[];
}

export interface AuditLogEntry {
  id: string;
  sequenceNum: number;
  actorId: string | null;
  actorEmail: string;
  actorRole: string;
  action: string;
  entityType: string;
  entityId: string;
  details: Record<string, unknown>;
  prevHash: string;
  entryHash: string;
  createdAt: string;
}

export interface AuditChainVerification {
  chainValid: boolean;
  totalEntriesChecked: number;
  brokenAtSequenceNum: number | null;
  message: string;
  verifiedAt: string;
}

export interface ApiKeyInfo {
  id: string;
  name: string;
  keyPrefix: string;
  allowedOrigins: string;
  scopes: string;
  active: boolean;
  lastUsedAt: string | null;
  createdAt: string;
}

export interface CreatedApiKeyResult {
  plainTextKey: string;
  apiKey: ApiKeyInfo;
}

// --- Input Types ---

export interface PurposeDecisionInput {
  purposeSlug: string;
  granted: boolean;
}

export interface SaveConsentInput {
  subjectKey?: string;
  region?: RegionCode;
  source?: string;
  experimentId?: string;
  variantKey?: string;
  decisions: PurposeDecisionInput[];
}

export interface UpdateProfileInput {
  fullName: string;
  region: RegionCode;
}

export interface CreateDataRequestInput {
  requestType: DataRequestType;
  reason?: string;
  confirmationText?: string;
}

export interface UpsertPurposeInput {
  id?: string;
  slug: string;
  name: string;
  description: string;
  legalBasis: LegalBasis;
  isEssential: boolean;
  active: boolean;
  displayOrder: number;
}

export interface PublishPolicyVersionInput {
  versionLabel: string;
  title: string;
  summary: string;
  contentMarkdown: string;
  requiresReconsent: boolean;
  activateImmediately?: boolean;
}

export interface UpdateRegionRuleInput {
  regionCode: RegionCode;
  regimeName: RegulatoryRegime;
  consentModel: ConsentModel;
  bannerStyle: BannerStyle;
  showRejectAll: boolean;
  retentionDays: number;
}

export interface ExperimentVariantInput {
  key: string;
  name: string;
  weight: number;
  headline: string;
  body: string;
  primaryCtaLabel: string;
  secondaryCtaLabel: string;
  layoutTone?: string;
}

export interface CreateExperimentInput {
  slug: string;
  name: string;
  description: string;
  targetScreen: ScreenName;
  targetRegion?: RegionCode | null;
  status?: ExperimentStatus;
  variants: ExperimentVariantInput[];
}

export interface ProcessDataRequestInput {
  id: string;
  status: DataRequestStatus;
  adminNotes?: string;
}

export interface CreateApiKeyInput {
  name: string;
  allowedOrigins?: string;
  scopes?: string;
}

// --- REST Error & Auth Envelopes ---

export interface ApiErrorEnvelope {
  timestamp?: string;
  status: number;
  error?: string;
  code: string;
  message: string;
  path?: string;
  correlationId?: string;
  fieldErrors?: Record<string, string>;
}

export interface AuthSessionResponse {
  user: UserProfile;
  csrfToken: string;
}
