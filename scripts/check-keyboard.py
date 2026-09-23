"""Tap the swipe menu's keyboard action and verify the Android IME appears."""

import re
import subprocess
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True)


root = ET.fromstring(adb("shell", "cat", "/sdcard/window.xml"))
button = next((node for node in root.iter() if node.get("text") == "Show keyboard"), None)
if button is None:
    raise SystemExit("Keyboard action is missing from the swipe menu")

left, top, right, bottom = map(int, re.findall(r"\d+", button.get("bounds")))
adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
for _ in range(8):
    time.sleep(1)
    state = adb("shell", "dumpsys", "input_method")
    if "mIsInputViewShown=true" in state and "mShowInputRequested=true" in state:
        print("Android soft keyboard appeared")
        break
else:
    print("Input method state:", state[-5000:])
    raise SystemExit("Android soft keyboard did not appear")
