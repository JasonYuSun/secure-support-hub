import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_URL || '/api/v1'

const apiClient = axios.create({
    baseURL: API_BASE,
    headers: { 'Content-Type': 'application/json' },
})

// Attach JWT token on every request
apiClient.interceptors.request.use((config) => {
    const token = localStorage.getItem('token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// Redirect to login on 401
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401 && window.location.pathname !== '/login') {
            localStorage.removeItem('token')
            localStorage.removeItem('user')
            window.location.href = '/login'
        }
        return Promise.reject(error)
    }
)

export default apiClient
