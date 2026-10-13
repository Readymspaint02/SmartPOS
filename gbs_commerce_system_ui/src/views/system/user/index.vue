<template>
  <div class="user-management">
    <!-- ========== 智能 Hero ========== -->
    <el-card class="hero-card" shadow="never">
      <div class="hero-bg" aria-hidden="true">
        <span class="orb orb-1"></span>
        <span class="orb orb-2"></span>
        <span class="grid-mask"></span>
      </div>
      <div class="hero-content">
        <div class="hero-left">
          <div class="hero-eyebrow">
            <span class="pos-ai-badge">AI · 用户中心</span>
            <span class="hero-date">{{ todayLabel }}</span>
          </div>
          <p class="hero-greeting">{{ greeting }}，{{ displayName }}</p>
          <p class="hero-subtitle">
            当前角色：<strong class="pos-gradient-text">{{ roleLabel }}</strong>。{{ roleWelcome }}
          </p>
        </div>
        <div class="hero-right">
          <div class="hero-stat">
            <span class="hero-stat__label">系统总用户</span>
            <span class="hero-stat__value pos-counter">{{ pagination.total || userList.length }}</span>
          </div>
          <div class="hero-stat">
            <span class="hero-stat__label">本页展示</span>
            <span class="hero-stat__value pos-counter">{{ userList.length }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- ========== 统计卡 ========== -->
    <el-row :gutter="16" class="summary-row" v-if="isSuperAdmin">
      <el-col :span="6" v-for="card in statCards" :key="card.key">
        <el-card :class="['summary-card', card.class]" shadow="never">
          <div class="summary-head">
            <span class="summary-icon"><el-icon><component :is="card.icon" /></el-icon></span>
            <p class="summary-title">{{ card.title }}</p>
          </div>
          <p class="summary-value pos-counter">{{ card.value }}</p>
          <div class="summary-footer"><p class="summary-total">{{ card.desc }}</p></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ========== 主卡：搜索 + 表格 ========== -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title"><span class="pos-ai-badge" style="margin-right:8px">用户列表</span>账号管理 · 人脸注册</span>
          <el-button v-if="isSuperAdmin" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon><span style="margin-left:4px">新增用户</span>
          </el-button>
        </div>
      </template>

      <!-- 智能搜索 -->
      <div class="smart-filter" v-if="isSuperAdmin">
        <el-input
          v-model="searchForm.username"
          placeholder="搜索用户名…"
          clearable
          :prefix-icon="Search"
          class="filter-input"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="searchForm.roleKey" placeholder="按角色筛选" clearable class="filter-select">
          <el-option v-for="item in roleList" :key="item.id" :label="item.roleName" :value="item.roleKey" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon><span style="margin-left:4px">查询</span>
        </el-button>
        <el-button @click="handleReset">
          <el-icon><RefreshRight /></el-icon><span style="margin-left:4px">重置</span>
        </el-button>
      </div>

      <!-- 用户表格 -->
      <el-table :data="filteredUserList" style="width: 100%" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" min-width="120">
          <template #default="scope">
            <span class="user-name">{{ scope.row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="avatar" label="头像" width="90">
          <template #default="scope">
            <img
              :src="scope.row.avatar ? '/api' + scope.row.avatar : defaultAvatar"
              class="user-avatar"
              :class="{ 'face-registered': scope.row.faceRegistered === 1 }"
            />
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="role" label="角色" min-width="160">
          <template #default="scope">
            <el-tag
              v-for="role in scope.row.roles"
              :key="role.id"
              :class="role.roleKey === 'admin' ? 'role-tag-admin' : 'role-tag-user'"
              style="margin-right: 6px;"
            >
              <el-icon style="vertical-align: middle; margin-right: 2px;">
                <UserFilled v-if="role.roleKey === 'admin'" />
                <User v-else />
              </el-icon>
              {{ role.roleName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="faceRegistered" label="人脸" width="90" align="center">
          <template #default="scope">
            <el-tag
              :type="scope.row.faceRegistered === 1 ? 'success' : 'info'"
              effect="light"
              size="small"
            >
              {{ scope.row.faceRegistered === 1 ? '已注册' : '未注册' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="280" v-if="isSuperAdmin" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" plain @click="handleEdit(scope.row)">
              <el-icon><Edit /></el-icon><span style="margin-left:4px">编辑</span>
            </el-button>
            <el-button
              size="small"
              :type="scope.row.faceRegistered === 1 ? 'warning' : 'success'"
              plain
              @click="openFaceRegister(scope.row)"
            >
              <el-icon><Camera /></el-icon>
              <span style="margin-left:4px">{{ scope.row.faceRegistered === 1 ? '人脸更新' : '人脸注册' }}</span>
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="scope.row.id === currentUser.id"
              @click="handleDelete(scope.row)"
            >
              <el-icon><Delete /></el-icon><span style="margin-left:4px">删除</span>
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-if="isSuperAdmin"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="pagination.currentPage"
        :page-sizes="[10, 20, 50]"
        :page-size="pagination.pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="pagination.total"
        class="smart-pagination"
      />
    </el-card>

    <!-- ========== AI 洞察 ========== -->
    <el-card v-if="isSuperAdmin" class="insight-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title"><span class="pos-ai-badge" style="margin-right:8px">AI 洞察</span>账号安全智能提示</span>
        </div>
      </template>
      <div class="insight-grid">
        <div class="pos-insight" v-for="(tip, i) in aiInsights" :key="i">{{ tip }}</div>
      </div>
    </el-card>

    <!-- 用户对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px">
      <el-form :model="userForm" :rules="userFormRules" ref="userFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" :disabled="!!userForm.id" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="头像" prop="avatar">
          <el-upload
            class="avatar-uploader"
            action="/api/system/file/upload"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
            :before-upload="beforeAvatarUpload"
            :headers="uploadHeaders"
          >
            <img v-if="userForm.avatar" :src="'/api' + userForm.avatar" class="avatar" />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" :prefix-icon="Message" />
        </el-form-item>
        <el-form-item label="角色" prop="roles" v-if="isSuperAdmin">
          <el-select v-model="userForm.roleIds" multiple placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="item in roleList"
              :key="item.id"
              :label="item.roleName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!userForm.id">
          <el-input v-model="userForm.password" type="password" show-password :prefix-icon="Lock" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitUserForm">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 人脸注册对话框 -->
    <el-dialog :title="faceDialogTitle" v-model="faceDialogVisible" width="480px">
      <div class="face-register-content">
        <div class="face-info">
          <p><strong>用户名：</strong>{{ faceRegisterUser.username }}</p>
          <p><strong>用户ID：</strong>{{ faceRegisterUser.id }}</p>
        </div>
        <div class="face-capture">
          <video ref="videoRef" autoplay playsinline class="face-video"></video>
          <canvas ref="canvasRef" style="display: none;"></canvas>
        </div>
        <div class="face-preview" v-if="capturedImage">
          <img :src="capturedImage" alt="人脸预览" class="preview-image" />
        </div>
      </div>
      <template #footer>
        <el-button @click="stopCamera">取消</el-button>
        <el-button type="primary" @click="captureAndRegister" :loading="faceRegistering">
          {{ capturedImage ? '确认注册' : '拍照' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserPage, getUserList, getCurrentUserInfo, createUser, updateUser, deleteUser } from '@/api/modules/user'
import { getRoleAll } from '@/api/modules/role'
import defaultAvatar from '@/assets/anon.jpeg'
import { Plus, User, UserFilled, Lock, Camera, Edit, Delete, Search, RefreshRight, Message } from '@element-plus/icons-vue'
import http from '@/api/request'

const userList = ref([])
const allUsers = ref([])
const loading = ref(false)
const currentUser = ref({})
const roleList = ref([])

const faceDialogVisible = ref(false)
const faceDialogTitle = ref('人脸注册')
const faceRegisterUser = ref({})
const faceRegistering = ref(false)
const capturedImage = ref('')
const videoRef = ref(null)
const canvasRef = ref(null)
let mediaStream = null

// 分页参数
const pagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

// 搜索表单
const searchForm = reactive({
  username: '',
  roleKey: ''
})

// 对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const userForm = reactive({
  id: '',
  username: '',
  avatar: '',
  email: '',
  roleIds: [],
  password: ''
})

// 表单引用
const userFormRef = ref(null)

// 表单验证规则
const userFormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] }
  ],
  roleIds: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ]
}
const uploadHeaders = {
  Authorization: 'Bearer ' + localStorage.getItem('token')
}
const handleAvatarSuccess = (response) => {
  if (response.code !== 200) {
    ElMessage.error(response.msg || '头像上传失败')
    return false
  }
  userForm.avatar = response.data
  ElMessage.success('头像上传成功')
}

const beforeAvatarUpload = (rawFile) => {
  if (rawFile.type !== 'image/jpeg' && rawFile.type !== 'image/png') {
    ElMessage.error('头像必须是 JPG 或 PNG 格式!')
    return false
  } else if (rawFile.size / 1024 / 1024 > 2) {
    ElMessage.error('头像大小不能超过 2MB!')
    return false
  }
  return true
}

// 判断是否为超级管理员
const isSuperAdmin = computed(() => {
  if (currentUser.value.roles && currentUser.value.roles.length > 0) {
    return currentUser.value.roles.some(role => role.roleKey === 'admin')
  }
  return false
})

// ========== 智能展示派生数据 ==========
const filteredUserList = computed(() => {
  if (!searchForm.roleKey) return userList.value
  return userList.value.filter(u => u.roles && u.roles.some(r => r.roleKey === searchForm.roleKey))
})

const statCards = computed(() => {
  const base = allUsers.value.length ? allUsers.value : userList.value
  const adminCount = base.filter(u => u.roles && u.roles.some(r => r.roleKey === 'admin')).length
  const faceCount = base.filter(u => u.faceRegistered === 1).length
  const faceRate = base.length ? Math.round((faceCount / base.length) * 100) : 0
  return [
    { key: 'total', title: '系统总用户', value: pagination.total || base.length, desc: '全量账号数据', icon: User, class: 'summary-card--brand' },
    { key: 'page', title: '本页用户', value: userList.value.length, desc: '当前页展示', icon: UserFilled, class: 'summary-card--cyan' },
    { key: 'admin', title: '全量管理员', value: adminCount, desc: '高权限账号', icon: Lock, class: 'summary-card--purple' },
    { key: 'face', title: '人脸注册率', value: faceRate + '%', desc: `已注册 ${faceCount} 人`, icon: Camera, class: 'summary-card--emerald' }
  ]
})

const aiInsights = computed(() => {
  const tips = []
  const base = allUsers.value.length ? allUsers.value : userList.value
  const noFace = base.filter(u => u.faceRegistered !== 1).length
  if (noFace > 0) tips.push(`检测到 ${noFace} 个账号尚未注册人脸，建议尽快完成人脸注册以启用刷脸登录。`)
  const adminCount = base.filter(u => u.roles && u.roles.some(r => r.roleKey === 'admin')).length
  if (adminCount > 1) tips.push(`当前存在 ${adminCount} 个管理员账号，建议遵循最小权限原则，避免多人共享高权限。`)
  tips.push('建议定期更新账号密码并启用邮箱校验，以提升系统整体安全性。')
  return tips
})

const todayLabel = computed(() => {
  const d = new Date()
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${week}`
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const displayName = computed(() => currentUser.value.username || '管理员')
const roleLabel = computed(() => {
  if (!currentUser.value.roles || !currentUser.value.roles.length) return '访客'
  return currentUser.value.roles.map(r => r.roleName).join('、')
})
const roleWelcome = computed(() =>
  isSuperAdmin.value ? '您拥有系统全部管理权限，请谨慎操作。' : '您当前可查看个人账号信息，如需更多权限请联系管理员。'
)

const loadRoleList = async () => {
  if (isSuperAdmin.value) {
    try {
      loading.value = true
      const res = await getRoleAll()
      roleList.value = res.data
    } catch (err) {
      console.error(err)
    } finally {
      loading.value = false
    }
  }
}

// 加载全量用户（用于统计卡：系统总用户/管理员/人脸注册率）
const loadAllUsers = async () => {
  if (!isSuperAdmin.value) return
  try {
    const res = await getUserList({})
    if (res.code === 200) {
      allUsers.value = res.data || []
    }
  } catch (err) {
    console.error('加载全量用户统计失败:', err)
  }
}

// 获取用户列表
const loadUserList = async () => {
  if (!isSuperAdmin.value) {
    // 普通用户只获取自己的信息
    try {
      loading.value = true
      const res = await getCurrentUserInfo()
      if (res.code === 200) {
        userList.value = [res.data]
      }
    } catch (err) {
      console.error(err)
    } finally {
      loading.value = false
    }
  } else {
    // 超级管理员获取所有用户
    try {
      loading.value = true
      const params = {
        pageNum: pagination.currentPage,
        pageSize: pagination.pageSize,
        queryParams: { username: searchForm.username }
      }
      const res = await getUserPage(params)
      if (res.code === 200) {
        userList.value = res.data.records
        pagination.total = res.data.total
      }
    } catch (err) {
      console.error(err)
    } finally {
      loading.value = false
    }
  }
}

// 获取当前用户信息
const getCurrentUser = () => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    currentUser.value = JSON.parse(userStr)
  }
}

// 处理搜索
const handleSearch = () => {
  pagination.currentPage = 1
  searchForm.roleKey = ''
  loadUserList()
}

// 处理重置
const handleReset = () => {
  searchForm.username = ''
  searchForm.roleKey = ''
  pagination.currentPage = 1
  loadUserList()
}

// 处理分页大小变化
const handleSizeChange = (val) => {
  pagination.pageSize = val
  pagination.currentPage = 1
  loadUserList()
}

// 处理当前页变化
const handleCurrentChange = (val) => {
  pagination.currentPage = val
  loadUserList()
}

// 新增用户
const handleAdd = () => {
  dialogTitle.value = '新增用户'
  Object.assign(userForm, {
    id: '',
    username: '',
    avatar: '',
    email: '',
    roleIds: [],
    password: ''
  })
  dialogVisible.value = true
}

// 编辑用户
const handleEdit = (row) => {
  dialogTitle.value = '编辑用户'
  Object.assign(userForm, {
    ...row,
    roleIds: row.roles ? row.roles.map(role => role.id) : []
  })
  userForm.password = ''
  dialogVisible.value = true
}

// 删除用户
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确认要删除用户"${row.username}"吗？`,
    '删除确认',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      const res = await deleteUser(row.id)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        loadUserList()
      }
    } catch (err) {
      console.error(err)
    }
  }).catch(() => {
    ElMessage.info('已取消删除')
  })
}

// 提交表单
const submitUserForm = () => {
  userFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const formData = {
          ...userForm,
          roles: userForm.roleIds.map(key => ({ id: key }))
        }

        let res
        if (userForm.id) {
          const { id, ...updateData } = formData
          if (!updateData.password) {
            delete updateData.password
          }
          res = await updateUser(id, updateData)
        } else {
          res = await createUser(formData)
        }

        if (res.code === 200) {
          ElMessage.success(userForm.id ? '更新成功' : '创建成功')
          dialogVisible.value = false
          loadUserList()
        }
      } catch (err) {
        console.error(err)
      }
    }
  })
}

// 组件挂载时执行
onMounted(() => {
  getCurrentUser()
  loadUserList()
  loadRoleList()
  loadAllUsers()
})

onBeforeUnmount(() => {
  stopCamera()
})

const openFaceRegister = async (row) => {
  faceRegisterUser.value = row
  capturedImage.value = ''
  faceDialogVisible.value = true

  try {
    const res = await http.get('/face/check', { params: { userId: String(row.id) } })
    if (res.code === 200 && res.data?.registered) {
      faceDialogTitle.value = '人脸更新'
      row.faceRegistered = 1
    } else {
      faceDialogTitle.value = '人脸注册'
      row.faceRegistered = 0
    }
  } catch (error) {
    faceDialogTitle.value = row.faceRegistered === 1 ? '人脸更新' : '人脸注册'
    console.error('检查人脸状态失败:', error)
  }

  await startCamera()
}

const startCamera = async () => {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: 640, height: 480 }
    })
    if (videoRef.value) {
      videoRef.value.srcObject = mediaStream
    }
  } catch (error) {
    ElMessage.error('无法访问摄像头，请检查权限设置')
    console.error(error)
  }
}

const stopCamera = () => {
  if (mediaStream) {
    mediaStream.getTracks().forEach(track => track.stop())
    mediaStream = null
  }
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
  capturedImage.value = ''
  faceDialogVisible.value = false
}

const captureAndRegister = async () => {
  if (!capturedImage.value) {
    const video = videoRef.value
    const canvas = canvasRef.value
    if (!video || !canvas) return

    canvas.width = video.videoWidth || 640
    canvas.height = video.videoHeight || 480
    const ctx = canvas.getContext('2d')
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    capturedImage.value = canvas.toDataURL('image/jpeg', 0.8)
    return
  }

  faceRegistering.value = true
  try {
    const base64Data = capturedImage.value.split(',')[1]
    const res = await http.post('/face/register', {
      userId: String(faceRegisterUser.value.id),
      image: base64Data,
      userInfo: faceRegisterUser.value.username
    })

    if (res.code === 200) {
      const msg = faceDialogTitle.value === '人脸更新' ? '人脸更新成功' : '人脸注册成功'
      ElMessage.success(msg)
      faceRegisterUser.value.faceRegistered = 1
      stopCamera()
      loadUserList()
    } else {
      ElMessage.error(res.msg || '人脸注册失败')
    }
  } catch (error) {
    const errMsg = error?.msg || error?.message || '人脸注册失败，请重试'
    ElMessage.error(errMsg)
    console.error('人脸注册错误:', error)
  } finally {
    faceRegistering.value = false
  }
}
</script>

<style scoped>
.user-management {
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
.hero-date { font-size: 12px; color: rgba(199,210,254,0.85); letter-spacing: 0.5px; }
.hero-greeting { margin: 0; font-size: 24px; font-weight: 700; }
.hero-subtitle { margin: 8px 0 0; color: rgba(226,232,240,0.85); }
.hero-subtitle strong { font-weight: 700; }
.hero-right { display: flex; gap: 28px; min-width: 240px; justify-content: flex-end; }
.hero-stat { display: flex; flex-direction: column; align-items: flex-end; }
.hero-stat__label { font-size: 12px; color: rgba(199,210,254,0.8); }
.hero-stat__value { font-size: 30px; font-weight: 700; color: #fff; }

/* ============ 统计卡 ============ */
.summary-row { margin-bottom: 0; }
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
.summary-total { margin: 0; font-size: 12.5px; font-weight: 600; color: rgba(255,255,255,0.95); font-variant-numeric: tabular-nums; }

/* ============ 主卡 ============ */
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-weight: 600; color: var(--pos-text); display: inline-flex; align-items: center; }

.smart-filter {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
  flex-wrap: wrap;
  align-items: center;
}
.filter-input { width: 240px; }
.filter-select { width: 180px; }

/* 用户表格 */
.user-name { font-weight: 600; color: var(--pos-text); }
.user-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--pos-border);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}
.user-avatar:hover { transform: scale(1.08); }
.user-avatar.face-registered {
  border-color: transparent;
  box-shadow: 0 0 0 2px rgba(16,185,129,0.55), 0 0 10px rgba(16,185,129,0.45);
}

.role-tag-admin :deep(.el-icon) { color: #fff; }
.role-tag-admin {
  background: linear-gradient(135deg, #4F46E5, #6366F1) !important;
  color: #fff !important;
  border: none !important;
}
.role-tag-user {
  background: rgba(6,182,212,0.12) !important;
  color: #0E7490 !important;
  border: 1px solid rgba(6,182,212,0.25) !important;
}

.smart-pagination {
  margin-top: 20px;
  justify-content: flex-end;
  display: flex;
}

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
.insight-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.insight-grid .pos-insight {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(129, 140, 248, 0.35);
  color: rgba(255, 255, 255, 0.95);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.65;
}

/* ============ 头像上传 ============ */
.avatar-uploader .avatar {
  width: 80px;
  height: 80px;
  display: block;
  border-radius: 12px;
  object-fit: cover;
}
.avatar-uploader :deep(.el-upload) {
  border: 1px dashed var(--el-border-color);
  border-radius: 12px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: var(--el-transition-duration-fast);
}
.avatar-uploader :deep(.el-upload:hover) {
  border-color: var(--pos-primary);
  background: rgba(79,70,229,0.04);
}
.el-icon.avatar-uploader-icon {
  font-size: 28px;
  color: var(--pos-text-muted);
  width: 80px;
  height: 80px;
  text-align: center;
}

/* ============ 人脸注册 ============ */
.face-register-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
.face-info {
  width: 100%;
  padding: 14px 16px;
  background: linear-gradient(135deg, rgba(79,70,229,0.06), rgba(6,182,212,0.06));
  border: 1px solid rgba(79,70,229,0.14);
  border-radius: 10px;
}
.face-info p { margin: 4px 0; }
.face-capture {
  width: 100%;
  max-width: 400px;
  border-radius: 12px;
  overflow: hidden;
  background: #000;
}
.face-video {
  width: 100%;
  height: 300px;
  object-fit: cover;
  display: block;
  transform: scaleX(-1);
}
.face-preview { width: 100%; max-width: 400px; }
.preview-image { width: 100%; border-radius: 12px; }

@media (max-width: 992px) {
  .insight-grid { grid-template-columns: 1fr; }
  .hero-right { display: none; }
}
</style>