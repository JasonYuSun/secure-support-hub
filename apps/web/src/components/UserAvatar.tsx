interface Props {
    username: string
    size?: number
}

function getInitials(name: string): string {
    return name
        .split(/[\s._-]/)
        .slice(0, 2)
        .map(part => part[0]?.toUpperCase() ?? '')
        .join('')
}

function hashColor(name: string): string {
    // Deterministic HSL color from username
    let hash = 0
    for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash)
    }
    const hue = Math.abs(hash) % 360
    return `hsl(${hue}, 60%, 45%)`
}

const UserAvatar: React.FC<Props> = ({ username, size = 32 }) => {
    const initials = getInitials(username)
    const bg = hashColor(username)

    return (
        <div
            title={username}
            style={{
                width: size,
                height: size,
                borderRadius: '50%',
                background: bg,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: size * 0.38,
                fontWeight: 700,
                color: '#fff',
                flexShrink: 0,
                userSelect: 'none',
            }}
        >
            {initials}
        </div>
    )
}

export default UserAvatar
