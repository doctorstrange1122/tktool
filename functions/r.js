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
</style>
</head>
<body>
  <div id="status">正在解析…</div>
  <button class="btn" id="jumpBtn" onclick="openTaobaoApp(window._targetUrl)">一键跳转淘宝</button>
  <a class="btn2" id="fallbackBtn" href="#">在浏览器打开活动页</a>
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

// ===== 以下逻辑逐字复刻 tktool 网页版 index.html 的 openTaobaoApp（用户实测浏览器可用）=====
// 跳转到淘宝APP（兼容 Android / iOS / 鸿蒙系统）
function openTaobaoApp(url) {
    var isIOS = /iPhone|iPad|iPod/i.test(navigator.userAgent);
    var isAPK = !!window.NativeBridge || (window.Android && typeof window.Android !== 'undefined');

    // tbopen:// 是淘宝官方 Deep Link，鸿蒙/安卓兼容性好
    var tbopenUrl = "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&h5Url=" + encodeURIComponent(url);
    // taobao:// scheme（iOS 主要认这个）
    var taobaoScheme = "taobao://" + url.replace('https://', '');

    var appOpened = false;
    var visibilityHandler = function () {
        if (document.hidden) { appOpened = true; }
    };
    document.addEventListener('visibilitychange', visibilityHandler);

    // 尝试唤起 APP
    function tryScheme(schemeUrl) {
        if (isAPK) {
            // APK 环境：Java shouldOverrideUrlLoading 会拦截 scheme
            window.location.href = schemeUrl;
        } else if (isIOS) {
            // iOS Safari：必须用 window.location.href 直接跳转，iframe 会被拦截
            window.location.href = schemeUrl;
        } else {
            // 安卓浏览器：用隐藏 iframe 避免页面导航错误
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
        // iOS：用 taobao:// scheme 直接唤起
        tryScheme(taobaoScheme);
    } else {
        // 安卓/鸿蒙/APK：优先 tbopen://，失败回落 taobao://
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

(async function () {
  var params = new URLSearchParams(location.search);
  var t = params.get("t");
  if (!t) { setStatus("链接无效：缺少参数"); return; }

  var url;
  try {
    url = await decryptToken(t);
    if (!/^https:\/\/([-a-z0-9.]+\.)*(taobao|tmall)\.com\//i.test(url)) throw new Error("domain");
  } catch (e) {
    setStatus("链接解析失败，请重新生成二维码");
    return;
  }

  // 仅解密 + 显示按钮，零自动唤起（与网页版行为完全一致，避免触发浏览器拦截）
  window._targetUrl = url;
  setStatus("点击下方按钮打开淘宝");
  document.getElementById("jumpBtn").style.display = "inline-block";

  // 兜底：浏览器直接打开活动页
  document.getElementById("fallbackBtn").addEventListener("click", function () {
    setTimeout(function () { location.href = url; }, 0);
  });
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
