@echo off
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/TouchDX/TouchDX-AndroidClient.git
git push -u origin main --force
