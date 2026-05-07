# ─────────────────────────────────────────────
#  STAGE: development
#  Node + Angular CLI.
# ─────────────────────────────────────────────
#  Versión normal para desarrollo (para no limitarnos en herramientas)
FROM node:24 AS development

WORKDIR /app

RUN npm install -g @angular/cli

EXPOSE 4200

CMD sh -c "[ -f package.json ] && npm install && ng serve --host 0.0.0.0 --poll 500 || echo 'No hay proyecto Angular todavía. Entra al contenedor y ejecuta: ng new <nombre-proyecto> --directory .'"


# ─────────────────────────────────────────────
#  STAGE: builder  (solo para producción)
# ─────────────────────────────────────────────
# Versión alpine para producción
FROM node:24-alpine AS builder 


WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production


# ─────────────────────────────────────────────
#  STAGE: production
#  Nginx sirve el bundle estático.
# ─────────────────────────────────────────────
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
