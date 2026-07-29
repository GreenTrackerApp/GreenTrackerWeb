# Implementation Plan - Web App: Custom History Time Period

Implement the "Custom Range" filter in the History screen for the web app, allowing users to filter their smoke logs by a specific start and end date using the browser's native date picker.

## User Review Required

> [!NOTE]
> I will add a "Custom Range" option to the History filters. When selected, you'll be able to click on "From" and "To" buttons to open your browser's date picker and select a specific timeframe.

## Proposed Changes

### [UI Enhancements]

#### [MODIFY] [SmokeTrackerScreen.kt](file:///home/marcel/Schreibtisch/GreenTrackerWeb/composeApp/src/commonMain/kotlin/com/example/ui/screens/SmokeTrackerScreen.kt)
- **`HistoryFilter` Enum**: Add `CUSTOM` to the enum.
- **Web Date Picker Helper**: Add an `@JsFun` external function `triggerWebDatePicker` to invoke the browser's `<input type="date">`.
- **`HistoryScreen` Update**:
    - Add states for `customStartDate` and `customEndDate`.
    - Implement the logic to filter sessions between these two timestamps.
    - Add a UI row (only visible when `CUSTOM` is selected) with buttons to set the dates.
- **`translate` Updates**: Ensure "Custom Range", "From:", "To:", "Select Start Date", and "Select End Date" are translated.

### [Core Logic & Data]

#### [MODIFY] [SmokeViewModel.kt](file:///home/marcel/Schreibtisch/GreenTrackerWeb/composeApp/src/commonMain/kotlin/com/example/ui/viewmodel/SmokeViewModel.kt)
- **Localization**: Add translations for the new date-related strings.

## Verification Plan

### Manual Verification
1.  **History Screen**: Open the web app and go to the History tab.
2.  **Filter Selection**: Tap on "Custom Range" (Zeitraum wählen).
3.  **Date Selection**:
    - Tap "Select Start Date". Verify the browser's date picker opens. Select a date.
    - Tap "Select End Date". Select a later date.
4.  **Filtering**: Verify that only logs within that specific timeframe are shown in the list.
5.  **Language Test**: Verify all labels are correctly translated in German.
