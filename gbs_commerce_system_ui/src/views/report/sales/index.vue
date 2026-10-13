<template>
  <div class="dashboard-container">
    <el-row :gutter="16" class="summary-row">
      <el-col :span="6" v-for="card in todayCards" :key="card.key">
        <el-card :class="['summary-card', card.class]">
          <p class="summary-title">{{ card.title }}</p>
          <p class="summary-value">{{ card.formatter(todayData[card.key]) }}</p>
          <p class="summary-trend" :class="getTrendClass(todayData[card.trendKey])">
            {{ formatTrend(todayData[card.trendKey]) }}
          </p>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="main-section">
      <el-col :span="16">
        <el-card v-loading="trendLoading">
          <template #header>
            <div class="card-header">
              <span>最近 {{ selectedDays }} 天销售趋势</span>
              <el-select v-model="selectedDays" size="small" style="width: 120px">
                <el-option v-for="option in daysOptions" :key="option" :label="`近 ${option} 天`" :value="option" />
              </el-select>
            </div>
          </template>
          <div class="chart-panel" ref="trendChartRef"></div>
        </el-card>

        <el-card class="hot-products-card">
          <template #header>
            <div class="card-header">
              <span>热销商品 TOP10</span>
              <el-tag size="small" type="warning">实时</el-tag>
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
        <el-card class="inventory-alert-card">
          <template #header>
            <div class="card-header">
              <span>库存预警</span>
              <el-badge :value="inventoryWarnings.length" :max="99" type="danger" />
            </div>
          </template>
          <div v-if="inventoryWarnings.length === 0" class="empty-alert">
            <el-icon><CircleCheck /></el-icon>
            <span>暂无库存预警</span>
          </div>
          <el-table v-else :data="inventoryWarnings" size="small" max-height="280" stripe>
            <el-table-column prop="productName" label="商品名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="stockQuantity" label="当前库存" width="90" align="right">
              <template #default="{ row }">
                <span class="warning-text">{{ row.stockQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="warningQuantity" label="预警阈值" width="90" align="right" />
            <el-table-column prop="diff" label="差值" width="80" align="right">
              <template #default="{ row }">
                <span :class="row.diff < 0 ? 'danger-text' : 'warning-text'">{{ row.diff }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="recent-orders-card">
          <template #header>
            <div class="card-header">
              <span>实时订单流</span>
              <el-tag size="small" type="success">最新10笔</el-tag>
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
                <el-tag size="small" :type="getPaymentTagType(order.paymentMethod)">
                  {{ getPaymentLabel(order.paymentMethod) }}
                </el-tag>
              </div>
            </div>
            <el-empty v-if="recentOrders.length === 0" description="暂无订单" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { CircleCheck } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getSalesOverview, getTodaySalesOverview, getSalesTrend, getRecentOrders, getInventoryWarnings } from '@/api/modules/report'

const overview = ref({
  totalSales: 0,
  totalOrders: 0,
  avgOrderValue: 0,
  totalMembers: 0,
  topProducts: []
})

const todayData = ref({
  todaySales: 0,
  todayOrders: 0,
  todayAvgOrderValue: 0,
  newMembersToday: 0,
  salesGrowth: 0,
  ordersGrowth: 0
})

const trendData = ref([])
const selectedDays = ref(7)
const daysOptions = [7, 14, 30]
const trendLoading = ref(false)
const inventoryWarnings = ref([])
const recentOrders = ref([])

const todayCards = [
  {
    key: 'todaySales',
    title: '今日销售额',
    class: 'summary-card--gold',
    trendKey: 'salesGrowth',
    formatter: (val) => `￥${formatCurrency(val)}`
  },
  {
    key: 'todayOrders',
    title: '今日订单数',
    class: 'summary-card--blue',
    trendKey: 'ordersGrowth',
    formatter: (val) => Number(val || 0)
  },
  {
    key: 'todayAvgOrderValue',
    title: '今日客单价',
    class: 'summary-card--green',
    trendKey: null,
    formatter: (val) => `￥${formatCurrency(val)}`
  },
  {
    key: 'newMembersToday',
    title: '今日新增会员',
    class: 'summary-card--purple',
    trendKey: null,
    formatter: (val) => Number(val || 0)
  }
]

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
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: { data: ['销售额', '订单数'], top: 0 },
    grid: { left: '3%', right: '4%', top: 40, bottom: 30, containLabel: true },
    xAxis: { type: 'category', data: categories, axisLabel: { fontSize: 11 } },
    yAxis: [
      { type: 'value', name: '销售额(元)', axisLabel: { formatter: '￥{value}' } },
      { type: 'value', name: '订单数', position: 'right' }
    ],
    series: [
      {
        name: '销售额',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: sales,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(255,159,64,0.4)' },
            { offset: 1, color: 'rgba(255,159,64,0.05)' }
          ])
        },
        lineStyle: { color: '#ff9f40', width: 2 },
        itemStyle: { color: '#ff9f40' }
      },
      {
        name: '订单数',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: 16,
        itemStyle: { color: '#5c96ff', borderRadius: [4, 4, 0, 0] },
        data: orders
      }
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

const formatCurrency = (val) => {
  const num = Number(val || 0)
  return num.toFixed(2)
}

const formatTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

const getTrendClass = (val) => {
  const num = Number(val || 0)
  return num >= 0 ? 'trend-up' : 'trend-down'
}

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

const resizeCharts = () => {
  trendChartInstance?.resize()
}

watch(selectedDays, () => {
  loadTrend()
})

onMounted(() => {
  loadOverview()
  loadTodayData()
  loadTrend()
  loadInventoryWarnings()
  loadRecentOrders()
  nextTick(initCharts)
  window.addEventListener('resize', resizeCharts)
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
  padding: 4px;
}

.summary-row {
  margin-bottom: 4px;
}

.summary-card {
  padding: 14px;
  border: none;
  color: #fff;
  cursor: default;
}

.summary-card--gold {
  background: linear-gradient(135deg, #f6d365, #fda085);
}

.summary-card--blue {
  background: linear-gradient(135deg, #5c96ff, #6dd5fa);
}

.summary-card--green {
  background: linear-gradient(135deg, #42e695, #3bb2b8);
}

.summary-card--purple {
  background: linear-gradient(135deg, #a18cd1, #fbc2eb);
}

.summary-title {
  margin: 0;
  font-size: 13px;
  opacity: 0.85;
}

.summary-value {
  margin: 8px 0 4px;
  font-size: 26px;
  font-weight: 700;
}

.summary-trend {
  margin: 0;
  font-size: 12px;
}

.trend-up {
  color: #fff;
}

.trend-down {
  color: #ffeded;
}

.main-section {
  flex: 1;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-panel {
  height: 280px;
}

.hot-products-card {
  margin-top: 16px;
}

.amount {
  font-weight: 600;
  color: #e6a23c;
}

.inventory-alert-card {
  transition: all 0.3s;
}

.inventory-alert-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.empty-alert {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  color: #67c23a;
}

.empty-alert .el-icon {
  font-size: 40px;
  margin-bottom: 8px;
}

.warning-text {
  color: #e6a23c;
  font-weight: 600;
}

.danger-text {
  color: #f56c6c;
  font-weight: 600;
}

.recent-orders-card {
  margin-top: 16px;
}

.order-flow {
  max-height: 320px;
  overflow-y: auto;
}

.order-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}

.order-item:last-child {
  border-bottom: none;
}

.order-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.order-no {
  font-size: 13px;
  color: #333;
  font-weight: 500;
}

.order-time {
  font-size: 11px;
  color: #999;
}

.order-amount {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
