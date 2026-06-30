import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import TransactionTracker from '../components/TransactionTracker';

export default function Dashboard() {
  const { user, logout } = useAuth();
  
  // Local wallet state (fetched from Bank Service)
  const [balance, setBalance] = useState(0.00);
  const [payee, setPayee] = useState('');
  const [amount, setAmount] = useState('');
  const [activeTxnId, setActiveTxnId] = useState(null);

  // Transaction history (loaded dynamically from database)
  const [transactions, setTransactions] = useState([]);
  const [isSimulating, setIsSimulating] = useState(false);

  // Derive sender UPI ID
  const senderUpiId = user?.email ? user.email.split('@')[0] + '@mesh' : '';

  // Fetch real balance from Bank-Service
  const fetchRealBalance = async () => {
    if (!senderUpiId) return;
    try {
      const res = await axios.get(`http://localhost:8085/api/accounts/upi/${senderUpiId}`);
      if (res.data && res.data.currentBalance !== undefined) {
        setBalance(res.data.currentBalance);
      }
    } catch (err) {
      console.error('Failed to fetch account balance from Bank-Service:', err);
    }
  };

  // Fetch real transactions from Transaction-Service
  const fetchRealTransactions = async () => {
    try {
      const token = localStorage.getItem('token');
      const res = await axios.get('http://localhost:8086/api/transactions', {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.data) {
        const successfulTxns = res.data.filter(txn => txn.status === 'SUCCESS');
        const mapped = successfulTxns.map(txn => {
          return {
            id: txn.transactionId,
            payee: txn.receiverUpiId,
            amount: txn.amount
          };
        });
        setTransactions(mapped);
      }
    } catch (err) {
      console.error('Failed to fetch transactions from Transaction-Service:', err);
    }
  };

  const loadData = () => {
    fetchRealBalance();
    fetchRealTransactions();
  };

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 8000);
    return () => clearInterval(interval);
  }, [senderUpiId]);

  const handleSimulatePayment = async (e) => {
    e.preventDefault();
    if (!payee || !amount || parseFloat(amount) <= 0) return;

    setIsSimulating(true);

    try {
      const token = localStorage.getItem('token');
      const response = await axios.post('http://localhost:8086/api/transactions', {
        receiverUpiId: payee.trim(),
        amount: parseFloat(amount)
      }, {
        headers: { Authorization: `Bearer ${token}` }
      });

      const { transactionId } = response.data;
      console.log('Transaction created successfully:', response.data);

      setActiveTxnId(transactionId);
      setIsSimulating(false);

      // Trigger immediate data refresh
      fetchRealBalance();
      fetchRealTransactions();

      setPayee('');
      setAmount('');
    } catch (err) {
      console.error('Failed to create transaction:', err);
      if (err.response && err.response.data) {
        console.error('Validation / Server Error details:', err.response.data);
      }
      setIsSimulating(false);
    }
  };

  return (
    <div className="min-h-screen bg-white text-slate-900 p-4 sm:p-8">
      <div className="max-w-4xl mx-auto space-y-8">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-slate-200 pb-6">
          <div>
            <h1 className="text-3xl font-extrabold text-slate-900">Welcome, {user?.fullName || 'User'}</h1>
            <p className="text-sm text-slate-500 mt-1">Terminal Node: <span className="font-mono text-blue-600">{user?.email}</span></p>
          </div>
          <div className="flex gap-3">
            <button 
              onClick={loadData}
              className="px-4 py-2 bg-slate-100 border border-slate-200 text-slate-700 rounded-xl hover:bg-slate-200 transition-colors text-xs flex items-center gap-2 cursor-pointer"
            >
              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99" />
              </svg>
              Refresh Data
            </button>
            <button 
              onClick={logout}
              className="px-4 py-2 bg-red-50 border border-red-200 text-red-600 rounded-xl hover:bg-red-100 transition-colors text-xs flex items-center gap-2 cursor-pointer"
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
              onClose={() => {
                setActiveTxnId(null);
                loadData(); // refresh table & balance on closing tracker
              }} 
            />
          </div>
        )}

        {/* Simulated Balance & Simulator Form row */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Simulated Balance */}
          <div className="bg-white border border-slate-200 p-6 rounded-3xl flex flex-col justify-between shadow-sm">
            <div>
              <span className="text-slate-500 text-xs uppercase tracking-wider font-semibold">Simulated Balance</span>
              <div className="mt-4">
                <span className="text-4xl font-extrabold text-slate-900">₹{balance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                <p className="text-slate-500 text-xs mt-2">Stored securely inside bank database credentials</p>
              </div>
            </div>
          </div>

          {/* Simulator Form */}
          <div className="bg-white border border-slate-200 p-6 rounded-3xl shadow-sm">
            <h3 className="text-lg font-bold text-slate-950 mb-4">Payment Simulator</h3>
            <form onSubmit={handleSimulatePayment} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-2">Payee UPI ID</label>
                <input
                  type="text"
                  required
                  value={payee}
                  onChange={(e) => setPayee(e.target.value)}
                  placeholder="payee@upi"
                  className="w-full bg-white border border-slate-200 rounded-xl px-4 py-3 text-sm text-slate-900 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition-all"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-2">Amount (₹)</label>
                <input
                  type="number"
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="100.00"
                  className="w-full bg-white border border-slate-200 rounded-xl px-4 py-3 text-sm text-slate-900 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition-all"
                />
              </div>

              <button
                type="submit"
                disabled={isSimulating}
                className="w-full py-3.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-bold transition-all disabled:opacity-50 shadow-sm cursor-pointer"
              >
                {isSimulating ? 'Simulating Broadcast...' : 'Broadcast Offline Transaction'}
              </button>
            </form>
          </div>
        </div>

        {/* Transactions Table */}
        <div className="bg-white border border-slate-200 p-6 rounded-3xl shadow-sm">
          <h3 className="text-lg font-bold text-slate-900 mb-4">Recent Simulated Ledger</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-slate-500 uppercase text-xs tracking-wider">
                  <th className="py-3 px-4">Transaction ID</th>
                  <th className="py-3 px-4">Payee</th>
                  <th className="py-3 px-4">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {transactions.length === 0 ? (
                  <tr>
                    <td colSpan="3" className="py-8 px-4 text-center text-slate-400 italic">
                      No transactions found in database for this user.
                    </td>
                  </tr>
                ) : (
                  transactions.map((txn) => (
                    <tr key={txn.id} className="hover:bg-slate-50 transition-colors">
                      <td className="py-4 px-4 font-mono text-xs text-slate-600">{txn.id}</td>
                      <td className="py-4 px-4 text-slate-700 font-medium">{txn.payee}</td>
                      <td className="py-4 px-4 font-bold text-slate-900">₹{txn.amount.toFixed(2)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

      </div>
    </div>
  );
}
