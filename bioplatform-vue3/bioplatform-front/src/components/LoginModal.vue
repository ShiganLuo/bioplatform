<template>
  <el-dialog
    v-model="dialogVisible"
    :title="isLogin ? '用户登录' : '用户注册'"
    width="420px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <div class="login-modal">
      <!-- Tabs -->
      <div class="modal-tabs">
        <div
          class="tab-item"
          :class="{ active: isLogin }"
          @click="isLogin = true"
        >
          登录
        </div>
        <div
          class="tab-item"
          :class="{ active: !isLogin }"
          @click="isLogin = false"
        >
          注册
        </div>
      </div>

      <!-- Login Form -->
      <el-form
        v-if="isLogin"
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-position="top"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <!-- Register Form -->
      <el-form
        v-else
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-position="top"
        @submit.prevent="handleRegister"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="registerForm.email"
            placeholder="请输入邮箱"
            :prefix-icon="Message"
            size="large"
          />
        </el-form-item>
        <el-form-item label="验证码" prop="verifyCode">
          <div style="display:flex;gap:8px;width:100%;">
            <el-input
              v-model="registerForm.verifyCode"
              placeholder="请输入验证码"
              size="large"
              style="flex:1;"
            />
            <el-button
              size="large"
              :disabled="codeCooldown > 0"
              @click="handleSendCode"
            >
              {{ codeCooldown > 0 ? codeCooldown + 's' : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="昵称" prop="nickName">
          <el-input
            v-model="registerForm.nickName"
            placeholder="请输入昵称（选填）"
            :prefix-icon="UserFilled"
            size="large"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleRegister"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="handleRegister"
          >
            注册
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { User, Lock, Message, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { sendEmailCode } from '@/api/authApi'

const props = defineProps<{
  visible: boolean
  mode?: 'login' | 'register'
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const userStore = useUserStore()
const isLogin = ref(props.mode !== 'register')
watch(() => props.mode, (val) => {
  if (val) isLogin.value = val !== 'register'
})
const loading = ref(false)

const dialogVisible = ref(props.visible)
watch(
  () => props.visible,
  (val) => { dialogVisible.value = val }
)
watch(
  () => dialogVisible.value,
  (val) => { emit('update:visible', val) }
)

// Login form
const loginFormRef = ref<FormInstance>()
const loginForm = reactive({
  username: '',
  password: '',
})
const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// Register form
const registerFormRef = ref<FormInstance>()
const codeCooldown = ref(0)
const registerForm = reactive({
  username: '',
  email: '',
  nickName: '',
  password: '',
  confirmPassword: '',
  verifyCode: '',
})
const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3-20 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于 6 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login({
        username: loginForm.username,
        password: loginForm.password,
      })
      ElMessage.success('登录成功')
      handleClose()
    } catch {
      // axios拦截器已弹出具体错误信息
    } finally {
      loading.value = false
    }
  })
}

async function handleSendCode() {
  if (!registerForm.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  try {
    await sendEmailCode(registerForm.email)
    ElMessage.success('验证码已发送')
    codeCooldown.value = 60
    const timer = setInterval(() => {
      codeCooldown.value--
      if (codeCooldown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch {
    // error handled by interceptor
  }
}

async function handleRegister() {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.register({
        username: registerForm.username,
        email: registerForm.email,
        password: registerForm.password,
        nickName: registerForm.nickName || undefined,
        verifyCode: registerForm.verifyCode,
      })
      ElMessage.success('注册成功')
      handleClose()
    } catch {
      // error handled by interceptor
    } finally {
      loading.value = false
    }
  })
}

function handleClose() {
  dialogVisible.value = false
  isLogin.value = true
  // Reset forms
  loginForm.username = ''
  loginForm.password = ''
  registerForm.username = ''
  registerForm.email = ''
  registerForm.nickName = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  registerForm.verifyCode = ''
}
</script>

<style scoped>
.login-modal {
  padding: 8px 0;
}

.modal-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  border-bottom: 2px solid #ebeef5;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 12px 0;
  font-size: 16px;
  font-weight: 500;
  color: #909399;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.tab-item.active {
  color: #409eff;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 20%;
  right: 20%;
  height: 2px;
  background: #409eff;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}
</style>
