import client from './client'

export const getNotifications = () => client.get('/notifications')
export const markNotificationRead = (notificationId) => client.post(`/notifications/${notificationId}/read`)
export const replayResultReadyNotification = ({ year, month, week }) =>
  client.post(`/notifications/admin/replay-result-ready?year=${year}&month=${month}&week=${week}`)
