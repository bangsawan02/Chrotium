with open('app/src/main/java/com/example/ui/dialogs/TabsSheet.kt', 'r') as f:
    text = f.read()

blocks = text.split('@Composable')

# Fix block 2 by removing the last } in it if it's extra
block2 = blocks[2]
open_c = block2.count('{')
close_c = block2.count('}')
if close_c > open_c:
    # remove the last '}'
    last_brace_idx = block2.rfind('}')
    if last_brace_idx != -1:
        block2 = block2[:last_brace_idx] + block2[last_brace_idx+1:]
        blocks[2] = block2

# Now combine back
text = '@Composable'.join(blocks)
with open('app/src/main/java/com/example/ui/dialogs/TabsSheet.kt', 'w') as f:
    f.write(text)

