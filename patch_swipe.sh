#!/bin/bash
sed -i '/val swipeRefreshLayout = remember(tab.id) {/,/swipeRefreshLayout.isEnabled = tab.isPullToRefreshEnabled && !isHome && !isSPA/d' app/src/main/java/com/example/ui/components/BrowserWebView.kt
sed -i '/    }/d' app/src/main/java/com/example/ui/components/BrowserWebView.kt # Need a better way
