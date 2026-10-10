package com.cachebrowse.adblock;

import java.util.Set;

/**
 * Ad blocker that blocks URLs matching advertising domains and patterns.
 */
public class AdBlocker {
    private final BlockList blockList;
    private volatile boolean enabled = true;

    public AdBlocker() {
        Set<String> defaultAds = Set.of(
                // Major ad networks & exchanges
                "ads.google.com",
                "doubleclick.net",
                "*.doubleclick.net",
                "googlesyndication.com",
                "*.googlesyndication.com",
                "adservice.google.com",
                "*.adservice.google.com",
                "pagead2.googlesyndication.com",
                "googleadservices.com",
                "*.googleadservices.com",
                "adnxs.com",
                "*.adnxs.com",
                "advertising.com",
                "*.advertising.com",
                "amazon-adsystem.com",
                "*.amazon-adsystem.com",
                "criteo.com",
                "*.criteo.com",
                "criteo.net",
                "*.criteo.net",
                "taboola.com",
                "*.taboola.com",
                "outbrain.com",
                "*.outbrain.com",
                "rubiconproject.com",
                "*.rubiconproject.com",
                "popads.net",
                "pubmatic.com",
                "*.pubmatic.com",
                "openx.net",
                "*.openx.net",
                "casale-media.com",
                "bidswitch.net",
                "smartadserver.com",
                "adroll.com",
                "*.adroll.com",
                "adtechus.com",
                "media.net",
                "*.media.net",
                "serving-sys.com",
                "exponential.com",
                "inmobi.com",
                "applovin.com",
                "unityads.unity3d.com",
                "ironsrc.com",
                "vungle.com",
                "chartboost.com",
                "adcolony.com",
                "revcontent.com",
                "buysellads.com",
                "zedo.com",
                "adblade.com",
                "chitika.com",
                "mgid.com",
                "propellerads.com",
                "adsterra.com",
                "exoclick.com",
                "trafficjunky.com",
                "juicyads.com",
                "zergnet.com",
                "yandexadexchange.net",
                "aniview.com",
                "teads.tv",
                "*.teads.tv",
                "sharethrough.com",
                "sovrn.com",
                "yieldmo.com",
                "gumgum.com",
                "triplelift.com",
                "undertone.com",
                "connatix.com",
                "spotxchange.com",
                "spotx.tv",
                "adform.net",
                "adzerk.net",
                // Specific ad path patterns
                "/pagead/",
                "/adservice/",
                "/ads/sponsor",
                "/ad_tag",
                "doubleclick.net/gampad/"
        );
        this.blockList = new BlockList("data/adblock_rules.txt", defaultAds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAdBlocked(String url) {
        if (!enabled || url == null || url.isBlank()) {
            return false;
        }
        return blockList.matches(url);
    }

    public void addRule(String rule) {
        blockList.addRule(rule);
    }

    public void removeRule(String rule) {
        blockList.removeRule(rule);
    }

    public void clearRules() {
        blockList.clearRules();
    }

    public Set<String> getRules() {
        return blockList.getRules();
    }

    public int getRuleCount() {
        return blockList.size();
    }
}