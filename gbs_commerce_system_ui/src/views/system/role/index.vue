<template>
  <div>
    <!-- 操作按钮 -->
    <div style="margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center;">
      <el-button type="primary" @click="handleAdd">新增角色</el-button>
      <el-form :inline="true" :model="searchForm">
        <el-form-item>
          <el-input v-model="searchForm.roleName" placeholder="角色名称" clearable @clear="handleSearchReset" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="searchForm.roleKey" placeholder="角色标识" clearable @clear="handleSearchReset" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleSearchReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 角色列表表格 -->
    <el-table :data="filteredRoleList" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="角色ID" width="80" />
      <el-table-column prop="roleName" label="角色名称" width="150" />
      <el-table-column prop="roleKey" label="角色标识" width="150" />
      <el-table-column prop="roleSort" label="显示排序" width="100" />
      <el-table-column prop="remark" label="备注" width="200" />
      <el-table-column prop="createTime" label="创建时间" width="180" />

      <el-table-column label="操作" width="250">
        <template #default="scope">
          <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="primary" @click="handlePermission(scope.row)">分配权限</el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 角色编辑/新增对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="角色标识" prop="roleKey">
          <el-input v-model="form.roleKey" />
        </el-form-item>
        <el-form-item label="显示排序" prop="roleSort">
          <el-input-number v-model="form.roleSort" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>
    <!-- 分配权限对话框 -->
    <el-dialog title="分配权限" v-model="permissionDialogVisible" width="500px" @close="closePermissionDialog">
      <el-tree 
        ref="menuTreeRef"
        :data="menuList" 
        show-checkbox
        node-key="id"
        :props="{ children: 'children', label: 'menuName' }"
        :default-checked-keys="checkedMenus"
        :default-expanded-keys="expandedMenus"
      />
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closePermissionDialog">取消</el-button>
          <el-button type="primary" @click="submitPermission">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  getRoleList, 
  getRoleInfo, 
  createRole, 
  updateRole, 
  deleteRole,
  getRoleMenuIds,
  updateRoleMenus
} from '@/api/modules/role'
import { getAllMenuList } from '@/api/modules/menu'

const roleList = ref([])
const loading = ref(false)
const searchForm = ref({ roleName: '', roleKey: '' })
const filteredRoleList = computed(() => {
  const { roleName, roleKey } = searchForm.value
  if (!roleName && !roleKey) return roleList.value
  return roleList.value.filter(item => {
    const matchName = !roleName || item.roleName.includes(roleName)
    const matchKey = !roleKey || item.roleKey.includes(roleKey)
    return matchName && matchKey
  })
})
const dialogVisible = ref(false)
const formRef = ref(null)
const isAdd = ref(true)
const editingRoleId = ref(null)
const currentRoleId = ref(null)

const menuList = ref([])
const permissionDialogVisible = ref(false)
const menuTreeRef = ref(null)
const checkedMenus = ref([])
const expandedMenus = ref([])

// 表单数据
const form = ref({
  roleName: '',
  roleKey: '',
  roleSort: 0,
  remark: ''
})

// 表单验证规则
const rules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请输入角色标识', trigger: 'blur' }],
  roleSort: [{ required: true, message: '请输入显示排序', trigger: 'blur' }]
}

// 对话框标题
const dialogTitle = computed(() => {
  return isAdd.value ? '新增角色' : '编辑角色'
})

// 获取菜单列表
const fetchMenuList = async () => {
  try {
    // 使用getAllMenuList获取所有菜单用于权限分配
    const response = await getAllMenuList()
    menuList.value = response.data || []
  } catch (error) {
    ElMessage.error('获取菜单列表失败')
    console.error(error)
  }
}

const getAllMenuIds = (menus) => {
  let ids = []
  menus.forEach(menu => {
    ids.push(menu.id)
    if (menu.children && menu.children.length > 0) {
      ids = ids.concat(getAllMenuIds(menu.children))
    }
  })
  return ids
}

const getLeafMenuIds = (menus) => {
  let ids = []
  menus.forEach(menu => {
    if (menu.children && menu.children.length > 0) {
      ids = ids.concat(getLeafMenuIds(menu.children))
    } else {
      ids.push(menu.id)
    }
  })
  return ids
}
// 分配权限
const handlePermission = async (row) => {
  currentRoleId.value = row.id
  
  // 获取菜单列表
  await fetchMenuList()
  
  // 获取当前角色已有的菜单权限
  try {
    const response = await getRoleMenuIds(row.id)
    if (response.code === 200) {
      checkedMenus.value = response.data
    } else {
      checkedMenus.value = []
      ElMessage.error('获取角色菜单权限失败')
    }
    
    // 展开所有节点
    expandedMenus.value = getAllMenuIds(menuList.value)
  } catch (error) {
    ElMessage.error('获取角色菜单权限失败')
    console.error(error)
    checkedMenus.value = []
    expandedMenus.value = []
  }
  
  permissionDialogVisible.value = true
  
  // 在下次DOM更新后设置树节点的选中状态
  nextTick(() => {
    if (menuTreeRef.value && checkedMenus.value.length > 0) {
      const leafIds = getLeafMenuIds(menuList.value)
      const leafCheckedKeys = checkedMenus.value.filter(id => leafIds.includes(id))
      menuTreeRef.value.setCheckedKeys(leafCheckedKeys)
    }
  })
}

// 获取角色列表
const fetchRoleList = async () => {
  loading.value = true
  try {
    const response = await getRoleList()
    if (response.code !== 200) {
      ElMessage.error('获取角色列表失败')
      return
    }
    roleList.value = response.data
  } catch (error) {
    ElMessage.error('获取角色列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}


// 新增角色
const handleAdd = () => {
  isAdd.value = true
  form.value = {
    roleName: '',
    roleKey: '',
    roleSort: 0,
    remark: ''
  }
  dialogVisible.value = true
}

// 编辑角色
const handleEdit = (row) => {
  isAdd.value = false
  editingRoleId.value = row.id
  form.value = {
    roleName: row.roleName,
    roleKey: row.roleKey,
    roleSort: row.roleSort || 0,
    remark: row.remark || ''
  }
  dialogVisible.value = true
}

// 删除角色
const handleDelete = (row) => {
  if (row.roleKey === 'admin') {
    ElMessage.warning('不能删除超级管理员角色')
    return
  }

  ElMessageBox.confirm(`确定要删除"${row.roleName}"角色吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const response = await deleteRole(row.id)
      if (response.code === 200) {
        ElMessage.success('删除成功')
        fetchRoleList() // 重新加载数据
      } else {
        ElMessage.error('删除失败')
      }
    } catch (error) {
      ElMessage.error('删除失败')
      console.error(error)
    }
  }).catch(() => {
    // 用户取消删除
  })
}
// 提交权限设置
const submitPermission = async () => {
  try {
    const checkedKeys = menuTreeRef.value.getCheckedKeys()
    const halfCheckedKeys = menuTreeRef.value.getHalfCheckedKeys()
    const allKeys = [...checkedKeys, ...halfCheckedKeys]
    
    const response = await updateRoleMenus(currentRoleId.value, allKeys)
    
    if (response.code === 200) {
      ElMessage.success('权限分配成功')
      permissionDialogVisible.value = false
    } else {
      ElMessage.error('权限分配失败: ' + response.msg)
    }
  } catch (error) {
    ElMessage.error('权限分配失败')
    console.error(error)
  }
}

// 添加关闭权限对话框的处理函数
const closePermissionDialog = () => {
  permissionDialogVisible.value = false
  // 清空选中状态，为下次打开做准备
  if (menuTreeRef.value) {
    menuTreeRef.value.setCheckedKeys([])
  }
}
// 提交表单
const submitForm = () => {
  formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        let response
        if (isAdd.value) {
          // 新增角色
          response = await createRole(form.value)
          if (response.code === 200) {
            ElMessage.success('新增成功')
          } else {
            ElMessage.error('新增失败')
            return
          }
        } else {
          // 编辑角色
          response = await updateRole(editingRoleId.value, form.value)
          if (response.code === 200) {
            ElMessage.success('更新成功')
          } else {
            ElMessage.error('更新失败')
            return
          }
        }
        dialogVisible.value = false
        fetchRoleList() // 重新加载数据
      } catch (error) {
        ElMessage.error(isAdd.value ? '新增失败' : '更新失败')
        console.error(error)
      }
    }
  })
}


const handleSearch = () => {}
const handleSearchReset = () => {
  searchForm.value = { roleName: '', roleKey: '' }
}

onMounted(() => {
  fetchRoleList()
})
</script>

<style scoped>
/* 可以添加一些自定义样式 */
</style>