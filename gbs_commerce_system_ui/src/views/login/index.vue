<template>
  <div class="login-container">
    <!-- 左侧品牌展示区 -->
    <div class="brand-panel">
      <div class="brand-bg">
        <div class="grid-overlay"></div>
        <div class="floating-orb orb-1"></div>
        <div class="floating-orb orb-2"></div>
        <div class="floating-orb orb-3"></div>
      </div>

      <div class="brand-content">
        <div class="brand-logo">
          <div class="logo-icon">
            <svg viewBox="0 0 64 64" fill="none">
              <rect x="8" y="14" width="48" height="36" rx="4" stroke="currentColor" stroke-width="2.5"/>
              <path d="M8 26h48" stroke="currentColor" stroke-width="2.5"/>
              <circle cx="16" cy="20" r="1.5" fill="currentColor"/>
              <circle cx="22" cy="20" r="1.5" fill="currentColor"/>
              <rect x="20" y="34" width="24" height="10" rx="1.5" stroke="currentColor" stroke-width="2"/>
              <path d="M26 34v-4a6 6 0 0 1 12 0v4" stroke="currentColor" stroke-width="2"/>
            </svg>
          </div>
          <div class="logo-text">
            <h1>智慧收银系统</h1>
            <span>SmartPOS · 新零售中台</span>
          </div>
        </div>

        <h2 class="brand-slogan">让每一笔交易<br/>都更简单高效</h2>

        <ul class="feature-list">
          <li>
            <span class="feature-dot"></span>
            语音收银 · 一句话完成下单
          </li>
          <li>
            <span class="feature-dot"></span>
            人脸支付 · 0.3秒极速识别
          </li>
          <li>
            <span class="feature-dot"></span>
            智能补货 · AI驱动经营决策
          </li>
        </ul>

        <div class="brand-footer">
          <span>v2.0 · 2026</span>
          <span class="dot-sep">·</span>
          <span>Powered by AI</span>
        </div>
      </div>
    </div>

    <!-- 右侧登录表单区 -->
    <div class="form-panel">
      <div class="form-wrapper">
        <div class="form-header">
          <h3>欢迎回来</h3>
          <p>请登录您的账户以继续</p>
        </div>

        <!-- 登录方式 Tab -->
        <div class="tab-bar">
          <button
            :class="['tab-item', { active: activeTab === 'password' }]"
            @click="activeTab = 'password'"
          >
            <el-icon><Lock /></el-icon>
            账号登录
          </button>
          <button
            :class="['tab-item', { active: activeTab === 'face' }]"
            @click="activeTab = 'face'"
          >
            <el-icon><UserFilled /></el-icon>
            人脸登录
          </button>
        </div>

        <!-- 密码登录 -->
        <transition name="fade-slide" mode="out-in">
          <div v-if="activeTab === 'password'" key="password" class="form-content">
            <el-form
              :model="loginForm"
              :rules="loginRules"
              ref="loginFormRef"
              size="large"
              hide-required-asterisk
            >
              <el-form-item prop="username">
                <el-input
                  v-model="loginForm.username"
                  placeholder="请输入用户名"
                  :prefix-icon="User"
                  clearable
                  @keyup.enter="onSubmit"
                />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="请输入密码"
                  :prefix-icon="Lock"
                  show-password
                  @keyup.enter="onSubmit"
                  @blur="fetchUserRoles"
                />
              </el-form-item>
              <el-form-item>
                <el-select
                  v-model="loginForm.roleKey"
                  :placeholder="roleLoading ? '加载角色中...' : (roleOptions.length ? '请选择角色' : '暂无可用角色')"
                  :loading="roleLoading"
                  clearable
                  style="width: 100%"
                >
                  <el-option
                    v-for="role in roleOptions"
                    :key="role.roleKey"
                    :label="role.roleName"
                    :value="role.roleKey"
                  />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  class="submit-btn"
                  :loading="loading"
                  @click="onSubmit"
                >
                  <span v-if="!loading">登 录</span>
                  <span v-else>登录中...</span>
                </el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 人脸登录 -->
          <div v-else key="face" class="form-content face-content">
            <div class="face-camera">
              <video
                ref="videoRef"
                autoplay
                playsinline
                class="face-video"
                :class="{ active: streaming }"
              ></video>
              <canvas ref="canvasRef" width="320" height="240" class="face-canvas"></canvas>
              <div v-if="!streaming" class="face-placeholder">
                <el-icon class="placeholder-icon"><Camera /></el-icon>
                <p>点击下方按钮打开摄像头</p>
              </div>
              <div v-if="streaming" class="face-frame">
                <div class="frame-corner tl"></div>
                <div class="frame-corner tr"></div>
                <div class="frame-corner bl"></div>
                <div class="frame-corner br"></div>
                <div class="scan-line"></div>
              </div>
            </div>

            <div class="face-actions">
              <el-button
                v-if="!streaming"
                type="primary"
                plain
                class="action-btn"
                @click="startCamera"
              >
                <el-icon><VideoCamera /></el-icon>
                打开摄像头
              </el-button>
              <el-button
                v-else
                type="danger"
                plain
                class="action-btn"
                @click="stopCamera"
              >
                <el-icon><VideoPause /></el-icon>
                关闭摄像头
              </el-button>
              <el-button
                type="success"
                class="action-btn submit-btn"
                :loading="faceLoading"
                :disabled="!streaming"
                @click="captureAndLogin"
              >
                <el-icon><Check /></el-icon>
                识别并登录
              </el-button>
            </div>
            <p class="face-hint">人脸识别成功后，将自动进入该账号所属角色的系统首页</p>
          </div>
        </transition>

        <div class="form-footer">
          <span>© 2026 智慧收银系统</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onBeforeUnmount, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Camera, VideoCamera, VideoPause, Check, UserFilled } from '@element-plus/icons-vue'
import { login, getRoleList, getUserRoles } from '@/api/modules/login'
import { getUserPermissions } from '@/api/modules/user'
import { initDynamicRouter } from '@/router/modules/dynamicRouter'
import { ElMessage, ElNotification } from 'element-plus'
import http from '@/api/request'

const router = useRouter()
const loginFormRef = useTemplateRef('loginFormRef')
const videoRef = ref(null)
const canvasRef = ref(null)

const activeTab = ref('password')
const loading = ref(false)
const faceLoading = ref(false)
const streaming = ref(false)
const roleLoading = ref(false)
let mediaStream = null

const roleOptions = ref([])

const loginForm = reactive({
  username: '',
  password: '',
  roleKey: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 统一展示全部角色（未选择角色时默认展示所有角色，不按账号过滤）
const loadAllRoles = async () => {
  roleLoading.value = true
  try {
    const res = await getRoleList()
    if (res.code === 200) {
      roleOptions.value = res.data || []
    }
  } catch (e) {
    console.error('获取全部角色失败:', e)
  } finally {
    roleLoading.value = false
  }
}

// 输入账号密码后，自动选中该账号可用的角色（单角色直接选中，多角色选第一个）
const fetchUserRoles = async () => {
  const uname = (loginForm.username || '').trim()
  if (!uname || !loginForm.password) {
    await loadAllRoles()
    return
  }
  roleLoading.value = true
  try {
    const res = await getUserRoles(uname)
    if (res.code === 200) {
      const roles = res.data || []
      if (roles.length > 0) {
        roleOptions.value = roles
        if (!roles.some(r => r.roleKey === loginForm.roleKey)) {
          loginForm.roleKey = roles[0].roleKey
        }
      } else {
        // 账号无角色：回退显示全部角色
        await loadAllRoles()
        loginForm.roleKey = ''
      }
    } else {
      await loadAllRoles()
    }
  } catch (e) {
    console.error('获取账号角色失败:', e)
    await loadAllRoles()
  } finally {
    roleLoading.value = false
  }
}

onMounted(loadAllRoles)

// 根据当前账号的菜单权限，取第一个可访问的页面作为登录后首页
const resolveHomeRoute = () => {
  const menus = JSON.parse(localStorage.getItem('authMenuList') || '[]')
  const findFirst = items => {
    for (const m of items || []) {
      if (m.visible === 1) continue
      if (m.children && m.children.length > 0) {
        const child = findFirst(m.children)
        if (child) return child
      } else if (m.path) {
        return m
      }
    }
    return null
  }
  return findFirst(menus)?.path || '/dashboard'
}

const getUserRoleName = user => {
  const roles = user?.roles || []
  return roles.length ? roles.map(r => r.roleName).join('、') : ''
}

const onSubmit = async () => {
  try {
    await loginFormRef.value.validate()
    // 角色可选：未选择时后端默认按账号校验
    loading.value = true
    const { code, msg, data: { token, user } } = await login({ ...loginForm })
    if (code !== 200) {
      ElMessage.error(msg || '用户名或密码错误')
      return
    }
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
    try {
      const permsRes = await getUserPermissions()
      if (permsRes.code === 200) {
        const userInfo = { ...user, permissions: permsRes.data || [] }
        localStorage.setItem('userInfo', JSON.stringify(userInfo))
      }
    } catch (error) {
      console.error('获取权限失败:', error)
    }
    await initDynamicRouter()
    router.push(resolveHomeRoute())
    ElNotification({ message: `欢迎登录智慧收银系统（${getUserRoleName(user) || '未分配角色'}）`, type: 'success', duration: 3000 })
  } catch (error) {
    if (error && error.msg) {
      ElMessage.error(error.msg)
    } else {
      ElMessage.error('登录失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

const startCamera = async () => {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: 640, height: 480 }
    })
    if (videoRef.value) {
      videoRef.value.srcObject = mediaStream
      streaming.value = true
    }
  } catch (e) {
    ElMessage.error('无法打开摄像头')
  }
}

const stopCamera = () => {
  if (mediaStream) {
    mediaStream.getTracks().forEach(t => t.stop())
    mediaStream = null
  }
  streaming.value = false
}

onBeforeUnmount(() => stopCamera())

const captureAndLogin = async () => {
  if (!videoRef.value) return
  try {
    faceLoading.value = true
    const video = videoRef.value
    const canvas = canvasRef.value
    const ctx = canvas.getContext('2d')
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    const dataUrl = canvas.toDataURL('image/jpeg', 0.9)
    const base64 = dataUrl.split(',')[1]
    const res = await http.post('/auth/faceLogin', { image: base64 })
    if (res.code !== 200) {
      ElMessage.error(res.msg || '人脸识别失败')
      return
    }
    const { token, user } = res.data
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
    try {
      const permsRes = await getUserPermissions()
      if (permsRes.code === 200) {
        const userInfo = { ...user, permissions: permsRes.data || [] }
        localStorage.setItem('userInfo', JSON.stringify(userInfo))
      }
    } catch {}
    await initDynamicRouter()
    router.push(resolveHomeRoute())
    const roleName = getUserRoleName(user)
    ElNotification({ message: `人脸登录成功${roleName ? `，身份：${roleName}` : ''}`, type: 'success', duration: 3000 })
  } catch (e) {
    if (e && e.msg) {
      ElMessage.error(e.msg)
    } else {
      ElMessage.error('人脸登录异常')
    }
  } finally {
    faceLoading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* ========== 左侧品牌区 ========== */
.brand-panel {
  flex: 1;
  position: relative;
  background: linear-gradient(135deg, #0F172A 0%, #1E1B4B 50%, #312E81 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.brand-bg {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.grid-overlay {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(99, 102, 241, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(99, 102, 241, 0.08) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(ellipse at center, black 30%, transparent 75%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 30%, transparent 75%);
}

.floating-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.5;
  animation: float 12s ease-in-out infinite;
}
.orb-1 { width: 360px; height: 360px; background: #4F46E5; top: 10%; left: -100px; }
.orb-2 { width: 280px; height: 280px; background: #06B6D4; bottom: 5%; right: -60px; animation-delay: -4s; }
.orb-3 { width: 200px; height: 200px; background: #A855F7; top: 60%; left: 40%; animation-delay: -8s; }

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -20px) scale(1.05); }
  66% { transform: translate(-20px, 30px) scale(0.95); }
}

.brand-content {
  position: relative;
  z-index: 2;
  max-width: 480px;
  padding: 0 60px;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 60px;
}

.logo-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #4F46E5, #06B6D4);
  border-radius: 14px;
  color: #fff;
  box-shadow: 0 8px 24px rgba(79, 70, 229, 0.4);
}
.logo-icon svg { width: 32px; height: 32px; }

.logo-text h1 {
  font-size: 22px;
  font-weight: 600;
  margin: 0;
  letter-spacing: 1px;
  background: linear-gradient(90deg, #fff, #A5B4FC);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.logo-text span {
  font-size: 12px;
  color: #94A3B8;
  letter-spacing: 0.5px;
}

.brand-slogan {
  font-size: 44px;
  font-weight: 700;
  line-height: 1.3;
  margin: 0 0 40px 0;
  letter-spacing: -0.5px;
  background: linear-gradient(180deg, #fff 0%, #CBD5E1 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0 0 60px 0;
}
.feature-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  font-size: 15px;
  color: #CBD5E1;
  transition: color 0.2s;
}
.feature-list li:hover { color: #fff; }
.feature-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4F46E5, #06B6D4);
  box-shadow: 0 0 12px #4F46E5;
}

.brand-footer {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #64748B;
  letter-spacing: 0.5px;
}
.dot-sep { opacity: 0.5; }

/* ========== 右侧表单区 ========== */
.form-panel {
  width: 520px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.form-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 0%, rgba(79, 70, 229, 0.04) 0%, transparent 40%),
    radial-gradient(circle at 80% 100%, rgba(6, 182, 212, 0.04) 0%, transparent 40%);
  pointer-events: none;
}

.form-wrapper {
  width: 100%;
  max-width: 380px;
  padding: 40px 0;
  position: relative;
  z-index: 1;
}

.form-header h3 {
  font-size: 28px;
  font-weight: 700;
  color: #0F172A;
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}
.form-header p {
  font-size: 14px;
  color: #64748B;
  margin: 0 0 32px 0;
}

/* Tab */
.tab-bar {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: #F1F5F9;
  border-radius: 10px;
  margin-bottom: 32px;
}
.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 0;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 14px;
  color: #64748B;
  cursor: pointer;
  transition: all 0.25s;
  font-weight: 500;
}
.tab-item:hover { color: #334155; }
.tab-item.active {
  background: #fff;
  color: #4F46E5;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

/* 表单 */
.form-content :deep(.el-input__wrapper) {
  padding: 4px 12px;
  border-radius: 8px;
  background: #F8FAFC;
  box-shadow: none !important;
  transition: all 0.2s;
}
.form-content :deep(.el-input__wrapper:hover) {
  background: #F1F5F9;
}
.form-content :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 2px #4F46E5 !important;
}
.form-content :deep(.el-input__inner) {
  height: 44px;
  font-size: 14px;
}
.form-content :deep(.el-input__prefix) {
  color: #94A3B8;
}
.form-content :deep(.el-form-item) {
  margin-bottom: 20px;
}

.submit-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #4F46E5 0%, #6366F1 50%, #06B6D4 100%) !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);
  transition: all 0.25s !important;
}
.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.4) !important;
}
.submit-btn:active { transform: translateY(0); }

/* 人脸登录 */
.face-content { padding-top: 8px; }

.face-camera {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: linear-gradient(135deg, #0F172A, #1E293B);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 20px;
}
.face-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
  display: block;
}
.face-canvas { display: none; }

.face-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #94A3B8;
  gap: 12px;
}
.placeholder-icon { font-size: 48px; color: #475569; }
.face-placeholder p { margin: 0; font-size: 13px; }

.face-frame {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.frame-corner {
  position: absolute;
  width: 28px;
  height: 28px;
  border: 2px solid #06B6D4;
  box-shadow: 0 0 12px rgba(6, 182, 212, 0.5);
}
.frame-corner.tl { top: 16px; left: 16px; border-right: none; border-bottom: none; border-top-left-radius: 6px; }
.frame-corner.tr { top: 16px; right: 16px; border-left: none; border-bottom: none; border-top-right-radius: 6px; }
.frame-corner.bl { bottom: 16px; left: 16px; border-right: none; border-top: none; border-bottom-left-radius: 6px; }
.frame-corner.br { bottom: 16px; right: 16px; border-left: none; border-top: none; border-bottom-right-radius: 6px; }

.scan-line {
  position: absolute;
  left: 16px;
  right: 16px;
  top: 16px;
  bottom: 16px;
  overflow: hidden;
  border-radius: 6px;
}
.scan-line::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, #06B6D4, transparent);
  box-shadow: 0 0 8px #06B6D4;
  animation: scan 2.4s linear infinite;
}
@keyframes scan {
  0% { top: 0; }
  100% { top: 100%; }
}

.face-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.action-btn {
  flex: 1;
  min-width: 100px;
  height: 40px;
  font-size: 13px;
  border-radius: 8px;
}

.face-hint {
  margin: 12px 0 0;
  text-align: center;
  font-size: 12px;
  color: #94A3B8;
}

.form-footer {
  margin-top: 32px;
  text-align: center;
  font-size: 12px;
  color: #94A3B8;
}

/* 过渡动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from { opacity: 0; transform: translateY(8px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-8px); }

/* 响应式 */
@media (max-width: 960px) {
  .brand-panel { display: none; }
  .form-panel { width: 100%; }
}
</style>
