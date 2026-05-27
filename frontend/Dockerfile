# -----------------------------------------------------------------------------
# STAGE: development
# Node + Angular CLI for local development.
# -----------------------------------------------------------------------------
# Use Node LTS in dev to avoid npm edge-version crashes on mounted volumes.
FROM node:22 AS development

WORKDIR /app

EXPOSE 4200

CMD sh -c "\
  if [ ! -f package.json ]; then \
    echo 'No se encontro package.json en /app. Revisa el volumen ./frontend:/app.'; \
    exit 1; \
  fi; \
  if [ ! -x node_modules/.bin/ng ]; then \
    echo 'Instalando dependencias del frontend...'; \
    if [ -f package-lock.json ]; then npm ci; else npm install; fi; \
  fi; \
  npm start -- --host 0.0.0.0 --poll 1000"


# -----------------------------------------------------------------------------
# STAGE: builder
# Production bundle.
# -----------------------------------------------------------------------------
FROM node:22-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production


# -----------------------------------------------------------------------------
# STAGE: production
# Nginx serves the static bundle.
# -----------------------------------------------------------------------------
FROM nginx:alpine AS production

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist/*/browser /usr/share/nginx/html

RUN chown -R nginx:nginx /usr/share/nginx/html \
    && chown -R nginx:nginx /var/cache/nginx \
    && chown -R nginx:nginx /var/log/nginx \
    && chown -R nginx:nginx /etc/nginx/conf.d \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid

USER nginx
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
