// Cloudflare Pages Function: 淘宝短链/分享文本解析代理
// 用法: https://tktool.pages.dev/api/proxy?url=<target_url>
//
// 改进点（对比旧版）：
//  1. 自动 UA 轮换池：依次尝试多组真实移动端 UA，优先拿到能 302 / 含商品 id 的响应
//  2. 重定向跟随（redirect: 'follow'）保留，并取跟随后的最终地址 finalUrl
//  3. 服务端直接抽取商品 id（itemIds / id / topIds / /i数字.htm），随响应一并返回 ids[]，
//     前端不必再赌 body 里的 var url 是否为空（淘宝短链多为 JS 动态跳转，服务端拿到的 body 里 var url=''）
//  4. 单次请求加 9s 超时，避免后端挂起

const UA_POOL = [
  'Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
  'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1',
  'Mozilla/5.0 (Linux; Android 9; HUAWEI VOG-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.110 Mobile Safari/537.36',
  'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 GSB/ALiApp(Taobao/10.65.0)',
  'Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36'
];

// 从任意文本中抽取商品 id（支持多 id 逗号拼接、自动去重）
function extractIds(text) {
  if (!text) return [];
  const ids = new Set();
  let m;
  const reItem = /[?&]itemIds=([^&\s'"<>]+)/g;
  while ((m = reItem.exec(text))) {
    m[1].split(',').forEach(x => { const t = x.trim(); if (/^\d{7,}$/.test(t)) ids.add(t); });
  }
  const reId = /[?&]id=(\d{7,})/g;    while ((m = reId.exec(text))) ids.add(m[1]);
  const reTop = /topIds=(\d+)/g;      while ((m = reTop.exec(text))) ids.add(m[1]);
  const reI = /\/i(\d+)\.htm/g;       while ((m = reI.exec(text))) ids.add(m[1]);
  const reHtml = /\/(\d{7,})\.html?/g;while ((m = reHtml.exec(text))) ids.add(m[1]);
  return [...ids];
}

const CORS = { 'Access-Control-Allow-Origin': '*', 'Content-Type': 'application/json' };

export async function onRequest(context) {
  const { request } = context;
  const url = new URL(request.url);
  const target = url.searchParams.get('url');

  if (!target) {
    return new Response(JSON.stringify({ error: 'Missing url parameter' }), { status: 400, headers: CORS });
  }

  let best = { url: '', status: 0, body: '', ids: [] };
  let lastErr = '';

  for (const ua of UA_POOL) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 9000);
    try {
      const resp = await fetch(target, {
        headers: { 'User-Agent': ua, 'Accept': '*/*' },
        redirect: 'follow',
        signal: ctrl.signal
      });
      clearTimeout(timer);

      const finalUrl = resp.url || target;
      const body = await resp.text();
      const ids = extractIds(finalUrl + '\n' + body);

      // 任一 UA 直接抽到 id 即视为成功，立即返回（不再试后续 UA）
      if (ids.length && best.ids.length === 0) {
        best = { url: finalUrl, status: resp.status, body, ids };
        break;
      }
      // 否则保留“响应最完整”的那次（最终地址发生变化，或 body 更长）
      if (finalUrl !== target || body.length > best.body.length) {
        best = { url: finalUrl, status: resp.status, body, ids: best.ids.length ? best.ids : ids };
      }
    } catch (e) {
      clearTimeout(timer);
      lastErr = e.message;
    }
  }

  if (best.status === 0 && best.body === '') {
    return new Response(JSON.stringify({ error: lastErr || 'all UA requests failed' }), { status: 502, headers: CORS });
  }

  return new Response(JSON.stringify({
    url: best.url,
    status: best.status,
    body: best.body,
    ids: best.ids
  }), { headers: CORS });
}
