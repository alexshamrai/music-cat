import { NavLink } from 'react-router';
import { LayoutDashboard, FolderOpen, Mic2, Disc3, Shuffle, Heart, Tag } from 'lucide-react';

const links = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/browse', icon: FolderOpen, label: 'Browse' },
  { to: '/artists', icon: Mic2, label: 'Artists' },
  { to: '/albums', icon: Disc3, label: 'Albums' },
  { to: '/random', icon: Shuffle, label: 'Random Pick' },
  { to: '/favorites', icon: Heart, label: 'Favorites' },
  { to: '/tags', icon: Tag, label: 'Tags' },
];

export default function Sidebar() {
  return (
    <aside className="w-60 shrink-0 bg-gray-900 text-gray-100 flex flex-col min-h-screen">
      <div className="px-6 py-5 border-b border-gray-700">
        <h1 className="text-lg font-bold tracking-tight text-white">Music Cat</h1>
        <p className="text-xs text-gray-400 mt-0.5">Personal Catalog</p>
      </div>
      <nav className="flex-1 py-4">
        {links.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
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
