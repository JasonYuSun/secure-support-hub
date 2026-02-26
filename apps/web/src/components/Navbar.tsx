import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Shield, LogOut, LayoutDashboard, PlusCircle, ListFilter } from 'lucide-react'

const Navbar: React.FC = () => {
    const { user, logout, hasRole } = useAuth()
    const navigate = useNavigate()

    const handleLogout = () => {
        logout()
        navigate('/login')
    }

    return (
        <nav className="navbar">
            <div className="navbar-inner">
                <div className="flex items-center gap-2">
                    <Shield size={20} color="var(--color-primary)" />
                    <span className="navbar-brand">Secure Support Hub</span>
                </div>

                <div className="navbar-nav">
                    <NavLink to="/" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
                        <LayoutDashboard size={15} style={{ display: 'inline', marginRight: 4 }} />
                        Dashboard
                    </NavLink>
                    <NavLink to="/requests/new" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
                        <PlusCircle size={15} style={{ display: 'inline', marginRight: 4 }} />
                        New Request
                    </NavLink>
                    {(hasRole('TRIAGE') || hasRole('ADMIN')) && (
                        <NavLink to="/triage" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
                            <ListFilter size={15} style={{ display: 'inline', marginRight: 4 }} />
                            Triage
                        </NavLink>
                    )}
                </div>

                <div className="navbar-user">
                    {user && (
                        <div className="flex items-center gap-2">
                            <span className="text-sm text-muted">{user.username}</span>
                            <div className="flex gap-2">
                                {user.roles.map(r => (
                                    <span key={r} className={`role-badge role-${r}`}>{r}</span>
                                ))}
                            </div>
                        </div>
                    )}
                    <button className="btn btn-secondary" style={{ padding: '6px 12px', fontSize: 13 }} onClick={handleLogout}>
                        <LogOut size={14} />
                        Logout
                    </button>
                </div>
            </div>
        </nav>
    )
}

export default Navbar
