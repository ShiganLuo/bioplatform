<template>
  <div class="register-container">
    <div class="register-card">
      <div class="register-header">
        <div class="register-logo">
          <el-icon :size="36" color="#409eff"><DataBoard /></el-icon>
        </div>
        <h2 class="register-title">生信云平台</h2>
        <p class="register-subtitle">创建新账号</p>
      </div>

      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        class="register-form"
        @keyup.enter="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="nickName">
          <el-input
            v-model="registerForm.nickName"
            placeholder="请输入昵称"
            :prefix-icon="UserFilled"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="email">
          <el-input
            v-model="registerForm.email"
            placeholder="请输入邮箱"
            :prefix-icon="Message"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="verifyCode">
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

        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请确认密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="register-btn"
            @click="handleRegister"
          >
            注 册
          </el-button>
        </el-form-item>

        <div class="register-footer-link">
          已有账号？<el-link type="primary" :underline="false" @click="router.push('/login')">返回登录</el-link>
        </div>
      </el-form>
    </div>

    <p class="register-footer">© {{ currentYear }} 生信云平台 Bioinformatics Cloud Platform</p>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, UserFilled, Message, DataBoard } from '@element-plus/icons-vue'
import http from '@/utils/http/axios'

const router = useRouter()
const registerFormRef = ref<any>()
const loading = ref(false)
const codeCooldown = ref(0)
const currentYear = new Date().getFullYear()

const registerForm = reactive({
  username: '',
  nickName: '',
  email: '',
  password: '',
  confirmPassword: '',
  verifyCode: ''
})

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules: Record<string, any[]> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  nickName: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  verifyCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleSendCode = async () => {
  if (!registerForm.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  try {
    await http.post('/api/front/auth/sendEmailCode', { email: registerForm.email })
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

const handleRegister = async () => {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    loading.value = true
    try {
      await http.post('/api/front/auth/register', {
        username: registerForm.username,
        nickName: registerForm.nickName,
        email: registerForm.email,
        password: registerForm.password,
        verifyCode: registerForm.verifyCode
      })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch {
      // error handled by interceptor
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.register-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  position: relative;
}

.register-container::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background:
    radial-gradient(circle at 20% 50%, rgba(255,255,255,0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255,255,255,0.08) 0%, transparent 40%);
}

.register-card {
  width: 420px;
  padding: 40px;
  background: rgba(255,255,255,0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
  position: relative;
  z-index: 1;
}

.register-header { text-align: center; margin-bottom: 28px; }
.register-logo { margin-bottom: 12px; }
.register-title {
  margin: 0 0 6px 0; font-size: 24px; font-weight: 700;
  background: linear-gradient(135deg, #409eff, #67c23a);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
}
.register-subtitle { margin: 0; color: #909399; font-size: 14px; }

.register-form { width: 100%; }
.register-form :deep(.el-input__wrapper) { border-radius: 10px; box-shadow: 0 0 0 1px #e4e7ed inset; }
.register-form :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px #c0c4cc inset; }
.register-form :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #409eff inset; }

.register-btn {
  width: 100%; border-radius: 10px; height: 44px; font-size: 16px; font-weight: 600;
  background: linear-gradient(135deg, #409eff, #66b1ff); border: none; transition: all 0.3s;
}
.register-btn:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(64,158,255,0.4); }

.register-footer-link { text-align: center; font-size: 14px; color: #606266; }

.register-footer {
  position: relative; z-index: 1; margin-top: 24px;
  color: rgba(255,255,255,0.7); font-size: 13px;
}
</style>
