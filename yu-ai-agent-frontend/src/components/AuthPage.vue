<template>
  <div class="auth-wrapper" @mousemove="handleMouseMove">
    <!-- Interactive Canvas Background -->
    <canvas ref="canvasEl" class="particle-canvas"></canvas>

    <section class="auth-card glass-panel">
      <div class="auth-header">
        <h1 class="gradient-text title">LoLo AI Agent</h1>
        <p class="subtitle">登录探索智能助理</p>
      </div>

      <div class="auth-tabs">
        <button
          type="button"
          class="auth-tab"
          :class="{ active: mode === 'login' }"
          @click="mode = 'login'"
        >
          登录
        </button>
        <button
          type="button"
          class="auth-tab"
          :class="{ active: mode === 'register' }"
          @click="mode = 'register'"
        >
          注册
        </button>
      </div>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <div class="input-group">
          <label>用户名</label>
          <input
            v-model.trim="username"
            autocomplete="username"
            placeholder="输入用户名"
            class="glow-input"
          />
        </div>
        
        <div class="input-group">
          <label>密码</label>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="输入密码"
            class="glow-input"
          />
        </div>

        <p v-if="errorText" class="auth-error">{{ errorText }}</p>

        <button type="submit" class="submit-btn" :disabled="loading || !username || !password">
          <span v-if="!loading">{{ mode === 'login' ? '登 录' : '注 册 并 登 录' }}</span>
          <span v-else class="loading-dots">
            <i></i><i></i><i></i>
          </span>
        </button>
      </form>
    </section>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { loginWithPassword, registerWithPassword } from '../utils/auth'

const props = defineProps({
  baseUrl: { type: String, required: true }
})

const emit = defineEmits(['authenticated'])

const mode = ref('login')
const username = ref('')
const password = ref('')
const loading = ref(false)
const errorText = ref('')

const canvasEl = ref(null)
let ctx = null
let animationFrameId = null
let particles = []
let mouse = { x: null, y: null, radius: 150 }

// Colors similar to the uploaded image swirl (Blues, purples, oranges, reds)
const colors = ['#2980b9', '#8e44ad', '#e74c3c', '#f39c12', '#3498db', '#d35400']

class Particle {
  constructor(x, y, canvas) {
    this.canvas = canvas;
    this.x = x;
    this.y = y;
    this.baseX = x;
    this.baseY = y;
    // Slanted short lines
    this.size = Math.random() * 2 + 1;
    this.color = colors[Math.floor(Math.random() * colors.length)];
    this.angle = Math.random() * 360;
    this.speed = Math.random() * 0.5 + 0.1;
  }
  
  draw(ctx) {
    ctx.save();
    ctx.translate(this.x, this.y);
    // Draw slanted dashes
    ctx.rotate((45 * Math.PI) / 180);
    ctx.fillStyle = this.color;
    ctx.beginPath();
    ctx.roundRect(-this.size, -this.size * 3, this.size * 2, this.size * 6, this.size);
    ctx.fill();
    ctx.restore();
  }
  
  update(mouse) {
    // Gentle rotation around its base
    this.angle += this.speed;
    let dx = mouse.x - this.x;
    let dy = mouse.y - this.y;
    let distance = Math.sqrt(dx * dx + dy * dy);
    
    // Mouse repelling physics
    if (mouse.x !== null && distance < mouse.radius) {
      let forceDirectionX = dx / distance;
      let forceDirectionY = dy / distance;
      let force = (mouse.radius - distance) / mouse.radius;
      let directionX = forceDirectionX * force * 5;
      let directionY = forceDirectionY * force * 5;
      this.x -= directionX;
      this.y -= directionY;
    } else {
      if (this.x !== this.baseX) {
        let dx = this.x - this.baseX;
        this.x -= dx / 10;
      }
      if (this.y !== this.baseY) {
        let dy = this.y - this.baseY;
        this.y -= dy / 10;
      }
    }
  }
}

function initCanvas() {
  if (!canvasEl.value) return;
  const canvas = canvasEl.value;
  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;
  ctx = canvas.getContext('2d');
  
  particles = [];
  
  // Create a swirl/spiral distribution
  const numParticles = 400;
  const centerX = canvas.width / 2;
  const centerY = canvas.height / 2;
  
  for (let i = 0; i < numParticles; i++) {
    const angle = i * 0.1;
    const radius = 5 + i * 1.5;
    const paddingVariance = Math.random() * 40 - 20;
    const x = centerX + Math.cos(angle) * radius + paddingVariance;
    const y = centerY + Math.sin(angle) * radius + paddingVariance;
    
    particles.push(new Particle(x, y, canvas));
  }
}

function animate() {
  if (!ctx) return;
  ctx.clearRect(0, 0, canvasEl.value.width, canvasEl.value.height);
  
  for (let i = 0; i < particles.length; i++) {
    particles[i].update(mouse);
    particles[i].draw(ctx);
  }
  
  animationFrameId = requestAnimationFrame(animate);
}

function handleMouseMove(e) {
  mouse.x = e.x;
  mouse.y = e.y;
}

function handleResize() {
  if (canvasEl.value) {
    canvasEl.value.width = window.innerWidth;
    canvasEl.value.height = window.innerHeight;
    initCanvas();
  }
}

onMounted(() => {
  initCanvas();
  animate();
  window.addEventListener('resize', handleResize);
  window.addEventListener('mouseout', () => {
    mouse.x = null;
    mouse.y = null;
  });
})

onUnmounted(() => {
  if (animationFrameId) cancelAnimationFrame(animationFrameId);
  window.removeEventListener('resize', handleResize);
})

async function handleSubmit() {
  if (!username.value || !password.value || loading.value) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const action = mode.value === 'login' ? loginWithPassword : registerWithPassword
    const payload = await action(props.baseUrl, username.value, password.value)
    emit('authenticated', payload)
  } catch (error) {
    errorText.value = error.message || '认证失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-wrapper {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.particle-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 0;
  pointer-events: none; /* Let mousemove pass through to wrapper */
}

.auth-card {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 420px;
  padding: 40px 32px;
  animation: slideUp 0.6s ease-out;
  margin: 20px;
  background: rgba(15, 17, 26, 0.45); /* slightly more transparent for the canvas */
}

.auth-header {
  text-align: center;
  margin-bottom: 32px;
}

.title {
  font-size: 2.2rem;
  margin-bottom: 8px;
  font-weight: 800;
}

.subtitle {
  color: var(--text-muted);
  font-size: 0.95rem;
}

.auth-tabs {
  display: flex;
  background: rgba(0,0,0,0.2);
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 24px;
}

.auth-tab {
  flex: 1;
  background: transparent;
  border: none;
  padding: 12px;
  color: var(--text-muted);
  border-radius: 8px;
  font-weight: 600;
  transition: all 0.3s;
}

.auth-tab.active {
  background: rgba(255,255,255,0.1);
  color: var(--text-main);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-group label {
  font-size: 0.85rem;
  font-weight: 500;
  color: #d1d5db;
  padding-left: 4px;
}

.glow-input {
  width: 100%;
  padding: 14px 16px;
  background: rgba(0,0,0,0.2);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 12px;
  color: var(--text-main);
  font-size: 1rem;
  transition: all 0.3s ease;
}

.glow-input:focus {
  background: rgba(0,0,0,0.4);
  border-color: var(--primary);
  box-shadow: 0 0 0 4px rgba(78, 204, 163, 0.15);
}

.auth-error {
  color: var(--danger);
  font-size: 0.85rem;
  text-align: center;
  background: rgba(255, 118, 117, 0.1);
  padding: 10px;
  border-radius: 8px;
  border: 1px solid rgba(255, 118, 117, 0.2);
}

.submit-btn {
  margin-top: 10px;
  padding: 15px;
  border: none;
  border-radius: 12px;
  background: var(--gradient-accent);
  color: white;
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: 1px;
  box-shadow: 0 4px 15px rgba(108, 92, 231, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(108, 92, 231, 0.5);
}

/* Loading Dots */
.loading-dots {
  display: flex;
  gap: 6px;
  height: 20px;
  align-items: center;
}

.loading-dots i {
  width: 8px;
  height: 8px;
  background: white;
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out;
}

.loading-dots i:nth-child(1) { animation-delay: -0.32s; }
.loading-dots i:nth-child(2) { animation-delay: -0.16s; }
</style>
