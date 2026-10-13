<script setup>
import { ref, onBeforeUnmount, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  sendAgentQuery,
  sendAgentQueryStream,
  synthesizeSpeech,
  checkAgentHealth,
  fetchAgentSessions,
  createAgentSession,
  deleteAgentSession,
  fetchAgentMessages,
} from '@/api/modules/agent';
import { createStreamingAsrClient } from '@/api/modules/asr';

const router = useRouter();
const isClient = typeof window !== 'undefined';
const supportsSpeechSynthesis = isClient && 'speechSynthesis' in window;

const wait = (ms = 1000) =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });

const isOpen = ref(false);
const panelMode = ref('normal'); // normal | large（展开模式，看清完整调用轨迹）
const voiceOrder = ref(null); // 语音下单草稿（订单卡片数据，借鉴天机学堂 param=1003）
const inputText = ref('');
const sending = ref(false);
const recording = ref(false);
const recognitionStatus = ref('');
const voiceDraft = ref('');
const isSpeaking = ref(false);
// 语音播报开关（默认开，localStorage 记忆）
const speechEnabled = ref(!isClient || localStorage.getItem('agentSpeechEnabled') !== '0');
const toggleSpeech = () => {
  speechEnabled.value = !speechEnabled.value;
  localStorage.setItem('agentSpeechEnabled', speechEnabled.value ? '1' : '0');
  if (!speechEnabled.value) stopSpeech();
};
const networkRecoveryInProgress = ref(false);
const networkRecoveryMessage = ref('');
const networkRecoveryFailed = ref(false);
const networkRecoveryAttempt = ref(0);
const maxNetworkRecoveryAttempts = 3;
const traceExpanded = ref({});
const messageContainer = ref(null);

const messages = ref([
  {
    id: 'hello',
    role: 'assistant',
    text: '您好，我是智慧收银助手 SmartPOS-AI。支持会员查询、订单查询、经营分析、商品/库存查询、语音收银(一句话下单)等。示例：尾号3333的会员余额和积分是多少？',
    time: new Date().toLocaleTimeString(),
    intent: null,
    iterations: null,
    events: [],
  },
]);

const quickActions = [
  { label: '尾号会员', prompt: '帮我查一下尾号3333的会员姓名、余额和积分' },
  { label: '最近订单', prompt: '最近5笔订单的情况是什么' },
  { label: '热销商品', prompt: '近30天热销商品TOP5是哪些' },
  { label: '低库存', prompt: '当前有哪些商品库存低于预警阈值' },
  { label: '一句话下单', prompt: '来2瓶可口可乐和1包乐事薯片，尾号3333的会员结个账' },
];

// ============ 会话记忆 ============
const sessionId = ref(''); // 当前会话ID（空=未开始）
const sessionTitle = ref('新会话');
const sessions = ref([]); // 历史会话列表
const showSessionList = ref(false); // 是否展开历史会话面板
const sessionLoading = ref(false);

const ensureSession = async () => {
  if (sessionId.value) return sessionId.value;
  try {
    const res = await createAgentSession();
    if (res?.status === 'success' && res.data?.session_id) {
      sessionId.value = res.data.session_id;
      sessionTitle.value = res.data.title || '新会话';
    }
  } catch (error) {
    console.warn('创建会话失败', error);
  }
  return sessionId.value;
};

const loadSessions = async () => {
  try {
    const res = await fetchAgentSessions();
    if (res?.status === 'success') {
      sessions.value = Array.isArray(res.data) ? res.data : [];
    }
  } catch (error) {
    console.warn('加载历史会话失败', error);
  }
};

const toggleSessionList = async () => {
  showSessionList.value = !showSessionList.value;
  if (showSessionList.value && sessions.value.length === 0) {
    await loadSessions();
  }
};

const newSession = async () => {
  sessionId.value = '';
  sessionTitle.value = '新会话';
  showSessionList.value = false;
  messages.value = [
    {
      id: `hello-${Date.now()}`,
      role: 'assistant',
      text: '您好，我是智慧收银助手 SmartPOS-AI。支持会员查询、订单查询、经营分析、商品/库存查询、语音收银(一句话下单)等。示例：尾号3333的会员余额和积分是多少？',
      time: new Date().toLocaleTimeString(),
      intent: null,
      iterations: null,
      events: [],
    },
  ];
  await ensureSession();
};

const openSession = async (item) => {
  if (!item?.session_id) return;
  sessionId.value = item.session_id;
  sessionTitle.value = item.title || '会话';
  showSessionList.value = false;
  // 加载历史消息回显
  messages.value = [];
  try {
    const res = await fetchAgentMessages(item.session_id);
    if (res?.status === 'success' && Array.isArray(res.data)) {
      for (const m of res.data) {
        if (m.role === 'user' || m.role === 'assistant') {
          messages.value.push({
            id: `${m.role}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
            role: m.role,
            text: m.content,
            time: '',
            intent: null,
            iterations: null,
            events: [],
          });
        }
      }
    }
  } catch (error) {
    console.warn('加载会话消息失败', error);
  }
  if (messages.value.length === 0) {
    messages.value.push({
      id: `hello-${Date.now()}`,
      role: 'assistant',
      text: '继续上次的对话吧，有什么可以帮您？',
      time: new Date().toLocaleTimeString(),
      intent: null,
      iterations: null,
      events: [],
    });
  }
  await scrollToBottom();
};

const removeSession = async (item, event) => {
  if (event) event.stopPropagation();
  if (!item?.session_id) return;
  try {
    await deleteAgentSession(item.session_id);
    sessions.value = sessions.value.filter((s) => s.session_id !== item.session_id);
    if (sessionId.value === item.session_id) {
      await newSession();
    }
    ElMessage.success('会话已删除');
  } catch (error) {
    ElMessage.error('删除会话失败');
    console.warn(error);
  }
};

let mediaStream = null;
let audioContext = null;
let processorNode = null;
let sourceNode = null;
let latestAudio = null;
let asrClient = null;
const targetSampleRate = 16000;

const togglePanel = () => {
  isOpen.value = !isOpen.value;
};

const togglePanelMode = () => {
  panelMode.value = panelMode.value === 'normal' ? 'large' : 'normal';
};

// 跳转收银台：把语音下单草稿写入 sessionStorage，收银台 onMounted 读取预填购物车
// card 可传入指定订单卡片；默认用最新的 voiceOrder
const goToCheckout = (card) => {
  const order = card || voiceOrder.value;
  if (!order) return;
  const items = Array.isArray(order.items) ? order.items : [];
  if (items.length === 0) {
    ElMessage.warning('订单草稿没有可用商品');
    return;
  }
  const pending = {
    items: items
      .filter((it) => it && it.matched !== false && it.product_id != null)
      .map((it) => ({
        productId: it.product_id,
        productName: it.product_name,
        productCode: it.product_code || '',
        barcode: it.barcode || '--',
        price: Number(it.price) || 0,
        quantity: Number(it.quantity) || 1,
        stock: Number(it.stock_quantity) || 0,
      })),
    totalAmount: Number(order.total_amount) || 0,
    member: order.member || null,
    createdAt: Date.now(),
  };
  if (pending.items.length === 0) {
    ElMessage.warning('没有匹配到的商品，无法生成订单');
    return;
  }
  sessionStorage.setItem('pendingVoiceOrder', JSON.stringify(pending));
  isOpen.value = false;
  router.push('/checkout');
};

const genId = (role) => `${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`;

const appendMessage = (role, text, extra = {}) => {
  messages.value.push({
    id: genId(role),
    role,
    text,
    time: new Date().toLocaleTimeString(),
    intent: null,
    iterations: null,
    events: [],
    ...extra,
  });
  return messages.value[messages.value.length - 1];
};

const scrollToBottom = async () => {
  await nextTick();
  if (messageContainer.value) {
    messageContainer.value.scrollTop = messageContainer.value.scrollHeight;
  }
};

const startVoiceInput = async () => {
  if (recording.value) {
    stopVoiceCapture(true);
    return;
  }
  if (!navigator?.mediaDevices?.getUserMedia) {
    ElMessage.error('当前浏览器不支持录音功能');
    return;
  }
  try {
    recognitionStatus.value = '正在连接语音服务...';
    asrClient = await createStreamingAsrClient(
      ({ text, isFinal }) => {
        if (text) {
          voiceDraft.value = text;
          if (isFinal) recognitionStatus.value = '识别完成';
        }
      },
      (error) => {
        ElMessage.error(error?.message || '语音识别出错');
        stopVoiceCapture(false);
      },
      ({ text }) => {
        if (text) {
          inputText.value = inputText.value ? `${inputText.value} ${text}` : text;
        }
      },
    );
    await setupAudioGraph();
    recording.value = true;
    recognitionStatus.value = '正在聆听，请开始说话';
    voiceDraft.value = '';
  } catch (error) {
    cleanupAudio();
    recognitionStatus.value = '';
    recording.value = false;
    ElMessage.error(error?.message || '语音识别服务不可用');
  }
};

const setupAudioGraph = async () => {
  mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
  const AudioContextClass =
    (isClient && (window.AudioContext || window.webkitAudioContext)) || null;
  if (!AudioContextClass) throw new Error('当前环境无法创建音频上下文');
  audioContext = new AudioContextClass({ sampleRate: targetSampleRate });
  if (audioContext.state === 'suspended') await audioContext.resume();
  sourceNode = audioContext.createMediaStreamSource(mediaStream);
  processorNode = audioContext.createScriptProcessor(4096, 1, 1);
  sourceNode.connect(processorNode);
  processorNode.connect(audioContext.destination);
  processorNode.onaudioprocess = (event) => {
    if (!recording.value) return;
    const inputData = event.inputBuffer.getChannelData(0);
    const downsampled = downSampleBuffer(inputData, audioContext.sampleRate, targetSampleRate);
    if (!downsampled) return;
    const int16Data = convertFloat32ToInt16(downsampled);
    if (asrClient && asrClient.isConnected) asrClient.sendAudio(int16Data);
  };
};

const downSampleBuffer = (buffer, sampleRate, outRate) => {
  if (outRate === sampleRate) return buffer;
  if (outRate > sampleRate) return null;
  const ratio = sampleRate / outRate;
  const newLength = Math.round(buffer.length / ratio);
  const result = new Float32Array(newLength);
  let offR = 0;
  let offB = 0;
  while (offR < result.length) {
    const nextB = Math.round((offR + 1) * ratio);
    let accum = 0;
    let count = 0;
    for (let i = offB; i < nextB && i < buffer.length; i++) {
      accum += buffer[i];
      count++;
    }
    result[offR] = accum / (count || 1);
    offR++;
    offB = nextB;
  }
  return result;
};

const convertFloat32ToInt16 = (buffer) => {
  const l = buffer.length;
  const result = new Int16Array(l);
  for (let i = 0; i < l; i++) {
    const s = Math.max(-1, Math.min(1, buffer[i]));
    result[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
  }
  return result;
};

const stopVoiceCapture = async (sendEnd = true) => {
  if (!recording.value && !mediaStream) return;
  recording.value = false;
  if (sendEnd && asrClient && asrClient.isConnected) asrClient.end();
  if (processorNode) {
    processorNode.disconnect();
    processorNode.onaudioprocess = null;
    processorNode = null;
  }
  if (sourceNode) {
    sourceNode.disconnect();
    sourceNode = null;
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach((track) => track.stop());
    mediaStream = null;
  }
  if (audioContext) {
    audioContext.close();
    audioContext = null;
  }
  if (!sendEnd && asrClient) {
    asrClient.close();
    asrClient = null;
  }
  recognitionStatus.value = '';
  voiceDraft.value = '';
};

const cleanupAudio = () => {
  if (processorNode) {
    processorNode.disconnect();
    processorNode.onaudioprocess = null;
  }
  if (sourceNode) sourceNode.disconnect();
  if (mediaStream) mediaStream.getTracks().forEach((track) => track.stop());
  if (audioContext) audioContext.close();
  if (asrClient) asrClient.close();
  processorNode = null;
  sourceNode = null;
  mediaStream = null;
  audioContext = null;
  asrClient = null;
};

const toggleVoice = async () => {
  if (recording.value) await stopVoiceCapture(true);
  else await startVoiceInput();
};

const cleanupAgentText = (text) => {
  if (!text) return '';
  let formatted = String(text);
  formatted = formatted
    .replace(/\\r\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\r\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n');
  return formatted.trim();
};

const isLikelyNetworkIssue = (error) => {
  if (!error) return false;
  const message = String(error?.message || '').toLowerCase();
  return (
    message.includes('network') ||
    message.includes('timeout') ||
    error?.code === 'ERR_NETWORK' ||
    error?.code === 'ECONNABORTED'
  );
};

const safeCheckAgentHealth = async () => {
  try {
    await checkAgentHealth();
  } catch (error) {
    console.warn('Agent health check failed', error);
  }
};

const reloadApplication = () => {
  if (isClient) window.location.reload();
};

const showNetworkRecoveryAttempt = (attempt) => {
  networkRecoveryInProgress.value = true;
  networkRecoveryFailed.value = false;
  networkRecoveryAttempt.value = attempt;
  const labels = { 1: '网络异常，正在重连 (1/3)', 2: '正在重连服务 (2/3)' };
  networkRecoveryMessage.value =
    labels[attempt] || `网络重连 (${attempt}/${maxNetworkRecoveryAttempts})`;
};

const markNetworkRecoverySuccess = () => {
  networkRecoveryInProgress.value = false;
  networkRecoveryFailed.value = false;
  networkRecoveryAttempt.value = 0;
  networkRecoveryMessage.value = '连接已恢复，可以继续询问 AI';
  setTimeout(() => {
    networkRecoveryMessage.value = '';
  }, 2500);
};

const markNetworkRecoveryFailure = () => {
  networkRecoveryInProgress.value = false;
  networkRecoveryFailed.value = true;
  networkRecoveryAttempt.value = maxNetworkRecoveryAttempts;
  networkRecoveryMessage.value = '网络异常，请等待网络维修中 (3/3)';
};

const resetNetworkRecoveryState = () => {
  networkRecoveryInProgress.value = false;
  networkRecoveryFailed.value = false;
  networkRecoveryAttempt.value = 0;
  networkRecoveryMessage.value = '';
};

const sendAgentQueryWithRecovery = async (content, sessionId) => {
  let lastError = null;
  for (let i = 0; i < maxNetworkRecoveryAttempts; i++) {
    try {
      if (i > 0) {
        showNetworkRecoveryAttempt(i);
        await safeCheckAgentHealth();
        await wait(1000);
      }
      const response = await sendAgentQuery(content, { maxIterations: 8, sessionId });
      if (i > 0) markNetworkRecoverySuccess();
      else resetNetworkRecoveryState();
      return response;
    } catch (error) {
      lastError = error;
      const shouldRetry =
        isLikelyNetworkIssue(error) && i < maxNetworkRecoveryAttempts - 1;
      if (!shouldRetry) {
        if (isLikelyNetworkIssue(error)) markNetworkRecoveryFailure();
        else resetNetworkRecoveryState();
        break;
      }
    }
  }
  throw lastError;
};

// ============ 事件流渲染辅助 ============
const eventLabel = (type) => {
  const map = {
    intent_detected: '意图识别',
    iteration: '推理步',
    thinking: '思考',
    tool_call: '工具调用',
    tool_result: '观察结果',
    answer: '最终回答',
    warning: '提示',
    error: '错误',
    max_iterations_reached: '达到最大步数',
  };
  return map[type] || type;
};

const eventIcon = (type) => {
  const map = {
    intent_detected: '🎯',
    iteration: '🧭',
    thinking: '💭',
    tool_call: '🛠',
    tool_result: '👁',
    answer: '✅',
    warning: '⚠',
    error: '❌',
    max_iterations_reached: '🛑',
  };
  return map[type] || '•';
};

const eventClass = (type) => `agent-event agent-event--${type}`;

const summarizeToolArguments = (args) => {
  if (!args || typeof args !== 'object') return '';
  const parts = [];
  if (args.keyword) parts.push(`关键字=${args.keyword}`);
  if (args.suffix) parts.push(`尾号=${args.suffix}`);
  if (args.metric) parts.push(`指标=${args.metric}`);
  if (args.days) parts.push(`天数=${args.days}`);
  if (args.limit) parts.push(`条数=${args.limit}`);
  if (args.member_id) parts.push(`会员=${args.member_id}`);
  if (args.order_no) parts.push(`订单号=${args.order_no}`);
  if (Array.isArray(args.items) && args.items.length) {
    parts.push(`商品=${args.items.length}项`);
  }
  return parts.length ? parts.join('，') : JSON.stringify(args);
};

const summarizeToolResult = (result) => {
  if (!result) return '';
  try {
    const obj = typeof result === 'string' ? JSON.parse(result) : result;
    if (obj.status === 'error') return `失败：${obj.message || ''}`;
    const data = obj.data;
    const count = obj.count ?? (Array.isArray(data) ? data.length : null);
    if (count != null) return `成功，共 ${count} 条`;
    if (data && data.total_amount != null) {
      const lines = (data.items || []).length;
      return `成功：${lines} 项商品，合计 ¥${Number(data.total_amount).toFixed(2)}`;
    }
    return '成功';
  } catch {
    return String(result).slice(0, 80);
  }
};

const formatIntent = (intent) => {
  if (!intent) return '';
  const pct = intent.confidence != null ? `${Math.round(intent.confidence * 100)}%` : '';
  return `${intent.label || intent.primary || ''} ${pct}`.trim();
};

const totalSteps = (events) => {
  if (!Array.isArray(events)) return 0;
  return events.filter((e) => e.type === 'iteration').length;
};

const toggleTrace = (id) => {
  traceExpanded.value[id] = !traceExpanded.value[id];
};

const runQuickAction = (action) => {
  inputText.value = action.prompt;
  sendMessage();
};

const sendMessage = async () => {
  if (!inputText.value.trim()) {
    ElMessage.warning('请输入要咨询的内容');
    return;
  }
  const content = inputText.value.trim();
  const sid = await ensureSession(); // 确保会话存在（多轮记忆）
  appendMessage('user', content);
  inputText.value = '';
  voiceOrder.value = null;
  sending.value = true;
  const pending = appendMessage('assistant', '正在分析您的请求…', {
    pending: true,
  });

  let streamUsed = false;
  let lastError = null;
  for (let attempt = 0; attempt < maxNetworkRecoveryAttempts; attempt++) {
    try {
      const meta = await new Promise((resolve, reject) => {
        let hasDelta = false;
        sendAgentQueryStream(content, {
          maxIterations: 8,
          sessionId: sid,
          onDelta: (delta) => {
            streamUsed = true;
            hasDelta = true;
            pending.pending = false;
            pending.text =
              pending.text === '正在分析您的请求…' ? delta : pending.text + delta;
            scrollToBottom();
          },
          onMeta: (metaData) => {
            if (metaData?.status === 'success') {
              resolve(metaData);
            } else {
              reject(new Error(metaData?.summary || '智能体服务异常'));
            }
          },
          onError: (error) => {
            if (!hasDelta) {
              reject(error);
            } else {
              // 已输出部分内容，忽略中断错误，交由 onMeta 收尾
            }
          },
        }).catch((error) => {
          // fetch 层错误：网络/超时/HTTP 状态
          reject(error);
        });
      });

      // 流式成功：把元数据（事件/意图/迭代/订单卡片）挂上
      if (streamUsed) {
        pending.pending = false;
        pending.text = cleanupAgentText(meta.summary) || pending.text;
        pending.events = Array.isArray(meta.events) ? meta.events : [];
        pending.intent = meta.intent || null;
        pending.iterations = meta.iterations ?? totalSteps(pending.events);
        voiceOrder.value = meta.voice_order || null;
        if (meta.voice_order) {
          pending.orderCard = meta.voice_order;
        }
        await scrollToBottom();
        await playAssistantSpeech(pending.text);
        sessionTitle.value = meta?.summary ? (sessionTitle.value === '新会话' ? content.slice(0, 12) : sessionTitle.value) : sessionTitle.value;
        loadSessions(); // 异步刷新历史会话列表（标题由后端总结）
        sending.value = false;
        return;
      }

      // 流式不可用时降级到普通查询
      const { data } = await sendAgentQueryWithRecovery(content, sid);
      pending.pending = false;

      if (data?.status !== 'success') {
        throw new Error(data?.message || '智能体服务异常');
      }

      const payload = data.data || {};
      pending.text =
        cleanupAgentText(payload.summary) || '我已经收到您的问题，稍后处理。';
      pending.events = Array.isArray(payload.events) ? payload.events : [];
      pending.intent = payload.intent || null;
      pending.iterations = payload.iterations ?? totalSteps(pending.events);

      // 语音下单草稿：渲染订单卡片（voice_order 为后端不截断的完整草稿）
      voiceOrder.value = payload.voice_order || null;
      // 拼多多式：把订单卡片挂到当前助手消息上，在聊天流里内联展示
      if (payload.voice_order) {
        pending.orderCard = payload.voice_order;
      }

      await scrollToBottom();
      await playAssistantSpeech(pending.text);
      sessionTitle.value = sessionTitle.value === '新会话' ? content.slice(0, 12) : sessionTitle.value;
      loadSessions();
      sending.value = false;
      return;
    } catch (error) {
      lastError = error;
      // 仅对网络类错误做重连重试；流式已输出时直接结束
      const shouldRetry =
        !streamUsed && isLikelyNetworkIssue(error) && attempt < maxNetworkRecoveryAttempts - 1;
      if (shouldRetry) {
        showNetworkRecoveryAttempt(attempt + 1);
        await safeCheckAgentHealth();
        await wait(1000);
        continue;
      }
      break;
    }
  }

  // 全部失败：统一错误处理
  pending.pending = false;
  if (lastError?.isAuthError) {
    pending.text = lastError.authMessage || '请登录后使用';
    ElMessage.warning('请先登录后再使用智慧助手');
  } else if (!streamUsed) {
    pending.text = '抱歉，我暂时无法处理这个请求。';
    if (isLikelyNetworkIssue(lastError)) markNetworkRecoveryFailure();
    else resetNetworkRecoveryState();
    ElMessage.error(lastError?.message || '智能体接口调用失败');
  } else {
    resetNetworkRecoveryState();
  }
  sending.value = false;
  await scrollToBottom();
};

const stopSpeech = () => {
  if (latestAudio) {
    latestAudio.pause();
    latestAudio = null;
  }
  if (isClient && supportsSpeechSynthesis) {
    window.speechSynthesis.cancel();
  }
  isSpeaking.value = false;
};

const playAssistantSpeech = async (text) => {
  if (!text || !speechEnabled.value) return; // 关闭语音播报时不播
  stopSpeech();
  isSpeaking.value = true;
  try {
    const { data } = await synthesizeSpeech({ text });
    if (data.status !== 'success') throw new Error(data.message || '语音合成失败');
    const url = `data:audio/${data.format || 'mp3'};base64,${data.audio_base64}`;
    const audio = new Audio(url);
    latestAudio = audio;
    audio.onended = () => {
      isSpeaking.value = false;
      latestAudio = null;
    };
    await audio.play();
  } catch (error) {
    isSpeaking.value = false;
    latestAudio = null;
    if (supportsSpeechSynthesis) {
      const utter = new SpeechSynthesisUtterance(text);
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(utter);
    } else {
      console.warn('语音播报失败', error);
    }
  }
};

const handleKeySend = (event) => {
  // Enter 提交；Shift+Enter 换行
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault();
    sendMessage();
  }
};

const visibleEvents = (events) => {
  if (!Array.isArray(events)) return [];
  return events;
};

onBeforeUnmount(() => {
  stopVoiceCapture(false);
  cleanupAudio();
  if (latestAudio) {
    latestAudio.pause();
    latestAudio = null;
  }
});
</script>

<template>
  <div class="smart-agent">
    <transition name="agent-fade">
      <div v-if="isOpen" :class="['agent-panel', { 'agent-panel--large': panelMode === 'large' }]">
        <header class="agent-panel__header">
          <div>
            <div class="agent-panel__title">智慧收银助手 SmartPOS-AI</div>
            <div class="agent-panel__subtitle">
              {{ sessionTitle }} · 意图识别 · 多步推理 · 工具调用 · 语音收银
            </div>
          </div>
          <div class="agent-panel__header-actions">
            <span class="agent-panel__status" v-if="recording">聆听中...</span>
            <button class="agent-icon-button" :title="speechEnabled ? '关闭语音播报' : '开启语音播报'" @click="toggleSpeech">
              <span :class="{ 'agent-session-btn-active': speechEnabled }">{{ speechEnabled ? '🔊' : '🔇' }}</span>
            </button>
            <button class="agent-icon-button" :title="'历史会话'" @click="toggleSessionList">
              <span :class="{ 'agent-session-btn-active': showSessionList }">☰</span>
            </button>
            <button class="agent-icon-button" :title="'新建会话'" @click="newSession">＋</button>
            <button class="agent-icon-button" :title="panelMode === 'normal' ? '展开大窗口' : '收起为小窗口'" @click="togglePanelMode">
              <span v-if="panelMode === 'normal'">⤢</span>
              <span v-else>⤢</span>
            </button>
            <button class="agent-icon-button" @click="isOpen = false" aria-label="关闭">×</button>
          </div>
        </header>

        <!-- 历史会话列表 -->
        <div v-if="showSessionList" class="agent-session-list">
          <div class="agent-session-list__head">
            <span>历史会话</span>
            <button class="agent-session-new" @click="newSession">＋ 新建会话</button>
          </div>
          <div v-if="sessions.length === 0" class="agent-session-empty">暂无历史会话</div>
          <div
            v-for="s in sessions"
            :key="s.session_id"
            class="agent-session-item"
            :class="{ 'agent-session-item--active': s.session_id === sessionId }"
            @click="openSession(s)"
          >
            <span class="agent-session-item__title">{{ s.title || '未命名会话' }}</span>
            <span class="agent-session-item__meta">{{ s.message_count || 0 }} 条</span>
            <button class="agent-session-item__del" :title="'删除会话'" @click.stop="removeSession(s, $event)">×</button>
          </div>
        </div>

        <div class="agent-quick">
          <button
            v-for="action in quickActions"
            :key="action.label"
            class="agent-quick__btn"
            :disabled="sending"
            @click="runQuickAction(action)"
          >
            {{ action.label }}
          </button>
        </div>

        <div class="agent-messages" ref="messageContainer">
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['agent-bubble', msg.role]"
          >
            <div class="agent-bubble__text">
              <span v-if="msg.pending" class="agent-bubble__pending">
                <span class="agent-dot" />正在分析您的请求…
              </span>
              <template v-else>{{ msg.text }}</template>
            </div>

            <!-- 拼多多式：订单卡片内联在助手消息里 -->
            <div
              v-if="msg.role === 'assistant' && msg.orderCard"
              class="agent-order-card agent-order-card--inline"
              @click="goToCheckout(msg.orderCard)"
            >
              <div class="agent-order-card__head">
                <span class="agent-order-card__title">🧾 订单</span>
                <span class="agent-order-card__badge">待支付</span>
              </div>
              <div class="agent-order-card__items">
                <div
                  v-for="(it, idx) in msg.orderCard.items"
                  :key="idx"
                  class="agent-order-card__row"
                  :class="{ 'agent-order-card__row--miss': it.matched === false }"
                >
                  <span class="ao-name">{{ it.product_name }}</span>
                  <span class="ao-qty">×{{ it.quantity }}</span>
                  <span class="ao-price">¥{{ Number(it.subtotal || it.price || 0).toFixed(2) }}</span>
                </div>
              </div>
              <div class="agent-order-card__total">
                <span>支付金额</span>
                <span class="ao-total">¥{{ Number(msg.orderCard.total_amount || 0).toFixed(2) }}</span>
              </div>
              <div v-if="msg.orderCard.warnings && msg.orderCard.warnings.length" class="agent-order-card__warn">
                <div v-for="(w, i) in msg.orderCard.warnings" :key="i">⚠ {{ w }}</div>
              </div>
              <div class="agent-order-card__cta">点击去收银台支付 →</div>
            </div>

            <div v-if="msg.role === 'assistant' && (msg.intent || msg.iterations != null)" class="agent-bubble__meta">
              <span v-if="msg.intent" class="agent-meta-tag agent-meta-tag--intent">
                🎯 {{ formatIntent(msg.intent) }}
              </span>
              <span v-if="msg.iterations != null" class="agent-meta-tag agent-meta-tag--iter">
                🧭 推理 {{ msg.iterations }} 步
              </span>
              <span
                v-if="msg.events && msg.events.length"
                class="agent-meta-tag agent-meta-tag--toggle"
                @click="toggleTrace(msg.id)"
              >
                {{ traceExpanded[msg.id] ? '▼ 收起轨迹' : '▶ 展开轨迹' }}
              </span>
            </div>

            <div
              v-if="msg.role === 'assistant' && msg.events && msg.events.length && traceExpanded[msg.id]"
              class="agent-trace"
            >
              <div
                v-for="(evt, idx) in visibleEvents(msg.events)"
                :key="idx"
                :class="eventClass(evt.type)"
              >
                <span class="agent-event__icon">{{ eventIcon(evt.type) }}</span>
                <span class="agent-event__label">{{ eventLabel(evt.type) }}</span>
                <span class="agent-event__body">
                  <template v-if="evt.type === 'intent_detected'">
                    {{ formatIntent(evt.intent) }}
                  </template>
                  <template v-else-if="evt.type === 'iteration'">
                    第 {{ evt.iteration }} 步
                  </template>
                  <template v-else-if="evt.type === 'thinking'">
                    {{ evt.content }}
                  </template>
                  <template v-else-if="evt.type === 'tool_call'">
                    <strong>{{ evt.name }}</strong>
                    <span v-if="summarizeToolArguments(evt.arguments)">
                      （{{ summarizeToolArguments(evt.arguments) }}）
                    </span>
                  </template>
                  <template v-else-if="evt.type === 'tool_result'">
                    <span :class="evt.ok ? 'agent-event__ok' : 'agent-event__err'">
                      {{ evt.name }}：{{ summarizeToolResult(evt.result) }}
                    </span>
                  </template>
                  <template v-else-if="evt.type === 'answer'">
                    {{ evt.content }}
                  </template>
                  <template v-else-if="evt.type === 'warning'">
                    {{ evt.message }}
                  </template>
                  <template v-else-if="evt.type === 'error'">
                    {{ evt.message }}
                  </template>
                  <template v-else-if="evt.type === 'max_iterations_reached'">
                    已达最大推理步数 {{ evt.max_iterations }}，停止迭代
                  </template>
                </span>
              </div>
            </div>

            <div class="agent-bubble__time">{{ msg.time }}</div>
          </div>
        </div>

        <div v-if="recognitionStatus" class="agent-voice-status">
          <span>{{ recognitionStatus }}</span>
          <span v-if="voiceDraft" class="agent-voice-draft">识别：{{ voiceDraft }}</span>
        </div>

        <div
          v-if="networkRecoveryMessage"
          class="agent-network-status"
          :class="{ 'agent-network-status--error': networkRecoveryFailed }"
        >
          <span>{{ networkRecoveryMessage }}</span>
          <el-button v-if="networkRecoveryFailed" size="small" type="warning" @click="reloadApplication">
            刷新页面
          </el-button>
        </div>

        <div class="agent-input">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="例如：尾号3333的会员余额是多少？或 一句话下单：来2瓶可乐…"
            @keydown="handleKeySend"
          />
          <div class="agent-input__actions">
            <el-button
              size="small"
              :type="recording ? 'danger' : 'info'"
              @click="toggleVoice"
            >
              {{ recording ? '结束录音' : '语音输入' }}
            </el-button>
            <el-button
              size="small"
              type="primary"
              :loading="sending"
              :disabled="sending || networkRecoveryInProgress"
              @click="sendMessage"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>
    </transition>

    <button class="agent-fab" @click="togglePanel" aria-label="智能助手">
      <span v-if="!isOpen">🤖</span>
      <span v-else>×</span>
      <span class="agent-fab__text">助手</span>
    </button>
  </div>
</template>

<style scoped>
.smart-agent {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 2000;
}

.agent-panel {
  width: 440px;
  height: 620px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(238, 242, 255, 0.96));
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.22);
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
  overflow: hidden;
}

/* 展开模式：大窗口，看清完整调用轨迹与介绍 */
.agent-panel--large {
  width: min(880px, 92vw);
  height: min(800px, 88vh);
}

.agent-panel__header {
  padding: 16px 18px;
  background: var(--pos-gradient-dark);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
}
.agent-panel__header::after {
  content: '';
  position: absolute; left: 0; right: 0; bottom: 0; height: 1px;
  background: linear-gradient(90deg, transparent, rgba(6,182,212,0.6), transparent);
}
.agent-panel__title {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 8px;
}
.agent-panel__title::before {
  content: '';
  width: 8px; height: 8px; border-radius: 50%;
  background: #06B6D4;
  box-shadow: 0 0 8px #06B6D4;
  animation: pos-ai-pulse 1.6s ease-in-out infinite;
}
.agent-panel__subtitle {
  font-size: 12px;
  color: rgba(199, 210, 254, 0.85);
  margin-top: 4px;
}
.agent-panel__header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.agent-panel__status {
  font-size: 12px;
  color: #FDE68A;
  font-weight: 600;
}

.agent-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px;
  background: linear-gradient(180deg, #EEF2FF, #F8FAFC);
  border-bottom: 1px solid var(--pos-border, #E5E7EB);
}
.agent-quick__btn {
  border: 1px solid rgba(79, 70, 229, 0.25);
  background: #fff;
  color: var(--pos-primary, #4F46E5);
  border-radius: 999px;
  font-size: 12px;
  padding: 3px 10px;
  cursor: pointer;
  transition: all 0.18s ease;
}
.agent-quick__btn:hover:not(:disabled) {
  background: var(--pos-gradient-brand, linear-gradient(135deg, #4F46E5, #06B6D4));
  color: #fff;
  border-color: transparent;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.28);
}
.agent-quick__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.agent-messages {
  flex: 1;
  padding: 14px 16px;
  overflow-y: auto;
  background: linear-gradient(180deg, #F8FAFC, #EEF2FF);
}
.agent-messages::-webkit-scrollbar { width: 6px; }
.agent-messages::-webkit-scrollbar-thumb { background: rgba(79,70,229,0.2); border-radius: 3px; }

.agent-bubble {
  max-width: 88%;
  margin-bottom: 12px;
  padding: 11px 14px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.6;
  position: relative;
  color: var(--pos-text, #1F2937);
}
.agent-bubble.user {
  margin-left: auto;
  background: var(--pos-gradient-brand, linear-gradient(135deg, #4F46E5, #06B6D4));
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.28);
}
.agent-bubble.assistant {
  margin-right: auto;
  background: linear-gradient(135deg, #EEF2FF 0%, #F0F9FF 100%);
  border: 1px solid rgba(99, 102, 241, 0.22);
  border-bottom-left-radius: 4px;
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.10);
  color: #1E293B;
}
.agent-bubble__text {
  white-space: pre-wrap;
  word-break: break-word;
}
.agent-bubble__pending {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgba(79, 70, 229, 0.8);
}
.agent-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: #4F46E5;
  animation: pos-ai-pulse 1.2s ease-in-out infinite;
}

.agent-bubble__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.agent-meta-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #EEF2FF;
  color: #4338CA;
  border: 1px solid rgba(79, 70, 229, 0.2);
}
.agent-meta-tag--intent { background: #ECFEFF; color: #0E7490; border-color: rgba(6,182,212,0.3); }
.agent-meta-tag--iter { background: #F5F3FF; color: #6D28D9; border-color: rgba(139,92,246,0.3); }
.agent-meta-tag--toggle {
  cursor: pointer;
  background: #fff7ed;
  color: #B45309;
  border-color: rgba(245,158,11,0.3);
  user-select: none;
}
.agent-meta-tag--toggle:hover { background: #FEF3C7; }

.agent-trace {
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  background: linear-gradient(180deg, #F8FAFC, #F1F5F9);
  border: 1px dashed rgba(99, 102, 241, 0.25);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.agent-event {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #475569;
}
.agent-event__icon { flex: 0 0 auto; }
.agent-event__label {
  flex: 0 0 auto;
  font-weight: 600;
  color: #1E293B;
  background: rgba(148,163,184,0.18);
  padding: 0 6px;
  border-radius: 4px;
}
.agent-event__body { flex: 1; }
.agent-event__ok { color: #047857; }
.agent-event__err { color: #B91C1C; }
.agent-event--tool_call .agent-event__label { color: #4338CA; background: rgba(199,210,254,0.4); }
.agent-event--tool_result .agent-event__label { color: #0E7490; background: rgba(165,243,252,0.4); }
.agent-event--intent_detected .agent-event__label { color: #B45309; background: rgba(254,215,170,0.45); }
.agent-event--iteration .agent-event__label { color: #6D28D9; background: rgba(221,214,254,0.55); }
.agent-event--thinking .agent-event__body { color: #6B21A8; font-style: italic; }
.agent-event--answer .agent-event__body { color: #065F46; font-weight: 500; }
.agent-event--warning .agent-event__label,
.agent-event--error .agent-event__label,
.agent-event--max_iterations_reached .agent-event__label { color: #B91C1C; background: rgba(254,202,202,0.5); }

.agent-bubble__time {
  font-size: 11px;
  opacity: 0.55;
  margin-top: 6px;
}

.agent-voice-status {
  padding: 9px 16px;
  font-size: 12px;
  color: var(--pos-primary, #4F46E5);
  font-weight: 600;
  border-top: 1px dashed rgba(79, 70, 229, 0.25);
  background: linear-gradient(90deg, #EEF2FF, #F8FAFC);
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.agent-voice-draft {
  color: var(--pos-text-soft, #6B7280);
  font-weight: 400;
}

.agent-network-status {
  padding: 9px 16px;
  font-size: 12px;
  color: #B45309;
  border-top: 1px dashed #FCD34D;
  background: #FFFBEB;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.agent-network-status--error {
  color: #B91C1C;
  border-color: #FCA5A5;
  background: #FEF2F2;
}

.agent-input {
  padding: 12px 16px 16px;
  border-top: 1px solid var(--pos-border, #E5E7EB);
  background: #fff;
}
.agent-input__actions {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
}

/* ============ 语音下单订单卡片 ============ */
.agent-order-card {
  margin: 4px 14px 12px;
  border-radius: 14px;
  border: 1px solid rgba(79, 70, 229, 0.28);
  background: linear-gradient(180deg, #F5F3FF, #EEF2FF);
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.14);
  overflow: hidden;
  font-size: 13px;
}
/* 拼多多式：内联在助手消息气泡里的订单卡片，整体可点击 */
.agent-order-card--inline {
  margin: 8px 0 10px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  border: 1px solid rgba(79, 70, 229, 0.35);
}
.agent-order-card--inline:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 26px rgba(79, 70, 229, 0.24);
}
.agent-order-card__cta {
  padding: 8px 14px;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #4F46E5, #06B6D4);
  letter-spacing: 0.5px;
}
.agent-order-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: linear-gradient(135deg, #4F46E5, #06B6D4);
  color: #fff;
}
.agent-order-card__title { font-weight: 700; font-size: 14px; }
.agent-order-card__badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255,255,255,0.25);
}
.agent-order-card__items { padding: 6px 12px; }
.agent-order-card__row {
  display: grid;
  grid-template-columns: 1fr auto auto auto;
  gap: 8px;
  align-items: center;
  padding: 7px 4px;
  border-bottom: 1px dashed rgba(99,102,241,0.18);
}
.agent-order-card__row:last-child { border-bottom: none; }
.agent-order-card__row--miss .ao-name { color: #B91C1C; text-decoration: line-through; }
.ao-name { color: #1F2937; }
.ao-qty { color: #4338CA; font-weight: 600; }
.ao-price { color: #0F172A; font-weight: 700; }
.ao-stock { font-size: 11px; }
.ao-ok { color: #047857; }
.ao-warn { color: #B45309; }
.agent-order-card__total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 9px 14px;
  background: #fff;
  border-top: 1px solid rgba(99,102,241,0.18);
  font-size: 15px;
}
.ao-total { color: #DC2626; font-weight: 800; font-size: 17px; }
.agent-order-card__member {
  padding: 7px 14px;
  background: #F0F9FF;
  color: #0E7490;
  font-size: 12px;
  border-top: 1px solid #BAE6FD;
}
.agent-order-card__warn {
  padding: 7px 14px;
  background: #FFFBEB;
  color: #B45309;
  font-size: 12px;
  border-top: 1px solid #FDE68A;
}
.agent-order-card__warn div { line-height: 1.6; }
.agent-order-card__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #fff;
  border-top: 1px solid rgba(99,102,241,0.18);
}
.agent-order-card__hint {
  font-size: 11px;
  color: #6B7280;
  line-height: 1.5;
}

.agent-fab {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  border: none;
  background: var(--pos-gradient-brand, linear-gradient(135deg, #4F46E5, #06B6D4));
  color: #fff;
  font-size: 24px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 14px 30px rgba(79, 70, 229, 0.45);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.agent-fab::before {
  content: '';
  position: absolute; inset: -4px; border-radius: 50%;
  background: var(--pos-gradient-brand, linear-gradient(135deg, #4F46E5, #06B6D4));
  opacity: 0.35; filter: blur(10px);
  animation: pos-ai-pulse 2.4s ease-in-out infinite;
  z-index: -1;
}
.agent-fab__text {
  font-size: 11px;
  margin-top: 2px;
  letter-spacing: 1px;
}
.agent-fab:hover {
  transform: translateY(-3px) scale(1.04);
  box-shadow: 0 18px 36px rgba(79, 70, 229, 0.55);
}

.agent-icon-button {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  cursor: pointer;
  font-size: 18px;
  line-height: 28px;
  text-align: center;
}
.agent-icon-button:hover { background: rgba(255, 255, 255, 0.3); }
.agent-session-btn-active { color: #A5B4FC; font-weight: 700; }

/* ============ 历史会话列表 ============ */
.agent-session-list {
  background: linear-gradient(135deg, #0F172A 0%, #1E1B4B 60%, #312E81 100%);
  border-bottom: 1px solid rgba(99, 102, 241, 0.3);
  padding: 10px 14px;
  max-height: 220px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.agent-session-list__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #E2E8F0;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 2px;
}
.agent-session-new {
  border: 1px solid rgba(129, 140, 248, 0.5);
  background: rgba(129, 140, 248, 0.15);
  color: #C7D2FE;
  border-radius: 6px;
  padding: 2px 10px;
  font-size: 12px;
  cursor: pointer;
}
.agent-session-new:hover { background: rgba(129, 140, 248, 0.3); color: #fff; }
.agent-session-empty { color: #94A3B8; font-size: 12px; text-align: center; padding: 10px 0; }
.agent-session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.2s ease;
}
.agent-session-item:hover { background: rgba(255, 255, 255, 0.12); }
.agent-session-item--active { border-color: rgba(129, 140, 248, 0.6); background: rgba(129, 140, 248, 0.15); }
.agent-session-item__title {
  flex: 1;
  color: #E2E8F0;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.agent-session-item__meta { color: #94A3B8; font-size: 11px; flex-shrink: 0; }
.agent-session-item__del {
  border: none;
  background: transparent;
  color: #94A3B8;
  font-size: 14px;
  cursor: pointer;
  flex-shrink: 0;
  line-height: 1;
}
.agent-session-item__del:hover { color: #FCA5A5; }

.agent-fade-enter-active,
.agent-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.agent-fade-enter-from,
.agent-fade-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.96);
}
</style>