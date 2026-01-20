#!/bin/bash

CONTAINER_NAME="showroomz-backend"

# 실행 중인 컨테이너 확인 및 중지/삭제
if [ $(docker ps -aq -f name=$CONTAINER_NAME) ]; then
    echo "Stopping existing container..."
    docker stop $CONTAINER_NAME
    echo "Removing existing container..."
    docker rm $CONTAINER_NAME
fi

# (선택 사항) 사용하지 않는 도커 이미지 정리
# docker image prune -f
