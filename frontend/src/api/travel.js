import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 8000
})

export const getDestinations = () => api.get('/destinations').then((response) => response.data)

export const getAttractions = (params) =>
  api.get('/attractions', { params }).then((response) => response.data)

export const createPlan = (payload) => api.post('/plans', payload).then((response) => response.data)
