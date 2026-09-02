import re

with open('app/src/main/java/com/example/ui/components/BrowserWebView.kt', 'r') as f:
    content = f.read()

# Replace factory = { swipeRefreshLayout } with factory = { webView }
content = content.replace("factory = { swipeRefreshLayout }", "factory = { webView }")

# Remove the primaryColor, backgroundColor and swipeRefreshLayout definition
pattern = re.compile(r'    val primaryColor = MaterialTheme\.colorScheme\.primary\.toArgb\(\)\n    val backgroundColor = MaterialTheme\.colorScheme\.surface\.toArgb\(\)\n\n    val swipeRefreshLayout = remember\(tab\.id\) \{.*?\n    \}\n\n    LaunchedEffect\(tab\.url, tab\.isPullToRefreshEnabled\) \{.*?\n    \}\n', re.DOTALL)
content = pattern.sub('', content)

with open('app/src/main/java/com/example/ui/components/BrowserWebView.kt', 'w') as f:
    f.write(content)
