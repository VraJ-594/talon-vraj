FROM node:24.13.1-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/package.json
RUN npm ci

COPY apps/web apps/web
RUN npm run build:web

FROM nginx:1.29.1-alpine
COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/apps/web/dist /usr/share/nginx/html

EXPOSE 80
