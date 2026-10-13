<template>
  <div class="payment-result">
    <el-result
      :icon="isSuccess ? 'success' : 'error'"
      :title="isSuccess ? '支付成功' : '支付失败'"
      :sub-title="message"
    >
      <template #extra>
        <div class="order-info" v-if="orderInfo">
          <p><strong>订单号：</strong>{{ orderInfo.out_trade_no }}</p>
          <p><strong>支付金额：</strong>￥{{ orderInfo.total_amount }}</p>
          <p><strong>交易号：</strong>{{ orderInfo.trade_no }}</p>
        </div>
        <div class="actions">
          <el-button type="primary" @click="goToCheckout">继续收银</el-button>
          <el-button @click="goToDashboard">返回首页</el-button>
        </div>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { queryAlipayPayment } from '@/api/modules/alipay'

const router = useRouter()
const route = useRoute()

const isSuccess = ref(false)
const message = ref('正在查询支付结果...')
const orderInfo = ref(null)

onMounted(async () => {
  const { out_trade_no, trade_no, total_amount, sign } = route.query

  if (!out_trade_no) {
    isSuccess.value = false
    message.value = '缺少订单信息'
    return
  }

  orderInfo.value = {
    out_trade_no,
    trade_no,
    total_amount
  }

  if (trade_no) {
    isSuccess.value = true
    message.value = '支付已完成'
    return
  }

  try {
    const res = await queryAlipayPayment(out_trade_no)
    if (res.code === 200 && res.data?.success) {
      const status = res.data.tradeStatus
      if (status === 'TRADE_SUCCESS' || status === 'TRADE_FINISHED') {
        isSuccess.value = true
        message.value = '支付成功'
        orderInfo.value.trade_no = res.data.tradeNo
      } else if (status === 'WAIT_BUYER_PAY') {
        isSuccess.value = false
        message.value = '等待支付中...'
      } else {
        isSuccess.value = false
        message.value = `支付状态: ${status}`
      }
    } else {
      isSuccess.value = false
      message.value = '查询支付状态失败'
    }
  } catch (error) {
    console.error(error)
    isSuccess.value = false
    message.value = '查询支付状态失败'
  }
})

const goToCheckout = () => {
  router.push('/checkout')
}

const goToDashboard = () => {
  router.push('/dashboard')
}
</script>

<style scoped>
.payment-result {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f7fa;
}

.order-info {
  background: #f5f7fa;
  padding: 16px 24px;
  border-radius: 8px;
  margin-bottom: 20px;
  text-align: left;
}

.order-info p {
  margin: 8px 0;
  font-size: 14px;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>