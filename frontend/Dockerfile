# Frontend (Vue) multi-stage build.
# Runtime imageにはNode/npm/node_modulesを一切含めず、production buildの静的ファイルのみを
# Nginxで配信する(package.jsonのengines.nodeは "^22.18.0 || >=24.12.0"、Node 22系を使用)。

# ---- Builder ----
FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Runtime ----
FROM nginx:alpine AS runtime

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
