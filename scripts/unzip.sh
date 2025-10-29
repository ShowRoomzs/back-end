#!/bin/bash

# 압축 해제
echo "Unzipping deploy.zip..."
cd /home/ubuntu
unzip -o deploy.zip -d /home/ubuntu

# 압축 해제 후 zip 삭제
rm -f /home/ubuntu/deploy.zip

echo "Unzip complete."
