import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

interface Props {
    children: React.ReactNode
    roles?: string[]
}

const ProtectedRoute: React.FC<Props> = ({ children, roles }) => {
    const { isAuthenticated, hasRole } = useAuth()

    if (!isAuthenticated) return <Navigate to="/login" replace />

    if (roles && !roles.some(role => hasRole(role))) {
        return <Navigate to="/" replace />
    }

    return <>{children}</>
}

export default ProtectedRoute
