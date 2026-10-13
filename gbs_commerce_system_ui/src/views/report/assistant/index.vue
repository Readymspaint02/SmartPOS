<template>
  <div class="member-analysis">
    <el-card class="entry-card">
      <template #header>
        <div class="card-header">
          <span>会员画像分析</span>
          <el-tag type="success">RFM模型</el-tag>
        </div>
      </template>
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="会员号/手机号">
          <el-input
            v-model="queryForm.memberId"
            placeholder="例如：MB20250001 或手机号"
            style="width: 260px"
            clearable
            @keyup.enter="analyzeMember"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="analyzeMember">
            生成画像
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        title="基于RFM模型分析会员消费特征，提供个性化商品推荐和营销策略建议"
        type="info"
        show-icon
      />
    </el-card>

    <div v-if="analysisData" class="analysis-container">
      <div class="top-row">
        <el-card class="member-profile-card">
          <template #header>
            <div class="card-header">
              <span>会员信息</span>
              <el-tag :type="getRfmTagType(analysisData.rfmSegment)" size="large">
                {{ analysisData.rfmSegment }}
              </el-tag>
            </div>
          </template>
          <div class="profile-content">
            <div class="profile-main">
              <div class="avatar">{{ getAvatarName(analysisData.memberInfo?.name) }}</div>
              <div class="info">
                <p class="name">{{ analysisData.memberInfo?.name || '未知' }}</p>
                <p class="level">{{ analysisData.memberInfo?.level || '普通会员' }}</p>
              </div>
            </div>
            <div class="profile-stats">
              <div class="stat-item">
                <span class="stat-value">{{ analysisData.memberInfo?.points || 0 }}</span>
                <span class="stat-label">积分</span>
              </div>
              <div class="stat-item">
                <span class="stat-value">￥{{ formatCurrency(analysisData.memberInfo?.balance) }}</span>
                <span class="stat-label">余额</span>
              </div>
              <div class="stat-item">
                <span class="stat-value">{{ analysisData.memberInfo?.phone || '--' }}</span>
                <span class="stat-label">手机号</span>
              </div>
            </div>
          </div>
        </el-card>

        <el-card class="consumption-card">
          <template #header>
            <span>消费概览</span>
          </template>
          <div class="consumption-grid">
            <div class="consumption-item">
              <p class="value">￥{{ formatCurrency(analysisData.consumption?.totalAmount) }}</p>
              <p class="label">累计消费金额</p>
            </div>
            <div class="consumption-item">
              <p class="value">{{ analysisData.consumption?.orderCount || 0 }}</p>
              <p class="label">累计订单数</p>
            </div>
            <div class="consumption-item">
              <p class="value">￥{{ formatCurrency(analysisData.consumption?.avgOrderValue) }}</p>
              <p class="label">客单价</p>
              <p class="diff" :class="analysisData.consumption?.avgDiffPercent >= 0 ? 'up' : 'down'">
                {{ analysisData.consumption?.avgDiffPercent >= 0 ? '高于' : '低于' }}均值
                {{ Math.abs(analysisData.consumption?.avgDiffPercent || 0) }}%
              </p>
            </div>
            <div class="consumption-item">
              <p class="value">{{ formatDate(analysisData.consumption?.lastOrderTime) }}</p>
              <p class="label">最近消费</p>
            </div>
          </div>
        </el-card>

        <el-card class="rfm-card">
          <template #header>
            <span>RFM分层</span>
          </template>
          <div class="rfm-content">
            <div class="rfm-chart" ref="rfmChartRef"></div>
            <div class="rfm-desc">
              <p><strong>当前分层：</strong>{{ analysisData.rfmSegment }}</p>
              <p class="suggestion">{{ getRfmSuggestion(analysisData.rfmSegment) }}</p>
            </div>
          </div>
        </el-card>
      </div>

      <div class="middle-row">
        <el-card class="category-card">
          <template #header>
            <div class="card-header">
              <span>品类偏好</span>
              <el-tag size="small">基于消费历史</el-tag>
            </div>
          </template>
          <div class="category-content">
            <div class="category-chart" ref="categoryChartRef"></div>
            <el-table :data="analysisData.categoryPreference || []" size="small" stripe>
              <el-table-column prop="categoryName" label="品类" />
              <el-table-column prop="quantity" label="购买次数" width="100" />
              <el-table-column prop="percent" label="占比" width="100">
                <template #default="{ row }">
                  {{ row.percent }}%
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>

        <el-card class="trend-card">
          <template #header>
            <div class="card-header">
              <span>消费趋势</span>
              <el-tag size="small">近6个月</el-tag>
            </div>
          </template>
          <div class="trend-chart" ref="trendChartRef"></div>
        </el-card>
      </div>

      <el-card class="recommend-card">
        <template #header>
          <div class="card-header">
            <span>个性化推荐</span>
            <el-tag type="warning" size="small">AI推荐</el-tag>
          </div>
        </template>
        <div class="recommend-list">
          <div v-for="(item, idx) in analysisData.recommendations || []" :key="idx" class="recommend-item">
            <div class="item-info">
              <span class="item-name">{{ item.productName }}</span>
              <el-tag size="small" type="info">{{ item.categoryName }}</el-tag>
            </div>
            <span class="item-price">￥{{ formatCurrency(item.price) }}</span>
          </div>
          <el-empty v-if="!analysisData.recommendations?.length" description="暂无推荐数据" />
        </div>
      </el-card>
    </div>

    <el-empty v-else description="请输入会员号生成画像分析" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { fetchMemberAnalysis } from '@/api/modules/assistant'
import { getMemberList } from '@/api/modules/member'

const queryForm = ref({
  memberId: ''
})

const loading = ref(false)
const analysisData = ref(null)

const rfmChartRef = ref(null)
const categoryChartRef = ref(null)
const trendChartRef = ref(null)
let rfmChartInstance = null
let categoryChartInstance = null
let trendChartInstance = null

const analyzeMember = async () => {
  if (!queryForm.value.memberId) {
    ElMessage.warning('请输入会员号或手机号')
    return
  }
  loading.value = true
  try {
    const res = await fetchMemberAnalysis(queryForm.value.memberId.trim())
    if (res.code === 200 && res.data) {
      analysisData.value = res.data
      nextTick(() => {
        initCharts()
        updateCharts()
      })
      ElMessage.success('画像生成成功')
    } else {
      ElMessage.error(res?.msg || '未查询到会员信息')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  queryForm.value.memberId = ''
  analysisData.value = null
}

const initCharts = () => {
  if (rfmChartRef.value && !rfmChartInstance) {
    rfmChartInstance = echarts.init(rfmChartRef.value)
  }
  if (categoryChartRef.value && !categoryChartInstance) {
    categoryChartInstance = echarts.init(categoryChartRef.value)
  }
  if (trendChartRef.value && !trendChartInstance) {
    trendChartInstance = echarts.init(trendChartRef.value)
  }
}

const updateCharts = () => {
  updateRfmChart()
  updateCategoryChart()
  updateTrendChart()
}

const updateRfmChart = () => {
  if (!rfmChartInstance || !analysisData.value) return
  
  const segment = analysisData.value.rfmSegment
  const scores = {
    '高价值会员': [95, 90, 95],
    '潜力会员': [75, 70, 80],
    '活跃会员': [60, 50, 60],
    '流失预警会员': [30, 40, 35],
    '普通会员': [50, 50, 50]
  }
  const values = scores[segment] || [50, 50, 50]

  rfmChartInstance.setOption({
    radar: {
      indicator: [
        { name: 'R(最近消费)', max: 100 },
        { name: 'F(消费频次)', max: 100 },
        { name: 'M(消费金额)', max: 100 }
      ],
      radius: '65%',
      axisName: { color: '#333', fontSize: 11 },
      splitArea: {
        areaStyle: {
          color: ['rgba(64, 158, 255, 0.1)', 'rgba(64, 158, 255, 0.2)']
        }
      }
    },
    series: [{
      type: 'radar',
      data: [{
        value: values,
        name: segment,
        areaStyle: { color: 'rgba(103, 194, 58, 0.3)' },
        lineStyle: { color: '#67c23a', width: 2 },
        itemStyle: { color: '#67c23a' }
      }]
    }]
  })
}

const updateCategoryChart = () => {
  if (!categoryChartInstance || !analysisData.value?.categoryPreference) return

  const data = analysisData.value.categoryPreference.map(item => ({
    name: item.categoryName,
    value: Number(item.amount || 0)
  }))

  categoryChartInstance.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: ￥{c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 0,
      top: 'center',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { fontSize: 11 }
    },
    series: [{
      type: 'pie',
      radius: ['35%', '55%'],
      center: ['30%', '50%'],
      data: data,
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 12 }
      }
    }]
  })
}

const updateTrendChart = () => {
  if (!trendChartInstance || !analysisData.value?.consumptionTrend) return

  const months = analysisData.value.consumptionTrend.map(item => item.month.slice(5))
  const amounts = analysisData.value.consumptionTrend.map(item => Number(item.amount || 0))
  const orders = analysisData.value.consumptionTrend.map(item => Number(item.orderCount || 0))

  trendChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['消费金额', '订单数'], top: 0 },
    grid: { left: '3%', right: '4%', top: 40, bottom: 30, containLabel: true },
    xAxis: { type: 'category', data: months },
    yAxis: [
      { type: 'value', name: '金额(元)' },
      { type: 'value', name: '订单数', position: 'right' }
    ],
    series: [
      {
        name: '消费金额',
        type: 'line',
        smooth: true,
        data: amounts,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.4)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        },
        lineStyle: { color: '#409eff', width: 2 },
        itemStyle: { color: '#409eff' }
      },
      {
        name: '订单数',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: 16,
        itemStyle: { color: '#f7ba1e', borderRadius: [4, 4, 0, 0] },
        data: orders
      }
    ]
  })
}

const formatCurrency = (val) => {
  const num = Number(val || 0)
  return num.toFixed(2)
}

const formatDate = (date) => {
  if (!date) return '--'
  return new Date(date).toLocaleDateString('zh-CN')
}

const getAvatarName = (name) => {
  if (!name) return '?'
  return name.slice(0, 1)
}

const getRfmTagType = (segment) => {
  const map = {
    '高价值会员': 'success',
    '潜力会员': 'primary',
    '活跃会员': 'info',
    '流失预警会员': 'danger',
    '普通会员': 'warning'
  }
  return map[segment] || 'info'
}

const getRfmSuggestion = (segment) => {
  const suggestions = {
    '高价值会员': '建议提供VIP专属服务、优先体验新品、生日专属优惠',
    '潜力会员': '建议加大促销力度、提升消费频次，培养忠诚度',
    '活跃会员': '建议推送会员活动、积分翻倍等营销活动',
    '流失预警会员': '建议发送召回优惠券、电话回访了解原因',
    '普通会员': '建议推送新人礼包、首单优惠等活动'
  }
  return suggestions[segment] || ''
}

const resizeCharts = () => {
  rfmChartInstance?.resize()
  categoryChartInstance?.resize()
  trendChartInstance?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', resizeCharts)
  try {
    const res = await getMemberList()
    if (res.code === 200 && res.data?.length > 0) {
      const randomIndex = Math.floor(Math.random() * res.data.length)
      const randomMember = res.data[randomIndex]
      queryForm.value.memberId = randomMember.memberId || randomMember.phone
      await analyzeMember()
    }
  } catch (error) {
    console.error('加载会员列表失败:', error)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  rfmChartInstance?.dispose()
  categoryChartInstance?.dispose()
  trendChartInstance?.dispose()
})
</script>

<style scoped>
.member-analysis {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px;
}

.entry-card {
  border-left: 3px solid #67c23a;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.analysis-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.top-row {
  display: grid;
  grid-template-columns: 1fr 1.5fr 1fr;
  gap: 16px;
}

.member-profile-card .profile-content {
  padding: 8px;
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: bold;
}

.profile-main .info .name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.profile-main .info .level {
  margin: 4px 0 0;
  color: #909399;
  font-size: 13px;
}

.profile-stats {
  display: flex;
  justify-content: space-around;
  border-top: 1px solid #f0f0f0;
  padding-top: 16px;
}

.stat-item {
  text-align: center;
}

.stat-value {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.consumption-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  padding: 8px;
}

.consumption-item {
  text-align: center;
  padding: 12px;
  background: #f9fafc;
  border-radius: 8px;
}

.consumption-item .value {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
}

.consumption-item .label {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}

.consumption-item .diff {
  margin: 4px 0 0;
  font-size: 11px;
}

.consumption-item .diff.up {
  color: #67c23a;
}

.consumption-item .diff.down {
  color: #f56c6c;
}

.rfm-content {
  padding: 8px;
}

.rfm-chart {
  height: 160px;
}

.rfm-desc {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.rfm-desc p {
  margin: 0;
  font-size: 13px;
}

.rfm-desc .suggestion {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.middle-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.category-content {
  display: flex;
  gap: 16px;
}

.category-chart {
  width: 280px;
  height: 200px;
  flex-shrink: 0;
}

.category-content .el-table {
  flex: 1;
}

.trend-chart {
  height: 250px;
}

.recommend-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.recommend-item {
  width: calc(20% - 10px);
  padding: 12px;
  background: #f9fafc;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.item-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-name {
  font-size: 13px;
  font-weight: 500;
}

.item-price {
  font-size: 14px;
  font-weight: 600;
  color: #e6a23c;
}

@media (max-width: 1200px) {
  .top-row {
    grid-template-columns: 1fr;
  }
  .middle-row {
    grid-template-columns: 1fr;
  }
  .recommend-item {
    width: calc(33.33% - 8px);
  }
}
</style>
