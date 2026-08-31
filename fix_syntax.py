with open('app/src/main/java/com/example/ui/dialogs/TabsSheet.kt', 'r') as f:
    content = f.read()

# Fix the extra } before @Composable fun TabsSheet
content = content.replace("}\n@Composable\nfun TabsSheet", "@Composable\nfun TabsSheet")

# Fix the duplicate Text(
content = content.replace("                            Text(\n                            Text(\n                                text = \"Tutup Semua\",", "                            Text(\n                                text = \"Tutup Semua\",")

with open('app/src/main/java/com/example/ui/dialogs/TabsSheet.kt', 'w') as f:
    f.write(content)
