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
  const [paymentError, setPaymentError] = useState('');

  // Transaction history (loaded dynamically from database)
  const [transactions, setTransactions] = useState([]);
  const [isSimulating, setIsSimulating] = useState(false);

  // Use persisted UPI ID from user profile
  const senderUpiId = user?.upiId || '';

  const bankServiceUrl = import.meta.env.VITE_BANK_SERVICE_URL || 'http://localhost:8085';
  const transactionServiceUrl = import.meta.env.VITE_TRANSACTION_SERVICE_URL || 'http://localhost:8086';

  // Fetch real balance from Bank-Service
  const fetchRealBalance = async () => {
    if (!senderUpiId) return;
    try {
      const res = await axios.get(`${bankServiceUrl}/api/accounts/upi/${senderUpiId}`);
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
      const res = await axios.get(`${transactionServiceUrl}/api/transactions`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.data) {
        const mapped = res.data.map(txn => {
          const isSender = txn.senderUpiId === senderUpiId;
          return {
            id: txn.transactionId,
            type: isSender ? 'SENT' : 'RECEIVED',
            counterparty: isSender ? txn.receiverUpiId : txn.senderUpiId,
            amount: txn.amount,
            status: txn.status
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
    setPaymentError('');

    if (!payee || !amount || parseFloat(amount) <= 0) return;

    // Client-side self-transfer check
    if (payee.trim().toLowerCase() === senderUpiId.toLowerCase()) {
      setPaymentError('You cannot send money to your own UPI ID.');
      return;
    }

    setIsSimulating(true);

    try {
      const token = localStorage.getItem('token');
      const response = await axios.post(`${transactionServiceUrl}/api/transactions`, {
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
        const data = err.response.data;
        setPaymentError(data.message || 'Transaction failed. Please try again.');
      } else {
        setPaymentError('Transaction failed. Could not reach the server.');
      }
      setIsSimulating(false);
    }
  };

  return (
    <div className="min-h-screen bg-white text-black p-4 sm:p-8">
      <div className="max-w-4xl mx-auto space-y-8">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-white shadow-sm pb-6">
          <div>
            <h1 className="text-3xl font-extrabold text-black">Welcome, {user?.fullName || 'User'}</h1>
            <p className="text-sm text-black mt-1">UPI ID: <span className="font-mono font-bold bg-blue-600 text-white px-2 py-0.5 rounded-md">{senderUpiId || 'Not assigned'}</span></p>
            <p className="text-xs text-black/60 mt-1">Terminal Node: <span className="font-mono">{user?.email}</span></p>
          </div>
          <div className="flex gap-3">
            <button 
              onClick={loadData}
              className="px-4 py-2 bg-white border border-white shadow-md text-black rounded-xl hover:bg-blue-600 hover:text-white transition-colors text-xs flex items-center gap-2 cursor-pointer"
            >
              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99" />
              </svg>
              Refresh Data
            </button>
            <button 
              onClick={logout}
              className="px-4 py-2 bg-white border border-white shadow-md text-black rounded-xl hover:bg-blue-600 hover:text-white transition-colors text-xs flex items-center gap-2 cursor-pointer"
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
          <div className="bg-white border border-white p-6 rounded-3xl flex flex-col justify-between shadow-xl">
            <div>
              <span className="text-black text-xs uppercase tracking-wider font-bold">Account Balance</span>
              <div className="mt-4">
                <span className="text-4xl font-extrabold text-black">₹{Number(balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                <p className="text-black text-xs mt-2">Linked to UPI: <span className="font-mono font-bold">{senderUpiId}</span></p>
              </div>
            </div>
          </div>

          {/* Simulator Form */}
          <div className="bg-white border border-white p-6 rounded-3xl shadow-xl">
            <h3 className="text-lg font-bold text-black mb-4">Payment Simulator</h3>

            {paymentError && (
              <div className="bg-white border-2 border-white text-black font-bold text-xs px-4 py-3 rounded-xl mb-4 shadow-md">
                ERROR: {paymentError}
              </div>
            )}

            <form onSubmit={handleSimulatePayment} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-black uppercase tracking-wider mb-2">Payee UPI ID</label>
                <input
                  type="text"
                  required
                  value={payee}
                  onChange={(e) => { setPayee(e.target.value); setPaymentError(''); }}
                  placeholder="payee@meshpay"
                  className="w-full bg-white border border-white rounded-xl px-4 py-3 text-sm text-black focus:outline-none focus:border-blue-600 focus:ring-1 focus:ring-blue-600 transition-all placeholder:text-black/50 shadow-md"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-black uppercase tracking-wider mb-2">Amount (₹)</label>
                <input
                  type="number"
                  required
                  min="0.01"
                  step="0.01"
                  value={amount}
                  onChange={(e) => { setAmount(e.target.value); setPaymentError(''); }}
                  placeholder="100.00"
                  className="w-full bg-white border border-white rounded-xl px-4 py-3 text-sm text-black focus:outline-none focus:border-blue-600 focus:ring-1 focus:ring-blue-600 transition-all placeholder:text-black/50 shadow-md"
                />
              </div>

              <button
                type="submit"
                disabled={isSimulating}
                className="w-full py-3.5 rounded-xl bg-blue-600 hover:opacity-90 text-white font-bold transition-all disabled:opacity-50 shadow-md cursor-pointer"
              >
                {isSimulating ? 'Simulating Broadcast...' : 'Broadcast Offline Transaction'}
              </button>
            </form>
          </div>
        </div>

        {/* Transactions Table */}
        <div className="bg-white border border-white p-6 rounded-3xl shadow-xl">
          <h3 className="text-lg font-bold text-black mb-4">Transaction History</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-white text-black uppercase text-xs tracking-wider">
                  <th className="py-3 px-4">Transaction ID</th>
                  <th className="py-3 px-4">Type</th>
                  <th className="py-3 px-4">Counterparty</th>
                  <th className="py-3 px-4">Amount</th>
                  <th className="py-3 px-4">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white">
                {transactions.length === 0 ? (
                  <tr>
                    <td colSpan="5" className="py-8 px-4 text-center text-black italic">
                      No transactions found in database for this user.
                    </td>
                  </tr>
                ) : (
                  transactions.map((txn) => (
                    <tr key={txn.id} className="hover:bg-blue-600 hover:text-white transition-colors">
                      <td className="py-4 px-4 font-mono text-xs">{txn.id}</td>
                      <td className="py-4 px-4">
                        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-bold ${
                          txn.type === 'SENT' 
                            ? 'bg-red-100 text-red-700' 
                            : 'bg-green-100 text-green-700'
                        }`}>
                          {txn.type === 'SENT' ? '↑ Sent' : '↓ Received'}
                        </span>
                      </td>
                      <td className="py-4 px-4 font-medium">{txn.counterparty}</td>
                      <td className="py-4 px-4 font-bold">
                        <span className={txn.type === 'SENT' ? 'text-red-600' : 'text-green-600'}>
                          {txn.type === 'SENT' ? '-' : '+'}₹{Number(txn.amount).toFixed(2)}
                        </span>
                      </td>
                      <td className="py-4 px-4">
                        <span className={`text-xs font-bold px-2 py-0.5 rounded-md ${
                          txn.status === 'SUCCESS' ? 'bg-green-100 text-green-700' :
                          txn.status === 'FAILED' ? 'bg-red-100 text-red-700' :
                          'bg-yellow-100 text-yellow-700'
                        }`}>
                          {txn.status}
                        </span>
                      </td>
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
