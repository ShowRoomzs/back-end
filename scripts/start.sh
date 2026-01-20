#!/bin/bash

cd /home/ubuntu/app-deploy

CONTAINER_NAME="showroomz-backend"
# 아래 주소는 본인의 ECR URI로 변경하세요
IMAGE_NAME="<AWS_ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/showroomz-backend:latest"

echo "Logging in to ECR..."
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin <AWS_ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com

echo "Pulling Docker image..."
docker pull $IMAGE_NAME

echo "Starting Docker container..."
# --env-file .env 옵션 추가 (배포된 .env 파일 사용)
docker run -d \
    --name $CONTAINER_NAME \
    -p 8080:8080 \
    --env-file .env \
    -v /home/ubuntu/logs:/logs \
    $IMAGE_NAME

echo "Deployment finished."
