#!/bin/bash

# [안전장치] 스크립트 실행 중 에러 발생 시 즉시 중단
set -e

# [수정됨] 배포된 프로젝트 경로로 이동 (appspec.yml의 destination 경로)
cd /home/ubuntu/app-deploy

# .env 파일이 있으면 불러오기 (환경변수 로드)
if [ -f .env ]; then
  # xargs는 특수문자나 공백 처리에 취약할 수 있으므로, 주석(#)이 있다면 아래 방식이 더 안전할 수 있습니다.
  export $(grep -v '^#' .env | xargs)
fi

# 필수 변수 확인
if [ -z "$AWS_ACCOUNT_ID" ] || [ -z "$AWS_REGION" ] || [ -z "$ECR_REPO_NAME" ]; then
  echo "Error: 필수 환경변수(AWS_ACCOUNT_ID, AWS_REGION, ECR_REPO_NAME)가 설정되지 않았습니다."
  exit 1
fi

# ECR 이미지 전체 주소 조합
ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
FULL_IMAGE_NAME="${ECR_URI}/${ECR_REPO_NAME}:latest"

echo "Deploying Image: $FULL_IMAGE_NAME"

echo "1. Logging in to ECR..."
# AWS CLI v2 권장 방식
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_URI

echo "2. Pulling latest images..."
# docker-compose가 환경변수를 인식하도록 설정
export IMAGE_NAME=$FULL_IMAGE_NAME
docker compose pull

echo "3. Starting containers..."
# 변경된 컨테이너만 재시작 (DB 등은 유지)
docker compose up -d

# (선택사항) 공간 확보를 위해 사용하지 않는 구버전 이미지 삭제
echo "4. Cleaning up old images..."
docker image prune -f

echo "Deployment finished successfully."
