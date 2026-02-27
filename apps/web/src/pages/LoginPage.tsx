import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Shield, Lock, User } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { login } from '../api/auth'

const LoginPage: React.FC = () => {
    const { login: authLogin } = useAuth()
    const navigate = useNavigate()
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault()
        setError('')
        setLoading(true)
        try {
            const res = await login({ username, password })
            authLogin(res.accessToken, res.user)
            navigate('/')
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })
                ?.response?.data?.message || 'Invalid username or password'
            setError(msg)
        } finally {
            setLoading(false)
        }
    }

    return (
        <div style={{
            minHeight: '100vh',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: 'radial-gradient(ellipse at 30% 40%, rgba(79,114,255,.12) 0%, transparent 60%), var(--color-bg)',
            padding: '20px',
        }}>
            <div style={{ width: '100%', maxWidth: 420 }}>
                {/* Logo */}
                <div style={{ textAlign: 'center', marginBottom: 32 }}>
                    <div style={{
                        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                        width: 56, height: 56, borderRadius: 16,
                        background: 'linear-gradient(135deg, var(--color-primary), var(--color-accent))',
                        marginBottom: 16, boxShadow: 'var(--shadow-glow)',
                    }}>
                        <Shield size={28} color="#fff" />
                    </div>
                    <h1 style={{ fontSize: '1.75rem', marginBottom: 6 }}>Welcome back</h1>
                    <p className="text-muted text-sm">Sign in to Secure Support Hub</p>
                </div>

                <div className="card" style={{ padding: 32 }}>
                    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>

                        {error && <div className="alert alert-error" role="alert">{error}</div>}

                        <div className="form-group">
                            <label className="form-label" htmlFor="username">
                                <User size={13} style={{ display: 'inline', marginRight: 5 }} />
                                Username
                            </label>
                            <input
                                id="username"
                                type="text"
                                className="form-input"
                                placeholder="e.g. user"
                                value={username}
                                onChange={e => setUsername(e.target.value)}
                                required
                                autoComplete="username"
                                autoFocus
                            />
                        </div>

                        <div className="form-group">
                            <label className="form-label" htmlFor="password">
                                <Lock size={13} style={{ display: 'inline', marginRight: 5 }} />
                                Password
                            </label>
                            <input
                                id="password"
                                type="password"
                                className="form-input"
                                placeholder="••••••••"
                                value={password}
                                onChange={e => setPassword(e.target.value)}
                                required
                                autoComplete="current-password"
                            />
                        </div>

                        <button
                            type="submit"
                            className="btn btn-primary w-full"
                            style={{ justifyContent: 'center', height: 44, marginTop: 4 }}
                            disabled={loading}
                        >
                            {loading ? <><span className="spinner" style={{ width: 16, height: 16, borderWidth: 2 }} /> Signing in…</> : 'Sign in'}
                        </button>
                    </form>
                </div>

                <p className="text-xs text-muted" style={{ textAlign: 'center', marginTop: 20 }}>
                    Demo accounts&nbsp;&nbsp;|&nbsp;&nbsp;
                    <strong>user</strong> · <strong>triage</strong> · <strong>admin</strong>
                    &nbsp;— all use password&nbsp;<strong>password</strong>
                </p>
            </div>
        </div>
    )
}

export default LoginPage
