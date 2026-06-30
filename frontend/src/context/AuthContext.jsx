import React, { createContext, useState, useEffect, useContext } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('token') || null);
  const [loading, setLoading] = useState(true);

  const fetchCurrentUser = async (jwtToken) => {
    try {
      const res = await api.get('/api/auth/me', {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });
      setUser(res.data);
    } catch (err) {
      console.error("Failed to load user info:", err);
      logout();
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (token) {
      fetchCurrentUser(token);
    } else {
      setLoading(false);
    }
  }, [token]);

  const login = async (email, password) => {
    const res = await api.post('/api/auth/login', { email, password });
    const { token: jwtToken, fullName } = res.data;
    localStorage.setItem('token', jwtToken);
    setToken(jwtToken);
    // Explicitly fetching User details immediately to populate database context
    try {
      const meRes = await api.get('/api/auth/me', {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });
      setUser(meRes.data);
    } catch (err) {
      setUser({ email, fullName });
    }
    return res.data;
  };

  const register = async (fullName, email, password) => {
    const res = await api.post('/api/auth/register', { fullName, email, password });
    return res.data;
  };

  const logout = () => {
    localStorage.removeItem('token');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
