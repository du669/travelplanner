<template>
  <div class="editor-page">
    <header class="editor-hero">
      <div class="editor-hero-copy">
        <span class="editor-kicker">行程编辑页</span>
        <h1>{{ cityName }} 行程编辑</h1>
        <p>左侧是实时地图，右侧按天展开编辑。你修改景点顺序、跨天移动或新增景点后，路线预览会自动刷新。</p>
      </div>

      <div class="editor-hero-actions">
        <a class="ghost-link" href="/">返回主页</a>
        <el-button plain @click="exportPlanAsImage('jpg')" :disabled="!plan || exportLoading">导出 JPG</el-button>
        <el-button plain @click="exportPlanAsPdf" :disabled="!plan || exportLoading">导出 PDF</el-button>
      </div>
    </header>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="editor-alert"
    />

    <section v-if="plan" class="editor-layout">
      <aside class="editor-map-column">
        <section class="map-panel">
          <div class="map-panel-head">
            <div class="map-panel-title">
              <span>实时地图</span>
              <strong>{{ currentViewLabel }}</strong>
            </div>

            <div class="day-chip-row">
              <button class="day-chip" :class="{ active: activeDayFilter === 'all' }" @click="setActiveDay('all')">
                全部天数
              </button>
              <button
                v-for="day in plan.itinerary"
                :key="`filter-${day.day}`"
                class="day-chip"
                :class="{ active: activeDayFilter === day.day }"
                @click="setActiveDay(day.day)"
              >
                第 {{ day.day }} 天
              </button>
            </div>
          </div>

          <div class="map-canvas-shell">
            <div ref="mapContainerRef" class="map-canvas"></div>
            <div v-if="isPreviewComputing" class="map-loading-mask">
              <div class="map-loading-card">
                <strong>AI 正在辅助计算</strong>
                <span>{{ computingMessage }}</span>
              </div>
            </div>
          </div>

          <div class="map-stats">
            <article class="map-stat accent">
              <small>目的地</small>
              <strong>{{ cityName }}</strong>
            </article>
            <article class="map-stat">
              <small>总天数</small>
              <strong>{{ plan.days }} 天</strong>
            </article>
            <article class="map-stat">
              <small>景点数量</small>
              <strong>{{ totalStops }} 个</strong>
            </article>
            <article class="map-stat">
              <small>路线摘要</small>
              <strong>{{ routeSummary }}</strong>
            </article>
          </div>
        </section>
      </aside>

      <section class="editor-side-column">
        <section class="optimizer-panel">
          <div class="optimizer-head">
            <div>
              <small>进一步优化</small>
              <h2>AI 优化当前草稿</h2>
            </div>
            <div class="optimizer-actions">
              <el-button type="primary" :loading="saving" @click="savePlanEdits">保存编辑</el-button>
              <el-button type="success" :loading="optimizing" @click="optimizeCurrentPlan">AI 优化</el-button>
            </div>
          </div>

          <el-input
            v-model="optimizationInstruction"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="例如：把第 2 天节奏放慢一些，减少折返，并把夜景集中到最后一天。"
          />
        </section>

        <section class="day-stack">
          <article
            v-for="day in plan.itinerary"
            :key="`day-card-${day.day}`"
            class="day-card"
            :class="{ active: activeEditorDay === day.day }"
          >
            <button class="day-card-head" @click="activateEditorDay(day.day)">
              <div class="day-card-title">
                <span class="day-card-badge">第 {{ day.day }} 天</span>
                <strong>{{ day.title }}</strong>
                <small>{{ day.theme || '继续细化当天节奏与路线。' }}</small>
              </div>
              <div class="day-card-meta">
                <em>{{ day.attractions.length }} 个景点</em>
                <span>{{ activeEditorDay === day.day ? '收起' : '展开' }}</span>
              </div>
            </button>

            <div v-if="activeEditorDay === day.day" class="day-card-body">
              <div class="day-card-summary">
                <span>{{ getRouteSummaryForDay(day.day) }}</span>
                <span>{{ getPlayHours(day) }}</span>
              </div>

              <div class="stop-list">
                <article
                  v-for="(attraction, attractionIndex) in day.attractions"
                  :key="`${day.day}-${attraction.id ?? attractionIndex}`"
                  class="stop-card"
                >
                  <div class="stop-card-copy">
                    <strong>{{ attractionIndex + 1 }}. {{ attraction.name }}</strong>
                    <span>{{ displayCategory(attraction.category) }} · 建议 {{ attraction.suggestedHours || 2 }} 小时</span>
                  </div>

                  <div class="stop-card-actions">
                    <button class="plain-pill" @click="moveAttraction(day.day, attractionIndex, 'up')">上移</button>
                    <button class="plain-pill" @click="moveAttraction(day.day, attractionIndex, 'down')">下移</button>
                    <button class="plain-pill" @click="moveAttraction(day.day, attractionIndex, 'prev-day')">前一天</button>
                    <button class="plain-pill" @click="moveAttraction(day.day, attractionIndex, 'next-day')">后一天</button>
                    <button class="plain-pill danger" @click="removeAttraction(day.day, attractionIndex)">删除</button>
                  </div>
                </article>
              </div>

              <div class="stop-add-row">
                <el-input
                  v-model="newAttractionInputs[day.day]"
                  placeholder="输入新景点名，例如：天坛、国家博物馆"
                  @keyup.enter="addAttractionToDay(day.day)"
                />
                <el-button type="primary" plain @click="addAttractionToDay(day.day)">添加景点</el-button>
              </div>
            </div>
          </article>
        </section>
      </section>
    </section>

    <el-empty v-else-if="!loading" description="没有可编辑的行程，请先从主页生成并保存一个行程。" />

    <div class="export-stage" aria-hidden="true">
      <div ref="exportSurfaceRef" class="export-surface" v-if="plan">
        <header class="export-cover">
          <small>Travel Planner Export</small>
          <h1>{{ cityName }} {{ plan.days }} 天编辑版行程</h1>
          <p>{{ plan.startDate }} 出发 · 共 {{ totalStops }} 个景点</p>
        </header>

        <section class="export-day-stack">
          <article v-for="day in plan.itinerary" :key="`export-${day.day}`" class="export-day-card">
            <div class="export-day-head">
              <div>
                <small>第 {{ day.day }} 天</small>
                <h3>{{ day.title }}</h3>
                <p>{{ day.theme }}</p>
              </div>
            </div>
            <div class="export-stop-list">
              <div
                v-for="(attraction, attractionIndex) in day.attractions"
                :key="`export-stop-${day.day}-${attractionIndex}`"
                class="export-stop-item"
              >
                <strong>{{ attractionIndex + 1 }}. {{ attraction.name }}</strong>
                <span>{{ displayCategory(attraction.category) }} · 建议 {{ attraction.suggestedHours || 2 }} 小时</span>
              </div>
            </div>
          </article>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import html2canvas from 'html2canvas'
import jsPDF from 'jspdf'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { applyPlanEdits, getPlanRoutes, getSavedPlan, optimizePlan, previewPlanRoutes } from './api/travel'

const EDIT_DRAFT_STORAGE_KEY = 'travelplanner-edit-draft'

const plan = ref(null)
const routeData = ref(null)
const errorMessage = ref('')
const loading = ref(false)
const saving = ref(false)
const optimizing = ref(false)
const exportLoading = ref(false)
const optimizationInstruction = ref('')
const exportSurfaceRef = ref(null)
const mapContainerRef = ref(null)
const newAttractionInputs = ref({})
const activeDayFilter = ref('all')
const activeEditorDay = ref(1)
const isPreviewComputing = ref(false)
const computingMessage = ref('正在根据你当前的修改刷新路线和地图。')

let mapInstance = null
let markerLayer = null
let lineLayer = null
let previewTimer = null
let previewRequestVersion = 0

const categoryLabels = {
  culture: '文化',
  food: '美食',
  history: '历史',
  nature: '自然',
  walk: '漫步',
  view: '观景',
  architecture: '建筑',
  art: '艺术',
  citywalk: '城市漫步',
  nightview: '夜景',
  photography: '摄影',
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
  手填: '手填'
}

const dayPalette = ['#d95f43', '#17836c', '#2d6ea4', '#f29f59', '#7b5ac7', '#d84f6f', '#1a88a5']

const cloneValue = (value) => JSON.parse(JSON.stringify(value))
const displayCategory = (category) => categoryLabels[category] || category || '综合'

const cityName = computed(() => plan.value?.destination?.city || '当前行程')
const totalStops = computed(() => (plan.value?.itinerary || []).reduce((sum, day) => sum + (day.attractions?.length || 0), 0))
const currentViewLabel = computed(() => (activeDayFilter.value === 'all' ? '全部天数' : `第 ${activeDayFilter.value} 天`))

const routeSummary = computed(() => {
  const days = routeData.value?.days || []
  if (!days.length) return '待估算'
  const distanceMeters = days.reduce((sum, day) => sum + Number(day.distanceMeters || 0), 0)
  const durationSeconds = days.reduce((sum, day) => sum + Number(day.durationSeconds || 0), 0)
  return `${formatDistance(distanceMeters)} · ${formatDuration(durationSeconds)}`
})

const activeDays = computed(() => {
  if (!plan.value?.itinerary?.length) return []
  if (activeDayFilter.value === 'all') return plan.value.itinerary
  return plan.value.itinerary.filter((day) => day.day === activeDayFilter.value)
})

const normalizeRequestError = (error) => error?.response?.data?.message || error?.message || '操作失败，请稍后重试。'

const formatDistance = (distanceMeters = 0) =>
  distanceMeters >= 1000 ? `${(distanceMeters / 1000).toFixed(1)} 公里` : `${Math.round(distanceMeters)} 米`

const formatDuration = (durationSeconds = 0) => {
  const safeSeconds = Math.max(0, Math.round(durationSeconds))
  const hours = Math.floor(safeSeconds / 3600)
  const minutes = Math.max(1, Math.round((safeSeconds % 3600) / 60))
  if (hours > 0) return `${hours} 小时 ${minutes} 分钟`
  return `${minutes} 分钟`
}

const getPlayHours = (day) =>
  `建议游玩 ${(day?.attractions || []).reduce((sum, attraction) => sum + Number(attraction.suggestedHours || 0), 0)} 小时`

const buildEditPayload = () => ({
  planId: plan.value?.planId || null,
  destination: plan.value?.destination || null,
  startDate: plan.value?.startDate || null,
  days: plan.value?.days || 1,
  itinerary: (plan.value?.itinerary || []).map((day) => ({
    day: day.day,
    title: day.title,
    theme: day.theme,
    distanceKm: day.distanceKm || 0,
    attractions: (day.attractions || []).map((attraction, attractionIndex) => ({
      ...attraction,
      id: attraction.id ?? `editor-${day.day}-${attractionIndex}-${Date.now()}`
    }))
  }))
})

const persistDraft = () => {
  if (!plan.value?.planId) return
  window.sessionStorage.setItem(EDIT_DRAFT_STORAGE_KEY, JSON.stringify(buildEditPayload()))
}

const hasValidCoordinates = (item) => {
  const lng = Number(item?.longitude)
  const lat = Number(item?.latitude)
  return Number.isFinite(lng) && Number.isFinite(lat) && Math.abs(lng) <= 180 && Math.abs(lat) <= 90 && !(Math.abs(lng) < 0.001 && Math.abs(lat) < 0.001)
}

const distanceBetweenKm = (pointA, pointB) => {
  if (!hasValidCoordinates(pointA) || !hasValidCoordinates(pointB)) return Number.POSITIVE_INFINITY
  const toRadians = (degrees) => (degrees * Math.PI) / 180
  const earthRadiusKm = 6371
  const dLat = toRadians(Number(pointB.latitude) - Number(pointA.latitude))
  const dLng = toRadians(Number(pointB.longitude) - Number(pointA.longitude))
  const lat1 = toRadians(Number(pointA.latitude))
  const lat2 = toRadians(Number(pointB.latitude))
  const haversine = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2
  return earthRadiusKm * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
}

const isNearPlanCenter = (point, center) => {
  if (!hasValidCoordinates(point) || !hasValidCoordinates(center)) return false
  return distanceBetweenKm(point, center) <= 180
}

const getCenterFromPlanContent = (planLike) => {
  const points = (planLike?.itinerary || [])
    .flatMap((day) => day.attractions || [])
    .filter(hasValidCoordinates)
    .map((item) => ({ longitude: Number(item.longitude), latitude: Number(item.latitude) }))
  if (!points.length) return null
  return {
    longitude: points.reduce((sum, point) => sum + point.longitude, 0) / points.length,
    latitude: points.reduce((sum, point) => sum + point.latitude, 0) / points.length
  }
}

const getPlanCenter = (planLike) => {
  if (hasValidCoordinates(planLike?.destination)) {
    return {
      longitude: Number(planLike.destination.longitude),
      latitude: Number(planLike.destination.latitude)
    }
  }
  return getCenterFromPlanContent(planLike)
}

const buildFallbackRoutes = (preview) => {
  if (!preview?.itinerary?.length) return null
  return {
    planId: preview.planId || -1,
    days: preview.itinerary.map((day) => {
      const validAttractions = (day.attractions || []).filter(hasValidCoordinates)
      const polyline = validAttractions.map((attraction) => ({ longitude: attraction.longitude, latitude: attraction.latitude }))
      const legs = validAttractions.slice(0, -1).map((attraction, index) => {
        const nextAttraction = validAttractions[index + 1]
        const distanceMeters = Math.round(distanceBetweenKm(attraction, nextAttraction) * 1000)
        const durationSeconds = Math.max(600, Math.round(distanceMeters / 1.15))
        return {
          fromIndex: index,
          toIndex: index + 1,
          fromName: attraction.name,
          toName: nextAttraction.name,
          mode: '预估路线',
          lineName: '',
          subway: false,
          distanceMeters,
          durationSeconds,
          polyline: [
            { longitude: attraction.longitude, latitude: attraction.latitude },
            { longitude: nextAttraction.longitude, latitude: nextAttraction.latitude }
          ],
          steps: []
        }
      })
      return {
        day: day.day,
        distanceMeters: legs.reduce((sum, leg) => sum + Number(leg.distanceMeters || 0), 0),
        durationSeconds: legs.reduce((sum, leg) => sum + Number(leg.durationSeconds || 0), 0),
        polyline,
        legs
      }
    })
  }
}

const queuePreviewRefresh = () => {
  if (!plan.value) return
  window.clearTimeout(previewTimer)
  const requestVersion = ++previewRequestVersion
  isPreviewComputing.value = true
  computingMessage.value = 'AI 正在辅助计算当前草稿的路线与地图。'
  previewTimer = window.setTimeout(async () => {
    try {
      const preview = await previewPlanRoutes(buildEditPayload())
      if (requestVersion !== previewRequestVersion) return
      routeData.value = preview
    } catch (error) {
      if (requestVersion !== previewRequestVersion) return
      routeData.value = buildFallbackRoutes(plan.value)
    } finally {
      if (requestVersion === previewRequestVersion) {
        isPreviewComputing.value = false
      }
    }
  }, 250)
}

const getEditableDay = (dayNumber) => plan.value?.itinerary?.find((day) => day.day === dayNumber)

const removeAttraction = (dayNumber, attractionIndex) => {
  const day = getEditableDay(dayNumber)
  if (!day) return
  day.attractions.splice(attractionIndex, 1)
  persistDraft()
  queuePreviewRefresh()
}

const moveAttraction = (dayNumber, attractionIndex, direction) => {
  const day = getEditableDay(dayNumber)
  if (!day) return

  if (direction === 'up' && attractionIndex > 0) {
    ;[day.attractions[attractionIndex - 1], day.attractions[attractionIndex]] = [day.attractions[attractionIndex], day.attractions[attractionIndex - 1]]
  } else if (direction === 'down' && attractionIndex < day.attractions.length - 1) {
    ;[day.attractions[attractionIndex + 1], day.attractions[attractionIndex]] = [day.attractions[attractionIndex], day.attractions[attractionIndex + 1]]
  } else if (direction === 'prev-day' && dayNumber > 1) {
    const [moved] = day.attractions.splice(attractionIndex, 1)
    getEditableDay(dayNumber - 1)?.attractions.push(moved)
  } else if (direction === 'next-day' && dayNumber < plan.value.itinerary.length) {
    const [moved] = day.attractions.splice(attractionIndex, 1)
    getEditableDay(dayNumber + 1)?.attractions.unshift(moved)
  }

  persistDraft()
  queuePreviewRefresh()
}

const addAttractionToDay = (dayNumber) => {
  const day = getEditableDay(dayNumber)
  const rawName = newAttractionInputs.value[dayNumber]?.trim()
  if (!day || !rawName) return

  day.attractions.push({
    id: `manual-${dayNumber}-${Date.now()}`,
    name: rawName,
    city: cityName.value,
    category: 'sight',
    description: '用户新增景点，待保存后由系统补全坐标和交通信息。',
    latitude: 0,
    longitude: 0,
    rating: 4.5,
    suggestedHours: 2,
    tags: ['手填'],
    imageUrls: []
  })

  newAttractionInputs.value = {
    ...newAttractionInputs.value,
    [dayNumber]: ''
  }
  persistDraft()
  queuePreviewRefresh()
}

const loadPlan = async (planId) => {
  loading.value = true
  errorMessage.value = ''
  try {
    const draftRaw = window.sessionStorage.getItem(EDIT_DRAFT_STORAGE_KEY)
    if (draftRaw) {
      const draft = JSON.parse(draftRaw)
      if (String(draft?.planId || '') === String(planId)) {
        plan.value = cloneValue(draft)
        activeEditorDay.value = draft.itinerary?.[0]?.day || 1
        activeDayFilter.value = draft.itinerary?.[0]?.day || 'all'
        routeData.value = buildFallbackRoutes(draft)
        queuePreviewRefresh()
        return
      }
    }

    const loadedPlan = await getSavedPlan(planId)
    plan.value = cloneValue(loadedPlan)
    activeEditorDay.value = loadedPlan.itinerary?.[0]?.day || 1
    activeDayFilter.value = loadedPlan.itinerary?.[0]?.day || 'all'
    routeData.value = await getPlanRoutes(planId)
    persistDraft()
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  } finally {
    loading.value = false
  }
}

const savePlanEdits = async () => {
  if (!plan.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    const savedPlan = await applyPlanEdits(buildEditPayload())
    plan.value = cloneValue(savedPlan)
    routeData.value = await getPlanRoutes(savedPlan.planId)
    persistDraft()
    const nextUrl = new URL(window.location.href)
    nextUrl.searchParams.set('planId', savedPlan.planId)
    window.history.replaceState({}, '', nextUrl)
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  } finally {
    saving.value = false
  }
}

const optimizeCurrentPlan = async () => {
  if (!plan.value) return
  optimizing.value = true
  isPreviewComputing.value = true
  computingMessage.value = 'AI 正在优化你当前编辑后的行程。'
  errorMessage.value = ''
  try {
    const optimizedPlan = await optimizePlan({
      plan: buildEditPayload(),
      interests: [],
      instruction: optimizationInstruction.value.trim()
    })
    plan.value = cloneValue(optimizedPlan)
    routeData.value = await getPlanRoutes(optimizedPlan.planId)
    optimizationInstruction.value = ''
    persistDraft()
    const nextUrl = new URL(window.location.href)
    nextUrl.searchParams.set('planId', optimizedPlan.planId)
    window.history.replaceState({}, '', nextUrl)
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  } finally {
    optimizing.value = false
    isPreviewComputing.value = false
  }
}

const exportCanvas = async () => {
  if (!exportSurfaceRef.value) {
    throw new Error('暂无可导出的行程内容')
  }
  return html2canvas(exportSurfaceRef.value, {
    backgroundColor: '#f4f1ea',
    scale: 2,
    useCORS: true
  })
}

const exportPlanAsImage = async (type = 'jpg') => {
  if (!plan.value) return
  exportLoading.value = true
  errorMessage.value = ''
  try {
    const canvas = await exportCanvas()
    const link = document.createElement('a')
    link.download = `${cityName.value || '行程'}-编辑版.${type}`
    link.href = canvas.toDataURL(type === 'jpg' ? 'image/jpeg' : 'image/png', 0.96)
    link.click()
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  } finally {
    exportLoading.value = false
  }
}

const exportPlanAsPdf = async () => {
  if (!plan.value) return
  exportLoading.value = true
  errorMessage.value = ''
  try {
    const canvas = await exportCanvas()
    const imageData = canvas.toDataURL('image/jpeg', 0.96)
    const orientation = canvas.width > canvas.height ? 'landscape' : 'portrait'
    const pdf = new jsPDF({
      orientation,
      unit: 'px',
      format: [canvas.width, canvas.height]
    })
    pdf.addImage(imageData, 'JPEG', 0, 0, canvas.width, canvas.height)
    pdf.save(`${cityName.value || '行程'}-编辑版.pdf`)
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  } finally {
    exportLoading.value = false
  }
}

const setActiveDay = (day) => {
  activeDayFilter.value = day
  if (day !== 'all') activeEditorDay.value = day
}

const activateEditorDay = (dayNumber) => {
  activeEditorDay.value = dayNumber
  activeDayFilter.value = dayNumber
}

const getRouteSummaryForDay = (dayNumber) => {
  const route = routeData.value?.days?.find((day) => day.day === dayNumber)
  if (!route) return '路线正在计算'
  return `${formatDistance(route.distanceMeters)} · ${formatDuration(route.durationSeconds)}`
}

const getDayColor = (dayNumber = 1) => dayPalette[(dayNumber - 1) % dayPalette.length]

const destroyMap = () => {
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
  }
  markerLayer = null
  lineLayer = null
}

const ensureMap = async () => {
  if (mapInstance || !mapContainerRef.value) return
  await nextTick()
  if (!mapContainerRef.value) return

  mapInstance = L.map(mapContainerRef.value, {
    zoomControl: true,
    attributionControl: false
  }).setView([31.2304, 121.4737], 11)

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19
  }).addTo(mapInstance)

  markerLayer = L.layerGroup().addTo(mapInstance)
  lineLayer = L.layerGroup().addTo(mapInstance)
}

const buildMarkerIcon = (dayNumber, stopIndex) =>
  L.divIcon({
    className: 'editor-map-marker-wrap',
    html: `<div class="editor-map-marker" style="background:${getDayColor(dayNumber)}">${dayNumber}-${stopIndex}</div>`,
    iconSize: [34, 34],
    iconAnchor: [17, 17]
  })

const renderMap = async () => {
  if (!plan.value) return
  await ensureMap()
  if (!mapInstance || !markerLayer || !lineLayer) return

  markerLayer.clearLayers()
  lineLayer.clearLayers()

  const center = getPlanCenter(plan.value)
  if (!center) return

  const bounds = []

  activeDays.value.forEach((day) => {
    const validAttractions = (day.attractions || []).filter((attraction) => isNearPlanCenter(attraction, center))
    validAttractions.forEach((attraction, index) => {
      const latlng = [Number(attraction.latitude), Number(attraction.longitude)]
      bounds.push(latlng)
      L.marker(latlng, { icon: buildMarkerIcon(day.day, index + 1) })
        .bindPopup(`<strong>${attraction.name}</strong><br/>${displayCategory(attraction.category)}`)
        .addTo(markerLayer)
    })

    const polyline = (routeData.value?.days?.find((route) => route.day === day.day)?.polyline || [])
      .filter((point) => isNearPlanCenter(point, center))
      .map((point) => [Number(point.latitude), Number(point.longitude)])

    if (polyline.length > 1) {
      polyline.forEach((point) => bounds.push(point))
      L.polyline(polyline, {
        color: getDayColor(day.day),
        weight: 5,
        opacity: 0.9
      }).addTo(lineLayer)
    }
  })

  if (bounds.length > 1) {
    mapInstance.fitBounds(bounds, { padding: [32, 32] })
  } else if (bounds.length === 1) {
    mapInstance.setView(bounds[0], 14)
  } else {
    mapInstance.setView([Number(center.latitude), Number(center.longitude)], 12)
  }

  window.setTimeout(() => mapInstance?.invalidateSize(), 60)
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const planId = params.get('planId')
  if (!planId) {
    errorMessage.value = '缺少 planId，请从主页打开编辑页。'
    return
  }
  await loadPlan(planId)
  await renderMap()
})

onBeforeUnmount(() => {
  window.clearTimeout(previewTimer)
  destroyMap()
})

watch(
  plan,
  async (value) => {
    if (!value) return
    persistDraft()
    await renderMap()
  },
  { deep: true }
)

watch(
  routeData,
  async () => {
    await renderMap()
  },
  { deep: true }
)

watch(activeDayFilter, () => renderMap())
</script>

<style scoped>
.editor-page {
  min-height: 100vh;
  padding: 24px;
  color: #17313a;
  background:
    radial-gradient(circle at top left, rgba(25, 120, 105, 0.12), transparent 26%),
    radial-gradient(circle at top right, rgba(238, 185, 97, 0.12), transparent 22%),
    linear-gradient(180deg, #f3f8f6 0%, #edf3f1 100%);
}

.editor-hero,
.map-panel,
.optimizer-panel,
.day-card {
  border: 1px solid rgba(213, 225, 221, 0.94);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 44px rgba(24, 53, 48, 0.06);
}

.editor-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px;
}

.editor-kicker {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  color: #16685f;
  font-size: 12px;
  font-weight: 700;
  background: rgba(18, 107, 98, 0.08);
}

.editor-hero h1 {
  margin: 14px 0 10px;
  font-size: 48px;
  line-height: 1.08;
  letter-spacing: -0.03em;
}

.editor-hero p {
  max-width: 780px;
  margin: 0;
  color: #5d726d;
  font-size: 18px;
  line-height: 1.7;
}

.editor-hero-actions,
.optimizer-actions,
.stop-card-actions,
.stop-add-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.ghost-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 16px;
  border-radius: 14px;
  color: #14655d;
  text-decoration: none;
  background: rgba(18, 107, 98, 0.08);
}

.editor-alert {
  margin-top: 16px;
}

.editor-layout {
  display: grid;
  grid-template-columns: minmax(620px, 1.18fr) minmax(420px, 0.82fr);
  gap: 20px;
  margin-top: 20px;
  align-items: start;
}

.map-panel {
  position: sticky;
  top: 20px;
  display: grid;
  gap: 16px;
  padding: 18px;
}

.map-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.map-panel-title {
  display: grid;
  gap: 4px;
}

.map-panel-title span {
  color: #728682;
  font-size: 12px;
  font-weight: 700;
}

.map-panel-title strong {
  font-size: 24px;
  color: #17313a;
}

.day-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.day-chip {
  padding: 10px 16px;
  color: #33514b;
  cursor: pointer;
  border: 1px solid rgba(214, 226, 222, 0.92);
  border-radius: 999px;
  background: #ffffff;
  transition: all 0.18s ease;
}

.day-chip:hover,
.day-chip.active {
  color: #ffffff;
  border-color: rgba(18, 107, 98, 0.9);
  background: linear-gradient(135deg, #126b62 0%, #1f8c77 100%);
}

.map-canvas-shell {
  position: relative;
  overflow: hidden;
  min-height: 760px;
  border-radius: 24px;
  border: 1px solid rgba(216, 226, 222, 0.92);
  background: linear-gradient(180deg, #eef5f2 0%, #f8fbfa 100%);
}

.map-canvas {
  width: 100%;
  height: 760px;
}

.map-loading-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(243, 248, 246, 0.66);
  backdrop-filter: blur(6px);
}

.map-loading-card {
  display: grid;
  gap: 8px;
  min-width: 280px;
  max-width: 360px;
  padding: 18px 20px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(212, 225, 220, 0.96);
  box-shadow: 0 18px 44px rgba(18, 107, 98, 0.12);
}

.map-loading-card strong {
  color: #15665d;
  font-size: 18px;
}

.map-loading-card span {
  color: #67807a;
  line-height: 1.6;
}

.map-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.map-stat {
  display: grid;
  gap: 8px;
  padding: 18px;
  border-radius: 22px;
  background: #f8fbfa;
}

.map-stat small {
  color: #728682;
  font-size: 12px;
}

.map-stat strong {
  color: #17313a;
  font-size: 26px;
}

.map-stat.accent {
  background: linear-gradient(135deg, #115f58 0%, #198371 100%);
}

.map-stat.accent small,
.map-stat.accent strong {
  color: #ffffff;
}

.editor-side-column {
  display: grid;
  gap: 16px;
}

.optimizer-panel {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.optimizer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.optimizer-head small {
  color: #728682;
  font-size: 12px;
  font-weight: 700;
}

.optimizer-head h2 {
  margin: 8px 0 0;
  font-size: 24px;
}

.day-stack {
  display: grid;
  gap: 14px;
}

.day-card {
  overflow: hidden;
}

.day-card.active {
  border-color: rgba(19, 108, 97, 0.28);
  box-shadow: 0 22px 50px rgba(18, 107, 98, 0.1);
}

.day-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 20px;
  text-align: left;
  cursor: pointer;
  border: 0;
  background: transparent;
}

.day-card-title {
  display: grid;
  gap: 8px;
}

.day-card-badge {
  display: inline-flex;
  width: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  color: #16685f;
  font-size: 12px;
  font-weight: 700;
  background: rgba(18, 107, 98, 0.08);
}

.day-card-title strong {
  color: #183640;
  font-size: 24px;
  line-height: 1.3;
}

.day-card-title small {
  color: #637773;
  font-size: 14px;
  line-height: 1.6;
}

.day-card-meta {
  display: grid;
  gap: 6px;
  text-align: right;
}

.day-card-meta em,
.day-card-meta span {
  color: #67807a;
  font-style: normal;
}

.day-card-body {
  display: grid;
  gap: 14px;
  padding: 0 20px 20px;
}

.day-card-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  color: #34524d;
  background: linear-gradient(180deg, #f6fbf9 0%, #edf6f2 100%);
}

.stop-list {
  display: grid;
  gap: 10px;
}

.stop-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, #fbfcfc 0%, #f5f9f7 100%);
  border: 1px solid rgba(222, 232, 228, 0.94);
}

.stop-card-copy {
  display: grid;
  gap: 4px;
}

.stop-card-copy strong {
  color: #183640;
  font-size: 22px;
}

.stop-card-copy span {
  color: #6f8480;
}

.stop-add-row {
  align-items: center;
}

.stop-add-row :deep(.el-input) {
  flex: 1 1 auto;
}

.plain-pill {
  min-height: 36px;
  padding: 0 14px;
  color: #1a5650;
  cursor: pointer;
  border: 1px solid rgba(191, 214, 207, 0.92);
  border-radius: 999px;
  background: #ffffff;
}

.plain-pill.danger {
  color: #b64a39;
  border-color: rgba(225, 176, 168, 0.92);
}

:deep(.editor-map-marker-wrap) {
  background: transparent;
  border: 0;
}

:deep(.editor-map-marker) {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
  border-radius: 999px;
  box-shadow: 0 10px 24px rgba(18, 107, 98, 0.24);
}

@media (max-width: 1440px) {
  .editor-layout {
    grid-template-columns: minmax(540px, 1fr) minmax(380px, 0.9fr);
  }

  .map-canvas-shell,
  .map-canvas {
    min-height: 660px;
    height: 660px;
  }
}

@media (max-width: 1180px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }

  .map-panel {
    position: static;
  }
}

@media (max-width: 760px) {
  .editor-page {
    padding: 16px;
  }

  .editor-hero,
  .day-card-head,
  .day-card-summary,
  .stop-card,
  .optimizer-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .editor-hero h1 {
    font-size: 34px;
  }

  .map-canvas-shell,
  .map-canvas {
    min-height: 420px;
    height: 420px;
  }

  .map-stats {
    grid-template-columns: 1fr;
  }

  .stop-card-copy strong {
    font-size: 18px;
  }
}
</style>
