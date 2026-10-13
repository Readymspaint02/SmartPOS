import axios from 'axios';
import http from '../request';

export const fetchAssistantInsight = (metric, params = {}) => {
  return http.get('/assistant/insight', {
    params: {
      metric,
      ...params,
    },
  });
};

export const fetchRecentOrders = (limit = 10) => {
  return http.get('/order/recent', { params: { limit } });
};

export const fetchMemberProfile = (memberId) => {
  return http.get(`/system/member/byMemberId/${memberId}`);
};

export const fetchMemberAnalysis = (memberId) => {
  return http.get(`/assistant/member/analysis/${memberId}`);
};
