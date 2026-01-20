#!/bin/bash

cd /home/ubuntu/app-deploy

# [변경] 전체 서비스 종료(down)가 아니라, 백엔드 앱(app)만 중지하고 제거합니다.
# "showroomz-backend"는 docker-compose.yml의 container_name과 일치해야 합니다.
if [ "$(docker ps -aq -f name=showroomz-backend)" ]; then
    echo "Stopping Spring Boot application..."
    docker stop showroomz-backend
    echo "Removing Spring Boot container..."
    docker rm showroomz-backend
fi

# (선택) 불필요한 이미지 정리 (공간 확보)
docker image prune -f
