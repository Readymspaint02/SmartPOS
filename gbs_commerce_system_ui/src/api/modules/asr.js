import http from '../request';

export function recognizeSpeech(data) {
  return http('/asr/recognize', { method: 'POST', data });
}

function resolveWsUrl() {
  if (typeof window === 'undefined') return '';
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const baseUrl = import.meta.env.VITE_WS_URL || `${proto}//${window.location.host}/shop/api/asr/stream`;
  return baseUrl;
}

export class StreamingAsrClient {
  constructor(options = {}) {
    this.wsUrl = options.wsUrl || resolveWsUrl();
    this.ws = null;
    this.onResult = options.onResult || (() => {});
    this.onError = options.onError || (() => {});
    this.onEnd = options.onEnd || (() => {});
    this.onOpen = options.onOpen || (() => {});
    this.isConnected = false;
    this.resultBuffer = '';
  }

  connect() {
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(this.wsUrl);
        this.ws.binaryType = 'arraybuffer';

        this.ws.onopen = () => {
          this.isConnected = true;
          this.ws.send(JSON.stringify({
            command: 'START',
            config: {
              audio_format: 'pcm16k16bit',
              property: 'chinese_16k_general',
              add_punc: 'yes',
              interim_results: 'yes'
            }
          }));
          this.onOpen();
          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            if (data.resp_type === 'RESULT') {
              const text = data.text || '';
              const isFinal = data.is_final || false;
              if (text) {
                this.resultBuffer = text;
                this.onResult({ text, isFinal });
              }
            } else if (data.resp_type === 'END') {
              this.onEnd({ text: this.resultBuffer });
              this.ws.close();
            } else if (data.resp_type === 'ERROR') {
              this.onError(new Error(data.error_msg || '识别错误'));
            }
          } catch (e) {
            console.warn('解析ASR响应失败', e, event.data);
          }
        };

        this.ws.onerror = (error) => {
          this.onError(error);
          reject(error);
        };

        this.ws.onclose = () => {
          this.isConnected = false;
        };
      } catch (error) {
        reject(error);
      }
    });
  }

  sendAudio(int16Data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const buffer = int16Data instanceof Int16Array
        ? int16Data.buffer
        : new Int16Array(int16Data).buffer;
      this.ws.send(buffer);
    }
  }

  end() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ command: 'END', cancel: false }));
    }
  }

  close() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.isConnected = false;
  }
}

export async function createStreamingAsrClient(onResult, onError, onEnd) {
  const client = new StreamingAsrClient({ onResult, onError, onEnd });
  await client.connect();
  return client;
}
