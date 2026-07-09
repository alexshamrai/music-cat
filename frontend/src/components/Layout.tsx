import { useEffect, useState } from 'react';
import { Outlet } from 'react-router';
import { Menu } from 'lucide-react';
import Sidebar from './Sidebar';

export default function Layout() {
  const [drawerOpen, setDrawerOpen] = useState(false);

  // While the mobile drawer is open, lock body scroll and close on Escape.
  useEffect(() => {
    if (!drawerOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setDrawerOpen(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [drawerOpen]);

  // Reaching the lg breakpoint (tablet rotated to landscape, desktop window widened)
  // reveals the static sidebar and hides the drawer via CSS — but the drawer state and
  // its body-scroll lock would otherwise persist with no visible control to release them.
  useEffect(() => {
    const mql = window.matchMedia('(min-width: 1024px)');
    const onChange = (e: MediaQueryListEvent) => {
      if (e.matches) setDrawerOpen(false);
    };
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);

  return (
    <div className="flex min-h-screen bg-gray-50">
      {/* Static sidebar — lg and up */}
      <div className="hidden lg:block">
        <Sidebar />
      </div>

      {/* Mobile drawer + scrim — below lg */}
      {drawerOpen && (
        <div className="lg:hidden">
          <div
            className="fixed inset-0 z-40 bg-black/40"
            onClick={() => setDrawerOpen(false)}
            aria-hidden="true"
          />
          <div className="fixed inset-y-0 left-0 z-50 w-60 shadow-xl">
            <Sidebar onNavigate={() => setDrawerOpen(false)} onClose={() => setDrawerOpen(false)} />
          </div>
        </div>
      )}

      {/* Content column */}
      <div className="flex flex-1 flex-col min-w-0">
        {/* Mobile top bar — below lg */}
        <header className="lg:hidden sticky top-0 z-30 flex items-center gap-3 bg-gray-900 px-4 py-3 text-white">
          <button
            type="button"
            onClick={() => setDrawerOpen(true)}
            aria-label="Open menu"
            className="-ml-1 p-1 text-gray-300 hover:text-white cursor-pointer"
          >
            <Menu size={22} />
          </button>
          <span className="font-bold tracking-tight">Music Cat</span>
        </header>

        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
