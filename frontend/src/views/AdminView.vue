<!-- AI-assisted management UI with Codex. Data comes from role-protected admin endpoints. -->
<script setup>
import { ref, computed, onMounted } from "vue";
import { request } from "../lib/api";
import { notify, money, statuses, time } from "../lib/shop";
const tab = ref("overview"),
  stats = ref(null),
  products = ref([]),
  orders = ref([]),
  mails = ref([]);
const busy = ref(false),
  loading = ref(true),
  error = ref(""),
  keyword = ref(""),
  page = ref(1),
  total = ref(0),
  orderStatus = ref("");
const editor = ref(null),
  shippingDialog = ref(null),
  mailDialog = ref(null),
  selectedMail = ref(null),
  editingId = ref(null),
  shipping = ref({ id: null, trackingNumber: "" });
const blank = () => ({
  name: "",
  category: "日常选物",
  price: 28,
  stock: 20,
  imageUrl: "/images/notebook.svg",
  tagline: "",
  description: "",
  active: true,
});
const form = ref(blank());
const pages = computed(() =>
  Math.max(1, Math.ceil(total.value / (tab.value === "products" ? 12 : 10))),
);
const trend = computed(() => {
  const data = stats.value?.trend || [],
    days = [];
  for (let index = 13; index >= 0; index--) {
    const date = new Date();
    date.setDate(date.getDate() - index);
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    const row = data.find((r) => String(r.day).slice(0, 10) === key);
    days.push({ day: key.slice(5), value: Number(row?.revenue || 0) });
  }
  const max = Math.max(1, ...days.map((d) => d.value));
  return days.map((d) => ({
    ...d,
    height: Math.max(d.value ? 3 : 0, (d.value / max) * 100),
  }));
});
async function load() {
  loading.value = true;
  error.value = "";
  try {
    if (tab.value === "overview") stats.value = await request("/admin/stats");
    if (tab.value === "products") {
      const data = await request(
        "/admin/products?" +
          new URLSearchParams({
            keyword: keyword.value,
            page: page.value,
            size: 12,
          }),
      );
      products.value = data.items;
      total.value = data.total;
    }
    if (tab.value === "orders") {
      const data = await request(
        "/admin/orders?" +
          new URLSearchParams({
            status: orderStatus.value,
            page: page.value,
            size: 10,
          }),
      );
      orders.value = data.items;
      total.value = data.total;
    }
    if (tab.value === "mail") mails.value = await request("/admin/mail");
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
function selectTab(value) {
  tab.value = value;
  page.value = 1;
  total.value = 0;
  load();
}
function edit(product) {
  editingId.value = product?.id || null;
  form.value = product
    ? {
        name: product.name,
        category: product.category,
        price: product.price,
        stock: product.stock,
        imageUrl: product.image_url,
        tagline: product.tagline,
        description: product.description,
        active: !!product.active,
      }
    : blank();
  editor.value.showModal();
}
async function save() {
  if (busy.value) return;
  busy.value = true;
  try {
    await request(
      "/admin/products" + (editingId.value ? "/" + editingId.value : ""),
      { method: editingId.value ? "PUT" : "POST", body: form.value },
    );
    editor.value.close();
    notify("商品资料已保存。");
    await load();
  } catch (e) {
    notify(e.message, "error");
  } finally {
    busy.value = false;
  }
}
async function remove(product) {
  if (busy.value) return;
  busy.value = true;
  try {
    await request("/admin/products/" + product.id, { method: "DELETE" });
    notify("商品已下架，历史订单资料保留。");
    await load();
  } catch (e) {
    notify(e.message, "error");
  } finally {
    busy.value = false;
  }
}
function askShip(order) {
  shipping.value = {
    id: order.id,
    trackingNumber: "DEMO-" + order.orderNumber,
  };
  shippingDialog.value.showModal();
}
async function ship() {
  if (busy.value) return;
  busy.value = true;
  try {
    await request("/admin/orders/" + shipping.value.id + "/ship", {
      method: "POST",
      body: { trackingNumber: shipping.value.trackingNumber },
    });
    shippingDialog.value.close();
    notify("已标记发货，确认邮件已进入发送队列。");
    await load();
  } catch (e) {
    notify(e.message, "error");
  } finally {
    busy.value = false;
  }
}
async function retry(mail) {
  try {
    await request("/admin/mail/" + mail.id + "/retry", { method: "POST" });
    notify("邮件已重新排队。");
    await load();
  } catch (e) {
    notify(e.message, "error");
  }
}
function readMail(mail) {
  selectedMail.value = mail;
  mailDialog.value.showModal();
}
onMounted(load);
</script>
<template>
  <section class="page-intro admin-intro">
    <div>
      <span class="kicker">BEHIND THE EVERYDAY JOY</span>
      <h1>拾光工作台</h1>
      <p>照顾每一件小物，也照顾每一份喜欢。</p>
    </div>
    <button class="secondary" @click="load">刷新数据 ↻</button>
  </section>
  <div class="admin-tabs">
    <button
      v-for="item in [
        { id: 'overview', name: '经营概览' },
        { id: 'products', name: '商品目录' },
        { id: 'orders', name: '订单管理' },
        { id: 'mail', name: '发货邮件' },
      ]"
      :key="item.id"
      :class="{ active: tab === item.id }"
      @click="selectTab(item.id)"
    >
      {{ item.name }}
    </button>
  </div>
  <div v-if="loading" class="state">正在整理店铺数据…</div>
  <div v-else-if="error" class="state" role="alert">
    <p>{{ error }}</p>
    <button @click="load">重新加载</button>
  </div>
  <section v-else-if="tab === 'overview' && stats" class="dashboard">
    <div class="metric-grid">
      <article>
        <span>已付款销售额</span
        ><strong>¥{{ money(stats.summary.revenue) }}</strong
        ><small>待发货、已发货及已完成订单</small>
      </article>
      <article>
        <span>全部订单</span><strong>{{ stats.summary.totalOrders }}</strong
        ><small>含待付款与已取消</small>
      </article>
      <article>
        <span>待发货订单</span
        ><strong>{{ stats.summary.pendingShipment }}</strong
        ><small>及时回应每一份喜欢</small>
      </article>
      <article>
        <span>在售商品 / 顾客</span
        ><strong
          >{{ stats.activeProducts }} <em>/ {{ stats.customers }}</em></strong
        ><small>当前商品目录与顾客数</small>
      </article>
    </div>
    <div class="dashboard-columns">
      <article class="chart-panel">
        <div class="panel-title">
          <h2>近14天销售趋势</h2>
          <span>按付款日期统计</span>
        </div>
        <div class="bar-chart" role="img" aria-label="最近14天每天销售额柱状图">
          <div
            v-for="point in trend"
            :key="point.day"
            class="bar-column"
            :title="point.day + ' 销售额 ¥' + money(point.value)"
          >
            <div class="bar-track">
              <div class="bar" :style="{ height: point.height + '%' }">
                <span v-if="point.value">{{ money(point.value) }}</span>
              </div>
            </div>
            <small>{{ point.day }}</small>
          </div>
        </div>
        <p v-if="!stats.trend.length" class="chart-empty">
          还没有已付款订单，完成一笔演示购买后即可看到趋势。
        </p>
        <div v-if="stats.statusDistribution.length" class="state-distribution">
          <h3>订单流转</h3>
          <div
            class="segmented-status"
            role="img"
            aria-label="当前订单状态数量分布"
          >
            <span
              v-for="item in stats.statusDistribution"
              :key="item.status"
              :class="item.status"
              :title="statuses[item.status] + ' ' + item.count + '笔'"
              :style="{
                width:
                  (Number(item.count) /
                    Math.max(1, Number(stats.summary.totalOrders))) *
                    100 +
                  '%',
              }"
            ></span>
          </div>
          <div class="distribution-legend">
            <span v-for="item in stats.statusDistribution" :key="item.status"
              ><i :class="item.status"></i>{{ statuses[item.status] }}
              <strong>{{ item.count }}</strong></span
            >
          </div>
        </div>
      </article>
      <article class="chart-panel">
        <div class="panel-title">
          <h2>畅销选物</h2>
          <span>按销售额排序</span>
        </div>
        <div v-if="!stats.topProducts.length" class="mini-empty">
          第一件畅销小物，等你发现。
        </div>
        <div
          v-for="(item, index) in stats.topProducts"
          :key="item.product_id"
          class="top-product"
        >
          <span class="rank">{{ String(index + 1).padStart(2, "0") }}</span>
          <div>
            <strong>{{ item.product_name }}</strong
            ><small>{{ item.units }} 件</small>
          </div>
          <span>¥{{ money(item.revenue) }}</span>
        </div>
      </article>
    </div>
    <div class="dashboard-note">
      统计包含预置演示订单与本次体验订单；价格与付款均为课程演示，不代表真实经营收入。
    </div>
  </section>
  <section v-else-if="tab === 'products'">
    <div class="admin-toolbar">
      <form
        class="search"
        @submit.prevent="
          page = 1;
          load();
        "
      >
        <label class="sr-only" for="admin-search">搜索商品目录</label
        ><input
          id="admin-search"
          v-model="keyword"
          placeholder="按商品名称搜索"
          maxlength="100"
        /><button>搜索 ↗</button>
      </form>
      <span class="muted">共 {{ total }} 件商品</span
      ><button class="primary" @click="edit(null)">添加商品 +</button>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>商品</th>
            <th>分类</th>
            <th>价格</th>
            <th>库存</th>
            <th>状态</th>
            <th>管理</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="product in products" :key="product.id">
            <td>
              <div class="table-product">
                <img
                  :src="product.image_url"
                  :alt="product.name"
                  @error="
                    (event) => (event.target.src = '/images/book-fallback.svg')
                  "
                />
                <div>
                  <strong>{{ product.name }}</strong
                  ><small>ID {{ product.id }}</small>
                </div>
              </div>
            </td>
            <td>{{ product.category }}</td>
            <td>¥{{ money(product.price) }}</td>
            <td>{{ product.stock }}</td>
            <td>
              <span
                class="status-badge"
                :class="product.active ? 'PAID' : 'CANCELLED'"
                >{{ product.active ? "在售" : "已下架" }}</span
              >
            </td>
            <td>
              <div class="button-row">
                <button class="text-button" @click="edit(product)">编辑</button
                ><button
                  v-if="product.active"
                  class="text-button danger"
                  :disabled="busy"
                  @click="remove(product)"
                >
                  下架
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!products.length" class="mini-empty">没有找到对应商品。</p>
    </div>
  </section>
  <section v-else-if="tab === 'orders'">
    <div class="admin-toolbar">
      <label class="select-label"
        >订单状态<select
          v-model="orderStatus"
          @change="
            page = 1;
            load();
          "
        >
          <option value="">全部状态</option>
          <option v-for="(name, key) in statuses" :key="key" :value="key">
            {{ name }}
          </option>
        </select></label
      ><span class="muted">共 {{ total }} 笔订单</span>
    </div>
    <div class="order-list">
      <article v-for="order in orders" :key="order.id" class="order-card">
        <div class="order-top">
          <div>
            <strong>{{ order.orderNumber }}</strong
            ><span v-if="order.sample" class="sample-badge">预置演示</span
            ><span class="muted"
              >{{ time(order.createdAt) }} · {{ order.recipientName }}</span
            >
          </div>
          <span class="status-badge" :class="order.status">{{
            statuses[order.status]
          }}</span>
        </div>
        <div class="admin-order-items">
          <div v-for="item in order.items" :key="item.productId">
            <img
              :src="item.imageUrl"
              :alt="item.productName"
              @error="
                (event) => (event.target.src = '/images/book-fallback.svg')
              "
            /><span>{{ item.productName }} × {{ item.quantity }}</span>
          </div>
        </div>
        <div class="order-delivery">
          <p>{{ order.recipientPhone }} · {{ order.shippingAddress }}</p>
          <p>{{ order.recipientEmail }}</p>
          <p v-if="order.trackingNumber">演示物流 {{ order.trackingNumber }}</p>
        </div>
        <div class="order-bottom">
          <strong>¥{{ money(order.totalAmount) }}</strong
          ><button
            v-if="order.status === 'PAID'"
            class="primary"
            :disabled="busy"
            @click="askShip(order)"
          >
            发货并通知 ↗</button
          ><span v-else class="muted">{{
            order.status === "PENDING_PAYMENT"
              ? "等待顾客完成演示付款"
              : "当前无需发货操作"
          }}</span>
        </div>
      </article>
      <div v-if="!orders.length" class="empty-card">
        <h2>当前没有这个状态的订单。</h2>
      </div>
    </div>
  </section>
  <section v-else-if="tab === 'mail'">
    <div class="admin-toolbar">
      <p class="muted">
        本地测试邮箱只截取邮件，不会发送至真实外部收件人。正式 SMTP
        送达会显示“已发送”。
      </p>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>邮件与订单</th>
            <th>收件人</th>
            <th>发送状态</th>
            <th>时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="mail in mails" :key="mail.id">
            <td>
              <strong>{{ mail.subject }}</strong
              ><small class="block muted">{{ mail.order_number }}</small>
            </td>
            <td>{{ mail.recipient }}</td>
            <td>
              <span class="status-badge" :class="mail.status">{{
                {
                  CAPTURED: "本地邮箱已收取",
                  SENT: "SMTP已发送",
                  PENDING: "待发送",
                  SENDING: "发送中",
                  FAILED: "发送失败",
                }[mail.status]
              }}</span
              ><small v-if="mail.last_error" class="block danger">{{
                mail.last_error
              }}</small>
            </td>
            <td>{{ time(mail.sent_at || mail.created_at) }}</td>
            <td>
              <div class="button-row">
                <button class="text-button" @click="readMail(mail)">查看</button
                ><button
                  v-if="mail.status === 'FAILED'"
                  class="text-button"
                  @click="retry(mail)"
                >
                  重试
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!mails.length" class="mini-empty">
        发出第一笔订单后，这里会出现确认邮件。
      </p>
    </div>
  </section>
  <div
    v-if="['products', 'orders'].includes(tab) && pages > 1 && !loading"
    class="pagination"
  >
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
  <dialog ref="editor" class="editor-dialog" aria-labelledby="edit-title">
    <div class="dialog-top">
      <h2 id="edit-title">{{ editingId ? "编辑商品" : "添加一件新选物" }}</h2>
      <button
        class="close"
        aria-label="关闭商品编辑"
        :disabled="busy"
        @click="editor.close()"
      >
        ×
      </button>
    </div>
    <form class="editor-form" @submit.prevent="save">
      <label
        >商品名称<input v-model="form.name" required maxlength="100"
      /></label>
      <div class="form-columns">
        <label
          >分类<input v-model="form.category" required maxlength="40" /></label
        ><label
          >售价<input
            v-model.number="form.price"
            required
            type="number"
            min="0.01"
            max="999999.99"
            step="0.01" /></label
        ><label
          >可售库存<input
            v-model.number="form.stock"
            required
            type="number"
            min="0"
            max="1000000"
            step="1"
        /></label>
      </div>
      <label
        >商品图片地址<input
          v-model="form.imageUrl"
          required
          maxlength="255"
          placeholder="/images/notebook.svg"
      /></label>
      <div class="image-presets">
        <button
          v-for="name in ['notebook', 'bag', 'bookmark', 'book-fallback']"
          :key="name"
          type="button"
          @click="form.imageUrl = '/images/' + name + '.svg'"
        >
          <img :src="'/images/' + name + '.svg'" :alt="name + '预设图片'" />
        </button>
      </div>
      <label>一句话介绍<input v-model="form.tagline" maxlength="100" /></label
      ><label
        >商品详情<textarea
          v-model="form.description"
          rows="4"
          maxlength="2000"
        ></textarea></label
      ><label class="checkbox-label"
        ><input v-model="form.active" type="checkbox" /> 在店铺展示此商品</label
      ><button class="primary wide" :disabled="busy">
        {{ busy ? "正在保存…" : "保存商品资料" }}
      </button>
    </form>
  </dialog>
  <dialog
    ref="shippingDialog"
    class="confirm-dialog"
    aria-labelledby="ship-title"
  >
    <h2 id="ship-title">发货与通知</h2>
    <p class="muted">确认后订单进入已发货状态，并创建一封发货确认邮件。</p>
    <form @submit.prevent="ship">
      <label
        >演示物流单号<input
          v-model="shipping.trackingNumber"
          required
          maxlength="100"
      /></label>
      <div class="button-row">
        <button
          type="button"
          class="secondary"
          :disabled="busy"
          @click="shippingDialog.close()"
        >
          返回</button
        ><button class="primary" :disabled="busy">
          {{ busy ? "正在处理…" : "确认发货并通知" }}
        </button>
      </div>
    </form>
  </dialog>
  <dialog ref="mailDialog" class="mail-dialog" aria-labelledby="mail-title">
    <div class="dialog-top">
      <h2 id="mail-title">发货确认邮件</h2>
      <button class="close" aria-label="关闭邮件" @click="mailDialog.close()">
        ×
      </button>
    </div>
    <div v-if="selectedMail" class="mail-content">
      <p><span class="muted">收件人</span> {{ selectedMail.recipient }}</p>
      <p>
        <strong>{{ selectedMail.subject }}</strong>
      </p>
      <pre>{{ selectedMail.body }}</pre>
      <p class="demo-notice">
        {{
          selectedMail.status === "CAPTURED"
            ? "该邮件已通过SMTP到达本地测试邮箱，未投递至外部邮箱。"
            : "发送状态以管理列表为准。"
        }}
      </p>
    </div>
  </dialog>
</template>
