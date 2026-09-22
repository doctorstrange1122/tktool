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
    display: none; margin-top: 28px; padding: 14px 48px; background: #ff5000; color: #fff;
    border-radius: 24px; font-size: 17px; text-decoration: none; font-weight: bold;
  }
  .tip { margin-top: 20px; font-size: 13px; color: #aaa; line-height: 1.6; }
</style>
</head>
<body>
  <div class="spinner" id="spinner"></div>
  <div id="status">正在打开淘宝…</div>
  <a class="btn" id="manualBtn" href="#">打开淘宝</a>
  <div class="tip">建议使用淘宝App「扫一扫」使用本码<br>如未自动跳转，请点击上方按钮</div>

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

function setStatus(text, hideSpinner) {
  document.getElementById("status").textContent = text;
  if (hideSpinner) document.getElementById("spinner").style.display = "none";
}

async function decryptToken(token) {
  var raw = b64urlToBuf(token);
  var iv = raw.slice(0, 12);
  var data = raw.slice(12);
  var key = await crypto.subtle.importKey("raw", b64urlToBuf(KEY_B64), { name: "AES-GCM" }, false, ["decrypt"]);
  var plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: iv }, key, data);
  return new TextDecoder().decode(plain);
}

(async function () {
  var params = new URLSearchParams(location.search);
  var t = params.get("t");
  if (!t) { setStatus("链接无效：缺少参数", true); return; }

  var url;
  try {
    url = await decryptToken(t);
    // 域名白名单：只允许跳转淘宝/天猫域名
    if (!/^https:\/\/([-a-z0-9.]+\.)*(taobao|tmall)\.com\//i.test(url)) throw new Error("domain");
  } catch (e) {
    setStatus("链接解析失败，请重新生成二维码", true);
    return;
  }

  // 组装 tbopen 唤起协议：手淘内部直接打开活动页（链接全程不出现在页面上）
  var tbopen = "tbopen://m.taobao.com/tbopen/index.html?h5Url=" +
               encodeURIComponent(url) +
               "&action=ali.open.nav&module=h5&bootImage=0";

  var btn = document.getElementById("manualBtn");
  btn.href = tbopen;
  location.href = tbopen; // 自动唤起

  setTimeout(function () {
    setStatus("未自动跳转？", true);
    btn.style.display = "inline-block";
  }, 2500);
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
