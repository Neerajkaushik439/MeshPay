/**
 * Normalizes an API URL to ensure it has a proper protocol and domain.
 * Render injects internal service names like "meshpay-user-service-rs49" without ".onrender.com".
 * This function converts them to full public URLs: "https://meshpay-user-service-rs49.onrender.com".
 */
export const normalizeUrl = (url, defaultUrl) => {
  if (!url || url === 'undefined' || url === 'null' || url === '') {
    return defaultUrl;
  }
  let trimmed = url.trim().replace(/\/+$/, ''); // trim whitespace and trailing slashes

  // Strip existing protocol if present to inspect the hostname
  let protocol = 'https://';
  if (trimmed.startsWith('http://')) {
    protocol = 'http://';
    trimmed = trimmed.substring(7);
  } else if (trimmed.startsWith('https://')) {
    protocol = 'https://';
    trimmed = trimmed.substring(8);
  }

  // Handle local development
  if (trimmed.startsWith('localhost') || trimmed.startsWith('127.0.0.1')) {
    return `http://${trimmed}`;
  }

  // If it's a Render internal service identifier (e.g. "meshpay-user-service-rs49" without dots)
  if (!trimmed.includes('.')) {
    return `https://${trimmed}.onrender.com`;
  }

  return `${protocol}${trimmed}`;
};

export const USER_SERVICE_URL = normalizeUrl(import.meta.env.VITE_USER_SERVICE_URL, 'http://localhost:8081');
export const BANK_SERVICE_URL = normalizeUrl(import.meta.env.VITE_BANK_SERVICE_URL, 'http://localhost:8085');
export const TRANSACTION_SERVICE_URL = normalizeUrl(import.meta.env.VITE_TRANSACTION_SERVICE_URL, 'http://localhost:8086');
