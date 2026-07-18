<template>
  <div class="assistant-visual">
    <el-card class="entry-card">
      <template #header>
        <div class="card-header">
          <span>助手智能图谱沙盘</span>
          <el-tag type="success">ECharts</el-tag>
        </div>
      </template>
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="会员号">
          <el-input
            v-model="queryForm.memberId"
            placeholder="例如：MB20250001"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="generateInsight">
            生成图谱
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        title="助手会实时调用本地 MCP 服务，生成热销商品、销售趋势等图谱供大模型参考。"
        type="info"
        show-icon
      />
    </el-card>

    <div class="charts-container">
      <div class="chart-row">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>会员画像</span>
            </div>
          </template>
          <div v-if="currentMember" class="profile-box">
            <p><strong>姓名：</strong>{{ currentMember.name }}</p>
            <p><strong>等级：</strong>{{ currentMember.level }}</p>
            <p><strong>积分：</strong>{{ currentMember.points }}</p>
            <p><strong>余额：</strong>￥{{ formatCurrency(currentMember.balance) }}</p>
          </div>
          <el-empty v-else description="请输入会员号后生成画像" />
        </el-card>
        <el-card class="chart-card" v-loading="chartLoading">
          <template #header>
            <div class="card-header">
              <span>热销商品（最近 30 天）</span>
            </div>
          </template>
          <div class="chart-panel" ref="barChartRef"></div>
        </el-card>
      </div>

      <div class="chart-row">
        <el-card class="chart-card" v-loading="chartLoading">
          <template #header>
            <div class="card-header">
              <span>近 7 天销售雷达</span>
            </div>
          </template>
          <div class="chart-panel" ref="radarChartRef"></div>
        </el-card>
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>标签建议</span>
            </div>
          </template>
          <div class="tags-cloud">
            <el-empty v-if="!personaTags.length" description="暂无标签，请先生成画像" />
            <template v-else>
              <span
                v-for="(tag, idx) in personaTags"
                :key="idx"
                class="tag-item"
                :class="tag.size"
                :style="{ color: tag.color }"
              >
                {{ tag.name }}
              </span>
            </template>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { fetchAssistantInsight, fetchMemberProfile } from '@/api/modules/assistant'

const queryForm = ref({
  memberId: ''
})

const loading = ref(false)
const chartLoading = ref(false)
const currentMember = ref(null)
const personaTags = ref([])

const barChartRef = ref(null)
const radarChartRef = ref(null)
let barChartInstance = null
let radarChartInstance = null

const formatCurrency = (val) => Number(val || 0).toFixed(2)

const generateInsight = async () => {
  if (!queryForm.value.memberId) {
    ElMessage.warning('请输入会员号')
    return
  }
  loading.value = true
  try {
    await loadMemberProfile(queryForm.value.memberId.trim())
    await loadAssistantCharts()
    ElMessage.success('助手图谱已更新')
  } catch (error) {
    console.error(error)
    if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      ElMessage.error('请求超时，MCP服务响应较慢，请稍后重试')
    } else {
      ElMessage.error(error?.message || '生成图谱失败')
    }
  } finally {
    loading.value = false
  }
}

const loadMemberProfile = async (memberId) => {
  const res = await fetchMemberProfile(memberId)
  if (res.code === 200 && res.data) {
    currentMember.value = res.data
    personaTags.value = buildPersonaTags(res.data)
  } else {
    throw new Error(res?.msg || '未查询到会员')
  }
}

const loadAssistantCharts = async () => {
  chartLoading.value = true
  try {
    const [topRes, trendRes] = await Promise.all([
      fetchAssistantInsight('top_products', { days: 30, limit: 5 }),
      fetchAssistantInsight('sales_trend', { days: 7 })
    ])

    if (topRes.code === 200 && topRes.data) {
      const list = topRes.data || []
      const categories = list.map(
        (item, idx) => item.product_name || `商品 ${idx + 1}`
      )
      const values = list.map((item) => Number(item.quantity || 0))
      updateBarChart(categories, values)
    } else {
      throw new Error(topRes?.msg || '获取热销商品失败')
    }

    if (trendRes.code === 200 && trendRes.data) {
      const radar = buildRadarFromTrend(trendRes.data || [])
      updateRadarChart(radar)
    } else {
      throw new Error(trendRes?.msg || '获取销售趋势失败')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error?.msg || error?.message || '助手图谱接口异常')
  } finally {
    chartLoading.value = false
  }
}

const buildPersonaTags = (member) => {
  const tags = []
  if (member.level?.includes('钻石') || member.level?.includes('铂金')) {
    tags.push({ name: '高价值会员', size: 'large', color: '#e6a23c' })
  }
  if ((member.points || 0) > 3000) {
    tags.push({ name: '高积分', size: 'medium', color: '#409eff' })
  }
  if ((member.balance || 0) > 1000) {
    tags.push({ name: '高余额', size: 'medium', color: '#67c23a' })
  }
  if (member.gender === 1) {
    tags.push({ name: '偏好数码/饮料', size: 'small', color: '#f56c6c' })
  } else if (member.gender === 2) {
    tags.push({ name: '关注生鲜/家清', size: 'small', color: '#909399' })
  }
  if (!tags.length) {
    tags.push({ name: '潜力会员', size: 'medium', color: '#409eff' })
  }
  return tags
}

const buildRadarFromTrend = (trend) => {
  if (!trend.length) {
    return null
  }
  const values = trend.map((item) => Number(item.order_count || 0))
  const maxValue = Math.max(...values, 1)
  return {
    indicators: trend.map((item) => ({
      name: item.date,
      max: Math.ceil(maxValue * 1.2)
    })),
    values
  }
}

const initCharts = () => {
  if (barChartRef.value && !barChartInstance) {
    barChartInstance = echarts.init(barChartRef.value)
  }
  if (radarChartRef.value && !radarChartInstance) {
    radarChartInstance = echarts.init(radarChartRef.value)
  }
}

const updateBarChart = (categories, values) => {
  if (!barChartInstance) return
  barChartInstance.setOption({
    tooltip: { 
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: { 
      left: '12%', 
      right: '5%', 
      top: '8%',
      bottom: '18%',
      containLabel: true 
    },
    xAxis: { 
      type: 'category', 
      data: categories,
      axisLabel: {
        interval: 0,
        rotate: 20,
        fontSize: 11,
        color: '#333'
      },
      axisLine: {
        lineStyle: { color: '#ddd' }
      }
    },
    yAxis: { 
      type: 'value',
      name: '销量',
      nameTextStyle: { fontSize: 12, color: '#666' },
      axisLine: { show: false },
      splitLine: {
        lineStyle: { type: 'dashed', color: '#eee' }
      }
    },
    series: [
      {
        type: 'bar',
        barWidth: '45%',
        itemStyle: { 
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#5c96ff' },
            { offset: 1, color: '#83bff6' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        data: values
      }
    ]
  })
}

const updateRadarChart = (radar) => {
  if (!radarChartInstance || !radar) {
    radarChartInstance?.clear()
    return
  }
  radarChartInstance.setOption({
    tooltip: {
      trigger: 'item'
    },
    radar: {
      indicator: radar.indicators,
      splitNumber: 4,
      radius: '60%',
      center: ['50%', '55%'],
      axisName: {
        color: '#333',
        fontSize: 11,
        fontWeight: 'normal'
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(64, 158, 255, 0.05)', 'rgba(64, 158, 255, 0.1)']
        }
      },
      axisLine: {
        lineStyle: { color: '#ddd' }
      },
      splitLine: {
        lineStyle: { color: '#eee' }
      }
    },
    series: [
      {
        type: 'radar',
        areaStyle: { 
          color: 'rgba(245, 108, 108, 0.25)'
        },
        lineStyle: { color: '#f56c6c', width: 2 },
        itemStyle: { color: '#f56c6c' },
        data: [{ value: radar.values, name: '近 7 天订单数' }]
      }
    ]
  })
}

const resetForm = () => {
  queryForm.value.memberId = ''
  currentMember.value = null
  personaTags.value = []
}

const resizeCharts = () => {
  barChartInstance?.resize()
  radarChartInstance?.resize()
}

onMounted(() => {
  nextTick(() => {
    initCharts()
    loadAssistantCharts()
    window.addEventListener('resize', resizeCharts)
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  barChartInstance?.dispose()
  radarChartInstance?.dispose()
})
</script>

<style scoped>
.assistant-visual {
  display: flex;
  flex-direction: column;
  padding: 20px;
  gap: 16px;
  height: calc(100vh - 100px);
  box-sizing: border-box;
}

.entry-card {
  border-left: 3px solid #67c23a;
  flex-shrink: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.charts-container {
  flex: 1;
  display: grid;
  grid-template-rows: 1fr 1fr;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  min-height: 0;
}

.chart-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chart-card :deep(.el-card__header) {
  padding: 12px 16px;
  flex-shrink: 0;
}

.chart-card :deep(.el-card__body) {
  flex: 1;
  padding: 12px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.chart-panel {
  flex: 1;
  width: 100%;
  min-height: 0;
}

.profile-box {
  padding: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.profile-box p {
  margin: 10px 0;
  font-size: 15px;
  line-height: 1.8;
}

.tags-cloud {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-content: center;
  justify-content: center;
  gap: 12px;
  padding: 16px;
}

.tag-item {
  display: inline-block;
  padding: 6px 14px;
  border-radius: 4px;
  font-weight: 500;
  background: rgba(0, 0, 0, 0.04);
  transition: all 0.3s;
}

.tag-item:hover {
  transform: scale(1.05);
  background: rgba(0, 0, 0, 0.08);
}

.tag-item.large {
  font-size: 20px;
  padding: 10px 20px;
}

.tag-item.medium {
  font-size: 16px;
  padding: 8px 16px;
}

.tag-item.small {
  font-size: 14px;
  padding: 6px 12px;
}
</style>
