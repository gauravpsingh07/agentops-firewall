# ---------------------------------------------------------------------------
# AgentOps Firewall — frontend Dockerfile.
# Multi-stage: Node build + Nginx static-serve.
# ---------------------------------------------------------------------------

# ---- build stage ----------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /workspace

COPY package*.json ./
RUN npm ci || npm install

COPY . .
RUN npm run build

# ---- runtime stage --------------------------------------------------------
FROM nginx:1.27-alpine
COPY --from=build /workspace/dist/agentops-firewall-frontend/browser /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
