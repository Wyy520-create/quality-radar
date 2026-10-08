import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { LineChart, Line, ResponsiveContainer, Tooltip } from 'recharts';
import './styles.css';

const projectId = '00000000-0000-0000-0000-000000000001';
type Dashboard = { latest: Record<string, unknown>; trend: Array<{ time: string; passed: number; total: number }>; topFailures: Array<{ type: string; occurrences: number }> };

function App() {
  const [tab, setTab] = useState<'overview'|'risk'|'import'|'gate'>('overview');
  const [gate, setGate] = useState<Record<string, any> | null>(null);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [diff, setDiff] = useState('+++ b/src/payment/PaymentService.java\n+public void capture() {}\n');
  const [assessment, setAssessment] = useState<Record<string, any> | null>(null);
  const [message, setMessage] = useState('');
  const load = () => fetch(`/api/v1/projects/${projectId}/dashboard`).then(r => r.json()).then(setDashboard).catch(() => setMessage('后端暂未连接，请先按 README 启动 Docker Compose。'));
  useEffect(() => { void load(); }, []);
  const assess = async () => { const r = await fetch(`/api/v1/projects/${projectId}/risk-assessments`, { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({diff}) }); setAssessment(await r.json()); };
  const runGate = async () => { if (!assessment?.id || !latest.id) return; const r = await fetch(`/api/v1/projects/${projectId}/gate-evaluations`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({testRunId:latest.id,riskAssessmentId:assessment.id})}); setGate(await r.json()); };
  const upload = async (file?: File) => { if (!file) return; const data = new FormData(); data.append('report', file); data.append('branch','main'); const r = await fetch(`/api/v1/projects/${projectId}/test-runs:import`, {method:'POST', body:data}); const body = await r.json(); setMessage(r.ok ? `已导入 ${body.total} 条用例：${body.passed} 通过，${body.failed + body.errored} 未通过。` : body.message || '导入失败'); load(); };
  const latest = dashboard?.latest ?? {};
  return <main><aside><div className="brand"><i/>QUALITY<br/><b>RADAR</b></div><p>持续质量决策平台</p>{[['overview','质量概览'],['risk','变更风险'],['gate','质量门禁'],['import','报告导入']].map(([id,label])=><button className={tab===id?'active':''} onClick={()=>setTab(id as typeof tab)} key={id}>{label}</button>)}<footer>DEMO MODE · v1.0</footer></aside><section><header><div><span>PROJECT / SHOP-DEMO</span><h1>{tab==='overview'?'质量脉冲':tab==='risk'?'风险评估':tab==='gate'?'质量门禁':'报告摄入'}</h1></div><div className="status"><em/> SYSTEM NOMINAL</div></header>{message&&<div className="notice">{message}</div>}
  {tab==='overview' && <><div className="grid"><Metric label="最近执行用例" value={String(latest.total ?? '—')} hint="JUnit XML 导入"/><Metric label="通过率" value={latest.total ? `${Math.round(Number(latest.passed)/Number(latest.total)*100)}%` : '—'} hint="门禁阈值 ≥ 95%"/><Metric label="未通过" value={String((Number(latest.failed||0)+Number(latest.errored||0)) || '—')} hint="失败 + 执行错误"/></div><div className="panel chart"><div><span>14 次执行通过趋势</span><h2>Regression health</h2></div><ResponsiveContainer width="100%" height={210}><LineChart data={dashboard?.trend ?? []}><Tooltip/><Line type="monotone" dataKey="passed" stroke="#72f5bd" strokeWidth={3}/><Line type="monotone" dataKey="total" stroke="#365BFF" strokeWidth={2}/></LineChart></ResponsiveContainer></div><div className="panel"><span>失败指纹 / FLAKY WATCH</span>{dashboard?.topFailures?.length ? dashboard.topFailures.map((f,i)=><div className="failure" key={i}><b>{f.type || 'AssertionError'}</b><small>{f.occurrences} 次出现</small></div>) : <p>导入 JUnit 报告后，平台会聚合失败指纹。</p>}</div></>}
  {tab==='risk' && <div className="split"><div className="panel"><span>UNIFIED DIFF</span><textarea value={diff} onChange={e=>setDiff(e.target.value)}/><button className="primary" onClick={assess}>计算风险与最小回归集</button></div>{assessment && <div className="panel result"><div className={`score ${assessment.level}`}>{assessment.score}<small>/100 · {assessment.level}</small></div><h2>可解释评分</h2>{assessment.factors.map((f:any,i:number)=><p key={i}><b>+{f.points}</b> {f.rule}<small>{f.detail}</small></p>)}<h3>推荐回归</h3>{assessment.recommendations.map((x:any)=><div className="recommendation" key={x.testKey}><b>{x.displayName}</b><small>{x.testKey} · {x.estimatedSeconds}s</small></div>)}</div>}</div>}
  {tab==='gate' && <div className="panel uploader"><span>RELEASE QUALITY GATE</span><h2>{gate ? (gate.status === 'PASSED' ? '允许发布' : '阻断发布') : '将测试执行与风险评估合并为发布结论'}</h2><p>先在“报告导入”生成最近一次执行，再在“变更风险”计算本次 diff。门禁会校验执行错误、失败率和风险上下文。</p><button className="primary" disabled={!assessment?.id || !latest.id} onClick={runGate}>执行质量门禁</button>{gate && <div className="gate-result"><b className={gate.status}>{gate.status}</b>{gate.violations?.length ? gate.violations.map((v:string,i:number)=><p key={i}>{v}</p>) : <p>所有门禁规则均满足。</p>}</div>}{!assessment?.id && <small>尚未计算风险评估。</small>}</div>}
  {tab==='import' && <div className="panel uploader"><span>JUnit XML REPORT</span><h2>导入一次真实测试执行</h2><p>支持常见 testsuites / testsuite / testcase 结构，服务端安全禁用 DTD 与外部实体。</p><label><input type="file" accept=".xml,text/xml" onChange={e=>upload(e.target.files?.[0])}/>选择 XML 文件</label><small>上限 10MB；原始文件不保存，仅持久化解析结果和 SHA-256。</small></div>}
  </section></main>;
}
function Metric({label,value,hint}:{label:string;value:string;hint:string}){return <div className="metric"><span>{label}</span><strong>{value}</strong><small>{hint}</small></div>}
createRoot(document.getElementById('root')!).render(<App/>);
