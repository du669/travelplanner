import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 8000
})

const aiApi = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: Number(import.meta.env.VITE_AI_API_TIMEOUT || 60000)
})

const toSearchParams = (params = {}) => {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach((item) => searchParams.append(key, item))
    } else if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  return searchParams
}

export const getDestinations = () => api.get('/destinations').then((response) => response.data)

export const getAttractions = (params) =>
  api.get('/attractions', { params: toSearchParams(params) }).then((response) => response.data)

export const createPlan = (payload) => api.post('/plans', payload).then((response) => response.data)

export const createAiPlan = (payload) => aiApi.post('/ai/plans', payload).then((response) => response.data)

export const getSavedPlans = () => api.get('/plans').then((response) => response.data)

export const getSavedPlan = (id) => api.get(`/plans/${id}`).then((response) => response.data)
