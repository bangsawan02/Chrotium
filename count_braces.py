with open('app/src/main/java/com/example/ui/dialogs/TabsSheet.kt', 'r') as f:
    text = f.read()

# Let's count { and } in each top-level block
blocks = text.split('@Composable')

for i, block in enumerate(blocks):
    open_count = block.count('{')
    close_count = block.count('}')
    print(f"Block {i}: {open_count} open, {close_count} close. Diff: {open_count - close_count}")
