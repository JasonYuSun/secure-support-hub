import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import Navbar from './components/Navbar'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import CreateRequestPage from './pages/CreateRequestPage'
import RequestDetailPage from './pages/RequestDetailPage'
import TriagePage from './pages/TriagePage'
import AdminUsersPage from './pages/AdminUsersPage'

const App: React.FC = () => (
    <AuthProvider>
        <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route path="/" element={<ProtectedRoute><><Navbar /><DashboardPage /></></ProtectedRoute>} />
            <Route path="/requests/new" element={<ProtectedRoute><><Navbar /><CreateRequestPage /></></ProtectedRoute>} />
            <Route path="/requests/:id" element={<ProtectedRoute><><Navbar /><RequestDetailPage /></></ProtectedRoute>} />
            <Route path="/triage" element={
                <ProtectedRoute roles={['TRIAGE', 'ADMIN']}>
                    <><Navbar /><TriagePage /></>
                </ProtectedRoute>
            } />
            <Route path="/admin/users" element={
                <ProtectedRoute roles={['ADMIN']}>
                    <><Navbar /><AdminUsersPage /></>
                </ProtectedRoute>
            } />

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    </AuthProvider>
)

export default App
