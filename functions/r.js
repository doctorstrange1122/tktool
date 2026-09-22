// 短链跳转服务（v2）：
//  - ?t=<加密token> -> 服务端返回解密中转页（AES-256-GCM 解密 + tbopen 唤起手淘，明文链接不出现）
//  - ?i=&d=&s=      -> 旧格式短链，保留 302 重定向兼容
const RELAY_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>正在打开淘宝…</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    height: 100vh; background: #fff; color: #333; text-align: center; padding: 24px;
  }
  .spinner {
    width: 36px; height: 36px; border: 3px solid #eee; border-top-color: #ff5000;
    border-radius: 50%; animation: spin 0.8s linear infinite; margin-bottom: 20px;
  }
  @keyframes spin { to { transform: rotate(360deg); } }
  #status { font-size: 16px; color: #666; }
  .btn {
    display: inline-block; margin-top: 28px; padding: 14px 48px; background: #ff5000; color: #fff;
    border-radius: 24px; font-size: 17px; text-decoration: none; font-weight: bold;
  }
  .btn-fallback {
    display: none; margin-top: 14px; padding: 12px 36px; background: #fff; color: #ff5000;
    border: 1.5px solid #ff5000; border-radius: 24px; font-size: 15px; text-decoration: none; font-weight: bold;
  }
  .tip { margin-top: 20px; font-size: 13px; color: #aaa; line-height: 1.6; }
</style>
</head>
<body>
  <div class="spinner" id="spinner"></div>
  <div id="status">正在打开淘宝…</div>
  <a class="btn" id="manualBtn" href="#">打开淘宝App</a>
  <a class="btn-fallback" id="fallbackBtn" href="#">唤起失败？在浏览器打开活动页</a>
  <div class="tip">建议使用淘宝App「扫一扫」使用本码</div>

<script>
// ===== AES-256-GCM 密钥（与生成端一致，base64url 编码的 32 字节）=====
var KEY_B64 = "qdFpEAC_A6a70v-ruXAlOAvKAxVWgQ4e-0okednASoo";

function b64urlToBuf(s) {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  var bin = atob(s);
  var buf = new Uint8Array(bin.length);
  for (var i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf;
}

function setStatus(text) {
  document.getElementById("status").textContent = text;
}

async function decryptToken(token) {
  var raw = b64urlToBuf(token);
  var iv = raw.slice(0, 12);
  var data = raw.slice(12);
  var key = await crypto.subtle.importKey("raw", b64urlToBuf(KEY_B64), { name: "AES-GCM" }, false, ["decrypt"]);
  var plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: iv }, key, data);
  return new TextDecoder().decode(plain);
}

// ===== 唤起淘宝（移植自 huafei 一键跳转，与元宝过肥一致：tbopen://优先，taobao://回落）=====
function openTaobaoApp(targetUrl) {
    // tbopen:// 是淘宝官方 Deep Link，鸿蒙系统兼容性最好
    var tbopenUrl = "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&h5Url=" + encodeURIComponent(targetUrl);
    // taobao:// 作为备用 scheme（直接域名替换）
    var taobaoScheme = "taobao://" + targetUrl.replace("https://", "");

    var appOpened = false;
    var visibilityHandler = function () {
        if (document.hidden) { appOpened = true; }
    };
    document.addEventListener("visibilitychange", visibilityHandler);

    // 尝试通过 scheme 唤起 APP（浏览器环境用隐藏 iframe，避免页面导航错误/被拦截）
    function tryScheme(schemeUrl) {
        if (window.NativeBridge || (window.Android && typeof window.Android !== "undefined")) {
            window.location.href = schemeUrl;
        } else {
            var iframe = document.createElement("iframe");
            iframe.style.display = "none";
            iframe.src = schemeUrl;
            document.body.appendChild(iframe);
            setTimeout(function () {
                if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
            }, 3000);
        }
    }

    // 第一优先：tbopen://（鸿蒙兼容性最好）
    tryScheme(tbopenUrl);

    // 1秒后未打开则尝试 taobao://
    setTimeout(function () {
        if (appOpened) {
            document.removeEventListener("visibilitychange", visibilityHandler);
            return;
        }
        tryScheme(taobaoScheme);
    }, 1000);

    return function () {
        document.removeEventListener("visibilitychange", visibilityHandler);
    };
}

(async function () {
  var params = new URLSearchParams(location.search);
  var t = params.get("t");
  if (!t) { setStatus("链接无效：缺少参数"); return; }

  var url;
  try {
    url = await decryptToken(t);
    // 域名白名单：只允许跳转淘宝/天猫域名
    if (!/^https:\/\/([-a-z0-9.]+\.)*(taobao|tmall)\.com\//i.test(url)) throw new Error("domain");
  } catch (e) {
    setStatus("链接解析失败，请重新生成二维码");
    return;
  }

  var btn = document.getElementById("manualBtn");
  var fbBtn = document.getElementById("fallbackBtn");

  // 主按钮：用户手势再触发一轮 tbopen -> taobao 唤起
  btn.addEventListener("click", function () { openTaobaoApp(url); });

  // 兜底：浏览器直接打开活动页（系统可能通过 App Linking 唤起淘宝；即便不唤起，H5 活动页也能正常使用）
  fbBtn.addEventListener("click", function () {
    setTimeout(function () { location.href = url; }, 0);
  });

  // 自动唤起：先跑一轮 tbopen -> taobao（iframe 方式，部分浏览器允许非手势触发）
  openTaobaoApp(url);

  // 2.2 秒后仍在页面 → 自动唤起未成功 → 状态提示（按钮常显，等待用户点击）
  setTimeout(function () {
    document.getElementById("spinner").style.display = "none";
    setStatus("若未自动跳转，请点击「打开淘宝App」");
  }, 2200);
})();
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
