export const environment = {
  production: true,
  apiBaseUrl: '/api',   // nginx proxia /api/ → backend:8080
  wsUrl: '/ws',         // nginx proxia /ws   → backend:8080/ws (SockJS resuelve el host)
};
