<template>
  <el-container class="app-shell">
    <el-aside width="360px" class="planner-panel">
      <div class="brand">
        <span class="brand-mark">TP</span>
        <div>
          <h1>旅行计划规划</h1>
          <p>输入目的地，让 AI 自动生成旅行路线</p>
        </div>
      </div>

      <section class="hero-panel">
        <h2>输入你想去的地方</h2>
        <p>支持直接输入城市名，点击 AI 规划后会自动生成并展示到地图和每日行程里。</p>
      </section>

      <el-form label-position="top" class="planner-form">
        <el-form-item label="规划模式">
          <el-segmented
            v-model="planMode"
            :options="planModeOptions"
            class="full-width"
          />
        </el-form-item>

        <el-form-item label="目的地输入">
          <el-autocomplete
            v-model="form.city"
            class="full-width"
            :fetch-suggestions="queryDestinations"
            clearable
            placeholder="例如：Tokyo、Shanghai、Chengdu"
            @select="handleDestinationSelect"
          >
            <template #default="{ item }">
              <div class="destination-option">
                <span>{{ item.city }}</span>
                <small>{{ item.country }}</small>
              </div>
            </template>
          </el-autocomplete>
          <p class="field-hint">当前内置景点支持：{{ supportedCitiesText }}</p>
        </el-form-item>

        <el-form-item label="出发日期">
          <el-date-picker
            v-model="form.startDate"
            class="full-width"
            type="date"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>

        <el-form-item label="旅行天数">
          <el-slider v-model="form.days" :min="1" :max="7" show-input />
        </el-form-item>

        <el-form-item label="兴趣偏好">
          <el-checkbox-group v-model="form.interests">
            <el-checkbox-button v-for="interest in interests" :key="interest.value" :value="interest.value">
              {{ interest.label }}
            </el-checkbox-button>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <div class="action-group">
        <el-button
          type="primary"
          class="full-width"
          :loading="loading && planMode === 'standard'"
          @click="generateStandardPlan"
        >
          普通规划
        </el-button>
        <el-button
          type="success"
          class="full-width ai-button"
          :loading="loading && planMode === 'ai'"
          @click="generateAiPlan"
        >
          立即 AI 规划
        </el-button>
      </div>

      <section class="mode-summary">
        <div class="mode-chip" :class="`mode-chip-${planMode}`">
          {{ planMode === 'ai' ? '当前模式：AI 大模型规划' : '当前模式：普通规则规划' }}
        </div>
        <p>
          {{ planMode === 'ai'
            ? '会调用千问生成结构化行程，再由后端按景点库校验和补齐。'
            : '使用本地规则和景点库快速生成稳定路线。' }}
        </p>
        <p v-if="loading && planMode === 'ai'" class="loading-tip">
          AI 正在生成目的地景点和行程，首次返回可能需要 10-60 秒。
        </p>
      </section>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
      />

      <section v-if="plan" class="destination-summary">
        <h2>{{ plan.destination.city }}</h2>
        <p>{{ plan.destination.summary }}</p>
        <el-tag effect="plain">适合季节：{{ plan.destination.bestSeason }}</el-tag>
      </section>

      <section v-else class="empty-state">
        <h2>等待生成行程</h2>
        <p>输入支持的目的地后点击 AI 规划，右侧会展示地图、每日路线和候选景点。</p>
      </section>

      <section class="saved-plans">
        <div class="section-title compact">
          <h2>已保存行程</h2>
          <span>{{ savedPlans.length }} 条</span>
        </div>
        <button
          v-for="savedPlan in savedPlans"
          :key="savedPlan.planId"
          class="saved-plan"
          :class="{ active: plan?.planId === savedPlan.planId }"
          @click="loadSavedPlan(savedPlan.planId)"
        >
          <span>{{ savedPlan.city }} · {{ savedPlan.days }} 天</span>
          <small>{{ savedPlan.startDate }} · {{ savedPlan.stopCount }} 个景点</small>
        </button>
      </section>
    </el-aside>

    <el-container>
      <el-main class="main-content">
        <section class="map-section">
          <div id="map" class="map"></div>
        </section>

        <section class="content-grid">
          <div class="itinerary">
            <div class="section-title">
              <h2>每日行程</h2>
              <span v-if="plan">
                {{ plan.startDate }} 起 · {{ plan.days }} 天 ·
                {{ planMode === 'ai' ? 'AI 结果' : '普通结果' }}
              </span>
            </div>

            <el-timeline v-if="plan">
              <el-timeline-item
                v-for="day in plan.itinerary"
                :key="day.day"
                :timestamp="`第 ${day.day} 天`"
                placement="top"
              >
                <el-card shadow="never" class="day-card">
                  <div class="day-card-head">
                    <div>
                      <h3>{{ day.title }}</h3>
                      <p>{{ day.theme }} · 约 {{ day.distanceKm }} km</p>
                    </div>
                    <el-tag>{{ day.attractions.length }} 个景点</el-tag>
                  </div>
                  <div class="route-list">
                    <button
                      v-for="attraction in day.attractions"
                      :key="`${day.day}-${attraction.id}`"
                      class="route-stop"
                      @click="focusAttraction(attraction)"
                    >
                      <span>{{ attraction.name }}</span>
                      <small>{{ displayCategory(attraction.category) }} · {{ attraction.suggestedHours }}小时</small>
                    </button>
                  </div>
                </el-card>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="还没有可展示的行程结果" />
          </div>

          <div class="attractions">
            <div class="section-title">
              <h2>景点库</h2>
              <span>{{ attractions.length }} 个结果</span>
            </div>
            <el-card v-for="attraction in attractions" :key="attraction.id" shadow="hover" class="spot-card">
              <div class="spot-card-head">
                <h3>{{ attraction.name }}</h3>
                <el-rate :model-value="attraction.rating" disabled size="small" />
              </div>
              <p>{{ attraction.description }}</p>
              <p class="spot-meta">{{ displayCategory(attraction.category) }} · 建议游玩 {{ attraction.suggestedHours }} 小时</p>
              <div class="tag-row">
                <el-tag v-for="tag in attraction.tags" :key="tag" size="small" effect="plain">{{ displayTag(tag) }}</el-tag>
              </div>
            </el-card>
            <el-empty v-if="!attractions.length" description="当前没有可展示的景点数据" />
          </div>
        </section>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import L from 'leaflet'
import { createAiPlan, createPlan, getAttractions, getDestinations, getSavedPlan, getSavedPlans } from './api/travel'

const destinations = ref([])
const attractions = ref([])
const plan = ref(null)
const savedPlans = ref([])
const loading = ref(false)
const errorMessage = ref('')
const map = ref(null)
const markers = ref([])
const routeLine = ref(null)
const planMode = ref('ai')

const planModeOptions = [
  { label: 'AI规划', value: 'ai' },
  { label: '普通规划', value: 'standard' }
]

const interests = [
  { label: '文化', value: 'culture' },
  { label: '美食', value: 'food' },
  { label: '历史', value: 'history' },
  { label: '自然', value: 'nature' },
  { label: '漫步', value: 'walk' },
  { label: '观景', value: 'view' }
]

const form = reactive({
  city: '',
  startDate: new Date().toISOString().slice(0, 10),
  days: 3,
  interests: ['culture', 'walk']
})

const supportedCitiesText = ref('')

const categoryLabels = {
  culture: '文化',
  food: '美食',
  history: '历史',
  nature: '自然',
  walk: '漫步',
  view: '观景',
  citywalk: '漫步',
  nightview: '夜景',
  photography: '拍照',
  garden: '园林',
  museum: '博物馆',
  shopping: '购物',
  landmark: '地标',
  cityscape: '城市风光',
  family: '亲子',
  nightlife: '夜生活',
  park: '公园',
  wildlife: '自然生态',
  animal: '动物',
  viewpoint: '观景台',
  sight: '景点',
  'ai-generated': 'AI生成',
  'ai生成': 'AI生成'
}

const displayCategory = (category) => categoryLabels[category] || category || '综合'

const displayTag = (tag) => categoryLabels[tag] || tag

const uniqueAttractionsFromPlan = (newPlan) => {
  const attractionMap = new Map()
  newPlan.itinerary
    .flatMap((day) => day.attractions)
    .forEach((attraction) => {
      attractionMap.set(attraction.id, attraction)
    })
  return Array.from(attractionMap.values())
}

const initMap = () => {
  map.value = L.map('map', { zoomControl: false }).setView([31.2304, 121.4737], 12)
  L.control.zoom({ position: 'bottomright' }).addTo(map.value)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map.value)
}

const renderMap = () => {
  if (!map.value || !plan.value) return

  markers.value.forEach((marker) => marker.remove())
  markers.value = []
  if (routeLine.value) {
    routeLine.value.remove()
    routeLine.value = null
  }

  const points = plan.value.itinerary.flatMap((day) => day.attractions)
  const latLngs = points.map((item) => [item.latitude, item.longitude])

  points.forEach((attraction) => {
    const marker = L.marker([attraction.latitude, attraction.longitude])
      .addTo(map.value)
      .bindPopup(`<strong>${attraction.name}</strong><br>${displayCategory(attraction.category)}`)
    markers.value.push(marker)
  })

  if (latLngs.length > 1) {
    routeLine.value = L.polyline(latLngs, {
      color: '#e74c3c',
      weight: 4,
      opacity: 0.85
    }).addTo(map.value)
    map.value.fitBounds(routeLine.value.getBounds(), { padding: [36, 36] })
  } else if (latLngs.length === 1) {
    map.value.setView(latLngs[0], 13)
  } else {
    map.value.setView([plan.value.destination.latitude, plan.value.destination.longitude], 12)
  }
}

const queryDestinations = (queryString, callback) => {
  const keyword = queryString.trim().toLowerCase()
  const results = keyword
    ? destinations.value.filter((destination) =>
        destination.city.toLowerCase().includes(keyword) ||
        destination.country.toLowerCase().includes(keyword)
      )
    : destinations.value

  callback(results.map((destination) => ({
    value: destination.city,
    city: destination.city,
    country: destination.country
  })))
}

const handleDestinationSelect = (item) => {
  form.city = item.value
}

const runPlanRequest = async (mode = planMode.value) => {
  if (!form.city?.trim()) {
    errorMessage.value = '请先输入目的地。'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const payload = {
      city: form.city.trim(),
      startDate: form.startDate,
      days: form.days,
      interests: form.interests
    }
    const newPlan = mode === 'ai' ? await createAiPlan(payload) : await createPlan(payload)
    plan.value = newPlan
    attractions.value = mode === 'ai'
      ? uniqueAttractionsFromPlan(newPlan)
      : await getAttractions({ city: form.city, interests: form.interests })
    savedPlans.value = await getSavedPlans()
    planMode.value = mode
  } catch (error) {
    plan.value = null
    attractions.value = []
    errorMessage.value = error?.response?.data?.message || error?.message || '生成行程失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

const refreshPlan = async () => runPlanRequest(planMode.value)

const generateStandardPlan = async () => runPlanRequest('standard')

const generateAiPlan = async () => runPlanRequest('ai')

const loadSavedPlan = async (id) => {
  loading.value = true
  errorMessage.value = ''
  try {
    const savedPlan = await getSavedPlan(id)
    plan.value = savedPlan
    form.city = savedPlan.destination.city
    form.startDate = savedPlan.startDate
    form.days = savedPlan.days
    attractions.value = uniqueAttractionsFromPlan(savedPlan)
  } catch (error) {
    plan.value = null
    attractions.value = []
    errorMessage.value = error?.response?.data?.message || error?.message || '读取已保存行程失败。'
  } finally {
    loading.value = false
  }
}

const focusAttraction = (attraction) => {
  if (!map.value) return
  map.value.setView([attraction.latitude, attraction.longitude], 15, { animate: true })
  const marker = markers.value.find((item) => {
    const position = item.getLatLng()
    return position.lat === attraction.latitude && position.lng === attraction.longitude
  })
  marker?.openPopup()
}

onMounted(async () => {
  await nextTick()
  initMap()
  destinations.value = await getDestinations()
  supportedCitiesText.value = destinations.value.map((destination) => destination.city).join('、')
  form.city = destinations.value[0]?.city || ''
  await generateAiPlan()
  savedPlans.value = await getSavedPlans()
})

watch(plan, renderMap)
</script>
