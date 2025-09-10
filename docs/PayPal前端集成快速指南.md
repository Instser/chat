# PayPal支付前端集成快速指南

## 🚀 快速开始

基于完整测试验证的PayPal支付API，提供前端开发者快速集成指南。

### 📋 前提条件

- 后端PayPal支付API已部署并正常运行
- 用户已完成登录认证
- 获得PayPal沙箱或生产环境客户端ID

---

## 🎯 核心API调用流程

### 1. 基础配置

```javascript
const PAYMENT_CONFIG = {
  apiBase: 'http://localhost:8080/api', // 生产环境请替换
  paypalEnvironment: 'sandbox', // 或 'production'
  currency: 'USD'
};
```

### 2. 创建支付订单

```javascript
async function createPayment(packageId) {
  const response = await fetch(`${PAYMENT_CONFIG.apiBase}/payment/create`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include', // 重要：支持Cookie认证
    body: JSON.stringify({
      packageId: packageId,
      currency: PAYMENT_CONFIG.currency,
      paymentMethod: 'paypal'
    })
  });
  
  const result = await response.json();
  
  if (result.code !== 200) {
    throw new Error(result.message);
  }
  
  return result.data;
}
```

### 3. 集成PayPal JS SDK

```html
<!-- 在HTML头部加载PayPal SDK -->
<script src="https://www.paypal.com/sdk/js?client-id=YOUR_CLIENT_ID&currency=USD&intent=capture"></script>
```

### 4. 创建PayPal支付按钮

```javascript
async function initPayPalButton(paymentData) {
  paypal.Buttons({
    // 使用后端创建的订单ID
    createOrder: function(data, actions) {
      return paymentData.paypalOrderId;
    },
    
    // 支付完成处理
    onApprove: function(data, actions) {
      return actions.order.capture().then(function(details) {
        console.log('PayPal支付完成:', details);
        
        // 验证后端支付状态
        checkPaymentStatus(paymentData.orderNo);
      });
    },
    
    onError: function(err) {
      console.error('支付错误:', err);
      alert('支付过程中发生错误，请重试');
    }
  }).render('#paypal-button-container');
}
```

### 5. 验证支付状态

```javascript
async function checkPaymentStatus(orderNo) {
  const response = await fetch(`${PAYMENT_CONFIG.apiBase}/payment/status/${orderNo}`, {
    credentials: 'include'
  });
  
  const result = await response.json();
  
  if (result.code === 200 && result.data.status === 1) {
    // 支付成功
    alert('🎉 支付成功！您的套餐已激活');
    window.location.reload(); // 刷新页面显示新套餐
  } else {
    // 继续等待或显示错误
    setTimeout(() => checkPaymentStatus(orderNo), 3000);
  }
}
```

---

## 🎯 完整示例

### HTML结构

```html
<!DOCTYPE html>
<html>
<head>
    <title>PayPal支付示例</title>
    <script src="https://www.paypal.com/sdk/js?client-id=YOUR_PAYPAL_CLIENT_ID&currency=USD"></script>
</head>
<body>
    <div id="payment-container">
        <h2>选择套餐</h2>
        <button onclick="startPayment(3)">购买Pro套餐 - $5.00</button>
        <div id="paypal-button-container" style="margin-top: 20px; display: none;"></div>
        <div id="status-message"></div>
    </div>
</body>
</html>
```

### JavaScript实现

```javascript
const API_BASE = 'http://localhost:8080/api';

// 显示状态消息
function showMessage(message, type = 'info') {
  const statusDiv = document.getElementById('status-message');
  statusDiv.innerHTML = `<p class="${type}">${message}</p>`;
}

// 开始支付流程
async function startPayment(packageId) {
  try {
    showMessage('🔄 正在创建支付订单...', 'loading');
    
    // 1. 创建后端支付订单
    const paymentData = await createPayPalOrder(packageId);
    
    // 2. 显示PayPal按钮
    document.getElementById('paypal-button-container').style.display = 'block';
    
    // 3. 初始化PayPal按钮
    await initPayPalButton(paymentData);
    
    showMessage('✅ 请点击PayPal按钮完成支付', 'success');
    
  } catch (error) {
    showMessage(`❌ 错误: ${error.message}`, 'error');
  }
}

// 创建PayPal订单
async function createPayPalOrder(packageId) {
  const response = await fetch(`${API_BASE}/payment/create`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({
      packageId: packageId,
      currency: 'USD',
      paymentMethod: 'paypal'
    })
  });
  
  const result = await response.json();
  
  if (result.code !== 200) {
    throw new Error(result.message);
  }
  
  console.log('支付订单创建成功:', result.data);
  return result.data;
}

// 初始化PayPal按钮
async function initPayPalButton(paymentData) {
  paypal.Buttons({
    style: {
      layout: 'vertical',
      color: 'gold',
      shape: 'pill',
      label: 'paypal'
    },
    
    createOrder: function(data, actions) {
      console.log('使用PayPal订单ID:', paymentData.paypalOrderId);
      return paymentData.paypalOrderId;
    },
    
    onApprove: function(data, actions) {
      showMessage('🔄 正在处理支付...', 'loading');
      
      return actions.order.capture().then(function(details) {
        console.log('PayPal支付捕获成功:', details);
        
        // 开始验证后端状态
        pollPaymentStatus(paymentData.orderNo);
      });
    },
    
    onCancel: function(data) {
      showMessage('支付已取消', 'warning');
    },
    
    onError: function(err) {
      console.error('PayPal支付错误:', err);
      showMessage('支付过程中发生错误，请重试', 'error');
    }
  }).render('#paypal-button-container');
  
  console.log('PayPal按钮初始化完成');
}

// 轮询支付状态
async function pollPaymentStatus(orderNo, maxAttempts = 30) {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      console.log(`检查支付状态 (${attempt}/${maxAttempts})...`);
      
      const response = await fetch(`${API_BASE}/payment/status/${orderNo}`, {
        credentials: 'include'
      });
      
      const result = await response.json();
      
      if (result.code === 200) {
        const status = result.data.status;
        const message = result.data.message;
        
        console.log(`支付状态: ${message} (${status})`);
        
        if (status === 1) {
          // 支付成功
          showMessage('🎉 支付成功！您的套餐已激活，页面即将刷新...', 'success');
          setTimeout(() => {
            window.location.reload();
          }, 2000);
          return;
        } else if (status === 2 || status === 3) {
          // 支付失败或取消
          showMessage('❌ 支付失败或已取消', 'error');
          return;
        }
      }
      
      // 继续等待
      if (attempt < maxAttempts) {
        await new Promise(resolve => setTimeout(resolve, 3000));
      }
      
    } catch (error) {
      console.warn('状态查询异常:', error);
      await new Promise(resolve => setTimeout(resolve, 3000));
    }
  }
  
  showMessage('⏰ 支付状态确认超时，请手动刷新页面检查', 'warning');
}
```

### CSS样式

```css
#payment-container {
  max-width: 500px;
  margin: 50px auto;
  padding: 30px;
  border: 1px solid #ddd;
  border-radius: 8px;
  text-align: center;
}

button {
  background: #0070ba;
  color: white;
  border: none;
  padding: 12px 24px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
}

button:hover {
  background: #005ea6;
}

.loading { color: #fa8c16; }
.success { color: #52c41a; }
.error { color: #ff4d4f; }
.warning { color: #faad14; }

#paypal-button-container {
  max-width: 300px;
  margin: 0 auto;
}
```

---

## 📱 框架集成示例

### React Hook示例

```jsx
import { useState, useEffect } from 'react';

function PayPalPayment({ packageId, amount }) {
  const [paymentStatus, setPaymentStatus] = useState('idle');
  const [message, setMessage] = useState('');
  
  const handlePayment = async () => {
    try {
      setPaymentStatus('creating');
      setMessage('正在创建支付订单...');
      
      // 创建支付订单
      const paymentData = await createPayPalOrder(packageId);
      
      setPaymentStatus('ready');
      setMessage('请完成PayPal支付');
      
      // 初始化PayPal按钮 (使用useEffect或ref)
      initPayPalButton(paymentData);
      
    } catch (error) {
      setPaymentStatus('error');
      setMessage(`创建订单失败: ${error.message}`);
    }
  };
  
  return (
    <div className="paypal-payment">
      <button onClick={handlePayment} disabled={paymentStatus === 'creating'}>
        购买套餐 ${amount}
      </button>
      
      {paymentStatus === 'ready' && (
        <div id="paypal-button-container" />
      )}
      
      <div className={`message ${paymentStatus}`}>
        {message}
      </div>
    </div>
  );
}
```

### Vue组件示例

```vue
<template>
  <div class="paypal-payment">
    <button @click="handlePayment" :disabled="isLoading">
      购买套餐 ${{ amount }}
    </button>
    
    <div v-if="showPayPalButton" id="paypal-button-container" />
    
    <div :class="['message', paymentStatus]">
      {{ message }}
    </div>
  </div>
</template>

<script>
export default {
  props: {
    packageId: Number,
    amount: Number
  },
  
  data() {
    return {
      paymentStatus: 'idle',
      message: '',
      isLoading: false,
      showPayPalButton: false
    };
  },
  
  methods: {
    async handlePayment() {
      try {
        this.isLoading = true;
        this.message = '正在创建支付订单...';
        
        const paymentData = await createPayPalOrder(this.packageId);
        
        this.showPayPalButton = true;
        this.message = '请完成PayPal支付';
        
        this.$nextTick(() => {
          initPayPalButton(paymentData);
        });
        
      } catch (error) {
        this.paymentStatus = 'error';
        this.message = `创建订单失败: ${error.message}`;
      } finally {
        this.isLoading = false;
      }
    }
  }
};
</script>
```

---

## ⚠️ 重要注意事项

### 1. 安全性
- 所有API调用必须使用`credentials: 'include'`支持Cookie认证
- 生产环境必须使用HTTPS
- PayPal客户端ID要与环境匹配

### 2. 错误处理
- 网络异常要有重试机制
- 支付状态轮询要设置超时时间
- 用户友好的错误提示

### 3. 用户体验
- 支付过程中显示加载状态
- 支付完成后给予明确反馈
- 提供取消支付的选项

### 4. 测试
- 使用PayPal沙箱环境测试
- 测试各种支付场景（成功、失败、取消）
- 验证套餐激活是否正确

---

## 🔧 故障排除

### 常见问题

1. **401未授权错误**
   - 确保用户已登录
   - 检查Cookie设置是否正确

2. **PayPal按钮不显示**
   - 检查PayPal SDK是否正确加载
   - 确认客户端ID是否正确

3. **支付成功但套餐未激活**
   - 检查后端Webhook处理是否正常
   - 查看后端日志确认异步处理状态

4. **支付状态轮询超时**
   - 增加轮询时间或次数
   - 检查后端支付处理性能

---

## 📞 技术支持

如遇到集成问题，请提供以下信息：
- 浏览器控制台错误日志
- 网络请求详情
- 后端API响应内容
- PayPal环境信息（沙箱/生产）

祝您集成成功！🚀