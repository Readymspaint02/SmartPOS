import os
import base64
import time
import threading
from typing import Dict, Any
from pathlib import Path
from urllib.parse import quote

from dotenv import load_dotenv
env_path = Path(__file__).parent / ".env.secrets"
# override=True：项目密钥优先于系统环境变量
if env_path.exists():
    load_dotenv(env_path, override=True)
else:
    load_dotenv(override=True)

import requests
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

app = FastAPI(title="Baidu TTS HTTP Proxy")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

BAIDU_TTS_API_KEY = os.getenv("BAIDU_TTS_API_KEY", "").strip()
BAIDU_TTS_SECRET_KEY = os.getenv("BAIDU_TTS_SECRET_KEY", "").strip()

# TTS 策略：baidu（百度云端，默认）或 local（本地离线引擎 pyttsx3）
TTS_PROVIDER = os.getenv("TTS_PROVIDER", "baidu").strip().lower()

TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
TTS_URL = "https://tsn.baidu.com/text2audio"

_token_lock = threading.Lock()
_cached_token = None
_cached_token_expire_at = 0.0


def get_access_token(force_refresh: bool = False) -> str:
    global _cached_token, _cached_token_expire_at
    if not BAIDU_TTS_API_KEY or not BAIDU_TTS_SECRET_KEY:
        raise RuntimeError(
            "请配置 BAIDU_TTS_API_KEY / BAIDU_TTS_SECRET_KEY 环境变量"
        )
    with _token_lock:
        now = time.time()
        if not force_refresh and _cached_token and now < _cached_token_expire_at:
            return _cached_token
        resp = requests.post(
            TOKEN_URL,
            data={
                "grant_type": "client_credentials",
                "client_id": BAIDU_TTS_API_KEY,
                "client_secret": BAIDU_TTS_SECRET_KEY,
            },
            timeout=15,
        )
        obj = resp.json()
        token = obj.get("access_token")
        if not token:
            raise RuntimeError(f"获取百度 access_token 失败: {obj}")
        expires_in = int(obj.get("expires_in", 2592000))
        _cached_token = token
        _cached_token_expire_at = now + expires_in - 300
        return token


def synthesize_audio(
    text: str,
    spd: int = 5,
    pit: int = 5,
    vol: int = 5,
    per: int = 0,
    aue: int = 3,
    cuid: str = "smart-cashier-tts",
) -> bytes:
    if not text:
        raise ValueError("text 不能为空")
    # tex 需做 2 次 urlencode，覆盖全部特殊字符
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Accept": "*/*",
    }

    def _build_body(token_value: str) -> str:
        return (
            f"tok={token_value}"
            f"&cuid={cuid}"
            f"&ctp=1"
            f"&lan=zh"
            f"&spd={max(0, min(15, int(spd)))}"
            f"&pit={max(0, min(15, int(pit)))}"
            f"&vol={max(0, min(15, int(vol)))}"
            f"&per={int(per)}"
            f"&aue={int(aue)}"
            f"&tex={quote(quote(text, safe=''), safe='')}"
        )

    resp = requests.post(
        TTS_URL, data=_build_body(get_access_token()),
        headers=headers, timeout=60,
    )
    ctype = resp.headers.get("Content-Type", "")
    # token 失效时百度会返回 application/json 错误体，刷新一次后重试
    if resp.status_code != 200 or not ctype.startswith("audio"):
        get_access_token(force_refresh=True)
        resp = requests.post(
            TTS_URL, data=_build_body(get_access_token()),
            headers=headers, timeout=60,
        )
        ctype = resp.headers.get("Content-Type", "")

    if not ctype.startswith("audio"):
        err_text = resp.text[:500]
        raise RuntimeError(f"百度 TTS 合成失败: Content-Type={ctype}, body={err_text}")

    return resp.content


def synthesize_audio_local(
    text: str,
    spd: int = 5,
    pit: int = 5,
    vol: int = 5,
    per: int = 0,
    aue: int = 3,
) -> bytes:
    if not text:
        raise ValueError("text 不能为空")
    try:
        import pyttsx3
    except ImportError:
        raise RuntimeError("本地 TTS 需要安装 pyttsx3: pip install pyttsx3")
    import tempfile
    engine = pyttsx3.init()
    # spd 0-15 映射到语速 100-300 词/分钟
    engine.setProperty("rate", 100 + max(0, min(15, int(spd))) * 13)
    engine.setProperty("volume", max(0, min(15, int(vol))) / 15.0)
    voices = engine.getProperty("voices")
    zh_voice = next((v for v in voices if "zh" in (v.id + v.name).lower()
                     or "chinese" in v.name.lower() or "huihui" in v.name.lower()), None)
    if zh_voice:
        engine.setProperty("voice", zh_voice.id)
    tmp = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
    tmp.close()
    try:
        engine.save_to_file(text, tmp.name)
        engine.runAndWait()
        with open(tmp.name, "rb") as f:
            data = f.read()
    finally:
        try:
            os.unlink(tmp.name)
        except OSError:
            pass
    if not data:
        raise RuntimeError("本地 TTS 合成结果为空，请检查系统语音（如 Windows 中文语音包）")
    return data


class TTSRequest(BaseModel):
    text: str
    spd: int = 5
    pit: int = 5
    vol: int = 5
    per: int = 0
    aue: int = 3


@app.post("/tts_synthesize")
async def tts_synthesize(req: TTSRequest) -> Dict[str, Any]:
    try:
        if TTS_PROVIDER == "local":
            audio_bytes = synthesize_audio_local(
                text=req.text,
                spd=req.spd,
                pit=req.pit,
                vol=req.vol,
                per=req.per,
                aue=getattr(req, "aue", 3),
            )
            audio_format = "wav"
        else:
            audio_bytes = synthesize_audio(
                text=req.text,
                spd=req.spd,
                pit=req.pit,
                vol=req.vol,
                per=req.per,
                aue=getattr(req, "aue", 3),
            )
            audio_format = "mp3"
        audio_base64 = base64.b64encode(audio_bytes).decode("utf-8")
        return {
            "status": "success",
            "provider": TTS_PROVIDER,
            "audio_base64": audio_base64,
            "format": audio_format,
            "size": len(audio_bytes),
            "spd": req.spd,
            "pit": req.pit,
            "vol": req.vol,
            "per": req.per,
        }
    except Exception as e:
        return {"status": "error", "message": str(e)}


@app.get("/health")
async def health():
    if TTS_PROVIDER == "local":
        try:
            import pyttsx3  # noqa: F401
            return {"status": "ok", "service": "TTS HTTP Proxy", "provider": "local"}
        except ImportError:
            return {"status": "error", "message": "provider=local 但未安装 pyttsx3"}
    if not BAIDU_TTS_API_KEY or not BAIDU_TTS_SECRET_KEY:
        return {"status": "error", "message": "未配置 BAIDU_TTS_* 密钥"}
    try:
        token = get_access_token()
        return {
            "status": "ok",
            "service": "TTS HTTP Proxy",
            "provider": "baidu",
            "token_preview": f"{token[:6]}...(len={len(token)})",
        }
    except Exception as e:
        return {"status": "error", "message": str(e)}


if __name__ == "__main__":
    import uvicorn

    port = int(os.getenv("TTS_PROXY_PORT", "8101"))
    print(f"Baidu TTS HTTP Proxy 启动在 http://0.0.0.0:{port}")
    uvicorn.run(app, host="0.0.0.0", port=port)