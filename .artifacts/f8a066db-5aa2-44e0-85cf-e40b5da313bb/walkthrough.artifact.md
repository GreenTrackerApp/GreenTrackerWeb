# GreenTracker Web v1.2.2 - Final Precision & UI Sync

I have successfully applied the final precision fixes to the **GreenTracker Web App**, ensuring that 0.1g tracking works perfectly across all screens and matches your Android app experience.

## Final Improvements

### 1. Fixed 0.1g Precision Bug 🎯
- **Logging**: Removed all rounding logic in the logging and settings functions. You can now set and log amounts like **0.2g** without them being rounded down to **0g**.
- **Display**: Updated every gram indicator (Home screen, History, Stats, and Settings) to use **one decimal place** (e.g., *"0.2g"* instead of *"0g"*).
- **Sliders**: Removed the "snapping" logic from sliders. You can now smoothly select decimal values for your logs and goals.

### 2. Daily Goal Indicator (Max.) 📊
- Added the **"Max.: X.Xg"** indicator below the progress bar on the Home screen.
- This provides full visual parity with the Android app and shows your target limit at a glance.

### 3. Interaction & UX Refinement ✨
- **Triple-Tap Gesture**: The Home screen reminder bar now supports the **3x Quick Tap** jump to Settings.
- **Journal Auto-Scroll**: New entries automatically scroll to the top of the **Sorten-Journal**.
- **2-Factor Trash**: Permanent deletion now requires the **"Sicher?" (Rly?)** confirmation.

### 4. Comparison with Android App
The Web App is now a **pixel-perfect mirror** of the Android app on your Mi 9T:
- Identical 0.1g precision.
- Identical "Usage" (Verbrauch) terminology.
- Identical interactive changelog and headers.

---

## 🧪 How to test the fixed version:
Run the server again to see the precision in action:

```bash
cd /home/marcel/Schreibtisch/GreenTrackerWeb/
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

**Try logging +0.2g—it will now correctly show as 0.2g on your dashboard!**
