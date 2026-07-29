# Implementation Plan - Web App Parity with Android v1.2.2

Bring the GreenTracker Web App to full feature parity with the Android app (v1.2.2), including localization, precision tracking, UI refinements, and functional bug fixes.

## User Review Required

> [!IMPORTANT]
> This update will synchronize the Web App with the Android version. Key changes include restoring 0.1g precision, standardising "Usage / Verbrauch" terminology, and adding the "Sicher?" (Rly?) confirmation for permanent deletion in the Trash.

## Proposed Changes

### [Core Logic & Data]

#### [MODIFY] [SmokeViewModel.kt](file:///home/marcel/Schreibtisch/GreenTrackerWeb/composeApp/src/commonMain/kotlin/com/example/ui/viewmodel/SmokeViewModel.kt)
- **Precision**: Remove `kotlin.math.round` from `logSession` to allow decimal tracking.
- **Localization**: Update the `translate` function with all missing strings (Usage, Widget terminology, Changelog entries, etc.).

### [User Interface]

#### [MODIFY] [SmokeTrackerScreen.kt](file:///home/marcel/Schreibtisch/GreenTrackerWeb/composeApp/src/commonMain/kotlin/com/example/ui/screens/SmokeTrackerScreen.kt)
- **HomeScreen**:
    - Update "Consumption" -> "Usage".
    - Show 0.1g precision in the main counter.
    - Standardise "app dashboard" -> "Widget" in quick log notes.
- **HistoryScreen**:
    - Implement "Heute" / "Gestern" headers.
    - Ensure logical day grouping (04:00 - 04:00).
- **JournalScreen**:
    - Add `rememberLazyListState` and auto-scroll to top when adding a new entry.
- **SettingsScreen**:
    - Implement the interactive **Versionsverlauf** (Changelog) for v1.2.2.
    - Fix missing German translation for "Set your preferred language".
    - Update Trash section labels.
- **Trash Refinement**:
    - **TrashedSessionRow** & **TrashedStrainRow**: Add the "Sicher?" (Rly?) confirmation step for permanent deletion.
    - **Number Bug**: Use `.format(1)` for session grams in the trash to prevent long floating-point strings.

## Verification Plan

### Manual Verification
1.  **Precision**: Log 0.2g. Verify the Home screen and History show 0.2g, not 0g or 1g.
2.  **Localization**: Switch to German. Verify "Verbrauch", "Heute", "Löschen", and "Abbrechen" are all correct.
3.  **Trash**: Delete an item. Go to Trash. Tap the delete icon. Verify "Sicher?" appears and auto-resets after 3 seconds.
4.  **Journal**: Add a new strain. Verify the list scrolls to the top automatically.
5.  **Changelog**: Verify "Versionsverlauf" lists v1.2.2 at the top.
