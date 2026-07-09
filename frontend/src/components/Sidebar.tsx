import { NavLink } from 'react-router';
import { LayoutDashboard, FolderOpen, Mic2, Disc3, Shuffle, Heart, Tag, X } from 'lucide-react';

const links = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/browse', icon: FolderOpen, label: 'Browse' },
  { to: '/artists', icon: Mic2, label: 'Artists' },
  { to: '/albums', icon: Disc3, label: 'Albums' },
  { to: '/random', icon: Shuffle, label: 'Random Pick' },
  { to: '/favorites', icon: Heart, label: 'Favorites' },
  { to: '/tags', icon: Tag, label: 'Tags' },
];

interface SidebarProps {
  // Called after a nav link is tapped — used to close the mobile drawer.
  onNavigate?: () => void;
  // When provided, renders a close (X) button in the header (mobile drawer only).
  onClose?: () => void;
}

export default function Sidebar({ onNavigate, onClose }: SidebarProps) {
  return (
    <aside className="w-60 shrink-0 bg-gray-900 text-gray-100 flex flex-col min-h-screen">
      <div className="px-6 py-5 border-b border-gray-700 flex items-start justify-between">
        <div>
          <h1 className="text-lg font-bold tracking-tight text-white">Music Cat</h1>
          <p className="text-xs text-gray-400 mt-0.5">Personal Catalog</p>
        </div>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            aria-label="Close menu"
            className="-mr-2 -mt-1 p-1 text-gray-400 hover:text-white cursor-pointer"
          >
            <X size={20} />
          </button>
        )}
      </div>
      <nav className="flex-1 py-4">
        {links.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 px-6 py-2.5 text-sm transition-colors ${
                isActive
                  ? 'bg-gray-700 text-white font-medium'
                  : 'text-gray-400 hover:text-white hover:bg-gray-800'
              }`
            }
          >
            <Icon size={16} />
            {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
