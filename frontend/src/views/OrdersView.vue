<!-- AI-assisted order history with Codex. -->
<script setup>
import { ref, onMounted, computed } from "vue";
import { request } from "../lib/api";
import { statuses, money, time, notify } from "../lib/shop";
const orders = ref([]),
  total = ref(0),
  page = ref(1),
  status = ref(""),
  loading = ref(true),
  busy = ref(false),
  error = ref(""),
  payment = ref(null),
  confirmDialog = ref(null);
const pages = computed(() => Math.max(1, Math.ceil(total.value / 8)));
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await request(
      "/orders?" +
        new URLSearchParams({
          status: status.value,
          page: page.value,
          size: 8,
        }),
    );
    orders.value = data.items;
    total.value = data.total;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
function filter(value) {
  status.value = value;
  page.value = 1;
  load();
}
async function action(order, name) {
  if (busy.value) return;
  busy.value = true;
  try {
    await request("/orders/" + order.id + "/" + name, { method: "POST" });
    notify(
      name === "pay"
        ? "演示付款成功，未产生实际扣款。"
        : name === "cancel"
          ? "订单已取消，库存已恢复。"
          : "已确认收货，谢谢你的体验。",
    );
    confirmDialog.value?.close();
    await load();
  } catch (e) {
    notify(e.message, "error");
  } finally {
    busy.value = false;
  }
}
function askPay(order) {
  payment.value = order;
  confirmDialog.value.showModal();
}
onMounted(load);
</script>
<template>
  <section class="page-intro">
    <span class="kicker">LITTLE THINGS, ON THEIR WAY</span>
    <h1>我的订单</h1>
    <p>每一件喜欢，都有迹可循。</p>
  </section>
  <div class="order-filters filters">
    <button :aria-pressed="status === ''" @click="filter('')">全部订单</button
    ><button
      v-for="(label, key) in statuses"
      :key="key"
      :aria-pressed="status === key"
      @click="filter(key)"
    >
      {{ label }}
    </button>
  </div>
  <p v-if="loading" class="state">正在查找你的订单…</p>
  <div v-else-if="error" class="state" role="alert">
    <p>{{ error }}</p>
    <button @click="load">重新加载</button>
  </div>
  <div v-else-if="!orders.length" class="empty-card">
    <span class="empty-symbol">⌁</span>
    <h2>这里还没有订单。</h2>
    <p>喜欢的小物，正在等你发现。</p>
    <a class="primary" href="#/">发现日常选物 ↗</a>
  </div>
  <section v-else class="order-list" aria-label="订单列表">
    <article v-for="order in orders" :key="order.id" class="order-card">
      <div class="order-top">
        <div>
          <span class="order-number">{{ order.orderNumber }}</span
          ><span v-if="order.sample" class="sample-badge">预置演示</span
          ><span class="muted">{{ time(order.createdAt) }}</span>
        </div>
        <span class="status-badge" :class="order.status">{{
          statuses[order.status]
        }}</span>
      </div>
      <div class="order-products">
        <div
          v-for="item in order.items"
          :key="item.productId"
          class="order-product"
        >
          <img
            :src="item.imageUrl"
            :alt="item.productName"
            @error="(event) => (event.target.src = '/images/book-fallback.svg')"
          />
          <div>
            <h3>{{ item.productName }}</h3>
            <span class="muted"
              >¥{{ money(item.unitPrice) }} × {{ item.quantity }}</span
            >
          </div>
          <strong>¥{{ money(item.unitPrice * item.quantity) }}</strong>
        </div>
      </div>
      <div class="order-delivery">
        <p>
          <span class="muted">收件资料</span>{{ order.recipientName }} ·
          {{ order.recipientPhone }} · {{ order.shippingAddress }}
        </p>
        <p><span class="muted">通知邮箱</span>{{ order.recipientEmail }}</p>
        <p v-if="order.trackingNumber">
          <span class="muted">演示物流</span>{{ order.trackingNumber }} ·
          发货通知将发送至订单邮箱
        </p>
      </div>
      <div class="order-bottom">
        <span
          >订单合计 <strong>¥{{ money(order.totalAmount) }}</strong></span
        >
        <div class="button-row">
          <template v-if="order.status === 'PENDING_PAYMENT'"
            ><button
              class="secondary"
              :disabled="busy"
              @click="action(order, 'cancel')"
            >
              取消订单</button
            ><button class="primary" :disabled="busy" @click="askPay(order)">
              演示付款
            </button></template
          ><button
            v-if="order.status === 'SHIPPED'"
            class="primary"
            :disabled="busy"
            @click="action(order, 'complete')"
          >
            确认收货</button
          ><span v-if="order.status === 'PAID'" class="muted"
            >店长正在准备你的小物</span
          ><span v-if="order.status === 'COMPLETED'" class="muted"
            >谢谢，让喜欢发生</span
          >
        </div>
      </div>
    </article>
  </section>
  <div v-if="pages > 1" class="pagination">
    <button
      :disabled="page <= 1"
      @click="
        page--;
        load();
      "
    >
      上一页</button
    ><span>{{ page }} / {{ pages }}</span
    ><button
      :disabled="page >= pages"
      @click="
        page++;
        load();
      "
    >
      下一页
    </button>
  </div>
  <dialog
    ref="confirmDialog"
    class="confirm-dialog"
    aria-labelledby="pay-title"
  >
    <h2 id="pay-title">确认演示付款</h2>
    <p>
      订单金额 <strong>¥{{ money(payment?.totalAmount) }}</strong>
    </p>
    <p class="muted">
      此操作仅更新课程演示订单状态，不会扣款，也无需提供银行卡或支付账号。
    </p>
    <div class="button-row">
      <button class="secondary" :disabled="busy" @click="confirmDialog.close()">
        稍后再说</button
      ><button class="primary" :disabled="busy" @click="action(payment, 'pay')">
        {{ busy ? "正在处理…" : "确认演示付款" }}
      </button>
    </div>
  </dialog>
</template>
