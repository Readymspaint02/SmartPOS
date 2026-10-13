import http from '../request';

export const createAlipayPayment = (data) => {
  return http('/alipay/create', { method: 'POST', data });
};

export const queryAlipayPayment = (orderNo) => {
  return http(`/alipay/query/${orderNo}`, { method: 'GET' });
};

export const queryAlipayLocalStatus = (orderNo) => {
  return http(`/alipay/status/${orderNo}`, { method: 'GET' });
};

export const syncAlipayStatus = (orderNo) => {
  return http(`/alipay/sync/${orderNo}`, { method: 'POST' });
};

export const refundAlipay = (data) => {
  return http('/alipay/refund', { method: 'POST', data });
};

export const queryAlipayRefund = (orderNo) => {
  return http(`/alipay/refund/query/${orderNo}`, { method: 'GET' });
};