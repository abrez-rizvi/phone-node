/**
 * PhoneNode API Configuration & Host Environment Resolver
 * Enables zero-config hosting from Android assets (file:///android_asset/web/)
 * as well as standard HTTP/HTTPS web servers.
 */

(function (window) {
  'use strict';

  // Configurable base URL for backend API
  function resolveApiBase() {
    // 1. Explicit global variable (e.g., injected by Android WebView bridge)
    if (window.PHONENODE_API_BASE) {
      return window.PHONENODE_API_BASE.replace(/\/+$/, '');
    }

    // 2. Saved user override in localStorage
    try {
      const stored = localStorage.getItem('phonenode_api_base');
      if (stored) return stored.replace(/\/+$/, '');
    } catch (e) {}

    // 3. Android asset file scheme (file:///android_asset/web/...)
    // When loaded from disk, relative root /api/... fails; point to local server
    if (window.location.protocol === 'file:') {
      return 'http://localhost:8000';
    }

    // 4. Default: relative to current origin (same-origin web hosting)
    return '';
  }

  const PhoneNodeConfig = {
    get apiBase() {
      return resolveApiBase();
    },

    setApiBase(url) {
      if (!url) {
        localStorage.removeItem('phonenode_api_base');
      } else {
        localStorage.setItem('phonenode_api_base', url.trim().replace(/\/+$/, ''));
      }
      console.log('[PhoneNode] API Base updated to:', this.apiBase);
    },

    // Build absolute/relative API URL
    url(endpoint) {
      const cleanEndpoint = endpoint.startsWith('/') ? endpoint : '/' + endpoint;
      return this.apiBase + cleanEndpoint;
    },

    // Build WebSocket URL
    wsUrl(endpoint) {
      const cleanEndpoint = endpoint.startsWith('/') ? endpoint : '/' + endpoint;
      const base = this.apiBase;
      if (base) {
        return base.replace(/^http:/, 'ws:').replace(/^https:/, 'wss:') + cleanEndpoint;
      }
      const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      return `${proto}//${window.location.host}${cleanEndpoint}`;
    }
  };

  window.PhoneNodeConfig = PhoneNodeConfig;
  window.API_BASE = PhoneNodeConfig.apiBase;
  window.apiUrl = (endpoint) => PhoneNodeConfig.url(endpoint);
  window.wsUrl = (endpoint) => PhoneNodeConfig.wsUrl(endpoint);

})(window);
