<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ -->
<script setup>
import {onMounted,reactive,ref} from 'vue'
import {request} from '../api'

const balances=ref([]),movements=ref([]),counts=ref([]),error=ref('')
const stock=reactive({sku:'SKU-DEMO-01',warehouse:'WH-SH',openingQuantity:100})
const movement=reactive({referenceNo:'SO-'+Date.now(),type:'SALES_ISSUE',sku:'SKU-DEMO-01',warehouse:'WH-SH',quantity:1})
const countForm=reactive({countNo:'COUNT-'+Date.now(),sku:'SKU-DEMO-01',warehouse:'WH-SH',countedQuantity:100})

async function load(){
  [balances.value,movements.value,counts.value]=await Promise.all([
    request('/api/core/psi/balances'),request('/api/core/psi/movements'),request('/api/core/psi/counts')
  ])
}
async function run(fn){try{error.value='';await fn();await load()}catch(e){error.value=e.message}}
async function create(){await run(()=>request('/api/core/psi/balances',{method:'POST',body:JSON.stringify(stock)}))}
async function post(){await run(()=>request('/api/core/psi/movements',{method:'POST',body:JSON.stringify({...movement,idempotencyKey:crypto.randomUUID()})}))}
async function createCount(){await run(async()=>{await request('/api/core/psi/counts',{method:'POST',body:JSON.stringify(countForm)});countForm.countNo='COUNT-'+Date.now()})}
async function countAction(item,action){
  await run(()=>action==='submit'
    ?request(`/api/core/psi/counts/${item.id}/submit`,{method:'POST'})
    :request(`/api/admin/core/psi/counts/${item.id}/review`,{method:'POST',body:JSON.stringify({decision:action,remark:action==='APPROVE'?'盘点差异复核通过':'盘点数据退回重查'})}))
}
onMounted(load)
</script>

<template>
  <section class="core-title"><div><span>INVENTORY CONTROL</span><h3>库存过账与盘点控制台</h3><p>库存余额、预留、调账与盘点复核统一进入可追溯台账，盘点期间库存变化会强制重新清点。</p></div></section>
  <p v-if="error" class="core-error">{{error}}</p>
  <section class="core-grid">
    <form @submit.prevent="create"><h4>建立库存台账</h4><label>SKU<input v-model="stock.sku"></label><label>仓库<input v-model="stock.warehouse"></label><label>期初数量<input v-model.number="stock.openingQuantity" type="number" min="0"></label><button>保存台账</button></form>
    <form @submit.prevent="post"><h4>库存业务过账</h4><label>动作<select v-model="movement.type"><option>PURCHASE_RECEIPT</option><option>SALES_ISSUE</option><option>RESERVE</option><option>RELEASE</option><option>ADJUSTMENT_IN</option><option>ADJUSTMENT_OUT</option></select></label><label>SKU<input v-model="movement.sku"></label><label>仓库<input v-model="movement.warehouse"></label><label>数量<input v-model.number="movement.quantity" type="number" min="1"></label><button>执行过账</button></form>
    <form class="wide" @submit.prevent="createCount"><h4>发起库存盘点</h4><label>盘点单号<input v-model="countForm.countNo"></label><label>SKU<input v-model="countForm.sku"></label><label>仓库<input v-model="countForm.warehouse"></label><label>实盘数量<input v-model.number="countForm.countedQuantity" type="number" min="0"></label><button>保存盘点单</button></form>
  </section>
  <section class="core-panel"><h4>库存余额</h4><table><thead><tr><th>SKU</th><th>仓库</th><th>在手</th><th>预留</th><th>可用</th></tr></thead><tbody><tr v-for="b in balances" :key="b.id"><td>{{b.sku}}</td><td>{{b.warehouse}}</td><td>{{b.onHand}}</td><td>{{b.reserved}}</td><td><b>{{b.available}}</b></td></tr></tbody></table></section>
  <section class="core-panel"><h4>盘点审批</h4><table><thead><tr><th>盘点单</th><th>SKU / 仓库</th><th>账面</th><th>实盘</th><th>差异</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="c in counts" :key="c.id"><td><code>{{c.countNo}}</code></td><td>{{c.sku}} / {{c.warehouse}}</td><td>{{c.bookQuantity}}</td><td>{{c.countedQuantity}}</td><td :class="{variance:c.variance!==0}">{{c.variance>0?'+':''}}{{c.variance}}</td><td>{{c.status}}</td><td><button v-if="c.status==='DRAFT'" @click="countAction(c,'submit')">提交复核</button><template v-if="c.status==='PENDING_REVIEW'"><button @click="countAction(c,'APPROVE')">批准调账</button><button class="secondary" @click="countAction(c,'REJECT')">退回</button></template></td></tr></tbody></table></section>
  <section class="core-panel"><h4>最近过账流水</h4><div v-for="m in movements.slice(0,8)" :key="m.id" class="core-row"><b>{{m.type}}</b><span>{{m.sku}} · {{m.warehouse}}</span><strong>{{m.beforeOnHand}} → {{m.afterOnHand}}</strong></div></section>
</template>

<style scoped>
.core-title,.core-panel,.core-grid form{background:#fff;border:1px solid #dce2df;padding:24px;margin-top:20px}.core-title span{font-size:11px;letter-spacing:.15em;color:#a56c24}.core-title h3{margin:6px 0}.core-title p{margin:0;color:#68777c}.core-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.core-grid form{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.core-grid .wide{grid-column:1/-1;grid-template-columns:repeat(4,1fr)}.core-grid h4{grid-column:1/-1;margin:0}.core-grid label{display:grid;gap:5px;font-size:12px;color:#68777c}.core-grid input,.core-grid select{padding:9px;border:1px solid #ccd6d2}.core-grid button,.core-panel button{padding:10px;border:0;background:#235a74;color:#fff}.core-grid form:not(.wide)>button{grid-column:1/-1}.core-panel table{width:100%;border-collapse:collapse}.core-panel th,.core-panel td{text-align:left;padding:11px;border-top:1px solid #edf0ef}.core-panel .secondary{background:#7d5a52;margin-left:6px}.variance{color:#a03f38;font-weight:700}.core-row{display:grid;grid-template-columns:170px 1fr 140px;padding:12px 0;border-top:1px solid #edf0ef}.core-error{color:#a03f38}@media(max-width:800px){.core-grid,.core-grid .wide{grid-template-columns:1fr}.core-grid .wide{grid-column:auto}.core-grid h4,.core-grid button{grid-column:auto}.core-panel{overflow:auto}}
</style>
