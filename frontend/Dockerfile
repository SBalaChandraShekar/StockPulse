FROM node:20-alpine AS build
WORKDIR /app

COPY package.json ./
COPY index.html ./
COPY vite.config.js ./
COPY src ./src

RUN npm install
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
