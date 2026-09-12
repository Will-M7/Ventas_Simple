FROM node:22-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY tsconfig.json schema.sql ./
COPY src ./src
RUN npm run build
RUN mkdir -p uploads
EXPOSE 3000
CMD ["npm", "start"]
