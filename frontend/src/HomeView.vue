<!-- AI-assisted implementation with Codex. Product content is loaded from Spring Boot/MySQL. -->
<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { addToCart } from './lib/shop'
async function addSelected() { dialog.value.close(); await addToCart(selected.value.id) }
const items = ref([]), categories = ref([]), total = ref(0), page = ref(1)
const keyword = ref(''), category = ref(''), loading = ref(false), error = ref('')
const selected = ref(null), dialog = ref(null), detailError = ref('')
const size = 9, pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
let listRequest, detailRequest
async function readJson(url, signal) {
  const response = await fetch(url, { signal })
  if (!response.ok) throw new Error(response.status === 404 ? '商品已下架或不存在，请刷新列表。' : '暂时无法读取商品，请稍后重试。')
  return response.json()
}
async function loadProducts() {
  listRequest?.abort()
  const request = new AbortController(); listRequest = request
  loading.value = true; error.value = ''
  try {
    const query = new URLSearchParams({ keyword: keyword.value.trim(), category: category.value, page: page.value, size })
    const data = await readJson('/api/products?' + query, request.signal)
    items.value = data.items; total.value = data.total
  } catch (e) { if (e.name !== 'AbortError') { error.value = e.message; items.value = []; total.value = 0 } }
  finally { if (listRequest === request) loading.value = false }
}
function search() { page.value = 1; loadProducts() }
function chooseCategory(value) { category.value = value; search() }
function changePage(value) { page.value = value; loadProducts() }
function reset() { keyword.value = ''; category.value = ''; search() }
async function showProduct(id) {
  detailRequest?.abort()
  const request = new AbortController(); detailRequest = request
  detailError.value = ''
  try {
    selected.value = await readJson('/api/products/' + id, request.signal)
    if (!dialog.value.open) dialog.value.showModal()
  } catch (e) { if (e.name !== 'AbortError') detailError.value = e.message }
}
onMounted(async () => {
  loadProducts()
  try { categories.value = await readJson('/api/categories') } catch { /* List remains searchable when category loading fails. */ }
})
onBeforeUnmount(() => { listRequest?.abort(); detailRequest?.abort() })
const money = value => Number(value).toFixed(2)
</script>

<template>
  <div class="home-page">
    <section class="hero" aria-labelledby="hero-title">
      <div><span class="kicker">A LITTLE JOY, EVERY DAY</span><h1 id="hero-title">把日常，<br>过成喜欢的样子。</h1><p>一页笔记，一只布袋，一枚书签。<br>从校园里的小物开始，收集平凡日子的灵感。</p><a class="browse" href="#collection">发现日常选物 <span>↓</span></a></div>
      <div class="hero-art"><span class="art-label">THE EVERYDAY EDIT</span><img src="/images/notebook.svg" alt="山野随行笔记本原创插画"><div class="art-caption"><span>01 / 山野随行</span><span>记录每一个小小灵感</span></div></div>
    </section>
    <section id="collection" aria-labelledby="collection-title">
      <div class="collection-heading"><div><span class="kicker">CURATED FOR YOUR CAMPUS DAYS</span><h2 id="collection-title">日常选物 <small>/ {{ String(total).padStart(2, '0') }}</small></h2></div><form class="search" @submit.prevent="search"><label class="sr-only" for="keyword">搜索商品</label><input id="keyword" v-model="keyword" maxlength="100" placeholder="找一本笔记，或一点灵感…" type="search"><button type="submit">搜索 <span aria-hidden="true">↗</span></button></form></div>
      <div class="collection-tools"><div class="filters" aria-label="商品分类"><button :aria-pressed="category === ''" @click="chooseCategory('')">全部</button><button v-for="item in categories" :key="item" :aria-pressed="category === item" @click="chooseCategory(item)">{{ item }}</button></div><span class="quiet">为学习，也为生活。</span></div>
      <div aria-live="polite" :aria-busy="loading">
        <p v-if="loading" class="state">正在寻找日常好物…</p>
        <div v-else-if="error" class="state" role="alert"><p>{{ error }}</p><button @click="loadProducts">重新加载</button></div>
        <div v-else-if="!items.length" class="state"><h3>暂时没有找到这件小物</h3><p>试试其他关键词，或者看看全部选物。</p><button @click="reset">查看全部商品</button></div>
        <div v-else class="grid"><article v-for="product in items" :key="product.id"><button class="picture" :aria-label="'查看' + product.name" @click="showProduct(product.id)"><img @error="event => event.target.src = '/images/book-fallback.svg'" :class="{ 'book-image': product.sourceType === 'OPEN_LIBRARY', 'catalog-image': product.sourceType === 'DUMMYJSON' }" :src="product.imageUrl" :alt="product.name + '商品图片'" loading="lazy"><span v-if="product.stock === 0" class="sold-out">暂时售罄</span></button><div class="meta"><span>{{ product.category }}</span><span>{{ product.tagline }}</span></div><div class="product-line"><h3>{{ product.name }}</h3><span class="price">¥{{ money(product.price) }}</span></div><button class="detail-link" @click="showProduct(product.id)">看看这件小物 <span aria-hidden="true">↗</span></button></article></div>
      </div>
      <p v-if="detailError" role="alert" class="inline-error">{{ detailError }}</p>
      <div v-if="pages > 1 && !loading && !error" class="pagination"><button :disabled="page <= 1" @click="changePage(page - 1)">上一页</button><span>{{ page }} / {{ pages }}</span><button :disabled="page >= pages" @click="changePage(page + 1)">下一页</button></div>
    </section>
    <section class="story"><span class="story-mark" aria-hidden="true">✳</span><div><h2>小物有用，日常有光。</h2><p>愿每一件陪伴你的小物，都能留下一点喜欢的理由。</p></div><span class="story-end">LESS, BUT LOVELY.</span></section>
  </div>

  <dialog ref="dialog" aria-labelledby="detail-title"><template v-if="selected"><div class="dialog-top"><span class="kicker">EVERYDAY FINDS</span><button class="close" aria-label="关闭商品详情" @click="dialog.close()">×</button></div><img @error="event => event.target.src = '/images/book-fallback.svg'" class="dialog-image" :src="selected.imageUrl" :alt="selected.name + '商品图片'"><div class="dialog-info"><span class="kicker">{{ selected.category }}</span><h2 id="detail-title">{{ selected.name }}</h2><p class="detail-price">¥{{ money(selected.price) }}</p><p>{{ selected.description }}</p><div class="stock">{{ selected.stock > 0 ? '现有库存 ' + selected.stock + ' 件' : '暂时售罄' }}</div><p v-if="selected.sourceType !== 'ORIGINAL'" class="source-note">{{ selected.sourceType === 'OPEN_LIBRARY' ? '真实书目资料 · Open Library' : '电商原型目录 · DummyJSON' }} <a :href="selected.sourceUrl" target="_blank" rel="noopener noreferrer">查看来源 ↗</a></p><p class="stage-note">人民币价格和库存为课程演示数据，不代表实际销售承诺。</p><button class="primary" :disabled="selected.stock === 0" @click="addSelected">{{ selected.stock > 0 ? '加入购物袋 +' : '暂时售罄' }}</button></div></template></dialog>
</template>
