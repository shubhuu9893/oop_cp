package com.cachebrowse.adblock;

import com.cachebrowse.statistics.CacheStatistics;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects CSS and JavaScript cosmetic rules into WebEngine after page load
 * to hide known advertisement containers, sponsor banners, and promo video overlays
 * that share domains with legitimate content (such as YouTube and major news portals).
 */
public class CosmeticFilter {
    private static final Logger LOG = LoggerFactory.getLogger(CosmeticFilter.class);

    private static final String GENERAL_AD_CSS =
            "/* General Ad Containers */\\n" +
            ".ad, .ads, .ad-banner, .ad-container, .adsbygoogle, .banner-ad, [class*='ad-banner'], " +
            "[id*='google_ads'], [id^='ad-'], [class^='ad-slot'], div[data-ad-unit], " +
            "div[id^='dfp-ad'], div[class*='sponsored-post'], .sponsored-content, " +
            ".taboola-ad, .outbrain-ad, ins.adsbygoogle, " +
            "iframe[id^='google_ads'], iframe[id*='ad-'], iframe[src*='/ads/'], " +
            "div[class*='advertisement'], [class*='ad_container'], [id*='ad_container'] " +
            "{ display: none !important; visibility: hidden !important; height: 0 !important; width: 0 !important; }";

    private static final String YOUTUBE_AD_CSS =
            "/* YouTube Ad elements */\\n" +
            "ytd-ad-slot-renderer, ytd-banner-promo-renderer, ytd-in-feed-ad-layout-renderer, " +
            "ytd-promoted-video-renderer, ytd-promoted-sparkles-web-renderer, " +
            "ytd-player-legacy-desktop-watch-ads-renderer, .ytd-merch-shelf-renderer, " +
            ".video-ads, .ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-message-container, " +
            ".ytp-ad-preview-container, .ytp-ad-skip-button-container, .ytp-ad-player-overlay, " +
            ".ytp-ad-image-overlay, .ytp-ad-overlay-image, .ytp-ad-text-overlay, " +
            "ytd-statement-banner-renderer, ytd-ad-inline-playback-meta-renderer, " +
            "ytd-compact-promoted-video-renderer, ytd-promoted-sparkles-text-search-renderer, " +
            "#masthead-ad, .sparkles-light-cta, .sparkle-renderer " +
            "{ display: none !important; visibility: hidden !important; height: 0 !important; }";

    /**
     * Applies cosmetic filtering script to the given WebEngine instance if enabled.
     * Reports removed/hidden element count to CacheStatistics.
     * 
     * NOTE: This method is called ONCE per successful page load. It increments the statistics
     * atomically and schedules a SINGLE stats UI update callback rather than calling it for
     * each individual element (which would flood the JavaFX thread queue and cause flickering).
     */
    public static void apply(WebEngine engine, String pageUrl, boolean adBlockEnabled, CacheStatistics statistics, Runnable onBlockedCountUpdated) {
        if (!adBlockEnabled || engine == null || pageUrl == null) {
            return;
        }

        Platform.runLater(() -> {
            try {
                boolean isYouTube = pageUrl.contains("youtube.com") || pageUrl.contains("youtu.be");
                String cssToInject = GENERAL_AD_CSS + (isYouTube ? ("\\n" + YOUTUBE_AD_CSS) : "");

                // JavaScript snippet to inject style element and remove identified ad nodes
                String script =
                        "(function() {" +
                        "  try {" +
                        "    var styleId = '__cachebrowse_adblock_style';" +
                        "    if (!document.getElementById(styleId)) {" +
                        "      var style = document.createElement('style');" +
                        "      style.id = styleId;" +
                        "      style.textContent = '" + cssToInject.replace("'", "\\'").replace("\n", " ") + "';" +
                        "      (document.head || document.documentElement).appendChild(style);" +
                        "    }" +
                        "    var selectors = [" +
                        "      '.adsbygoogle', 'ytd-ad-slot-renderer', 'ytd-banner-promo-renderer', " +
                        "      'ytd-in-feed-ad-layout-renderer', 'ytd-promoted-video-renderer', " +
                        "      'ytd-promoted-sparkles-web-renderer', '.ytp-ad-overlay-container', " +
                        "      '[data-ad-unit]', '.ad-container', '.sponsored-content', " +
                        "      'ins.adsbygoogle', '[id*=\"google_ads\"]', 'iframe[src*=\"/ads/\"]', " +
                        "      '[class*=\"advertisement\"]', '[class*=\"ad_container\"]', " +
                        "      'ytd-ad-inline-playback-meta-renderer', '.ytp-ad-module', " +
                        "      '.video-ads', 'ytd-compact-promoted-video-renderer'" +
                        "    ];" +
                        "    var hiddenSet = new Set();" +
                        "    var count = 0;" +
                        "    for (var i = 0; i < selectors.length; i++) {" +
                        "      try {" +
                        "        var elems = document.querySelectorAll(selectors[i]);" +
                        "        for (var j = 0; j < elems.length; j++) {" +
                        "          var elem = elems[j];" +
                        "          if (elem && !hiddenSet.has(elem)) {" +
                        "            var computed = window.getComputedStyle(elem);" +
                        "            if (computed.display !== 'none') {" +
                        "              elem.style.setProperty('display', 'none', 'important');" +
                        "              hiddenSet.add(elem);" +
                        "              count++;" +
                        "            }" +
                        "          }" +
                        "        }" +
                        "      } catch(e) {}" +
                        "    }" +
                        "    if (window.location.hostname.indexOf('youtube.com') !== -1) {" +
                        "      try {" +
                        "        var skipBtns = document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');" +
                        "        for (var i = 0; i < skipBtns.length; i++) {" +
                        "          if (skipBtns[i]) { skipBtns[i].click(); count++; break; }" +
                        "        }" +
                        "      } catch(e) {}" +
                        "      // NOTE: video.currentTime manipulation intentionally removed." +
                        "      // Seeking the video element disrupts the HTML5 media pipeline," +
                        "      // forces re-buffering, and causes WebView Prism texture invalidations" +
                        "      // (RTTexture null / WCPageBackBufferImpl.validate errors) that" +
                        "      // trigger screen flickering. The CSS hiding above is sufficient." +
                        "    }" +
                        "    return Math.max(0, count);" +
                        "  } catch(e) {" +
                        "    return 0;" +
                        "  }" +
                        "})();";

                Object result = engine.executeScript(script);
                if (result instanceof Number num) {
                    int removed = num.intValue();
                    if (removed > 0 && statistics != null) {
                        // Increment the counter atomically for ALL removed elements
                        // Note: This increments incAdsBlocked for cosmetic-hidden elements
                        // The actual blocked count comes from network-layer blocking in the proxy
                        for (int i = 0; i < removed; i++) {
                            statistics.incAdsBlocked();
                            statistics.recordCosmeticAdHidden();
                        }
                        // Schedule a SINGLE stats update callback (not one per element)
                        // The callback itself has throttling to prevent flooding
                        if (onBlockedCountUpdated != null) {
                            onBlockedCountUpdated.run();
                        }
                        LOG.debug("CosmeticFilter hidden {} ad elements on {}", removed, pageUrl);
                    }
                }
            } catch (Exception e) {
                LOG.debug("CosmeticFilter could not execute script on {}: {}", pageUrl, e.getMessage());
            }
        });
    }
}
