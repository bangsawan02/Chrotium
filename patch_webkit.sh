#!/bin/bash
sed -i 's/if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)) {/if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {/g' app/src/main/java/com/example/ui/components/BrowserWebView.kt
sed -i 's/WebViewCompat.setWebViewRenderProcessClient(/this.webViewRenderProcessClient =/g' app/src/main/java/com/example/ui/components/BrowserWebView.kt
