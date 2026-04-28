import client from './client'

export const getGenerationRuleConfig = () => client.get('/config/generation-rules')
export const saveGenerationRuleConfig = (rules) => client.put('/config/generation-rules', { rules })
