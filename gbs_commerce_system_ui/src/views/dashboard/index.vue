<template>
  <div class="dashboard-container">
    <!-- 智能欢迎 Hero -->
    <el-card class="hero-card" shadow="never">
      <div class="hero-bg" aria-hidden="true">
        <span class="orb orb-1"></span><span class="orb orb-2"></span>
        <span class="grid-mask"></span>
      </div>
      <div class="hero-content">
        <div class="hero-left">
          <div class="hero-eyebrow">
            <span class="pos-ai-badge">AI · 智能工作台</span>
            <span class="hero-date">{{ todayLabel }}</span>
          </div>
          <p class="hero-greeting">{{ greeting }}，{{ displayName }}</p>
          <p class="hero-subtitle">
            当前角色：<strong class="pos-gradient-text">{{ roleLabel }}</strong>。{{ roleWelcome }}
          </p>
          <div class="hero-buttons">
            <el-button v-for="btn in availableQuickButtons" :key="btn.key" :type="btn.type" @click="openRemote(btn.key)">
              {{ btn.label }}
            </el-button>
          </div>
        </div>
        <div class="hero-right">
          <div class="hero-stat" v-if="canViewSales">
            <span class="hero-stat__label">今日待办</span>
            <span class="hero-stat__value pos-counter">{{ mockStats.todo }}</span>
          </div>
          <div class="hero-stat" v-if="canViewMember">
            <span class="hero-stat__label">会员咨询</span>
            <span class="hero-stat__value pos-counter">{{ mockStats.member }}</span>
          </div>
          <div class="hero-stat">
            <span class="hero-stat__label">库存预警</span>
            <span class="hero-stat__value pos-counter danger">{{ inventoryWarnings.length }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 销售汇总卡片 -->
    <el-row v-if="canViewSales" :gutter="16" class="summary-row">
      <el-col :span="6" v-for="card in todayCards" :key="card.key">
        <el-card :class="['summary-card', card.class]" shadow="never">
          <div class="summary-head">
            <span class="summary-icon"><el-icon><component :is="card.icon" /></el-icon></span>
            <p class="summary-title">{{ card.title }}</p>
          </div>
          <p class="summary-value pos-counter">{{ card.display }}</p>
          <div class="summary-footer">
            <p v-if="card.trendKey" class="summary-trend" :class="getTrendClass(todayData[card.trendKey])">
              {{ formatTrend(todayData[card.trendKey]) }}
            </p>
            <p v-if="card.totalKey" class="summary-total">
              累计 {{ card.totalFormatter ? card.totalFormatter(overview[card.totalKey]) : overview[card.totalKey] }}
            </p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- AI 智能洞察 -->
    <el-card v-if="canViewSales" class="insight-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title"><span class="pos-ai-badge" style="margin-right:8px">AI 洞察</span>经营智能提示</span>
        </div>
      </template>
      <div class="insight-grid">
        <div class="pos-insight" v-for="(tip, i) in aiInsights" :key="i">{{ tip }}</div>
      </div>
    </el-card>

    <el-row v-if="canViewSales" :gutter="16" class="main-section">
      <el-col :span="16">
        <el-card v-loading="trendLoading" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">最近 {{ selectedDays }} 天销售趋势</span>
              <el-select v-model="selectedDays" size="small" style="width: 120px">
                <el-option v-for="option in daysOptions" :key="option" :label="`近 ${option} 天`" :value="option" />
              </el-select>
            </div>
          </template>
          <div class="chart-panel" ref="trendChartRef"></div>
        </el-card>

        <el-card class="hot-products-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">热销商品 TOP10</span>
              <el-tag size="small" type="warning" effect="light">实时</el-tag>
            </div>
          </template>
          <el-table :data="overview.topProducts || []" size="small" stripe>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="productName" label="商品名" />
            <el-table-column prop="totalQuantity" label="销量" width="100" sortable />
            <el-table-column prop="totalAmount" label="销售额" width="120" sortable>
              <template #default="{ row }">
                <span class="amount">￥{{ formatCurrency(row.totalAmount) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="inventory-alert-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">库存预警</span>
              <el-badge :value="inventoryWarnings.length" :max="99" type="danger" />
            </div>
          </template>
          <div v-if="inventoryWarnings.length === 0" class="empty-alert">
            <el-icon><CircleCheck /></el-icon>
            <span>暂无库存预警</span>
          </div>
          <el-table v-else :data="inventoryWarnings" size="small" max-height="220" stripe>
            <el-table-column prop="productName" label="商品名称" min-width="100" show-overflow-tooltip />
            <el-table-column prop="stockQuantity" label="库存" width="70" align="right">
              <template #default="{ row }"><span class="warning-text">{{ row.stockQuantity }}</span></template>
            </el-table-column>
            <el-table-column prop="diff" label="差值" width="70" align="right">
              <template #default="{ row }">
                <span :class="row.diff < 0 ? 'danger-text' : 'warning-text'">{{ row.diff }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="recent-orders-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">实时订单流</span>
              <el-tag size="small" type="success" effect="light">最新10笔</el-tag>
            </div>
          </template>
          <div class="order-flow">
            <div v-for="(order, idx) in recentOrders" :key="idx" class="order-item">
              <div class="order-info">
                <span class="order-no">{{ order.orderNo }}</span>
                <span class="order-time">{{ formatTime(order.createTime) }}</span>
              </div>
              <div class="order-amount">
                <span class="amount">￥{{ formatCurrency(order.paidAmount) }}</span>
                <el-tag size="small" :type="getPaymentTagType(order.paymentMethod)">{{ getPaymentLabel(order.paymentMethod) }}</el-tag>
              </div>
            </div>
            <el-empty v-if="recentOrders.length === 0" description="暂无订单" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="!canViewSales && canViewInventory" class="inventory-section" shadow="never">
      <template #header>
        <div class="card-header"><span class="card-title">库存概览</span></div>
      </template>
      <el-row :gutter="16">
        <el-col :span="12">
          <div class="inventory-summary-item">
            <p class="inventory-summary-title">库存预警商品</p>
            <el-table :data="inventoryWarnings" size="small" max-height="300" stripe>
              <el-table-column prop="productName" label="商品名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="stockQuantity" label="当前库存" width="100" align="right">
                <template #default="{ row }"><span class="warning-text">{{ row.stockQuantity }}</span></template>
              </el-table-column>
              <el-table-column prop="warningQuantity" label="预警阈值" width="100" align="right" />
              <el-table-column prop="diff" label="差值" width="80" align="right">
                <template #default="{ row }">
                  <span :class="row.diff < 0 ? 'danger-text' : 'warning-text'">{{ row.diff }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="inventory-summary-item">
            <p class="inventory-summary-title">快捷操作</p>
            <div class="quick-actions">
              <el-button type="primary" @click="openRemote('stock')">库存查询</el-button>
              <el-button type="success" @click="openRemote('in')">商品入库</el-button>
              <el-button type="warning" @click="openRemote('out')">商品出库</el-button>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card v-if="isCashier" class="cashier-section" shadow="never">
      <template #header>
        <div class="card-header"><span class="card-title">收银工作台</span></div>
      </template>
      <el-row :gutter="16">
        <el-col :span="12">
          <div class="cashier-actions">
            <el-button type="primary" size="large" @click="openRemote('checkout')">
              <el-icon><ShoppingCartFull /></el-icon> 开始收银
            </el-button>
            <el-button size="large" @click="openRemote('order')">
              <el-icon><Document /></el-icon> 订单查询
            </el-button>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="cashier-tips">
            <p class="tips-title">收银提示</p>
            <ul class="tips-list">
              <li>扫描商品条码或手动输入商品编码</li>
              <li>支持现金、微信、支付宝、银行卡、刷脸支付</li>
              <li>会员可使用积分抵扣或储值支付</li>
            </ul>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheck, ShoppingCartFull, Document, Money, ShoppingCart, TrendCharts, User } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getSalesOverview, getTodaySalesOverview, getSalesTrend, getRecentOrders, getInventoryWarnings } from '@/api/modules/report'

const router = useRouter()

const userInfo = JSON.parse(localStorage.getItem('user') || '{}')
const authMenuList = JSON.parse(localStorage.getItem('authMenuList') || '[]')

const getAllPaths = (menuList) => {
  const paths = []
  menuList.forEach(item => {
    if (item.path) paths.push(item.path)
    if (item.children && item.children.length > 0) {
      item.children.forEach(child => { if (child.path) paths.push(child.path) })
    }
  })
  return paths
}

const userPaths = getAllPaths(authMenuList)
const hasPath = (path) => userPaths.includes(path)

const REMOTE_URLS = {
  checkout: '/checkout',
  member: '/member',
  stock: '/stock',
  in: '/inventory/in',
  out: '/inventory/out',
  order: '/cashier/order'
}

const canViewSales = computed(() => hasPath('/sales') || hasPath('/checkout'))
const canViewInventory = computed(() => hasPath('/stock') || hasPath('/inventory/in'))
const canViewMember = computed(() => hasPath('/member'))
const isCashier = computed(() => hasPath('/checkout') && !hasPath('/sales'))

const roleWelcome = computed(() => {
  const role = userInfo.roleName || userInfo.role || ''
  if (role.includes('收银员')) return '您可以进入收银台开始工作。'
  if (role.includes('库存管理员')) return '您可以查看库存状态、进行入库出库操作。'
  if (role.includes('商品管理员')) return '您可以管理商品信息和库存。'
  if (role.includes('店长')) return '您可以查看经营数据和库存状态。'
  return '您可以进入各模块进行管理操作。'
})

const allQuickButtons = computed(() => {
  const buttons = []
  if (hasPath('/checkout')) buttons.push({ key: 'checkout', label: '开始收银', type: 'primary' })
  if (hasPath('/stock')) buttons.push({ key: 'stock', label: '库存查询', type: 'default' })
  if (hasPath('/member')) buttons.push({ key: 'member', label: '会员管理', type: 'default' })
  return buttons
})

const availableQuickButtons = computed(() => allQuickButtons.value)

const displayName = computed(() => userInfo.nickname || userInfo.username || '管理员')
const roleLabel = computed(() => {
  const { roles, roleName, role } = userInfo
  if (Array.isArray(roles) && roles.length) {
    return roles.map((item) => (typeof item === 'string' ? item : item?.roleName || item?.name || '')).filter(Boolean).join(' / ')
  }
  return roleName || role || '管理员'
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const todayLabel = computed(() => {
  const d = new Date()
  const week = ['周日','周一','周二','周三','周四','周五','周六'][d.getDay()]
  return `${d.getMonth()+1}月${d.getDate()}日 · ${week}`
})

const mockStats = { todo: 3, member: 12 }

const openRemote = (key) => {
  const path = REMOTE_URLS[key]
  if (!path) return
  // 用 vue-router 跳转，自动带上 base(/shop/) 前缀，避免跳到缺前缀的裸路径
  router.push(path)
}

const overview = ref({ totalSales: 0, totalOrders: 0, avgOrderValue: 0, totalMembers: 0, topProducts: [] })
const todayData = ref({ todaySales: 0, todayOrders: 0, todayAvgOrderValue: 0, newMembersToday: 0, salesGrowth: 0, ordersGrowth: 0 })
const trendData = ref([])
const selectedDays = ref(7)
const daysOptions = [7, 14, 30]
const trendLoading = ref(false)
const inventoryWarnings = ref([])
const recentOrders = ref([])

const todayCards = computed(() => [
  { key: 'todaySales', title: '今日销售额', class: 'summary-card--brand', icon: Money, trendKey: 'salesGrowth', totalKey: 'totalSales', totalFormatter: (val) => `￥${formatCurrency(val)}` },
  { key: 'todayOrders', title: '今日订单数', class: 'summary-card--cyan', icon: ShoppingCart, trendKey: 'ordersGrowth', totalKey: 'totalOrders' },
  { key: 'todayAvgOrderValue', title: '今日客单价', class: 'summary-card--purple', icon: TrendCharts, trendKey: null, totalKey: 'avgOrderValue', totalFormatter: (val) => `￥${formatCurrency(val)}` },
  { key: 'newMembersToday', title: '今日新增会员', class: 'summary-card--emerald', icon: User, trendKey: null, totalKey: 'totalMembers' }
].map(c => ({ ...c, display: formatCard(c.key) })))

// 数字滚动
const displayValues = ref({})
const animateTo = (key, target, formatter) => {
  const start = Number(displayValues.value[key] || 0)
  const end = Number(target || 0)
  if (start === end) { displayValues.value[key] = formatter ? formatter(end) : end; return }
  const dur = 800; const t0 = performance.now()
  const step = (t) => {
    const p = Math.min((t - t0) / dur, 1)
    const eased = 1 - Math.pow(1 - p, 3)
    const cur = start + (end - start) * eased
    displayValues.value[key] = formatter ? formatter(cur) : Math.round(cur)
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

watch(todayData, (val) => {
  todayCards.value.forEach((c) => animateTo(c.key, val[c.key], c.formatter))
}, { deep: true })

// AI 洞察
const aiInsights = computed(() => {
  const tips = []
  const t = todayData.value
  if (t.salesGrowth >= 0) tips.push(`今日销售额环比上升 ${t.salesGrowth}%，经营态势良好，建议保持当前促销节奏。`)
  else tips.push(`今日销售额环比下降 ${Math.abs(t.salesGrowth)}%，AI 建议关注客流/促销策略，可加大会员唤醒与爆款推荐。`)
  if (inventoryWarnings.value.length > 0) tips.push(`检测到 ${inventoryWarnings.value.length} 个库存预警商品，建议尽快补货以免断货损失销售。`)
  if ((t.todayAvgOrderValue || 0) > 0 && (t.todayOrders || 0) > 0) tips.push(`今日客单价 ￥${formatCurrency(t.todayAvgOrderValue)}，订单 ${t.todayOrders} 笔，可结合智能补货模型优化商品结构。`)
  tips.push('点右下角语音助手，可一句话语音查询订单/会员/库存。')
  return tips.length ? tips : ['暂无数据，请稍候再试。']
})

const trendChartRef = ref(null)
let trendChartInstance = null

const initCharts = () => {
  if (trendChartRef.value && !trendChartInstance) {
    trendChartInstance = echarts.init(trendChartRef.value)
  }
  updateTrendChart()
}

const updateTrendChart = () => {
  if (!trendChartInstance) return
  const categories = trendData.value.map((item) => item.day.slice(5))
  const sales = trendData.value.map((item) => Number(item.totalSales || 0))
  const orders = trendData.value.map((item) => Number(item.orderCount || 0))

  trendChartInstance.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['销售额', '订单数'], top: 0, textStyle: { color: '#475569' } },
    grid: { left: '3%', right: '4%', top: 40, bottom: 30, containLabel: true },
    xAxis: { type: 'category', data: categories, axisLabel: { fontSize: 11, color: '#64748B' }, axisLine: { lineStyle: { color: '#E2E8F0' } } },
    yAxis: [
      { type: 'value', name: '销售额(元)', axisLabel: { formatter: '￥{value}', color: '#64748B' }, splitLine: { lineStyle: { color: '#EEF2F6' } } },
      { type: 'value', name: '订单数', position: 'right', axisLabel: { color: '#64748B' }, splitLine: { show: false } }
    ],
    series: [
      { name: '销售额', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: sales,
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(79,70,229,0.35)' }, { offset: 1, color: 'rgba(79,70,229,0.03)' }]) },
        lineStyle: { color: '#4F46E5', width: 3 }, itemStyle: { color: '#4F46E5' } },
      { name: '订单数', type: 'bar', yAxisIndex: 1, barWidth: 16,
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#06B6D4' }, { offset: 1, color: '#22D3EE' }]), borderRadius: [6, 6, 0, 0] },
        data: orders }
    ]
  })
}

const loadOverview = async () => {
  const res = await getSalesOverview()
  overview.value = res.data || overview.value
}
const loadTodayData = async () => {
  const res = await getTodaySalesOverview()
  todayData.value = res.data || todayData.value
}
const loadTrend = async () => {
  try {
    trendLoading.value = true
    const res = await getSalesTrend({ days: selectedDays.value })
    trendData.value = res.data || []
    nextTick(updateTrendChart)
  } finally {
    trendLoading.value = false
  }
}
const loadInventoryWarnings = async () => {
  const res = await getInventoryWarnings()
  inventoryWarnings.value = res.data || []
}
const loadRecentOrders = async () => {
  const res = await getRecentOrders({ limit: 10 })
  recentOrders.value = res.data || []
}

function formatCurrency(val) {
  const num = Number(val || 0)
  return num.toFixed(2)
}
const MONEY_KEYS = ['todaySales', 'todayAvgOrderValue']
const formatCard = (key) => MONEY_KEYS.includes(key) ? `￥${formatCurrency(todayData.value[key])}` : Number(todayData.value[key] || 0)
const formatTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
const getTrendClass = (val) => Number(val || 0) >= 0 ? 'trend-up' : 'trend-down'
const formatTrend = (val) => {
  const num = Number(val || 0)
  const arrow = num >= 0 ? '↑' : '↓'
  return `${arrow} ${Math.abs(num)}%`
}
const getPaymentLabel = (method) => {
  const map = { 1: '现金', 2: '微信', 3: '支付宝', 4: '银行卡', 5: '刷脸', 6: '余额' }
  return map[method] || '未知'
}
const getPaymentTagType = (method) => {
  const map = { 1: 'info', 2: 'success', 3: 'primary', 4: 'warning', 5: 'danger', 6: '' }
  return map[method] || 'info'
}

const resizeCharts = () => { trendChartInstance?.resize() }

watch(selectedDays, () => { loadTrend() })

onMounted(async () => {
  await loadInventoryWarnings()
  if (canViewSales.value) {
    loadOverview()
    loadTodayData()
    loadTrend()
    loadRecentOrders()
    nextTick(initCharts)
    window.addEventListener('resize', resizeCharts)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  trendChartInstance?.dispose()
})
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 2px;
}

/* ============ Hero ============ */
.hero-card {
  position: relative;
  overflow: hidden;
  border: none !important;
  background: var(--pos-gradient-dark) !important;
  color: #fff;
}
.hero-bg { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.orb { position: absolute; border-radius: 50%; filter: blur(50px); opacity: 0.55; }
.orb-1 { width: 240px; height: 240px; background: #4F46E5; top: -80px; right: -40px; }
.orb-2 { width: 200px; height: 200px; background: #06B6D4; bottom: -90px; left: 20%; }
.grid-mask {
  position: absolute; inset: 0;
  background-image: linear-gradient(rgba(99,102,241,0.10) 1px, transparent 1px), linear-gradient(90deg, rgba(99,102,241,0.10) 1px, transparent 1px);
  background-size: 40px 40px;
  -webkit-mask-image: radial-gradient(ellipse at left, #000 30%, transparent 80%);
          mask-image: radial-gradient(ellipse at left, #000 30%, transparent 80%);
}
.hero-content { position: relative; display: flex; justify-content: space-between; gap: 24px; align-items: center; }
.hero-eyebrow { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.hero-date { font-size: 13px; font-weight: 600; color: rgba(224,231,255,0.95); letter-spacing: 0.5px; }
.pos-ai-badge { background: rgba(255,255,255,0.14); }
.hero-greeting { margin: 0; font-size: 26px; font-weight: 700; letter-spacing: 0.3px; text-shadow: 0 2px 8px rgba(15, 23, 42, 0.25); }
.hero-subtitle { margin: 8px 0 16px; font-size: 14px; color: rgba(226,232,240,0.95); }
.hero-subtitle strong { font-weight: 700; }
.hero-buttons { display: flex; gap: 12px; flex-wrap: wrap; }
.hero-right { display: flex; gap: 32px; min-width: 280px; justify-content: flex-end; }
.hero-stat { display: flex; flex-direction: column; align-items: flex-end; }
.hero-stat__label { font-size: 13px; font-weight: 600; color: rgba(199,210,254,0.95); }
.hero-stat__value { font-size: 32px; font-weight: 700; color: #fff; text-shadow: 0 2px 8px rgba(15, 23, 42, 0.25); font-variant-numeric: tabular-nums; }
.hero-stat__value.danger { color: #FECACA; }

/* ============ 汇总卡片 ============ */
.summary-row { margin-bottom: 4px; }
.summary-card {
  padding: 16px 18px;
  border: none !important;
  color: #fff;
  cursor: default;
  position: relative;
  overflow: hidden;
}
.summary-card::before {
  content: '';
  position: absolute; inset: 0;
  background: rgba(255,255,255,0.08);
  opacity: 0; transition: opacity 0.25s ease;
}
.summary-card:hover::before { opacity: 1; }
/* 科技蓝深色渐变：必须 !important 覆盖全局 .el-card 的白色背景 */
.summary-card--brand { background: linear-gradient(135deg, #0F172A 0%, #1E1B4B 55%, #3730A3 100%) !important; box-shadow: 0 8px 22px rgba(15,23,42,0.45); }
.summary-card--cyan { background: linear-gradient(135deg, #082F49 0%, #0C4A6E 55%, #0E7490 100%) !important; box-shadow: 0 8px 22px rgba(8,47,73,0.45); }
.summary-card--purple { background: linear-gradient(135deg, #1E1B4B 0%, #312E81 55%, #4F46E5 100%) !important; box-shadow: 0 8px 22px rgba(30,27,75,0.45); }
.summary-card--emerald { background: linear-gradient(135deg, #0F172A 0%, #164E63 55%, #0891B2 100%) !important; box-shadow: 0 8px 22px rgba(15,23,42,0.45); }
.summary-card--rose { background: linear-gradient(135deg, #1E293B 0%, #334155 55%, #4F46E5 100%) !important; box-shadow: 0 8px 22px rgba(30,41,59,0.45); }
.summary-card--amber { background: linear-gradient(135deg, #0F172A 0%, #1D4ED8 55%, #2563EB 100%) !important; box-shadow: 0 8px 22px rgba(15,23,42,0.45); }
.summary-head { display: flex; align-items: center; gap: 10px; position: relative; }
.summary-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 34px; height: 34px; border-radius: 10px;
  background: rgba(255,255,255,0.20); color: #fff;
  font-size: 17px;
}
.summary-title { margin: 0; font-size: 13.5px; font-weight: 600; letter-spacing: 0.3px; color: rgba(255,255,255,0.98); }
.summary-value {
  margin: 14px 0 10px; font-size: 32px; font-weight: 700; position: relative;
  font-variant-numeric: tabular-nums; letter-spacing: 0.5px;
  text-shadow: 0 2px 6px rgba(15, 23, 42, 0.18);
}
.summary-footer { display: flex; flex-direction: column; align-items: flex-start; gap: 5px; position: relative; }
.summary-trend { margin: 0; font-size: 12.5px; font-weight: 700; letter-spacing: 0.3px; }
.summary-total { margin: 0; font-size: 12.5px; font-weight: 600; color: rgba(255,255,255,0.95); font-variant-numeric: tabular-nums; }
.trend-up { color: #FFFFFF; }
.trend-down { color: #FFF1F2; }

/* ============ AI 洞察 ============ */
/* 科技蓝深色渐变：!important 覆盖全局 .el-card 白底 */
.insight-card {
  background: linear-gradient(135deg, #0F172A 0%, #1E1B4B 55%, #312E81 100%) !important;
  border: 1px solid rgba(99, 102, 241, 0.35) !important;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.45) !important;
}
.insight-card .card-title {
  color: #fff;
}
.insight-card .el-card__header {
  background: transparent !important;
  border-bottom: 1px solid rgba(99, 102, 241, 0.30) !important;
}
.insight-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.insight-grid .pos-insight {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(129, 140, 248, 0.35);
  color: rgba(255, 255, 255, 0.95);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.65;
}

/* ============ 主区 ============ */
.main-section { flex: 1; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-weight: 700; color: var(--pos-text); display: inline-flex; align-items: center; font-size: 16px; letter-spacing: 0.2px; }
.chart-panel { height: 280px; }
.hot-products-card { margin-top: 16px; }
.amount { font-weight: 700; color: #4338CA; font-variant-numeric: tabular-nums; }

.inventory-alert-card { transition: box-shadow 0.25s ease; }
.inventory-alert-card:hover { box-shadow: var(--pos-shadow-md) !important; }
.empty-alert { display: flex; flex-direction: column; align-items: center; padding: 20px; color: #059669; }
.empty-alert .el-icon { font-size: 40px; margin-bottom: 8px; }
.warning-text { color: #D97706; font-weight: 700; }
.danger-text { color: #E11D48; font-weight: 700; }

.recent-orders-card { margin-top: 16px; }
.order-flow { max-height: 320px; overflow-y: auto; }
.order-item { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #E2E8F0; }
.order-item:last-child { border-bottom: none; }
.order-info { display: flex; flex-direction: column; gap: 4px; }
.order-no { font-size: 13.5px; color: var(--pos-text); font-weight: 600; font-variant-numeric: tabular-nums; }
.order-time { font-size: 12px; color: var(--pos-text-muted); }
.order-amount { display: flex; align-items: center; gap: 8px; }

.inventory-section { margin-top: 16px; }
.inventory-summary-item { padding: 16px; }
.inventory-summary-title { margin: 0 0 12px; font-size: 16px; font-weight: 600; color: var(--pos-text); }
.quick-actions { display: flex; gap: 12px; flex-wrap: wrap; }

.cashier-section { margin-top: 16px; }
.cashier-actions { display: flex; gap: 16px; padding: 24px; }
.cashier-tips { padding: 24px; }
.tips-title { margin: 0 0 12px; font-size: 16px; font-weight: 600; color: var(--pos-text); }
.tips-list { margin: 0; padding-left: 20px; color: var(--pos-text-soft); }
.tips-list li { margin-bottom: 8px; }

@media (max-width: 992px) {
  .insight-grid { grid-template-columns: 1fr; }
  .hero-right { display: none; }
}
</style>