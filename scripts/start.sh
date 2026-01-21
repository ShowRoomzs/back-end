#!/bin/bash

cd /home/ubuntu/app-deploy

# 환경변수 설정
export IMAGE_NAME="773182954354.dkr.ecr.ap-northeast-2.amazonaws.com/showroomz-backend:latest"

echo "Logging in to ECR..."
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 773182954354.dkr.ecr.ap-northeast-2.amazonaws.com

echo "Pulling images..."
# docker-compose.yml에 정의된 최신 이미지를 가져옴
docker-compose pull

echo "Starting containers..."
# [중요] up -d는 변경 사항이 있는 컨테이너(App)만 재시작하고,
# 변경이 없는 컨테이너(DB)는 건드리지 않습니다.
docker-compose up -d

echo "Deployment finished."
