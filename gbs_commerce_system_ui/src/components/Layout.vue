<template>
  <div class="common-layout">
    <el-container class="layout-container">
      <el-header class="layout-header">
        <div class="header-content">
          <div class="header-brand">
            <span class="brand-orb">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 9l1-4h16l1 4" />
                <rect x="4" y="9" width="16" height="11" rx="2" />
                <path d="M9 14h6" /><circle cx="17" cy="14" r="0.5" fill="currentColor" />
              </svg>
            </span>
            <div class="brand-text">
              <h1 class="brand-title">智慧收银系统</h1>
              <span class="brand-sub">SmartPOS · AI 新零售中台</span>
            </div>
            <span class="pos-ai-badge pos-ai-badge--header">AI · 智能</span>
          </div>
          <div class="header-actions">
            <div class="user-info">
              <span class="welcome-text">您好，{{ getCurrentUserRole() }}：</span>
              <el-upload
                class="avatar-uploader"
                action="/api/system/user/updateAvatar"
                :show-file-list="false"
                :on-success="handleAvatarSuccess"
                :before-upload="beforeAvatarUpload"
                :headers="uploadHeaders"
              >
                <el-avatar v-if="currentUser.avatar" :src="'/api' + currentUser.avatar" size="small" />
                <el-avatar v-else :src="defaultAvatar" size="small" />
              </el-upload>
              <span class="username">{{ currentUser.username }}</span>
            </div>
            <el-dropdown @command="handleUserCommand">
              <el-button size="small" type="primary">
                个人中心
                <el-icon class="el-icon--right"><arrow-down /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="faceRegister">人脸注册</el-dropdown-item>
                  <el-dropdown-item command="changePassword">修改密码</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>
      <el-container>
        <el-aside width="216px" class="layout-aside">
          <el-menu
            :default-active="route.path"
            router
            unique-opened
            class="layout-menu"
            background-color="transparent"
            text-color="#CBD5E1"
            active-text-color="#FFFFFF"
          >
            <template v-for="item in visibleMenuList" :key="item.id">
              <el-sub-menu v-if="item.children && item.children.length > 0" :index="item.path">
                <template #title>
                  <el-icon><component :is="getMenuIcon(item.path)" /></el-icon>
                  <span>{{ item.menuName }}</span>
                </template>
                <el-menu-item v-for="child in item.children" :key="child.id" :index="child.path">
                  <el-icon><component :is="getMenuIcon(child.path)" /></el-icon>
                  <span>{{ child.menuName }}</span>
                </el-menu-item>
              </el-sub-menu>
              <el-menu-item v-else :index="item.path">
                <el-icon><component :is="getMenuIcon(item.path)" /></el-icon>
                <span>{{ item.menuName }}</span>
              </el-menu-item>
            </template>
          </el-menu>
        </el-aside>
        <el-main class="layout-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
  <el-dialog
    title="修改密码"
    v-model="passwordDialogVisible"
    width="500px"
    @close="handlePasswordDialogClose"
  >
    <el-form
      :model="passwordForm"
      :rules="passwordFormRules"
      ref="passwordFormRef"
      label-width="100px"
    >
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="passwordForm.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPasswordForm">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog
    title="人脸注册"
    v-model="faceDialogVisible"
    width="420px"
    @open="openFaceDialog"
    @close="closeFaceDialog"
  >
    <div class="face-register-body">
      <div class="face-camera-box">
        <video ref="faceVideoRef" autoplay playsinline class="face-video"></video>
        <canvas ref="faceCanvasRef" width="320" height="240" style="display: none"></canvas>
        <p v-if="!faceStreaming" class="face-tip">点击下方按钮打开摄像头</p>
      </div>
      <p class="face-status">
        当前状态：<el-tag :type="faceRegistered ? 'success' : 'info'" size="small">
          {{ faceRegistered ? '已注册' : '未注册' }}
        </el-tag>
      </p>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button v-if="!faceStreaming" @click="startFaceCamera">打开摄像头</el-button>
        <el-button v-else @click="stopFaceCamera">关闭摄像头</el-button>
        <el-button type="primary" :loading="faceRegistering" :disabled="!faceStreaming" @click="submitFaceRegister">
          采集并注册
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, HomeFilled, Goods, ShoppingCart, User, Document, DataAnalysis, Setting, Grid, Money, List } from '@element-plus/icons-vue'
import { changePassword } from '@/api/modules/user'
import defaultAvatar from '@/assets/anon.jpeg'
import { he } from 'element-plus/es/locales.mjs'
const router = useRouter()
const route = useRoute()
const passwordFormRef = ref(null)
const passwordDialogVisible = ref(false)
const currentUser = ref({})

let authMenuList = localStorage.getItem('authMenuList')
if (authMenuList) {
  authMenuList = JSON.parse(authMenuList)
}
const uploadHeaders = {
  Authorization:'Bearer ' + localStorage.getItem('token')
}
const getCurrentUserRole = () => {
  if (currentUser.value && currentUser.value.roles && currentUser.value.roles.length > 0) {
    // 如果用户有多个角色，显示第一个角色的名称
    return currentUser.value.roles[0].roleName || '未知角色'
  }
  return '未知角色'
}
const handleAvatarSuccess= (response,uploadFile
) => {
  if (response.code !== 200) {
    ElMessage.error(response.msg||'头像更新失败')
    return false
  }
  currentUser.value.avatar = response.data
  ElMessage.success('头像更新成功')
  // 更新本地存储的用户信息
  const userStr = localStorage.getItem('user')
  if (userStr) {
    const user = JSON.parse(userStr)
    user.avatar = response.data
    localStorage.setItem('user', JSON.stringify(user))
  }
}

const beforeAvatarUpload = (rawFile) => {
  if (rawFile.type !== 'image/jpeg'&&rawFile.type !== 'image/png') {
    ElMessage.error('头像必须是 JPG 或 PNG 格式!')
    return false
  } else if (rawFile.size / 1024 / 1024 > 2) {
    ElMessage.error('头像大小不能超过 2MB!')
    return false
  }
  return true
}
// 修改密码表单数据
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 修改密码表单验证规则
const passwordFormRules = reactive({
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
})

const handleUserCommand = (command) => {
  if (command === 'changePassword') {
    // 打开修改密码对话框
    passwordDialogVisible.value = true
  } else if (command === 'faceRegister') {
    // 打开人脸注册对话框
    faceDialogVisible.value = true
  } else if (command === 'logout') {
    // 退出登录
    handleLogout()
  }
}

// ========== 人脸注册（自助） ==========
import http from '@/api/request'

const faceDialogVisible = ref(false)
const faceVideoRef = ref(null)
const faceCanvasRef = ref(null)
const faceStreaming = ref(false)
const faceRegistering = ref(false)
const faceRegistered = ref(false)
let faceMediaStream = null

const openFaceDialog = async () => {
  try {
    const res = await http.get('/face/check', { params: { userId: String(currentUser.value.id) } })
    faceRegistered.value = res.code === 200 && res.data?.registered === true
  } catch (e) {
    faceRegistered.value = false
  }
}

const closeFaceDialog = () => {
  stopFaceCamera()
}

const startFaceCamera = async () => {
  try {
    faceMediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: 640, height: 480 }
    })
    if (faceVideoRef.value) {
      faceVideoRef.value.srcObject = faceMediaStream
      faceStreaming.value = true
    }
  } catch (e) {
    ElMessage.error('无法打开摄像头')
  }
}

const stopFaceCamera = () => {
  if (faceMediaStream) {
    faceMediaStream.getTracks().forEach(t => t.stop())
    faceMediaStream = null
  }
  faceStreaming.value = false
}

const submitFaceRegister = async () => {
  if (!faceVideoRef.value || !faceCanvasRef.value) return
  try {
    faceRegistering.value = true
    const video = faceVideoRef.value
    const canvas = faceCanvasRef.value
    const ctx = canvas.getContext('2d')
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    const base64 = canvas.toDataURL('image/jpeg', 0.9).split(',')[1]
    const res = await http.post('/face/register', {
      userId: String(currentUser.value.id),
      image: base64,
      userInfo: currentUser.value.nickname || currentUser.value.username || ''
    })
    if (res.code === 200) {
      faceRegistered.value = true
      ElMessage.success('人脸注册成功，下次可用人脸登录')
      stopFaceCamera()
    } else {
      ElMessage.error(res.msg || '人脸注册失败')
    }
  } catch (e) {
    ElMessage.error(e?.msg || '人脸注册异常')
  } finally {
    faceRegistering.value = false
  }
}
// ========== 人脸注册结束 ==========

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('authMenuList')
  router.push('/login')
}

// 提交修改密码表单
const submitPasswordForm = async () => {
  passwordFormRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      // 调用修改密码的API
      const res = await changePassword(currentUser.value.id,passwordForm)
      if (res.code === 200) {
        ElMessage.success('密码修改成功')
        passwordDialogVisible.value = false
        handlePasswordDialogClose()
      }
    } catch (error) {
      console.error(error)
      ElMessage.error('密码修改失败')
    }
  })
}

// 关闭修改密码对话框时重置表单
const handlePasswordDialogClose = () => {
  passwordFormRef.value?.resetFields()
  Object.assign(passwordForm, {
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
  })
}
// 获取当前用户信息
const getCurrentUser = () => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    currentUser.value = JSON.parse(userStr)
  }
}
// 页面加载时初始化
onMounted(() => {
  getCurrentUser()
})

const getMenuIcon = (path) => {
  const iconMap = {
    '/dashboard': HomeFilled,
    '/product': Goods,
    '/product/list': List,
    '/product/category': Grid,
    '/cashier': ShoppingCart,
    '/cashier/checkout': Money,
    '/cashier/order': Document,
    '/inventory': Goods,
    '/inventory/stock': DataAnalysis,
    '/inventory/in': Document,
    '/inventory/out': Document,
    '/report': DataAnalysis,
    '/report/sales': DataAnalysis,
    '/report/assistant': User,
    '/system': Setting,
    '/system/user': User,
    '/system/role': User,
    '/system/menu': List,
    '/system/test': Setting,
  }
  return iconMap[path] || Document
}

const visibleMenuList = computed(() => {
  const filterMenus = (menus) => {
    return menus
      .filter(menu => menu.visible !== 1)
      .map(menu => ({
        ...menu,
        children: menu.children ? filterMenus(menu.children) : []
      }))
  }
  return authMenuList ? filterMenus(authMenuList) : []
})
</script>

<style scoped>
.common-layout {
  height: 100vh;
}

.layout-container {
  height: 100%;
}

/* ============ 顶栏 ============ */
.layout-header {
  background: var(--pos-gradient-dark);
  color: #fff;
  height: 64px;
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.25);
  z-index: 100;
  position: relative;
}
.layout-header::after {
  content: '';
  position: absolute;
  left: 0; right: 0; bottom: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(6,182,212,0.6), transparent);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  padding: 0 24px;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}
.brand-orb {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--pos-gradient-brand);
  color: #fff;
  box-shadow: var(--pos-shadow-glow);
}
.brand-text { display: flex; flex-direction: column; line-height: 1.2; }
.brand-title {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.brand-sub {
  font-size: 11px;
  color: rgba(199, 210, 254, 0.85);
  letter-spacing: 1px;
  margin-top: 2px;
}
.pos-ai-badge--header {
  margin-left: 4px;
  background: rgba(255,255,255,0.12);
  backdrop-filter: blur(6px);
  box-shadow: 0 0 0 1px rgba(255,255,255,0.18) inset;
}

/* ============ 右侧操作 ============ */
.header-actions {
  display: flex;
  align-items: center;
  height: 100%;
}
.user-info {
  display: flex;
  align-items: center;
  margin-right: 20px;
}
.welcome-text {
  color: rgba(226, 232, 240, 0.9);
  font-weight: 500;
  margin-right: 10px;
  font-size: 13px;
}
.avatar-uploader :deep(.el-upload) {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.username {
  margin-left: 8px;
  color: #fff;
  font-weight: 500;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
}

/* ============ 侧栏 ============ */
.layout-aside {
  background: var(--pos-gradient-dark);
  color: #fff;
  overflow: hidden;
  box-shadow: 4px 0 24px rgba(15, 23, 42, 0.28);
  z-index: 99;
  position: relative;
}
.layout-aside::after {
  content: '';
  position: absolute;
  top: 0; bottom: 0; right: 0;
  width: 1px;
  background: linear-gradient(180deg, transparent, rgba(6,182,212,0.35), transparent);
}
.layout-menu {
  border-right: none;
  height: calc(100vh - 64px);
  padding: 10px 12px;
  background: transparent;
}

/* 菜单项通用 */
.layout-menu :deep(.el-menu-item),
.layout-menu :deep(.el-sub-menu__title) {
  border-radius: 10px;
  margin: 4px 0;
  transition: all 0.2s ease;
}
.layout-menu :deep(.el-menu-item:hover),
.layout-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(99, 102, 241, 0.18) !important;
  color: #fff !important;
}
.layout-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(79,70,229,0.55), rgba(6,182,212,0.25)) !important;
  color: #fff !important;
  box-shadow: inset 3px 0 0 0 #06B6D4, 0 4px 14px rgba(79,70,229,0.35);
}
.layout-menu :deep(.el-sub-menu .el-menu) {
  background: rgba(15, 23, 42, 0.35) !important;
  border-radius: 10px;
  margin: 4px 8px;
}

/* ============ 主区 ============ */
.layout-main {
  background: var(--pos-bg);
  padding: 22px;
  overflow: auto;
}
.layout-main::-webkit-scrollbar { width: 6px; }
.layout-main::-webkit-scrollbar-thumb { background: rgba(79,70,229,0.2); border-radius: 3px; }
.layout-main::-webkit-scrollbar-track { background: transparent; }

/* ============ 人脸注册对话框 ============ */
.face-register-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.face-camera-box {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: linear-gradient(135deg, #0F172A, #1E293B);
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.face-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
  display: block;
}
.face-tip {
  position: absolute;
  color: #94A3B8;
  font-size: 13px;
  margin: 0;
}
.face-status {
  margin: 0;
  font-size: 13px;
  color: #475569;
}
</style>