/**
 * PayPal支付集成前端API调用示例
 * 
 * 基于测试验证的完整支付流程，提供前端开发参考
 * 适用于React、Vue、Angular等现代前端框架
 * 
 * @author Claude Code
 * @version v2.0.3
 * @date 2025-09-10
 */

// ================================
// 1. API配置和工具函数
// ================================

const API_CONFIG = {
  baseURL: 'http://localhost:8080/api', // 生产环境请替换为实际域名
  timeout: 30000, // 30秒超时
  retryAttempts: 3 // 重试次数
};

/**
 * HTTP请求工具函数
 * 支持自动重试和错误处理
 */
async function apiRequest(url, options = {}) {
  const defaultOptions = {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    },
    credentials: 'include', // 支持Cookie认证
    ...options
  };

  for (let attempt = 1; attempt <= API_CONFIG.retryAttempts; attempt++) {
    try {
      const response = await fetch(`${API_CONFIG.baseURL}${url}`, defaultOptions);
      
      if (!response.ok) {
        throw new Error(`HTTP Error: ${response.status} ${response.statusText}`);
      }
      
      const data = await response.json();
      
      // 检查业务响应码
      if (data.code !== 200) {
        throw new PaymentError(data.code, data.message, data.data);
      }
      
      return data;
    } catch (error) {
      console.error(`API请求失败 (尝试 ${attempt}/${API_CONFIG.retryAttempts}):`, error);
      
      if (attempt === API_CONFIG.retryAttempts) {
        throw error;
      }
      
      // 指数退避重试策略
      await new Promise(resolve => setTimeout(resolve, Math.pow(2, attempt) * 1000));
    }
  }
}

/**
 * 支付错误类
 */
class PaymentError extends Error {
  constructor(code, message, data = null) {
    super(message);
    this.name = 'PaymentError';
    this.code = code;
    this.data = data;
  }
}

// ================================
// 2. 用户认证API
// ================================

/**
 * 用户登录
 * @param {string} usernameOrEmail 用户名或邮箱
 * @param {string} password 密码
 * @returns {Promise<Object>} 用户信息
 */
async function login(usernameOrEmail, password) {
  console.log('[认证] 开始用户登录...');
  
  const response = await apiRequest('/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      usernameOrEmail,
      password
    })
  });
  
  console.log('[认证] 登录成功，用户信息:', response.data);
  return response.data;
}

/**
 * 获取当前用户信息
 * @returns {Promise<Object>} 用户信息包含套餐信息
 */
async function getCurrentUser() {
  console.log('[认证] 获取当前用户信息...');
  
  const response = await apiRequest('/auth/me');
  
  console.log('[认证] 用户信息获取成功:', response.data);
  return response.data;
}

// ================================
// 3. 套餐管理API
// ================================

/**
 * 获取可用套餐列表
 * @param {string} currency 货币类型，默认USD
 * @returns {Promise<Array>} 套餐列表
 */
async function getPackages(currency = 'USD') {
  console.log(`[套餐] 获取套餐列表，货币: ${currency}`);
  
  const response = await apiRequest(`/payment/packages?currency=${currency}`);
  
  console.log('[套餐] 套餐列表获取成功:', response.data);
  return response.data;
}

/**
 * 获取指定套餐详细信息
 * @param {number} packageId 套餐ID
 * @param {string} currency 货币类型
 * @returns {Promise<Object>} 套餐详情和支付方式
 */
async function getPackageInfo(packageId, currency = 'USD') {
  console.log(`[套餐] 获取套餐详情，ID: ${packageId}, 货币: ${currency}`);
  
  const response = await apiRequest('/payment/package/info', {
    method: 'POST',
    body: JSON.stringify({
      packageId,
      currency
    })
  });
  
  console.log('[套餐] 套餐详情获取成功:', response.data);
  return response.data;
}

// ================================
// 4. PayPal支付流程API
// ================================

/**
 * 创建PayPal支付订单
 * @param {number} packageId 套餐ID
 * @param {string} currency 货币类型
 * @returns {Promise<Object>} PayPal订单信息
 */
async function createPayPalPayment(packageId, currency = 'USD') {
  console.log(`[PayPal] 创建支付订单，套餐ID: ${packageId}, 货币: ${currency}`);
  
  const response = await apiRequest('/payment/create', {
    method: 'POST',
    body: JSON.stringify({
      packageId,
      currency,
      paymentMethod: 'paypal'
    })
  });
  
  const paymentData = response.data;
  console.log('[PayPal] 支付订单创建成功:', {
    orderNo: paymentData.orderNo,
    paypalOrderId: paymentData.paypalOrderId,
    amount: paymentData.amount,
    currency: paymentData.currency
  });
  
  return paymentData;
}

/**
 * 查询支付订单状态
 * @param {string} orderNo 订单号
 * @returns {Promise<Object>} 订单状态信息
 */
async function getPaymentStatus(orderNo) {
  console.log(`[PayPal] 查询支付状态，订单号: ${orderNo}`);
  
  const response = await apiRequest(`/payment/status/${orderNo}`);
  
  console.log('[PayPal] 支付状态查询成功:', response.data);
  return response.data;
}

/**
 * 轮询支付状态直到完成
 * @param {string} orderNo 订单号
 * @param {number} maxDuration 最大轮询时间（毫秒）
 * @param {number} interval 轮询间隔（毫秒）
 * @returns {Promise<Object>} 最终支付状态
 */
async function pollPaymentStatus(orderNo, maxDuration = 300000, interval = 3000) {
  console.log(`[PayPal] 开始轮询支付状态，订单号: ${orderNo}`);
  
  const startTime = Date.now();
  let checkCount = 0;
  
  while (Date.now() - startTime < maxDuration) {
    checkCount++;
    
    try {
      console.log(`[PayPal] 第 ${checkCount} 次检查支付状态...`);
      
      const statusResult = await getPaymentStatus(orderNo);
      const status = statusResult.status;
      const message = statusResult.message;
      
      console.log(`[PayPal] 订单状态: ${message} (状态码: ${status})`);
      
      if (status === 1) {
        console.log('[PayPal] 🎉 支付成功！订单已完成');
        return statusResult;
      } else if (status === 2 || status === 3) {
        console.log('[PayPal] ❌ 支付失败或已取消');
        throw new PaymentError(status, message);
      }
      
      // 等待下次检查
      if (Date.now() - startTime < maxDuration - interval) {
        console.log(`[PayPal] 等待 ${interval/1000} 秒后继续检查...`);
        await new Promise(resolve => setTimeout(resolve, interval));
      }
      
    } catch (error) {
      if (error instanceof PaymentError) {
        throw error;
      }
      console.warn(`[PayPal] 查询状态异常: ${error.message}`);
      await new Promise(resolve => setTimeout(resolve, interval));
    }
  }
  
  throw new Error('支付状态轮询超时，请手动检查支付结果');
}

// ================================
// 5. PayPal JS SDK集成辅助函数
// ================================

/**
 * 动态加载PayPal JS SDK
 * @param {string} clientId PayPal客户端ID
 * @param {string} currency 货币类型
 * @param {string} environment 环境 (sandbox/production)
 * @returns {Promise<void>}
 */
async function loadPayPalSDK(clientId, currency = 'USD', environment = 'sandbox') {
  return new Promise((resolve, reject) => {
    if (window.paypal) {
      resolve();
      return;
    }
    
    const script = document.createElement('script');
    script.src = `https://www.paypal.com/sdk/js?client-id=${clientId}&currency=${currency}&intent=capture&enable-funding=venmo,card&disable-funding=credit`;
    script.async = true;
    
    script.onload = () => {
      console.log('[PayPal SDK] JavaScript SDK 加载成功');
      resolve();
    };
    
    script.onerror = () => {
      console.error('[PayPal SDK] JavaScript SDK 加载失败');
      reject(new Error('PayPal SDK 加载失败'));
    };
    
    document.head.appendChild(script);
  });
}

/**
 * 创建PayPal按钮并处理支付
 * @param {string} containerId PayPal按钮容器ID
 * @param {Object} paymentData 支付订单数据
 * @param {Function} onSuccess 支付成功回调
 * @param {Function} onError 支付失败回调
 * @returns {Promise<void>}
 */
async function createPayPalButton(containerId, paymentData, onSuccess, onError) {
  const { paypalOrderId, paypalClientId, amount, currency, environment } = paymentData;
  
  // 加载PayPal SDK
  await loadPayPalSDK(paypalClientId, currency, environment);
  
  console.log('[PayPal SDK] 创建支付按钮...');
  
  window.paypal.Buttons({
    // 设置样式
    style: {
      layout: 'vertical',
      color: 'gold',
      shape: 'pill',
      label: 'paypal',
      height: 40
    },
    
    // 创建订单 - 使用后端已创建的订单ID
    createOrder: function(data, actions) {
      console.log('[PayPal SDK] 使用已创建的订单ID:', paypalOrderId);
      return paypalOrderId;
    },
    
    // 批准支付后的处理
    onApprove: function(data, actions) {
      console.log('[PayPal SDK] 支付已批准，订单ID:', data.orderID);
      
      return actions.order.capture().then(function(details) {
        console.log('[PayPal SDK] 支付捕获成功:', details);
        
        // 开始轮询后端支付状态
        console.log('[PayPal SDK] 开始验证后端支付状态...');
        
        pollPaymentStatus(paymentData.orderNo)
          .then(statusResult => {
            console.log('[PayPal SDK] 🎉 后端支付验证成功！');
            onSuccess(statusResult, details);
          })
          .catch(error => {
            console.error('[PayPal SDK] ❌ 后端支付验证失败:', error);
            onError(error);
          });
      });
    },
    
    // 取消支付
    onCancel: function(data) {
      console.log('[PayPal SDK] 用户取消了支付');
      onError(new PaymentError(2, '用户取消了支付'));
    },
    
    // 支付错误处理
    onError: function(err) {
      console.error('[PayPal SDK] 支付过程中发生错误:', err);
      onError(new PaymentError(0, 'PayPal支付过程中发生错误', err));
    }
  }).render(`#${containerId}`);
  
  console.log('[PayPal SDK] PayPal支付按钮创建完成');
}

// ================================
// 6. 完整支付流程封装
// ================================

/**
 * 完整的PayPal支付流程处理类
 */
class PayPalPaymentFlow {
  constructor() {
    this.currentPayment = null;
    this.statusPolling = null;
  }
  
  /**
   * 初始化支付流程
   * @param {number} packageId 套餐ID
   * @param {string} currency 货币类型
   * @param {string} containerId PayPal按钮容器ID
   * @returns {Promise<Object>} 支付订单信息
   */
  async initiate(packageId, currency = 'USD', containerId = 'paypal-button-container') {
    try {
      console.log('[支付流程] 🚀 开始初始化PayPal支付流程');
      
      // 1. 创建后端支付订单
      console.log('[支付流程] 第1步：创建后端支付订单');
      this.currentPayment = await createPayPalPayment(packageId, currency);
      
      // 2. 创建PayPal支付按钮
      console.log('[支付流程] 第2步：创建PayPal支付按钮');
      await createPayPalButton(
        containerId,
        this.currentPayment,
        this.handlePaymentSuccess.bind(this),
        this.handlePaymentError.bind(this)
      );
      
      console.log('[支付流程] ✅ PayPal支付流程初始化完成');
      console.log('[支付流程] 请用户点击PayPal按钮完成支付');
      
      return this.currentPayment;
      
    } catch (error) {
      console.error('[支付流程] ❌ 支付流程初始化失败:', error);
      throw error;
    }
  }
  
  /**
   * 支付成功处理
   * @param {Object} backendStatus 后端支付状态
   * @param {Object} paypalDetails PayPal支付详情
   */
  async handlePaymentSuccess(backendStatus, paypalDetails) {
    console.log('[支付流程] 🎉 支付成功处理开始');
    
    try {
      // 1. 更新用户信息
      console.log('[支付流程] 更新用户信息...');
      const updatedUser = await getCurrentUser();
      
      // 2. 触发成功回调事件
      const successEvent = new CustomEvent('paypal-payment-success', {
        detail: {
          order: this.currentPayment,
          status: backendStatus,
          paypal: paypalDetails,
          user: updatedUser
        }
      });
      
      document.dispatchEvent(successEvent);
      
      console.log('[支付流程] ✅ 支付成功处理完成');
      console.log('[支付流程] 用户套餐已升级:', {
        packageId: updatedUser.packageId,
        packageName: updatedUser.packageName
      });
      
    } catch (error) {
      console.error('[支付流程] 支付成功后处理异常:', error);
    }
  }
  
  /**
   * 支付失败处理
   * @param {Error} error 错误信息
   */
  handlePaymentError(error) {
    console.error('[支付流程] ❌ 支付失败:', error);
    
    // 触发失败回调事件
    const errorEvent = new CustomEvent('paypal-payment-error', {
      detail: {
        error,
        order: this.currentPayment
      }
    });
    
    document.dispatchEvent(errorEvent);
  }
  
  /**
   * 取消当前支付流程
   */
  cancel() {
    console.log('[支付流程] 取消支付流程');
    
    if (this.statusPolling) {
      clearInterval(this.statusPolling);
      this.statusPolling = null;
    }
    
    this.currentPayment = null;
  }
}

// ================================
// 7. 使用示例
// ================================

/**
 * React组件使用示例
 */
class PayPalPaymentExample {
  
  /**
   * 基础支付流程示例
   */
  async basicExample() {
    try {
      // 1. 用户登录
      await login('user@example.com', 'password');
      
      // 2. 获取套餐列表
      const packages = await getPackages('USD');
      console.log('可用套餐:', packages);
      
      // 3. 选择套餐（假设选择ID为3的套餐）
      const selectedPackage = packages.find(pkg => pkg.id === 3);
      
      // 4. 初始化支付流程
      const paymentFlow = new PayPalPaymentFlow();
      await paymentFlow.initiate(selectedPackage.id, 'USD', 'paypal-button-container');
      
      // 5. 监听支付结果
      document.addEventListener('paypal-payment-success', (event) => {
        const { user, status } = event.detail;
        console.log('🎉 支付成功！用户套餐已升级:', user.packageName);
        
        // 跳转到成功页面或显示成功消息
        this.showSuccessMessage('支付成功！您的套餐已激活');
        this.redirectToUserDashboard();
      });
      
      document.addEventListener('paypal-payment-error', (event) => {
        const { error } = event.detail;
        console.error('❌ 支付失败:', error.message);
        
        // 显示错误消息
        this.showErrorMessage(error.message);
      });
      
    } catch (error) {
      console.error('支付流程异常:', error);
      this.showErrorMessage('支付流程异常，请稍后重试');
    }
  }
  
  /**
   * Vue.js组件使用示例
   */
  vueExample() {
    return {
      data() {
        return {
          packages: [],
          selectedPackage: null,
          paymentFlow: null,
          isLoading: false,
          paymentStatus: 'idle' // idle, processing, success, error
        };
      },
      
      async mounted() {
        await this.loadPackages();
        this.setupPaymentListeners();
      },
      
      methods: {
        async loadPackages() {
          try {
            this.packages = await getPackages('USD');
          } catch (error) {
            this.$message.error('加载套餐失败');
          }
        },
        
        async initiatePayment(packageId) {
          try {
            this.isLoading = true;
            this.paymentStatus = 'processing';
            
            this.paymentFlow = new PayPalPaymentFlow();
            await this.paymentFlow.initiate(packageId, 'USD', 'paypal-button-container');
            
          } catch (error) {
            this.paymentStatus = 'error';
            this.$message.error('支付初始化失败');
          } finally {
            this.isLoading = false;
          }
        },
        
        setupPaymentListeners() {
          document.addEventListener('paypal-payment-success', (event) => {
            this.paymentStatus = 'success';
            this.$message.success('支付成功！套餐已激活');
            this.$router.push('/dashboard');
          });
          
          document.addEventListener('paypal-payment-error', (event) => {
            this.paymentStatus = 'error';
            this.$message.error(event.detail.error.message);
          });
        }
      }
    };
  }
  
  /**
   * 工具方法示例
   */
  showSuccessMessage(message) {
    // 可以使用各种UI库的消息组件
    // 例如：Element UI的 this.$message.success(message)
    // 或者：Ant Design的 message.success(message)
    console.log('✅ 成功:', message);
  }
  
  showErrorMessage(message) {
    console.error('❌ 错误:', message);
  }
  
  redirectToUserDashboard() {
    // window.location.href = '/dashboard';
    // 或使用路由：this.$router.push('/dashboard')
    console.log('🔄 跳转到用户控制台');
  }
}

// ================================
// 8. HTML模板示例
// ================================

const HTML_TEMPLATE = `
<!-- PayPal支付页面HTML模板 -->
<div class="paypal-payment-container">
  <!-- 套餐选择 -->
  <div class="package-selection">
    <h2>选择您的套餐</h2>
    <div id="packages-list">
      <!-- 套餐列表将通过JS动态加载 -->
    </div>
  </div>
  
  <!-- 支付区域 -->
  <div class="payment-section" style="display: none;">
    <h3>完成支付</h3>
    <div class="selected-package-info">
      <p>已选择套餐: <span id="selected-package-name"></span></p>
      <p>价格: <span id="selected-package-price"></span></p>
    </div>
    
    <!-- PayPal按钮容器 -->
    <div id="paypal-button-container" style="margin-top: 20px;"></div>
    
    <!-- 支付状态指示器 -->
    <div id="payment-status" class="payment-status">
      <div class="loading" style="display: none;">
        <p>🔄 处理中，请稍候...</p>
      </div>
      <div class="success" style="display: none;">
        <p>🎉 支付成功！正在激活套餐...</p>
      </div>
      <div class="error" style="display: none;">
        <p>❌ <span class="error-message"></span></p>
      </div>
    </div>
  </div>
</div>

<style>
.paypal-payment-container {
  max-width: 600px;
  margin: 0 auto;
  padding: 20px;
}

.package-selection, .payment-section {
  background: #f9f9f9;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.selected-package-info {
  background: #e7f3ff;
  padding: 15px;
  border-radius: 4px;
  border-left: 4px solid #1890ff;
}

.payment-status {
  margin-top: 20px;
  padding: 15px;
  border-radius: 4px;
}

.payment-status .loading {
  background: #fff7e6;
  border-left: 4px solid #fa8c16;
}

.payment-status .success {
  background: #f6ffed;
  border-left: 4px solid #52c41a;
}

.payment-status .error {
  background: #fff2f0;
  border-left: 4px solid #ff4d4f;
}
</style>
`;

// ================================
// 9. 导出API
// ================================

// ES6模块导出
export {
  // 基础API函数
  login,
  getCurrentUser,
  getPackages,
  getPackageInfo,
  createPayPalPayment,
  getPaymentStatus,
  pollPaymentStatus,
  
  // PayPal SDK工具
  loadPayPalSDK,
  createPayPalButton,
  
  // 支付流程类
  PayPalPaymentFlow,
  
  // 错误类
  PaymentError,
  
  // 配置对象
  API_CONFIG
};

// CommonJS导出 (Node.js)
if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    login,
    getCurrentUser,
    getPackages,
    getPackageInfo,
    createPayPalPayment,
    getPaymentStatus,
    pollPaymentStatus,
    loadPayPalSDK,
    createPayPalButton,
    PayPalPaymentFlow,
    PaymentError,
    API_CONFIG
  };
}

console.log('PayPal支付集成前端API已加载完成 v2.0.3');