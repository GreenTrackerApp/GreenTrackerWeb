#!/bin/bash

# Navigate to the web project directory
cd /home/marcel/Schreibtisch/GreenTrackerWeb/

echo "🚀 Starting GreenTracker Web v1.2.2 Push..."

# 1. Stage all changes
git add .

# 2. Create the commit
git commit -m "v1.2.2 Full Sync: Triple-Tap, 2-Factor Trash, Custom Range & Localization"

# 3. Push to GitHub
# Note: This assumes 'origin' and 'main' are already configured.
git push origin main

echo "✅ Successfully pushed to GitHub!"
