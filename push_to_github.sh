#!/bin/bash

# Navigate to the project directory
cd /home/marcel/Schreibtisch/GreenTrackerWeb/

# Check if we are in a git repository
if [ ! -d .git ]; then
    echo "Error: .git directory not found. Are you in the correct directory?"
    exit 1
fi

# Clean up previously tracked files that are now in .gitignore
echo "Cleaning up git index..."
git rm -r --cached . > /dev/null 2>&1

# Add all relevant changes
echo "Adding changes..."
git add .

# Check if there are changes to commit
if git diff --cached --quiet; then
    echo "No local changes to commit. Checking for remote updates..."
else
    # Commit changes
    echo "Committing changes..."
    git commit -m "Update v1.3.5: Ported Android features (Limit Confirmation, Debouncing, Interactive Stats, Journal Picker, and memory fix)"
fi

# Sync with remote
echo "Syncing with remote (Pulling latest changes)..."
if ! git pull --rebase origin main; then
    echo "Error: Conflict detected during pull. Please resolve conflicts manually."
    exit 1
fi

# Push to GitHub
echo "Pushing to origin main..."
if git push origin main; then
    echo "Successfully pushed to GitHub!"
else
    echo "Error: Push failed. Check your internet connection or permissions."
    exit 1
fi

echo "Done!"
