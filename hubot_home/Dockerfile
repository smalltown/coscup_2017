FROM node:latest

RUN apt-get update && apt-get install -y redis-server && rm -rf /var/lib/apt/lists/* 

RUN npm install -g hubot coffee-script yo generator-hubot

CMD /bin/bash
