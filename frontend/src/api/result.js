import client from './client'

export const getWeekResult = ({ year, month, week } = {}) => {
  const params = new URLSearchParams()
  if (year != null) params.set('year', year)
  if (month != null) params.set('month', month)
  if (week != null) params.set('week', week)

  const query = params.toString()
  return client.get(`/result/week${query ? `?${query}` : ''}`)
}

export const prepareLatestResultManualTest = () => client.post('/result/admin/manual-test-prepare')
export const runLatestResultManualTest = () => client.post('/result/admin/manual-test-latest')
