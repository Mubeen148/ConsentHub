import { expect, test, type Page } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";

const SCREENSHOT_DIR = path.resolve(__dirname, "../../docs/screenshots");

function ensureScreenshotDir() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function attachConsoleAndErrorGuards(page: Page): string[] {
  const errors: string[] = [];
  page.on("pageerror", (err) => {
    errors.push(`PageError: ${err.message}`);
  });
  page.on("console", (msg) => {
    if (msg.type() === "error") {
      errors.push(`ConsoleError: ${msg.text()}`);
    }
  });
  return errors;
}

test.describe("ConsentHub End-to-End Verification & Visual Evidence Suite", () => {
  test.beforeAll(() => {
    ensureScreenshotDir();
  });

  test("01. First-visit visitor receives region-aware GDPR SDUI banner and accepts consent", async ({
    page,
  }, testInfo) => {
    const runtimeErrors = attachConsoleAndErrorGuards(page);
    const isDesktop = testInfo.project.name.includes("desktop");

    await page.goto("/portal?region=EU");
    await expect(page.getByTestId("sdui-accept-all")).toBeVisible();
    await expect(page.getByTestId("sdui-reject-optional")).toBeVisible();

    await page.screenshot({
      path: path.join(
        SCREENSHOT_DIR,
        isDesktop ? "01-eu-gdpr-banner-desktop.png" : "01-eu-gdpr-banner-mobile.png"
      ),
      fullPage: false,
    });

    await page.getByTestId("sdui-accept-all").click();
    await expect(page.getByTestId("sdui-accept-all")).toBeHidden();
    await expect(page.getByRole("heading", { level: 1 }).first()).toBeVisible();

    expect(runtimeErrors, `Unexpected console/page errors: ${runtimeErrors.join(" | ")}`).toEqual([]);
  });

  test("02. End-User Privacy Center journey: Login, Overview, Preferences, Ledger, DSAR Export & Settings", async ({
    page,
  }, testInfo) => {
    const runtimeErrors = attachConsoleAndErrorGuards(page);
    const isDesktop = testInfo.project.name.includes("desktop");

    await page.goto("/login");
    await expect(page.getByRole("heading", { name: /sign in to your account/i })).toBeVisible();

    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "02-login-page.png"),
        fullPage: true,
      });
    }

    await page.getByTestId("fill-demo-user-btn").click();
    await page.getByTestId("login-submit-btn").click();

    await page.waitForURL("**/portal");
    await expect(page.getByText(/Privacy & Data Rights Overview/i)).toBeVisible();

    await page.screenshot({
      path: path.join(
        SCREENSHOT_DIR,
        isDesktop ? "03-portal-overview-desktop.png" : "03-portal-overview-mobile.png"
      ),
      fullPage: true,
    });

    // Preference Center (Interactive Controls + SDUI-driven mode)
    await page.goto("/portal/preferences");
    await expect(page.getByText(/Always Active/i).first()).toBeVisible();
    await expect(page.getByTestId("save-preferences-btn")).toBeVisible();

    await page.screenshot({
      path: path.join(
        SCREENSHOT_DIR,
        isDesktop ? "04-preference-center-desktop.png" : "04-preference-center-mobile.png"
      ),
      fullPage: true,
    });

    await page.getByTestId("pref-sdui-mode-btn").click();
    await expect(page.getByTestId("sdui-save-preferences")).toBeVisible();
    await page.getByTestId("sdui-save-preferences").click();

    // Immutable Consent History Timeline
    await page.goto("/portal/history");
    await expect(
      page.getByRole("heading", { name: /Cryptographic Consent History/i }).first()
    ).toBeVisible();
    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "05-consent-ledger.png"),
        fullPage: true,
      });
    }

    // Data Subject Access Requests (DSAR) + Live JSON Export Download
    await page.goto("/portal/requests");
    await expect(
      page.getByRole("heading", { name: /Data Subject Access Requests/i })
    ).toBeVisible();

    const downloadPromise = page.waitForEvent("download");
    await page.getByRole("button", { name: /download.*json|download my data/i }).first().click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain("consenthub-data-export-");

    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "06-dsar-data-rights.png"),
        fullPage: true,
      });
    }

    // Account Settings
    await page.goto("/portal/settings");
    await expect(
      page.getByRole("heading", { name: /Account & Jurisdiction Settings/i })
    ).toBeVisible();
    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "07-account-settings.png"),
        fullPage: true,
      });
    }

    expect(runtimeErrors, `Unexpected console/page errors: ${runtimeErrors.join(" | ")}`).toEqual([]);
  });

  test("03. Admin Governance Console: Analytics, Policies, Regions, Experiments, DSAR Queue, SDK Keys & Audit Chain", async ({
    page,
  }, testInfo) => {
    const runtimeErrors = attachConsoleAndErrorGuards(page);
    const isDesktop = testInfo.project.name.includes("desktop");

    await page.goto("/login");
    await page.getByTestId("fill-demo-admin-btn").click();
    await page.getByTestId("login-submit-btn").click();

    await page.waitForURL("**/admin");
    await expect(page.getByText(/Consent Rate/i).first()).toBeVisible();

    await page.screenshot({
      path: path.join(
        SCREENSHOT_DIR,
        isDesktop ? "08-admin-analytics-desktop.png" : "08-admin-analytics-mobile.png"
      ),
      fullPage: true,
    });

    await page.goto("/admin/policies");
    await expect(page.getByText("v2.0").first()).toBeVisible();
    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "09-admin-policies.png"),
        fullPage: true,
      });
    }

    await page.goto("/admin/regions");
    await expect(page.getByText("GDPR").first()).toBeVisible();
    await expect(page.getByText("CCPA").first()).toBeVisible();
    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "10-admin-regions.png"),
        fullPage: true,
      });
    }

    await page.goto("/admin/experiments");
    await expect(page.getByText(/eu-banner-clarity-q4|Banner Copy Clarity/i).first()).toBeVisible();
    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "11-admin-experiments.png"),
        fullPage: true,
      });
    }

    await page.goto("/admin/requests");
    await expect(
      page.locator("text=/alex\\.rivera@example\\.com|user@consenthub\\.io/i >> visible=true").first()
    ).toBeVisible();
    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "12-admin-dsar-queue.png"),
        fullPage: true,
      });
    }

    await page.goto("/admin/sdk-keys");
    await expect(page.locator("text=/ch_live_demo_/i >> visible=true").first()).toBeVisible();
    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "13-admin-sdk-keys.png"),
        fullPage: true,
      });
    }

    await page.goto("/admin/audit");
    await expect(page.getByTestId("verify-audit-chain-btn")).toBeVisible();
    await page.getByTestId("verify-audit-chain-btn").click();
    await expect(page.getByTestId("audit-chain-verification-banner")).toBeVisible();
    await expect(page.getByTestId("audit-chain-verification-banner")).toContainText(/verified intact/i);

    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "14-admin-audit-verified.png"),
        fullPage: true,
      });
    }

    expect(runtimeErrors, `Unexpected console/page errors: ${runtimeErrors.join(" | ")}`).toEqual([]);
  });

  test("04. Embeddable Third-Party SDK (consent.js) renders Shadow DOM banner and updates live state", async ({
    page,
  }, testInfo) => {
    const runtimeErrors = attachConsoleAndErrorGuards(page);
    const isDesktop = testInfo.project.name.includes("desktop");

    await page.goto("/sdk-demo.html");
    const sdkAcceptBtn = page.locator("#consenthub-sdk-root").getByTestId("sdk-accept-all");
    await expect(sdkAcceptBtn).toBeVisible();

    if (isDesktop) {
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "15-embeddable-sdk-demo.png"),
        fullPage: false,
      });
    }

    await sdkAcceptBtn.click();
    await expect(sdkAcceptBtn).toBeHidden();
    await expect(page.locator("#chip-analytics")).toHaveText("analytics: GRANTED");

    expect(runtimeErrors, `Unexpected console/page errors: ${runtimeErrors.join(" | ")}`).toEqual([]);
  });
});
