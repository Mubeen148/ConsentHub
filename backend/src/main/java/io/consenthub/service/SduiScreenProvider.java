package io.consenthub.service;

import java.util.Map;

/**
 * Decoupled SPI interface implemented by the Server-Driven UI engine (`io.consenthub.graphql` / `sdui`)
 * so `SdkController` can embed the composed `CONSENT_BANNER` screen in `GET /api/v1/sdk/config`
 * without circular package dependencies.
 */
public interface SduiScreenProvider {

    Map<String, Object> buildBannerScreenMap(String subjectKey, String regionCode, String previewVariantKey);
}
