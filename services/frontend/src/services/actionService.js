import * as apiService from './apiService'
import { formatResponse } from '../utils/formatters'
import { rolesFromToken } from './authService'

export async function performAction(action, accessToken) {
  const method = action.method || 'GET'
  let apiPath = action.apiPath

  if (action.requiresId && apiPath.includes('{id}')) {
    const listPath = action.getIdFrom || apiPath.replace('/{id}', '').replace('{id}', '').replace(/\/$/, '')
    const listData = await apiService.apiGet(listPath, accessToken)

    if (!Array.isArray(listData) || listData.length === 0) {
      throw new Error(`Nu exista elemente disponibile pentru ${action.label}. Creeaza mai intai un element.`)
    }

    const firstId = listData[0].id
    apiPath = apiPath.replace('{id}', firstId)
  }

  if (action.requiresClassId && apiPath.includes('/timetables/me')) {
    const roles = rolesFromToken(accessToken)
    if (!roles.includes('student')) {
      const classes = await apiService.apiGet('/classes', accessToken)
      if (Array.isArray(classes) && classes.length > 0) {
        const classId = classes[0].id
        apiPath = `${apiPath}?class_id=${classId}`
      } else {
        throw new Error('Nu exista clase disponibile. Creeaza mai intai o clasa.')
      }
    }
  }

  let body = null
  if (action.body) {
    if (typeof action.body === 'function') {
      const boundApiGet = (path) => apiService.apiGet(path, accessToken)
      body = await action.body(boundApiGet)
    } else {
      body = action.body
    }
  }

  let data

  if (method === 'GET') {
    data = await apiService.apiGet(apiPath, accessToken)
  } else if (method === 'POST') {
    data = await apiService.apiPost(apiPath, body || {}, accessToken)
  } else if (method === 'PUT') {
    data = await apiService.apiPut(apiPath, body || {}, accessToken)
  } else if (method === 'PATCH') {
    data = await apiService.apiPatch(apiPath, body || {}, accessToken)
  } else if (method === 'DELETE') {
    data = await apiService.apiDelete(apiPath, accessToken)
  } else {
    data = await apiService.apiGet(apiPath, accessToken)
  }

  return formatResponse(action.id, data)
}
