#!/bin/bash
cd /home/ubuntu

echo "Stopping existing application..."
sudo pkill -f 'app.jar' || true

echo "Starting new application with prod profile..."
# 프로덕션 프로파일로 실행
nohup java -Dspring.profiles.active=prod -jar app.jar > app.log 2>&1 &
