import http from '../request';

export const getSalesOverview = () => {
  return http('/report/sales/overview', { method: 'GET' });
};

export const getTodaySalesOverview = () => {
  return http('/report/sales/today', { method: 'GET' });
};

export const getSalesTrend = (params = {}) => {
  return http('/report/sales/trend', { method: 'GET', params });
};

export const getRecentOrders = (params = {}) => {
  return http('/report/sales/recentOrders', { method: 'GET', params });
};

export const getInventoryWarnings = () => {
  return http('/report/inventory/warning', { method: 'GET' });
};

export const getInventoryStats = () => {
  return http('/report/inventory/stats', { method: 'GET' });
};
