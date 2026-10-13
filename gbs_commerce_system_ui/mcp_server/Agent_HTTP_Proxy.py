"""智慧收银系统 · 智慧助手 Agent HTTP Proxy
================================================================
基于 DeepSeek 原生 function calling 的 agent-loop 智能体后端：
  1. 意图识别(Intent Recognition)：规则预分类 + LLM 复核，作为事件流首个事件
  2. 多步推理(agent-loop)：观察(tool_result) → 思考(thinking) → 调用下一工具，直到完成任务或达到最大迭代
  3. 事件流(Events)：每一步产出结构化 event，前端按事件渲染「推理轨迹」
  4. 业务结合(Business Tools)：会员/订单/商品/库存/经营分析/语音下单预览
  5. 语音收银 MVP：voice_order_preview 工具将一句话解析为订单草稿(只读校验，不扣库存不落单)

设计参考：
  - OpenAI/DeepSeek 原生 function calling(类 Hermes 严格 schema)
  - LangGraph / agent-loop 的「观察-思考-行动」范式
  - MCP(MCP Server, FastMCP 框架)与本 HTTP 网关解耦，工具同名同语义
"""
import os
import json
import re
import time
import uuid
import httpx
import redis
from typing import Any, AsyncIterator, Dict, List, Optional, Tuple
from pathlib import Path

from dotenv import load_dotenv
env_path = Path(__file__).parent / ".env.secrets"
# override=True：项目密钥优先于系统环境变量，避免系统里残留的失效 DEEPSEEK_API_KEY 导致 401
if env_path.exists():
    load_dotenv(env_path, override=True)
else:
    load_dotenv(override=True)

from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel
import jwt as pyjwt

from repositories import (
    MemberRepository,
    OrderRepository,
    AnalyticsRepository,
    ProductRepository,
    InventoryRepository,
)
from db_utils import get_connection, normalize_record


class AgentQuery(BaseModel):
    query: str
    max_iterations: Optional[int] = None
    # 多轮会话历史：前端按会话保存，每次发送最近若干轮 {role, content}（role=user/assistant）
    # 让 agent-loop 拥有上下文（如「他余额多少」可承接上一句「查尾号3333」）
    history: Optional[List[Dict[str, Any]]] = None
    # 会话ID：为空时自动新建；非空时加载 Redis 记忆实现多轮上下文
    session_id: Optional[str] = None


app = FastAPI(title="智慧收银系统 · 智慧助手 Agent Proxy")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

JWT_SECRET = os.getenv("JWT_SECRET", "")
if not JWT_SECRET or JWT_SECRET == "CHANGE_ME":
    raise ValueError("JWT_SECRET environment variable must be configured with a non-placeholder secret")
security = HTTPBearer(auto_error=False)


def verify_token(credentials: HTTPAuthorizationCredentials = Depends(security)) -> Dict[str, Any]:
    if credentials is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="未提供认证令牌")
    token = credentials.credentials
    try:
        return pyjwt.decode(token, JWT_SECRET, algorithms=["HS256", "HS384", "HS512"])
    except pyjwt.ExpiredSignatureError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="令牌已过期")
    except pyjwt.InvalidTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="无效的令牌")


DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
if not DEEPSEEK_API_KEY:
    raise ValueError("DEEPSEEK_API_KEY environment variable is required. Please set it in .env.secrets file.")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
DEFAULT_MAX_ITERATIONS = int(os.getenv("AGENT_MAX_ITERATIONS", "8"))

member_repo = MemberRepository()
order_repo = OrderRepository()
analytics_repo = AnalyticsRepository()
product_repo = ProductRepository()
inventory_repo = InventoryRepository()


# ============ 会话记忆（MySQL 会话元数据 + Redis 对话记忆） ============
# 与天机学堂一致：会话列表/标题存 MySQL(ai_chat_session)，对话内容记忆存 Redis(ai:memory:{session_id})
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
# protocol=2：兼容老版本 Redis（不支持 RESP3 HELLO 命令）
_redis_pool = redis.ConnectionPool(host=REDIS_HOST, port=REDIS_PORT, db=0, decode_responses=True, protocol=2)


def _get_redis() -> redis.Redis:
    return redis.Redis(connection_pool=_redis_pool)


def memory_key(session_id: str) -> str:
    return f"ai:memory:{session_id}"


def _now_str() -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S")


def load_memory(session_id: str, limit: int = 30) -> List[Dict[str, Any]]:
    """读取 Redis 会话记忆，返回最近 N 条 {role, content} 列表（时间正序）。"""
    if not session_id:
        return []
    try:
        r = _get_redis()
        raw = r.lrange(memory_key(session_id), 0, -1)
        msgs: List[Dict[str, Any]] = []
        for item in raw[-limit:]:
            try:
                parsed = json.loads(item)
            except Exception:
                continue
            role = parsed.get("role")
            content = parsed.get("content")
            if role in ("user", "assistant") and content:
                msgs.append({"role": role, "content": str(content)})
        return msgs
    except Exception as e:
        print(f"[memory] 读取Redis记忆失败: {e}")
        return []


def append_memory(session_id: str, role: str, content: str) -> None:
    if not session_id or not content:
        return
    try:
        r = _get_redis()
        r.rpush(memory_key(session_id), json.dumps({"role": role, "content": content}, ensure_ascii=False))
        # 限制单会话最多 200 条，防止无限增长
        length = r.llen(memory_key(session_id))
        if length > 200:
            r.ltrim(memory_key(session_id), length - 200, -1)
    except Exception as e:
        print(f"[memory] 写入Redis记忆失败: {e}")


def clear_memory(session_id: str) -> None:
    if not session_id:
        return
    try:
        r = _get_redis()
        r.delete(memory_key(session_id))
    except Exception as e:
        print(f"[memory] 清理Redis记忆失败: {e}")


def ensure_session(session_id: str, user_id: str, first_message: str) -> bool:
    """确保会话在 MySQL 中存在；不存在则创建，返回是否新建。"""
    try:
        with get_connection() as conn, conn.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) AS c FROM ai_chat_session WHERE session_id = %s", (session_id,))
            row = cursor.fetchone()
            if row and row["c"] > 0:
                return False
            title = first_message[:20] if first_message else "新会话"
            cursor.execute(
                "INSERT INTO ai_chat_session (session_id, user_id, title, summary, message_count, model, status, create_time, update_time) "
                "VALUES (%s, %s, %s, %s, 0, %s, 1, NOW(), NOW())",
                (session_id, user_id, title, first_message[:200], DEEPSEEK_MODEL),
            )
            return True
    except Exception as e:
        print(f"[session] 创建会话失败: {e}")
        return False


def increment_message_count(session_id: str) -> None:
    try:
        with get_connection() as conn, conn.cursor() as cursor:
            cursor.execute("UPDATE ai_chat_session SET message_count = message_count + 1, update_time = NOW() WHERE session_id = %s", (session_id,))
    except Exception as e:
        print(f"[session] 更新消息数失败: {e}")


def rename_session(session_id: str, title: str) -> None:
    try:
        with get_connection() as conn, conn.cursor() as cursor:
            cursor.execute("UPDATE ai_chat_session SET title = %s, update_time = NOW() WHERE session_id = %s", (title[:60], session_id))
    except Exception as e:
        print(f"[session] 更新标题失败: {e}")


def list_sessions(user_id: str) -> List[Dict[str, Any]]:
    try:
        with get_connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                "SELECT session_id, title, message_count, create_time, update_time "
                "FROM ai_chat_session WHERE user_id = %s AND status = 1 ORDER BY update_time DESC",
                (user_id,),
            )
            return [normalize_record(r) for r in cursor.fetchall()]
    except Exception as e:
        print(f"[session] 查询会话列表失败: {e}")
        return []


def delete_session(session_id: str, user_id: str) -> None:
    clear_memory(session_id)
    try:
        with get_connection() as conn, conn.cursor() as cursor:
            cursor.execute("UPDATE ai_chat_session SET status = 0 WHERE session_id = %s AND user_id = %s", (session_id, user_id))
    except Exception as e:
        print(f"[session] 删除会话失败: {e}")


async def summarize_title(session_id: str, question: str, answer: str) -> None:
    """首轮对话后异步生成简短标题写入 MySQL。"""
    try:
        prompt = (
            "根据以下用户提问生成一个不超过15字的简短会话标题，直接输出标题本身，不要引号和多余说明。\n"
            f"用户：{question[:100]}\n助手：{answer[:150]}"
        )
        resp = await call_deepseek([
            {"role": "system", "content": "你是会话标题生成助手，只输出简短标题。"},
            {"role": "user", "content": prompt},
        ])
        if "error" in resp:
            return
        title = (resp.get("choices") or [{}])[0].get("message", {}).get("content", "")
        title = re.sub(r'["\'\n]', "", (title or "")).strip()
        if title:
            rename_session(session_id, title)
    except Exception as e:
        print(f"[session] 标题总结失败: {e}")


# ============ 意图识别 ============
INTENT_RULES: List[Tuple[str, List[str]]] = [
    ("voice_order", ["下单", "帮买单", "我要买", "来一份", "来一杯", "结账", "语音收银", "一句话", "扫码下单", "来瓶", "来包"]),
    ("member_query", ["会员", "积分", "余额", "尾号", "手机号", "手机尾", "等级", "VIP"]),
    ("order_query", ["订单", "交易", "消费", "下单记录", "购买记录", "订单号", "退款"]),
    ("ops_analysis", ["销售", "热销", "经营", "趋势", "分析", "营业额", "排行", "TOP", "top"]),
    ("product_query", ["商品", "价格", "多少钱", "库存", "有没有", "卖什么", "什么价格"]),
]
INTENT_LABELS = {
    "voice_order": "语音收银 · 一句话下单",
    "member_query": "会员查询",
    "order_query": "订单查询",
    "ops_analysis": "经营分析",
    "product_query": "商品/库存查询",
    "chitchat": "通用问答",
}


def classify_intent(query: str) -> Dict[str, Any]:
    """轻量规则预分类：作为 agent 的提示与事件流首个事件(不增加模型调用)。"""
    text = (query or "").strip()
    if not text:
        return {"primary": "chitchat", "label": INTENT_LABELS["chitchat"], "confidence": 0.0, "hits": []}
    lowered = text.lower()
    scored: Dict[str, List[str]] = {}
    for intent, keywords in INTENT_RULES:
        hits = [kw for kw in keywords if kw in lowered]
        if hits:
            scored[intent] = hits
    if not scored:
        return {"primary": "chitchat", "label": INTENT_LABELS["chitchat"], "confidence": 0.3, "hits": []}
    primary = sorted(scored.items(), key=lambda kv: len(kv[1]), reverse=True)[0][0]
    confidence = min(0.95, 0.55 + 0.1 * len(scored[primary]))
    return {
        "primary": primary,
        "label": INTENT_LABELS.get(primary, primary),
        "confidence": round(confidence, 2),
        "hits": scored[primary],
    }


# ============ 工具定义(OpenAI/DeepSeek 兼容 function calling，类 Hermes 严格 schema) ============
def tool(name: str, description: str, properties: Dict[str, Any], required: List[str]) -> Dict[str, Any]:
    return {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": {
                "type": "object",
                "properties": properties,
                "required": required,
            },
        },
    }


TOOLS: List[Dict[str, Any]] = [
    tool(
        "member_get_profile",
        "按会员ID精确查询会员资料(姓名/手机号/等级/积分/余额)。当用户已提供完整会员编号或手机号时使用。",
        {
            "member_id": {"type": "string", "description": "会员编号或手机号，例如 MB20250001 或 13800001234"},
        },
        ["member_id"],
    ),
    tool(
        "member_search",
        "模糊搜索会员，关键字可以是会员编号、姓名或完整手机号。当无法确定完整会员编号时使用。",
        {
            "keyword": {"type": "string", "description": "搜索关键字"},
            "limit": {"type": "integer", "description": "返回条数，默认10，最大50"},
        },
        ["keyword"],
    ),
    tool(
        "member_get_by_phone_suffix",
        "按手机号尾号查询会员(智慧收银高频问法，如「尾号3333的会员」)。返回匹配该后号的所有会员列表。",
        {
            "suffix": {"type": "string", "description": "手机号尾号，纯数字，例如 3333"},
            "limit": {"type": "integer", "description": "返回条数，默认10，最大50"},
        },
        ["suffix"],
    ),
    tool(
        "order_get_detail",
        "按订单号查询订单详情(含商品明细、金额、支付方式、状态)。",
        {
            "order_no": {"type": "string", "description": "订单号"},
        },
        ["order_no"],
    ),
    tool(
        "order_recent",
        "查询最近订单列表。",
        {
            "limit": {"type": "integer", "description": "返回条数，默认10"},
        },
        [],
    ),
    tool(
        "order_recent_by_member",
        "按会员ID查询其最近订单列表。",
        {
            "member_id": {"type": "string", "description": "会员编号"},
            "limit": {"type": "integer", "description": "返回条数，默认5"},
        },
        ["member_id"],
    ),
    tool(
        "assistant_insight",
        "获取经营分析数据：销售趋势(sales_trend)、热销商品(top_products)、会员分层(member_segments)。",
        {
            "metric": {"type": "string", "enum": ["sales_trend", "top_products", "member_segments"], "description": "指标类型"},
            "days": {"type": "integer", "description": "统计天数，默认7(仅对 sales_trend/top_products 生效)"},
            "limit": {"type": "integer", "description": "返回条数，默认5(仅对 top_products 生效)"},
        },
        ["metric"],
    ),
    tool(
        "product_search",
        "按名称/条码/编码搜索在售商品，返回价格、单位、销量、分类。语音下单前先解析商品名时使用。",
        {
            "keyword": {"type": "string", "description": "商品名称/条码关键字，例如 可口可乐"},
            "limit": {"type": "integer", "description": "返回条数，默认10，最大50"},
        },
        ["keyword"],
    ),
    tool(
        "inventory_low_stock",
        "查询低于预警库存阈值的商品(低库存预警)，用于智慧收银补货建议。",
        {
            "limit": {"type": "integer", "description": "返回条数，默认20"},
        },
        [],
    ),
    tool(
        "voice_order_preview",
        "语音收银 MVP：把一句自然语言(\"来2瓶可口可乐和1包薯片，用尾号3333的会员结账\")解析为订单草稿并校验库存与会员余额。只读，不落单不扣库存。直到用户在收银台正式结账，才生成真实订单。",
        {
            "items": {
                "type": "array",
                "description": "下单商品清单，由模型从自然语言中抽取",
                "items": {
                    "type": "object",
                    "properties": {
                        "product_name": {"type": "string", "description": "商品名称关键字"},
                        "quantity": {"type": "integer", "description": "数量，最小1"},
                    },
                    "required": ["product_name", "quantity"],
                },
            },
            "phone_suffix": {"type": "string", "description": "可选：会员手机尾号，纯数字"},
        },
        ["items"],
    ),
]


# ============ 工具执行 ============
def _ok(data: Any, **extra: Any) -> Dict[str, Any]:
    out: Dict[str, Any] = {"status": "success"}
    if isinstance(data, dict):
        out.update({"data": data})
    else:
        out["data"] = data
    out.update(extra)
    return out


def _err(message: str, **extra: Any) -> Dict[str, Any]:
    out: Dict[str, Any] = {"status": "error", "message": message}
    out.update(extra)
    return out


def _list_recent_orders_by_member(member_id: str, limit: int) -> List[Dict[str, Any]]:
    """按 member_id 字段过滤最近订单(收敛到 list_recent_orders 与字段过滤，避免新增全表扫描)。"""
    recent = order_repo.list_recent_orders(limit * 5)
    return [r for r in recent if str(r.get("member_id")) == str(member_id)][:limit]


def execute_tool(tool_name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
    try:
        if tool_name == "member_get_profile":
            member_id = str(arguments.get("member_id", "")).strip()
            profile = member_repo.get_member(member_id)
            if not profile:
                return _err("会员不存在", member_id=member_id)
            return _ok(_slim_member(profile))

        if tool_name == "member_search":
            keyword = arguments.get("keyword", "")
            limit = arguments.get("limit", 10)
            members = member_repo.search_members(keyword, limit)
            return _ok([_slim_member(m) for m in members], count=len(members))

        if tool_name == "member_get_by_phone_suffix":
            suffix = str(arguments.get("suffix", "")).strip()
            limit = arguments.get("limit", 10)
            if not suffix or not suffix.isdigit() or len(suffix) > 6:
                return _err("手机号尾号应为 1~6 位纯数字", suffix=suffix)
            members = member_repo.search_by_phone_suffix(suffix, limit)
            if not members:
                return _err("未找到匹配该尾号的会员", suffix=suffix)
            return _ok([_slim_member(m) for m in members], count=len(members), suffix=suffix)

        if tool_name == "order_get_detail":
            order_no = str(arguments.get("order_no", "")).strip()
            bundle = order_repo.get_order_with_items(order_no)
            if not bundle:
                return _err("订单不存在", order_no=order_no)
            return _ok(order_repo.summarize_order(bundle))

        if tool_name == "order_recent":
            limit = arguments.get("limit", 10)
            orders = order_repo.list_recent_orders(limit)
            return _ok(orders, count=len(orders))

        if tool_name == "order_recent_by_member":
            member_id = str(arguments.get("member_id", "")).strip()
            limit = arguments.get("limit", 5)
            orders = order_repo.list_recent_orders(limit * 5)
            filtered = [o for o in orders if str(o.get("member_id")) == str(member_id)][:limit]
            return _ok(filtered, count=len(filtered))

        if tool_name == "assistant_insight":
            metric = arguments.get("metric", "sales_trend")
            days = arguments.get("days", 7)
            limit = arguments.get("limit", 5)
            if metric == "sales_trend":
                data = analytics_repo.get_sales_trend(days)
            elif metric == "top_products":
                data = analytics_repo.get_top_products(days, limit)
            elif metric == "member_segments":
                data = analytics_repo.get_member_segments()
            else:
                return _err(f"未知指标: {metric}")
            return _ok(data, metric=metric)

        if tool_name == "product_search":
            keyword = arguments.get("keyword", "")
            limit = arguments.get("limit", 10)
            products = product_repo.search_products(keyword, limit)
            return _ok(products, count=len(products))

        if tool_name == "inventory_low_stock":
            limit = arguments.get("limit", 20)
            data = inventory_repo.list_low_stock(limit)
            return _ok(data, count=len(data))

        if tool_name == "voice_order_preview":
            return _build_voice_order_preview(arguments)

        return _err(f"未知工具: {tool_name}")

    except Exception as exc:
        return _err(f"工具执行异常: {exc}", tool=tool_name)


def _slim_member(m: Dict[str, Any]) -> Dict[str, Any]:
    """裁剪会员字段，避免把敏感/无用字段交给 LLM。"""
    keep = ["member_id", "name", "level", "points", "balance", "phone", "gender", "create_time"]
    return {k: m.get(k) for k in keep if k in m}


def _build_voice_order_preview(arguments: Dict[str, Any]) -> Dict[str, Any]:
    items_raw = arguments.get("items") or []
    phone_suffix = str(arguments.get("phone_suffix") or "").strip()
    if not items_raw:
        return _err("未识别到下单商品")

    line_items: List[Dict[str, Any]] = []
    total = 0.0
    warnings: List[str] = []
    for idx, it in enumerate(items_raw, 1):
        name = str(it.get("product_name") or "").strip()
        qty = it.get("quantity")
        try:
            qty = int(qty)
        except (TypeError, ValueError):
            qty = 1
        if qty < 1:
            qty = 1
        if not name:
            warnings.append(f"第{idx}项未提供商品名，已忽略")
            continue
        candidates = product_repo.search_products(name, 1)
        if not candidates:
            warnings.append(f"未找到商品「{name}」")
            line_items.append({
                "product_name": name,
                "quantity": qty,
                "matched": False,
                "price": None,
                "unit": None,
                "subtotal": None,
                "stock_quantity": None,
                "note": "商品不存在或已下架",
            })
            continue
        prod = candidates[0]
        stock = inventory_repo.get_stock(prod["product_id"]) or {}
        stock_qty = int(stock.get("stock_quantity") or 0)
        subtotal = round(float(prod["price"]) * qty, 2)
        total += subtotal
        line_items.append({
            "product_id": prod["product_id"],
            "product_name": prod["product_name"],
            "quantity": qty,
            "matched": True,
            "price": prod["price"],
            "unit": prod["unit"],
            "subtotal": subtotal,
            "stock_quantity": stock_qty,
            "stock_ok": stock_qty >= qty,
        })
        if stock_qty < qty:
            warnings.append(f"{prod['product_name']} 库存不足(剩 {stock_qty}，需 {qty})")

    preview = {
        "items": line_items,
        "total_amount": round(total, 2),
        "warnings": warnings,
    }

    if phone_suffix:
        if (not phone_suffix.isdigit()) or len(phone_suffix) > 6:
            preview["member"] = None
            preview["member_warning"] = "手机尾号格式不正确，已忽略会员匹配"
        else:
            members = member_repo.search_by_phone_suffix(phone_suffix, 1)
            if not members:
                preview["member"] = None
                preview["member_warning"] = f"未匹配到尾号 {phone_suffix} 的会员"
            else:
                m = _slim_member(members[0])
                preview["member"] = m
                balance = float(m.get("balance") or 0)
                if balance < total:
                    preview["member_warning"] = f"会员余额 ¥{balance:.2f} 不足以支付 ¥{total:.2f}，需补差或换支付方式"

    preview["next_action"] = "请在收银台确认后正式结账(POST /api/order/checkout)，本预览不扣库存不落单"
    return _ok(preview)


# ============ 事件流辅助 ============
def _now_ms() -> int:
    return int(time.time() * 1000)


def _emit(events: List[Dict[str, Any]], event_type: str, **payload: Any) -> None:
    events.append({"type": event_type, "ts": _now_ms(), **payload})


def _truncate(text: str, n: int = 1200) -> str:
    if text is None:
        return ""
    return text if len(text) <= n else (text[:n] + "…(已截断)")


# ============ DeepSeek 调用 ============
async def call_deepseek(messages: List[Dict[str, Any]]) -> Dict[str, Any]:
    if not DEEPSEEK_API_KEY:
        return {"error": "未配置 DEEPSEEK_API_KEY"}

    url = f"{DEEPSEEK_BASE_URL}/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": DEEPSEEK_MODEL,
        "messages": messages,
        "tools": TOOLS,
        "tool_choice": "auto",
        "temperature": 0.3,
        "max_tokens": 1200,
    }
    async with httpx.AsyncClient(timeout=60.0) as client:
        resp = await client.post(url, headers=headers, json=payload)
        if resp.status_code != 200:
            return {"error": f"DeepSeek API错误: {resp.status_code}", "detail": resp.text}
        return resp.json()


async def call_deepseek_stream(messages: List[Dict[str, Any]]) -> AsyncIterator[str]:
    """流式调用 DeepSeek，逐块产出 content 增量文本。"""
    if not DEEPSEEK_API_KEY:
        raise RuntimeError("未配置 DEEPSEEK_API_KEY")

    url = f"{DEEPSEEK_BASE_URL}/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": DEEPSEEK_MODEL,
        "messages": messages,
        "tools": TOOLS,
        "tool_choice": "auto",
        "temperature": 0.3,
        "max_tokens": 1200,
        "stream": True,
    }
    async with httpx.AsyncClient(timeout=120.0) as client:
        async with client.stream("POST", url, headers=headers, json=payload) as resp:
            if resp.status_code != 200:
                body = await resp.aread()
                raise RuntimeError(f"DeepSeek API错误: {resp.status_code} {body.decode('utf-8', 'ignore')[:200]}")
            async for line in resp.aiter_lines():
                if not line or not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    chunk = json.loads(data)
                except Exception:
                    continue
                delta = (chunk.get("choices") or [{}])[0].get("delta") or {}
                content = delta.get("content")
                if content:
                    yield content


# ============ 意图 → 系统提示注入 ============
SYSTEM_PROMPT_TEMPLATE = (
    "你是「智慧收银系统 SmartPOS · AI 新零售中台」的智慧助手，服务于超市收银员/店长/会员。\n"
    "工作方式(必须遵守)：\n"
    "1. 你遵循 agent-loop 范式：观察工具返回 → 思考是否信息齐全 → 再调用下一个工具或给出最终答案。\n"
    "2. 意图识别结果(参考，可能被你修正)：{{intent}}。若与用户请求不符，以用户请求为准。\n"
    "3. 当用户请求包含多个信息需求(如「尾号3333会员的余额、积分、最近订单」)，请在同一回合并行调用所需工具，避免串行低效。\n"
    "4. 工具调用阶段不要输出自然语言 content；信息齐全后再用一句或几句话给出最终答案。\n"
    "5. 语音收银类(一句话下单)：必须先用 voice_order_preview 生成订单草稿并校验库存/会员余额；严禁直接编造订单号或声称已下单。\n"
    "6. 回答用纯文本中文，不要 markdown、不要星号井号、不要 emoji。\n"
    "7. 表达要像真人店长/导购在对话：语气自然亲切、口语化、有人情味，避免生硬的罗列和机器人腔；"
    "先说结论，再给关键细节；多数据项用「分点+短句」让段落清晰（可用编号或换行，不要表格）；"
    "必要时带一句自然的衔接或提醒(如「我帮您看了下」「简单来说」)，但不要过度寒暄。\n"
    "8. 金额用 ¥，两位小数；价格、积分等数字准确即可。\n"
    "9. 对涉敏感操作(真实下单/退款/扣库存)只给说明，不虚构结果。\n"
    "10. 若达到最大推理步数仍未完成，请给出当前已知信息与下一步建议。\n"
)


def build_system_prompt(intent: Dict[str, Any]) -> str:
    intent_desc = json.dumps(intent, ensure_ascii=False)
    return SYSTEM_PROMPT_TEMPLATE.replace("{{intent}}", intent_desc)


def _parse_tool_arguments(raw: str) -> Dict[str, Any]:
    if not raw:
        return {}
    try:
        return json.loads(raw)
    except Exception:
        # 容错：尝试修复单引号
        try:
            return json.loads(raw.replace("'", '"'))
        except Exception:
            return {}


# ============ 核心 agent-loop ============
async def agent_loop(user_query: str, max_iterations: int = DEFAULT_MAX_ITERATIONS,
                     session_id: Optional[str] = None, user_id: Optional[str] = None) -> Dict[str, Any]:
    events: List[Dict[str, Any]] = []
    intent = classify_intent(user_query)
    _emit(events, "intent_detected", intent=intent)

    messages: List[Dict[str, Any]] = [
        {"role": "system", "content": build_system_prompt(intent)},
    ]
    # 注入会话记忆（多轮上下文）
    if session_id:
        history = load_memory(session_id)
        messages.extend(history)
    messages.append({"role": "user", "content": user_query})

    iterations = 0
    final_summary = "抱歉，我暂时无法处理这个请求。"
    # 语音下单草稿（不截断，供前端渲染订单卡片；借鉴天机学堂 param=1003 卡片）
    voice_order: Optional[Dict[str, Any]] = None

    for i in range(1, max_iterations + 1):
        iterations = i
        _emit(events, "iteration", iteration=i)

        result = await call_deepseek(messages)
        if "error" in result:
            _emit(events, "error", message=result.get("error"))
            return {
                "summary": f"AI 服务暂时不可用：{result.get('error')}",
                "events": events,
                "intent": intent,
                "iterations": iterations,
                "voice_order": voice_order,
            }

        choice = (result.get("choices") or [{}])[0]
        message = choice.get("message", {}) or {}
        finish_reason = choice.get("finish_reason", "")

        # 兼容 deepseek-reasoner 的思考内容
        reasoning = message.get("reasoning_content")
        if reasoning:
            _emit(events, "thinking", content=_truncate(str(reasoning), 300))

        tool_calls = message.get("tool_calls")
        if finish_reason == "tool_calls" and tool_calls:
            # 将 assistant 消息原文(含 tool_calls)入栈，保持 OpenAI 协议
            messages.append({
                "role": "assistant",
                "content": message.get("content") or "",
                "tool_calls": tool_calls,
            })
            for tc in tool_calls:
                fn = tc.get("function", {}) or {}
                name = fn.get("name", "")
                arguments = _parse_tool_arguments(fn.get("arguments", ""))
                _emit(events, "tool_call", name=name, arguments=arguments, tool_call_id=tc.get("id"))
                tool_result = execute_tool(name, arguments)
                # 语音下单草稿：捕获不截断的完整结果，供前端渲染卡片
                if name == "voice_order_preview" and tool_result.get("status") == "success":
                    voice_order = tool_result.get("data")
                _emit(
                    events,
                    "tool_result",
                    name=name,
                    ok=tool_result.get("status") == "success",
                    result=_truncate(json.dumps(tool_result, ensure_ascii=False), 800),
                )
                messages.append({
                    "role": "tool",
                    "tool_call_id": tc.get("id"),
                    "content": json.dumps(tool_result, ensure_ascii=False),
                })
            continue

        content = message.get("content")
        if finish_reason == "stop" and content:
            _emit(events, "answer", content=_truncate(content, 1000), final=True)
            final_summary = content
            break

        if content:
            _emit(events, "answer", content=_truncate(content, 1000), final=True)
            final_summary = content
            break

        # 既无 tool_calls 也无 content —— 兜底结束
        _emit(events, "warning", message="本轮既无工具调用也无回答，结束推理")
        break
    else:
        # for-else：达到最大迭代仍未给出最终答案
        _emit(
            events,
            "max_iterations_reached",
            max_iterations=max_iterations,
            note="已达到最大推理步数，停止迭代并汇总当前信息",
        )

    # 保存会话记忆 + 更新消息数 + 首轮标题总结
    if session_id:
        is_new = ensure_session(session_id, user_id or "0", user_query)
        append_memory(session_id, "user", user_query)
        append_memory(session_id, "assistant", final_summary)
        increment_message_count(session_id)
        if is_new:
            import asyncio
            asyncio.get_event_loop().create_task(summarize_title(session_id, user_query, final_summary))

    return {
        "summary": final_summary,
        "events": events,
        "intent": intent,
        "iterations": iterations,
        "voice_order": voice_order,
        "session_id": session_id,
    }


# ============ 流式 agent-loop（SSE） ============
async def agent_loop_stream(user_query: str, max_iterations: int = DEFAULT_MAX_ITERATIONS,
                            session_id: Optional[str] = None, user_id: Optional[str] = None) -> AsyncIterator[Dict[str, Any]]:
    """agent-loop 的流式版本：逐步 yield 事件(供 SSE 前端实时渲染)，最终回答逐块产出 delta。

    每个 yield 是一个 dict：{"type": "...", ...}
      - event: 单个轨迹事件（intent_detected/iteration/tool_call/tool_result/thinking 等）
      - delta: 回答增量文本 {"text": str}
      - meta: 流结束元数据 {"summary", "events", "intent", "iterations", "voice_order", "status", "session_id"}
      - error: 出错信息 {"message": str}
    """
    events: List[Dict[str, Any]] = []
    intent = classify_intent(user_query)
    _emit(events, "intent_detected", intent=intent)
    yield {"type": "event", "event": events[-1]}

    messages: List[Dict[str, Any]] = [
        {"role": "system", "content": build_system_prompt(intent)},
    ]
    # 注入会话记忆（多轮上下文）
    if session_id:
        history = load_memory(session_id)
        messages.extend(history)
    messages.append({"role": "user", "content": user_query})

    iterations = 0
    final_summary = "抱歉，我暂时无法处理这个请求。"
    voice_order: Optional[Dict[str, Any]] = None

    try:
        for i in range(1, max_iterations + 1):
            iterations = i
            _emit(events, "iteration", iteration=i)
            yield {"type": "event", "event": events[-1]}

            result = await call_deepseek(messages)
            if "error" in result:
                err = result.get("error")
                _emit(events, "error", message=err)
                yield {"type": "event", "event": events[-1]}
                yield {"type": "error", "message": err}
                return

            choice = (result.get("choices") or [{}])[0]
            message = choice.get("message", {}) or {}
            finish_reason = choice.get("finish_reason", "")

            reasoning = message.get("reasoning_content")
            if reasoning:
                _emit(events, "thinking", content=_truncate(str(reasoning), 300))
                yield {"type": "event", "event": events[-1]}

            tool_calls = message.get("tool_calls")
            if finish_reason == "tool_calls" and tool_calls:
                messages.append({
                    "role": "assistant",
                    "content": message.get("content") or "",
                    "tool_calls": tool_calls,
                })
                for tc in tool_calls:
                    fn = tc.get("function", {}) or {}
                    name = fn.get("name", "")
                    arguments = _parse_tool_arguments(fn.get("arguments", ""))
                    _emit(events, "tool_call", name=name, arguments=arguments, tool_call_id=tc.get("id"))
                    yield {"type": "event", "event": events[-1]}
                    tool_result = execute_tool(name, arguments)
                    if name == "voice_order_preview" and tool_result.get("status") == "success":
                        voice_order = tool_result.get("data")
                    _emit(
                        events,
                        "tool_result",
                        name=name,
                        ok=tool_result.get("status") == "success",
                        result=_truncate(json.dumps(tool_result, ensure_ascii=False), 800),
                    )
                    yield {"type": "event", "event": events[-1]}
                    messages.append({
                        "role": "tool",
                        "tool_call_id": tc.get("id"),
                        "content": json.dumps(tool_result, ensure_ascii=False),
                    })
                continue

            content = message.get("content")
            if finish_reason == "stop" and content:
                _emit(events, "answer", content=_truncate(content, 1000), final=True)
                yield {"type": "event", "event": events[-1]}
                final_summary = content
                # 最终回答：流式逐块推送
                try:
                    async for delta in call_deepseek_stream(messages):
                        final_summary += delta
                        yield {"type": "delta", "text": delta}
                except Exception as e:
                    yield {"type": "error", "message": str(e)}
                # 保存会话记忆
                if session_id:
                    ensure_session(session_id, user_id or "0", user_query)
                    append_memory(session_id, "user", user_query)
                    append_memory(session_id, "assistant", final_summary)
                    increment_message_count(session_id)
                    import asyncio
                    asyncio.get_event_loop().create_task(summarize_title(session_id, user_query, final_summary))
                yield {"type": "meta", "meta": {
                    "status": "success",
                    "summary": final_summary,
                    "events": events,
                    "intent": intent,
                    "iterations": iterations,
                    "voice_order": voice_order,
                    "session_id": session_id,
                }}
                return

            if content:
                _emit(events, "answer", content=_truncate(content, 1000), final=True)
                yield {"type": "event", "event": events[-1]}
                final_summary = content
                # 保存会话记忆
                if session_id:
                    ensure_session(session_id, user_id or "0", user_query)
                    append_memory(session_id, "user", user_query)
                    append_memory(session_id, "assistant", final_summary)
                    increment_message_count(session_id)
                    import asyncio
                    asyncio.get_event_loop().create_task(summarize_title(session_id, user_query, final_summary))
                yield {"type": "meta", "meta": {
                    "status": "success",
                    "summary": final_summary,
                    "events": events,
                    "intent": intent,
                    "iterations": iterations,
                    "voice_order": voice_order,
                    "session_id": session_id,
                }}
                return

            _emit(events, "warning", message="本轮既无工具调用也无回答，结束推理")
            yield {"type": "event", "event": events[-1]}
            break
        else:
            _emit(
                events,
                "max_iterations_reached",
                max_iterations=max_iterations,
                note="已达到最大推理步数，停止迭代并汇总当前信息",
            )
            yield {"type": "event", "event": events[-1]}

        yield {"type": "meta", "meta": {
            "status": "success",
            "summary": final_summary,
            "events": events,
            "intent": intent,
            "iterations": iterations,
            "voice_order": voice_order,
            "session_id": session_id,
        }}
    except Exception as exc:
        yield {"type": "error", "message": str(exc)}


async def sse_event_payload(item: Dict[str, Any]) -> str:
    """把 agent_loop_stream 的 yield item 转成 SSE 文本。"""
    etype = item.get("type")
    if etype == "event":
        return f'event: event\ndata: {json.dumps(item["event"], ensure_ascii=False)}\n\n'
    if etype == "delta":
        return f'event: delta\ndata: {json.dumps({"text": item["text"]}, ensure_ascii=False)}\n\n'
    if etype == "meta":
        return f'event: meta\ndata: {json.dumps(item["meta"], ensure_ascii=False)}\n\n'
    if etype == "error":
        return f'event: error\ndata: {json.dumps({"message": item["message"]}, ensure_ascii=False)}\n\n'
    return ""


@app.post("/agent_query_stream")
async def agent_query_stream(req: AgentQuery, user: Dict[str, Any] = Depends(verify_token)) -> StreamingResponse:
    try:
        max_iter = int(req.max_iterations or DEFAULT_MAX_ITERATIONS)
        max_iter = max(1, min(max_iter, 20))
    except (TypeError, ValueError):
        max_iter = DEFAULT_MAX_ITERATIONS
    # 会话ID：前端传入；为空则服务端生成（一次性会话）
    session_id = req.session_id or uuid.uuid4().hex
    user_id = str(user.get("userId") or user.get("user_id") or user.get("sub") or "0")

    async def event_gen():
        async for item in agent_loop_stream(req.query, max_iterations=max_iter,
                                            session_id=session_id, user_id=user_id):
            yield await sse_event_payload(item)

    return StreamingResponse(
        event_gen(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.post("/agent_query")
async def agent_query(req: AgentQuery, user: Dict[str, Any] = Depends(verify_token)) -> Dict[str, Any]:
    try:
        max_iter = int(req.max_iterations or DEFAULT_MAX_ITERATIONS)
        max_iter = max(1, min(max_iter, 20))
    except (TypeError, ValueError):
        max_iter = DEFAULT_MAX_ITERATIONS
    session_id = req.session_id or uuid.uuid4().hex
    user_id = str(user.get("userId") or user.get("user_id") or user.get("sub") or "0")
    try:
        result = await agent_loop(req.query, max_iterations=max_iter,
                                  session_id=session_id, user_id=user_id)
        return {"status": "success", "data": result}
    except Exception as exc:
        return {"status": "error", "message": str(exc)}


# ============ 会话管理端点 ============
@app.get("/agent/sessions")
async def agent_sessions(user: Dict[str, Any] = Depends(verify_token)) -> Dict[str, Any]:
    """历史会话列表（标题/消息数/时间）。"""
    user_id = str(user.get("userId") or user.get("user_id") or user.get("sub") or "0")
    return {"status": "success", "data": list_sessions(user_id)}


@app.post("/agent/session")
async def agent_session_create(user: Dict[str, Any] = Depends(verify_token)) -> Dict[str, Any]:
    """新建会话，返回 session_id。"""
    session_id = uuid.uuid4().hex
    user_id = str(user.get("userId") or user.get("user_id") or user.get("sub") or "0")
    ensure_session(session_id, user_id, "新会话")
    return {"status": "success", "data": {"session_id": session_id, "title": "新会话"}}


@app.delete("/agent/session/{session_id}")
async def agent_session_delete(session_id: str, user: Dict[str, Any] = Depends(verify_token)) -> Dict[str, Any]:
    """删除会话（软删 MySQL + 清 Redis 记忆）。"""
    user_id = str(user.get("userId") or user.get("user_id") or user.get("sub") or "0")
    delete_session(session_id, user_id)
    return {"status": "success", "msg": "已删除"}


@app.get("/agent/messages")
async def agent_messages(session_id: str, user: Dict[str, Any] = Depends(verify_token)) -> Dict[str, Any]:
    """读取某会话的历史消息（Redis 记忆，供前端回显）。"""
    return {"status": "success", "data": load_memory(session_id)}


@app.get("/health")
async def health() -> Dict[str, str]:
    return {"status": "ok", "model": DEEPSEEK_MODEL, "tools": str(len(TOOLS))}


@app.get("/tools")
async def list_tools() -> Dict[str, Any]:
    return {"status": "ok", "tools": [{"name": t["function"]["name"], "description": t["function"]["description"]} for t in TOOLS]}


if __name__ == "__main__":
    import uvicorn

    host = "0.0.0.0"
    port = int(os.getenv("AGENT_PROXY_PORT", "9000"))
    print(f"智慧助手 Agent Proxy 启动在 http://{host}:{port}")
    print(f"模型: {DEEPSEEK_MODEL}, 工具数: {len(TOOLS)}, 默认最大迭代: {DEFAULT_MAX_ITERATIONS}")
    uvicorn.run(app, host=host, port=port)