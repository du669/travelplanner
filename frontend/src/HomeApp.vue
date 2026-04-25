<template>
  <div class="home-page">
  <el-container class="app-shell">
    <el-aside width="360px" class="planner-panel">
      <div class="brand">
        <span class="brand-mark">TP</span>
        <div>
          <h1>旅行路线规划</h1>
          <p>主页负责生成、预览、地图联动和导出，编辑放到独立页面处理。</p>
        </div>
      </div>

      <section class="hero-panel compact-hero">
        <h2>下一段旅程从这里开始</h2>
        <div class="hero-stats">
          <div v-for="item in plannerHighlights" :key="item.label" class="hero-stat">
            <small>{{ item.label }}</small>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>

      <section class="planner-summary-card">
        <div class="planner-summary-head">
          <strong>AI 规划</strong>
        </div>

        <el-form label-position="top" class="quick-ai-form">
          <el-form-item label="目的地" class="quick-field">
            <el-autocomplete
              v-model="form.city"
              :fetch-suggestions="queryDestinations"
              clearable
              placeholder="例如：上海、北京、东京"
              @select="handleDestinationSelect"
            >
              <template #default="{ item }">
                <div class="destination-option">
                  <span>{{ item.city }}</span>
                  <small>{{ item.country }}</small>
                </div>
              </template>
            </el-autocomplete>
          </el-form-item>

          <div class="quick-inline-grid">
          <el-form-item label="开始日期" class="quick-field">
            <el-date-picker
              v-model="form.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              format="YYYY/MM/DD"
              :clearable="false"
              class="quick-date-picker"
              @change="handleStartDateChange"
            />
          </el-form-item>

          <el-form-item label="游玩天数" class="quick-field">
            <el-input-number
              :min="1"
              :max="7"
              :model-value="form.days"
              class="quick-days-input"
              controls-position="right"
              @change="handleDaysChange"
            />
          </el-form-item>
          </div>

          <el-form-item label="兴趣偏好" class="quick-field">
            <el-checkbox-group v-model="form.interests" class="quick-interest-grid">
              <el-checkbox-button v-for="interest in quickInterests" :key="interest.value" :value="interest.value">
                {{ interest.label }}
              </el-checkbox-button>
            </el-checkbox-group>
          </el-form-item>
        </el-form>

        <div class="quick-ai-actions">
          <el-button
            type="success"
            class="quick-ai-submit"
            :loading="activeLoadingMode === 'ai'"
            @click="generateAiPlan"
          >
            开始 AI 规划
          </el-button>

          <el-button
            v-if="activeLoadingMode === 'ai'"
            class="quick-ai-stop"
            type="danger"
            plain
            @click="stopAiGeneration"
          >
            停止 AI 生成
          </el-button>
        </div>
      </section>

      <el-dialog
        v-model="plannerDialogVisible"
        class="planner-dialog"
        width="min(920px, calc(100vw - 64px))"
        append-to-body
      >
        <template #header>
          <div class="planner-dialog-head">
            <div>
              <small>规划设置</small>
              <strong>{{ currentModeLabel }}</strong>
            </div>
          </div>
        </template>

        <el-form label-position="top" class="planner-form">
          <el-form-item label="规划模式">
            <el-segmented v-model="planMode" :options="planModeOptions" class="full-width" />
          </el-form-item>

          <el-form-item label="目的地">
            <el-autocomplete
              v-model="form.city"
              :fetch-suggestions="queryDestinations"
              clearable
              placeholder="例如：上海、北京、东京"
              @select="handleDestinationSelect"
            >
              <template #default="{ item }">
                <div class="destination-option">
                  <span>{{ item.city }}</span>
                  <small>{{ item.country }}</small>
                </div>
              </template>
            </el-autocomplete>
            <p class="field-hint">当前内置城市：{{ supportedCitiesText || '加载中...' }}</p>
          </el-form-item>

          <el-form-item label="开始日期">
            <label class="native-date-field single-date-field full-width">
              <span>选择出发日期</span>
              <input
                :value="form.startDate"
                class="native-date-input"
                type="date"
                @input="handleStartDateChange($event.target.value)"
              />
            </label>
          </el-form-item>

          <el-form-item label="总天数">
            <div class="days-picker-row">
              <el-slider
                :min="1"
                :max="7"
                :model-value="form.days"
                class="days-slider"
                show-stops
                @input="handleDaysChange"
              />
              <el-input-number
                :min="1"
                :max="7"
                :model-value="form.days"
                controls-position="right"
                @change="handleDaysChange"
              />
            </div>
          </el-form-item>

          <el-form-item label="兴趣偏好">
            <el-checkbox-group v-model="form.interests">
              <el-checkbox-button v-for="interest in interests" :key="interest.value" :value="interest.value">
                {{ interest.label }}
              </el-checkbox-button>
            </el-checkbox-group>
          </el-form-item>

          <section v-if="planMode === 'standard'" class="manual-plan-panel">
            <div class="manual-plan-head">
              <div>
                <h3>普通规划：只保留你填写的景点</h3>
                <p>默认不会擅自补景点。只有你打开“允许补充”时，系统才会辅助增加新的点位。</p>
              </div>
              <el-tag type="success" effect="plain">手动规划</el-tag>
            </div>

            <el-switch
              v-model="form.allowSupplementalAttractions"
              inline-prompt
              active-text="允许补充"
              inactive-text="仅保留手填"
            />

            <div class="manual-day-list">
              <article v-for="day in form.manualDays" :key="day.day" class="manual-day-card">
                <div class="manual-day-card-head">
                  <strong>第 {{ day.day }} 天</strong>
                  <span>{{ formatDateLabel(day.date) }}</span>
                </div>
                <el-input
                  v-model="day.attractionsText"
                  type="textarea"
                  :rows="3"
                  resize="none"
                  placeholder="例如：外滩、豫园、上海博物馆"
                />
              </article>
            </div>
          </section>
        </el-form>

        <template #footer>
          <div class="planner-dialog-footer">
            <el-button @click="plannerDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="confirmPlannerDialog">确认设置</el-button>
          </div>
        </template>
      </el-dialog>

      <div class="action-group compact-actions">
        <el-button type="primary" class="full-width" @click="openStandardPlannerDialog">普通规划设置</el-button>
        <el-button
          type="primary"
          class="full-width"
          :loading="activeLoadingMode === 'standard'"
          @click="generateStandardPlan"
        >
          生成普通规划
        </el-button>
      </div>

      <section v-if="errorMessage" class="status-card status-error">
        <strong>出现问题</strong>
        <p>{{ errorMessage }}</p>
      </section>

      <section class="saved-plans">
        <div class="section-title">
          <h2>最近保存的行程</h2>
          <span>{{ savedPlans.length }}</span>
        </div>
        <button
          v-for="savedPlan in savedPlans"
          :key="savedPlan.planId"
          class="saved-plan-card"
          @click="loadSavedPlan(savedPlan.planId)"
        >
          <span>{{ savedPlan.city }} · {{ savedPlan.days }} 天</span>
          <small>{{ savedPlan.startDate }} · {{ savedPlan.stopCount }} 个景点</small>
        </button>
      </section>
    </el-aside>

    <el-container>
      <el-main class="main-content">
        <section ref="mapSectionRef" class="map-section" :class="{ fullscreen: isMapFullscreen }">
            <div class="map-toolbar" v-if="displayPlan">
              <div class="map-toolbar-group">
                <span class="map-toolbar-label">地图视图</span>
                <div class="map-filter-chips">
                  <button class="map-chip" :class="{ active: activeDayFilter === 'all' }" @click="setActiveDay('all')">
                    全部天数
                  </button>
                  <button
                    v-for="day in displayPlan.itinerary"
                    :key="`map-day-${day.day}`"
                    class="map-chip"
                    :class="{ active: activeDayFilter === day.day }"
                    @click="setActiveDay(day.day)"
                  >
                    第 {{ day.day }} 天
                  </button>
                </div>
              </div>

              <div class="map-toolbar-group">
                <span class="map-toolbar-label">地图风格</span>
                <el-segmented v-model="mapTheme" :options="mapThemeOptions" size="small" />
              </div>

              <div class="map-toolbar-group" v-if="activeRouteSummary">
                <span class="map-toolbar-label">路线概览</span>
                <div class="map-toolbar-metric">{{ activeRouteSummary.distanceText }} · {{ activeRouteSummary.durationText }}</div>
              </div>

              <div class="map-toolbar-group map-action-group">
                <el-button plain @click="openEditorPage" :disabled="loading || !plan?.planId">编辑行程</el-button>
                <el-button plain @click="exportPlanAsImage('jpg')" :disabled="!displayPlan || exportLoading">导出 JPG</el-button>
                <el-button plain @click="exportPlanAsPdf" :disabled="!displayPlan || exportLoading">导出 PDF</el-button>
              </div>
            </div>

            <div ref="mapStageRef" class="map-stage" :class="{ fullscreen: isMapFullscreen }">
              <div id="map" class="map"></div>
            </div>
            <button class="map-fullscreen-btn" type="button" @click="toggleMapFullscreen">
              {{ isMapFullscreen ? '退出全屏' : '全屏查看' }}
            </button>
        </section>

        <section class="content-grid">
          <div class="itinerary">
            <div class="section-title">
              <h2>每日概览</h2>
              <span v-if="displayPlan">{{ formatDateLabel(displayPlan.startDate) }} 起 · {{ displayPlan.days }} 天</span>
            </div>

            <div v-if="isPreviewing" class="preview-banner">
              <div class="preview-banner-copy">
                <strong>{{ planMode === 'ai' ? 'AI 实时预览已开启' : '普通规划实时预览已开启' }}</strong>
                <span>{{ previewMessage }}</span>
              </div>
              <el-progress
                class="running-progress"
                :percentage="previewProgress"
                :stroke-width="10"
                :show-text="true"
                :format="formatProgress"
                status="success"
              />
            </div>

            <el-timeline v-if="displayPlan">
              <el-timeline-item
                v-for="day in displayPlan.itinerary"
                :key="day.day"
                :timestamp="`第 ${day.day} 天 · ${getDateForDay(day.day)}`"
                placement="top"
              >
                <el-card
                  shadow="never"
                  class="day-card draggable-surface"
                  :class="{
                    'day-card-active': detailDayNumber === day.day,
                    'dragging-card': draggedDayNumber === day.day
                  }"
                  draggable="true"
                  @click="activateDay(day.day)"
                  @dragstart="handleDayDragStart(day.day)"
                  @dragover.prevent
                  @drop="handleDayDrop(day.day)"
                  @dragend="clearDragState"
                >
                  <div class="day-card-shell">
                    <div class="day-card-head">
                      <div>
                        <div class="drag-label">拖动可调整整天顺序</div>
                        <h3>{{ day.title }}</h3>
                        <div class="day-date-chip">{{ getDateForDay(day.day) }}</div>
                        <p>{{ day.theme }} · 景点 {{ day.attractions.length }} 个 · 概览 {{ getDayDistanceText(day.day, day) }}</p>
                      </div>
                      <el-tag round type="success">{{ getDayRouteMeta(day.day)?.distanceText || '待估算' }}</el-tag>
                    </div>

                    <div class="route-list">
                      <button
                        v-for="(attraction, attractionIndex) in day.attractions"
                        :key="`${day.day}-${attraction.id ?? attractionIndex}`"
                        class="route-stop"
                        @click.stop="focusAttraction(day.day, attraction)"
                      >
                        <span>{{ attractionIndex + 1 }}. {{ attraction.name }}</span>
                        <small>{{ displayCategory(attraction.category) }} · 建议 {{ attraction.suggestedHours || 2 }} 小时</small>
                      </button>
                    </div>
                  </div>
                </el-card>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="还没有可展示的行程结果。" />
          </div>

          <div class="detail-sidebar">
            <div class="section-title">
              <h2>当天详细攻略</h2>
              <span v-if="activeDetailDay">第 {{ activeDetailDay.day }} 天</span>
            </div>

            <template v-if="activeDetailDay">
              <div class="detail-summary">
                <div class="metric-card glass-card">
                  <strong>{{ activeDetailDay.attractions.length }}</strong>
                  <span>景点数量</span>
                </div>
                <div class="metric-card glass-card">
                  <strong>{{ getDayPlayHours(activeDetailDay) }}</strong>
                  <span>建议游玩</span>
                </div>
                <div class="metric-card glass-card">
                  <strong>{{ activeDayRouteMeta?.distanceText || '待估算' }}</strong>
                  <span>换乘距离</span>
                </div>
              </div>

              <div class="detail-drag-tip">右侧景点卡片支持拖动排序，地图和路线会立即刷新。</div>

              <div class="strategy-stack">
                <article
                  v-for="item in activeDaySchedule.items"
                  :key="item.key"
                  class="detail-card"
                  :class="[
                    item.type === 'spot' ? 'spot-detail-card draggable-surface' : 'transfer-card',
                    draggedSpotKey === item.key ? 'dragging-card' : ''
                  ]"
                  :draggable="item.type === 'spot'"
                  @dragstart="item.type === 'spot' && handleSpotDragStart(item)"
                  @dragover.prevent="item.type === 'spot' && handleSpotDragOver(item)"
                  @drop="item.type === 'spot' && handleSpotDrop(item)"
                  @dragend="clearDragState"
                >
                  <template v-if="item.type === 'spot'">
                    <div v-if="resolveAttractionImage(item.attraction, item.key)" class="spot-image-wrap">
                      <img
                        :src="resolveAttractionImage(item.attraction, item.key)"
                        :alt="item.attraction.name"
                        class="spot-image"
                        @error="markImageBroken(item.key)"
                      />
                    </div>
                    <div v-else class="spot-image-placeholder">{{ item.attraction.name.slice(0, 1) }}</div>
                    <div class="spot-detail-body">
                      <div class="spot-detail-head">
                        <div>
                          <div class="drag-label">拖动可调整景点顺序</div>
                          <h3>{{ item.order }}. {{ item.attraction.name }}</h3>
                          <p>{{ item.startText }} - {{ item.endText }} · {{ displayCategory(item.attraction.category) }}</p>
                        </div>
                        <el-rate :model-value="item.attraction.rating" disabled size="small" />
                      </div>
                      <p>{{ item.attraction.description || '已加入当天路线，可继续在编辑页中做更细的优化。' }}</p>
                    </div>
                  </template>

                  <template v-else>
                    <div class="transfer-head">
                      <div>
                        <h3>{{ item.leg.fromName }} → {{ item.leg.toName }}</h3>
                        <p>{{ item.startText }} - {{ item.endText }} · {{ modeLabel(item.leg.mode) }}</p>
                      </div>
                      <div class="transfer-badges">
                        <el-tag size="small" type="success">{{ formatDistance(item.leg.distanceMeters) }}</el-tag>
                        <el-tag size="small" type="info">{{ formatDuration(item.leg.durationSeconds) }}</el-tag>
                      </div>
                    </div>
                  </template>
                </article>
              </div>
            </template>

            <el-empty v-else description="选择某一天后，这里会显示当天的详细攻略。" />
          </div>
        </section>

        <section class="export-stage" aria-hidden="true">
          <div ref="exportSurfaceRef" class="export-surface" v-if="displayPlan">
            <header class="export-cover">
              <small>Travel Planner Export</small>
              <h1>{{ displayPlan.destination.city }} {{ displayPlan.days }} 天行程总览</h1>
              <p>
                {{ formatDateLabel(displayPlan.startDate) }} 出发 · 共
                {{ displayPlan.itinerary.reduce((sum, day) => sum + day.attractions.length, 0) }} 个景点
              </p>
            </header>

            <section class="export-summary-grid">
              <article class="export-summary-card">
                <span>目的地</span>
                <strong>{{ displayPlan.destination.city }}</strong>
              </article>
              <article class="export-summary-card">
                <span>总天数</span>
                <strong>{{ displayPlan.days }} 天</strong>
              </article>
              <article class="export-summary-card">
                <span>路线摘要</span>
                <strong>{{ activeRouteSummary?.distanceText || '待估算' }}</strong>
              </article>
            </section>

            <section class="export-day-stack">
              <article v-for="day in displayPlan.itinerary" :key="`export-${day.day}`" class="export-day-card">
                <div class="export-day-head">
                  <div>
                    <small>第 {{ day.day }} 天</small>
                    <h3>{{ day.title }}</h3>
                    <p>{{ day.theme }}</p>
                  </div>
                  <span>{{ getDateForDay(day.day) }}</span>
                </div>
                <div class="export-stop-list">
                  <div
                    v-for="(attraction, attractionIndex) in day.attractions"
                    :key="`export-${day.day}-${attractionIndex}`"
                    class="export-stop-item"
                  >
                    <strong>{{ attractionIndex + 1 }}. {{ attraction.name }}</strong>
                    <span>{{ displayCategory(attraction.category) }} · 建议 {{ attraction.suggestedHours || 2 }} 小时</span>
                  </div>
                </div>
              </article>
            </section>
          </div>
        </section>
      </el-main>
    </el-container>
  </el-container>
  <footer class="site-record-footer">
    <span>@2026 DJL&ZJC</span>
    <a href="https://beian.miit.gov.cn" target="_blank" rel="noopener noreferrer">辽ICP备2025071101号-2</a>
  </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import html2canvas from 'html2canvas'
import jsPDF from 'jspdf'
import {
  getDestinations,
  getMapConfig,
  getPlanRoutes,
  getSavedPlan,
  getSavedPlans,
  streamAiPlan,
  streamStandardPlan
} from './api/travel'

const EDIT_DRAFT_STORAGE_KEY = 'travelplanner-edit-draft'

const destinations = ref([])
const plan = ref(null)
const previewPlan = ref(null)
const routeData = ref(null)
const previewRouteData = ref(null)
const savedPlans = ref([])
const loading = ref(false)
const activeLoadingMode = ref('')
const errorMessage = ref('')
const plannerDialogVisible = ref(false)
const planMode = ref('ai')
const mapConfig = ref(null)
const map = ref(null)
const mapReady = ref(false)
const mapOverlays = ref([])
const infoWindow = ref(null)
const mapSectionRef = ref(null)
const mapStageRef = ref(null)
const supportedCitiesText = ref('')
const activeDayFilter = ref('all')
const mapTheme = ref('normal')
const isMapFullscreen = ref(false)
const previewMessage = ref('')
const previewProgress = ref(0)
const brokenImages = ref({})
const pendingPreviewCity = ref('')
const pendingPreviewCenter = ref(null)
const aiAbortController = ref(null)
const lastAnimatedTarget = ref('')
const exportLoading = ref(false)
const exportSurfaceRef = ref(null)
const draggedDayNumber = ref(null)
const draggedSpotKey = ref('')
const draggedSpotMeta = ref(null)

const interests = [
  { label: '文化', value: 'culture' },
  { label: '美食', value: 'food' },
  { label: '历史', value: 'history' },
  { label: '自然', value: 'nature' },
  { label: '漫步', value: 'walk' },
  { label: '观景', value: 'view' },
  { label: '建筑', value: 'architecture' },
  { label: '艺术', value: 'art' },
  { label: '夜生活', value: 'nightlife' },
  { label: '摄影', value: 'photography' },
  { label: '购物', value: 'shopping' },
  { label: '亲子', value: 'family' },
  { label: '博物馆', value: 'museum' }
]

const quickInterests = interests.slice(0, 8)

const planModeOptions = [
  { label: 'AI 规划', value: 'ai' },
  { label: '普通规划', value: 'standard' }
]

const mapThemeOptions = [
  { label: '标准', value: 'normal' },
  { label: '浅色', value: 'whitesmoke' },
  { label: '清新', value: 'fresh' }
]

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

const dayPalette = ['#e76f51', '#2a9d8f', '#457b9d', '#f4a261', '#8d5fd3', '#ef476f', '#118ab2']
const today = new Date()
const defaultStartDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(
  today.getDate()
).padStart(2, '0')}`

const form = reactive({
  city: '',
  startDate: defaultStartDate,
  endDate: defaultStartDate,
  days: 3,
  interests: ['culture', 'walk', 'food'],
  manualDays: [],
  allowSupplementalAttractions: false
})

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'short'
})

const cloneValue = (value) => JSON.parse(JSON.stringify(value))

const parseDateString = (value) => {
  if (!value) return null
  if (value instanceof Date) return value
  if (typeof value !== 'string') return null
  const matched = value.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!matched) return null
  const parsed = new Date(Number(matched[1]), Number(matched[2]) - 1, Number(matched[3]))
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

const toDateInputValue = (value) => {
  const parsed = parseDateString(value)
  if (!parsed) return defaultStartDate
  return `${parsed.getFullYear()}-${String(parsed.getMonth() + 1).padStart(2, '0')}-${String(parsed.getDate()).padStart(
    2,
    '0'
  )}`
}

const shiftDate = (value, offsetDays) => {
  const parsed = parseDateString(value)
  if (!parsed) return defaultStartDate
  parsed.setDate(parsed.getDate() + offsetDays)
  return toDateInputValue(parsed)
}

const clampDays = (value) => Math.min(7, Math.max(1, Math.round(Number(value) || 1)))

const formatDateLabel = (value) => {
  const parsed = parseDateString(value)
  return parsed ? dateFormatter.format(parsed) : '未设置'
}

const syncManualDays = () => {
  const nextManualDays = []
  for (let index = 0; index < form.days; index += 1) {
    const existing = form.manualDays[index] || {}
    nextManualDays.push({
      day: index + 1,
      date: shiftDate(form.startDate, index),
      attractionsText: existing.attractionsText || ''
    })
  }
  form.manualDays = nextManualDays
}

const syncDerivedDates = () => {
  form.startDate = toDateInputValue(form.startDate)
  form.days = clampDays(form.days)
  form.endDate = shiftDate(form.startDate, form.days - 1)
  syncManualDays()
}

const displayCategory = (category) => categoryLabels[category] || category || '综合'
const getDayColor = (dayNumber = 1) => dayPalette[(dayNumber - 1) % dayPalette.length]
const getMapStyle = (theme) => `amap://styles/${theme}`

const isPreviewing = computed(() => loading.value && !!previewPlan.value)
const displayPlan = computed(() => (isPreviewing.value ? previewPlan.value : plan.value))
const displayRouteData = computed(() => (isPreviewing.value ? previewRouteData.value : routeData.value))
const currentModeLabel = computed(() => (planMode.value === 'ai' ? 'AI 规划' : '普通规划'))
const manualDaysFilledCount = computed(() => form.manualDays.filter((day) => day.attractionsText?.trim()).length)
const plannerHighlights = computed(() => [
  { label: '行程跨度', value: `${form.days} 天` },
  { label: '兴趣标签', value: `${form.interests.length} 项` },
  { label: '手填天数', value: `${manualDaysFilledCount.value} 天` }
])

const activeDays = computed(() => {
  if (!displayPlan.value?.itinerary?.length) return []
  if (activeDayFilter.value === 'all') return displayPlan.value.itinerary
  return displayPlan.value.itinerary.filter((day) => day.day === activeDayFilter.value)
})

const detailDayNumber = computed(() => {
  if (!displayPlan.value?.itinerary?.length) return null
  if (activeDayFilter.value !== 'all') return activeDayFilter.value
  return displayPlan.value.itinerary[0].day
})

const activeDetailDay = computed(() => displayPlan.value?.itinerary?.find((day) => day.day === detailDayNumber.value) || null)
const activeDetailRoute = computed(() => displayRouteData.value?.days?.find((day) => day.day === detailDayNumber.value) || null)

const formatDistance = (distanceMeters = 0) =>
  distanceMeters >= 1000 ? `${(distanceMeters / 1000).toFixed(1)} 公里` : `${Math.round(distanceMeters)} 米`

const formatDistanceKm = (distanceKm = 0) => `${Number(distanceKm || 0).toFixed(1)} km`

const formatDuration = (durationSeconds = 0) => {
  const safeSeconds = Math.max(0, Math.round(durationSeconds))
  const hours = Math.floor(safeSeconds / 3600)
  const minutes = Math.max(1, Math.round((safeSeconds % 3600) / 60))
  return hours > 0 ? `${hours} 小时 ${minutes} 分钟` : `${minutes} 分钟`
}

const getDayPlayHours = (day) =>
  `${(day?.attractions || []).reduce((sum, attraction) => sum + Number(attraction.suggestedHours || 0), 0)} 小时`

const getDayRouteMeta = (dayNumber) => {
  const route = displayRouteData.value?.days?.find((item) => item.day === dayNumber)
  if (!route) return null
  return {
    distanceText: formatDistance(route.distanceMeters),
    durationText: formatDuration(route.durationSeconds)
  }
}

const activeDayRouteMeta = computed(() => getDayRouteMeta(detailDayNumber.value))

const getDayDistanceText = (dayNumber, day) => {
  const routeMeta = getDayRouteMeta(dayNumber)
  if (routeMeta?.distanceText) {
    return routeMeta.distanceText
  }
  return formatDistanceKm(day?.distanceKm || 0)
}

const activeRouteSummary = computed(() => {
  const routes = displayRouteData.value?.days?.filter((route) => activeDayFilter.value === 'all' || route.day === activeDayFilter.value) || []
  if (!routes.length) return null
  return {
    distanceText: formatDistance(routes.reduce((sum, route) => sum + Number(route.distanceMeters || 0), 0)),
    durationText: formatDuration(routes.reduce((sum, route) => sum + Number(route.durationSeconds || 0), 0))
  }
})

const formatClock = (minutes) => {
  const safeMinutes = Math.max(0, Math.round(minutes))
  return `${String(Math.floor(safeMinutes / 60)).padStart(2, '0')}:${String(safeMinutes % 60).padStart(2, '0')}`
}

const formatProgress = (percentage) => `${Math.round(percentage)}%`
const getDateForDay = (dayNumber) => formatDateLabel(shiftDate(displayPlan.value?.startDate || form.startDate, Math.max(0, dayNumber - 1)))

const activeDaySchedule = computed(() => {
  const day = activeDetailDay.value
  const route = activeDetailRoute.value
  if (!day) return { items: [] }

  const legs = route?.legs || []
  const items = []
  let cursor = 9 * 60
  day.attractions.forEach((attraction, index) => {
    const playMinutes = Math.max(60, Number(attraction.suggestedHours || 2) * 60)
    const startMinutes = cursor
    const endMinutes = cursor + playMinutes
    items.push({
      key: `spot-${day.day}-${index}`,
      type: 'spot',
      order: index + 1,
      attraction,
      dayNumber: day.day,
      attractionIndex: index,
      startText: formatClock(startMinutes),
      endText: formatClock(endMinutes)
    })
    cursor = endMinutes
    const leg = legs[index]
    if (leg) {
      const transferMinutes = Math.max(10, Math.round(Number(leg.durationSeconds || 0) / 60))
      items.push({
        key: `leg-${day.day}-${index}`,
        type: 'transfer',
        leg,
        startText: formatClock(cursor),
        endText: formatClock(cursor + transferMinutes)
      })
      cursor += transferMinutes
    }
  })
  return { items }
})

const modeLabel = (mode) => {
  const value = String(mode || '')
  if (value.includes('地铁')) return '地铁'
  if (value.includes('公交')) return '公交'
  if (value.includes('预估')) return '预估路线'
  return '步行'
}

const markImageBroken = (key) => {
  brokenImages.value = { ...brokenImages.value, [key]: true }
}

const resolveAttractionImage = (attraction, key) => (brokenImages.value[key] ? '' : attraction?.imageUrls?.[0] || '')

const queryDestinations = (queryString, callback) => {
  const keyword = queryString.trim().toLowerCase()
  const results = keyword
    ? destinations.value.filter(
        (destination) =>
          destination.city.toLowerCase().includes(keyword) || destination.country.toLowerCase().includes(keyword)
      )
    : destinations.value

  callback(
    results.map((destination) => ({
      value: destination.city,
      city: destination.city,
      country: destination.country
    }))
  )
}

const handleDestinationSelect = (item) => {
  form.city = item.value
}

const handleStartDateChange = (value) => {
  form.startDate = toDateInputValue(value)
  syncDerivedDates()
}

const handleDaysChange = (value) => {
  form.days = clampDays(value)
  syncDerivedDates()
}

const setActiveDay = (day) => {
  activeDayFilter.value = day
}

const activateDay = (day) => {
  activeDayFilter.value = day
}

const clearPreview = () => {
  previewPlan.value = null
  previewRouteData.value = null
  previewMessage.value = ''
  previewProgress.value = 0
  pendingPreviewCity.value = ''
  pendingPreviewCenter.value = null
}

const normalizeRequestError = (error) => error?.response?.data?.message || error?.message || '生成行程失败，请稍后重试。'

const buildPayload = () => ({
  city: form.city.trim(),
  startDate: form.startDate,
  endDate: form.endDate,
  days: form.days,
  interests: form.interests,
  allowSupplementalAttractions: form.allowSupplementalAttractions,
  manualDays: form.manualDays.map((day) => ({
    day: day.day,
    date: day.date,
    attractions: day.attractionsText
      .split(/[\n,，、]+/)
      .map((item) => item.trim())
      .filter(Boolean)
  }))
})

const hasValidCoordinates = (item) => {
  const lng = Number(item?.longitude)
  const lat = Number(item?.latitude)
  return Number.isFinite(lng) && Number.isFinite(lat) && Math.abs(lng) <= 180 && Math.abs(lat) <= 90 && !(Math.abs(lng) < 0.001 && Math.abs(lat) < 0.001)
}

const findDestinationCenterFromCatalog = (cityName) => {
  const keyword = String(cityName || '').trim().toLowerCase()
  if (!keyword) return null
  const matched = destinations.value.find((destination) => destination.city?.trim().toLowerCase() === keyword)
  if (!matched || !hasValidCoordinates(matched)) return null
  return { longitude: Number(matched.longitude), latitude: Number(matched.latitude) }
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
  if (isPreviewing.value && pendingPreviewCenter.value) return pendingPreviewCenter.value
  if (hasValidCoordinates(planLike?.destination)) {
    return {
      longitude: Number(planLike.destination.longitude),
      latitude: Number(planLike.destination.latitude)
    }
  }
  return getCenterFromPlanContent(planLike) || findDestinationCenterFromCatalog(planLike?.destination?.city || form.city) || pendingPreviewCenter.value
}

const cityCenterCache = new Map()
let amapGeocoderPromise
const normalizeCityKeyword = (cityName) => String(cityName || '').trim().toLowerCase()

const getAmapGeocoder = async () => {
  if (!window.AMap?.Geocoder) return null
  if (!amapGeocoderPromise) {
    amapGeocoderPromise = Promise.resolve(new window.AMap.Geocoder({ city: '全国' }))
  }
  return amapGeocoderPromise
}

const resolveCityCenter = async (cityName) => {
  const keyword = normalizeCityKeyword(cityName)
  if (!keyword) return null
  if (cityCenterCache.has(keyword)) return cityCenterCache.get(keyword)
  const catalogCenter = findDestinationCenterFromCatalog(cityName)
  if (catalogCenter) {
    cityCenterCache.set(keyword, catalogCenter)
    return catalogCenter
  }
  const geocoder = await getAmapGeocoder()
  if (!geocoder) return null
  const resolvedCenter = await new Promise((resolve) => {
    geocoder.getLocation(cityName, (status, result) => {
      const location = status === 'complete' ? result?.geocodes?.[0]?.location : null
      if (location) {
        resolve({ longitude: Number(location.lng), latitude: Number(location.lat) })
        return
      }
      resolve(null)
    })
  })
  cityCenterCache.set(keyword, resolvedCenter)
  return resolvedCenter
}

const syncPendingPreviewCenter = async (cityName) => {
  const center = await resolveCityCenter(cityName)
  if (normalizeCityKeyword(pendingPreviewCity.value) === normalizeCityKeyword(cityName)) {
    pendingPreviewCenter.value = center
  }
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

const buildPreviewRoutes = (preview) => {
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

const buildEditDraftPayload = (planLike) => ({
  planId: planLike?.planId || null,
  destination: planLike?.destination || null,
  startDate: planLike?.startDate || null,
  days: planLike?.days || 1,
  itinerary: (planLike?.itinerary || []).map((day) => ({
    day: day.day,
    title: day.title,
    theme: day.theme,
    distanceKm: day.distanceKm || 0,
    attractions: (day.attractions || []).map((attraction, attractionIndex) => ({
      ...attraction,
      id: attraction.id ?? -(Date.now() + day.day * 100 + attractionIndex)
    }))
  }))
})

const persistEditDraft = () => {
  if (!plan.value?.planId) return
  window.sessionStorage.setItem(EDIT_DRAFT_STORAGE_KEY, JSON.stringify(buildEditDraftPayload(plan.value)))
}

const sanitizeDayTitle = (title, fallbackDayNumber) => {
  if (!title) return `第 ${fallbackDayNumber} 天`
  if (/第\s*\d+\s*天/.test(title)) {
    return title.replace(/第\s*\d+\s*天/, `第 ${fallbackDayNumber} 天`)
  }
  return title
}

const normalizeItineraryDays = (itinerary) =>
  itinerary.map((day, index) => ({
    ...day,
    day: index + 1,
    title: sanitizeDayTitle(day.title, index + 1)
  }))

const applyPlanMutation = (mutator) => {
  if (!plan.value?.itinerary?.length) return
  const draft = cloneValue(plan.value)
  mutator(draft)
  draft.itinerary = normalizeItineraryDays(draft.itinerary || [])
  draft.days = draft.itinerary.length
  plan.value = draft
  routeData.value = buildPreviewRoutes(draft)
  persistEditDraft()
}

const moveArrayItem = (array, fromIndex, toIndex) => {
  if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= array.length || toIndex >= array.length) return
  const [moved] = array.splice(fromIndex, 1)
  array.splice(toIndex, 0, moved)
}

const loadRoutes = async (planId) => {
  routeData.value = await getPlanRoutes(planId)
}

const stopAiGeneration = () => {
  if (activeLoadingMode.value !== 'ai') return
  aiAbortController.value?.abort()
}

const isAbortError = (error) => error?.name === 'AbortError' || String(error?.message || '').toLowerCase().includes('aborted')

const runPlanRequest = async (mode = planMode.value) => {
  if (!form.city.trim()) {
    errorMessage.value = '请先输入目的地。'
    return
  }
  syncDerivedDates()
  loading.value = true
  activeLoadingMode.value = mode
  errorMessage.value = ''
  activeDayFilter.value = 'all'
  brokenImages.value = {}
  try {
    const payload = buildPayload()
    clearPreview()
    pendingPreviewCity.value = payload.city
    pendingPreviewCenter.value = null
    syncPendingPreviewCenter(payload.city)

    if (mode === 'ai') {
      aiAbortController.value = new AbortController()
      await nextTick()
      await renderMap()
      await streamAiPlan(
        payload,
        {
          onStatus: (event) => {
            previewProgress.value = event.progress || previewProgress.value
            previewMessage.value = event.message || 'AI 正在生成中，请稍等。'
          },
          onPreview: (event) => {
            previewProgress.value = event.progress || previewProgress.value
            previewMessage.value = event.message || previewMessage.value
            previewPlan.value = event.preview
            previewRouteData.value = buildPreviewRoutes(event.preview)
          },
          onComplete: async (event) => {
            plan.value = event.plan
            persistEditDraft()
            clearPreview()
            await loadRoutes(event.plan.planId)
            savedPlans.value = await getSavedPlans()
          }
        },
        { signal: aiAbortController.value.signal }
      )
    } else {
      await nextTick()
      await renderMap()
      await streamStandardPlan(payload, {
        onStatus: (event) => {
          previewProgress.value = event.progress || previewProgress.value
          previewMessage.value = event.message || '普通规划整理中，请稍等。'
        },
        onPreview: (event) => {
          previewProgress.value = event.progress || previewProgress.value
          previewMessage.value = event.message || previewMessage.value
          previewPlan.value = event.preview
          previewRouteData.value = buildPreviewRoutes(event.preview)
        },
        onComplete: async (event) => {
          plan.value = event.plan
          persistEditDraft()
          clearPreview()
          await loadRoutes(event.plan.planId)
          savedPlans.value = await getSavedPlans()
        }
      })
    }

    plannerDialogVisible.value = false
  } catch (error) {
    if (isAbortError(error)) {
      previewMessage.value = '本次 AI 生成已停止。'
      return
    }
    if (!isPreviewing.value) {
      plan.value = null
      routeData.value = null
    }
    errorMessage.value = normalizeRequestError(error)
  } finally {
    aiAbortController.value = null
    loading.value = false
    activeLoadingMode.value = ''
  }
}

const generateStandardPlan = async () => {
  planMode.value = 'standard'
  await runPlanRequest('standard')
}
const generateAiPlan = async () => {
  planMode.value = 'ai'
  await runPlanRequest('ai')
}

const openStandardPlannerDialog = () => {
  planMode.value = 'standard'
  plannerDialogVisible.value = true
}

const confirmPlannerDialog = () => {
  syncDerivedDates()
  plannerDialogVisible.value = false
}

const loadSavedPlan = async (id) => {
  loading.value = true
  errorMessage.value = ''
  try {
    clearPreview()
    plan.value = await getSavedPlan(id)
    persistEditDraft()
    await loadRoutes(plan.value.planId)
    activeDayFilter.value = 'all'
    form.city = plan.value.destination.city
    form.startDate = toDateInputValue(plan.value.startDate)
    form.days = clampDays(plan.value.days)
    syncDerivedDates()
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  } finally {
    loading.value = false
  }
}

let amapLoaderPromise

const loadAmapSdk = async () => {
  if (window.AMap) return window.AMap
  if (amapLoaderPromise) return amapLoaderPromise
  if (!mapConfig.value?.enabled || !mapConfig.value?.webKey) {
    throw new Error('服务端尚未配置高德地图 Key')
  }
  window._AMapSecurityConfig = { securityJsCode: mapConfig.value.securityJsCode }
  amapLoaderPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${mapConfig.value.webKey}&plugin=AMap.Scale,AMap.ToolBar,AMap.Geocoder`
    script.async = true
    script.onload = () => resolve(window.AMap)
    script.onerror = () => reject(new Error('高德地图 SDK 加载失败'))
    document.head.appendChild(script)
  })
  return amapLoaderPromise
}

const clearMapOverlays = () => {
  if (!map.value || !mapOverlays.value.length) return
  map.value.remove(mapOverlays.value)
  mapOverlays.value = []
}

const createMarkerContent = (day, stopIndex) =>
  `<div class="amap-stop-pin" style="background:${getDayColor(day)}"><span>${day}-${stopIndex}</span></div>`

const initMap = async () => {
  const AMap = await loadAmapSdk()
  map.value = new AMap.Map('map', {
    zoom: 11,
    center: [121.4737, 31.2304],
    mapStyle: getMapStyle(mapTheme.value),
    animateEnable: true,
    jogEnable: true
  })
  map.value.addControl(new AMap.Scale())
  map.value.addControl(new AMap.ToolBar())
  infoWindow.value = new AMap.InfoWindow({ offset: new AMap.Pixel(0, -24) })
  mapReady.value = true
}

const resizeMapViewport = () => {
  ;[80, 220, 420].forEach((delay) => {
    window.setTimeout(() => {
      if (!map.value) return
      map.value.resize()
      const focusPoint = getMapFocusPoint() || getPlanCenter(displayPlan.value)
      if (hasValidCoordinates(focusPoint)) {
        map.value.setZoomAndCenter(map.value.getZoom(), [Number(focusPoint.longitude), Number(focusPoint.latitude)], true)
      }
    }, delay)
  })
}

const animateMapTo = (point, zoom = 12) => {
  if (!map.value || !hasValidCoordinates(point)) return
  const targetKey = `${point.longitude},${point.latitude}@${zoom}`
  if (lastAnimatedTarget.value === targetKey) return
  lastAnimatedTarget.value = targetKey
  map.value.setZoom(zoom)
  map.value.panTo([Number(point.longitude), Number(point.latitude)])
}

const getMapFocusPoint = () => {
  const center = getPlanCenter(displayPlan.value)
  const latestValidAttraction = [...activeDays.value]
    .reverse()
    .flatMap((day) => [...(day.attractions || [])].reverse())
    .find((attraction) => isNearPlanCenter(attraction, center))
  return latestValidAttraction || center
}

const renderMap = async () => {
  if (!mapReady.value || !map.value) return
  clearMapOverlays()
  const currentPlan = displayPlan.value
  if (!currentPlan?.itinerary?.length) return
  const center = getPlanCenter(currentPlan)
  if (!center) return

  const AMap = window.AMap
  const overlays = []
  const fitPositions = []

  activeDays.value.forEach((day) => {
    const color = getDayColor(day.day)
    const validAttractions = (day.attractions || []).filter((attraction) => isNearPlanCenter(attraction, center))
    validAttractions.forEach((attraction, index) => {
      const position = [Number(attraction.longitude), Number(attraction.latitude)]
      fitPositions.push(position)
      overlays.push(
        new AMap.Marker({
          position,
          content: createMarkerContent(day.day, index + 1),
          offset: new AMap.Pixel(-18, -40)
        })
      )
    })

    const routePolyline = (displayRouteData.value?.days?.find((item) => item.day === day.day)?.polyline || [])
      .filter((point) => isNearPlanCenter(point, center))
      .map((point) => [Number(point.longitude), Number(point.latitude)])

    if (routePolyline.length > 1) {
      routePolyline.forEach((position) => fitPositions.push(position))
      overlays.push(
        new AMap.Polyline({
          path: routePolyline,
          strokeColor: color,
          strokeWeight: 6,
          strokeOpacity: 0.8,
          strokeStyle: 'solid'
        })
      )
    }
  })

  if (overlays.length) {
    map.value.add(overlays)
    mapOverlays.value = overlays
    if (fitPositions.length > 1) {
      lastAnimatedTarget.value = ''
      map.value.setFitView(overlays, false, [56, 56, 56, 56])
      const focusPoint = getMapFocusPoint()
      if (focusPoint) {
        window.setTimeout(() => animateMapTo(focusPoint), 120)
      }
      return
    }
  }

  animateMapTo(getMapFocusPoint() || center)
}

const focusAttraction = async (dayNumber, attraction) => {
  activeDayFilter.value = dayNumber
  await nextTick()
  const center = getPlanCenter(displayPlan.value)
  if (!map.value || !isNearPlanCenter(attraction, center)) return
  const position = [Number(attraction.longitude), Number(attraction.latitude)]
  map.value.setZoomAndCenter(15, position, true)
  infoWindow.value?.setContent(`<div class="amap-info-window"><strong>${attraction.name}</strong><br/>${displayCategory(attraction.category)}</div>`)
  infoWindow.value?.open(map.value, position)
}

const handleDayDragStart = (dayNumber) => {
  draggedDayNumber.value = dayNumber
}

const handleDayDrop = (targetDayNumber) => {
  if (!draggedDayNumber.value || draggedDayNumber.value === targetDayNumber) return
  const fromIndex = (plan.value?.itinerary || []).findIndex((day) => day.day === draggedDayNumber.value)
  const toIndex = (plan.value?.itinerary || []).findIndex((day) => day.day === targetDayNumber)
  if (fromIndex < 0 || toIndex < 0) return
  applyPlanMutation((draft) => {
    moveArrayItem(draft.itinerary, fromIndex, toIndex)
  })
  activeDayFilter.value = toIndex + 1
  clearDragState()
}

const handleSpotDragStart = (item) => {
  draggedSpotKey.value = item.key
  draggedSpotMeta.value = {
    dayNumber: item.dayNumber,
    attractionIndex: item.attractionIndex
  }
}

const handleSpotDragOver = (item) => {
  if (!draggedSpotMeta.value || item.type !== 'spot') return
  draggedSpotKey.value = item.key
}

const handleSpotDrop = (item) => {
  if (!draggedSpotMeta.value || item.type !== 'spot') return
  if (draggedSpotMeta.value.dayNumber !== item.dayNumber) {
    clearDragState()
    return
  }
  const fromIndex = draggedSpotMeta.value.attractionIndex
  const toIndex = item.attractionIndex
  if (fromIndex === toIndex) {
    clearDragState()
    return
  }
  applyPlanMutation((draft) => {
    const day = draft.itinerary.find((entry) => entry.day === item.dayNumber)
    if (!day) return
    moveArrayItem(day.attractions, fromIndex, toIndex)
  })
  clearDragState()
}

const clearDragState = () => {
  draggedDayNumber.value = null
  draggedSpotKey.value = ''
  draggedSpotMeta.value = null
}

const openEditorPage = () => {
  if (!plan.value?.planId) return
  persistEditDraft()
  window.open(`/editor.html?planId=${plan.value.planId}`, '_blank', 'noopener')
}

const syncMapFullscreenState = () => {
  isMapFullscreen.value = Boolean(mapStageRef.value && document.fullscreenElement === mapStageRef.value)
  resizeMapViewport()
}

const toggleMapFullscreen = async () => {
  const target = mapStageRef.value
  if (!target) return
  try {
    if (document.fullscreenElement === target) {
      await document.exitFullscreen()
    } else {
      if (document.fullscreenElement) {
        await document.exitFullscreen()
      }
      await target.requestFullscreen()
    }
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  }
}

const exportCanvas = async () => {
  if (!exportSurfaceRef.value) throw new Error('暂无可导出的行程内容')
  return html2canvas(exportSurfaceRef.value, {
    backgroundColor: '#f4f1ea',
    scale: 2,
    useCORS: true
  })
}

const exportPlanAsImage = async (type = 'jpg') => {
  if (!displayPlan.value) return
  exportLoading.value = true
  errorMessage.value = ''
  try {
    const canvas = await exportCanvas()
    const link = document.createElement('a')
    const safeCity = displayPlan.value.destination.city || '行程'
    link.download = `${safeCity}-行程导出.${type}`
    link.href = canvas.toDataURL(type === 'jpg' ? 'image/jpeg' : 'image/png', 0.96)
    link.click()
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  } finally {
    exportLoading.value = false
  }
}

const exportPlanAsPdf = async () => {
  if (!displayPlan.value) return
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
    pdf.save(`${displayPlan.value.destination.city || '行程'}-行程导出.pdf`)
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  } finally {
    exportLoading.value = false
  }
}

onMounted(async () => {
  syncDerivedDates()
  document.addEventListener('fullscreenchange', syncMapFullscreenState)
  try {
    mapConfig.value = await getMapConfig()
    await initMap()
    resizeMapViewport()
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  }
  try {
    destinations.value = await getDestinations()
    supportedCitiesText.value = destinations.value.map((destination) => destination.city).join('、')
    if (!form.city) form.city = destinations.value[0]?.city || ''
    savedPlans.value = await getSavedPlans()
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncMapFullscreenState)
})

watch(() => [form.startDate, form.days], syncDerivedDates, { immediate: true })
watch(displayPlan, () => nextTick(renderMap))
watch(displayRouteData, () => nextTick(renderMap))
watch(activeDayFilter, () => nextTick(renderMap))
watch(mapTheme, (theme) => {
  if (!map.value) return
  map.value.setMapStyle(getMapStyle(theme))
  renderMap()
})
watch(pendingPreviewCenter, () => nextTick(renderMap))
</script>

<style scoped>
.map-action-group {
  margin-left: auto;
}

.drag-label {
  margin-bottom: 6px;
  color: #6f8480;
  font-size: 12px;
  font-weight: 700;
}

.draggable-surface {
  cursor: grab;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.draggable-surface:active {
  cursor: grabbing;
}

.dragging-card {
  transform: scale(0.99);
  border-color: rgba(24, 120, 103, 0.42);
  box-shadow: 0 18px 38px rgba(18, 107, 98, 0.14);
}

.detail-drag-tip {
  margin: 12px 0 14px;
  padding: 10px 14px;
  border-radius: 14px;
  color: #21514a;
  font-size: 13px;
  background: linear-gradient(180deg, rgba(231, 244, 240, 0.9) 0%, rgba(244, 249, 247, 0.94) 100%);
  border: 1px solid rgba(194, 219, 212, 0.92);
}

@media (max-width: 980px) {
  .map-action-group {
    margin-left: 0;
  }
}
</style>
