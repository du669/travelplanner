import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './assets/styles.css'
import EditorApp from './EditorApp.vue'

createApp(EditorApp).use(ElementPlus).mount('#app')
