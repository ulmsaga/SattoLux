import client from './client'

export const getGenerationRules = () => client.get('/make-week-num/rules')
export const getCurrentWeekNumbers = () => client.get('/make-week-num/current-week')
export const getCurrentWeekStatus = () => client.get('/make-week-num/status')
export const generateCurrentWeekNumbers = (force = false) =>
  client.post(`/make-week-num/generate${force ? '?force=true' : ''}`)
export const generateManualCurrentWeekNumbers = () => client.post('/make-week-num/manual-generate')
