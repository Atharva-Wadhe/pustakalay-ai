import { LayoutDashboard, BookOpen, MessageSquare, Settings } from 'lucide-react';

interface SidebarProps {
    activeTab: string;
    setActiveTab: (tab: string) => void;
}

export const Sidebar = ({ activeTab, setActiveTab }: SidebarProps) => {
    const menuItems = [
        { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
        { id: 'library', label: 'Library', icon: BookOpen },
        { id: 'chat', label: 'Chat Assistant', icon: MessageSquare },
        { id: 'settings', label: 'Settings', icon: Settings },
    ];

    return (
        <div className="sidebar">
            <div className="logo-container">
                <div className="logo-icon">P</div>
                <div className="logo-text">Pustakalay.ai</div>
            </div>

            <nav className="nav-menu">
                {menuItems.map((item) => {
                    const Icon = item.icon;
                    return (
                        <div
                            key={item.id}
                            className={`nav-item ${activeTab === item.id ? 'active' : ''}`}
                            onClick={() => setActiveTab(item.id)}
                        >
                            <Icon className="nav-item-icon" />
                            <span>{item.label}</span>
                        </div>
                    );
                })}
            </nav>

            <div style={{ marginTop: 'auto', padding: '8px', fontSize: '0.75rem', color: 'var(--text-muted)', textAlign: 'center' }}>
                v1.0.0-beta • Production Ready
            </div>
        </div>
    );
};
