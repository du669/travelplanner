<template>
  <el-container class="app-shell">
    <el-aside width="360px" class="planner-panel">
      <div class="brand">
        <span class="brand-mark">TP</span>
        <div>
          <h1>旅行计划规划</h1>
          <p>城市、兴趣、地图与行程联动</p>
        </div>
      </div>

      <el-form label-position="top" class="planner-form">
        <el-form-item label="目的地">
          <el-select v-model="form.city" class="full-width" @change="refreshPlan">
            <el-option
              v-for="destination in destinations"
              :key="destination.city"
              :label="`${destination.city} · ${destination.country}`"
              :value="destination.city"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="出发日期">
          <el-date-picker
            v-model="form.startDate"
            class="full-width"
            type="date"
            value-format="YYYY-MM-DD"
            @change="refreshPlan"
          />
        </el-form-item>

        <el-form-item label="旅行天数">
          <el-slider v-model="form.days" :min="1" :max="7" show-input @change="refreshPlan" />
        </el-form-item>

        <el-form-item label="兴趣偏好">
          <el-checkbox-group v-model="form.interests" @change="refreshPlan">
            <el-checkbox-button v-for="interest in interests" :key="interest.value" :value="interest.value">
              {{ interest.label }}
            </el-checkbox-button>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <el-button type="primary" class="full-width" :loading="loading" @click="refreshPlan">
        重新生成路线
      </el-button>

      <section v-if="plan" class="destination-summary">
        <h2>{{ plan.destination.city }}</h2>
        <p>{{ plan.destination.summary }}</p>
        <el-tag effect="plain">适合季节：{{ plan.destination.bestSeason }}</el-tag>
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
              <span v-if="plan">{{ plan.startDate }} 起 · {{ plan.days }} 天</span>
            </div>

            <el-timeline v-if="plan">
              <el-timeline-item
                v-for="day in plan.itinerary"
                :key="day.day"
                :timestamp="`Day ${day.day}`"
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
                      <small>{{ attraction.category }} · {{ attraction.suggestedHours }}h</small>
                    </button>
                  </div>
                </el-card>
              </el-timeline-item>
            </el-timeline>
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
              <div class="tag-row">
                <el-tag v-for="tag in attraction.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
              </div>
            </el-card>
          </div>
        </section>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import L from 'leaflet'
import { createPlan, getAttractions, getDestinations } from './api/travel'

const destinations = ref([])
const attractions = ref([])
const plan = ref(null)
const loading = ref(false)
const map = ref(null)
const markers = ref([])
const routeLine = ref(null)

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
      .bindPopup(`<strong>${attraction.name}</strong><br>${attraction.category}`)
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

const refreshPlan = async () => {
  if (!form.city) return
  loading.value = true
  try {
    const payload = {
      city: form.city,
      startDate: form.startDate,
      days: form.days,
      interests: form.interests
    }
    const [newPlan, newAttractions] = await Promise.all([
      createPlan(payload),
      getAttractions({ city: form.city, interests: form.interests })
    ])
    plan.value = newPlan
    attractions.value = newAttractions
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
  form.city = destinations.value[0]?.city || ''
  await refreshPlan()
})

watch(plan, renderMap)
</script>
