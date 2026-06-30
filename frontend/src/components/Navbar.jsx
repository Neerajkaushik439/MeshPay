import React, { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const [isOpen, setIsOpen] = useState(false);
  const { user, logout } = useAuth();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    setIsOpen(false);
  };

  return (
    <nav className="bg-slate-950/80 backdrop-blur-md border-b border-slate-900 sticky top-0 z-50 transition-all duration-300">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <div className="flex items-center">
            <Link to="/" className="flex items-center gap-2 group">
              <span className="h-10 w-10 rounded-xl bg-gradient-to-tr from-sky-500 to-emerald-400 flex items-center justify-center text-slate-950 font-bold text-xl shadow-lg shadow-sky-500/20 group-hover:scale-105 transition-transform duration-300">
                M
              </span>
              <span className="font-bold text-xl tracking-tight bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent group-hover:text-white transition-colors duration-300">
                MeshPay
              </span>
            </Link>
          </div>
          
          {/* Desktop Links */}
          <div className="hidden md:flex items-center gap-6">
            <Link
              to="/"
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
                location.pathname === '/'
                  ? 'bg-slate-900/60 text-sky-400 shadow-inner'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/40'
              }`}
            >
              Home
            </Link>

            {user ? (
              <>
                <Link
                  to="/dashboard"
                  className={`px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
                    location.pathname === '/dashboard'
                      ? 'bg-slate-900/60 text-sky-400 shadow-inner'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/40'
                  }`}
                >
                  Dashboard
                </Link>
                <button
                  onClick={handleLogout}
                  className="px-3 py-2 rounded-lg text-sm font-medium text-red-400 hover:text-red-300 hover:bg-red-500/10 transition-all duration-200"
                >
                  Logout
                </button>
              </>
            ) : (
              <>
                <Link
                  to="/login"
                  className={`px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
                    location.pathname === '/login'
                      ? 'bg-slate-900/60 text-sky-400 shadow-inner'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/40'
                  }`}
                >
                  Login
                </Link>
                <Link
                  to="/register"
                  className={`px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
                    location.pathname === '/register'
                      ? 'bg-slate-900/60 text-sky-400 shadow-inner'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/40'
                  }`}
                >
                  Register
                </Link>
              </>
            )}
          </div>

          {/* Mobile menu button */}
          <div className="md:hidden">
            <button
              onClick={() => setIsOpen(!isOpen)}
              type="button"
              className="inline-flex items-center justify-center p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 focus:outline-none"
            >
              <span className="sr-only">Open main menu</span>
              {!isOpen ? (
                <svg className="block h-6 w-6" fill="none" viewBox="0 0 24 24" strokeWidth="1.5" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
                </svg>
              ) : (
                <svg className="block h-6 w-6" fill="none" viewBox="0 0 24 24" strokeWidth="1.5" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Menu */}
      {isOpen && (
        <div className="md:hidden bg-slate-955 border-b border-slate-900 px-2 pt-2 pb-3 space-y-1 sm:px-3">
          <Link
            to="/"
            onClick={() => setIsOpen(false)}
            className={`block px-3 py-2 rounded-lg text-base font-medium ${
              location.pathname === '/' ? 'bg-slate-900 text-sky-400' : 'text-slate-400 hover:bg-slate-900/50 hover:text-white'
            }`}
          >
            Home
          </Link>

          {user ? (
            <>
              <Link
                to="/dashboard"
                onClick={() => setIsOpen(false)}
                className={`block px-3 py-2 rounded-lg text-base font-medium ${
                  location.pathname === '/dashboard' ? 'bg-slate-900 text-sky-400' : 'text-slate-400 hover:bg-slate-900/50 hover:text-white'
                }`}
              >
                Dashboard
              </Link>
              <button
                onClick={handleLogout}
                className="w-full text-left block px-3 py-2 rounded-lg text-base font-medium text-red-400 hover:bg-red-500/10"
              >
                Logout
              </button>
            </>
          ) : (
            <>
              <Link
                to="/login"
                onClick={() => setIsOpen(false)}
                className={`block px-3 py-2 rounded-lg text-base font-medium ${
                  location.pathname === '/login' ? 'bg-slate-900 text-sky-400' : 'text-slate-400 hover:bg-slate-900/50 hover:text-white'
                }`}
              >
                Login
              </Link>
              <Link
                to="/register"
                onClick={() => setIsOpen(false)}
                className={`block px-3 py-2 rounded-lg text-base font-medium ${
                  location.pathname === '/register' ? 'bg-slate-900 text-sky-400' : 'text-slate-400 hover:bg-slate-900/50 hover:text-white'
                }`}
              >
                Register
              </Link>
            </>
          )}
        </div>
      )}
    </nav>
  );
}
