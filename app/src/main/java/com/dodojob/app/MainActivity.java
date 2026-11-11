package com.dodojob.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.AdLoader;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class MainActivity extends Activity {
    // Real AdMob IDs. For internal testing, replace with Google's official test IDs or use test devices.
    private static final String BANNER_ID = BuildConfig.DEBUG ? "ca-app-pub-3940256099942544/9214589741" : "ca-app-pub-7687605449802442/7335374507";
    private static final String INTERSTITIAL_ID = BuildConfig.DEBUG ? "ca-app-pub-3940256099942544/1033173712" : "ca-app-pub-7687605449802442/5196296325";
    private static final String NATIVE_ID = BuildConfig.DEBUG ? "ca-app-pub-3940256099942544/2247696110" : "ca-app-pub-7687605449802442/2570132984";
    private WebView web;
    private FrameLayout bannerSlot, nativeSlot;
    private AdView banner;
    private NativeAd nativeAd;
    private InterstitialAd interstitial;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastPage = "home";
    private int eligibleTransitions = 0;
    private long lastInterstitialTime = 0;
    private boolean loaded = false;
    private boolean adsStarted = false;
    private final Runnable pageWatcher = new Runnable() {
        @Override public void run() {
            if (loaded && web != null) {
                web.evaluateJavascript("(typeof page==='string'?page:'home')", value -> {
                    String current = value == null ? "home" : value.replace("\"", "");
                    if (!current.equals(lastPage)) {
                        lastPage = current;
                        nativeSlot.setVisibility("home".equals(current) && nativeAd != null ? View.VISIBLE : View.GONE);
                        if ("jobs".equals(current) || "candidates".equals(current)) {
                            eligibleTransitions++;
                            long now = System.currentTimeMillis();
                            if (eligibleTransitions >= 6 && now-lastInterstitialTime > 180000 && interstitial != null) {
                                eligibleTransitions = 0;
                                lastInterstitialTime = now;
                                InterstitialAd showing = interstitial;
                                interstitial = null;
                                showing.show(MainActivity.this);
                            }
                        }
                    }
                });
            }
            handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        web = findViewById(R.id.web);
        bannerSlot = findViewById(R.id.banner_slot);
        nativeSlot = findViewById(R.id.native_slot);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
            @Override public void onPageFinished(WebView view, String url) { loaded = true; }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame() && request.getUrl().toString().startsWith("https://willowy-gnome-99c69b.netlify.app/")) {
                    view.loadUrl("https://appassets.androidplatform.net/assets/index.html");
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        // Use the verified public origin for Firebase Auth; bundled HTML remains as a fallback.
        web.loadUrl("https://willowy-gnome-99c69b.netlify.app/");
        handler.post(pageWatcher);
        // UMP checks whether a consent form is required before requesting ads.
        ConsentInformation consentInfo = UserMessagingPlatform.getConsentInformation(this);
        consentInfo.requestConsentInfoUpdate(this, new ConsentRequestParameters.Builder().build(),
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, formError -> {
                if (consentInfo.canRequestAds()) startAds();
            }),
            requestError -> { if (consentInfo.canRequestAds()) startAds(); });
        if (consentInfo.canRequestAds()) startAds();
    }
    private void startAds() {
        if (adsStarted || isFinishing() || isDestroyed()) return;
        adsStarted = true;
        MobileAds.initialize(this, status -> runOnUiThread(() -> {
            loadBanner(); loadInterstitial(); loadNative();
        }));
    }
    private void loadBanner() {
        banner = new AdView(this);
        banner.setAdUnitId(BANNER_ID);
        int width = (int)(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, width));
        bannerSlot.addView(banner);
        banner.loadAd(new AdRequest.Builder().build());
    }
    private void loadInterstitial() {
        InterstitialAd.load(this, INTERSTITIAL_ID, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd ad) {
                interstitial = ad;
                ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdDismissedFullScreenContent() { loadInterstitial(); }
                    @Override public void onAdFailedToShowFullScreenContent(AdError error) { loadInterstitial(); }
                });
            }
            @Override public void onAdFailedToLoad(LoadAdError error) { interstitial = null; }
        });
    }
    private void loadNative() {
        new AdLoader.Builder(this, NATIVE_ID).forNativeAd(ad -> {
            if (isFinishing() || isDestroyed()) { ad.destroy(); return; }
            if (nativeAd != null) nativeAd.destroy();
            nativeAd = ad;
            nativeSlot.removeAllViews();
            NativeAdView view = (NativeAdView)getLayoutInflater().inflate(R.layout.native_ad, nativeSlot, false);
            TextView headline = view.findViewById(R.id.ad_headline);
            TextView body = view.findViewById(R.id.ad_body);
            Button cta = view.findViewById(R.id.ad_cta);
            headline.setText(ad.getHeadline());
            view.setHeadlineView(headline);
            if (ad.getBody() != null) { body.setText(ad.getBody()); view.setBodyView(body); }
            else body.setVisibility(View.GONE);
            if (ad.getCallToAction() != null) { cta.setText(ad.getCallToAction()); view.setCallToActionView(cta); }
            else cta.setVisibility(View.GONE);
            view.setNativeAd(ad);
            nativeSlot.addView(view);
            nativeSlot.setVisibility("home".equals(lastPage) ? View.VISIBLE : View.GONE);
        }).withAdListener(new AdListener() {}).build().loadAd(new AdRequest.Builder().build());
    }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else if (web != null) web.evaluateJavascript("if(typeof go==='function'){go('home')}", null);
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(pageWatcher);
        if (banner != null) banner.destroy();
        if (nativeAd != null) nativeAd.destroy();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
