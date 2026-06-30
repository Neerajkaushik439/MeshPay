import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import TransactionTracker from '../components/TransactionTracker';

export default function Dashboard() {
  const { user, logout } = useAuth();
  
  // Local wallet state
  const [balance, setBalance] = useState(12500.00);
  const [payee, setPayee] = useState('');
  const [amount, setAmount] = useState('');
  const [routingPath, setRoutingPath] = useState('User -> Relay 1 -> Relay 2 -> Gateway -> Bank');
  const [activeTxnId, setActiveTxnId] = useState(null);

  // Interactive node statuses
  const [nodes, setNodes] = useState([
    { id: 'user-service', name: 'User Service', port: 8081, url: 'http://localhost:8081/health', status: 'Checking...', response: '' },
    { id: 'relay-service-1', name: 'Relay Service 1', port: 8082, url: 'http://localhost:8082/health', status: 'Checking...', response: '' },
    { id: 'relay-service-2', name: 'Relay Service 2', port: 8083, url: 'http://localhost:8083/health', status: 'Checking...', response: '' },
    { id: 'gateway-service', name: 'Gateway Service', port: 8084, url: 'http://localhost:8084/health', status: 'Checking...', response: '' },
    { id: 'bank-service', name: 'Bank Service', port: 8085, url: 'http://localhost:8085/health', status: 'Checking...', response: '' }
  ]);

  // Transaction history
  const [transactions, setTransactions] = useState([
    { id: 'TXN-98402', payee: 'alex@upi', amount: 350.00, time: '2 mins ago', status: 'Settled', path: 'User -> Relay 1 -> Gateway -> Bank' },
    { id: 'TXN-98319', payee: 'store@merchant', amount: 1200.00, time: '1 hour ago', status: 'Settled', path: 'User -> Relay 2 -> Gateway -> Bank' },
    { id: 'TXN-97992', payee: 'sam@upi', amount: 50.00, time: '1 day ago', status: 'Settled', path: 'User -> Relay 1 -> Relay 2 -> Gateway -> Bank' }
  ]);

  // Simulation status messages
  const [simulationSteps, setSimulationSteps] = useState([]);
  const [isSimulating, setIsSimulating] = useState(false);

  // Ping services
  const pingAllNodes = async () => {
    const updatedNodes = await Promise.all(
      nodes.map(async (node) => {
        try {
          const res = await axios.get(node.url, { timeout: 2000 });
          return {
            ...node,
            status: 'ONLINE',
            response: res.data
          };
        } catch (err) {
          return {
            ...node,
            status: 'OFFLINE',
            response: 'Connection Refused'
          };
        }
      })
    );
    setNodes(updatedNodes);
  };

  useEffect(() => {
    pingAllNodes();
    const interval = setInterval(pingAllNodes, 8000);
    return () => clearInterval(interval);
  }, []);

  const handleSimulatePayment = async (e) => {
    e.preventDefault();
    if (!payee || !amount || parseFloat(amount) <= 0) return;

    setIsSimulating(true);
    setSimulationSteps(['Initiating payment request...', 'Connecting to Transaction-Service...']);

    try {
      const token = localStorage.getItem('token');
      const response = await axios.post('http://localhost:8086/api/transactions', {
        receiverUpiId: payee,
        amount: parseFloat(amount)
      }, {
        headers: { Authorization: `Bearer ${token}` }
      });

      const { transactionId, status } = response.data;
      console.log('Transaction created successfully:', response.data);

      setActiveTxnId(transactionId);
      setIsSimulating(false);
      setSimulationSteps(prev => [
        ...prev,
        `Transaction created: ${transactionId}`,
        'WebSocket connection established. Tracking live...'
      ]);

      // Deduct balance locally
      setBalance(prev => prev - parseFloat(amount));

      // Append to local transaction log list
      setTransactions(prev => [
        {
          id: transactionId,
          payee,
          amount: parseFloat(amount),
          time: 'Just now',
          status: 'Routing',
          path: routingPath
        },
        ...prev
      ]);

      setPayee('');
      setAmount('');
    } catch (err) {
      console.error('Failed to create transaction:', err);
      setIsSimulating(false);
      const errMsg = err.response?.data?.message || err.message || 'Connection Failure';
      setSimulationSteps(prev => [...prev, `Error: ${errMsg}`]);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-4 sm:p-8">
      <div className="max-w-7xl mx-auto space-y-6">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-slate-900 pb-6">
          <div>
            <h1 className="text-3xl font-extrabold text-white">Welcome, {user?.fullName || 'User'}</h1>
            <p className="text-sm text-slate-400 mt-1">Terminal Node: <span className="font-mono text-sky-400">{user?.email}</span></p>
          </div>
          <div className="flex gap-3">
            <button 
              onClick={pingAllNodes}
              className="px-4 py-2 bg-slate-900 border border-slate-800 rounded-xl hover:bg-slate-850 hover:text-white transition-colors text-xs flex items-center gap-2"
            >
              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99" />
              </svg>
              Refresh Connections
            </button>
            <button 
              onClick={logout}
              className="px-4 py-2 bg-red-950/20 border border-red-900/30 text-red-400 rounded-xl hover:bg-red-900/20 transition-colors text-xs flex items-center gap-2"
            >
              Logout Terminal
            </button>
          </div>
        </div>

        {/* WebSocket Live Tracker mount */}
        {activeTxnId && (
          <div className="mt-4">
            <TransactionTracker 
              transactionId={activeTxnId} 
              onClose={() => setActiveTxnId(null)} 
            />
          </div>
        )}

        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-slate-900/40 backdrop-blur border border-slate-850 p-6 rounded-3xl flex flex-col justify-between shadow-lg">
            <span className="text-slate-400 text-xs uppercase tracking-wider font-semibold">Simulated Balance</span>
            <div className="mt-4">
              <span className="text-3xl font-extrabold text-white">₹{balance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
              <p className="text-slate-500 text-xs mt-1">Stored securely inside local database credentials</p>
            </div>
          </div>
          
          <div className="bg-slate-900/40 backdrop-blur border border-slate-850 p-6 rounded-3xl flex flex-col justify-between shadow-lg md:col-span-2">
            <span className="text-slate-400 text-xs uppercase tracking-wider font-semibold">Connection Status Monitor</span>
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 mt-4">
              {nodes.map(node => (
                <div key={node.id} className="bg-slate-950/80 p-3 rounded-2xl border border-slate-900 flex flex-col justify-between">
                  <div className="text-[10px] text-slate-500 tracking-wider font-mono">{node.name}</div>
                  <div className="mt-2 flex items-center justify-between">
                    <span className="text-xs font-semibold font-mono text-slate-400">:{node.port}</span>
                    <span className={`px-2 py-0.5 rounded-full text-[9px] font-bold ${
                      node.status === 'ONLINE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'
                    }`}>
                      {node.status}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Simulator Panel and Log Console */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          
          {/* Simulator Form */}
          <div className="bg-slate-900/40 backdrop-blur border border-slate-850 p-6 rounded-3xl shadow-lg lg:col-span-1">
            <h3 className="text-lg font-bold text-white mb-4">Payment Simulator</h3>
            <form onSubmit={handleSimulatePayment} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Payee UPI ID</label>
                <input
                  type="text"
                  required
                  value={payee}
                  onChange={(e) => setPayee(e.target.value)}
                  placeholder="payee@upi"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm text-slate-200 focus:outline-none focus:border-sky-500 focus:ring-1 focus:ring-sky-500 transition-all"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Amount (₹)</label>
                <input
                  type="number"
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="100.00"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm text-slate-200 focus:outline-none focus:border-sky-500 focus:ring-1 focus:ring-sky-500 transition-all"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Relay Routing Sequence</label>
                <select 
                  value={routingPath}
                  onChange={(e) => setRoutingPath(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm text-slate-200 focus:outline-none focus:border-sky-500 focus:ring-1 focus:ring-sky-500 transition-all"
                >
                  <option value="User -> Relay 1 -> Relay 2 -> Gateway -> Bank">User ➔ Relay 1 ➔ Relay 2 ➔ Gateway ➔ Bank</option>
                  <option value="User -> Relay 1 -> Gateway -> Bank">User ➔ Relay 1 ➔ Gateway ➔ Bank</option>
                  <option value="User -> Relay 2 -> Gateway -> Bank">User ➔ Relay 2 ➔ Gateway ➔ Bank</option>
                </select>
              </div>

              <button
                type="submit"
                disabled={isSimulating}
                className="w-full py-3.5 rounded-xl bg-gradient-to-r from-sky-500 to-emerald-500 text-slate-950 font-bold hover:opacity-90 transition-opacity disabled:opacity-50 shadow-lg shadow-sky-500/10"
              >
                {isSimulating ? 'Simulating Broadcast...' : 'Broadcast Offline Transaction'}
              </button>
            </form>
          </div>

          {/* Log Console */}
          <div className="bg-slate-900/40 backdrop-blur border border-slate-850 p-6 rounded-3xl shadow-lg lg:col-span-2 flex flex-col justify-between min-h-[300px]">
            <div>
              <h3 className="text-lg font-bold text-white mb-4">Simulation Console Log</h3>
              <div className="bg-slate-950 rounded-2xl p-4 font-mono text-xs text-sky-400 border border-slate-900 h-64 overflow-y-auto space-y-2">
                {simulationSteps.length === 0 ? (
                  <div className="text-slate-600 italic">No transaction simulation currently active. Fill the simulator form to test.</div>
                ) : (
                  simulationSteps.map((step, idx) => (
                    <div key={idx} className="flex gap-2 items-start">
                      <span className="text-slate-600 select-none">[{idx + 1}]</span>
                      <span className={idx === simulationSteps.length - 1 && isSimulating ? 'text-emerald-400 animate-pulse' : 'text-sky-300'}>{step}</span>
                    </div>
                  ))
                )}
              </div>
            </div>
            <div className="text-[10px] text-slate-500 mt-2">
              Note: This is a simulation panel visualizing node propagation. Services currently respond to ping health checks.
            </div>
          </div>
        </div>

        {/* Transactions Table */}
        <div className="bg-slate-900/40 backdrop-blur border border-slate-850 p-6 rounded-3xl shadow-lg">
          <h3 className="text-lg font-bold text-white mb-4">Recent Simulated Ledger</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-850 text-slate-400 uppercase text-xs tracking-wider">
                  <th className="py-3 px-4">Transaction ID</th>
                  <th className="py-3 px-4">Payee</th>
                  <th className="py-3 px-4">Routing Path</th>
                  <th className="py-3 px-4">Amount</th>
                  <th className="py-3 px-4">Time</th>
                  <th className="py-3 px-4">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-850/50">
                {transactions.map((txn) => (
                  <tr key={txn.id} className="hover:bg-slate-900/30 transition-colors">
                    <td className="py-4 px-4 font-mono font-bold text-slate-350">{txn.id}</td>
                    <td className="py-4 px-4 text-slate-300">{txn.payee}</td>
                    <td className="py-4 px-4 text-xs font-mono text-slate-400">{txn.path}</td>
                    <td className="py-4 px-4 font-semibold text-white">₹{txn.amount.toFixed(2)}</td>
                    <td className="py-4 px-4 text-slate-500 text-xs">{txn.time}</td>
                    <td className="py-4 px-4">
                      <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-emerald-500/10 text-emerald-400">
                        {txn.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

      </div>
    </div>
  );
}
