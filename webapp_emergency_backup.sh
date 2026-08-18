#!/bin/bash

# Script to update the WebApp emergency backup with confirmation.

echo "--- GreenTracker WebApp Emergency Backup ---"
read -p "Möchten Sie das WebApp Notfall-Backup (webapp_emergency_backup.zip) jetzt überschreiben? (j/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Jj]$ ]]
then
    echo "Erstelle WebApp Backup..."
    zip -r webapp_emergency_backup.zip . -x "*/build/*" "*/.gradle/*" "*/.git/*" "*/.artifacts/*" "*/.kotlin/*" "webapp_emergency_backup.zip" "webapp_emergency_backup.sh"
    echo "WebApp Backup wurde erfolgreich aktualisiert."
else
    echo "Vorgang abgebrochen."
fi
