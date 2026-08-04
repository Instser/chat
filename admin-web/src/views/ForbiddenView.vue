<template>
  <div class="wrap">
    <el-result icon="warning" title="403 无管理员权限" sub-title="当前账号不是 ROLE_ADMIN，无法进入管理后台。">
      <template #extra>
        <el-button type="primary" @click="$router.push('/login')">返回登录</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100vh;
  display: grid;
  place-items: center;
}
</style>
