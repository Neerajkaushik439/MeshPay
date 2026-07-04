import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !password) {
      setError('Please fill in all fields.');
      return;
    }
    
    setLoading(true);
    setError('');

    try {
      await login(email, password);
      navigate('/dashboard');
    } catch (err) {
      console.error(err);
      if (err.response && err.response.data && err.response.data.message) {
        setError(err.response.data.message);
      } else {
        setError('Login failed. Please check your credentials and try again.');
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
          <h2 className="text-3xl font-extrabold text-black">Welcome Back</h2>
          <p className="text-sm text-black mt-2">Sign in to your offline mesh terminal</p>
        </div>

        {error && (
          <div className="bg-white border-2 border-white text-black font-bold text-xs px-4 py-3 rounded-xl mb-4 shadow-md">
            ERROR: {error}
          </div>
        )}

        <form className="space-y-6" onSubmit={handleSubmit}>
          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-black mb-2">
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
            <label className="block text-xs font-bold uppercase tracking-wider text-black mb-2">
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

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 px-4 rounded-xl bg-blue-600 text-white font-bold hover:opacity-90 transition-opacity disabled:opacity-50 shadow-md cursor-pointer"
          >
            {loading ? 'Authenticating...' : 'Authenticate Terminal'}
          </button>
        </form>

        <p className="text-center text-xs text-black mt-8">
          Need a terminal ID?{' '}
          <Link to="/register" className="text-black font-bold underline hover:no-underline">
            Register terminal
          </Link>
        </p>
      </div>
    </div>
  );
}
