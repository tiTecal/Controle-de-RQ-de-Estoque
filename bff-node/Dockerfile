# ==============================================================================
# Multi-stage Dockerfile — BFF Node.js (Tecal Almoxarifado)
# ==============================================================================

FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json tsconfig.json ./
RUN npm ci
COPY src ./src
RUN npm run build
RUN npm prune --production

FROM node:22-alpine AS runtime
RUN apk add --no-cache tzdata
ENV TZ=America/Sao_Paulo
WORKDIR /app

COPY --from=build /app/package*.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist

EXPOSE 8080
ENV NODE_ENV=production
ENTRYPOINT ["node", "dist/server.js"]

