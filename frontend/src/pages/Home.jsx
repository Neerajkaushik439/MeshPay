import React from 'react';
import { Link } from 'react-router-dom';

export default function Home() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-between">
      {/* Hero Section */}
      <div className="relative overflow-hidden pt-20 pb-16 sm:pb-24">
        {/* Glow Effects */}
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-sky-500/10 rounded-full blur-3xl pointer-events-none"></div>
        <div className="absolute top-1/3 left-1/4 w-72 h-72 bg-emerald-500/5 rounded-full blur-3xl pointer-events-none"></div>

        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center relative z-10">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-sky-500/10 text-sky-400 border border-sky-500/20 mb-6">
            <span className="w-1.5 h-1.5 rounded-full bg-sky-400 animate-pulse"></span>
            Phase 1: Project Setup & Architecture
          </span>

          <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tight text-white mb-6 leading-none">
            Distributed Offline Payments <br />
            <span className="bg-gradient-to-r from-sky-400 via-sky-200 to-emerald-400 bg-clip-text text-transparent">
              Via Simulated Mesh Routing
            </span>
          </h1>

          <p className="max-w-2xl mx-auto text-base sm:text-lg text-slate-400 mb-8">
            MeshPay simulates secure offline transaction routing through local peer-to-peer microservices, eventually resolving payments at the gateway when internet connectivity is reached.
          </p>

          <div className="flex flex-col sm:flex-row justify-center items-center gap-4">
            <Link
              to="/dashboard"
              className="w-full sm:w-auto px-8 py-3.5 rounded-xl bg-gradient-to-r from-sky-500 to-emerald-500 text-slate-950 font-bold hover:opacity-90 transition-opacity shadow-lg shadow-sky-500/25 flex items-center justify-center gap-2"
            >
              Go to Simulation Dashboard
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
              </svg>
            </Link>
            <Link
              to="/login"
              className="w-full sm:w-auto px-8 py-3.5 rounded-xl bg-slate-900 border border-slate-800 font-semibold hover:bg-slate-850 hover:text-white transition-colors"
            >
              Log In
            </Link>
          </div>
        </div>
      </div>

      {/* Architecture Simulation Blocks */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 border-t border-slate-900">
        <h2 className="text-2xl sm:text-3xl font-bold text-center text-white mb-10">
          The Simulation Network Flow
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-5 gap-4 relative">
          
          {/* User Card */}
          <div className="bg-slate-900/50 backdrop-blur border border-slate-850 p-6 rounded-2xl flex flex-col justify-between">
            <div>
              <div className="text-sky-400 font-bold text-xs uppercase mb-2">Source</div>
              <h3 className="text-lg font-semibold text-white mb-2">User Service</h3>
              <p className="text-xs text-slate-400">Initiates local transaction signatures offline.</p>
            </div>
            <div className="mt-4 pt-4 border-t border-slate-800 flex justify-between items-center text-xs">
              <span className="text-slate-500">Port 8081</span>
              <span className="text-emerald-500">Active</span>
            </div>
          </div>

          {/* Relay 1 */}
          <div className="bg-slate-900/50 backdrop-blur border border-slate-850 p-6 rounded-2xl flex flex-col justify-between">
            <div>
              <div className="text-amber-400 font-bold text-xs uppercase mb-2">Relay Node A</div>
              <h3 className="text-lg font-semibold text-white mb-2">Relay 1</h3>
              <p className="text-xs text-slate-400">Receives and stores offline payments via REST.</p>
            </div>
            <div className="mt-4 pt-4 border-t border-slate-800 flex justify-between items-center text-xs">
              <span className="text-slate-500">Port 8082</span>
              <span className="text-emerald-500">Active</span>
            </div>
          </div>

          {/* Relay 2 */}
          <div className="bg-slate-900/50 backdrop-blur border border-slate-850 p-6 rounded-2xl flex flex-col justify-between">
            <div>
              <div className="text-amber-400 font-bold text-xs uppercase mb-2">Relay Node B</div>
              <h3 className="text-lg font-semibold text-white mb-2">Relay 2</h3>
              <p className="text-xs text-slate-400">Propagates network signals forward when peer range matches.</p>
            </div>
            <div className="mt-4 pt-4 border-t border-slate-800 flex justify-between items-center text-xs">
              <span className="text-slate-500">Port 8083</span>
              <span className="text-emerald-500">Active</span>
            </div>
          </div>

          {/* Gateway */}
          <div className="bg-slate-900/50 backdrop-blur border border-slate-850 p-6 rounded-2xl flex flex-col justify-between">
            <div>
              <div className="text-indigo-400 font-bold text-xs uppercase mb-2">Internet Bridge</div>
              <h3 className="text-lg font-semibold text-white mb-2">Gateway</h3>
              <p className="text-xs text-slate-400">Submits buffered mesh payloads to the public internet.</p>
            </div>
            <div className="mt-4 pt-4 border-t border-slate-800 flex justify-between items-center text-xs">
              <span className="text-slate-500">Port 8084</span>
              <span className="text-emerald-500">Active</span>
            </div>
          </div>

          {/* Bank */}
          <div className="bg-slate-900/50 backdrop-blur border border-slate-850 p-6 rounded-2xl flex flex-col justify-between">
            <div>
              <div className="text-emerald-400 font-bold text-xs uppercase mb-2">Settlement</div>
              <h3 className="text-lg font-semibold text-white mb-2">Bank Service</h3>
              <p className="text-xs text-slate-400">Verifies transaction cryptology and updates balances.</p>
            </div>
            <div className="mt-4 pt-4 border-t border-slate-800 flex justify-between items-center text-xs">
              <span className="text-slate-500">Port 8085</span>
              <span className="text-emerald-500">Active</span>
            </div>
          </div>

        </div>
      </div>

      {/* Footer */}
      <footer className="bg-slate-950 border-t border-slate-900 py-8 text-center text-xs text-slate-500">
        <p>&copy; {new Date().getFullYear()} MeshPay - Distributed Offline Payment Network Simulation. All Rights Reserved.</p>
      </footer>
    </div>
  );
}
