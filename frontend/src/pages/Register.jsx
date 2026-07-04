import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Register() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [initialBalance, setInitialBalance] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const { register } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    // Client-side validations
    if (!name || !email || !password || !confirmPassword) {
      setError('Please fill in all fields.');
      return;
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }

    // Strong password pattern
    const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/;
    if (!passwordPattern.test(password)) {
      setError('Password must contain at least one letter and one number.');
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    // Validate initial balance if provided
    if (initialBalance !== '' && (isNaN(parseFloat(initialBalance)) || parseFloat(initialBalance) < 0)) {
      setError('Initial balance must be zero or greater.');
      return;
    }

    setLoading(true);

    try {
      const result = await register(name, email, password, initialBalance);
      const upiMsg = result?.upiId ? ` Your UPI ID: ${result.upiId}` : '';
      setSuccess(`Registration successful!${upiMsg} Redirecting to login...`);
      setTimeout(() => {
        navigate('/login');
      }, 2000);
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data) {
        const data = err.response.data;
        if (data.errors) {
          const fieldErrors = Object.values(data.errors).join(' ');
          setError(fieldErrors);
        } else if (data.message) {
          setError(data.message);
        } else {
          setError('Registration failed. Please check your inputs.');
        }
      } else {
        setError('Registration failed. Connecting to auth server failed.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-4rem)] bg-white flex items-center justify-center px-4 py-12 relative overflow-hidden">
      <div className="max-w-md w-full bg-white border border-white p-8 rounded-3xl shadow-2xl relative z-10">
        <div className="text-center mb-8">
          <span className="h-12 w-12 rounded-2xl bg-blue-600 flex items-center justify-center text-white font-bold text-2xl mx-auto shadow-md mb-4">
            M
          </span>
          <h2 className="text-3xl font-extrabold text-black">Create Account</h2>
          <p className="text-sm text-black mt-2">Register new mesh node terminal</p>
        </div>

        {error && (
          <div className="bg-white border-2 border-white text-black font-bold text-xs px-4 py-3 rounded-xl mb-4 shadow-md">
            ERROR: {error}
          </div>
        )}

        {success && (
          <div className="bg-white border-2 border-white text-black font-bold text-xs px-4 py-3 rounded-xl mb-4 shadow-md">
            SUCCESS: {success}
          </div>
        )}

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-black mb-1.5">
              Full Name
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full bg-white border border-white rounded-xl px-4 py-3 text-sm text-black focus:outline-none focus:ring-1 focus:ring-blue-600 focus:border-blue-600 transition-all placeholder:text-black/50 shadow-md"
              placeholder="John Doe"
              required
            />
          </div>

          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-black mb-1.5">
              Email Address
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full bg-white border border-white rounded-xl px-4 py-3 text-sm text-black focus:outline-none focus:ring-1 focus:ring-blue-600 focus:border-blue-600 transition-all placeholder:text-black/50 shadow-md"
              placeholder="name@example.com"
              required
            />
          </div>

          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-black mb-1.5">
              Password
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full bg-white border border-white rounded-xl px-4 py-3 text-sm text-black focus:outline-none focus:ring-1 focus:ring-blue-600 focus:border-blue-600 transition-all placeholder:text-black/50 shadow-md"
              placeholder="••••••••"
              required
            />
          </div>

          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-black mb-1.5">
              Confirm Password
            </label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full bg-white border border-white rounded-xl px-4 py-3 text-sm text-black focus:outline-none focus:ring-1 focus:ring-blue-600 focus:border-blue-600 transition-all placeholder:text-black/50 shadow-md"
              placeholder="••••••••"
              required
            />
          </div>

          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-black mb-1.5">
              Initial Balance (₹)
            </label>
            <input
              type="number"
              min="0"
              step="0.01"
              value={initialBalance}
              onChange={(e) => setInitialBalance(e.target.value)}
              className="w-full bg-white border border-white rounded-xl px-4 py-3 text-sm text-black focus:outline-none focus:ring-1 focus:ring-blue-600 focus:border-blue-600 transition-all placeholder:text-black/50 shadow-md"
              placeholder="10000.00 (optional, defaults to 0)"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 px-4 rounded-xl bg-blue-600 text-white font-bold hover:opacity-90 transition-opacity disabled:opacity-50 shadow-md mt-2 cursor-pointer"
          >
            {loading ? 'Registering...' : 'Create Node & Generate Key'}
          </button>
        </form>

        <p className="text-center text-xs text-black mt-6">
          Already registered?{' '}
          <Link to="/login" className="text-black font-bold underline hover:no-underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
