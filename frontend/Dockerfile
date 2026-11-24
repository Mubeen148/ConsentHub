# ==============================================================================
# ConsentHub Frontend — Multi-Stage Production Dockerfile
# Stage 1: Node 22 Alpine TypeScript + Vite SPA Build
# Stage 2: Hardened Non-Root Nginx 1.27 Alpine Reverse Proxy & Static Server
# ==============================================================================

FROM node:22-alpine AS build

WORKDIR /app

ARG VITE_API_BASE_URL=""
ARG VITE_DEMO_SDK_API_KEY="ch_live_demo_public_sdk_key_2026"
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_DEMO_SDK_API_KEY=${VITE_DEMO_SDK_API_KEY}

# Install dependencies cleanly using lockfile when present
COPY package*.json ./
RUN if [ -f package-lock.json ]; then \
        npm ci --no-audit --no-fund; \
    else \
        npm install --no-audit --no-fund; \
    fi

# Copy frontend source & public SDK assets (`consent.js`, `sdk-demo.html`) and build
COPY . .
RUN npm run build

# ------------------------------------------------------------------------------
# Runtime Stage — Non-root Nginx 1.27 serving SPA, `/consent.js`, & API proxy
# ------------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# Allow non-root `nginx` user to bind port 80 across all Linux kernels (SEC-G10)
RUN apk add --no-cache libcap \
    && setcap 'cap_net_bind_service=+ep' /usr/sbin/nginx \
    && sed -i '/^user[[:space:]]/d' /etc/nginx/nginx.conf \
    && sed -i 's|pid[[:space:]]*/var/run/nginx.pid;|pid /tmp/nginx.pid;|g; s|pid[[:space:]]*/run/nginx.pid;|pid /tmp/nginx.pid;|g' /etc/nginx/nginx.conf \
    && rm -f /docker-entrypoint.d/10-listen-on-ipv6-by-default.sh \
    && rm -rf /usr/share/nginx/html/* \
    && chown -R nginx:nginx /var/cache/nginx /var/log/nginx /etc/nginx/conf.d /usr/share/nginx/html

COPY --chown=nginx:nginx nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html

USER nginx

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=6 \
    CMD wget -qO- http://127.0.0.1:80/ >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]
