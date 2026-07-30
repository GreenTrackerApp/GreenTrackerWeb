#!/bin/bash

# Navigate to the project directory
cd /home/marcel/Schreibtisch/GreenTrackerWeb/

# Check if we are in a git repository
if [ ! -d .git ]; then
    echo "Error: .git directory not found. Are you in the correct directory?"
    exit 1
fi

# Clean up previously tracked files that are now in .gitignore (like build/ and .gradle/)
echo "Cleaning up git index..."
git rm -r --cached . > /dev/null 2>&1

# Add all relevant changes
echo "Adding changes..."
git add .

# Check if there are changes to commit
if git diff --cached --quiet; then
    echo "No changes to commit."
else
    # Commit changes
    echo "Committing changes..."
    git commit -m "Sync web app v1.2.2: Fix Stats scale, Journal alignment, and German localization"

    # Push to GitHub
    echo "Pushing to origin main..."
    git push origin main
fi

echo "Done!"
