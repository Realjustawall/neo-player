from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/com/neoplayer/app/ui/NeoPlayerApp.kt"
text = path.read_text()
text = text.replace('"\nLicense: Apache-2.0"', '"\\nLicense: Apache-2.0"')
text = text.replace('"\n"', '"\\n"')
path.write_text(text)
print("UX2 escape repair applied")
