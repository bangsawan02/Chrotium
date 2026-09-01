import re

with open('app/src/main/java/com/example/ui/dialogs/TabsSheet.kt', 'r') as f:
    lines = f.readlines()

# The user wants TabsSheet to have BottomCenter, fillMaxWidth, drag handle, and correct braces.
# We will just fix the braces first.
# Wait, let's just write the end of the file correctly.

# Find line with "Tutup Semua"
idx = 0
for i, line in enumerate(lines):
    if '"Tutup Semua"' in line:
        idx = i
        break

if idx > 0:
    # Rewrite the closing part
    end_part = """                                text = "Tutup Semua",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                } // inner col
                } // outer col
            }
        }
    }
}

@Composable
private fun TabListCard("""
    
    # find where @Composable private fun TabListCard starts
    idx2 = 0
    for i in range(idx, len(lines)):
        if "private fun TabListCard(" in lines[i]:
            idx2 = i
            break
            
    if idx2 > 0:
        new_lines = lines[:idx] + [end_part + "\n"] + lines[idx2+1:]
        with open('app/src/main/java/com/example/ui/dialogs/TabsSheet.kt', 'w') as f:
            f.writelines(new_lines)
        print("Fixed TabsSheet")
