<template>
  <el-container class="app-shell">
    <el-aside width="360px" class="planner-panel">
      <div class="brand">
        <span class="brand-mark">TP</span>
        <div>
          <h1>旅行路线规划</h1>
          <p>结合 AI 推荐、高德坐标和真实换乘，生成更可信的每日行程。</p>
        </div>
      </div>

      <section class="hero-panel compact-hero">
        <h2>规划你的下一站</h2>
        <p>现在只保留开始日期和总天数，避免日期选择器继续出错。结束日期会自动推算。</p>
        <div class="hero-stats">
          <div v-for="item in plannerHighlights" :key="item.label" class="hero-stat">
            <small>{{ item.label }}</small>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>

      <section class="mode-launcher">
        <button class="mode-launch-card" :class="{ active: planMode === 'ai' }" @click="openPlannerDialog('ai')">
          <small>智能生成</small>
          <strong>AI 规划</strong>
          <span>适合快速拿到完整草案，并实时看到生成进度。</span>
        </button>
        <button class="mode-launch-card" :class="{ active: planMode === 'standard' }" @click="openPlannerDialog('standard')">
          <small>自己做主</small>
          <strong>普通规划</strong>
          <span>按天填写想去的景点，再由系统整理路线与换乘。</span>
        </button>
      </section>

      <section class="planner-summary-card">
        <div class="planner-summary-head">
          <div>
            <small>当前模式</small>
            <strong>{{ currentModeLabel }}</strong>
          </div>
          <el-button text type="primary" @click="openPlannerDialog(planMode)">编辑</el-button>
        </div>
        <div class="summary-row">
          <span>目的地</span>
          <strong>{{ form.city || '未设置' }}</strong>
        </div>
        <div class="summary-row">
          <span>开始日期</span>
          <strong>{{ formatDateLabel(form.startDate) }}</strong>
        </div>
        <div class="summary-row">
          <span>结束日期</span>
          <strong>{{ formatDateLabel(form.endDate) }}</strong>
        </div>
        <div class="summary-row">
          <span>总天数</span>
          <strong>{{ form.days }} 天</strong>
        </div>
        <div class="summary-row">
          <span>兴趣偏好</span>
          <div class="summary-tag-list">
            <span v-for="interest in selectedInterestLabels.slice(0, 4)" :key="interest" class="summary-tag">{{ interest }}</span>
            <span v-if="selectedInterestLabels.length > 4" class="summary-tag">+{{ selectedInterestLabels.length - 4 }}</span>
          </div>
        </div>
        <div v-if="planMode === 'standard'" class="summary-row">
          <span>普通规划</span>
          <strong>{{ manualDaysFilledCount }} 天已填写</strong>
        </div>
      </section>

      <el-dialog
        v-model="plannerDialogVisible"
        class="planner-dialog"
        width="min(920px, calc(100vw - 64px))"
        :show-close="true"
        append-to-body
        align-center
        destroy-on-close
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
              placeholder="例如：Shanghai、Tokyo、Chengdu"
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
            <p class="field-hint">日期只保留一个开始日期，避免范围选择继续出现 1940 年和反向区间 bug。</p>
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
            <p class="field-hint">最多支持 7 天，结束日期会自动计算。</p>
          </el-form-item>

          <el-form-item label="行程跨度">
            <div class="date-pill-row">
              <div class="date-pill">
                <span>出发</span>
                <strong>{{ formatDateLabel(form.startDate) }}</strong>
              </div>
              <div class="date-pill">
                <span>结束</span>
                <strong>{{ formatDateLabel(form.endDate) }}</strong>
              </div>
              <div class="date-pill accent">
                <span>总天数</span>
                <strong>{{ form.days }} 天</strong>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="兴趣偏好">
            <el-checkbox-group v-model="form.interests">
              <el-checkbox-button v-for="interest in interests" :key="interest.value" :value="interest.value">
                {{ interest.label }}
              </el-checkbox-button>
            </el-checkbox-group>
            <p class="field-hint">可以多选，普通规划和 AI 规划都会用这些偏好做筛选。</p>
          </el-form-item>

          <section v-if="planMode === 'standard'" class="manual-plan-panel">
            <div class="manual-plan-head">
              <div>
                <h3>普通规划：按天填写想去的景点</h3>
                <p>每一天填 2 到 5 个景点名称，系统会补齐高德 POI、路线与换乘信息。</p>
              </div>
              <el-tag type="success" effect="plain">手动规划模式</el-tag>
            </div>

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
                <small>可以用逗号、顿号或者换行分隔景点。</small>
              </article>
            </div>
          </section>
        </el-form>

        <template #footer>
          <div class="planner-dialog-footer">
            <el-button type="primary" @click="plannerDialogVisible = false">确定</el-button>
          </div>
        </template>
      </el-dialog>

      <div class="action-group compact-actions">
        <el-button type="primary" class="full-width" @click="openPlannerDialog(planMode)">
          调整设置
        </el-button>
        <el-button
          :type="planMode === 'ai' ? 'success' : 'primary'"
          class="full-width"
          :class="{ 'ai-button': planMode === 'ai' }"
          :loading="loading"
          @click="planMode === 'ai' ? generateAiPlan() : generateStandardPlan()"
        >
          {{ planMode === 'ai' ? '立即 AI 规划' : '生成普通规划' }}
        </el-button>
      </div>

      <section class="mode-summary">
        <div class="mode-chip" :class="`mode-chip-${planMode}`">
          {{ planMode === 'ai' ? '当前模式：AI 规划' : '当前模式：普通规划' }}
        </div>
        <p>
          {{ planMode === 'ai'
            ? 'AI 会实时生成每天的景点草案，再结合高德坐标和路线逐步校正。'
            : '普通规划会优先使用你自己填写的景点，并补齐真实坐标、顺序与换乘信息。' }}
        </p>
        <p v-if="loading && planMode === 'ai'" class="loading-tip">
          {{ previewMessage || 'AI 正在生成中，请稍等。' }}
        </p>
      </section>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
      />

      <section v-if="displayPlan" class="destination-summary">
        <div class="destination-summary-head">
          <h2>{{ displayPlan.destination.city }}</h2>
          <el-tag effect="plain" class="season-tag">最佳季节：{{ displayPlan.destination.bestSeason || '暂无' }}</el-tag>
        </div>
        <p>{{ displayPlan.destination.summary }}</p>
      </section>

      <section v-else class="empty-state">
        <h2>等待生成行程</h2>
        <p>设置目的地、开始日期和总天数后，点击按钮即可生成地图、每日概览和详细攻略。</p>
      </section>

      <section class="saved-plans">
        <div class="section-title compact">
          <h2>已保存行程</h2>
          <span>{{ savedPlans.length }}</span>
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
              <span class="map-toolbar-label">路线摘要</span>
              <div class="map-toolbar-metric">
                {{ activeRouteSummary.distanceText }} · {{ activeRouteSummary.durationText }}
              </div>
            </div>
          </div>
          <div id="map" class="map"></div>
        </section>

        <section class="content-grid">
          <div class="itinerary">
            <div class="section-title">
              <h2>每日概览</h2>
              <span v-if="displayPlan">
                {{ formatDateLabel(displayPlan.startDate) }} 起 · {{ displayPlan.days }} 天 ·
                {{ isPreviewing ? 'AI 预览生成中' : (planMode === 'ai' ? 'AI 结果' : '普通结果') }}
              </span>
            </div>

            <div v-if="isPreviewing" class="preview-banner">
              <div class="preview-banner-copy">
                <strong>实时预览已开启</strong>
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

            <div v-if="isPreviewing" class="thinking-track">
              <div class="thinking-track-bar">
                <div class="thinking-track-fill" :style="{ width: `${previewProgress}%` }"></div>
              </div>
              <div class="thinking-track-stages">
                <div
                  v-for="stage in thinkingStages"
                  :key="stage.key"
                  class="thinking-stage"
                  :class="{
                    active: previewProgress >= stage.threshold,
                    current: currentThinkingStage === stage.key
                  }"
                >
                  <span class="thinking-stage-dot"></span>
                  <strong>{{ stage.label }}</strong>
                  <small>{{ stage.hint }}</small>
                </div>
              </div>
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
                  class="day-card"
                  :class="{ 'day-card-active': detailDayNumber === day.day }"
                  @click="activateDay(day.day)"
                >
                  <div class="day-card-shell">
                    <div class="day-card-head">
                      <div>
                        <h3>{{ day.title }}</h3>
                        <div class="day-date-chip">{{ getDateForDay(day.day) }}</div>
                        <p>{{ day.theme }} · 景点 {{ day.attractions.length }} 个 · 概览 {{ day.distanceKm }} km</p>
                      </div>
                      <el-tag round type="success">{{ getDayRouteMeta(day.day)?.distanceText || '预估中' }}</el-tag>
                    </div>

                    <div class="day-metrics">
                      <div class="metric-card">
                        <strong>{{ getDayPlayHours(day) }}</strong>
                        <span>游玩时长</span>
                      </div>
                      <div class="metric-card">
                        <strong>{{ getDayRouteMeta(day.day)?.durationText || '预估中' }}</strong>
                        <span>换乘时长</span>
                      </div>
                      <div class="metric-card">
                        <strong>{{ getDayTransportSummary(day.day) }}</strong>
                        <span>主要方式</span>
                      </div>
                    </div>

                    <div class="route-list">
                      <button
                        v-for="(attraction, attractionIndex) in day.attractions"
                        :key="`${day.day}-${attraction.id ?? attractionIndex}`"
                        class="route-stop"
                        @click.stop="focusAttraction(day.day, attraction)"
                      >
                        <span>{{ attractionIndex + 1 }}. {{ attraction.name }}</span>
                        <small>{{ displayCategory(attraction.category) }} · 建议 {{ attraction.suggestedHours }} 小时</small>
                      </button>
                    </div>
                  </div>
                </el-card>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="还没有可展示的行程结果" />
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
                  <strong>{{ activeDayRouteMeta?.distanceText || '预估中' }}</strong>
                  <span>换乘距离</span>
                </div>
              </div>

              <div v-if="activeDayRouteMeta" class="strategy-banner">
                推荐从 {{ formatClock(activeDaySchedule.startMinutes) }} 开始，当天换乘约 {{ activeDayRouteMeta.durationText }}。
              </div>

              <div class="strategy-stack">
                <template v-for="item in activeDaySchedule.items" :key="item.key">
                  <article v-if="item.type === 'spot'" class="detail-card spot-detail-card">
                    <div v-if="resolveAttractionImage(item.attraction, item.key)" class="spot-image-wrap">
                      <img
                        :src="resolveAttractionImage(item.attraction, item.key)"
                        :alt="item.attraction.name"
                        class="spot-image"
                        @error="markImageBroken(item.key)"
                      />
                    </div>
                    <div v-else class="spot-image-placeholder">
                      {{ item.attraction.name.slice(0, 1) }}
                    </div>

                    <div class="spot-detail-body">
                      <div class="spot-detail-head">
                        <div>
                          <h3>{{ item.order }}. {{ item.attraction.name }}</h3>
                          <p>{{ item.startText }} - {{ item.endText }} · {{ displayCategory(item.attraction.category) }}</p>
                        </div>
                        <el-rate :model-value="item.attraction.rating" disabled size="small" />
                      </div>

                      <p>{{ item.attraction.description }}</p>

                      <div class="tag-row">
                        <el-tag v-for="tag in item.attraction.tags || []" :key="`${item.key}-${tag}`" size="small" effect="plain">
                          {{ displayTag(tag) }}
                        </el-tag>
                      </div>
                    </div>
                  </article>

                  <article v-else class="detail-card transfer-card">
                    <div class="transfer-head">
                      <div>
                        <h3>{{ item.leg.fromName }} → {{ item.leg.toName }}</h3>
                        <p>{{ item.startText }} - {{ item.endText }} · {{ modeLabel(item.leg.mode) }}</p>
                      </div>
                      <div class="transfer-badges">
                        <el-tag size="small" type="success">{{ formatDistance(item.leg.distanceMeters) }}</el-tag>
                        <el-tag size="small" type="info">{{ formatDuration(item.leg.durationSeconds) }}</el-tag>
                        <el-tag v-if="item.leg.subway" size="small" type="warning">地铁</el-tag>
                      </div>
                    </div>

                    <div class="transfer-brief">
                      <span>{{ summarizeLeg(item.leg) }}</span>
                      <el-button text type="primary" @click="toggleTransferDetail(item.key)">
                        {{ expandedTransfers[item.key] ? '收起详情' : '查看详情' }}
                      </el-button>
                    </div>

                    <div v-if="expandedTransfers[item.key]" class="transfer-steps">
                      <div v-for="(step, stepIndex) in item.leg.steps || []" :key="`${item.key}-${stepIndex}`" class="transfer-step">
                        <strong>{{ stepIndex + 1 }}.</strong>
                        <span>{{ step.instruction }}</span>
                      </div>
                    </div>
                  </article>
                </template>
              </div>
            </template>

            <el-empty v-else description="选择某一天后，这里会显示当天的详细攻略" />
          </div>
        </section>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  createPlan,
  getDestinations,
  getMapConfig,
  getPlanRoutes,
  getSavedPlan,
  getSavedPlans,
  streamAiPlan
} from './api/travel'

const destinations = ref([])
const plan = ref(null)
const previewPlan = ref(null)
const routeData = ref(null)
const previewRouteData = ref(null)
const savedPlans = ref([])
const loading = ref(false)
const errorMessage = ref('')
const plannerDialogVisible = ref(false)
const planMode = ref('ai')
const mapConfig = ref(null)
const map = ref(null)
const mapReady = ref(false)
const mapOverlays = ref([])
const infoWindow = ref(null)
const supportedCitiesText = ref('')
const activeDayFilter = ref('all')
const mapTheme = ref('normal')
const previewMessage = ref('')
const previewProgress = ref(0)
const expandedTransfers = ref({})
const brokenImages = ref({})

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
  { label: '拍照', value: 'photography' },
  { label: '购物', value: 'shopping' },
  { label: '亲子', value: 'family' },
  { label: '博物馆', value: 'museum' }
]

const planModeOptions = [
  { label: 'AI 规划', value: 'ai' },
  { label: '普通规划', value: 'standard' }
]

const mapThemeOptions = [
  { label: '标准', value: 'normal' },
  { label: '浅色', value: 'whitesmoke' },
  { label: '清新', value: 'fresh' }
]

const dayPalette = ['#e76f51', '#2a9d8f', '#457b9d', '#f4a261', '#8d5fd3', '#ef476f', '#118ab2']
const today = new Date()
const defaultStartDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`

const form = reactive({
  city: '',
  startDate: defaultStartDate,
  endDate: defaultStartDate,
  days: 3,
  interests: ['culture', 'walk', 'food'],
  manualDays: []
})

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
  photography: '拍照',
  garden: '园林',
  museum: '博物馆',
  shopping: '购物',
  landmark: '地标',
  cityscape: '城市风光',
  family: '亲子',
  nightlife: '夜生活',
  park: '公园',
  wildlife: '野生动物',
  animal: '动物',
  viewpoint: '观景台',
  sight: '景点',
  fallback: '补充景点',
  preview: '预览'
}

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'short'
})

const parseDateString = (value) => {
  if (!value) return null
  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) return null
    return new Date(value.getFullYear(), value.getMonth(), value.getDate())
  }
  if (typeof value !== 'string') return null
  const matched = value.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!matched) return null
  const parsed = new Date(Number(matched[1]), Number(matched[2]) - 1, Number(matched[3]))
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

const toDateInputValue = (value) => {
  const parsed = parseDateString(value)
  if (!parsed) return defaultStartDate
  const year = parsed.getFullYear()
  const month = String(parsed.getMonth() + 1).padStart(2, '0')
  const day = String(parsed.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const shiftDate = (value, offsetDays) => {
  const parsed = parseDateString(value)
  if (!parsed) return defaultStartDate
  parsed.setDate(parsed.getDate() + offsetDays)
  return toDateInputValue(parsed)
}

const clampDays = (value) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return 1
  return Math.min(7, Math.max(1, Math.round(numeric)))
}

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
const displayTag = (tag) => categoryLabels[tag] || tag
const getDayColor = (dayNumber = 1) => dayPalette[(dayNumber - 1) % dayPalette.length]
const getMapStyle = (theme) => `amap://styles/${theme}`

const isPreviewing = computed(() => loading.value && !!previewPlan.value)
const displayPlan = computed(() => (isPreviewing.value ? previewPlan.value : plan.value))
const displayRouteData = computed(() => (isPreviewing.value ? previewRouteData.value : routeData.value))
const currentModeLabel = computed(() => (planMode.value === 'ai' ? 'AI 规划' : '普通规划'))
const selectedInterestLabels = computed(() =>
  interests.filter((interest) => form.interests.includes(interest.value)).map((interest) => interest.label)
)
const manualDaysFilledCount = computed(() => form.manualDays.filter((day) => day.attractionsText?.trim()).length)
const plannerHighlights = computed(() => [
  { label: '行程跨度', value: `${form.days} 天` },
  { label: '兴趣标签', value: `${form.interests.length} 项` },
  { label: '普通规划', value: `${manualDaysFilledCount.value} 天已填写` }
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

const activeDetailDay = computed(() =>
  displayPlan.value?.itinerary?.find((day) => day.day === detailDayNumber.value) || null
)

const activeDetailRoute = computed(() =>
  displayRouteData.value?.days?.find((day) => day.day === detailDayNumber.value) || null
)

const formatDistance = (distanceMeters = 0) =>
  distanceMeters >= 1000 ? `${(distanceMeters / 1000).toFixed(1)} 公里` : `${Math.round(distanceMeters)} 米`

const formatDuration = (durationSeconds = 0) => {
  const safeSeconds = Math.max(0, Math.round(durationSeconds))
  const hours = Math.floor(safeSeconds / 3600)
  const minutes = Math.max(1, Math.round((safeSeconds % 3600) / 60))
  if (hours > 0) return `${hours} 小时 ${minutes} 分钟`
  return `${minutes} 分钟`
}

const getDayPlayHours = (day) => `${(day?.attractions || []).reduce((sum, attraction) => sum + (attraction.suggestedHours || 0), 0)} 小时`

const getDayRouteMeta = (dayNumber) => {
  const route = displayRouteData.value?.days?.find((item) => item.day === dayNumber)
  if (!route) return null
  return {
    distanceText: formatDistance(route.distanceMeters),
    durationText: formatDuration(route.durationSeconds)
  }
}

const getDayTransportSummary = (dayNumber) => {
  const route = displayRouteData.value?.days?.find((item) => item.day === dayNumber)
  if (!route?.legs?.length) return isPreviewing.value ? '预估中' : '步行'
  const modes = route.legs.map((leg) => modeLabel(leg.mode))
  return Array.from(new Set(modes)).slice(0, 2).join(' / ')
}

const activeDayRouteMeta = computed(() => getDayRouteMeta(detailDayNumber.value))

const activeRouteSummary = computed(() => {
  const routes = displayRouteData.value?.days?.filter((route) => activeDayFilter.value === 'all' || route.day === activeDayFilter.value) || []
  if (!routes.length) return null
  return {
    distanceText: formatDistance(routes.reduce((sum, route) => sum + (route.distanceMeters || 0), 0)),
    durationText: formatDuration(routes.reduce((sum, route) => sum + (route.durationSeconds || 0), 0))
  }
})

const formatClock = (minutes) => {
  const safeMinutes = Math.max(0, Math.round(minutes))
  const hour = Math.floor(safeMinutes / 60)
  const minute = safeMinutes % 60
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}

const formatProgress = (percentage) => `${Math.round(percentage)}%`
const getDateForDay = (dayNumber) => formatDateLabel(shiftDate(form.startDate, Math.max(0, dayNumber - 1)))

const thinkingStages = [
  { key: 'draft', label: '构思路线', hint: '先安排每天主题和节奏', threshold: 5 },
  { key: 'spots', label: '补充景点', hint: '逐个写入当天候选景点', threshold: 30 },
  { key: 'transfers', label: '整理换乘', hint: '补足景点之间的连接关系', threshold: 65 },
  { key: 'finalize', label: '收束结果', hint: '准备正式路线和最终输出', threshold: 90 }
]

const currentThinkingStage = computed(() => {
  const progress = previewProgress.value
  for (let index = thinkingStages.length - 1; index >= 0; index -= 1) {
    if (progress >= thinkingStages[index].threshold) return thinkingStages[index].key
  }
  return thinkingStages[0].key
})

const activeDaySchedule = computed(() => {
  const day = activeDetailDay.value
  const route = activeDetailRoute.value
  if (!day) return { startMinutes: 540, items: [] }

  const legs = route?.legs || []
  const items = []
  let cursor = 9 * 60

  day.attractions.forEach((attraction, index) => {
    const playMinutes = Math.max(60, (attraction.suggestedHours || 2) * 60)
    const startMinutes = cursor
    const endMinutes = cursor + playMinutes
    items.push({
      key: `spot-${day.day}-${index}`,
      type: 'spot',
      order: index + 1,
      attraction,
      startMinutes,
      endMinutes,
      startText: formatClock(startMinutes),
      endText: formatClock(endMinutes)
    })
    cursor = endMinutes

    const leg = legs[index]
    if (leg) {
      const transferMinutes = Math.max(10, Math.round((leg.durationSeconds || 0) / 60))
      items.push({
        key: `leg-${day.day}-${index}`,
        type: 'transfer',
        leg,
        startMinutes: cursor,
        endMinutes: cursor + transferMinutes,
        startText: formatClock(cursor),
        endText: formatClock(cursor + transferMinutes)
      })
      cursor += transferMinutes
    }
  })

  return { startMinutes: 9 * 60, items }
})

const modeLabel = (mode) => {
  const safeMode = mode || ''
  if (safeMode.includes('地铁')) return '地铁'
  if (safeMode.includes('公交')) return '公交'
  if (safeMode.includes('预览') || safeMode.includes('估算')) return '预估路线'
  return '步行'
}

const summarizeLeg = (leg) => {
  const line = leg.lineName ? `优先乘坐 ${leg.lineName}` : `建议以${modeLabel(leg.mode)}为主`
  return `${line}，全程约 ${formatDistance(leg.distanceMeters)}，耗时 ${formatDuration(leg.durationSeconds)}。`
}

const toggleTransferDetail = (key) => {
  expandedTransfers.value = {
    ...expandedTransfers.value,
    [key]: !expandedTransfers.value[key]
  }
}

const markImageBroken = (key) => {
  brokenImages.value = {
    ...brokenImages.value,
    [key]: true
  }
}

const resolveAttractionImage = (attraction, key) => {
  if (brokenImages.value[key]) return ''
  return attraction?.imageUrls?.[0] || ''
}

const queryDestinations = (queryString, callback) => {
  const keyword = queryString.trim().toLowerCase()
  const results = keyword
    ? destinations.value.filter((destination) =>
        destination.city.toLowerCase().includes(keyword) || destination.country.toLowerCase().includes(keyword)
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

const openPlannerDialog = (mode = planMode.value) => {
  planMode.value = mode
  plannerDialogVisible.value = true
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
}

const normalizeRequestError = (error) => {
  const rawMessage = error?.response?.data?.message || error?.message || '生成行程失败，请稍后重试。'
  if (String(rawMessage).toLowerCase().includes('timeout')) {
    return 'AI 生成时间较长，当前请求超时了。可以重新尝试，或者减少天数后再生成。'
  }
  return rawMessage
}

const buildPayload = () => ({
  city: form.city.trim(),
  startDate: form.startDate,
  endDate: form.endDate,
  days: form.days,
  interests: form.interests,
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
  return Number.isFinite(lng) && Number.isFinite(lat) && Math.abs(lng) <= 180 && Math.abs(lat) <= 90 && !(lng === 0 && lat === 0)
}

const findDestinationCenterFromCatalog = (cityName) => {
  const keyword = String(cityName || '').trim().toLowerCase()
  if (!keyword) return null
  const matched = destinations.value.find((destination) => destination.city?.trim().toLowerCase() === keyword)
  if (!matched || !hasValidCoordinates(matched)) return null
  return {
    longitude: Number(matched.longitude),
    latitude: Number(matched.latitude)
  }
}

const getPlanCenter = (planLike) => {
  if (hasValidCoordinates(planLike?.destination)) {
    return {
      longitude: Number(planLike.destination.longitude),
      latitude: Number(planLike.destination.latitude)
    }
  }
  return findDestinationCenterFromCatalog(planLike?.destination?.city || form.city)
}

const distanceBetweenKm = (pointA, pointB) => {
  if (!hasValidCoordinates(pointA) || !hasValidCoordinates(pointB)) return Number.POSITIVE_INFINITY
  const toRadians = (degrees) => (degrees * Math.PI) / 180
  const earthRadiusKm = 6371
  const dLat = toRadians(Number(pointB.latitude) - Number(pointA.latitude))
  const dLng = toRadians(Number(pointB.longitude) - Number(pointA.longitude))
  const lat1 = toRadians(Number(pointA.latitude))
  const lat2 = toRadians(Number(pointB.latitude))
  const haversine =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
  const arc = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
  return earthRadiusKm * arc
}

const isNearPlanCenter = (item, planLike, maxDistanceKm = 300) => {
  if (!hasValidCoordinates(item)) return false
  const center = getPlanCenter(planLike)
  if (!center) return true
  return distanceBetweenKm(item, center) <= maxDistanceKm
}

const buildPreviewRoutes = (preview) => {
  if (!preview?.itinerary?.length) return null

  return {
    planId: preview.planId || -1,
    days: preview.itinerary.map((day) => {
      const validAttractions = (day.attractions || []).filter((attraction) => isNearPlanCenter(attraction, preview, 300))
      const polyline = validAttractions.map((attraction) => ({
        longitude: attraction.longitude,
        latitude: attraction.latitude
      }))

      const legs = validAttractions.slice(0, -1).map((attraction, index) => ({
        fromIndex: index,
        toIndex: index + 1,
        fromName: attraction.name,
        toName: validAttractions[index + 1].name,
        mode: '预览路线',
        lineName: '',
        subway: false,
        distanceMeters: 0,
        durationSeconds: 0,
        polyline: [
          { longitude: attraction.longitude, latitude: attraction.latitude },
          { longitude: validAttractions[index + 1].longitude, latitude: validAttractions[index + 1].latitude }
        ],
        steps: []
      }))

      return {
        day: day.day,
        distanceMeters: 0,
        durationSeconds: 0,
        polyline,
        legs
      }
    })
  }
}

const loadRoutes = async (planId) => {
  routeData.value = await getPlanRoutes(planId)
}

const runPlanRequest = async (mode = planMode.value) => {
  if (!form.city.trim()) {
    errorMessage.value = '请先输入目的地。'
    return
  }

  syncDerivedDates()
  loading.value = true
  errorMessage.value = ''
  activeDayFilter.value = 'all'
  expandedTransfers.value = {}
  brokenImages.value = {}

  try {
    const payload = buildPayload()
    if (mode === 'ai') {
      clearPreview()
      await streamAiPlan(payload, {
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
          clearPreview()
          await loadRoutes(event.plan.planId)
          savedPlans.value = await getSavedPlans()
        },
        onError: (event) => {
          throw new Error(event.message || 'AI 流式生成失败')
        }
      })
    } else {
      clearPreview()
      plan.value = await createPlan(payload)
      await loadRoutes(plan.value.planId)
      savedPlans.value = await getSavedPlans()
    }

    planMode.value = mode
    plannerDialogVisible.value = false
  } catch (error) {
    if (!isPreviewing.value) {
      plan.value = null
      routeData.value = null
    }
    errorMessage.value = normalizeRequestError(error)
  } finally {
    loading.value = false
  }
}

const generateStandardPlan = async () => runPlanRequest('standard')
const generateAiPlan = async () => runPlanRequest('ai')

const loadSavedPlan = async (id) => {
  loading.value = true
  errorMessage.value = ''
  try {
    clearPreview()
    plan.value = await getSavedPlan(id)
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

  window._AMapSecurityConfig = {
    securityJsCode: mapConfig.value.securityJsCode
  }

  amapLoaderPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${mapConfig.value.webKey}&plugin=AMap.Scale,AMap.ToolBar`
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
    mapStyle: getMapStyle(mapTheme.value)
  })
  map.value.addControl(new AMap.Scale())
  map.value.addControl(new AMap.ToolBar())
  infoWindow.value = new AMap.InfoWindow({ offset: new AMap.Pixel(0, -24) })
  mapReady.value = true
}

const renderMap = async () => {
  if (!mapReady.value || !map.value) return
  clearMapOverlays()

  const currentPlan = displayPlan.value
  if (!currentPlan?.itinerary?.length) return

  const AMap = window.AMap
  const center = getPlanCenter(currentPlan)

  if (isPreviewing.value) {
    const previewOverlays = []

    if (center) {
      previewOverlays.push(new AMap.Marker({
        position: [center.longitude, center.latitude],
        content: '<div class="amap-stop-pin amap-stop-pin-preview"><span>目的地</span></div>',
        offset: new AMap.Pixel(-24, -40)
      }))
    }

    activeDays.value.forEach((day) => {
      const previewAttractions = (day.attractions || []).filter((attraction) => isNearPlanCenter(attraction, currentPlan, 240))
      const previewPath = []

      previewAttractions.forEach((attraction, index) => {
        const position = [attraction.longitude, attraction.latitude]
        previewPath.push(position)
        previewOverlays.push(new AMap.Marker({
          position,
          content: createMarkerContent(day.day, index + 1),
          offset: new AMap.Pixel(-18, -40)
        }))
      })

      if (previewPath.length > 1) {
        previewOverlays.push(new AMap.Polyline({
          path: previewPath,
          strokeColor: getDayColor(day.day),
          strokeWeight: 5,
          strokeOpacity: 0.72,
          lineJoin: 'round',
          lineCap: 'round',
          strokeStyle: 'dashed'
        }))
      }
    })

    if (previewOverlays.length) {
      map.value.add(previewOverlays)
      mapOverlays.value = previewOverlays
    }

    if (center) {
      map.value.setZoomAndCenter(11, [center.longitude, center.latitude])
    }
    return
  }

  const visibleDays = activeDays.value
  const routeDays = displayRouteData.value?.days || []
  const overlays = []
  const allPositions = []

  visibleDays.forEach((day) => {
    const color = getDayColor(day.day)
    const validAttractions = (day.attractions || []).filter((attraction) => isNearPlanCenter(attraction, currentPlan, 300))

    validAttractions.forEach((attraction, index) => {
      const position = [attraction.longitude, attraction.latitude]
      allPositions.push(position)
      overlays.push(new AMap.Marker({
        position,
        content: createMarkerContent(day.day, index + 1),
        offset: new AMap.Pixel(-18, -40)
      }))
    })

    const matchedRoute = routeDays.find((item) => item.day === day.day)
    const routePolyline = (matchedRoute?.polyline || [])
      .filter((point) => isNearPlanCenter(point, currentPlan, 300))
      .map((point) => [point.longitude, point.latitude])

    const fallbackPolyline = validAttractions.map((attraction) => [attraction.longitude, attraction.latitude])
    const positions = routePolyline.length > 1 ? routePolyline : fallbackPolyline

    if (positions.length > 1) {
      positions.forEach((position) => allPositions.push(position))
      overlays.push(new AMap.Polyline({
        path: positions,
        strokeColor: color,
        strokeWeight: 6,
        strokeOpacity: 0.78,
        lineJoin: 'round',
        lineCap: 'round',
        strokeStyle: routePolyline.length > 1 ? 'solid' : 'dashed'
      }))
    }
  })

  if (overlays.length) {
    map.value.add(overlays)
    mapOverlays.value = overlays
  }

  if (allPositions.length) {
    map.value.setFitView(overlays, false, [60, 60, 60, 60])
  } else if (center) {
    map.value.setZoomAndCenter(11, [center.longitude, center.latitude])
  }
}

const focusAttraction = async (dayNumber, attraction) => {
  activeDayFilter.value = dayNumber
  await nextTick()
  if (!map.value || !hasValidCoordinates(attraction)) return

  const position = [attraction.longitude, attraction.latitude]
  map.value.setZoomAndCenter(15, position)
  infoWindow.value?.setContent(`
    <div class="amap-info-window">
      <strong>${attraction.name}</strong><br/>
      ${displayCategory(attraction.category)} · 建议 ${attraction.suggestedHours} 小时
    </div>
  `)
  infoWindow.value?.open(map.value, position)
}

onMounted(async () => {
  syncDerivedDates()
  try {
    mapConfig.value = await getMapConfig()
    await initMap()
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  }

  try {
    destinations.value = await getDestinations()
    supportedCitiesText.value = destinations.value.map((destination) => destination.city).join('、')
    if (!form.city) {
      form.city = destinations.value[0]?.city || ''
    }
    savedPlans.value = await getSavedPlans()
  } catch (error) {
    errorMessage.value = normalizeRequestError(error)
  }
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
</script>
