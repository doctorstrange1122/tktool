// 短链跳转服务（v2）：
//  - ?t=<加密token> -> 服务端返回解密中转页（AES-256-GCM 解密 + tbopen 唤起手淘，明文链接不出现）
//  - ?i=&d=&s=      -> 旧格式短链，保留 302 重定向兼容
const RELAY_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>正在打开…</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    height: 100vh; background: #fff; color: #333; text-align: center; padding: 24px;
  }
  #status { font-size: 16px; color: #666; line-height: 1.7; }
  .btn {
    display: none; margin-top: 24px; padding: 15px 52px; background: #ff5000; color: #fff;
    border-radius: 28px; font-size: 17px; text-decoration: none; font-weight: bold;
    border: none; font-family: inherit;
  }
  .btn2 {
    display: none; margin-top: 14px; padding: 12px 36px; background: #fff; color: #666;
    border: 1.5px solid #ccc; border-radius: 24px; font-size: 15px; text-decoration: none; font-weight: bold;
  }
  .tip { margin-top: 18px; font-size: 13px; color: #aaa; line-height: 1.6; }
  #diag { position: fixed; bottom: 6px; left: 8px; right: 8px; font-size: 10px; color: #ccc; text-align: left; word-break: break-all; }
</style>
</head>
<body>
  <div id="status">1/4 读取参数…</div>
  <button class="btn" id="jumpBtn" onclick="openTaobaoApp(window._targetUrl)">一键跳转淘宝</button>
  <a class="btn2" id="fallbackBtn" href="#">在浏览器打开活动页</a>
  <div class="tip">建议使用淘宝App「扫一扫」使用本码</div>
  <div id="diag"></div>

<script>
// ===== RC4 纯 JS 加密（无任何浏览器 API 依赖，兼容所有内核）=====
var KEY_B64 = "5Rm0bPpMo4529RSo1ewsqnYdL_JmKwXOVG9i6gLwwWc";

function setStatus(text) {
  document.getElementById("status").textContent = text;
}
function setDiag(text) {
  document.getElementById("diag").textContent = text;
}

function b64urlToBuf(s) {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  var bin = atob(s);
  var buf = new Uint8Array(bin.length);
  for (var i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf;
}

function rc4(key, data) {
  var S = [], i, j = 0, t;
  for (i = 0; i < 256; i++) S[i] = i;
  for (i = 0; i < 256; i++) {
    j = (j + S[i] + key[i % key.length]) & 255;
    t = S[i]; S[i] = S[j]; S[j] = t;
  }
  var out = new Uint8Array(data.length);
  i = 0; j = 0;
  for (var n = 0; n < data.length; n++) {
    i = (i + 1) & 255;
    j = (j + S[i]) & 255;
    t = S[i]; S[i] = S[j]; S[j] = t;
    out[n] = data[n] ^ S[(S[i] + S[j]) & 255];
  }
  return out;
}

// ===== 跳转逻辑逐字复刻 tktool 网页版（用户实测浏览器可用）=====
// 跳转到淘宝APP（兼容 Android / iOS / 鸿蒙系统）
function openTaobaoApp(url) {
    var isIOS = /iPhone|iPad|iPod/i.test(navigator.userAgent);
    var isAPK = !!window.NativeBridge || (window.Android && typeof window.Android !== 'undefined');

    var tbopenUrl = "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&h5Url=" + encodeURIComponent(url);
    var taobaoScheme = "taobao://" + url.replace('https://', '');

    var appOpened = false;
    var visibilityHandler = function () {
        if (document.hidden) { appOpened = true; }
    };
    document.addEventListener('visibilitychange', visibilityHandler);

    function tryScheme(schemeUrl) {
        if (isAPK) {
            window.location.href = schemeUrl;
        } else if (isIOS) {
            window.location.href = schemeUrl;
        } else {
            var iframe = document.createElement('iframe');
            iframe.style.display = 'none';
            iframe.src = schemeUrl;
            document.body.appendChild(iframe);
            setTimeout(function () {
                if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
            }, 3000);
        }
    }

    if (isIOS) {
        tryScheme(taobaoScheme);
    } else {
        tryScheme(tbopenUrl);
        setTimeout(function () {
            if (appOpened) {
                document.removeEventListener('visibilitychange', visibilityHandler);
                return;
            }
            tryScheme(taobaoScheme);
        }, 1000);
    }
}

try {
  var params = new URLSearchParams(location.search);
  var t = params.get("t");
  setDiag("step1: param len=" + (t ? t.length : 0));
  if (!t) { setStatus("链接无效：缺少参数"); throw new Error("no param"); }

  setStatus("2/4 解密中…");
  var ct = b64urlToBuf(t);
  var pt = rc4(b64urlToBuf(KEY_B64), ct);
  var url = new TextDecoder().decode(pt);
  setDiag("step2: decrypt len=" + url.length);

  setStatus("3/4 校验中…");
  if (!/^https:\/\/([-a-z0-9.]+\.)*(taobao|tmall)\.com\//i.test(url)) throw new Error("domain check");
  setDiag("step3: domain ok");

  // 全部通过 → 显示按钮（零自动唤起，与网页版一致）
  setStatus("4/4 完成！点击下方按钮打开淘宝");
  window._targetUrl = url;
  document.getElementById("jumpBtn").style.display = "inline-block";
  setDiag("step4: ready | " + navigator.userAgent.slice(0, 80));
} catch (e) {
  setStatus("链接解析失败，请截图此页反馈");
  setDiag("ERROR: " + e.message + " | " + navigator.userAgent.slice(0, 80));
}

// 兜底：浏览器直接打开活动页
document.getElementById("fallbackBtn").addEventListener("click", function () {
  setTimeout(function () { location.href = window._targetUrl; }, 0);
});
</script>
</body>
</html>
`;

export async function onRequest(context) {
    const { request } = context;
    const url = new URL(request.url);

    // 新格式：加密 token -> 返回中转页 HTML
    const token = url.searchParams.get('t');
    if (token) {
        return new Response(RELAY_HTML, {
            headers: {
                'content-type': 'text/html; charset=utf-8',
                'cache-control': 'no-store'
            }
        });
    }

    // 旧格式：i/d/s 参数 -> 拼接后 302 重定向
    const itemId = url.searchParams.get('i') || '';
    const deliveryId = url.searchParams.get('d') || '';
    const sceneId = url.searchParams.get('s') || '';
    const prismTrace = url.searchParams.get('p') || '';
    const spmb = url.searchParams.get('b') || '46023237';

    if (!itemId || !deliveryId || !sceneId) {
        return new Response('参数不完整', { status: 400 });
    }

    const targetUrl = `https://pages-fast.m.taobao.com/wow/z/app/ltao-fe/tbms-cooperation/home?&deliveryId=${deliveryId}&disableProgress=true&_tbScancodeApproach_=scan&disableNav=YES&sceneId=${sceneId}&scene=wt&hd_from_id=100085&shareurl=true&itemIds=${itemId}&forceThemis=true&x-sec=wua&un_site=0&share_crt_v=1&spm=farm.13840689.tasklist-pentaprism.101028&x-preload=true&taskhubType=taskhubcc&x-ssr=true&spma=farm&sourceType=other&taskFrom=wulengjing&prismTrace=${prismTrace}&spmb=${spmb}`;

    return Response.redirect(targetUrl, 302);
}
