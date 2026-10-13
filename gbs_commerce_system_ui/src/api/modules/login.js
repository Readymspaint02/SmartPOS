import http from "../request";

export const login = async data => {
    return http('/auth/login', { method: 'POST', data });
};

export const getRoleList = async () => {
    return http.get('/auth/roles');
};

// 混合登录方案：输入账号后带出该账号拥有的可用角色
export const getUserRoles = async username => {
    return http.get('/auth/userRoles', { params: { username } });
};
