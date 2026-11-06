<!-- AI-assisted accessible native dialog with Codex. -->
<script setup>
import { ref, watch } from "vue";
import { request, session } from "../lib/api";
import { shop, notify, addToCart, refreshCart } from "../lib/shop";
const dialog = ref(null),
  mode = ref("login"),
  busy = ref(false),
  error = ref("");
const form = ref({ username: "", password: "", email: "", displayName: "" });
watch(
  () => shop.authOpen,
  (value) => {
    if (value) {
      error.value = "";
      dialog.value?.showModal();
    } else dialog.value?.close();
  },
);
function close() {
  if (!busy.value) shop.authOpen = false;
}
async function submit() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await request("/auth/" + mode.value, {
      method: "POST",
      body:
        mode.value === "login"
          ? { username: form.value.username, password: form.value.password }
          : form.value,
    });
    shop.user = await session();
    await refreshCart();
    shop.authOpen = false;
    notify("欢迎回来，" + shop.user.displayName + "。");
    form.value.password = "";
    if (shop.pendingProduct) {
      const pending = shop.pendingProduct;
      shop.pendingProduct = null;
      await addToCart(pending.productId, pending.quantity);
    }
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <dialog
    ref="dialog"
    class="auth-dialog"
    aria-labelledby="auth-title"
    @cancel.prevent="close"
    @close="shop.authOpen = false"
  >
    <div class="dialog-top">
      <span class="kicker">A LITTLE SPACE FOR YOU</span
      ><button
        class="close"
        aria-label="关闭登录窗口"
        :disabled="busy"
        @click="close"
      >
        ×
      </button>
    </div>
    <div class="auth-content">
      <span class="auth-flower" aria-hidden="true">✳</span>
      <h2 id="auth-title">
        {{ mode === "login" ? "欢迎回到拾光。" : "给日常留一个位置。" }}
      </h2>
      <p class="muted">
        {{
          mode === "login"
            ? "登录后，把喜欢的小物带回去。"
            : "创建账号，收藏生活里的每一点喜欢。"
        }}
      </p>
      <div class="auth-tabs">
        <button
          :class="{ active: mode === 'login' }"
          @click="
            mode = 'login';
            error = '';
          "
        >
          登录</button
        ><button
          :class="{ active: mode === 'register' }"
          @click="
            mode = 'register';
            error = '';
          "
        >
          注册
        </button>
      </div>
      <form @submit.prevent="submit">
        <label
          >用户名<input
            v-model="form.username"
            required
            minlength="3"
            maxlength="32"
            pattern="[A-Za-z0-9_]{3,32}"
            autocomplete="username"
            placeholder="3–32位字母、数字或下划线" /></label
        ><label v-if="mode === 'register'"
          >怎么称呼你<input
            v-model="form.displayName"
            required
            maxlength="50"
            autocomplete="nickname"
            placeholder="你的昵称" /></label
        ><label v-if="mode === 'register'"
          >邮箱<input
            v-model="form.email"
            required
            type="email"
            maxlength="254"
            autocomplete="email"
            placeholder="用于接收发货通知" /></label
        ><label
          >密码<input
            v-model="form.password"
            required
            :minlength="mode === 'register' ? 8 : undefined"
            maxlength="72"
            :autocomplete="
              mode === 'login' ? 'current-password' : 'new-password'
            "
            type="password"
            placeholder="至少8位，请使用独立的演示密码"
        /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="primary wide" :disabled="busy">
          {{
            busy ? "正在处理…" : mode === "login" ? "登录拾光" : "创建我的账号"
          }}
          <span>↗</span>
        </button>
      </form>
      <p class="fineprint">
        课程演示商城，无实际扣款或物流。请勿填写敏感个人资料。
      </p>
    </div>
  </dialog>
</template>
