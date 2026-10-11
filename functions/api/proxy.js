// Cloudflare Pages Function: 淘宝短链/分享文本解析代理
// 用法: https://tktool.pages.dev/api/proxy?url=<target_url>
//
// 优化点（针对 e.tb.cn / m.tb.cn 短链 + tk 淘口令经常解析失败）：
//  1. UA 轮换池：依次尝试真实移动端 UA（含淘宝 App UA），优先拿到能 302 / 含商品 id 的响应
//  2. 重定向跟随（redirect: 'follow'），取最终地址 finalUrl
//  3. 服务端直接抽取商品 id，覆盖更多形态：
//     - itemIds= / id= / topIds= / /i数字.htm / 数字.html
//     - 淘宝 H5 落地页常见的 var url='https://item.taobao.com/item.htm?id=...'
//     - 响应体 JSON 里的 "itemId"
//     - 响应体中任意 item.taobao.com/item.htm?id= 链接
//  4. 二次跟随：若首轮未抽到 id 但 body 含 item.taobao.com/item.htm 链接，再追一层拿到真实商品页抽 id
//     （e.tb.cn 常先 302 到 H5 落地页，落地页再用 JS 跳转到真实商品页）
//  5. 单次请求 9s 超时；附 Referer 提升短链跟随成功率；返回 tk 便于排查

const UA_POOL = [
  'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1',
  'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 GSB/ALiApp(Taobao/10.65.0)',
  'Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36',
  'Mozilla/5.0 (Linux; Android 9; HUAWEI VOG-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.110 Mobile Safari/537.36',
  'Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
];

// 从任意文本中抽取商品 id（支持多 id 逗号拼接、自动去重）
function extractIds(text) {
  if (!text) return [];
  const ids = new Set();
  const push = (s) => {
    if (!s) return;
    let m;
    const reItem = /[?&]itemIds=([^&\s'"<>]+)/g;
    while ((m = reItem.exec(s))) m[1].split(',').forEach(x => { const t = x.trim(); if (/^\d{7,}$/.test(t)) ids.add(t); });
    const reId = /[?&]id=(\d{7,})/g;        while ((m = reId.exec(s))) ids.add(m[1]);
    const reTop = /topIds=(\d+)/g;           while ((m = reTop.exec(s))) ids.add(m[1]);
    const reI = /\/i(\d+)\.htm/g;            while ((m = reI.exec(s))) ids.add(m[1]);
    const reHtml = /\/(\d{7,})\.html?/g;     while ((m = reHtml.exec(s))) ids.add(m[1]);
    // 淘宝 H5 落地页常见：var url='https://item.taobao.com/item.htm?id=xxx'
    const reVar = /var\s+url\s*=\s*['"]([^'"]+)['"]/g; while ((m = reVar.exec(s))) push(m[1]);
    // 响应体 JSON 里的 itemId
    const reJson = /"itemId"\s*:\s*"?(\d{7,})/g; while ((m = reJson.exec(s))) ids.add(m[1]);
    // 响应体中任意 item.taobao.com/item.htm?id= 链接
    const reLink = /item\.taobao\.com\/item\.htm[^'"]*?id=(\d{7,})/gi; while ((m = reLink.exec(s))) ids.add(m[1]);
  };
  push(text);
  return [...ids];
}

async function tryFetch(u, ua, referer) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 9000);
  try {
    const resp = await fetch(u, {
      headers: { 'User-Agent': ua, 'Accept': '*/*', 'Referer': referer },
      redirect: 'follow',
      signal: ctrl.signal
    });
    clearTimeout(timer);
    const finalUrl = resp.url || u;
    const body = await resp.text();
    return { finalUrl, status: resp.status, body };
  } catch (e) {
    clearTimeout(timer);
    return { error: e.message };
  }
}

const CORS = { 'Access-Control-Allow-Origin': '*', 'Content-Type': 'application/json' };

export async function onRequest(context) {
  const { request } = context;
  const url = new URL(request.url);
  const target = url.searchParams.get('url');

  if (!target) {
    return new Response(JSON.stringify({ error: 'Missing url parameter' }), { status: 400, headers: CORS });
  }

  let tk = '';
  try { tk = new URL(target).searchParams.get('tk') || ''; } catch (e) {}
  const referer = 'https://tb.cn/';

  let best = { url: '', status: 0, body: '', ids: [] };
  let lastErr = '';

  for (const ua of UA_POOL) {
    const r1 = await tryFetch(target, ua, referer);
    if (r1.error) { lastErr = r1.error; continue; }

    let ids = extractIds(r1.finalUrl + '\n' + r1.body);

    // 二次跟随：首轮没抽到 id，但 body 里含 item.taobao.com/item.htm 链接 → 追到真实商品页再抽
    if (ids.length === 0) {
      const mm = r1.body.match(/item\.taobao\.com\/item\.htm[^'"]*/i);
      if (mm) {
        const next = mm[0].startsWith('http') ? mm[0] : 'https://' + mm[0];
        const r2 = await tryFetch(next, ua, referer);
        if (!r2.error) {
          ids = extractIds(r2.finalUrl + '\n' + r2.body);
        }
      }
    }

    if (ids.length) {
      best = { url: r1.finalUrl, status: r1.status, body: r1.body, ids };
      break;
    }
    // 保留响应最完整的一次（最终地址有变化，或 body 更长）
    if (r1.finalUrl !== target || r1.body.length > best.body.length) {
      best = { url: r1.finalUrl, status: r1.status, body: r1.body, ids: best.ids.length ? best.ids : ids };
    }
  }

  if (best.status === 0 && best.body === '') {
    return new Response(JSON.stringify({ error: lastErr || 'all UA requests failed', tk }), { status: 502, headers: CORS });
  }

  return new Response(JSON.stringify({
    url: best.url,
    status: best.status,
    body: best.body,
    ids: best.ids,
    tk
  }), { headers: CORS });
}
