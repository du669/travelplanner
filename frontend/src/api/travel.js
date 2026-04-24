import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: Number(import.meta.env.VITE_API_TIMEOUT || 60000)
})

const aiApi = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: Number(import.meta.env.VITE_AI_API_TIMEOUT || 180000)
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
export const applyPlanEdits = (payload) => api.post('/plans/edit', payload).then((response) => response.data)
export const previewPlanRoutes = (payload) => api.post('/plans/routes-preview', payload).then((response) => response.data)

export const createAiPlan = (payload) => aiApi.post('/ai/plans', payload).then((response) => response.data)
export const optimizePlan = (payload) => aiApi.post('/ai/plans/optimize', payload).then((response) => response.data)

const streamPlanEvents = async (endpoint, payload, handlers = {}, options = {}) => {
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || '/api'}${endpoint}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload),
    signal: options.signal
  })

  if (!response.ok || !response.body) {
    throw new Error(`Streaming request failed: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let currentEvent = 'message'

  const emit = (eventName, data) => {
    if (eventName === 'preview') handlers.onPreview?.(data)
    else if (eventName === 'status') handlers.onStatus?.(data)
    else if (eventName === 'complete') handlers.onComplete?.(data)
    else if (eventName === 'error') handlers.onError?.(data)
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    let boundaryIndex
    while ((boundaryIndex = buffer.indexOf('\n\n')) >= 0) {
      const chunk = buffer.slice(0, boundaryIndex)
      buffer = buffer.slice(boundaryIndex + 2)

      const lines = chunk.split('\n')
      let dataLine = ''
      currentEvent = 'message'

      lines.forEach((line) => {
        if (line.startsWith('event:')) currentEvent = line.slice(6).trim()
        if (line.startsWith('data:')) dataLine += line.slice(5).trim()
      })

      if (!dataLine) continue

      try {
        emit(currentEvent, JSON.parse(dataLine))
      } catch (error) {
        console.error('Failed to parse stream chunk', error)
      }
    }
  }
}

export const streamAiPlan = async (payload, handlers = {}, options = {}) =>
  streamPlanEvents('/ai/plans/stream', payload, handlers, options)

export const streamStandardPlan = async (payload, handlers = {}, options = {}) =>
  streamPlanEvents('/plans/stream', payload, handlers, options)

export const getSavedPlans = () => api.get('/plans').then((response) => response.data)

export const getSavedPlan = (id) => api.get(`/plans/${id}`).then((response) => response.data)

export const getPlanRoutes = (id) => api.get(`/plans/${id}/routes`).then((response) => response.data)

export const getMapConfig = () => api.get('/map/config').then((response) => response.data)
