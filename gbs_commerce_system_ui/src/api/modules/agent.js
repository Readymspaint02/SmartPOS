import axios from 'axios';

const isClient = typeof window !== 'undefined';

const getRuntimeInjection = (key) => {
  if (!isClient || !key) {
    return undefined;
  }
  const value = window[key];
  return typeof value === 'string' ? value : undefined;
};

const normalizeToHttps = (value) => {
  if (typeof value !== 'string') {
    return value;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return '';
  }
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed;
  }
  if (/^\/\//.test(trimmed)) {
    return `https:${trimmed}`;
  }
  return trimmed;
};

const resolveBaseUrl = (envValue, runtimeKey, fallbackValue) => {
  const runtimeValue = getRuntimeInjection(runtimeKey);
  const candidate =
    (typeof envValue === 'string' && envValue.trim()) ||
    runtimeValue ||
    fallbackValue;

  const normalized = normalizeToHttps(candidate);
  return normalized || fallbackValue;
};

const defaultAgentBase = resolveBaseUrl(
  import.meta.env.VITE_AGENT_PROXY_URL,
  '__AGENT_PROXY_URL__',
  'http://localhost:9000'
);

const defaultTtsBase = resolveBaseUrl(
  import.meta.env.VITE_TTS_HTTP_URL,
  '__TTS_HTTP_URL__',
  'http://localhost:8101'
);

const agentHttp = axios.create({
  baseURL: defaultAgentBase,
  timeout: 20000,
});

const ttsHttp = axios.create({
  baseURL: defaultTtsBase,
  timeout: 20000,
});

const getAuthToken = () => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('token');
    if (token) {
      return token;
    }
  }
  return null;
};

agentHttp.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

agentHttp.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      const detail = error?.response?.data?.detail || '请登录后使用';
      error.isAuthError = true;
      error.authMessage = detail;
    }
    return Promise.reject(error);
  }
);

export const sendAgentQuery = (message, options = {}) => {
  const payload = { query: message, session_id: options.sessionId || null };
  if (options.maxIterations != null && !Number.isNaN(Number(options.maxIterations))) {
    payload.max_iterations = Number(options.maxIterations);
  }
  return agentHttp.post('/agent_query', payload);
};

/**
 * 流式智能体查询（SSE）：最终回答逐块增量回调，实现打字机效果。
 * @param {string} message 用户消息
 * @param {object} options
 * @param {string} [options.sessionId] 会话ID（多轮记忆）
 * @param {(delta: string) => void} options.onDelta 回答增量回调
 * @param {(meta: object) => void} [options.onMeta] 流结束元数据回调（events/intent/iterations/voice_order/session_id）
 * @param {(error: Error) => void} [options.onError] 出错回调
 * @param {AbortSignal} [options.signal] 取消信号
 * @returns {Promise<object>} resolve 为最终 meta，reject 为网络/HTTP 错误
 */
export const sendAgentQueryStream = (message, options = {}) => {
  const { onDelta, onMeta, onError, signal } = options;
  const payload = { query: message, session_id: options.sessionId || null };
  const maxIterations = options.maxIterations;
  if (maxIterations != null && !Number.isNaN(Number(maxIterations))) {
    payload.max_iterations = Number(maxIterations);
  }
  const token = getAuthToken();

  return fetch(`${defaultAgentBase}/agent_query_stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
    signal,
  })
    .then((resp) => {
      if (!resp.ok) {
        return resp.text().then((text) => {
          const detail = (() => {
            try {
              return JSON.parse(text)?.detail || '';
            } catch {
              return '';
            }
          })();
          const err = new Error(detail || `智能体服务异常(${resp.status})`);
          if (resp.status === 401) {
            err.isAuthError = true;
            err.authMessage = detail || '请登录后使用';
          }
          throw err;
        });
      }
      const reader = resp.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      const readLoop = async (resolve, reject) => {
        try {
          const { done, value } = await reader.read();
          if (done) {
            resolve(null);
            return;
          }
          buffer += decoder.decode(value, { stream: true });
          // SSE 事件以空行分隔，逐条解析
          let sepIndex;
          while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
            const rawEvent = buffer.slice(0, sepIndex);
            buffer = buffer.slice(sepIndex + 2);
            const lines = rawEvent.split('\n');
            let eventType = 'message';
            let data = '';
            for (const line of lines) {
              if (line.startsWith('event:')) eventType = line.slice(6).trim();
              else if (line.startsWith('data:')) data += line.slice(5).trim();
            }
            if (!data) continue;
            let parsed;
            try {
              parsed = JSON.parse(data);
            } catch {
              continue;
            }
            if (eventType === 'delta' && typeof parsed.text === 'string') {
              onDelta?.(parsed.text);
            } else if (eventType === 'meta' && parsed) {
              onMeta?.(parsed);
            } else if (eventType === 'error') {
              onError?.(new Error(parsed.message || '智能体流式响应异常'));
            }
          }
          readLoop(resolve, reject);
        } catch (error) {
          if (error?.name === 'AbortError') {
            reject(error);
          } else {
            onError?.(error);
            reject(error);
          }
        }
      };

      return new Promise((resolve, reject) => {
        readLoop(resolve, reject);
      });
    });
};

export const synthesizeSpeech = (payload) => {
  return ttsHttp.post('/tts_synthesize', payload);
};

export const checkAgentHealth = () => {
  return agentHttp.get('/health');
};

// ============ 会话记忆 ============
// 注意：agentHttp 无响应拦截器，这里统一解包 response.data，调用方直接拿后端 body {status, data}
export const fetchAgentSessions = async () => {
  const res = await agentHttp.get('/agent/sessions');
  return res.data;
};

export const createAgentSession = async () => {
  const res = await agentHttp.post('/agent/session');
  return res.data;
};

export const deleteAgentSession = async (sessionId) => {
  const res = await agentHttp.delete(`/agent/session/${sessionId}`);
  return res.data;
};

export const fetchAgentMessages = async (sessionId) => {
  const res = await agentHttp.get('/agent/messages', { params: { session_id: sessionId } });
  return res.data;
};
